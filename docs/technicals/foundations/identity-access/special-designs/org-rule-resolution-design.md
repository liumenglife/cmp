# 组织架构 / 权限 / 统一认证主线专项设计：组织规则解析

## 1. 文档说明

本文档用于继续下沉 `identity-access` 主线中“组织规则解析”能力，重点回答以下问题：

- `ia_org_rule.resolver_config` 的内部语法如何定义
- 不同 `rule_type` 如何在统一上下文中被解析为候选主体集合
- 组织规则如何做版本治理、兼容升级、缓存失效与审计追溯

本文是以下文档的下游专项设计：

- [`identity-access Detailed Design`](../detailed-design.md)

`workflow-engine` 对本专项设计属于强依赖、关键消费方：它高度依赖本设计产出的规则解析能力与约束来完成流程节点选人，但这种关系属于跨模块依赖关系，不属于本文档的父文档关系，也不构成本文档的下游来源。

本文不展开以下内容：

- 不重写流程节点编排、会签聚合或流程实例状态机
- 不重写用户、组织、角色、数据权限主表设计
- 不把内部解析语法直接暴露成外部 `API` 契约
- 不写前端规则编辑器交互稿与页面表单布局
- 不写具体 `SQL`、索引细节或最终代码实现

## 2. 设计目标

- 为审批节点、通知路由、授权命中等规则消费场景提供统一的组织规则解释器
- 让各规则消费方只消费解析结果，不再各自维护一套选人规则语法
- 保证规则解析结果可审计、可回放、可缓存、可重算
- 允许规则语法演进，但不破坏已生效引用关系与历史消费快照的可解释性
- 当组织、人员、角色发生变化时，可按统一失效机制重新解析

## 3. 适用范围与调用方

组织规则解析能力的正式调用方包括：

- 流程引擎：审批节点绑定“部门负责人”“发起人上级”“部门内某角色”等规则时调用
- 通知中心：按组织规则计算待通知对象时调用
- 授权判定：需要把某类组织规则解释为候选主体或命中依据时调用
- 管理端预校验：规则保存、流程发布、授权配置保存前调用预解析能力

统一原则如下：

- 规则定义权属于 `identity-access`
- 规则消费方只能传入上下文并获取结果，不能自行解释 `resolver_config`
- 解析结果默认是候选主体集合及其证据摘要，而不是流程引擎专用结构

## 4. 核心对象与边界

### 4.1 规则主档

规则主档沿用 `ia_org_rule`，核心字段解释如下：

- `org_rule_id`：规则主键
- `rule_code`：规则稳定标识，供流程节点、通知模板、授权配置引用
- `rule_type`：规则大类，目前固定为 `MANAGER_OF_ORG_UNIT`、`STARTER_MANAGER`、`ROLE_IN_ORG_UNIT`、`FIXED_CHAIN`
- `rule_scope_type` / `rule_scope_ref`：规则可用范围
- `resolver_config`：内部解析配置，使用版本化 `JSON`
- `fallback_policy`：无法解析出候选主体时的统一兜底策略
- `version_no`：规则内容版本号，用于兼容历史引用与缓存失效

规则主档只承接“当前可编辑规则”，不单独承接历史冻结引用。凡已经发布、生效、被流程定义或授权命中正式消费的规则版本，统一落到 `ia_org_rule_version`；凡运行期、预校验或授权命中产生的正式解析结果，统一落到 `ia_org_rule_resolution_record`。

### 4.2 冻结版本与解析记录

为避免消费方只依赖 `org_rule_id + version_no` 这一可变口径，组织规则解析补充两个正式对象：

- `ia_org_rule_version`：不可变版本承接对象，冻结 `resolver_config_snapshot`、`fallback_policy_snapshot`、`schema_version` 与 `version_checksum`
- `ia_org_rule_resolution_record`：正式解析记录对象，冻结 `org_rule_version_id`、`context_checksum`、解析结果快照与证据摘要

统一要求如下：

- 已发布流程定义、已生效授权命中、通知正式投递等持久化消费关系，必须承接 `org_rule_version_id`
- 任何需要解释“当时为什么解析到这些人”的审计、回放和人工复核，必须读取 `ia_org_rule_resolution_record`
- `ia_org_rule.version_no` 仍用于当前主档递增，但不再单独承担历史回放语义

### 4.3 解析输入上下文

统一解析入参在内部归一为 `ResolutionContext`，最少包含：

- `request_id`：本次解析请求标识
- `org_rule_ref`：规则引用，至少包含 `org_rule_id` 或 `rule_code`
- `active_org_id`：当前组织上下文
- `active_org_unit_id`：当前组织单元上下文，可为空
- `starter_user_id`：发起人主体，可为空
- `starter_org_unit_id`：发起人发起时所在部门，可为空
- `resource_owner_user_id`：业务对象负责人，可为空
- `resource_owner_org_unit_id`：业务对象归属部门，可为空
- `candidate_org_unit_id`：消费方显式指定的目标组织单元，可为空
- `business_context`：业务附加上下文，只允许作为参数源，不允许携带自定义脚本

解析器只接受上述结构化上下文，不接受任意表达式拼接。

### 4.4 解析输出

统一输出为 `OrgRuleResolutionResult`：

- `resolution_status`：`RESOLVED`、`EMPTY`、`FALLBACK_USED`、`FAILED_PRECHECK`
- `resolved_subject_list`：候选主体列表
- `evidence_list`：命中路径、所用组织单元、角色、规则版本、兜底原因
- `resolver_version`：解释器版本
- `org_rule_version_id`：本次解析所用冻结版本主键
- `rule_version_no`：规则版本
- `context_checksum`：本次上下文摘要，供缓存与审计使用

候选主体项至少包含：

- `user_id`
- `source_type`：`ORG_MANAGER`、`STARTER_MANAGER`、`ROLE_MEMBER`、`CHAIN_MANAGER`、`FALLBACK_SUBJECT`
- `source_ref`
- `priority_no`
- `org_rule_resolution_record_id`：正式解析时返回，用于与授权命中、流程实例、审计事件关联

## 5. `resolver_config` 内部语法

`resolver_config` 采用受控 `JSON` 文档，不支持脚本、不支持自由表达式、不支持运行时拼接 `SQL`。

统一顶层结构如下：

```json
{
  "schema_version": "1.0",
  "subject_anchor": {
    "type": "STARTER_ORG_UNIT"
  },
  "selection": {
    "dedupe": true,
    "sort": "PRIORITY_ASC"
  },
  "rule_body": {},
  "fallback": {
    "policy": "EMPTY_SET"
  }
}
```

字段说明：

- `schema_version`：`resolver_config` 语法版本，不等于 `ia_org_rule.version_no`
- `subject_anchor`：解析锚点来源
- `selection`：结果去重和排序策略
- `rule_body`：具体规则体，随 `rule_type` 变化
- `fallback`：本条规则的局部兜底配置；若为空，则继承 `fallback_policy`

### 5.1 `subject_anchor`

合法值如下：

- `ACTIVE_ORG_UNIT`：当前会话所在组织单元
- `STARTER_ORG_UNIT`：发起人发起时所在组织单元
- `RESOURCE_OWNER_ORG_UNIT`：业务对象负责人所在组织单元
- `EXPLICIT_ORG_UNIT`：由调用方显式传入 `candidate_org_unit_id`
- `FIXED_ORG_UNIT`：规则中直接写死某个 `org_unit_id`

该字段只决定“从哪里开始找”，不决定最终找谁。

### 5.2 `selection`

当前只支持以下策略：

- `dedupe`：默认 `true`，按 `user_id` 去重
- `sort`：当前固定为 `PRIORITY_ASC`
- `limit`：可选，限制最大返回人数；默认不限制

### 5.3 按 `rule_type` 的 `rule_body`

#### A. `MANAGER_OF_ORG_UNIT`

用于解析某个组织单元负责人。

```json
{
  "schema_version": "1.0",
  "subject_anchor": { "type": "STARTER_ORG_UNIT" },
  "rule_body": {
    "manager_source": "ORG_UNIT_MANAGER",
    "allow_parent_fallback": true,
    "max_parent_levels": 3
  }
}
```

- `manager_source`：当前仅允许 `ORG_UNIT_MANAGER`
- `allow_parent_fallback`：本级无负责人时，是否向上级部门查找
- `max_parent_levels`：向上最多查找层数，必须是正整数

#### B. `STARTER_MANAGER`

用于解析发起人的直接上级或管理链上级。

```json
{
  "schema_version": "1.0",
  "subject_anchor": { "type": "STARTER_ORG_UNIT" },
  "rule_body": {
    "manager_level": 1,
    "prefer_line_manager": true,
    "fallback_to_org_manager": true
  }
}
```

- `manager_level`：从发起人开始向上查找第几级管理者，默认 `1`
- `prefer_line_manager`：优先个人管理链，再回落到部门负责人链
- `fallback_to_org_manager`：个人管理链缺失时，是否回落到组织负责人

#### C. `ROLE_IN_ORG_UNIT`

用于解析某组织单元内具备指定角色的主体集合。

```json
{
  "schema_version": "1.0",
  "subject_anchor": { "type": "ACTIVE_ORG_UNIT" },
  "rule_body": {
    "role_code": "DEPT_REVIEWER",
    "include_child_org_units": false,
    "member_status_filter": ["ACTIVE"],
    "assignment_status_filter": ["ACTIVE"]
  }
}
```

- `role_code`：必填，引用 `ia_role.role_code`
- `include_child_org_units`：是否向子部门展开
- `member_status_filter`：成员状态过滤
- `assignment_status_filter`：角色授予状态过滤

#### D. `FIXED_CHAIN`

用于解析一条预定义的组织链或审批链。

```json
{
  "schema_version": "1.0",
  "subject_anchor": { "type": "STARTER_ORG_UNIT" },
  "rule_body": {
    "chain_steps": [
      { "step_type": "SELF_MANAGER", "required": true },
      { "step_type": "PARENT_ORG_MANAGER", "required": true, "levels": 1 },
      { "step_type": "ORG_ROLE", "required": false, "role_code": "LEGAL_REVIEWER" }
    ]
  }
}
```

约束如下：

- `chain_steps` 至少 1 步
- 每一步只能使用受控 `step_type`
- 当前支持 `SELF_MANAGER`、`PARENT_ORG_MANAGER`、`ORG_ROLE`
- `required=false` 的步骤解析失败时可跳过，不触发整体失败

## 6. 解析流程

统一解析流程固定为以下 8 步：

1. 读取规则主档，校验 `rule_status=ACTIVE`
2. 校验 `rule_scope_type` 与当前 `active_org_id` / `active_org_unit_id` 是否匹配
3. 解析 `resolver_config.schema_version`，选择对应解释器
4. 根据 `subject_anchor` 计算锚点组织单元
5. 按 `rule_type` 执行规则体求值
6. 对结果按 `user_id` 去重、按 `priority_no` 排序
7. 若结果为空，则按 `fallback` 或 `fallback_policy` 执行兜底
8. 输出结果、证据摘要、上下文摘要，并记录审计事件

### 6.1 预校验模式

保存规则、保存引用组织规则的配置，以及发布或生效会固化规则引用关系的定义前，应优先调用预校验模式。

预校验模式与正式解析的区别：

- 不落审计事实主流水，只输出校验结果
- 允许使用模拟上下文样本验证语法与可解析性
- 重点判断“规则可解释”，不要求一定解析到最终人

### 6.2 正式解析模式

实例运行、待办生成、通知投递、授权命中时使用正式解析模式。

正式解析必须保证：

- 有审计 `trace_id`
- 结果可回放到某个规则版本与某份上下文摘要
- 高敏场景的兜底必须可解释
- 必须写 `ia_org_rule_resolution_record`，并回传 `org_rule_resolution_record_id`

## 7. 兜底策略

统一支持以下 `fallback_policy`：

- `EMPTY_SET`：返回空集合，由消费方决定后续处理
- `PARENT_MANAGER`：继续向上级部门负责人查找
- `FIXED_SUBJECTS`：回退到预配置的固定主体集合
- `FAIL_PRECHECK`：在预校验阶段直接视为失败，不允许保存 / 发布

使用原则：

- 审批、授权等高风险引用场景默认不建议静默回退到无边界固定人，除非业务明确接受
- 涉及授权命中时，默认应优先 `EMPTY_SET`，避免因兜底扩大权限
- `FIXED_SUBJECTS` 必须显式写入 `resolver_config.fallback.subject_list`

## 8. 版本治理与兼容策略

### 8.1 双版本概念

必须区分两个版本号：

- `ia_org_rule.version_no`：规则内容版本，规则配置每次变更递增
- `resolver_config.schema_version`：语法版本，控制解释器如何读取配置

二者不能混用。

### 8.2 兼容规则

- `schema_version` 的同主版本内只允许追加可选字段，不允许修改已有字段语义
- 出现破坏性变更时，必须升级主版本，例如从 `1.x` 升到 `2.0`
- 新解释器上线前，必须保留旧解释器读取历史规则的能力
- 已生效引用关系中使用的 `org_rule_version_id` 不得被原地重写；`org_rule_id + version_no` 只是该冻结对象的定位键，不再是唯一正式承接口径

### 8.3 升级策略

规则语法升级按以下顺序进行：

1. 新增解释器读取能力
2. 提供配置迁移器，把旧配置转为新语法
3. 对管理端编辑入口切换默认新语法
4. 存量规则按需迁移，不强制一次性重写
5. 确认无历史依赖后，才允许淘汰旧语法写入入口

## 9. 缓存、失效与重算

### 9.1 缓存键

解析结果缓存键统一为：

`org_rule_version_id + context_checksum`

其中 `context_checksum` 至少覆盖：

- `active_org_id`
- `active_org_unit_id`
- `starter_user_id`
- `starter_org_unit_id`
- `resource_owner_user_id`
- `resource_owner_org_unit_id`
- `candidate_org_unit_id`

### 9.2 失效触发条件

发生以下变化时，应触发相关规则缓存失效：

- `ia_org_rule` 规则内容变更
- `ia_org_unit.manager_user_id` 变更
- 组织树父子关系变更
- `ia_org_membership` 变更
- `ia_role_assignment` 变更且被 `ROLE_IN_ORG_UNIT` 类型规则引用
- 主体状态变更为 `DISABLED`、`LOCKED`、`MERGED`

### 9.3 重算原则

- 运行时缓存失效后允许同步重算
- 大批量组织变更后应支持异步批量重算
- 历史实例不回写原审批事实；审计回放读取当时的 `ia_org_rule_resolution_record`，诊断模式才允许额外计算“若按当前规则会选到谁”

## 10. 审计与观测

每次正式解析至少记录以下字段：

- `trace_id`
- `org_rule_id`
- `rule_version_no`
- `schema_version`
- `resolution_status`
- `resolved_subject_count`
- `fallback_used`
- `elapsed_ms`
- `context_checksum`

关键指标至少包括：

- 组织规则解析耗时分位值
- 各 `rule_type` 解析成功率
- 兜底触发率
- 空结果率
- 因组织变更触发的缓存失效率

## 11. 与其他主线的协作约束

### 11.1 与流程引擎

- 流程引擎只能引用规则和消费结果，不拥有规则语法解释权
- `workflow-engine` 是本能力的强依赖、关键消费方，但其依赖关系不改变本文档归属于 `identity-access` 设计体系这一事实
- 任何会在发布、生效或持久化后固化组织规则引用关系的消费方，都必须先调用预校验，确保引用的是合法规则，并正式承接 `org_rule_version_id`
- 会签、并行聚合、转办不在本专项设计处理，仍归流程引擎

### 11.2 与通知中心

- 通知中心可复用同一规则解析能力决定触达对象
- 通知模板、渠道优先级不在本文处理

### 11.3 与授权判定

- 授权判定只把组织规则作为命中依据之一
- 规则解析为空时，不得默认扩大权限
- 授权命中若依赖组织规则，必须同时保存 `org_rule_version_id` 与 `org_rule_resolution_record_id`，避免命中解释回退到当前主档

## 12. 实施与落地建议

建议把后续实现拆为以下几个独立工作单元：

- 规则配置校验器
- 规则解释器注册中心
- 上下文归一器与锚点解析器
- 四类 `rule_type` 解释器
- 缓存失效与批量重算任务
- 审计埋点与管理端预校验接口

## 13. 本文结论

组织规则解析能力应被收口为 `identity-access` 内部统一解释器，而不是散落在流程引擎、通知、授权判定中的多套局部逻辑。

通过版本化 `resolver_config`、不可变 `ia_org_rule_version`、正式 `ia_org_rule_resolution_record`、受控锚点模型、统一兜底策略与可回放审计链路，可以让审批节点“绑定部门、人员或组织规则”的约束在实现层真正落地，并保持后续可演进、可兼容、可追溯。

# 流程引擎专项设计：组织规则求值器与参与人快照生成

## 1. 文档说明

本文档用于继续下沉 `workflow-engine` 主线中"组织规则求值消费与参与人快照生成"这一能力，重点回答以下问题：

- 流程引擎如何定义 `ORG_RULE` 的消费侧规则字典，而非重复定义规则语法本身
- 节点绑定中的岗位 / 角色过滤语言如何在求值器内被组装为统一执行参数
- 求值器如何调用 `identity-access` 组织规则解析能力，而非自行解释 `resolver_config`
- 求值结果如何在运行时被冻结为正式参与人快照，保证历史任务不因组织变更而漂移
- 求值与快照生成过程如何留痕，保证审计可回放、争议可追责

本文是以下文档的下游专项设计：

- [`workflow-engine Detailed Design`](../detailed-design.md)

`identity-access org-rule-resolution` 是本设计的关键消费依赖方：它提供规则解析入参结构、解析输出、版本治理与缓存失效机制，流程引擎高度依赖这些能力来完成节点选人，但这种关系属于跨模块消费依赖关系，不属于本文档的父文档关系，也不构成本文档的下游来源。

本文不展开以下内容：

- 不重写 `identity-access` 侧的 `resolver_config` 语法、`rule_type` 定义或解析流程
- 不重写 `wf_node_binding`、`wf_approval_task` 等主表结构
- 不写对外 `API` 路径、请求字段、响应样例、错误码枚举全集
- 不写并行与会签聚合算法、转办内部规则或超时策略
- 不写实施排期、迁移项目计划或上线手册

## 2. 设计目标

- 让流程引擎对 `ORG_RULE` 的消费行为收敛为"组装上下文 + 调用解析 + 冻结结果"三步，而非自行解释规则语法
- 让节点绑定上的岗位 / 角色过滤参数成为受控消费侧配置，而不是绕过组织底座的私有选人逻辑
- 让运行时参与人快照成为以 `wf_participant_snapshot` 为锚点的正式不可变真相，不因后续组织变更而改写已生成任务的执行人
- 让求值过程的输入、输出、证据和失败原因可审计、可回放、可诊断
- 让求值器具备缓存、重算与兜底能力，但不把缓存结果当作唯一真相

## 3. `ORG_RULE` 消费侧规则字典

### 3.1 字典定位

流程引擎不拥有组织规则的定义权和解释权。`ORG_RULE` 消费侧规则字典的定位是：描述"流程引擎在消费组织规则求值结果时，需要额外关注哪些消费侧约束"，而不是重新定义组织规则本身。

该字典由 `identity-access` 侧发布的正式 `rule_type` 驱动，流程引擎只在其上叠加消费侧行为声明。

### 3.2 字典结构

消费侧规则字典以 `wf_org_rule_consumer_profile` 为逻辑承载对象，至少包含：

- `rule_type`：与 `identity-access` 侧 `ia_org_rule.rule_type` 对齐，当前固定为 `MANAGER_OF_ORG_UNIT`、`STARTER_MANAGER`、`ROLE_IN_ORG_UNIT`、`FIXED_CHAIN`
- `consumer_context_mapping`：声明该 `rule_type` 在流程引擎消费时需要从哪些运行时来源提取解析上下文字段
- `candidate_filter_chain`：声明求值结果返回后，流程引擎侧需要叠加的过滤步骤集合
- `snapshot_policy`：声明该规则类型的参与人快照冻结策略
- `fallback_behavior`：声明当求值结果为空或失败时，流程引擎侧的兜底行为

字典不承载 `resolver_config` 的语法定义。语法定义权始终属于 `identity-access`。

### 3.3 字典与 `resolver_config` 的关系

- `resolver_config` 是组织底座侧的规则解析配置，定义"如何从组织数据中找到候选主体"
- 消费侧规则字典是流程引擎侧的消费行为配置，定义"流程引擎如何组装调用参数、如何过滤结果、如何冻结快照"
- 二者通过 `rule_type` 和 `org_rule_id` 建立引用关系，但职责不重叠
- 流程引擎在运行时读取 `wf_node_binding.binding_value`（即 `org_rule_code` 或 `org_rule_id`），再结合消费侧字典组装完整调用参数

## 4. 岗位 / 角色过滤语言

### 4.1 过滤语言定位

岗位 / 角色过滤语言是流程引擎在 `ORG_RULE` 求值结果之上叠加的消费侧过滤能力。它不替代 `identity-access` 侧的 `member_status_filter` 或 `assignment_status_filter`，而是在解析结果返回后，针对流程审批场景进一步收窄候选人范围。

### 4.2 过滤参数模型

过滤参数统一收口为 `CandidateFilterSpec`，以受控 `JSON` 结构表达，不支持脚本、不支持自由表达式。

```json
{
  "filter_version": "1.0",
  "filters": [
    {
      "filter_type": "EXCLUDE_USERS",
      "params": {
        "user_ids": ["{starter_user_id}"]
      }
    },
    {
      "filter_type": "REQUIRE_APPROVAL_PERMISSION",
      "params": {
        "permission_code": "WORKFLOW_APPROVAL"
      }
    },
    {
      "filter_type": "EXCLUDE_ABSENT",
      "params": {}
    },
    {
      "filter_type": "EXCLUDE_CROSS_ORG",
      "params": {
        "boundary_org_id": "{active_org_id}"
      }
    }
  ],
  "dedupe": true,
  "sort": "PRIORITY_ASC",
  "limit": null
}
```

### 4.3 受控过滤类型

当前只支持以下过滤类型，新增类型必须经字典登记后方可使用：

- `EXCLUDE_USERS`：按 `user_id` 排除特定主体，典型场景为排除发起人自身
- `REQUIRE_APPROVAL_PERMISSION`：要求候选主体具备指定审批权限码
- `EXCLUDE_ABSENT`：排除当前处于离岗、休假、停用状态的主体
- `EXCLUDE_CROSS_ORG`：排除不属于指定组织边界的主体，防止跨组织越权审批
- `EXCLUDE_LOCKED`：排除账号处于锁定或合并状态的主体
- `MAX_CANDIDATES`：限制最大候选人数，超出时按优先级截断

### 4.4 过滤语言约束

- 过滤器只操作求值器返回的候选主体集合，不得修改组织底座数据
- 过滤器参数只允许引用已登记的运行时上下文字段或固定值，不允许携带自定义脚本
- 过滤器执行顺序按 `filters` 数组顺序依次执行，前一步结果为后一步输入
- 过滤器版本升级遵循与 `resolver_config.schema_version` 类似的兼容规则：同主版本只允许追加可选过滤器，不允许修改已有过滤器语义

### 4.5 过滤器与节点绑定的关系

过滤参数存储在 `wf_node_binding.resolver_config` 的消费侧扩展槽中，具体路径统一为 `resolver_config.consumer_filter_spec`。这里显式带上 `_spec`，用于与 `identity-access` 侧 `resolver_config` 自带的解析语义、成员过滤语义区分开来，避免把流程引擎消费侧过滤误解为组织规则定义本身的一部分。这样绑定声明与过滤声明在同一对象内收敛，避免过滤参数散落在流程定义的其他位置。

`wf_node_binding.fallback_strategy` 在过滤后生效：若过滤后候选人为空，则按 `fallback_strategy` 定义的兜底动作执行，而非直接失败。

## 5. 求值器实现设计

### 5.1 求值器定位

求值器是 `Org Binding Resolver` 内部针对 `ORG_RULE` 绑定类型的专用执行路径。它的职责是：组装解析上下文 → 调用 `identity-access` 规则解析 → 叠加消费侧过滤 → 输出最终候选人集合与证据。

求值器不拥有规则语法解释权，不直接读取组织主数据表，不自行实现管理链查找或角色匹配逻辑。

### 5.2 执行流程

求值器执行流程固定为以下 8 步：

1. 读取 `wf_node_binding`，确认 `binding_type=ORG_RULE`，提取 `binding_value`（规则引用）和 `resolver_config`
2. 从消费侧规则字典加载该 `rule_type` 的 `consumer_context_mapping`，组装 `ResolutionContext`
3. 调用 `identity-access` 规则解析接口，传入 `ResolutionContext`
4. 接收 `OrgRuleResolutionResult`，校验 `resolution_status`
5. 若状态为 `RESOLVED`，按 `consumer_filter_spec` 依次执行过滤器链
6. 若状态为 `EMPTY` 或 `FALLBACK_USED`，按 `fallback_strategy` 执行兜底
7. 若状态为 `FAILED_PRECHECK`，记录失败原因，不自动重试，直接进入人工介入链路
8. 输出最终 `candidate_user_list`、`resolved_assignee_list`、`resolver_snapshot` 与过滤证据

### 5.3 上下文组装规则

求值器从以下运行时来源提取 `ResolutionContext` 字段：

| ResolutionContext 字段 | 运行时来源 |
| --- | --- |
| `request_id` | 本次解析请求内部标识 |
| `org_rule_ref` | `wf_node_binding.binding_value` |
| `active_org_id` | 流程实例绑定的组织域 |
| `active_org_unit_id` | 节点绑定配置或实例上下文 |
| `starter_user_id` | `wf_process_instance.starter_user_id` |
| `starter_org_unit_id` | 发起人所属部门，从组织底座实时查询 |
| `resource_owner_user_id` | 合同主档负责人，由业务上下文传入 |
| `resource_owner_org_unit_id` | 合同主档归属部门 |
| `candidate_org_unit_id` | 节点绑定显式指定的目标组织单元 |
| `business_context` | 流程实例业务附加上下文 |

组装约束：

- 只允许填充上述受控字段，不允许拼接自定义脚本或任意键值
- 字段缺失时按消费侧字典的默认策略处理，不静默忽略
- `business_context` 只作为参数源，不允许携带可执行代码

### 5.4 结果消费规则

求值器接收到 `OrgRuleResolutionResult` 后，按以下规则消费：

- `resolved_subject_list` 是候选主体来源，求值器在此之上叠加过滤器链
- `evidence_list` 必须完整保留，不得裁剪或丢弃
- `resolver_version` 和 `rule_version_no` 必须写入快照，供后续审计回放
- `context_checksum` 用于缓存键和审计比对

### 5.5 兜底与失败边界

求值器的兜底行为由 `wf_node_binding.fallback_strategy` 驱动，建议支持：

- `ESCALATE`：升级到上级审批人或管理人
- `AUTO_TRANSFER`：转给预设接替人
- `FAIL_INSTANCE`：实例进入 `FAILED` 状态，等待人工介入
- `SKIP_NODE`：跳过当前节点，仅在节点标记为非强制时允许

兜底行为的执行结果必须写入 `resolver_snapshot.fallback_detail`，不得静默执行。

## 6. 与 `identity-access org-rule-resolution` 的协作边界

### 6.1 边界原则

流程引擎与 `identity-access org-rule-resolution` 的关系是"消费方与解析方"，而不是"调用方与被调用方"的简单反转。边界如下：

- 规则定义权属于 `identity-access`，流程引擎只引用 `org_rule_id` 或 `rule_code`
- 规则语法解释权属于 `identity-access`，流程引擎不读取 `resolver_config` 内部结构自行求值
- 解析入参结构以 `identity-access` 定义的 `ResolutionContext` 为准，流程引擎负责组装但不扩展字段
- 解析输出结构以 `identity-access` 定义的 `OrgRuleResolutionResult` 为准，流程引擎负责消费但不修改语义
- 兜底策略在两个层面独立生效：规则级兜底由 `identity-access` 处理，节点级兜底由流程引擎处理

### 6.2 预校验协作

流程引擎在以下场景必须调用 `identity-access` 预校验模式：

- 流程定义发布前，校验所有 `ORG_RULE` 绑定引用的规则是否存在且可解析
- 节点绑定配置变更后，校验新配置的规则参数是否满足最小约束
- 规则版本升级后，校验引用该规则的流程定义是否仍然可解析

预校验结果只作为发布门禁证据，不写入运行时快照。

其中与规则版本冻结边界相关的正式语义必须收口如下：

- 流程发布时冻结 `ORG_RULE` 引用关系：发布产物必须把每个节点实际引用的 `org_rule_id + rule_version_no` 一并冻结到流程版本快照，和 `identity-access` 已定义的“已生效引用关系不得原地改写”保持一致
- 实例启动时不再次挑选或改写规则版本；实例只继承其 `wf_process_version` 已冻结的规则版本边界，避免同一流程版本的不同实例因发布时间差异绑定到不同规则版本
- 未进入节点前，不跟随最新规则版本；后续节点在真正进入时，只基于发布时已冻结的 `rule_version_no` 配合最新组织主数据重新解析，不会自动切到更新后的规则内容版本
- 规则升级后的“重新校验引用流程”语义，是对引用该规则的流程定义重新执行发布级预校验，产出影响报告、阻断告警或要求重新发布的新流程版本；它不热更新已发布流程版本，也不回写运行中实例或已生成任务的快照

### 6.3 正式解析协作

流程引擎在以下场景调用 `identity-access` 正式解析模式：

- 实例进入审批节点时，为 `ORG_RULE` 类型绑定生成参与人
- 任务转办后需要重新解析候选人时
- 超时升级策略需要解析上级审批人时

正式解析必须保证有审计 `trace_id`，结果可回放到规则版本与上下文摘要。

正式解析读取的规则版本来源于流程版本快照中已冻结的 `org_rule_id + rule_version_no`，而不是按 `rule_code` 临时解析到当前最新版本。

### 6.4 缓存协作

- `identity-access` 侧按 `org_rule_id + version_no + context_checksum` 管理解析结果缓存
- 流程引擎侧不重复建设规则解析缓存，只消费 `identity-access` 的缓存结果
- 当 `identity-access` 因组织变更触发缓存失效时，流程引擎侧尚未生成任务的后续节点在进入时重新解析
- 已生成的任务不受缓存失效影响，以冻结快照为准

## 7. 运行时参与人快照生成规则

### 7.1 快照定位

运行时参与人快照是流程引擎在节点执行时生成的不可变事实记录。它回答"这次节点执行时，基于哪一版规则、哪份上下文、经过哪些过滤、最终选出了谁"，而不是"当前组织架构下这个规则会选出谁"。

快照一旦写入，不因后续组织变更、规则变更或人员状态变更而自动更新。

### 7.2 快照对象结构

参与人快照统一收口为 `ParticipantSnapshot`，至少包含：

- `snapshot_id`：快照主键
- `process_id`：所属流程实例
- `node_id`：所属节点
- `binding_id`：所属绑定配置
- `binding_type`：`ORG_RULE`
- `org_rule_id`：引用的组织规则主键
- `rule_version_no`：规则内容版本
- `resolver_version`：解释器版本
- `context_checksum`：本次解析上下文摘要
- `resolution_status`：解析状态
- `candidate_user_list`：候选人集合，每项至少包含 `user_id`、`source_type`、`source_ref`、`priority_no`
- `filter_applied`：已应用的过滤器列表及各过滤器移除的主体摘要
- `resolved_assignee_list`：最终生成任务的执行人集合
- `fallback_detail`：兜底执行详情，若未触发兜底则为空
- `evidence_summary`：解析证据摘要
- `snapshot_generated_at`：快照生成时间
- `snapshot_generated_by`：快照生成触发者，系统生成时为系统标识

### 7.3 快照生成时机

快照在以下时机生成：

- 实例进入审批节点时，为该节点所有 `ORG_RULE` 类型绑定生成快照
- 任务转办触发重新解析时，生成新快照并关联到新任务
- 超时升级策略触发重新解析时，生成新快照

快照生成必须在事务内完成：解析 → 过滤 → 快照写入 → 任务生成，四步在同一事务内提交，不允许中间态泄漏。

### 7.4 快照与任务的关系

- `ParticipantSnapshot` 是组织规则求值的正式证据主对象，其父文档持久化锚点为 `wf_participant_snapshot`；任务侧字段只是面向任务执行和列表查询的内嵌投影，不形成第二套求值真相
- 每个 `wf_approval_task` 通过 `resolver_snapshot.snapshot_id` 稳定引用一份参与人快照
- 同一节点的多个并行或会签任务共享同一份快照的候选集合，但各自记录实际执行人
- 转办派生的新任务引用新快照，原任务快照不被修改
- `candidate_snapshot` 只保留任务分发所需的最小候选人与执行人投影；`resolver_snapshot` 引用 `ParticipantSnapshot` 作为完整求值证据，二者内容不允许各自独立演化

### 7.5 快照不可变性约束

- 快照一经写入，禁止原地更新
- 若需记录"重新解析"结果，必须生成新快照并建立版本链
- 快照删除只允许在实例级联删除或数据归档场景下执行，不允许单独删除快照而保留任务
- 快照的 `row_version` 用于防并发写入冲突

### 7.6 组织变更对快照的影响

- 已生成的快照不受组织变更影响，保持写入时的求值结果
- 尚未生成快照的后续节点在进入时重新解析，使用最新组织数据
- 若组织变更导致后续节点解析为空，按 `fallback_strategy` 执行，实例不静默跳过节点
- 组织变更本身不触发已生成快照的重算，但允许通过管理端人工干预触发诊断性重算

## 8. 审计留痕

### 8.1 审计范围

围绕组织规则求值与参与人快照生成，审计留痕至少覆盖四类事件：

1. 解析事件：何时、对哪个节点、哪个绑定、使用哪版规则发起解析
2. 过滤事件：应用了哪些过滤器、各过滤器移除了多少候选人、最终候选人集合摘要
3. 快照事件：何时生成快照、快照主键、关联的任务主键
4. 失败与兜底事件：解析失败原因、兜底策略触发详情、人工介入记录

### 8.2 审计字段

每次求值至少记录以下审计字段：

- `trace_id`
- `process_id`
- `node_id`
- `binding_id`
- `org_rule_id`
- `rule_version_no`
- `resolver_version`
- `context_checksum`
- `resolution_status`
- `candidate_count_before_filter`
- `candidate_count_after_filter`
- `filter_summary`
- `fallback_triggered`
- `fallback_strategy_used`
- `snapshot_id`
- `elapsed_ms`

### 8.3 审计原则

- 审计记录引用 `process_id`、`node_id`、`org_rule_id`、`rule_version_no`，保证可回放到具体版本与上下文
- 审计保存证据摘要和过滤摘要，不保存完整候选人列表中每个主体的全部组织属性明细
- 解析失败的审计必须记录失败原因分类，支持后续按失败类型聚合分析
- 兜底触发的审计必须记录兜底前后的候选人变化，支持后续评估兜底策略合理性

### 8.4 关键指标

建议至少暴露以下指标：

- `ORG_RULE` 求值耗时分位值
- 各 `rule_type` 求值成功率
- 过滤器命中率（各过滤器移除候选人比例）
- 兜底触发率
- 空结果率
- 快照生成失败率

## 9. 与其他专项设计的边界

- 与 `workflow-dsl-and-validator-design` 的边界：本文只定义 `ORG_RULE` 在运行时如何被求值和生成快照；`ORG_RULE` 在 DSL 中如何被声明和校验由前者下沉。
- 与 `identity-access org-rule-resolution-design` 的边界：本文只定义流程引擎如何消费规则解析结果并生成参与人快照；规则语法、解析流程、版本治理和缓存失效机制由前者定义。
- 与 `parallel-and-countersign-aggregation-design` 的边界：本文只定义单节点求值与快照生成；并行与会签场景下多节点聚合、收敛判定由后者下沉。
- 与 `oa-bridge-mapping-and-compensation-design` 的边界：`OA` 主路径下的参与人映射由 `OA` 桥接层消费快照结果，不改变本文定义的求值与快照机制。

## 10. 本文结论

流程引擎对组织规则的消费行为应被收口为"组装上下文 + 调用解析 + 叠加过滤 + 冻结快照"的标准管道，而不是在流程引擎内部重建一套规则解释器。

通过消费侧规则字典、受控过滤语言、标准求值流程、不可变参与人快照和完整审计链路，流程引擎可以稳定消费 `identity-access` 的组织规则解析能力，同时保证运行时选人结果不因后续组织变更而漂移，审批争议可回放到具体规则版本与上下文。

这正是 `Detailed Design` 第 6 节"节点与组织架构绑定模型"中 `ORG_RULE` 绑定类型在实现层的正式下沉。

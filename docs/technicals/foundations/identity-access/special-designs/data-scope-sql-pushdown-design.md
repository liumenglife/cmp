# 组织架构 / 权限 / 统一认证主线专项设计：数据权限 SQL 下推

## 1. 文档说明

本文档用于继续下沉 `identity-access` 主线中“数据权限下推到具体查询仓储”的设计，重点回答以下问题：

- `ia_data_scope` 与 `AuthorizationDecision` 如何转换为查询仓储可消费的受控条件
- 如何避免业务模块或调用方拼接任意 `SQL`
- 不同 `resource_type` 的负责人、归属组织、状态字段如何映射
- 显式 `DENY`、`RULE` 范围、分页计数、缓存失效与审计如何统一处理

本文是以下文档的下游专项设计：

- [`identity-access Detailed Design`](../detailed-design.md)

本文不展开以下内容：

- 不写完整物理表 `DDL`、具体索引创建语句或数据库执行计划调优
- 不替代合同、文档、流程任务等业务模块的仓储设计
- 不定义前端列表筛选、查询表单、页面显隐或导出交互
- 不允许把数据权限表达为调用方传入的任意 `SQL` 片段

## 2. 设计目标

- 将数据权限从 `ia_data_scope` 和 `AuthorizationDecision` 稳定转换为受控查询条件
- 让合同主档、文档中心、流程引擎等业务仓储只消费标准化条件对象，不自行解释权限表
- 确保数据权限过滤、列表分页、总数统计、详情访问与导出前判定使用同一权限语义
- 支持 `SELF`、`ORG`、`ORG_UNIT`、`ORG_SUBTREE`、`USER_LIST`、`RULE` 六类范围语义
- 保证显式 `DENY` 优先、规则版本可回放、命中依据可审计、缓存失效可控

## 3. 适用范围与边界

本设计适用于所有需要把数据权限下推到查询仓储的资源：

- `CONTRACT`：合同列表、合同详情、合同检索、合同导出前筛选
- `DOCUMENT`：文档列表、文档详情、预览、下载前筛选
- `WORKFLOW_TASK`：待办、已办、流程任务查询
- `DECRYPT_GRANT`：解密下载授权配置与授权可见范围查询

统一边界如下：

- `data-scope-engine` 只输出数据范围结论和受控条件，不拥有业务资源本体
- 业务模块负责维护自身资源表、业务状态和字段真实含义
- 查询仓储只能消费结构化 `DataScopePredicate`，不得接收权限侧拼出的自由文本 `SQL`
- `AuthorizationDecision` 是一次判定冻结事实，不替代 `ia_data_scope` 原始配置
- 对列表类查询，权限下推是必经步骤；对详情类查询，可在资源定位后使用相同条件做命中校验

## 4. 核心对象

### 4.1 数据范围主表

`ia_data_scope` 是数据权限一等能力主表，关键字段包括：

- `subject_type`、`subject_id`：授权主体，可为 `USER`、`ROLE`、`ORG_UNIT`
- `resource_type`：资源类型，可为 `CONTRACT`、`DOCUMENT`、`WORKFLOW_TASK`、`DECRYPT_GRANT`
- `scope_type`：范围语义，可为 `SELF`、`ORG`、`ORG_UNIT`、`ORG_SUBTREE`、`USER_LIST`、`RULE`
- `scope_ref`：范围引用，按 `scope_type` 指向用户、组织单元、组织规则或受控集合
- `scope_status`、`priority_no`、`effect_mode`、`effective_start_at`、`effective_end_at`：生效、优先级和显式允许 / 拒绝控制

### 4.2 授权判定冻结对象

查询前若已经完成统一授权判定，应携带 `decision_id`。查询仓储不得直接信任 `decision_id` 本身，而应由 `authorization-engine` 读取：

- `ia_authorization_decision.decision_result`
- `ia_authorization_decision.data_scope_snapshot_checksum`
- `ia_authorization_hit_result` 中 `hit_type=DATA_SCOPE`、`hit_type=ORG_RULE` 的命中明细

当没有既有 `decision_id` 时，查询入口应先触发一次面向 `resource_type + action_code` 的数据权限判定，再把冻结后的条件对象交给仓储。

### 4.3 受控条件对象

数据权限下推的正式输出是 `DataScopePredicate`，而不是 `SQL` 字符串。最小结构如下：

```json
{
  "resource_type": "CONTRACT",
  "decision_id": "...",
  "effect": "ALLOW",
  "deny_predicates": [],
  "allow_predicates": [],
  "field_mapping_version": "1.0",
  "scope_snapshot_checksum": "..."
}
```

每个 `predicate` 只能使用平台预定义操作符：

- `EQ`
- `IN`
- `BETWEEN`
- `IS_NULL`
- `STARTS_WITH_PATH`
- `EXISTS_MEMBERSHIP`

不允许出现 `RAW_SQL`、任意函数名、调用方自定义列名或调用方自定义运算符。

## 5. 字段映射规则

### 5.1 字段映射职责

不同业务资源的字段名称由资源归属模块提供，但必须注册到 `identity-access` 可识别的受控映射表述中。映射对象为 `ResourceScopeFieldMapping`，最少包含：

- `resource_type`
- `owner_user_field`
- `handler_user_field`
- `owner_org_field`
- `org_path_field`
- `status_field`
- `deleted_flag_field`
- `tenant_or_org_field`
- `mapping_version`

业务模块只能注册字段映射，不能注册任意表达式。

### 5.2 `resource_type` 映射

当前正式映射语义如下：

| `resource_type` | 负责人字段语义 | 组织字段语义 | 状态字段语义 |
| --- | --- | --- | --- |
| `CONTRACT` | 合同创建人、经办人、负责人引用 `ia_user.user_id` | 合同归属部门引用 `ia_org_unit.org_unit_id` | 合同生命周期状态由合同主档维护 |
| `DOCUMENT` | 文档上传人、文档责任人引用 `ia_user.user_id` | 文档归属部门或关联合同归属部门引用 `ia_org_unit.org_unit_id` | 文档可用、归档、作废等状态由文档中心维护 |
| `WORKFLOW_TASK` | 发起人、当前处理人、候选处理人引用 `ia_user.user_id` | 发起部门、任务归属部门引用 `ia_org_unit.org_unit_id` | 任务待办、已办、取消、终止状态由流程引擎维护 |
| `DECRYPT_GRANT` | 授权创建人、授权对象为用户时引用 `ia_user.user_id` | 授权对象为部门时引用 `ia_org_unit.org_unit_id` | 授权生效、撤销、过期状态由 `ia_permission_grant` 与授权配置视图承接 |

统一约束：

- `owner_user_field` 与 `owner_org_field` 必须能回到 `ia_user`、`ia_org_unit`
- 状态字段只用于排除业务不可见对象，不得替代权限判定
- `deleted_flag_field` 必须由仓储层统一追加，避免已删除对象绕过权限条件

## 6. 下推主链路

数据权限下推固定为以下 9 步：

1. 查询入口传入 `subject_user_id`、`active_org_id`、`active_org_unit_id`、`resource_type`、`action_code` 与业务查询条件
2. `authorization-engine` 校验主体、会话和功能权限
3. `data-scope-engine` 读取主体直授、角色授予、组织单元授予对应的 `ia_data_scope`
4. 过滤 `scope_status`、有效期、资源类型和组织上下文
5. 按 `effect_mode`、`priority_no` 聚合 `DENY` 与 `ALLOW` 范围
6. 若包含 `RULE`，通过 `org-rule-resolver` 使用冻结规则版本生成解析记录
7. 写入 `ia_authorization_decision` 与 `ia_authorization_hit_result`
8. 生成 `DataScopePredicate` 并交给业务仓储
9. 业务仓储按受控字段映射编译为参数化查询条件，同时用于列表数据查询与总数统计

第 7 步是权限事实冻结点；第 9 步只是仓储编译，不得产生新的权限语义。

## 7. 范围语义转换规则

### 7.1 `SELF`

`SELF` 转换为受控用户字段命中：

- `owner_user_field = subject_user_id`
- 或 `handler_user_field = subject_user_id`
- 对 `WORKFLOW_TASK` 可扩展为候选处理人关系命中，但必须由流程引擎注册受控关系映射

### 7.2 `ORG`

`ORG` 转换为当前组织边界命中：

- `tenant_or_org_field = active_org_id`
- 同时叠加资源有效状态与删除标记过滤

`ORG` 不能跨顶层组织扩展，也不能被业务查询条件覆盖。

### 7.3 `ORG_UNIT`

`ORG_UNIT` 转换为指定部门命中：

- `owner_org_field IN (:org_unit_ids)`

其中 `org_unit_ids` 来自 `scope_ref` 或授权上下文，必须先校验这些部门属于当前 `active_org_id`。

### 7.4 `ORG_SUBTREE`

`ORG_SUBTREE` 转换为组织树路径命中：

- 优先使用 `org_path_field STARTS_WITH_PATH :org_path`
- 若资源侧不保存路径字段，则由仓储使用平台受控的部门子树集合参数，不允许拼接递归 `SQL`

组织树路径来自 `ia_org_unit.org_path`，组织树重建或部门移动后必须触发相关权限缓存失效。

### 7.5 `USER_LIST`

`USER_LIST` 转换为用户集合命中：

- `owner_user_field IN (:user_ids)`
- 或资源映射声明的受控参与人字段命中

`scope_ref` 必须解析为受控用户集合，不允许直接把逗号字符串拼入查询文本。

### 7.6 `RULE`

`RULE` 必须依赖组织规则冻结版本和正式解析记录：

- 保存或生效数据范围配置时，应把可被引用的规则固化为 `ia_org_rule_version`
- 运行期解析必须写 `ia_org_rule_resolution_record`
- `ia_authorization_hit_result.hit_type=ORG_RULE` 时，必须同时保存 `frozen_ref_id=org_rule_version_id` 与 `resolution_record_id`
- 解析结果再转换为 `USER_LIST`、`ORG_UNIT` 或 `ORG_SUBTREE` 等受控条件

不得在查询仓储中直接读取 `ia_org_rule.resolver_config` 当前主档进行即时解释。

## 8. 显式 DENY 与优先级

显式 `DENY` 高于任何 `ALLOW`，下推规则如下：

- `effect_mode=DENY` 的 `ia_data_scope` 先生成 `deny_predicates`
- `effect_mode=ALLOW` 的 `ia_data_scope` 生成 `allow_predicates`
- 最终条件等价于“命中任一 `ALLOW` 且不命中任何 `DENY`”
- 当只存在 `DENY`、不存在有效 `ALLOW` 时，最终结果为拒绝，不生成全量可见条件
- 同一资源下 `priority_no` 用于解释命中依据和冲突排序，但不能让低优先级 `ALLOW` 覆盖显式 `DENY`

`ia_authorization_hit_result` 必须记录每条 `DENY` 命中依据，方便管理端解释“为什么不可见”。

## 9. 分页与计数一致性

列表数据查询与总数统计必须使用同一份 `DataScopePredicate`：

- 查询页数据和 `count` 不能分别触发两次权限求值
- 同一次请求内的 `decision_id`、`scope_snapshot_checksum`、字段映射版本必须一致
- 排序、关键字、业务状态筛选可叠加在权限条件之后，但不得删除权限条件
- 对游标分页，游标条件必须与权限条件同时参与查询，不能先取全局游标再做内存过滤

若权限配置在分页过程中发生变化：

- 同一请求使用已冻结的 `decision_id` 保持结果解释一致
- 下一次请求必须重新判定或校验缓存是否仍有效
- 对高敏导出，不复用过期的列表 `decision_id`，应重新执行导出动作级授权判定

## 10. 缓存、失效与并发

### 10.1 缓存对象

允许缓存的是结构化权限摘要和 `DataScopePredicate`，不是最终 `SQL` 文本。缓存键至少包含：

- `subject_user_id`
- `active_org_id`
- `active_org_unit_id`
- `resource_type`
- `action_code`
- `permission_snapshot_checksum`
- `data_scope_snapshot_checksum`
- `field_mapping_version`

### 10.2 失效触发

发生以下变更时必须失效相关缓存：

- `ia_user.user_status`、`ia_org_membership`、`ia_org_unit` 组织树或负责人变化
- `ia_role`、`ia_role_assignment`、`ia_permission_grant` 变化
- `ia_data_scope` 新增、撤销、禁用、有效期变化或 `effect_mode` 变化
- `ia_org_rule_version` 生效、组织规则解析依赖的组织关系变化
- 资源字段映射版本变化

### 10.3 并发约束

- 授权判定结果只追加写，不覆盖旧 `ia_authorization_decision`
- 缓存失效失败时，以 `MySQL` 真相为准，并通过异步任务补偿重建
- 多个相同查询并发进入时，可按 `subject + resource_type + action_code` 使用短期去重，但不得共享不同组织上下文的权限结果

## 11. 审计与恢复

每次正式数据权限判定至少记录：

- `decision_id`
- `subject_user_id`
- `active_org_id`
- `active_org_unit_id`
- `resource_type`
- `action_code`
- 命中的 `data_scope_id`
- 命中的 `org_rule_version_id` 与 `org_rule_resolution_record_id`
- `decision_result`
- `data_scope_snapshot_checksum`
- `request_trace_id`

审计事件写入 `ia_identity_audit_event`，事件类型使用 `DATA_SCOPE_HIT` 或 `AUTHZ_DENIED`。恢复时可重算当前权限结果，但历史解释必须优先读取 `ia_authorization_decision`、`ia_authorization_hit_result` 与当时的规则解析记录。

## 12. 与其他主线的挂接

### 12.1 与合同主档

合同主档注册 `CONTRACT` 字段映射，列表、详情、导出前查询都必须消费 `DataScopePredicate`。合同主档不得复制 `ia_data_scope` 或自建角色范围表。

### 12.2 与文档中心

文档中心注册 `DOCUMENT` 字段映射。文档访问、预览、下载、批注等动作先经授权判定，再由文档仓储使用相同权限条件筛选目标文档。

### 12.3 与流程引擎

流程引擎注册 `WORKFLOW_TASK` 字段映射。待办、已办、候选任务查询可使用受控参与人映射，但候选人规则解释仍回到 `org-rule-resolver`。

### 12.4 与加密文档主线

加密文档主线执行解密下载前必须接收 `decision_id` 或等价授权命中凭据。数据权限下推只证明目标资源是否在可接触范围内，不生成明文文件、不管理导出作业。

## 13. 本文结论

数据权限下推必须以 `ia_data_scope`、`ia_authorization_decision`、`ia_authorization_hit_result`、`ia_org_rule_version`、`ia_org_rule_resolution_record` 为正式依据，输出结构化 `DataScopePredicate`，再由业务仓储按受控字段映射编译为参数化查询条件。

通过显式 `DENY` 优先、`RULE` 范围冻结、字段映射白名单、分页计数共用同一判定快照、缓存按权限真相失效和审计全链路留痕，可以避免任意 `SQL` 拼接和各业务模块私建数据权限体系。

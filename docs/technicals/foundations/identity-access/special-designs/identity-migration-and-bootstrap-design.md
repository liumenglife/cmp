# 组织架构 / 权限 / 统一认证主线专项设计：身份迁移与初始化

## 1. 文档说明

本文档用于继续下沉 `identity-access` 主线中“组织同步、权限初始化、历史账号并表迁移”的设计，重点回答以下问题：

- 组织同步、权限初始化、历史账号并表迁移脚本的设计边界是什么
- 初始化顺序、幂等键、冲突冻结和账号归并如何定义
- 组织树如何重建，角色和数据权限如何初始化
- 失败后的回滚、补偿、审计和验收核对如何闭环

本文是以下文档的下游专项设计：

- [`identity-access Detailed Design`](../detailed-design.md)

本文不展开以下内容：

- 不写具体迁移脚本代码、命令行参数、`DDL` 或批处理实现
- 不定义实施排期、负责人、上线窗口或割接会议安排
- 不替代外部目录系统、企业微信、`SSO`、`LDAP` 的接入协议设计
- 不把历史系统字段全集搬入平台主体真相

## 2. 设计目标

- 建立可重复执行、可审计、可暂停恢复的身份与组织初始化流程
- 将历史账号归并到平台正式 `ia_user`，避免业务模块继续持有多套用户真相
- 将组织树、组织成员、角色、特殊授权、数据权限按稳定顺序初始化
- 对身份冲突、组织冲突、授权冲突进行冻结处理，不做静默覆盖
- 为后续恢复、核对、补偿和审计提供稳定对象与证据链

## 3. 适用范围与边界

本设计覆盖三类初始化 / 迁移动作：

- 组织同步：从正式来源承接组织单元、成员关系、负责人、组织树路径
- 权限初始化：建立系统角色、基础菜单 / 功能 / 特殊授权、初始数据权限
- 历史账号并表迁移：把存量账号、外部身份绑定、历史经办人引用归并到 `ia_user`

统一边界如下：

- 初始化脚本只能写入 `identity-access` 正式对象或经业务模块确认的映射投影
- 组织、角色、权限、数据权限真相以 `ia_user`、`ia_org_unit`、`ia_role`、`ia_permission_grant`、`ia_data_scope` 为准
- 历史系统字段只作为迁移输入和审计证据，不成为新平台长期主键
- 冲突记录必须冻结并等待人工处理，不允许自动覆盖已有主体、组织或授权
- 业务模块历史数据引用的重写必须通过映射关系执行，不允许直接按姓名、手机号临时匹配

## 4. 核心对象

### 4.1 主体与身份绑定

- `ia_user`：平台正式用户主体
- `ia_identity_binding`：外部身份、历史账号与平台主体的绑定关系
- `ia_protocol_exchange`、`ia_identity_binding_precheck`、`ia_identity_manual_disposition`：外部身份进入与冲突处理的正式记录

迁移过程不得把外部账号主键直接作为 `ia_user.user_id`。

### 4.2 组织与成员

- `ia_org_unit`：组织树节点
- `ia_org_membership`：主体与组织单元关系

组织同步只维护组织真相，不直接授予业务权限。部门负责人、成员归属、组织路径变化会触发权限摘要和规则解析缓存失效。

### 4.3 角色与授权

- `ia_role`：角色定义
- `ia_role_assignment`：角色授予关系
- `ia_permission_grant`：菜单、功能、特殊授权
- `ia_data_scope`：数据权限范围

初始化角色和授权时，必须区分系统内置角色、业务角色、临时授权和显式拒绝，不把数据范围塞入功能权限字段。

### 4.4 规则、判定与审计

- `ia_org_rule_version`、`ia_org_rule_resolution_record`：组织规则版本与解析记录
- `ia_authorization_decision`、`ia_authorization_hit_result`：初始化后授权判定抽检和回放依据
- `ia_identity_audit_event`：迁移、冻结、归并、授权初始化的追加式审计事实

## 5. 初始化顺序

初始化按以下顺序执行，后一步不得绕过前一步的验收结果：

1. 建立迁移批次与输入快照
2. 导入或校验顶层组织 `org_id`
3. 初始化 `ia_org_unit` 组织树节点
4. 重建 `org_path`、`path_depth`、父子关系和负责人引用占位
5. 初始化 `ia_user` 主体候选
6. 写入 `ia_identity_binding` 与历史账号映射
7. 写入 `ia_org_membership` 成员关系、主部门、兼职部门、负责人关系
8. 初始化 `ia_role`
9. 初始化 `ia_role_assignment`
10. 初始化 `ia_permission_grant`
11. 初始化 `ia_data_scope`
12. 预校验组织规则并冻结可被引用的 `ia_org_rule_version`
13. 重建会话、权限摘要和组织树缓存
14. 执行授权判定抽检、数量核对和审计核对

顺序约束：

- 没有 `ia_user` 与 `ia_org_unit`，不得初始化成员关系和授权
- 没有 `ia_role`，不得初始化角色授予和角色级权限
- 没有数据权限，不得把列表可见范围默认为全量
- 组织规则必须先预校验，再允许被流程、授权或数据范围正式引用

## 6. 幂等键设计

### 6.1 批次幂等

每次迁移或初始化运行必须有 `migration_batch_id`。批次幂等键最少包含：

- `source_system`
- `source_snapshot_id`
- `migration_phase`
- `input_checksum`

同一批次重复执行时，只能复用既有结果或补偿未完成步骤，不得创建重复主体、组织或授权。

### 6.2 对象幂等

对象级幂等键如下：

| 对象 | 幂等键 |
| --- | --- |
| `ia_user` | `user_no` 或经确认的历史主体映射键 |
| `ia_identity_binding` | `provider + external_identity_key` |
| `ia_org_unit` | `org_id + org_unit_code` |
| `ia_org_membership` | `user_id + org_unit_id + membership_type` |
| `ia_role` | `role_code` |
| `ia_role_assignment` | `role_id + subject_type + subject_id + grant_org_id` |
| `ia_permission_grant` | `grant_target_type + grant_target_id + permission_type + permission_code + resource_type` |
| `ia_data_scope` | `subject_type + subject_id + resource_type + scope_type + scope_ref` |

幂等命中后，应校验关键字段是否一致；不一致时进入冲突冻结，不得静默覆盖。

## 7. 冲突冻结规则

### 7.1 身份冲突

以下情况必须冻结：

- 同一 `provider + external_identity_key` 指向多个候选 `ia_user`
- 同一历史账号同时命中多个手机号、邮箱或工号候选
- 外部身份已绑定到活动主体，但本次迁移试图绑定到其他主体
- 历史账号标识相同但姓名、部门、状态等关键证据不一致

冻结后写入 `ia_identity_binding.binding_status=CONFLICT`，必要时写 `ia_identity_binding_precheck` 和 `ia_identity_manual_disposition`，并追加 `ia_identity_audit_event`。

### 7.2 组织冲突

以下情况必须冻结组织节点：

- 同一 `org_unit_code` 在同一 `org_id` 下出现不同父节点
- 同一父节点下出现同名但来源标识不同的部门
- 组织移动会造成环路或跨顶层组织挂接
- 部门负责人指向不存在、禁用或冲突冻结的主体

冻结组织不得参与新授权初始化；已依赖该组织的权限摘要应标记为待重建。

### 7.3 授权冲突

以下情况必须冻结授权：

- 同一主体、资源和权限码同时出现互斥 `ALLOW` 与 `DENY`，且无明确优先级
- 数据范围引用不存在的组织、用户或规则
- `RULE` 范围引用未通过预校验的组织规则
- 临时授权缺失有效期但来源声明为临时

冻结授权不得进入 `ACTIVE` 状态，也不得被缓存展开。

## 8. 账号归并规则

账号归并目标是让同一自然人在平台内只有一个活动 `ia_user`。

归并候选证据按优先级使用：

1. 已确认的历史主键映射
2. `provider + external_identity_key`
3. 工号或平台内部 `user_no`
4. 手机号、邮箱、姓名、部门组合证据

归并规则：

- 高置信唯一候选可归并到保留主体
- 多候选或关键证据冲突必须冻结
- 被归并主体设置 `user_status=MERGED`
- `merged_into_user_id` 指向保留主体
- 新登录、授权和组织成员关系只能落到保留主体
- 历史审计和历史业务引用保留原始证据，并通过映射关系解释

账号归并不得物理删除原主体，不得把多个活动主体长期保留为同一自然人。

## 9. 组织树重建规则

组织树重建按受控步骤执行：

1. 校验所有节点的 `org_unit_code`、父节点引用和顶层 `org_id`
2. 检查环路、孤儿节点、跨组织挂接
3. 计算 `org_path` 与 `path_depth`
4. 写入或更新 `ia_org_unit`
5. 写入 `ia_org_membership`
6. 校验负责人 `manager_user_id`
7. 发布组织树只读快照
8. 触发权限、组织规则和数据范围缓存失效

组织树重建原则：

- 正式父子关系以 `parent_org_unit_id` 为准，`org_path` 是加速字段
- 组织移动必须触发 `ORG_SUBTREE` 数据权限和组织规则解析缓存失效
- 禁用部门不物理删除，历史授权和审计仍可回放
- 被禁用部门不得继续承接新的部门授权或成员主部门

## 10. 角色与数据权限初始化

### 10.1 角色初始化

角色初始化先创建 `ia_role`，再创建 `ia_role_assignment`：

- 系统角色使用稳定 `role_code`
- 业务角色按当前组织或业务域配置
- 角色继承只使用 `inherits_role_id` 的简单单继承
- 角色授予到部门时，不预先物化到所有成员

初始化后必须抽检角色展开结果，确认用户直授、部门授予和角色继承不会产生重复或越权。

### 10.2 权限初始化

菜单、功能、特殊授权统一写入 `ia_permission_grant`：

- 菜单权限只控制入口可见
- 功能权限控制动作可执行
- 特殊授权控制高敏能力，例如 `SPECIAL:DECRYPT_DOWNLOAD`
- 显式 `DENY` 必须作为正式授权记录，而不是只写备注或黑名单文件

### 10.3 数据权限初始化

数据权限统一写入 `ia_data_scope`：

- 普通部门数据范围使用 `ORG_UNIT` 或 `ORG_SUBTREE`
- 个人经办范围使用 `SELF`
- 固定人员集合使用 `USER_LIST`
- 动态组织规则范围使用 `RULE`
- 全组织范围使用 `ORG`，必须审慎授予并留痕

初始化 `RULE` 范围前必须完成组织规则预校验，并确保可冻结 `ia_org_rule_version`。数据权限初始化完成后，应使用 `authorization-engine` 对代表用户执行抽检并写入判定结果。

## 11. 回滚、补偿与恢复

### 11.1 回滚边界

迁移和初始化不以物理删除作为默认回滚方式。回滚优先使用状态反转和补偿记录：

- 新增但未发布的组织、角色、授权可标记为 `DISABLED`、`REVOKED` 或等价状态
- 已被业务引用的 `ia_user`、`ia_org_unit` 不物理删除
- 已写审计事件不删除，只追加回滚事件
- 已冻结的 `AuthorizationDecision` 不改写，只在新请求中重新判定

### 11.2 补偿策略

常见补偿如下：

- 用户已创建但绑定失败：补偿创建 `ia_identity_binding` 或冻结该主体
- 组织节点已创建但路径重建失败：冻结组织树发布，修正后重建 `org_path`
- 成员关系已写入但角色展开失败：保留成员关系，重建权限摘要
- 授权已写入但缓存失效失败：以数据库真相为准，异步重建缓存
- 审计写入失败：保留业务结果，通过审计补偿任务追加恢复事件

### 11.3 恢复顺序

恢复时按以下顺序读取真相：

1. `ia_user`、`ia_identity_binding`
2. `ia_org_unit`、`ia_org_membership`
3. `ia_role`、`ia_role_assignment`
4. `ia_permission_grant`、`ia_data_scope`
5. `ia_org_rule_version`、`ia_org_rule_resolution_record`
6. `ia_authorization_decision`、`ia_authorization_hit_result`
7. `ia_identity_audit_event`

缓存、只读投影、权限摘要和组织树快照均可重建，不作为恢复真相源。

## 12. 审计与验收核对

### 12.1 审计事件

迁移和初始化必须记录以下事件：

- 用户创建、主体归并、主体冻结
- 外部身份绑定创建、冲突冻结、人工处置
- 组织创建、移动、禁用、负责人变更
- 角色创建、授予、撤销
- 权限授予、撤销、显式拒绝
- 数据权限初始化、范围扩大、范围拒绝
- 组织规则预校验、规则版本冻结

审计统一写入 `ia_identity_audit_event`，并携带 `migration_batch_id`、`trace_id`、操作者、目标对象、前后状态摘要和输入快照引用。

### 12.2 验收核对

初始化完成后至少核对：

- 输入组织节点数与 `ia_org_unit` 有效节点数
- 输入成员关系数与 `ia_org_membership` 有效关系数
- 历史账号数、归并数、冻结冲突数、活动 `ia_user` 数
- 外部身份绑定数与冲突绑定数
- 角色定义数、角色授予数、权限授予数
- `ia_data_scope` 中各 `resource_type`、`scope_type` 分布
- 显式 `DENY` 数量及命中抽检结果
- 代表用户的菜单、功能、数据权限、解密下载授权抽检结果
- 审计事件数量与迁移批次步骤数量是否匹配

验收核对不得只看脚本成功退出码，必须以数据库真相、冻结冲突清单和授权抽检结果为准。

## 13. 与其他主线的挂接

### 13.1 与集成主线

集成主线负责外部连接器、目录同步传输、通用验签和适配器运行时。本主线负责把同步结果落到主体、组织、绑定和授权真相，并处理冲突冻结。

### 13.2 与流程引擎

流程引擎引用 `ia_org_unit`、`ia_user`、`ia_org_rule_version`。迁移完成前，不允许发布依赖未校验组织规则的流程定义。

### 13.3 与合同主档和文档中心

历史合同、文档中的创建人、经办人、归属部门、上传人等引用，应通过迁移映射回写到 `user_id` 和 `org_unit_id`。业务模块不保留历史账号字段作为新权限判断依据。

### 13.4 与审计中心

本主线先写 `ia_identity_audit_event`，审计中心按 `migration_batch_id`、`trace_id`、`user_id`、`org_unit_id` 聚合展示迁移过程和冲突处理结果。

## 14. 本文结论

身份迁移与初始化必须以可重复执行、可冻结冲突、可审计恢复为核心原则。组织同步、权限初始化和历史账号并表迁移都应围绕 `ia_user`、`ia_identity_binding`、`ia_org_unit`、`ia_org_membership`、`ia_role`、`ia_role_assignment`、`ia_permission_grant`、`ia_data_scope` 建立正式真相。

通过明确初始化顺序、对象级幂等键、冲突冻结、账号归并、组织树重建、角色 / 数据权限初始化、状态化回滚和验收核对，可以避免迁移脚本把历史脏数据静默带入平台权限体系。

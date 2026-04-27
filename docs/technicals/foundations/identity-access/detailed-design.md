# 组织架构 / 权限 / 统一认证主线 Detailed Design

## 1. 文档说明

本文档是组织架构 / 权限 / 统一认证主线的第一份正式
`Detailed Design`。

本文在以下输入文档约束下，继续把本主线下沉到内部实现层：

- 需求基线：[`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- 总平台架构：[`Architecture Design`](../../architecture-design.md)
- 总平台接口边界：[`API Design`](../../api-design.md)
- 总平台共享内部边界：[`Detailed Design`](../../detailed-design.md)
- 本主线架构边界：[`Architecture Design`](./architecture-design.md)
- 本主线接口边界：[`API Design`](./api-design.md)
- 关联主线内部实现：[`workflow-engine Detailed Design`](../../modules/workflow-engine/detailed-design.md)
- 关联主线内部实现：[`document-center Detailed Design`](../../modules/document-center/detailed-design.md)
- 关联主线内部实现：[`encrypted-document Detailed Design`](../../modules/encrypted-document/detailed-design.md)

本文输出以下内容：

- 本主线内部模块拆分与职责边界
- 核心物理表、关键字段、索引与关联对象
- 统一身份、组织、角色、权限、数据权限的内部模型
- 身份映射、组织解析、授权判定、解密下载授权命中的内部模型
- 与流程引擎、合同主档、文档中心、加密下载授权、通知、审计、集成主线的内部挂接方式
- 缓存、锁、幂等、并发控制、异步任务、补偿恢复与观测边界

本文只回答“平台内唯一的身份与授权真相如何实现并稳定运行”，不展开以下内容：

- 不复述需求范围、业务背景、一期范围或验收条目
- 不重写总平台架构总览、拓扑图或模块能力总览
- 不重写对外 `API` 资源清单、报文字段、错误码全集
- 不写实施排期、任务拆分、联调顺序或负责人安排
- 不把组织规则表达式、前端权限树交互、认证协议报文字段细化到代码级协议

## 2. 设计目标与约束落点

### 2.1 设计目标

- 平台只存在一套正式的用户、组织、角色、权限真相
- 统一身份、组织、角色、菜单权限、功能权限、数据权限都以本主线为唯一内部真相源
- 审批节点必须绑定部门、人员或组织规则，流程引擎只能消费本主线的解析结果
- 管理端可按部门、人员做解密下载授权，但授权边界仍回到本主线统一解释
- `SSO`、`LDAP`、企业微信只影响身份进入平台和映射过程，不改写平台主体真相
- 数据权限是正式一等能力，不能退化为列表页私有过滤条件
- 业务模块不得各自维护一套用户、组织、权限真相或长期缓存真相

### 2.2 约束落点

- `MySQL` 承担本主线正式真相；`Redis` 只做增强层
- 角色、授权、组织关系、授权命中结果都必须可审计、可恢复、可重算
- 身份映射冲突不能自动静默覆盖，必须进入待处理状态与审计链路
- 多组织归属是正式能力，主体与组织成员关系分离建模
- 授权判定必须同时考虑主体状态、组织上下文、角色授权、显式授权、数据范围和资源上下文
- 授权缓存失效不影响数据库真相恢复，命中结果允许重算

## 3. 模块内部拆分

内部实现按九个组件拆分，统一围绕 `user_id`、`org_unit_id`、`role_id`、
`permission_grant_id`、`decision_id` 运行。

### 3.1 `identity-registry`

- 持有平台正式 `User` 主体真相
- 统一管理主体状态、默认组织、显示信息、平台登录入口属性
- 不直接承接外部身份源字段全集，只保留平台主体需要的稳定字段

### 3.2 `identity-binding-hub`

- 承接 `SSO`、`LDAP`、企业微信与本地账号的身份绑定关系
- 负责同一自然人的主体归并、冲突冻结、最近验证结果与解绑留痕
- 外部身份只在此处落映射，不直接落入业务模块账号字段

### 3.3 `org-directory`

- 持有组织单元、组织树、成员挂接、主部门、兼职部门、管理链摘要
- 负责组织解析、上级链查找、祖先路径维护、组织规则基础输入
- 不拥有流程实例、合同数据、消息任务等业务真相

### 3.4 `role-permission-center`

- 持有角色定义、角色授予、菜单权限、功能权限、特殊受控授权
- 负责角色继承展开、角色有效性校验和授权撤销传播
- 菜单权限与功能权限在同一授权中心解释，但保持独立资源类型

### 3.5 `data-scope-engine`

- 持有数据权限范围、范围引用、范围优先级与生效状态
- 负责把组织范围、人员范围、管理范围解释为可计算的数据裁剪条件
- 仅输出数据范围结论，不拥有合同、文档、流程等业务资源本体

### 3.6 `org-rule-resolver`

- 持有组织规则主档、规则版本、规则摘要和解析快照
- 把“部门负责人”“发起人上级”“部门内指定角色”“组织树某层负责人”等规则
  解析为主体集合
- 规则解析结果服务流程引擎、通知、授权判定与管理授权命中
- 对任何已发布、已生效、已写入持久化对象的规则引用，统一输出不可变版本承接对象，
  不允许消费方只依赖 `ia_org_rule.version_no` 这一当前主档口径

### 3.7 `authorization-engine`

- 统一承接菜单、功能、数据、特殊授权的判定
- 输出一次可追踪的 `AuthorizationDecision`
- 负责解密下载授权命中结果冻结、授权依据摘要和拒绝原因生成
- 当命中依据涉及组织规则时，必须同步冻结规则版本引用与本次解析记录引用

### 3.8 `session-context-cache`

- 管理当前登录会话快照、主体上下文、权限摘要缓存、组织切换态
- 只保存短期上下文与已展开摘要，不保存正式角色授权真相
- 为高频 `GET /me`、菜单树、待办列表授权判定提供读加速

### 3.9 `identity-audit-observability`

- 统一记录登录、映射、组织变更、角色授权、数据权限命中、越权拒绝、
  解密下载授权命中等关键事实
- 输出模块级审计事实、日志关联字段和指标
- 不替代总平台审计中心，只保留本主线所需的高颗粒度追加事实

## 4. 核心物理表设计

本节只列本主线主表与必要支撑表，不展开完整 `DDL`。
全部表默认包含基础审计字段：`created_at`、`created_by`、`updated_at`、
`updated_by`、`is_deleted`、`row_version`。

### 4.1 `ia_user`

用途：平台正式用户主体主表，是平台唯一用户真相源。

- 关键主键：`user_id`
- 关键字段：
  - `user_no`：平台内部稳定编号
  - `login_name`：本地登录名，可为空但全局唯一
  - `display_name`
  - `user_status`：`PENDING_ACTIVATION`、`ACTIVE`、`LOCKED`、`DISABLED`、`MERGED`
  - `default_org_id`
  - `default_org_unit_id`
  - `primary_email`、`primary_mobile`
  - `source_preference`：默认登录来源偏好
  - `merged_into_user_id`：主体归并后指向保留主体
  - `last_login_at`
- 关键索引 / 唯一约束：
  - `uk_user_no(user_no)`
  - `uk_login_name(login_name)`
  - `idx_default_org(default_org_id, user_status)`
  - `idx_merge_target(merged_into_user_id)`
- 关联对象：
  - 关联 `ia_identity_binding`
  - 关联 `ia_role_assignment`
  - 关联 `ia_org_membership`
  - 被合同主档、流程引擎、通知、审计引用为统一主体

设计说明：

- 平台只允许一个正式 `User` 主体标识，不把外部账号直接当作平台用户主键
- 主体归并保留历史留痕，不物理删除原主体

### 4.2 `ia_identity_binding`

用途：外部身份与平台主体绑定表，承接统一认证与主体归一。

- 关键主键：`binding_id`
- 关键字段：
  - `provider`：`LOCAL`、`SSO`、`LDAP`、`WECOM`
  - `external_identity_key`：来源内稳定身份键
  - `external_login_name`
  - `external_org_ref`
  - `user_id`
  - `binding_status`：`PENDING`、`ACTIVE`、`CONFLICT`、`UNLINKED`、`REVOKED`
  - `identity_fingerprint`
  - `trust_level`
  - `last_verified_at`
  - `last_login_at`
  - `conflict_group_id`
  - `provider_payload_ref`
- 关键索引 / 唯一约束：
  - `uk_provider_identity(provider, external_identity_key)`
  - `idx_user_binding(user_id, binding_status)`
  - `idx_conflict_group(conflict_group_id, binding_status)`
  - `idx_last_verified(provider, last_verified_at)`
- 关联对象：
  - 关联 `ia_user`
  - 关联 `ia_identity_session_snapshot`
  - 关联 `ia_identity_audit_event`

设计说明：

- 外部身份源影响映射，但不改写 `ia_user` 的平台主体真相
- 冲突状态必须冻结并转入人工处理，不允许自动覆盖已有绑定

### 4.3 `ia_protocol_exchange`

用途：外部身份协议交换主表，正式承接一次外部身份进入平台时的协议受理、可信校验、幂等去重与冻结状态。

- 关键主键：`protocol_exchange_id`
- 关键字段：
  - `provider`：`LOCAL`、`SSO`、`LDAP`、`WECOM`
  - `provider_tenant_ref`
  - `provider_app_ref`
  - `entry_mode`
  - `protocol_message_key`
  - `credential_digest`
  - `idempotency_key`
  - `verification_result`：`TRUSTED`、`REJECTED_SIGNATURE`、`REJECTED_TICKET`、`REJECTED_REPLAY`、`REJECTED_CONTEXT_MISMATCH`、`WAIT_MANUAL_REVIEW`
  - `exchange_status`：`RECEIVED`、`VERIFIED`、`PRECHECKED`、`FROZEN`、`REJECTED`、`SESSION_ALLOWED`、`SESSION_BLOCKED`
  - `retry_policy_status`：`RETRYABLE`、`FROZEN_NO_RETRY`、`MANUAL_ONLY`、`TERMINATED`
  - `raw_payload_ref`
  - `verification_evidence_ref`
  - `last_precheck_id`
  - `final_disposition_id`
  - `occurred_at`
- 关键索引 / 唯一约束：
  - `uk_exchange_idem(provider, provider_tenant_ref, idempotency_key)`
  - `uk_protocol_message(provider, provider_tenant_ref, protocol_message_key, credential_digest)`
  - `idx_exchange_status(exchange_status, occurred_at)`
  - `idx_retry_policy(retry_policy_status, exchange_status)`
- 关联对象：
  - 关联 `ia_identity_binding_precheck`
  - 关联 `ia_identity_manual_disposition`
  - 关联 `ia_identity_audit_event`

设计说明：

- `protocol_exchange_id` 是外部身份协议治理的正式主键；重复回调、重试和人工介入都围绕该对象承接
- 一旦进入 `FROZEN`、`MANUAL_ONLY` 或 `TERMINATED`，后续自动重试只能复用既有交换记录，不允许生成绕过冻结语义的新成功记录

### 4.4 `ia_identity_binding_precheck`

用途：外部身份绑定预检查主表，正式承接身份指纹归一、绑定查找、冲突冻结与是否允许换会话的判断。

- 关键主键：`precheck_id`
- 关键字段：
  - `protocol_exchange_id`
  - `provider`
  - `external_identity_key`
  - `identity_fingerprint`
  - `matched_binding_id`
  - `matched_user_id`
  - `conflict_group_id`
  - `precheck_result`：`PASS`、`CONFLICT`、`WAIT_MANUAL`、`REJECTED`
  - `session_gate_result`：`ALLOW`、`BLOCKED`、`MANUAL_REQUIRED`
  - `result_snapshot`
  - `evaluated_at`
- 关键索引 / 唯一约束：
  - `idx_exchange(protocol_exchange_id, evaluated_at)`
  - `idx_fingerprint(identity_fingerprint, precheck_result)`
  - `idx_conflict_group(conflict_group_id, precheck_result)`
- 关联对象：
  - 关联 `ia_protocol_exchange`
  - 关联 `ia_identity_binding`
  - 关联 `ia_user`

设计说明：

- 预检查是正式记录，不是临时内存判断；恢复、回放、人工处置都以本表为准
- 是否允许建立平台会话，以本表的 `session_gate_result` 为正式闸门，不以即时内存判断替代

### 4.5 `ia_identity_manual_disposition`

用途：外部身份人工处置历史表，正式承接冻结、解冻、解绑、重绑、撤销等人工决定与理由。

- 关键主键：`disposition_id`
- 关键字段：
  - `protocol_exchange_id`
  - `precheck_id`
  - `binding_id`
  - `disposition_action`：`FREEZE`、`UNFREEZE`、`RELINK`、`UNLINK`、`REVOKE`、`RETRY_APPROVED`
  - `before_status_snapshot`
  - `after_status_snapshot`
  - `operator_id`
  - `disposition_reason`
  - `evidence_ref`
  - `disposed_at`
- 关键索引 / 唯一约束：
  - `idx_exchange_disposed(protocol_exchange_id, disposed_at)`
  - `idx_binding_disposed(binding_id, disposed_at)`
  - `idx_operator(operator_id, disposed_at)`
- 关联对象：
  - 关联 `ia_protocol_exchange`
  - 关联 `ia_identity_binding_precheck`
  - 关联 `ia_identity_binding`

设计说明：

- 人工处置历史是正式承接对象，不能只留在审计描述或工单备注里
- 后续自动重试、再次登录或重新绑定，必须先检查最后一条人工处置历史是否允许继续

### 4.6 `ia_org_unit`

用途：统一组织单元表，承接组织、事业部、部门等层级节点。

- 关键主键：`org_unit_id`
- 关键字段：
  - `org_id`：顶层组织标识
  - `parent_org_unit_id`
  - `org_unit_code`
  - `org_unit_name`
  - `org_unit_type`：`ORG`、`DIVISION`、`DEPARTMENT`、`TEAM`
  - `org_status`：`ACTIVE`、`DISABLED`、`ARCHIVED`
  - `org_path`
  - `path_depth`
  - `manager_user_id`
  - `sort_order`
  - `source_system`
  - `source_ref`
- 关键索引 / 唯一约束：
  - `uk_org_code(org_id, org_unit_code)`
  - `uk_parent_name(org_id, parent_org_unit_id, org_unit_name)`
  - `idx_org_path(org_path)`
  - `idx_manager(manager_user_id, org_status)`
  - `idx_parent(parent_org_unit_id, sort_order)`
- 关联对象：
  - 关联 `ia_org_membership`
  - 关联 `ia_org_rule`
  - 关联 `ia_data_scope`
  - 被流程节点绑定、合同归属、通知路由、解密下载授权引用

设计说明：

- 组织树是真相，业务模块不得复制组织路径或私建部门树
- `org_path` 用于祖先链快速判断，正式关系仍以父子节点为准

### 4.7 `ia_org_membership`

用途：主体与组织单元挂接表，用于表达主部门、兼职部门、跨组织归属。

- 关键主键：`membership_id`
- 关键字段：
  - `user_id`
  - `org_id`
  - `org_unit_id`
  - `membership_type`：`PRIMARY`、`PART_TIME`、`MANAGER`、`ASSISTANT`
  - `membership_status`：`ACTIVE`、`INACTIVE`
  - `is_primary_department`
  - `effective_start_at`、`effective_end_at`
  - `position_title`
- 关键索引 / 唯一约束：
  - `uk_user_org_unit(user_id, org_unit_id, membership_type)`
  - `uk_primary_department(user_id, org_id, is_primary_department)`
    其中 `is_primary_department=1` 时同一组织只允许一条
  - `idx_org_unit_user(org_unit_id, membership_status)`
  - `idx_user_active(user_id, membership_status, effective_end_at)`
- 关联对象：
  - 关联 `ia_user`
  - 关联 `ia_org_unit`
  - 被组织解析、数据权限、通知、流程引擎候选人选择引用

设计说明：

- 把主体和组织关系独立建模，避免把多组织能力硬塞进用户主表

### 4.8 `ia_role`

用途：平台角色主表，承接统一角色定义。

- 关键主键：`role_id`
- 关键字段：
  - `role_code`
  - `role_name`
  - `role_scope`：`PLATFORM`、`ORG`、`ORG_UNIT`
  - `role_type`：`SYSTEM`、`BUSINESS`、`SECURITY`
  - `role_status`：`ACTIVE`、`DISABLED`、`ARCHIVED`
  - `inherits_role_id`：简单单继承即可，不做复杂多继承图
  - `grant_policy`：角色授予策略摘要
- 关键索引 / 唯一约束：
  - `uk_role_code(role_code)`
  - `idx_scope_status(role_scope, role_status)`
  - `idx_inherits(inherits_role_id)`
- 关联对象：
  - 关联 `ia_role_assignment`
  - 关联 `ia_permission_grant`
  - 被流程引擎、管理端、数据权限解释消费

设计说明：

- 角色是授权聚合载体，不替代部门、人员和数据范围边界

### 4.9 `ia_role_assignment`

用途：角色授予关系表，表达角色如何授予给用户或组织单元。

- 关键主键：`assignment_id`
- 关键字段：
  - `role_id`
  - `subject_type`：`USER`、`ORG_UNIT`
  - `subject_id`
  - `grant_org_id`
  - `assignment_status`：`ACTIVE`、`EXPIRED`、`REVOKED`
  - `effective_start_at`、`effective_end_at`
  - `granted_reason`
  - `granted_by`
  - `revoked_by`
- 关键索引 / 唯一约束：
  - `uk_role_subject(role_id, subject_type, subject_id, grant_org_id)`
  - `idx_subject_lookup(subject_type, subject_id, assignment_status)`
  - `idx_role_effective(role_id, assignment_status, effective_end_at)`
- 关联对象：
  - 关联 `ia_role`
  - 关联 `ia_user` 或 `ia_org_unit`
  - 被 `authorization-engine` 展开为主体有效角色集

设计说明：

- 组织单元授予会在判定时动态下沉到成员，不预先物化到所有用户授权表

### 4.10 `ia_permission_grant`

用途：菜单权限、功能权限、特殊受控授权的统一授权表。

- 关键主键：`permission_grant_id`
- 关键字段：
  - `grant_target_type`：`ROLE`、`USER`、`ORG_UNIT`
  - `grant_target_id`
  - `permission_type`：`MENU`、`FUNCTION`、`SPECIAL`
  - `permission_code`
  - `resource_type`
  - `resource_scope_ref`
  - `grant_status`：`ACTIVE`、`DISABLED`、`REVOKED`
  - `priority_no`
  - `effect_mode`：`ALLOW`、`DENY`
  - `effective_start_at`、`effective_end_at`
- 关键索引 / 唯一约束：
  - `uk_target_permission(grant_target_type, grant_target_id, permission_type, permission_code, resource_type)`
  - `idx_permission_lookup(permission_type, permission_code, grant_status)`
  - `idx_target_lookup(grant_target_type, grant_target_id, grant_status)`
  - `idx_effective(grant_status, effective_end_at)`
- 关联对象：
  - 关联 `ia_role`
  - 关联 `ia_user`
  - 关联 `ia_authorization_decision`
  - 被菜单树、功能校验、解密下载授权判定复用

设计说明：

- 解密下载授权属于 `SPECIAL` 授权，不单独走角色菜单体系
- 管理端按部门、人员授予解密下载授权时，分别落到 `grant_target_type=ORG_UNIT`
  与 `grant_target_type=USER`
- 支持显式 `DENY`，用于高敏能力收紧和例外封禁

### 4.11 `ia_data_scope`

用途：数据权限范围表，是正式一等数据能力的主表。

- 关键主键：`data_scope_id`
- 关键字段：
  - `subject_type`：`USER`、`ROLE`、`ORG_UNIT`
  - `subject_id`
  - `resource_type`：`CONTRACT`、`DOCUMENT`、`WORKFLOW_TASK`、`DECRYPT_GRANT`
  - `scope_type`：`SELF`、`ORG`、`ORG_UNIT`、`ORG_SUBTREE`、`USER_LIST`、`RULE`
  - `scope_ref`
  - `scope_status`：`ACTIVE`、`DISABLED`、`REVOKED`
  - `priority_no`
  - `effect_mode`：`ALLOW`、`DENY`
  - `effective_start_at`、`effective_end_at`
- 关键索引 / 唯一约束：
  - `uk_subject_resource_scope(subject_type, subject_id, resource_type, scope_type, scope_ref)`
  - `idx_resource_subject(resource_type, subject_type, subject_id, scope_status)`
  - `idx_scope_effective(scope_status, effective_end_at, priority_no)`
- 关联对象：
  - 关联 `ia_role`
  - 关联 `ia_user`
  - 关联 `ia_org_unit`
  - 关联 `ia_org_rule`
  - 被合同主档、文档中心、流程引擎、加密授权判定消费

设计说明：

- 数据权限与功能权限分表，避免把“可见范围”混成按钮权限附属字段

### 4.12 `ia_org_rule`

用途：组织规则主表，承接审批节点选人、通知路由、数据范围和授权命中的组织规则引用。

- 关键主键：`org_rule_id`
- 关键字段：
  - `rule_code`
  - `rule_name`
  - `rule_type`：`MANAGER_OF_ORG_UNIT`、`STARTER_MANAGER`、`ROLE_IN_ORG_UNIT`、`FIXED_CHAIN`
  - `rule_status`：`ACTIVE`、`DISABLED`、`ARCHIVED`
  - `rule_scope_type`：`GLOBAL`、`ORG`、`ORG_UNIT`
  - `rule_scope_ref`
  - `resolver_config`
  - `fallback_policy`
  - `version_no`
- 关键索引 / 唯一约束：
  - `uk_rule_code(rule_code)`
  - `idx_rule_type(rule_type, rule_status)`
  - `idx_scope(rule_scope_type, rule_scope_ref, rule_status)`
- 关联对象：
  - 关联 `ia_authorization_hit_result`
  - 被流程引擎节点绑定、通知解析、数据权限和授权命中复用

设计说明：

- 规则内容只保存内部可解释配置，不在本层级固化成对外协议表达式

### 4.13 `ia_org_rule_version`

用途：组织规则不可变版本承接表，正式冻结每一次可被消费方引用的规则内容快照。

- 关键主键：`org_rule_version_id`
- 关键字段：
  - `org_rule_id`
  - `version_no`
  - `rule_type`
  - `rule_scope_type`
  - `rule_scope_ref`
  - `resolver_config_snapshot`
  - `fallback_policy_snapshot`
  - `schema_version`
  - `version_checksum`
  - `version_status`：`DRAFT`、`EFFECTIVE`、`SUPERSEDED`、`RETIRED`
  - `effective_from`
  - `superseded_by_version_id`
- 关键索引 / 唯一约束：
  - `uk_rule_version(org_rule_id, version_no)`
  - `idx_rule_effective(org_rule_id, version_status, effective_from)`
  - `idx_version_checksum(version_checksum)`
- 关联对象：
  - 关联 `ia_org_rule`
  - 关联 `ia_org_rule_resolution_record`
  - 被流程定义、授权命中、通知路由等一切已生效引用消费

设计说明：

- `ia_org_rule` 只承接当前主档；历史回放、已生效引用冻结与跨流程持久化引用统一回到本表
- 一旦进入 `EFFECTIVE` 并被正式引用，版本快照不得原地覆盖，只允许新增更高版本

### 4.14 `ia_org_rule_resolution_record`

用途：组织规则正式解析记录表，承接一次可回放的解析输入、输出与证据摘要。

- 关键主键：`org_rule_resolution_record_id`
- 关键字段：
  - `org_rule_version_id`
  - `request_trace_id`
  - `resolution_scene`：`WORKFLOW`、`AUTHORIZATION`、`NOTIFICATION`、`PRECHECK`
  - `context_checksum`
  - `context_snapshot_ref`
  - `resolution_status`
  - `resolved_subject_snapshot`
  - `evidence_snapshot`
  - `fallback_used`
  - `resolver_version`
  - `resolved_at`
- 关键索引 / 唯一约束：
  - `idx_rule_version(org_rule_version_id, resolved_at)`
  - `idx_trace_scene(request_trace_id, resolution_scene)`
  - `idx_context_checksum(context_checksum)`
- 关联对象：
  - 关联 `ia_org_rule_version`
  - 关联 `ia_authorization_hit_result`
  - 被流程运行期、通知投递与审计回放引用

设计说明：

- 本表记录的是“当时按哪个冻结版本、在哪个上下文下、解析出了什么结果”，用于审计回放与已生效动作解释
- 预校验可写轻量记录；正式运行与授权命中必须写正式解析记录

### 4.15 `ia_authorization_decision`

用途：一次统一授权判定的结果主表，可追踪、可审计、可回放。

- 关键主键：`decision_id`
- 关键字段：
  - `subject_user_id`
  - `subject_org_id`
  - `subject_org_unit_id`
  - `action_code`
  - `resource_type`
  - `resource_id`
  - `decision_result`：`ALLOW`、`DENY`、`CONDITIONAL`
  - `decision_reason_code`
  - `permission_snapshot_checksum`
  - `data_scope_snapshot_checksum`
  - `request_trace_id`
  - `expires_at`：短期命中缓存失效时间
- 关键索引 / 唯一约束：
  - `idx_subject_action(subject_user_id, action_code, evaluated_at)`
  - `idx_resource_lookup(resource_type, resource_id, evaluated_at)`
  - `idx_trace(request_trace_id)`
  - `idx_expires(expires_at)`
- 关联对象：
  - 关联 `ia_authorization_hit_result`
  - 关联 `ia_identity_audit_event`
  - 被流程引擎、文档中心、加密下载、管理端高敏操作消费

设计说明：

- 本表记录判定事实，不替代原始授权配置表
- 可设置短保留期，但高敏动作结果必须同步沉淀审计事件

### 4.16 `ia_authorization_hit_result`

用途：授权命中依据明细表，承接本次判定命中了哪些角色、授权项、数据范围或组织规则。

- 关键主键：`hit_result_id`
- 关键字段：
  - `decision_id`
  - `hit_type`：`ROLE`、`PERMISSION_GRANT`、`DATA_SCOPE`、`ORG_RULE`
  - `hit_ref_id`
  - `frozen_ref_id`：当 `hit_type=ORG_RULE` 时指向 `org_rule_version_id`
  - `resolution_record_id`：当命中依赖组织规则正式解析时指向 `ia_org_rule_resolution_record`
  - `hit_result`：`ALLOW`、`DENY`
  - `hit_priority_no`
  - `evidence_snapshot`
- 关键索引 / 唯一约束：
  - `idx_decision(decision_id, hit_priority_no)`
  - `idx_hit_ref(hit_type, hit_ref_id)`
- 关联对象：
  - 关联 `ia_authorization_decision`
  - 关联 `ia_role`、`ia_permission_grant`、`ia_data_scope`、`ia_org_rule`

设计说明：

- 明细和主结果分表，便于审计追溯与拒绝原因解释
- 组织规则命中不得只回指 `ia_org_rule` 当前主档，必须落到冻结版本和正式解析记录

### 4.17 `ia_identity_session_snapshot`

用途：当前会话的身份上下文快照表，承接登录态与组织切换快照。

- 关键主键：`session_snapshot_id`
- 关键字段：
  - `session_id`
  - `user_id`
  - `binding_id`
  - `active_org_id`
  - `active_org_unit_id`
  - `role_snapshot_json`
  - `permission_checksum`
  - `data_scope_checksum`
  - `session_status`：`ACTIVE`、`REVOKED`、`EXPIRED`
  - `issued_at`、`expires_at`、`last_access_at`
- 关键索引 / 唯一约束：
  - `uk_session_id(session_id)`
  - `idx_user_status(user_id, session_status, expires_at)`
  - `idx_binding(binding_id)`
- 关联对象：
  - 关联 `ia_user`
  - 关联 `ia_identity_binding`
  - 被 `session-context-cache` 和高频授权读取复用

设计说明：

- 这是会话快照，不是正式授权真相
- 会话丢失可以重建，主体和授权真相仍回到主表

### 4.18 `ia_identity_audit_event`

用途：本主线追加式审计事实表。

- 关键主键：`identity_audit_event_id`
- 关键字段：
  - `event_type`：`LOGIN_SUCCEEDED`、`LOGIN_FAILED`、`BINDING_CONFLICT`、
    `BINDING_LINKED`、`BINDING_UNLINKED`、`BINDING_REBOUND`、`BINDING_REVOKED`、
    `PROTOCOL_CALLBACK_RECEIVED`、`PROTOCOL_EXCHANGE_SUCCEEDED`、`PROTOCOL_SIGNATURE_REJECTED`、
    `PROTOCOL_TICKET_REJECTED`、`PROTOCOL_REPLAY_REJECTED`、`PROTOCOL_PRECHECK_BLOCKED`、
    `PROTOCOL_FROZEN`、`PROTOCOL_MANUAL_UNFROZEN`、`PROTOCOL_MANUAL_REBOUND`、
    `PROTOCOL_MANUAL_REVOKED`、`ORG_CHANGED`、`ROLE_GRANTED`、`ROLE_REVOKED`、
    `PERMISSION_GRANTED`、`DATA_SCOPE_HIT`、`AUTHZ_DENIED`、
    `DECRYPT_DOWNLOAD_HIT`、`DECRYPT_DOWNLOAD_DENIED`
  - `event_result`：`SUCCESS`、`DENIED`、`FAILED`
  - `actor_type`：`USER`、`SYSTEM`、`INTERNAL_SERVICE`
  - `actor_id`
  - `target_user_id`
  - `target_resource_type`
  - `target_resource_id`
  - `protocol_exchange_id`
  - `precheck_id`
  - `disposition_id`
  - `decision_id`
  - `trace_id`
  - `event_payload_ref`
  - `occurred_at`
- 关键索引 / 唯一约束：
  - `idx_actor_time(actor_type, actor_id, occurred_at)`
  - `idx_target(target_resource_type, target_resource_id, occurred_at)`
  - `idx_decision(decision_id)`
  - `idx_trace(trace_id)`
  - `idx_event_type(event_type, occurred_at)`
- 关联对象：
  - 关联 `ia_authorization_decision`
  - 关联 `ia_identity_binding`
  - 关联 `ia_user`
  - 供总平台审计中心聚合

设计说明：

- 关键身份与授权事实必须先在本表追加写，再汇总到平台审计视图
- 协议层事件必须使用稳定 `event_type` 枚举承接“回调接收、验签失败、票据失效、重放拒绝、预检查阻断、人工解冻 / 重绑 / 撤销”等事实，且通过 `protocol_exchange_id`、`precheck_id`、`disposition_id` 直连正式对象，不能只把语义塞进 `event_payload_ref`

## 5. 统一身份 / 组织 / 角色 / 权限 / 数据权限内部模型

### 5.1 统一身份模型

- `ia_user` 是平台正式主体
- `ia_identity_binding` 是外部身份到平台主体的映射层
- `ia_protocol_exchange`、`ia_identity_binding_precheck`、`ia_identity_manual_disposition` 是外部身份进入主体映射前的正式协议治理承接层
- `ia_identity_session_snapshot` 是登录后运行时快照层

三层关系必须稳定分离：

1. 外部认证源只解决“你是谁从哪里来”
2. 平台主体只解决“你在平台内是谁”
3. 会话快照只解决“你此刻以哪个组织上下文、哪些展开权限在访问”

协议治理补充边界：

- 协议交换、预检查、人工处置必须先落正式记录，再决定是否允许进入主体映射或会话建立
- 已被冻结、已人工介入、已撤销的协议交换记录优先于任何自动重试或重复回调

内部规则：

- 同一自然人可有多个 `binding`，但只能归并到一个活动 `user_id`
- 主体归并后，原主体状态改为 `MERGED`，所有新登录只能落到保留主体
- 外部身份失效只影响 `binding_status`，不直接物理删除平台主体

### 5.2 组织模型

- `ia_org_unit` 表达组织树
- `ia_org_membership` 表达人与组织关系
- `org_id` 负责顶层组织隔离，`org_unit_id` 负责具体挂点
- `manager_user_id` 和 `membership_type` 共同支撑管理链解析

组织能力边界：

- 平台只有一份正式组织树
- 流程引擎、合同主档、通知、解密下载授权只能引用组织真相，不复制组织树
- 审批节点必须绑定部门、人员或组织规则；其中“部门”与“组织规则”的解释都回到本主线

### 5.3 角色与权限模型

- `ia_role` 负责角色定义
- `ia_role_assignment` 负责角色授予
- `ia_permission_grant` 负责菜单权限、功能权限、特殊授权

判定展开顺序：

1. 读取主体直授角色
2. 读取组织单元授予角色并映射到主体
3. 展开角色继承链
4. 聚合角色级权限与用户直授权限
5. 应用显式 `DENY` 与有效期过滤

菜单权限与功能权限保持同源，但用途不同：

- 菜单权限控制入口可见
- 功能权限控制动作可执行
- 特殊授权控制高敏例外能力，例如解密下载授权

### 5.4 数据权限模型

数据权限是独立于角色权限的一等能力。

- 角色只回答“能不能做某类动作”
- 数据权限回答“能对哪些对象做”

`ia_data_scope` 的内部求值使用统一范围语义：

- `SELF`：本人创建、本人经办、本人待办
- `ORG`：当前组织全部对象
- `ORG_UNIT`：指定部门对象
- `ORG_SUBTREE`：指定部门及下级部门对象
- `USER_LIST`：指定人员集合对象
- `RULE`：由 `ia_org_rule` 动态解析的对象范围

数据权限不会改写业务真相，只输出统一裁剪条件或命中结论。

## 6. 身份映射、组织解析、授权判定、解密下载授权命中的内部模型

### 6.1 身份映射内部模型

身份映射分六步处理：

1. 协议交换承接
   - 先写 `ia_protocol_exchange`，冻结 `protocol_exchange_id`、来源消息键、幂等键、可信校验结论与原始证据引用
2. `provider` 输入标准化
   - 把 `SSO`、`LDAP`、企业微信返回的标识统一整理为 `external_identity_key`
3. 绑定预检查
   - 写 `ia_identity_binding_precheck`，承接指纹归一、重复回调判定、冲突冻结判断与是否允许换会话的正式结论
4. 绑定查找
   - 优先按 `provider + external_identity_key` 查 `ia_identity_binding`
5. 主体归并判定
   - 若不存在绑定，则按手机号、邮箱、工号等指纹形成候选集
   - 候选唯一时可进入待确认绑定
   - 候选多于一条时写入 `CONFLICT`
6. 会话快照建立
   - 绑定有效后生成 `ia_identity_session_snapshot`

设计要点：

- 外部身份源只参与映射，不直接推翻平台已有角色、组织、权限真相
- 映射冲突必须可追踪、可补偿、可人工处理
- 协议层重试只能复用既有 `ia_protocol_exchange` / `ia_identity_binding_precheck` 结论或继续停留冻结态，不能绕过人工处置历史直接补签会话

### 6.2 组织解析内部模型

组织解析服务流程引擎、通知、数据权限和解密下载授权命中。

输入上下文：

- `subject_user_id`
- `active_org_id`
- `active_org_unit_id`
- 可选业务对象上下文，如 `contract_owner_org_unit_id`

解析能力：

- 祖先链解析
- 直属负责人解析
- 发起人上级链解析
- 部门内指定角色持有人解析
- 组织树范围解析

解析输出：

- 候选用户集合
- 解析证据摘要
- 回退结果或失败原因
- 正式解析记录引用：`org_rule_resolution_record_id`

### 6.3 授权判定内部模型

统一授权判定采用五段式流水：

1. 主体合法性校验
   - `user_status`、会话状态、活跃组织上下文
2. 功能权限判定
   - 菜单权限、功能权限、特殊授权聚合
3. 数据权限判定
   - 生成资源级数据范围约束或命中结果
4. 业务上下文补充判定
   - 如合同归属部门、文档密级、流程任务承办人、授权范围生效期
5. 判定冻结
   - 写入 `ia_authorization_decision` 和 `ia_authorization_hit_result`
   - 若命中组织规则，同时冻结 `org_rule_version_id + org_rule_resolution_record_id`

结果优先级：

1. 主体失效直接拒绝
2. 显式 `DENY` 高于 `ALLOW`
3. 特殊高敏能力必须同时满足功能权限和数据范围
4. 最终无有效 `ALLOW` 视为拒绝

### 6.4 解密下载授权命中内部模型

管理端可按部门、人员做解密下载授权，但平台不再单独维护第二套授权真相。

内部命中逻辑：

1. 用户发起“解密下载”动作
2. `authorization-engine` 先校验主体状态与会话
3. 校验是否具备 `SPECIAL:DECRYPT_DOWNLOAD` 功能授权
4. 结合 `DOCUMENT` 或 `CONTRACT` 数据权限确认是否有权接触目标对象
5. 解析部门 / 人员维度的特定授权范围
6. 若命中，冻结为一次 `AuthorizationDecision`
7. 把 `decision_id` 传给加密文档主线执行真实导出作业

边界约束：

- 本主线只负责“授权是否命中”
- 加密文档主线负责“如何生成明文导出文件、如何交付、如何过期”
- 命中结果必须可审计，且可追溯到人员、部门、授权项、数据范围和组织规则

## 7. 与其他主线的内部挂接设计

### 7.1 与流程引擎的挂接

- 流程定义阶段：流程引擎引用 `ia_org_unit`、`ia_user`、`ia_org_rule` 作为节点绑定来源
- 流程发布阶段：流程引擎调用组织解析预校验，确认每个节点都能落到部门、人员或组织规则；凡发布后会被冻结的规则引用，都必须承接 `org_rule_version_id`
- 运行阶段：流程引擎只消费解析结果快照，不自行拼装组织树；运行期审计与实例回放统一引用 `ia_org_rule_resolution_record`
- 审批动作阶段：审批人动作授权复用 `AuthorizationDecision`

边界：

- 流程实例、任务、动作真相仍由流程引擎维护
- 本主线只提供统一主体、组织规则解析、授权结论与审计归因

### 7.2 与合同主档的挂接

- 合同创建人、归属部门、经办人等字段统一引用 `user_id`、`org_unit_id`
- 合同列表和详情的数据权限过滤由本主线输出范围约束
- 合同管理端授权范围也必须复用 `ia_data_scope`

边界：

- 合同主档不维护自己的用户、组织、权限主数据
- 本主线不拥有合同生命周期状态真相

### 7.3 与文档中心的挂接

- 文档访问、下载、批注、预览等动作先经本主线授权判定
- 文档中心可缓存授权摘要，但不得落独立授权真相表
- 文档中心保存文件真相，本主线保存谁能访问这些文件的统一边界

### 7.4 与加密下载授权的挂接

- 解密下载授权配置在语义上归本主线统一解释
- 加密文档主线在受理下载前必须携带 `decision_id` 或等价命中凭据
- 若授权配置撤销，不影响已冻结且正在执行的作业快照，但新请求必须按新权限重判
- 若加密文档主线因执行需要保留授权冻结快照，该快照只作为作业侧执行投影，
  不得成为第二套正式授权真相

### 7.5 与通知主线的挂接

- 通知主线通过组织解析服务获取待办人、抄送人、异常接收人
- 通知模板只消费主体与组织结果，不改写授权真相
- 身份冲突、越权拒绝、授权变更等高敏事件可触发通知

### 7.6 与审计主线的挂接

- 本主线先写 `ia_identity_audit_event`
- 总平台审计中心再按 `trace_id`、`decision_id`、`user_id` 聚合查询视图
- 登录、绑定、授权命中、拒绝、组织变更等高敏事件必须全链路可追踪

### 7.7 与集成主线的挂接

- 集成主线只负责连接器、外部网络交互、通用验签 / 换票能力与适配器运行时，不持有外部身份协议治理主对象
- 本主线负责 `SSO`、`LDAP`、企业微信进入平台后的协议交换承接、标准化身份语义、绑定预检查、冲突冻结、人工处置与会话准入
- 外部目录同步可更新候选组织引用，但不能直接改写平台授权真相而不留痕

## 8. 缓存、锁、幂等、并发控制

### 8.1 缓存边界

允许缓存到 `Redis` 的内容：

- 会话票据与短期会话上下文
- 组织树只读快照
- 当前主体角色展开摘要
- 菜单树与权限摘要
- 高频授权判定短期结果

必须落 `MySQL` 的内容：

- 用户主体、身份绑定、组织树、角色授予、权限授权、数据权限
- 协议交换记录、绑定预检查记录、人工处置记录
- 组织规则版本快照、组织规则解析记录
- 授权判定事实、命中依据、审计事件
- 主体归并与冲突状态

缓存原则：

- 组织、角色、权限、数据范围变更后，按主体和组织粒度失效
- 授权缓存只用于加速，不作为审计或恢复依据

### 8.2 锁机制

短期分布式锁用于以下场景：

- 同一外部身份绑定并发创建
- 同一主体归并操作
- 同一组织节点移动或重命名
- 同一高敏授权项并发修改
- 同一授权判定在高冲突资源上的重复提交去重

规则：

- 锁粒度优先按 `binding`、`user`、`org_unit`、`permission_grant`、`resource`
- 锁失效后，必须依靠唯一约束和 `row_version` 再兜底

### 8.3 幂等机制

- 登录回调按 `ia_protocol_exchange.idempotency_key` 去重，最小承接对象仍是 `protocol_exchange_id`
- 身份绑定创建按 `provider + external_identity_key` 去重
- 角色授予按 `role_id + subject_type + subject_id + grant_org_id` 去重
- 授权创建按主唯一键去重
- 解密下载授权判定按请求级 `Idempotency-Key + resource fingerprint` 去重

### 8.4 并发控制

统一采用“唯一约束 + 乐观锁 + 条件更新 + 必要时短锁”的组合策略。

- 组织树修改必须校验 `row_version`
- 主体归并必须条件更新 `user_status`
- 角色与权限撤销必须校验未被并发再次激活
- 授权判定结果只追加写，不做覆盖式更新

## 9. 异步任务、补偿与恢复

### 9.1 异步任务范围

以下场景采用异步任务：

- 外部身份回调后的目录补全与候选归并检查
- 组织树全量重建缓存
- 大批量角色 / 数据权限变更后的权限摘要重建
- 审计事件汇总与报表投影刷新
- 失效会话批量驱逐

### 9.2 补偿机制

- 身份绑定创建成功但会话快照失败时：保留绑定，补偿重建会话
- 组织调整成功但缓存失效失败时：数据库真相保留，异步重建缓存
- 授权项生效成功但摘要未刷新时：通过重建任务重新展开
- 授权判定成功但审计写入失败时：判定结果保留，并重放审计写入任务

### 9.3 恢复边界

- `Redis` 丢失后，可通过数据库重建会话外的全部主体、组织、角色、权限摘要
- 授权判定历史可通过 `ia_authorization_decision` 和明细表追溯
- 组织树缓存、菜单树缓存、权限摘要缓存允许重建
- 冲突绑定、归并状态、撤销状态必须完全依赖数据库恢复

### 9.4 人工介入边界

以下情况必须允许人工处理：

- 同一外部身份命中多个候选主体
- 主体归并后发现组织挂接冲突
- 高敏特殊授权疑似越权
- 解密下载授权命中依据存在歧义

## 10. 审计、日志、指标与恢复边界

### 10.1 审计要求

必须审计的事件：

- 登录成功、失败、锁定、注销
- 协议回调接收、协议换票成功 / 失败、签名拒绝、票据失效、重放拒绝、预检查阻断、冲突冻结、人工解冻 / 重绑 / 撤销
- 身份绑定创建、解绑、冲突、归并
- 组织创建、移动、禁用、负责人变更
- 角色授予、撤销、失效
- 权限授予、撤销、显式拒绝
- 数据权限命中、拒绝、范围扩大
- 解密下载授权命中与拒绝

### 10.2 日志要求

- 业务日志必须带 `trace_id`、`user_id`、`active_org_id`、`action_code`
- 认证适配日志与授权判定日志分开记录，避免敏感字段混写
- 外部身份原始报文不直接明文落业务日志，仅保存引用或脱敏摘要

### 10.3 指标要求

- 登录成功率、失败率、映射冲突率
- 会话建立耗时、授权判定耗时、组织规则解析耗时
- 角色 / 权限变更后缓存刷新延迟
- 显式拒绝命中数、高敏授权命中数、解密下载授权命中率

### 10.4 恢复边界

- 恢复时先恢复主体、组织、角色、权限真相，再重建缓存和只读投影
- 审计表为追加写事实，恢复后不得补写伪造历史，只能补写恢复动作本身
- 外部身份源不可用时，已存在的本地主体真相仍保留，不允许把历史主体清空

## 11. 继续下沉到后续专项设计或实现的内容

以下内容继续下沉，不在本文展开：

- 组织规则 resolver_config 的内部语法与版本兼容策略： [org-rule-resolution-design.md](./special-designs/org-rule-resolution-design.md)
- 企业微信、SSO、LDAP 的协议字段、签名校验与回调细节： [external-identity-protocol-design.md](./special-designs/external-identity-protocol-design.md)
- 菜单树、功能点、前端路由守卫与页面显隐实现
- 数据权限下推到具体查询仓储时的 `SQL` 组装策略： [data-scope-sql-pushdown-design.md](./special-designs/data-scope-sql-pushdown-design.md)
- 解密下载授权管理端的交互流程与审批单据联动细节： [decrypt-download-authorization-design.md](./special-designs/decrypt-download-authorization-design.md)
- 组织同步、权限初始化、历史账号并表迁移的实施脚本： [identity-migration-and-bootstrap-design.md](./special-designs/identity-migration-and-bootstrap-design.md)

## 12. 本文结论

本主线以 `ia_user`、`ia_identity_binding`、`ia_org_unit`、`ia_role`、
`ia_role_assignment`、`ia_permission_grant`、`ia_data_scope`、
`ia_org_rule_version`、`ia_org_rule_resolution_record`、`ia_protocol_exchange`、
`ia_identity_binding_precheck`、`ia_identity_manual_disposition`、
`ia_authorization_decision`、`ia_org_rule`、`ia_identity_audit_event` 为核心，
建立平台内唯一的统一身份、组织、角色、权限与数据权限真相。

外部认证源只影响身份映射，不改写平台主体真相；业务模块只消费统一解析和授权结论，
不再各自维护一套用户、组织、权限真相。

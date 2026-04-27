# 组织架构 / 权限 / 统一认证主线 API Design

## 1. 文档说明

本文档是组织架构 / 权限 / 统一认证主线的第一份正式 `API Design`。
它基于 [`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)、
[`总平台 Architecture Design`](../../architecture-design.md)、
[`总平台 API Design`](../../api-design.md)、
[`总平台 Detailed Design`](../../detailed-design.md)、
[`identity-access Architecture Design`](./architecture-design.md) 与
[`integration-hub API Design`](../integration-hub/api-design.md)，
用于固化组织架构、统一认证、角色权限、数据权限、授权查询与审计查询的接口边界。

### 1.1 输入

- [`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- [`总平台 Architecture Design`](../../architecture-design.md)
- [`总平台 API Design`](../../api-design.md)
- [`总平台 Detailed Design`](../../detailed-design.md)
- [`identity-access Architecture Design`](./architecture-design.md)
- [`integration-hub API Design`](../integration-hub/api-design.md)

### 1.2 输出

- 本文：[`API Design`](./api-design.md)
- 为后续 [`Detailed Design`](./detailed-design.md) 提供明确下沉边界

### 1.3 阅读边界

本文只定义主线级接口契约，不展开以下内容：

- 不复述一期需求范围、建设必要性或实施排期
- 不写认证协议细节、各家 `SSO` / `LDAP` / 企业微信 `SDK` 接入方式
- 不写物理表结构、索引、缓存键、同步任务参数或内部求值算法
- 不写组织规则表达式、审批规则表达式、菜单树前端渲染细节
- 不写文档中心内部加解密执行流程、文件模块内部状态机或下载介质细节

## 2. API 边界

### 2.1 边界总则

- 本主线是平台统一身份、组织、角色、权限、数据权限与授权判定的唯一接口面。
- 业务模块不得自建第二套用户、组织、角色、权限或数据范围写接口。
- 审批节点绑定部门、人员或组织规则的约束在本主线暴露为引用型接口边界，
  不在本文展开规则表达式细节。
- 本主线只暴露“身份与授权真相”，不拥有合同主档、流程实例、文档版本链、
  解密执行流水等业务真相。

### 2.2 登录 / 会话接口边界

登录与会话接口只负责建立、续期、注销和查询平台统一身份上下文。

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/auth/password/sessions` | 账号密码登录并建立会话 | `login_name`、`password`、`client_type` | `session_id`、`access_token`、`expires_at`、`user_context` |
| `POST /api/auth/sessions/exchanges` | 用外部票据或认证结果换取平台会话 | `provider`、`ticket` 或 `code`、`redirect_uri` | `session_id`、`access_token`、`expires_at`、`binding_status` |
| `POST /api/auth/sessions/refreshes` | 刷新会话 | `refresh_token` | `access_token`、`expires_at` |
| `DELETE /api/auth/sessions/{session_id}` | 注销当前会话或指定会话 | 路径参数 `session_id` | `revoked` |
| `GET /api/auth/me` | 查询当前登录身份上下文 | 无 | `user`、`org_context`、`role_list`、`permission_summary` |
| `GET /api/auth/sessions` | 查询当前主体可见会话列表 | `user_id` 可选 | `item_list`、`total` |

边界约束：

- `POST /api/auth/sessions/exchanges` 只承接平台已认可的外部认证结果，
  不在本主线重复定义外部认证中心原生协议。
- `GET /api/auth/me` 返回的是平台统一身份上下文，不是某个外部身份源原样透传结果。
- 同一自然人来自多个认证源时，以平台统一 `User` 视角返回，而不是返回多份碎片化账号。

### 2.3 统一认证回调与身份映射接口边界

这组接口负责接收认证回调结果、查询身份映射、处理映射冲突与管理绑定关系。

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/auth/callbacks/sso` | 接收统一认证或 `SSO` 回调 | `provider_request_id`、`ticket`、`payload` | `accepted`、`exchange_ref` |
| `POST /api/auth/callbacks/wecom` | 接收企业微信认证回调 | `code`、`state` | `accepted`、`exchange_ref` |
| `POST /api/auth/callbacks/ldap` | 接收目录认证承接结果 | `provider_request_id`、`payload` | `accepted`、`exchange_ref` |
| `GET /api/identity-bindings` | 查询身份绑定关系列表 | `provider`、`user_id`、`external_identity`、`binding_status` | `item_list`、`total` |
| `GET /api/identity-bindings/{binding_id}` | 查询单条身份绑定关系 | 路径参数 `binding_id` | `binding_detail` |
| `POST /api/identity-bindings` | 创建或确认身份绑定 | `provider`、`external_identity`、`user_id` | `binding_id`、`binding_status` |
| `POST /api/identity-bindings/{binding_id}/unlinks` | 解除身份绑定 | 路径参数 `binding_id`、`reason` | `unlinked` |
| `POST /api/identity-bindings/conflict-checks` | 对外部身份进行映射冲突检查 | `provider`、`external_identity` | `conflict_status`、`candidate_user_list` |

边界约束：

- 回调接口只承接认证结果，不承接组织全量同步、角色授权或业务数据写入。
- `IdentityBinding` 表达“外部身份到平台主体”的稳定绑定关系，
  不替代 `User`、`OrgUnit`、`Role` 的主数据接口。
- 冲突检查只返回冲突状态与候选主体摘要，不在本文定义归并算法。

### 2.4 用户 / 组织 / 部门 / 角色接口边界

这组接口负责统一用户主体、组织单元、部门树和角色授权载体的查询与维护。

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `GET /api/users` | 查询用户列表 | `org_id`、`department_id`、`keyword`、`user_status` | `item_list`、`total` |
| `POST /api/users` | 创建平台用户 | `login_name`、`display_name`、`org_membership_list` | `user_id`、`user_status` |
| `GET /api/users/{user_id}` | 查询用户详情 | 路径参数 `user_id` | `user_detail` |
| `PATCH /api/users/{user_id}` | 更新用户信息与状态 | 路径参数 `user_id`、`display_name`、`user_status` | `user_id`、`updated_at` |
| `GET /api/org-units` | 查询组织 / 部门树或平铺列表 | `org_type`、`parent_org_unit_id`、`include_disabled` | `item_list`、`total` |
| `POST /api/org-units` | 创建组织单元 | `parent_org_unit_id`、`org_unit_name`、`org_unit_type` | `org_unit_id` |
| `GET /api/org-units/{org_unit_id}` | 查询组织单元详情 | 路径参数 `org_unit_id` | `org_unit_detail` |
| `PATCH /api/org-units/{org_unit_id}` | 更新组织单元 | 路径参数 `org_unit_id`、`org_unit_name`、`org_status` | `org_unit_id`、`updated_at` |
| `GET /api/roles` | 查询角色列表 | `org_id`、`role_scope`、`role_status` | `item_list`、`total` |
| `POST /api/roles` | 创建角色 | `role_code`、`role_name`、`role_scope` | `role_id` |
| `GET /api/roles/{role_id}` | 查询角色详情 | 路径参数 `role_id` | `role_detail` |
| `POST /api/roles/{role_id}/assignments` | 给用户或组织授予角色 | `subject_type`、`subject_id`、`org_id` | `assignment_id`、`assignment_status` |
| `DELETE /api/roles/{role_id}/assignments/{assignment_id}` | 解除角色授予 | 路径参数 `role_id`、`assignment_id` | `revoked` |

边界约束：

- `OrgUnit` 同时承接组织与部门层级，不要求在 API 层拆出第二套部门资源。
- 组织接口暴露的是组织单元主数据与成员挂接结果，
  不在本文下沉组织路径维护、编码生成或同步补偿细节。
- 角色接口只定义角色本体和授予关系，不把菜单、功能、数据权限直接混写进角色资源本体。

### 2.5 菜单权限 / 功能权限 / 数据权限接口边界

这组接口负责统一授权语义，不把前端菜单显隐与后端动作校验拆成两套体系。

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `GET /api/permission-grants` | 查询授权项列表 | `subject_type`、`subject_id`、`permission_type`、`org_id` | `item_list`、`total` |
| `POST /api/permission-grants` | 创建授权项 | `subject_type`、`subject_id`、`permission_type`、`permission_code`、`scope_ref` | `permission_grant_id` |
| `PATCH /api/permission-grants/{permission_grant_id}` | 更新授权项 | 路径参数 `permission_grant_id`、`grant_status`、`scope_ref` | `permission_grant_id`、`updated_at` |
| `DELETE /api/permission-grants/{permission_grant_id}` | 删除授权项 | 路径参数 `permission_grant_id` | `revoked` |
| `GET /api/permissions/menu-tree` | 查询当前主体可见菜单权限树 | `org_id` 可选 | `menu_tree`、`permission_version` |
| `GET /api/permissions/function-actions` | 查询当前主体功能权限 | `resource_code` 可选 | `action_list` |
| `GET /api/data-scopes` | 查询数据权限范围列表 | `subject_type`、`subject_id`、`resource_type` | `item_list`、`total` |
| `POST /api/data-scopes` | 创建数据权限范围 | `subject_type`、`subject_id`、`resource_type`、`scope_type`、`scope_ref` | `data_scope_id` |
| `GET /api/data-scopes/effective` | 查询当前主体在指定资源上的生效数据范围 | `resource_type`、`action_code`、`org_id` | `effective_scope_list` |

边界约束：

- 菜单权限负责入口可见性，功能权限负责动作可执行性，数据权限负责对象范围；
  三者可联动，但接口边界保持独立资源。
- `scope_ref` 只表达被引用的数据范围对象或范围标识，
  不在本文展开内部过滤表达式与求值过程。
- 数据权限是正式一等能力，必须提供独立查询与维护接口，
  不能退化为各业务列表接口的隐式筛选参数。

### 2.6 组织规则解析与授权查询接口边界

这组接口服务流程引擎、合同主档、通知与管理端授权查询。

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/authorization/org-rules/resolutions` | 解析组织规则到候选主体集合 | `org_rule_ref`、`org_context`、`business_context` | `resolved_subject_list`、`resolution_status` |
| `POST /api/authorization/decisions` | 计算一次统一授权判定 | `subject_ref`、`action_code`、`resource_type`、`resource_ref` | `decision_id`、`decision_result`、`reason_list` |
| `GET /api/authorization/decisions/{decision_id}` | 查询授权判定详情 | 路径参数 `decision_id` | `decision_detail` |
| `POST /api/authorization/checks/batch` | 批量授权判定 | `subject_ref`、`check_list` | `decision_list` |
| `GET /api/authorization/subjects/{user_id}/effective` | 查询主体当前生效授权摘要 | 路径参数 `user_id`、`org_id` 可选 | `role_list`、`permission_list`、`data_scope_list` |

边界约束：

- 组织规则解析接口只返回解析结果与必要上下文，不暴露规则表达式内部语法。
- `AuthorizationDecision` 是可追踪的判定结果资源，
  用于业务模块消费统一授权结论，不替代业务模块自身对象详情。
- 审批节点绑定部门、人员或组织规则时，应通过本组接口解析，
  但节点编排、时序与执行仍归流程引擎治理。

### 2.7 解密下载授权配置 / 查询 / 命中判定接口边界

这组接口是平台“解密下载”授权治理的正式归属，负责授权配置、授权查询与命中判定。

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `GET /api/authorization/decrypt-download-grants` | 查询解密下载授权列表 | `org_id`、`department_id`、`user_id`、`resource_scope` | `item_list`、`total` |
| `POST /api/authorization/decrypt-download-grants` | 创建解密下载授权 | `grant_scope_type`、`grant_scope_ref`、`subject_type`、`subject_id`、`resource_scope` | `permission_grant_id` |
| `POST /api/authorization/decrypt-download-hits` | 查询一次解密下载授权是否命中 | `user_id`、`document_id` 或 `contract_id`、`action_code` | `hit`、`decision_ref`、`matched_grant_list` |

边界约束：

- 管理端可按部门、人员做解密下载授权，但本主线只定义授权配置与命中判定接口。
- 总平台与 `encrypted-document` 主线涉及“解密下载”时，都应复用本组接口作为授权真相来源。
- 文档中心或加密子模块负责真正的解密下载执行、文件流生成和结果落地，
  不在本主线 API 中展开。
- 命中判定返回授权依据摘要，不返回文件模块内部执行细节。

### 2.8 审计查询接口边界

审计查询只暴露身份、组织、授权相关的只读审计视图。

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `GET /api/identity-audit-views` | 查询身份与授权审计视图列表 | `event_type`、`actor_user_id`、`target_user_id`、`trace_id`、`occurred_at_start`、`occurred_at_end` | `item_list`、`total` |
| `GET /api/identity-audit-views/{audit_view_id}` | 查询单条审计视图详情 | 路径参数 `audit_view_id` | `audit_view_detail` |

边界约束：

- 审计视图是只读聚合视图，不提供直接写入接口。
- 登录成功、登录失败、映射冲突、角色变更、授权变更、数据权限命中、
  解密下载授权命中等事件都应可被查询。
- 审计中心持有完整留痕真相，本主线只暴露与身份和授权治理直接相关的查询视图。

## 3. 核心资源划分

### 3.1 `IdentitySession`

`IdentitySession` 表示平台内一次可验证、可续期、可注销的统一身份会话。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `session_id` | string | 会话主键 |
| `user_id` | string | 平台用户主键 |
| `org_context` | object | 当前生效组织上下文摘要 |
| `provider` | string | 建立会话的认证来源 |
| `session_status` | string | 会话状态 |
| `issued_at` | string | 会话签发时间 |
| `expires_at` | string | 会话失效时间 |
| `last_access_at` | string | 最近访问时间 |

资源边界：

- 它表示平台会话，不等于外部认证中心原始登录态。
- 它持有统一身份上下文摘要，不直接承载角色授权明细全集。

### 3.2 `IdentityBinding`

`IdentityBinding` 表示外部身份与平台 `User` 之间的稳定绑定关系。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `binding_id` | string | 绑定关系主键 |
| `provider` | string | 身份来源，如 `SSO`、`LDAP`、`WECOM` |
| `external_identity` | string | 外部身份标识 |
| `user_id` | string | 平台用户主键 |
| `binding_status` | string | 绑定状态 |
| `last_verified_at` | string | 最近验证时间 |
| `linked_at` | string | 建立绑定时间 |

资源边界：

- 它只表达身份映射，不表达组织归属、角色授予和业务权限全集。
- 一个 `User` 可关联多个 `IdentityBinding`，但平台主体仍保持唯一。

### 3.3 `User`

`User` 是平台统一主体资源。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `user_id` | string | 用户主键 |
| `login_name` | string | 登录名 |
| `display_name` | string | 显示名称 |
| `user_status` | string | 用户状态 |
| `default_org_id` | string | 默认组织 |
| `membership_list` | array | 多组织 / 多部门归属摘要 |
| `role_list` | array | 当前角色摘要 |

资源边界：

- `User` 是平台统一主体，不直接等于某个外部账号对象。
- 用户详情可带角色与组织摘要，但角色、组织、权限仍由独立资源治理。

### 3.4 `OrgUnit`

`OrgUnit` 是统一组织单元资源，可表达组织、事业部、部门等层级节点。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `org_unit_id` | string | 组织单元主键 |
| `org_id` | string | 顶层组织标识 |
| `parent_org_unit_id` | string | 上级组织单元 |
| `org_unit_type` | string | 单元类型 |
| `org_unit_name` | string | 单元名称 |
| `org_status` | string | 组织状态 |
| `manager_user_id` | string | 管理责任人，可为空 |

资源边界：

- `OrgUnit` 提供统一组织树真相，不替代流程节点绑定或数据权限本身。
- 多组织能力通过 `org_id` 与跨组织成员归属在 API 层体现。

### 3.5 `Role`

`Role` 是平台授权聚合载体。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `role_id` | string | 角色主键 |
| `role_code` | string | 角色编码 |
| `role_name` | string | 角色名称 |
| `role_scope` | string | 角色作用域 |
| `role_status` | string | 角色状态 |
| `assignment_count` | integer | 当前授予数量摘要 |

资源边界：

- 角色只作为授权聚合载体，不替代组织边界与数据范围边界。
- 角色本体不直接携带完整菜单树或规则表达式正文。

### 3.6 `PermissionGrant`

`PermissionGrant` 是菜单权限、功能权限、管理授权与特定受控授权的统一资源。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `permission_grant_id` | string | 授权主键 |
| `subject_type` | string | 受权主体类型 |
| `subject_id` | string | 受权主体主键 |
| `permission_type` | string | `MENU` / `FUNCTION` / `SPECIAL` |
| `permission_code` | string | 权限编码 |
| `scope_ref` | object | 权限范围引用 |
| `grant_status` | string | 授权状态 |
| `effective_at` | string | 生效时间 |

资源边界：

- 它负责表达“谁被授予什么权限”，不负责计算最终是否命中具体业务对象。
- 解密下载授权可复用 `PermissionGrant` 作为授权载体，但命中判定仍通过专门查询接口暴露。

### 3.7 `DataScope`

`DataScope` 是平台正式数据权限范围资源。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data_scope_id` | string | 数据范围主键 |
| `subject_type` | string | 主体类型 |
| `subject_id` | string | 主体主键 |
| `resource_type` | string | 资源类型，如 `CONTRACT`、`DOCUMENT`、`WORKFLOW_TASK` |
| `scope_type` | string | 范围类型 |
| `scope_ref` | object | 范围引用摘要 |
| `scope_status` | string | 范围状态 |

资源边界：

- 它只表达数据可见与可操作范围，不表达业务资源本体。
- 具体求值顺序、合并策略与过滤下推方式下沉到 `Detailed Design`。

### 3.8 `AuthorizationDecision`

`AuthorizationDecision` 是一次可追踪的授权判定结果资源。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `decision_id` | string | 判定主键 |
| `subject_ref` | object | 判定主体 |
| `action_code` | string | 请求动作 |
| `resource_type` | string | 资源类型 |
| `resource_ref` | object | 资源引用 |
| `decision_result` | string | `ALLOW` / `DENY` / `CONDITIONAL` |
| `reason_list` | array | 判定依据摘要 |
| `evaluated_at` | string | 判定时间 |

资源边界：

- 它是统一授权结论，不替代原始授权项与数据范围资源。
- 它可以被流程、合同、文档、通知等模块消费，但不暴露内部求值算法。

### 3.9 `IdentityAuditView`

`IdentityAuditView` 是供查询身份与授权相关关键事实的只读聚合视图。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `audit_view_id` | string | 审计视图主键 |
| `event_type` | string | 事件类型 |
| `actor_user_id` | string | 操作主体 |
| `target_user_id` | string | 目标主体，可为空 |
| `resource_ref` | object | 关联资源引用 |
| `result_status` | string | 结果状态 |
| `trace_id` | string | 链路追踪标识 |
| `occurred_at` | string | 发生时间 |

资源边界：

- 它是查询视图，不承担写入主资源职责。
- 它聚合认证、映射、授权、命中、拒绝等事实，但不替代审计中心完整底层留痕。

## 4. 统一约定

### 4.1 协议

- 继承 [`总平台 API Design`](../../api-design.md) 的统一约定，默认使用
  `HTTPS + JSON + UTF-8`。
- 时间字段使用 `ISO 8601`。
- 分页字段使用 `page`、`page_size`。
- 统一响应结构、成功响应与失败响应格式继承总平台，不在本主线重定义。

### 4.2 鉴权

- 登录、外部认证回调换会话接口允许匿名访问，但必须满足来源校验与重放保护。
- 用户、组织、角色、授权配置类接口要求平台登录态与相应管理权限。
- 授权判定、数据范围查询、审计查询接口默认属于平台内部服务或受控管理接口。
- 内部模块调用本主线接口时，使用平台内部服务身份或受控用户上下文，
  不允许业务模块绕过本主线直连底层授权存储。

### 4.3 幂等

- 所有写接口都应支持 `Idempotency-Key` 或等价业务幂等键。
- 外部认证回调优先使用 `provider_request_id`、`state` 或等价票据引用作为幂等键。
- 授权创建、角色授予、身份绑定等接口在相同主体和相同目标下必须具备去重语义。
- 相同幂等键对应不同请求体时，应返回 `40905` `IDEMPOTENCY_CONFLICT`。

### 4.4 命名规范继承总平台 API Design

- 路径段使用 `kebab-case`。
- 请求字段、响应字段、路径参数、查询参数使用 `snake_case`。
- 资源主键统一使用 `<resource>_id`。
- 枚举值统一使用 `UPPER_SNAKE_CASE`。
- 不为本主线另起命名风格，严格继承 [`总平台 API Design`](../../api-design.md)。

### 4.5 错误码复用策略

本主线优先复用 [`总平台 API Design`](../../api-design.md) 的统一错误码。

| 业务错误码 | 错误名称 | 本主线使用场景 |
| --- | --- | --- |
| `40001` | `INVALID_PAYLOAD` | 登录、回调、授权创建等请求结构非法 |
| `40002` | `INVALID_FIELD_VALUE` | `provider`、`role_scope`、`scope_type` 等字段值非法 |
| `40003` | `INVALID_QUERY_PARAMS` | 审计查询、组织查询、授权查询参数非法 |
| `40101` | `AUTH_REQUIRED` | 未登录或会话已失效 |
| `40102` | `AUTH_TOKEN_INVALID` | 会话令牌、票据或换票结果无效 |
| `40103` | `CALLBACK_SIGNATURE_INVALID` | 统一认证回调来源校验失败 |
| `40301` | `PERMISSION_DENIED` | 无权访问管理、授权或审计接口 |
| `40403` | `EXTERNAL_INSTANCE_NOT_FOUND` | 外部身份来源记录或换票引用不存在 |
| `40905` | `IDEMPOTENCY_CONFLICT` | 相同幂等键对应不同授权 / 绑定请求 |
| `42204` | `WECOM_SYNC_FAILED` | 企业微信身份承接或认证换票失败 |
| `50001` | `INTERNAL_SERVER_ERROR` | 主线内部未分类异常 |
| `50201` | `EXTERNAL_SYSTEM_UNAVAILABLE` | 外部认证源不可用或响应异常 |

新增身份域错误码时，应先扩展总平台错误码体系，不在本文件自建局部私有码表。

## 5. 与流程引擎的接口边界

本主线与流程引擎的关系是“提供节点选人、组织解析和授权判定底座”，
不是“持有流程定义或流程实例真相”。

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/internal/identity-access/workflow-node-bindings/resolutions` | 解析审批节点绑定对象 | `workflow_node_ref`、`binding_type`、`binding_ref`、`business_context` | `resolved_subject_list`、`resolution_status` |
| `POST /api/internal/identity-access/workflow-authorizations/checks` | 判断当前主体能否处理节点动作 | `user_id`、`workflow_instance_id`、`node_ref`、`action_code` | `decision_result`、`reason_list` |
| `GET /api/internal/identity-access/workflow-subjects/{user_id}/assignable-org-contexts` | 查询主体在流程中的可用组织上下文 | 路径参数 `user_id` | `org_context_list` |

边界约束：

- 审批节点必须绑定部门、人员或组织规则，但规则表达式细节不在本文展开。
- 流程引擎消费本主线的解析结果与授权结果，不反向改写组织和权限真相。
- 节点流转、并行 / 会签 / 转办时序仍归流程引擎治理。

## 6. 与合同主档、文档中心、加密下载授权的接口边界

### 6.1 与合同主档的接口边界

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/internal/identity-access/contracts/access-decisions` | 判断主体对合同对象的访问 / 操作权限 | `user_id`、`contract_id`、`action_code` | `decision_result`、`data_scope_hit` |
| `GET /api/internal/identity-access/contracts/{contract_id}/authorized-subjects` | 查询指定合同的授权主体摘要 | 路径参数 `contract_id`、`action_code` | `subject_list` |

边界约束：

- 合同主档负责合同真相，本主线只返回身份与授权结论。
- 合同创建人、归属部门、经办人等身份字段应引用本主线统一主体与组织单元。

### 6.2 与文档中心的接口边界

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/internal/identity-access/documents/access-decisions` | 判断主体对文档的查看、下载、管理权限 | `user_id`、`document_id`、`action_code` | `decision_result`、`reason_list` |
| `GET /api/internal/identity-access/documents/{document_id}/effective-scopes` | 查询文档对象对应的生效数据范围摘要 | 路径参数 `document_id` | `effective_scope_list` |

边界约束：

- 文档中心持有文件与版本链真相，本主线只治理访问主体与授权边界。
- 文档预览、协作、批注等动作的执行协议不在本主线文档展开。

### 6.3 与加密下载授权的接口边界

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/internal/identity-access/decrypt-download/access-decisions` | 判断主体是否可执行解密下载 | `user_id`、`document_id` 或 `contract_id`、`org_context` | `decision_result`、`matched_grant_list` |
| `GET /api/internal/identity-access/decrypt-download/grants/effective` | 查询对象上的生效解密下载授权摘要 | `document_id` 或 `contract_id` | `grant_list` |

边界约束：

- 管理端按部门、人员授予解密下载权限的配置、查询和判定由本主线统一暴露。
- 真正的解密执行、下载文件生成、一次性链接或文件流发放由文档 / 加密主线负责。

## 7. 与通知、审计、集成主线的接口边界

### 7.1 与通知的接口边界

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/internal/identity-access/notification-receivers/resolutions` | 解析通知接收人 | `receiver_rule`、`org_context`、`business_ref` | `receiver_list` |

边界约束：

- 本主线提供接收主体解析结果，通知中心负责通道发送与回执处理。

### 7.2 与审计的接口边界

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/internal/identity-access/audit-events` | 提交身份 / 授权相关关键事实给审计主线 | `event_type`、`actor_ref`、`resource_ref`、`result_status` | `accepted`、`audit_ref` |

边界约束：

- 审计主线持有正式审计留痕，本主线只定义需要被提交的身份与授权事实边界。

### 7.3 与集成主线的接口边界

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/internal/identity-access/integrations/identity-upserts` | 将外部身份投影承接为平台主体或绑定关系 | `source_system`、`external_identity`、`profile_projection` | `user_id`、`binding_id`、`accepted` |
| `POST /api/internal/identity-access/integrations/org-sync-upserts` | 将外部组织投影承接为组织单元变更请求 | `source_system`、`external_request_id`、`org_projection` | `org_unit_id`、`accepted` |

边界约束：

- 集成主线负责协议差异与外部交换治理，本主线负责平台内身份与组织真相承接。
- 企业微信、`SSO`、`LDAP` 的来源差异不应泄漏到业务模块接口层。

## 8. 多组织在 API 层的体现

- 统一使用 `org_id` 表达主体当前所属组织或操作目标组织。
- 用户详情与会话上下文允许返回 `membership_list`，表达一个主体在多个组织中的归属。
- 角色授予、权限授予、数据权限范围都允许携带 `org_id` 或等价组织作用域字段。
- 授权判定接口允许传入 `org_context`，避免把跨组织切换隐式埋入服务端默认逻辑。
- 跨组织场景下，未显式带出组织上下文的管理写接口，应按总平台默认组织选择规则校验，
  但具体默认选取策略下沉到 `Detailed Design`。

## 9. 异步与回调边界

- 登录换会话、权限查询、授权判定优先设计为同步接口。
- 外部认证结果回调、组织目录同步承接、批量授权变更、批量审计导出可受理为异步任务。
- 异步受理成功时返回 `202` 与任务引用；任务编排、补偿、重试策略下沉到
  `Detailed Design`。
- 回调接口只承接外部身份结果或目录变更结果，不承接业务模块私有回调面。
- 回调受理成功不等于主体绑定、组织承接或授权更新已最终完成。

## 10. 需要下沉到 Detailed Design 的内容边界

以下内容应下沉到该主线 [`Detailed Design`](./detailed-design.md)，不在本文展开：

- `IdentitySession`、`IdentityBinding`、`User`、`OrgUnit`、`Role`、`PermissionGrant`、
  `DataScope` 的内部模型、约束与存储组织
- 外部身份归并、冲突处理、主体合并与解绑的内部流程
- 组织规则解析、菜单权限聚合、功能权限判定、数据权限求值的内部算法
- 多组织默认上下文选择、继承、隔离与跨组织授权冲突处理
- 解密下载授权与文档 / 加密主线之间的内部协同过程
- 授权缓存、审计事件装配、异步补偿、目录同步与回调重放防护实现

## 11. 本文结论

本主线 `API Design` 将组织架构、统一认证、角色权限、数据权限、组织规则解析、
解密下载授权判定与身份审计查询统一收口为平台正式接口边界。
它保证流程引擎、合同主档、文档中心、通知、审计与集成主线都基于同一套身份与授权
真相协作，而不把实现细节越界写入 `API Design`。

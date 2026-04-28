# identity-access 组织、角色、权限与数据权限实现验证报告

## 任务范围

- 执行 `102-01-batch-1-foundations-implementation-plan.md` 的 Task 3。
- 范围限定为组织、成员关系、角色、菜单权限、功能权限、显式拒绝、数据权限下推条件、组织规则版本冻结与解析记录。
- 未实现统一授权判定 API、解密下载授权、`agent-os` 或 `integration-hub` 主能力。

## TDD 证据

### 失败测试

- 命令：`mvn -Dtest=IdentityAccessOrgRolePermissionTests test`
- 工作目录：`backend/`
- 结果：失败。
- 失败原因：新增测试在清理 `ia_org_rule_resolution_record` 时失败，H2 报错 `Table "IA_ORG_RULE_RESOLUTION_RECORD" not found`，证明 Task 3 所需组织规则解析表尚未由 Flyway 创建。

### 修复后聚焦测试

- 命令：`mvn -Dtest=IdentityAccessOrgRolePermissionTests test`
- 工作目录：`backend/`
- 结果：通过，`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`。

### 首次 QA 阻断修复失败先行证据

- 命令：`mvn -Dtest=IdentityAccessOrgRolePermissionTests test`
- 工作目录：`backend/`
- 结果：失败，`Tests run: 7, Failures: 3, Errors: 0, Skipped: 0`。
- 失败点 1：`ruleDataScopeResolvesFrozenOrgRuleAndPersistsHitReferences` 断言 `$.allow_predicates[0].values[0] = u-rule-manager` 失败，实际 `RULE` 范围返回空 `values`。
- 失败点 2：`onlyExplicitDenyDataScopeRejectsWithoutFullVisibleAllowPredicate` 断言 `$.effect = DENY` 失败，实际仅 `DENY` 时仍返回 `ALLOW`。
- 失败点 3：`explicitDenyDataScopeIsPreservedWithAllowAndCannotBeOverridden` 断言 `$.effect = CONDITIONAL` 失败，实际 `ALLOW + DENY` 时仍返回 `ALLOW`。

### 首次 QA 阻断修复后聚焦测试

- 命令：`mvn -Dtest=IdentityAccessOrgRolePermissionTests test`
- 工作目录：`backend/`
- 结果：通过，`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`。

## 验证命令与结果

- `mvn test`，工作目录 `backend/`，通过，`Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`。
- `scripts/verify-all.sh`，工作目录仓库根目录，通过，覆盖后端测试、前端 lint/test/build、Docker 镜像构建和 Compose 健康检查。
- `git status --short --untracked-files=all`，显示本任务相关变更文件。

## 覆盖点

- 组织树创建与查询：`/api/org-units`、`/api/org-units/tree`。
- 成员主部门与兼职部门：`/api/org-memberships`。
- 角色授予：`/api/roles`、`/api/role-assignments`。
- 菜单权限：`/api/menus/visible`。
- 功能显式拒绝：`/api/permissions/function-check`。
- 数据范围下推条件：`/api/data-scopes`、`/api/data-scope-predicates`，输出结构化 `DataScopePredicate`，不输出自由 SQL。
- `RULE` 数据范围：`scope_ref` 引用冻结 `org_rule_version_id`，下推时调用组织规则正式解析，生成 `ia_org_rule_resolution_record`，并输出解析得到的受控用户集合条件。
- 显式 `DENY` 数据范围：`ALLOW + DENY` 输出 `effect=CONDITIONAL` 且同时包含 `allow_predicates` 与 `deny_predicates`；仅 `DENY` 输出 `effect=DENY` 且不生成全量可见 `allow_predicates`。
- 组织规则版本冻结与解析证据：`/api/org-rules`、`/api/org-rule-versions`、`/api/org-rule-resolutions`。
- 授权命中证据：`ia_authorization_hit_result` 对 `RULE` 范围额外写入 `hit_type=ORG_RULE`，并保存 `frozen_ref_id` 与 `resolution_record_id`；对每条 `DENY` 数据范围保留 `hit_result=DENY`。
- 审计落库：角色授予、权限拒绝、数据范围命中、组织规则解析写入 `ia_identity_audit`。
- 持久化表存在与关键记录落库：测试覆盖 `ia_org_unit`、`ia_org_membership`、`ia_role`、`ia_role_assignment`、`ia_permission_grant`、`ia_data_scope`、`ia_authorization_decision`、`ia_authorization_hit_result`、`ia_org_rule`、`ia_org_rule_version`、`ia_org_rule_resolution_record`。

## 首次 QA 阻断问题关闭说明

- 问题 1：数据权限 `RULE` 范围未真实实现。已关闭：`RULE` 范围下推时读取冻结 `ia_org_rule_version`，调用正式组织规则解析，写入 `ia_org_rule_resolution_record`，并在 `ia_authorization_hit_result` 写入 `hit_type=ORG_RULE`、`frozen_ref_id`、`resolution_record_id`；输出的 `DataScopePredicate` 为下游可消费的 `owner_user_id IN resolved_subjects` 受控条件。
- 问题 2：显式 `DENY` 数据范围语义错误。已关闭：判定先分类 `ALLOW` 与 `DENY`，仅有 `DENY` 或无有效 `ALLOW` 时写入 `decision_result=DENY`、`decision_reason_code=EXPLICIT_DENY_NO_ALLOW` 或 `NO_DATA_SCOPE`，返回体不生成全量可见条件；`ALLOW + DENY` 返回 `CONDITIONAL`，语义为命中允许且不得命中拒绝。
- 问题 3：测试覆盖不足。已关闭：新增 `RULE` 数据范围解析落库、`ALLOW + DENY`、仅 `DENY` 拒绝、`frozen_ref_id` / `resolution_record_id` 落库断言。
- 问题 4：验证报告不真实。已关闭：本报告追加失败先行证据、修复后验证结果、逐项关闭说明和剩余风险。

## 范围边界说明

- 数据权限下推只生成受控条件对象，由下游仓储消费；未实现业务仓储 SQL 编译器。
- 功能权限检查仅覆盖 Task 3 所需菜单/功能/显式拒绝语义；未实现 Task 4 的统一授权判定 API。
- 组织规则解析实现 `MANAGER_OF_ORG_UNIT` 的最小可运行闭环，用于版本冻结与审计回放证据；未扩展全部规则类型解释器。
- 本次修复未实现 Task 4 的统一授权判定 API、解密下载授权，也未实现 `agent-os` 或 `integration-hub` 主能力。

## 阻断问题列表

无。

## 未覆盖风险

- `RULE` 数据范围已覆盖 `MANAGER_OF_ORG_UNIT` 正式解析、冻结版本、解析记录和命中引用；`STARTER_MANAGER`、`ROLE_IN_ORG_UNIT`、`FIXED_CHAIN` 等更完整组织规则解释器仍属于后续扩展风险，不影响本次 QA 阻断项关闭。

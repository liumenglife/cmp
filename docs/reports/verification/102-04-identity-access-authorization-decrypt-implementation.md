# 102-04 identity-access 统一授权判定与解密下载授权实现验证报告

## 任务范围

- 实现 `identity-access` 内统一解密下载授权判定，不把授权真相下沉到文档中心或加密执行侧。
- 复用 `ia_permission_grant`、`ia_data_scope`、`ia_authorization_decision`、`ia_authorization_hit_result` 与 `ia_identity_audit` 作为持久化真相。
- 仅输出 `decision_id` 等授权命中凭据，不实现真实文件解密、明文包、下载链接或文档版本链。

## TDD 证据

- 先新增测试：`backend/src/test/java/com/cmp/platform/identityaccess/IdentityAccessDecryptDownloadAuthorizationTests.java`。
- 失败测试命令：`mvn -Dtest=IdentityAccessDecryptDownloadAuthorizationTests test`。
- 失败原因：新增的 `/api/authorization/decrypt-download-grants` 与 `/api/authorization/decrypt-download-hits` 端点尚未实现，MockMvc 返回 `404 No static resource api/authorization/decrypt-download-grants` / `404 No static resource api/authorization/decrypt-download-hits`，6 个用例失败。
- 修复后聚焦测试命令：`mvn -Dtest=IdentityAccessDecryptDownloadAuthorizationTests test`。
- 修复后结果：通过，`Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`。

## 覆盖项

- 允许：人员授权命中 `SPECIAL:DECRYPT_DOWNLOAD` 且数据范围命中后返回 `ALLOW`。
- 拒绝：无解密下载授权时返回 `NO_DECRYPT_DOWNLOAD_GRANT`。
- 显式拒绝优先：用户级 `DENY` 覆盖部门级 `ALLOW`。
- 数据范围不命中：特殊授权命中但 `DOCUMENT` 数据范围不命中时返回 `DATA_SCOPE_MISS`。
- 部门授权：`ORG_UNIT` 授权基于当前有效 `ia_org_membership` 命中。
- 人员授权：`USER` 授权直接命中平台主体。
- 撤销后重判：撤销 `ia_permission_grant` 后新请求重新判定并拒绝。
- 审计追踪：命中、拒绝、撤销、显式拒绝和恢复动作均写入 `ia_identity_audit`。
- 持久化：断言 `ia_authorization_decision`、`ia_authorization_hit_result` 存在，并校验关键记录落库。
- 授权凭据传递：响应 `decision_ref` 携带 `decision_id`、`action_code`、`resource_id`、`expires_at`、`request_trace_id`，未实现真实解密下载。

## 验证命令与结果

- `mvn -Dtest=IdentityAccessDecryptDownloadAuthorizationTests test`：通过。
- `mvn test`：通过，`Tests run: 25, Failures: 0, Errors: 0, Skipped: 0`。
- `./scripts/verify-all.sh`：首次 120 秒工具超时，日志显示后端/前端测试与镜像构建已通过，Docker Compose 阶段正在启动/清理；随后以 300 秒超时复跑通过，完成后端测试、前端 lint/test/build、Docker 镜像构建、Compose 健康检查与清理。
- `git status --short --untracked-files=all`：最终状态见交付回执。

## 范围边界

- 未实现 `document-center` 文档版本链。
- 未实现真实文件解密、明文包生成、下载链接、作业状态机或水印清理。
- 未实现 `agent-os` 或 `integration-hub` 主能力。
- 未修改 `docs/planning/*`、Superpowers 规格/计划文件或正式技术设计文件。

## 未覆盖风险

- 当前实现以最小闭环支持 `USER_LIST` / `ORG` / 直接引用型数据范围命中；复杂业务资源字段映射和组织规则型解密下载范围仍应在后续主链路接入真实文档 / 合同资源仓储时继续扩展。

## 首次 QA 不通过修复追加记录

### 修复范围

- 补齐 `POST /api/authorization/decisions` 统一授权判定入口，并新增 `GET /api/authorization/decisions/{decision_id}` 判定详情回查入口。
- 将解密下载授权命中改为复用统一判定函数，`SPECIAL:DECRYPT_DOWNLOAD` 作为高敏 `action_code` 场景处理。
- 补齐 `RULE` 数据范围在解密下载命中链路中的组织规则解析、`ORG_RULE` 命中证据落库、`frozen_ref_id` 与 `resolution_record_id` 回查证据。
- 补齐高敏动作审计链路：审计事件通过 `target_resource_id=decision_id` 关联 `ia_authorization_decision`，再回查 `ia_authorization_hit_result` 中主体、组织、授权项、数据范围与组织规则证据。

### TDD 证据

- 先新增失败测试：`IdentityAccessDecryptDownloadAuthorizationTests` 增加统一授权判定入口、合同 / 流程 / 文档复用、`RULE` 数据范围解密下载、组织规则冻结证据与审计证据链回查断言。
- 失败命令：`mvn -Dtest=IdentityAccessDecryptDownloadAuthorizationTests test`。
- 失败结果：`Tests run: 8, Failures: 2, Errors: 0, Skipped: 0`。
- 失败原因：`POST /api/authorization/decisions` 返回 `404 No static resource api/authorization/decisions`；`RULE` 数据范围解密下载返回 `DATA_SCOPE_MISS`，未写入 `ORG_RULE` 证据。
- 修复后聚焦命令：`mvn -Dtest=IdentityAccessDecryptDownloadAuthorizationTests test`。
- 修复后聚焦结果：通过，`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`。

### QA 问题逐项关闭

- 问题 1：已新增 `POST /api/authorization/decisions`，统一返回 `decision_id`、`decision_result`、`reason_list`、`subject_ref`、`resource_ref`、`matched_permission_list`、`data_scope_hit` 与 `org_rule_evidence_list`；合同、流程、文档动作测试均复用同一入口。
- 问题 2：已让解密下载命中路径支持 `RULE` 数据范围，运行期调用组织规则解析，写入 `ia_org_rule_resolution_record`，并在 `ia_authorization_hit_result` 写入 `hit_type=ORG_RULE`、`frozen_ref_id`、`resolution_record_id`。
- 问题 3：审计事件保留 `actor_user_id`、`target_user_id`、`trace_id`，并通过 `target_resource_id=decision_id` 串联 `ia_authorization_decision` 与命中证据表；`GET /api/authorization/decisions/{decision_id}` 可验证主体、组织、授权项、数据范围和组织规则证据完整链路。
- 问题 4：测试已新增覆盖统一授权判定入口、合同 / 流程 / 文档消费者复用、`RULE` 解密下载命中、组织规则冻结证据、审计证据链完整性。
- 问题 5：本节已追加修复 TDD 证据、逐项关闭说明、验证结果与剩余风险。

### 修复后验证结果

- `mvn -Dtest=IdentityAccessDecryptDownloadAuthorizationTests test`：通过，`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`。
- `mvn test`：通过，`Tests run: 27, Failures: 0, Errors: 0, Skipped: 0`。
- `scripts/verify-all.sh`：通过，完成后端测试、前端依赖安装、lint、test、build、Docker 镜像构建、Compose 健康检查与清理。

### 剩余风险

- 当前统一判定仍是第一批底座最小闭环实现，尚未接入真实合同、文档、流程资源仓储字段映射；复杂资源归属判断应在后续业务主链路接入时继续扩展。

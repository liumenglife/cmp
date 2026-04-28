# identity-access 统一主体与身份协议治理实现验证报告

## 任务范围

- 执行 `102-01-batch-1-foundations-implementation-plan.md` 的 Task 2。
- 范围限定为统一用户主体、身份绑定、会话上下文、外部身份协议交换、绑定预检查、冲突冻结、人工处置与身份审计。
- 未实现组织权限真相、数据权限求值、授权判定、解密下载、`agent-os` 或 `integration-hub` 主能力。

## TDD 证据

### 失败测试

- 命令：`mvn -Dtest=IdentityAccessSubjectProtocolTests test`
- 工作目录：`backend/`
- 结果：失败。
- 失败原因：新增身份治理 MVC 测试后，`POST /api/users`、`POST /api/auth/password/sessions`、`POST /api/auth/sessions/exchanges` 等端点尚未实现，首个失败表现为 `Status expected:<201> but was:<404>`，响应为 `No static resource api/users.`。

### 修复后测试

- 命令：`mvn -Dtest=IdentityAccessSubjectProtocolTests test`
- 工作目录：`backend/`
- 结果：通过，`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`。

### 后端完整测试

- 命令：`mvn test`
- 工作目录：`backend/`
- 结果：通过，`Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`。

## 覆盖清单

- 本地账号进入统一主体：`localAccountLoginCreatesUnifiedSubjectAndSession`。
- 外部换票 / 外部身份协议交换：`externalProtocolExchangeMapsTrustedSsoIdentityToUnifiedSubject`。
- 重复回调幂等：`duplicateExternalCallbackIsIdempotentAndDoesNotCreateSecondExchange`。
- 身份冲突冻结：`candidateSubjectConflictFreezesIdentityAndBlocksSession`。
- 人工解冻 / 人工处置：`manualDispositionCanUnfreezeAndRelinkConflictedBinding`。
- 会话查询：`meReturnsControlledSessionContextWithEmptyRoleAndPermissionSummaries`。
- 审计追踪：`auditViewCanTraceProtocolAndManualEventsByTraceId`。
- 幂等冲突：`sameIdempotencyKeyWithDifferentExchangePayloadReturnsConflict`。

## 全量验证

- 命令：`scripts/verify-all.sh`
- 工作目录：仓库根目录。
- 首次结果：后端测试、前端 lint / test / build 与镜像构建已执行，120 秒超时发生在 Docker Compose 网络创建阶段。
- 复跑命令：`scripts/verify-all.sh`，超时上限扩大到 300 秒。
- 复跑结果：通过。后端测试、前端 lint / test / build、Docker 镜像构建、本地编排健康检查均完成，容器和网络已清理。

## 实现说明

- 新增 `IdentityAccessController` 与最小内存服务边界，承接 `User`、`IdentityBinding`、`IdentitySession`、`ProtocolExchange`、人工处置、幂等记录与身份审计事件。
- 外部来源按 `LOCAL`、`SSO`、`LDAP`、`WECOM` 进入统一主体映射；平台访问令牌只由 `identity-access` 会话接口签发。
- 冲突场景返回 `CONFLICT`、`FROZEN`、`MANUAL_REQUIRED`，人工处置后恢复为 `ACTIVE` 与 `RETRYABLE`。
- 会话上下文返回当前主体、默认组织上下文、空角色摘要和空权限摘要，不提前实现组织权限真相。

## 首次 QA 阻断修复追加证据

### 修复范围

- 将 `User`、`IdentityBinding`、`IdentitySession`、`protocol_exchange`、`binding_precheck`、人工处置与身份审计从内存集合替换为 `JdbcTemplate` 持久化边界。
- 新增 Flyway 迁移：`db/migration/mysql/V1__identity_access_subject_protocol.sql` 与 `db/migration/h2/V1__identity_access_subject_protocol.sql`。
- `local` profile 使用 `spring.datasource.url/username/password` 连接 Docker Compose MySQL；测试使用 H2 与同名最小表结构承接迁移。
- 新增正式存储对象 `ia_identity_binding_precheck` 与 `ia_identity_manual_disposition`，冲突预检查、人工处置和恢复边界均落库。
- `provider + external_identity` 创建改为同主体幂等返回，跨主体冲突拒绝并写入预检查与审计，不再静默覆盖原绑定。
- 外部换票增加最小可信状态机：非 `trusted:` 测试票据 / 代码返回 `401`，写入 `FAILED` 协议交换与拒绝审计；真实 SDK / 验签仍留给后续外部接入任务。

### 本次 TDD 红灯证据

- 命令：`mvn -Dtest=IdentityAccessSubjectProtocolTests test`
- 工作目录：`backend/`
- 结果：失败。
- 失败原因：新增持久化与可信票据测试后，Flyway 未创建身份治理表，`@BeforeEach` 清理首个表失败：`Table "IA_IDEMPOTENCY_RECORD" not found`。

### 本次修复后限定测试

- 命令：`mvn -Dtest=IdentityAccessSubjectProtocolTests test`
- 工作目录：`backend/`
- 结果：通过，`Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`。
- 迁移证据：测试启动日志显示 H2 数据库 `Successfully validated 1 migration`，并执行 `Migrating schema "PUBLIC" to version "1 - identity access subject protocol"`。
- 持久化证据：测试断言 `ia_user`、`ia_identity_binding`、`ia_identity_session`、`ia_protocol_exchange`、`ia_identity_binding_precheck`、`ia_identity_manual_disposition`、`ia_identity_audit` 表存在，并验证用户、绑定、会话、协议交换、预检查、人工处置与审计记录落库。

### 本次后端完整测试

- 命令：`mvn test`
- 工作目录：`backend/`
- 结果：通过，`Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`。

### 本次仓库级验证

- 命令：`scripts/verify-all.sh`
- 工作目录：仓库根目录。
- 首次结果：后端测试、前端 lint / test / build 已通过，Docker 构建阶段因后端镜像首次下载 Maven 依赖超过 300 秒超时。
- 复跑结果：后端测试、前端 lint / test / build、Docker 镜像构建、MySQL / Redis / backend / frontend 健康检查均已完成；600 秒超时发生在脚本退出清理阶段，随后手动 `docker compose down` 清理成功。
- 第三次结果：通过。后端测试、前端 lint / test / build、Docker 镜像构建、本地编排健康检查与自动清理均完成。

## 未覆盖风险

- 真实外部系统 SDK、签名验签、证书链和租户密钥轮换仍未实现，保留给后续外部接入任务。
- 角色、权限、数据权限、授权判定与解密下载将在后续 Task 独立实现。

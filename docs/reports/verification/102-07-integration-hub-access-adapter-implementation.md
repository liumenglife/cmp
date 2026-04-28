# 102-07 integration-hub 统一接入与适配基础实现验证报告

## 任务范围

- 执行 `102-01-batch-1-foundations-implementation-plan.md` Task 7。
- 范围限定为 `integration-hub` 统一接入、适配器运行时基础、企业微信协议交换边界、审计与幂等基础。
- 未实现 Task 8 的完整入站承接、出站派发、回调闭环、补偿、对账和业务主链路回写。

## TDD 证据

### RED

- 命令：`mvn -Dtest=IntegrationHubAccessAdapterTests test`
- 结果：失败。
- 失败原因：新增测试先访问 `ih_integration_audit_event` 等 `integration-hub` 表，Flyway 只迁移到 `v5`，H2 报错 `Table "IH_INTEGRATION_AUDIT_EVENT" not found`。
- 判断：失败来自缺失的 `integration-hub` 持久化主对象，不是测试拼写或环境错误。

### GREEN

- 命令：`mvn -Dtest=IntegrationHubAccessAdapterTests test`
- 结果：通过，`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`。
- 覆盖：签名成功入站、签名失败入站、重复入站、重复回调、端点或凭证失败、企业微信换票移交、审计查询、表存在与关键记录落库、统一外部接入路径边界。

## 验证命令与结果

- `mvn -Dtest=IntegrationHubAccessAdapterTests test`：通过，8 个测试全部通过。
- `mvn test`：通过，50 个测试全部通过。
- `scripts/verify-all.sh`：首次 120 秒工具超时中止在 Docker build 阶段；加长到 300 秒后通过，后端测试、前端 lint/test/build、Docker Compose 健康验证均完成。
- `git status --short --untracked-files=all`：待最终回执记录当前变更清单。

## 关键落库证据

- `ih_inbound_message`：记录统一入站主对象、幂等键、验签结论、归一化报文和处理状态。
- `ih_outbound_dispatch`：记录统一出站派发主对象、目标系统、端点凭证失败结果和重试候选状态。
- `ih_callback_receipt`：记录统一回调主对象、回调幂等键、验签结论和处理状态。
- `ih_integration_binding`：记录企业微信换票后移交给 `identity-access` 的确认引用，不保存身份判定逻辑。
- `ih_integration_job`：记录入站处理和凭证失败出站的集成任务视图。
- `ih_endpoint_profile`：记录端点、凭证引用、超时、限流桶和运行画像。
- `ih_integration_audit_event`：记录入站、重复、签名失败、凭证失败、企业微信换票移交等审计事件。

## 范围边界

- 外部接入端点统一使用 `/api/integration-hub/**`。
- 企业微信换票只输出 `protocol_exchange_ref`、`external_ticket_result`、`handoff_target=identity-access` 和标准身份上下文，不返回平台 `access_token`。
- `integration-hub` 不维护平台用户、组织、权限、合同、文档、流程真相源；只记录交换过程、绑定引用、任务视图和审计事件。
- 当前签名校验、端点解析、凭证引用、字段归一和错误转译为最小可验证基础，未接入真实外部 SDK 或生产密钥托管。

## 阻断问题

无。

## 首次 QA 不通过后的修复证据

### 修复范围

- 本次只修复 Task 7 的统一接入与适配基础：签名可信状态机、入站 / 回调 / 出站幂等冲突、审计证据和测试覆盖。
- 未实现 Task 8 的完整入站承接、出站派发、回调补偿、对账闭环，也未接入合同核心、文档中心或工作流主链路。

### 新增 RED 证据

- 命令：`mvn -Dtest=IntegrationHubAccessAdapterTests test`
- 结果：失败，`Tests run: 13, Failures: 8, Errors: 2, Skipped: 0`。
- 关键失败点：带 `timestamp`、`nonce`、安全画像版本和证书版本的签名请求仍被旧实现按非 `valid-signature` 拒绝为 `401`；重复出站请求触发 `DuplicateKeyException`，唯一约束异常暴露为测试错误。
- 判断：失败准确暴露首次 QA 指出的签名绕过、幂等摘要冲突缺失和出站重复处理缺失，不是测试拼写或环境错误。

### 修复后 GREEN 证据

- 命令：`mvn -Dtest=IntegrationHubAccessAdapterTests test`
- 结果：通过，`Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`。
- 覆盖新增：签名 `nonce` 重放拒绝、同一入站幂等键不同 payload 返回 `40905`、同一回调幂等键不同 payload 返回 `40905`、重复出站同 payload 稳定返回既有 `dispatch_id`、出站同幂等键不同 payload 返回 `40905`。

### 逐项关闭 QA 问题

- 签名校验绕过风险：已从 `X-CMP-Signature == valid-signature` 改为最小可信状态机，校验 `X-CMP-Timestamp`、`X-CMP-Nonce`、`X-CMP-Security-Profile-Version`、`X-CMP-Certificate-Version`、规范化签名串和请求摘要，并通过 `ih_security_nonce` 记录重放窗口内的 nonce。
- 入站幂等冲突未实现：`ih_inbound_message` 增加 `request_digest`；重复幂等键同摘要返回既有资源，不同摘要返回 `40905 IDEMPOTENCY_CONFLICT` 并写 `IDEMPOTENCY_CONFLICT` 审计。
- 回调幂等冲突未实现：`ih_callback_receipt` 增加 `request_digest`；重复幂等键同摘要返回既有回执，不同摘要返回 `40905 IDEMPOTENCY_CONFLICT` 并写审计。
- 出站幂等处理缺失：`ih_outbound_dispatch` 增加 `request_digest`；创建前先查询幂等键，同摘要返回既有 `dispatch_id`，不同摘要返回 `40905 IDEMPOTENCY_CONFLICT`，不再把数据库唯一约束异常暴露为 500。
- 验证报告不真实：本节追加本次修复 TDD 失败证据、通过证据、逐项关闭说明、验证结果和剩余风险。
- 测试覆盖不足：`IntegrationHubAccessAdapterTests` 从 8 个用例扩展到 13 个用例，新增覆盖首次 QA 要求的 5 类场景。

### 修复后验证命令与结果

- `mvn -Dtest=IntegrationHubAccessAdapterTests test`：通过，13 个测试全部通过。
- `mvn test`：通过，55 个测试全部通过。
- `scripts/verify-all.sh`：第一次失败于 Docker Compose 后端启动，根因为本地 `batch1-foundations_cmp-mysql-data` 命名卷保留旧 `V6` Flyway checksum；执行 `docker compose down -v` 清理本地验证残留后，重新运行同一命令通过，后端测试、前端 lint/test/build、Docker 镜像构建和 compose 健康验证均完成。
- `git status --short --untracked-files=all`：由最终交付回执记录。

### 当前未覆盖风险

- 当前签名算法是最小可验证的 `SHA-256` 规范串校验，没有接入真实外部 SDK、生产密钥托管、证书链校验或密钥轮换服务。
- 重放窗口固定为 Task 7 内的最小实现，尚未下沉为可配置 `SecurityProfile` 策略。
- `request_digest` 忽略 `trace_id` 以支持重试追踪号变化；更细的业务字段语义等价判断留待 Task 8 及后续适配器规则扩展。

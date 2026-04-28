# 第八项集成中心入站出站回调补偿对账实现验证报告

## 范围

- 本次实现仅覆盖 `integration-hub` 入站、出站、回调、补偿与对账最小闭环。
- 未实现合同核心主链路、文档中心主链路、工作流主链路或智能增强能力。
- 未引入真实外部 `SDK`、生产密钥托管、证书链校验或密钥轮换服务。
- 外部入口继续统一位于 `/api/integration-hub/**` 命名空间内。

## 失败测试证据

- 先扩展 `IntegrationHubAccessAdapterTests`，新增入站处理推进、失败补偿候选、出站发送尝试、回调确认、补偿发现与执行、对账结果、审计回查、`trace_id` 幂等等价规则测试。
- 首次运行命令：`mvn test -Dtest=IntegrationHubAccessAdapterTests`
- 失败结果：构建失败，`22` 个测试在清理阶段报错，原因是 `ih_reconciliation_diff` 等第八项持久化表尚不存在。
- 关键失败信息：`Table "IH_RECONCILIATION_DIFF" not found; SQL statement: DELETE FROM ih_reconciliation_diff [42102-224]`。

## 修复证据

- 新增 `V7__integration_hub_flow_compensation_reconciliation.sql`，同时覆盖 `H2` 与 `MySQL`。
- 新增 `ih_outbound_attempt`、`ih_recovery_ticket`、`ih_reconciliation_task`、`ih_reconciliation_record`、`ih_reconciliation_diff`。
- 扩展 `IntegrationHubController` / `IntegrationHubService`，支持：
- `POST /api/integration-hub/inbound-messages/{inboundMessageId}/process`
- `POST /api/integration-hub/outbound-dispatches/{dispatchId}/send`
- `POST /api/integration-hub/callback-receipts/{callbackReceiptId}/process`
- `POST /api/integration-hub/compensations/discover`
- `POST /api/integration-hub/compensations/{recoveryTicketId}/execute`
- `POST /api/integration-hub/reconciliations`
- `request_digest` 已按业务字段等价规则处理，递归排除 `trace_id`、`span_id`、`request_trace_id` 等追踪元数据；同幂等键业务字段不同仍返回 `40905 IDEMPOTENCY_CONFLICT`。

## 首次质量审查修复追加证据

- 审查问题一已修复：`request_digest` 对业务字段执行递归稳定规范化，对对象字段名排序，并继续排除追踪元数据；新增 `equivalentInboundWithDifferentObjectFieldOrderReturnsExistingResource` 覆盖同一业务字段不同字段顺序仍返回既有入站资源。
- 审查问题二已修复：回调处理会校验非空 `linked_dispatch_id` 是否存在，不存在时把回调处理状态置为 `FAILED`，返回 `42208 LINKED_DISPATCH_NOT_FOUND`，并写入 `CALLBACK_LINKED_DISPATCH_MISSING` 失败审计；新增 `callbackProcessingWithUnknownLinkedDispatchFailsAndAudits` 覆盖无效关联出站。
- 审查问题三已修复：补偿发现只用同资源 `OPEN` 工单阻断重复发现，已关闭的 `RESOLVED` 工单不再阻断新一轮失败；新工单按同资源历史最大 `ticket_round_no + 1` 生成；新增 `compensationDiscoveryCreatesNextRoundAfterResolvedTicketFailsAgain` 覆盖关闭后再次失败创建第二轮工单。
- 审查问题四已修复：本报告追加记录失败测试、修复证据、验证命令结果与剩余风险，保留首次质量审查暴露问题的修复闭环证据。

## 首次质量审查修复的失败测试证据

- 新增失败测试命令：`mvn test -Dtest=IntegrationHubAccessAdapterTests`
- 失败结果：`Tests run: 25, Failures: 3, Errors: 0, Skipped: 0`，`BUILD FAILURE`。
- 失败原因一：`equivalentInboundWithDifferentObjectFieldOrderReturnsExistingResource` 期望 `202`，实际 `409`，证明字段顺序变化被误判为幂等冲突。
- 失败原因二：`callbackProcessingWithUnknownLinkedDispatchFailsAndAudits` 期望 `422`，实际 `200`，证明无效 `linked_dispatch_id` 被错误处理为成功。
- 失败原因三：`compensationDiscoveryCreatesNextRoundAfterResolvedTicketFailsAgain` 期望 `created_count=1`，实际 `created_count=0`，证明已关闭旧工单阻断了新一轮恢复工单。

## 第二次质量审查修复追加证据

- 审查问题一已修复：`processInbound` 读取并校验 `ingest_status`、`verification_result`、`processing_status`，仅允许 `ACCEPTED`、`VERIFIED` 且处于 `PENDING`、`FAILED`、`WAIT_MANUAL` 的入站记录进入处理；被拒绝或签名失败记录返回 `40906 INVALID_STATE_TRANSITION`，不会被推进为 `SUCCEEDED`。新增 `rejectedInboundCannotBeProcessedAsSuccess` 覆盖被拒绝入站不能处理为成功。
- 审查问题二已修复：`processCallback` 读取并校验 `receipt_status`、`verification_result`、`processing_status`，仅允许 `ACCEPTED`、`VERIFIED` 且处于 `PENDING`、`FAILED`、`WAIT_MANUAL` 的回调记录进入处理；被拒绝或签名失败回调返回 `40906 INVALID_STATE_TRANSITION`，不会被推进为 `SUCCEEDED`。新增 `rejectedCallbackCannotBeProcessedAsSuccess` 覆盖被拒绝回调不能处理为成功。
- 审查问题三已修复：`executeCompensation` 读取并校验 `recovery_status` 与 `recovery_strategy`，仅允许 `OPEN` 且策略为 `REPLAY_OR_RETRY` 的工单执行；已 `RESOLVED` 工单返回 `40906 INVALID_STATE_TRANSITION`，不会重复执行成功审计。新增 `resolvedCompensationTicketCannotBeExecutedAgain` 覆盖已关闭工单不能重复执行。
- 审查问题四已修复：本报告追加第二次质量审查失败测试证据、修复证据、验证命令结果与剩余风险。

## 第二次质量审查修复的失败测试证据

- 新增失败测试命令：`mvn test -Dtest=IntegrationHubAccessAdapterTests`
- 失败结果：`Tests run: 28, Failures: 3, Errors: 0, Skipped: 0`，`BUILD FAILURE`。
- 失败原因一：`rejectedInboundCannotBeProcessedAsSuccess` 期望 `409`，实际 `200`，响应显示 `ingest_status=REJECTED`、`verification_result=REJECTED_SIGNATURE` 的入站记录被推进为 `processing_status=SUCCEEDED`。
- 失败原因二：`rejectedCallbackCannotBeProcessedAsSuccess` 期望 `409`，实际 `200`，响应显示 `receipt_status=REJECTED` 的回调记录被推进为 `processing_status=SUCCEEDED`。
- 失败原因三：`resolvedCompensationTicketCannotBeExecutedAgain` 期望 `409`，实际 `200`，证明已 `RESOLVED` 的补偿工单可被重复执行。

## 第三次质量审查修复追加证据

- 审查问题一已修复：补偿发现的入站候选新增 `ingest_status = 'ACCEPTED'` 与 `verification_result = 'VERIFIED'` 门禁，被拒绝或未验证入站不会创建恢复工单；补偿执行入站资源前再次读取并校验 `ingest_status`、`verification_result`、`processing_status`，不满足门禁时返回 `40906 INVALID_STATE_TRANSITION`，不把入站推进为 `SUCCEEDED`。新增 `rejectedInboundIsNotDiscoveredOrAdvancedByCompensation` 覆盖被拒绝入站既不能被发现，也不能通过手工构造工单执行成功。
- 审查问题二已修复：对账插入 `ih_reconciliation_diff` 前按 `diff_identity_key` 与 `baseline_fingerprint` 查询既有差异；同一差异已存在时跳过新增差异记录，重复对账返回成功且保持既有差异记录稳定，不暴露唯一约束异常。新增 `repeatedReconciliationForSameUnresolvedDiffIsIdempotent` 覆盖同一失败出站重复对账。
- 审查问题三已修复：本报告追加第三次质量审查失败测试证据、修复证据、验证命令结果与剩余风险。

## 第三次质量审查修复的失败测试证据

- 新增失败测试命令：`mvn test -Dtest=IntegrationHubAccessAdapterTests`
- 失败结果：`Tests run: 30, Failures: 1, Errors: 1, Skipped: 0`，`BUILD FAILURE`。
- 失败原因一：`rejectedInboundIsNotDiscoveredOrAdvancedByCompensation` 期望 `created_count=0`，实际 `created_count=1`，证明 `ingest_status=REJECTED`、`verification_result=REJECTED_SIGNATURE` 的入站仍会被补偿发现选中。
- 失败原因二：`repeatedReconciliationForSameUnresolvedDiffIsIdempotent` 第二次对账同一失败出站时抛出 `DuplicateKeyException`，唯一键 `uk_ih_reconciliation_diff(diff_identity_key, baseline_fingerprint)` 冲突，证明重复对账未幂等处理既有未解决差异。

## 第四次质量审查修复追加证据

- 审查问题一已修复：`acceptInbound` 在签名失败分支写入拒绝记录前，先按 `source_system` 与 `idempotency_key` 查询既有入站资源；同一 `source_system`、`message_type`、`external_request_id` 的重复无效入站稳定复用既有资源并返回 `40103 CALLBACK_SIGNATURE_INVALID`，不再暴露唯一约束异常。插入拒绝记录时也捕获并发唯一约束冲突，二次查询既有资源后返回同一业务失败。
- 审查问题二已修复：`acceptCallback` 在签名失败分支写入拒绝记录前，先按 `source_system` 与 `idempotency_key` 查询既有回调资源；同一 `source_system`、`receipt_type`、`external_receipt_id` 的重复无效回调稳定复用既有资源并返回 `40103 CALLBACK_SIGNATURE_INVALID`，不再暴露唯一约束异常。插入拒绝记录时也捕获并发唯一约束冲突，二次查询既有资源后返回同一业务失败。
- 审查问题三已修复：本报告追加第四次质量审查失败测试证据、修复证据、验证命令结果与剩余风险。

## 第四次质量审查修复的失败测试证据

- 新增失败测试命令：`mvn test -Dtest=IntegrationHubAccessAdapterTests`
- 失败结果：`Tests run: 32, Failures: 0, Errors: 2, Skipped: 0`，`BUILD FAILURE`。
- 失败原因一：`repeatedInvalidInboundReusesRejectedRecordWithoutUniqueConstraintError` 第二次提交同一签名失败入站时抛出 `DuplicateKeyException`，唯一键 `UK_IH_INBOUND_IDEM(source_system, idempotency_key)` 冲突，证明重复无效入站暴露数据库异常。
- 失败原因二：`repeatedInvalidCallbackReusesRejectedReceiptWithoutUniqueConstraintError` 第二次提交同一签名失败回调时抛出 `DuplicateKeyException`，唯一键 `UK_IH_CALLBACK_IDEM(source_system, idempotency_key)` 冲突，证明重复无效回调暴露数据库异常。

## 验证命令与结果

- `git status --short --untracked-files=all`
- 初始结果：无输出，开始时工作区干净。
- 最终结果：仅包含本任务相关 5 个变更文件。
- 第二次质量审查修复后结果：`M backend/src/main/java/com/cmp/platform/integrationhub/IntegrationHubController.java`、`M backend/src/test/java/com/cmp/platform/integrationhub/IntegrationHubAccessAdapterTests.java`、`?? backend/src/main/resources/db/migration/h2/V7__integration_hub_flow_compensation_reconciliation.sql`、`?? backend/src/main/resources/db/migration/mysql/V7__integration_hub_flow_compensation_reconciliation.sql`、`?? docs/reports/verification/102-08-integration-hub-flow-compensation-reconciliation-implementation.md`。
- 第三次质量审查修复后结果：`M backend/src/main/java/com/cmp/platform/integrationhub/IntegrationHubController.java`、`M backend/src/test/java/com/cmp/platform/integrationhub/IntegrationHubAccessAdapterTests.java`、`?? backend/src/main/resources/db/migration/h2/V7__integration_hub_flow_compensation_reconciliation.sql`、`?? backend/src/main/resources/db/migration/mysql/V7__integration_hub_flow_compensation_reconciliation.sql`、`?? docs/reports/verification/102-08-integration-hub-flow-compensation-reconciliation-implementation.md`。
- 第四次质量审查修复后结果：`M backend/src/main/java/com/cmp/platform/integrationhub/IntegrationHubController.java`、`M backend/src/test/java/com/cmp/platform/integrationhub/IntegrationHubAccessAdapterTests.java`、`?? backend/src/main/resources/db/migration/h2/V7__integration_hub_flow_compensation_reconciliation.sql`、`?? backend/src/main/resources/db/migration/mysql/V7__integration_hub_flow_compensation_reconciliation.sql`、`?? docs/reports/verification/102-08-integration-hub-flow-compensation-reconciliation-implementation.md`。
- `mvn test -Dtest=IntegrationHubAccessAdapterTests`
- 首次实现结果：`Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- 首次质量审查修复后结果：`Tests run: 25, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- 第二次质量审查修复后结果：`Tests run: 28, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- 第三次质量审查修复后结果：`Tests run: 30, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- 第四次质量审查修复后结果：`Tests run: 32, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `mvn test`
- 首次实现结果：`Tests run: 64, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- 首次质量审查修复后结果：`Tests run: 67, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- 第二次质量审查修复后结果：`Tests run: 70, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- 第三次质量审查修复后结果：`Tests run: 72, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- 第四次质量审查修复后结果：`Tests run: 74, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- `./scripts/verify-all.sh`
- 第一次结果：后端测试、前端 lint、前端测试、前端构建已通过，Docker 构建阶段超过 `120000 ms` 工具超时，未作为成功结论使用。
- 重新运行命令：`./scripts/verify-all.sh`，超时设为 `300000 ms`。
- 重新运行结果：后端 `64` 个测试通过，前端 `1` 个测试通过，前端构建通过，Docker Compose 本地栈构建、健康检查、停止和清理均完成，命令退出成功。
- 首次质量审查修复后重新运行结果：后端 `67` 个测试通过，前端 lint 通过，前端 `1` 个测试通过，前端构建通过，Docker Compose 本地栈构建、健康检查、停止和清理均完成，命令退出成功。
- 第二次质量审查修复后重新运行结果：后端 `70` 个测试通过，前端 lint 通过，前端 `1` 个测试通过，前端构建通过，Docker Compose 本地栈构建、健康检查、停止和清理均完成，命令退出成功。
- 第三次质量审查修复后重新运行结果：后端 `72` 个测试通过，前端 lint 通过，前端 `1` 个测试通过，前端构建通过，Docker Compose 本地栈构建、健康检查、停止和清理均完成，命令退出成功。
- 第四次质量审查修复后重新运行结果：后端 `74` 个测试通过，前端 lint 通过，前端 `1` 个测试通过，前端构建通过，Docker Compose 本地栈构建、健康检查、停止和清理均完成，命令退出成功。

## 覆盖点

- 入站成功处理：`processing_status` 从 `PENDING` 推进到 `SUCCEEDED`，任务和审计同步落库。
- 入站失败处理：`processing_status` 推进到 `FAILED`，任务进入 `FAILED_RETRYABLE`，审计记录失败。
- 出站成功发送：`dispatch_status` 推进到 `SENT`，`ih_outbound_attempt` 记录成功尝试。
- 出站失败补偿：`dispatch_status` 推进到 `WAIT_COMPENSATION`，失败尝试和审计落库。
- 回调成功确认：`CallbackReceipt` 推进到 `SUCCEEDED`，关联出站推进到 `ACKED`。
- 重复回调：保持既有幂等资源并记录 `DUPLICATE_CALLBACK` 审计。
- 补偿任务：可发现失败出站或入站并创建 `ih_recovery_ticket`，执行后更新资源状态与工单状态。
- 对账结果：可生成 `ih_reconciliation_task`、记录一致与差异结果，并对异常出站生成 `MISSING_ON_EXTERNAL` 差异。
- 审计查询：可通过同一 `trace_id` 查到入站、出站、回调、补偿、对账事件。
- 幂等摘要：`trace_id` 变化不会造成业务等价重试冲突，业务字段变化仍触发 `40905`。
- 稳定摘要：同一业务字段在对象内字段顺序不同不会造成幂等冲突。
- 回调关联校验：不存在的 `linked_dispatch_id` 不会把回调标记为成功，并落失败审计。
- 补偿多轮次：同一资源关闭旧工单后再次失败可创建新轮次工单。
- 入站状态机门禁：被拒绝、签名失败或非可处理状态的入站记录不能绕过门禁推进为成功。
- 回调状态机门禁：被拒绝、签名失败或非可处理状态的回调记录不能绕过门禁推进为成功。
- 补偿状态机门禁：已关闭或不可执行策略的补偿工单不能重复执行。
- 补偿发现门禁：被拒绝或未验证入站不会被补偿发现选中。
- 补偿执行入站二次门禁：即使存在异常构造的入站补偿工单，也不能把未通过接入校验的入站推进为成功。
- 重复对账幂等：同一失败出站的同一未解决差异重复对账不会抛唯一约束异常，既有差异记录保持稳定。
- 重复签名失败入站：同一 `source_system`、`message_type`、`external_request_id` 的重复无效入站不会抛唯一约束异常，会复用既有拒绝记录返回 `40103`。
- 重复签名失败回调：同一 `source_system`、`receipt_type`、`external_receipt_id` 的重复无效回调不会抛唯一约束异常，会复用既有拒绝记录返回 `40103`。

## 范围边界

- 当前补偿动作是最小平台侧恢复动作，不伪造外部系统真实成功事实。
- 当前对账是最小数量与状态一致性检查，不替代后续真实外部系统拉取式对账。
- 当前出站发送是受控模拟状态推进，不接入真实 `SAP`、`OA`、企业微信或其他外部 `SDK`。
- 当前回调处理只完成集成中心侧闭环与关联出站状态推进，不越界修改合同、文档或流程真相源。

## 未覆盖风险

- 真实外部系统网络错误、限流错误和供应商错误码映射仍需在生产化专项中扩展。
- 真实证据对象存储、脱敏视图、原始报文访问授权未在本次实现中展开。
- 对账当前覆盖最小一致与差异判定，尚未实现跨时间窗基线复用和人工台账升级闭环。
- 补偿当前覆盖最小自动恢复，尚未实现管理员确认、多人审批或复杂顺序修复。
- `request_digest` 当前覆盖对象字段排序和追踪元数据排除；数组顺序仍按业务输入保留，尚未实现集合语义字段的无序等价规则。
- 回调关联当前校验出站记录存在性；尚未校验外部回调状态与出站目标请求引用的生产级强一致关系。
- 补偿轮次当前按同资源历史最大轮次递增；尚未实现跨差异单、人工工单与外部事故单的统一根工单树。
- 状态机门禁当前覆盖入站、回调和补偿三条被审查入口；出站发送重复执行、对账任务重复触发等更细状态转移策略仍待后续生产化治理补全。
- 重复对账当前对既有差异采取跳过新增策略，尚未把新一轮对账记录与既有差异建立复用引用或差异观察历史。
- 重复签名失败入站与回调当前复用既有幂等资源并追加拒绝审计，尚未扩展为独立的接入拒绝去重台账或按安全事件聚合统计。

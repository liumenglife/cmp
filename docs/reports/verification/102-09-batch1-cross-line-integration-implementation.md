# 第一批跨主线联调实现验证报告

## 结论

- 状态：完成。
- 范围：仅覆盖 `identity-access`、`agent-os`、`integration-hub` 三条底座主线的最小跨主线闭环。
- 结果：本轮授权绕过回归测试先按预期失败，修复后跨主线联调测试、完整后端测试和仓库完整验证脚本均通过。

## 失败测试证据

- 命令：`mvn -Dtest=Batch1CrossLineIntegrationTests test`
- 工作目录：`backend/`
- 首次结果：失败。
- 失败摘要：`Tests run: 4, Failures: 4, Errors: 0, Skipped: 0`
- 关键失败点：
  - `agentToolExecutionMustPassIdentityAccessAuthorizationBeforeToolAudit`：响应缺少 `$.authorization.decision_result`，说明 `agent-os` 尚未消费 `identity-access` 授权判定。
  - `unauthorizedAgentToolExecutionIsRejectedAndLeavesIdentityAuditWithoutCreatingAgentRun`：预期 `403`，实际 `202`，说明未授权主体仍能创建 Agent 运行。
  - `wecomTicketEntersThroughIntegrationHubAndPlatformTokenIsIssuedOnlyByIdentityAccess`：测试签名 helper 与现有签名协议不一致导致 `401`，修正测试 helper 后用于验证企业微信换票边界。
  - `crossLineAuditTraceReturnsIdentityAgentAndIntegrationEvents`：跨主线审计查询能力缺失。

## 本轮授权绕过失败测试证据

- 命令：`mvn -Dtest=Batch1CrossLineIntegrationTests test`
- 工作目录：`backend/`
- 本轮新增测试后的结果：失败。
- 失败摘要：`Tests run: 6, Failures: 2, Errors: 0, Skipped: 0`。
- 关键失败点：
  - `unauthorizedAgentToolTaskIsRejectedEvenWhenRequestDisablesAuthorization`：预期 `403`，实际 `202`；证明 `platform.contract.readonly.lookup` 这类需要授权的数据读取工具仍可被请求方通过 `authorization_required = false` 绕过，且未授权请求创建了 Agent 运行。
  - `directAgentToolInvocationIsRejectedWithoutIdentityAccessAuthorization`：预期 `403`，实际 `200`；证明 `/api/agent-os/runs/{runId}/tools/{toolName}/invoke` 直接工具调用端点只依赖已有运行上下文，未重新接入 `identity-access` 授权判定。

## 修复证据

- 新增 `identity-access` 包内授权网关 `IdentityAccessAuthorizationGateway`，由 `identity-access` 负责写入授权决策、命中证据与身份审计。
- `agent-os` 在 `authorization_required = true` 时先调用身份授权网关；授权拒绝返回 `403 IDENTITY_ACCESS_AUTHZ_DENIED`，且不创建 `ao_agent_run`。
- `agent-os` 创建任务时不再信任请求方布尔字段；当实际场景会调用 `platform.contract.readonly.lookup` 时，即使 `authorization_required = false` 也先调用 `identity-access`，授权拒绝则返回 `403 IDENTITY_ACCESS_AUTHZ_DENIED` 且不创建运行。
- `/api/agent-os/runs/{runId}/tools/{toolName}/invoke` 直接工具调用端点新增独立授权门禁；`platform.contract.*` 工具调用必须先通过 `identity-access` 判定，授权拒绝不写入 `ao_tool_invocation`。
- 授权通过时，`agent-os` 响应携带授权判定引用，并继续通过既有工具契约、沙箱、工具调用审计与工具结果审计执行。
- 既有 `agent-os` 工具沙箱测试补充 `identity-access` 授权夹具，避免测试继续隐含无授权直通假设。
- 新增 `/api/foundations/audit-trace` 只读聚合查询端点，按 `trace_id` 返回三条主线审计事件，不作为外部系统接入口。
- 修正跨主线测试中的签名 helper，使其与 `integration-hub` 现有 `cmp-sha256` 换行签名串一致。

## 验证命令与结果

- `git status --short --untracked-files=all`
  - 结果：通过，显示本任务变更文件，未发现无关已提交外变更被回滚。
- `mvn -Dtest=Batch1CrossLineIntegrationTests test`
  - 工作目录：`backend/`
  - 本轮失败结果：失败，`Tests run: 6, Failures: 2, Errors: 0, Skipped: 0`，失败原因为请求方关闭授权和直接工具调用均绕过 `identity-access`。
  - 修复后结果：通过，`Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`。
- `mvn test`
  - 工作目录：`backend/`
  - 首次修复后结果：失败，`AgentOsToolSandboxGovernanceTests` 中 3 个既有测试缺少授权夹具，暴露旧测试仍假设内部合同工具可无授权直通。
  - 补齐授权夹具后结果：通过，`Tests run: 80, Failures: 0, Errors: 0, Skipped: 0`。
- `./scripts/verify-all.sh`
  - 工作目录：仓库根。
  - 本轮结果：通过；后端测试、前端 lint / test / build、本地 Docker 栈构建与健康检查均完成，容器随后正常清理。

## 覆盖范围

- 企业微信换票经 `integration-hub` 进入，响应只返回移交上下文，不返回平台 `access_token`；平台访问令牌仍由 `identity-access` 的会话交换签发。
- `agent-os` 需要授权的工具任务先由 `identity-access` 写入 `ia_authorization_decision` 和 `ia_identity_audit`，再进入 Agent 运行与工具审计。
- 未授权主体调用跨主线 Agent 工具任务会被拒绝，并留下身份授权拒绝审计，不创建 Agent 运行。
- 请求方省略或关闭 `authorization_required` 不能绕过需要授权的合同读取工具；授权需要由 `agent-os` 按工具/数据访问实际需求强制触发。
- 直接工具调用端点不能复用创建运行时的授权假设；每次 `platform.contract.*` 工具调用都必须独立通过 `identity-access` 判定，拒绝时不创建工具调用记录。
- 跨主线审计查询可按 `trace_id` 同时查询身份授权、Agent 工具执行、集成入站事件。

## 范围边界

- 未提前实现第二批合同核心主链路、第三批依赖业务主链路、第四批智能增强能力或第五批上线准备能力。
- 未重新打开第七项和第八项已全绿的生产级安全风险。
- 未引入真实外部 SDK、生产密钥托管、证书链校验、密钥轮换服务或企业微信真实换票。
- `/api/foundations/audit-trace` 是平台内部只读审计聚合查询，不是新的外部系统接入入口。
- 授权网关仅为第一批跨主线最小胶水能力，仍以 `identity-access` 数据表为唯一身份、权限和数据权限真相源。
- 本轮授权门禁只覆盖第一批已出现的 `platform.contract.*` 内部合同工具调用路径；未扩展真实业务合同查询、写回服务或后续批次主链路。

## 未覆盖风险

- 授权网关当前只覆盖跨主线联调所需的用户级功能授权与用户级数据范围；角色继承、组织规则复杂解析仍由既有 `identity-access` 端点测试覆盖，未在本联调任务重复扩展。
- `agent-os` 对工具是否需要授权的判定当前以第一批内置工具命名和场景为准，尚未生产化为可配置工具权限策略注册表。
- 直接工具调用已具备最小独立授权门禁，但生产级能力仍需补充更细粒度的工具动作映射、调用主体来源绑定、授权缓存失效策略和跨服务防伪造调用凭据；这些属于后续生产化安全专项，不在本轮修复范围内。
- 跨主线审计聚合当前按三条底座主线查询，不包含后续业务主线审计事件。
- 企业微信流程使用受信任模拟票据和既有签名协议，不覆盖真实企业微信 SDK、证书链或生产密钥生命周期。

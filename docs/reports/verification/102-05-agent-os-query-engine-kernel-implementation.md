# Agent OS 最小 QueryEngine / Harness Kernel 实现验证报告

## 任务范围

- 执行计划：`docs/superpowers/plans/102-01-batch-1-foundations-implementation-plan.md` Task 5。
- 实现范围：最小 `AgentTask`、`AgentRun`、`AgentResult`、`EnvironmentEvent`、`PromptSnapshot`、模型工具调用摘要、检查点和审计闭环。
- 非范围：未实现 Task 6 的完整工具注册、沙箱治理、Provider 降级矩阵、长期记忆、人工确认控制台、跨 Session 委派和 `integration-hub` 主能力。

## TDD 证据

### RED

- 命令：`mvn -Dtest=AgentOsQueryEngineKernelTests test`
- 目录：`backend/`
- 结果：失败。
- 失败原因：`AgentOsQueryEngineKernelTests.cleanAgentOsTables` 清理 `ao_agent_audit_event` 时失败，H2 报 `Table "AO_AGENT_AUDIT_EVENT" not found`，说明 `agent-os` 持久化表和接口尚未实现。

### GREEN

- 命令：`mvn -Dtest=AgentOsQueryEngineKernelTests test`
- 目录：`backend/`
- 结果：通过，`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`。

### 首次 QA 修复 RED

- 命令：`mvn -Dtest=AgentOsQueryEngineKernelTests test`
- 目录：`backend/`
- 结果：失败，`Tests run: 8, Failures: 1, Errors: 0, Skipped: 0`。
- 失败原因：新增环境事件派生任务闭环断言后，`GET /api/agent-os/runs/{run_id}` 返回 `run_status=PENDING`，期望为 `SUCCEEDED`；派生任务未进入最小 `QueryEngine` 主循环，也未生成结果、Prompt 快照、检查点和完整审计证据链。

### 首次 QA 修复 GREEN

- 命令：`mvn -Dtest=AgentOsQueryEngineKernelTests test`
- 目录：`backend/`
- 结果：通过，`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`。
- 修复点：`agent_processing_required=true` 的环境事件派生 `ENVIRONMENT_TRIAGE` 任务后，复用最小 `executeMinimalLoop` 推进运行，生成 `AgentResult`、`PromptSnapshot`、`latest_checkpoint_summary`、模型工具调用摘要和审计事件，并补齐 `GET /api/agent-os/results/{result_id}` 查询入口。

## 覆盖点

- 成功路径：合同风险提示任务完成一轮最小 `QueryEngine` 循环，落库任务、运行、结果、Prompt 快照、模型工具调用、检查点和审计。
- 模型失败：模型调用失败时任务、运行、结果均进入失败状态并记录审计。
- 预算拒绝：Provider 预算拒绝时阻断模型工具调用并记录失败码。
- 外部取消：运行取消后任务和运行状态保持一致，并记录取消审计。
- 最大循环：超过最大循环后写入检查点并失败收口。
- 结果契约：结果返回 `SUMMARY`、结构化风险等级和合同引用。
- 审计缺失阻断：缺少必要审计时阻断结果释放。
- 持久化表与关键记录：通过 H2 + Flyway 验证核心表存在和关键记录落库。
- 环境事件：支持创建、查询，并在需要 Agent 处理时派生任务；派生任务进入最小 `QueryEngine` 主循环，可通过运行、任务结果、结果详情和审计接口验证任务主键、运行主键、结果主键、Prompt 快照、检查点与 trace。
- 幂等：相同幂等键和相同请求返回既有任务，不同请求返回 `40905 IDEMPOTENCY_CONFLICT`。

## 首次 QA 问题关闭说明

- 问题 1：环境事件派生的 `AgentTask` 未进入最小 `QueryEngine` 主循环。已关闭，派生任务现在创建任务与运行后立即执行最小循环，运行可查询为 `SUCCEEDED`，并保留 trace、Prompt 快照、检查点和审计事件。
- 问题 2：环境事件派生任务只创建 `ao_agent_task` / `ao_agent_run` 且运行停在 `PENDING`。已关闭，派生任务现在写入 `ao_agent_result`、`ao_prompt_snapshot`、`latest_checkpoint_summary`、`ao_tool_invocation` 和关键审计事件，任务 `final_result_id` 与运行状态同步推进。
- 问题 3：报告写“未覆盖风险：无”不真实。已关闭，本报告补充首次 QA 修复 RED/GREEN 证据、逐项关闭说明、完整验证结果，并在“未覆盖风险”中如实保留最小实现边界风险。
- 问题 4：测试未覆盖派生任务闭环。已关闭，`environmentEventCanBeCreatedQueriedAndLinkedToAgentTask` 已覆盖派生运行查询、派生结果查询、`result_id` 查询、Prompt 快照、检查点和审计断言。

## 验证命令

- `mvn -Dtest=AgentOsQueryEngineKernelTests test`：通过。
- `mvn test`：通过，`Tests run: 35, Failures: 0, Errors: 0, Skipped: 0`。
- `scripts/verify-all.sh`：首次以 120 秒工具超时中断在 Docker 镜像导出阶段，未作为通过证据；随后以 300 秒超时重跑通过，完成后端测试、前端 lint/test/build、Docker 镜像构建与 Docker Compose 健康验证。

## 边界说明

- 使用 `JdbcTemplate + Flyway` 作为任务、运行、结果和审计真相源，未使用内存集合作为正式状态源。
- 模型调用以最小模型工具摘要模拟，保持 Provider 抽象边界，不向业务接口暴露 Provider 私参。
- 环境事件仅覆盖最小入站、查询与派生任务关联，不实现完整事件分类、补偿或集成主链路。
- 本次修复只关闭 Task 5 首次 QA 阻断问题，未提前实现 Task 6 的完整工具契约、沙箱治理、长期记忆、人工确认控制台、跨 Session 委派、`integration-hub` 主能力或合同核心主链路。

## 阻断问题

无。

## 未覆盖风险

- 当前仍是最小 `Harness Kernel`：模型调用为本地摘要模拟，未验证真实 Provider 超时、降级、成本配额和真实模型响应差异。
- 环境事件只覆盖 `agent_processing_required=true` 的最小派生闭环，未实现事件分类算法、补偿调度、跨系统回调异常治理或批量事件归并。
- 工具契约、沙箱、长期记忆、人工确认、跨 Session 委派和 `integration-hub` 对接仍属于后续任务范围，本报告不将其声明为已覆盖。

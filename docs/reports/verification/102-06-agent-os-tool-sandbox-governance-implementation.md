# Agent OS 工具契约、沙箱与治理挂点实现验证报告

## 范围

- 任务：`102-01-batch-1-foundations-implementation-plan.md` Task 6。
- 工作区：`.worktrees/feature/batch1-foundations`。
- 实现范围：工具注册与发现、工具 schema 快照、工具授权、沙箱执行边界、输出卸载、Provider 预算/降级记录、工具调用与结果配对审计、验证报告入口。
- 明确未实现：长期记忆、人工确认控制台、跨 Session 委派、`integration-hub` 主能力、合同核心主链路。

## TDD 证据

1. 先新增失败测试：`backend/src/test/java/com/cmp/platform/agentos/AgentOsToolSandboxGovernanceTests.java`。
2. 红灯命令：`mvn -Dtest=AgentOsToolSandboxGovernanceTests test`。
3. 红灯结果：失败，原因是 `ao_verification_report` 等 Task 6 持久化表不存在，错误为 `Table "AO_VERIFICATION_REPORT" not found`。
4. 绿灯命令：`mvn -Dtest=AgentOsToolSandboxGovernanceTests test`。
5. 绿灯结果：通过，`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`。

## 覆盖项

- 工具注册与工具发现成功：覆盖 `ao_tool_definition`、`ao_tool_definition_snapshot`、`ao_tool_grant`。
- 模型工具成功：`model.generate_text` 作为 `MODEL` 工具通过工具契约调用。
- 平台只读工具成功：`platform.contract.readonly.lookup` 作为 `INTERNAL_SERVICE` 只读工具调用。
- 工具失败：`simulate=FAILURE` 写入失败结果。
- 工具超时：`simulate=TIMEOUT` 写入 `TIMED_OUT` 结果。
- 沙箱拒绝不可绕过：受控写工具和 `bypass_sandbox=true` 均返回 `SANDBOX_REJECTED`。
- 输出卸载：`simulate=LARGE_OUTPUT` 只返回摘要与 `artifact_ref`。
- Provider 降级：`simulate=PRIMARY_CIRCUIT_OPEN` 记录 `MOCK_FALLBACK_PROVIDER` 与 `DEGRADED`。
- 工具结果断对检测：缺少 `ao_tool_result` 时返回 `TOOL_PAIR_BROKEN` 并写审计。
- 验证报告生成：`ao_verification_report` 固化证据包、检查项、失败证据、性能基线和回归入口。
- 持久化表存在和关键记录落库：新增 H2/MySQL Flyway V5 迁移并在测试中断言关键表和记录。
- 审计链配对：每个工具调用写入 `TOOL_INVOKED`，每个结果写入 `TOOL_RESULT_RECORDED`。

## 验证命令与结果

- `mvn -Dtest=AgentOsToolSandboxGovernanceTests test`：通过。
- `mvn test`：通过，`Tests run: 40, Failures: 0, Errors: 0, Skipped: 0`。
- `scripts/verify-all.sh`：第一次 120s 超时，已完成后端、前端 lint/test/build，超时发生在 Docker build 阶段。
- `scripts/verify-all.sh`：使用 300s 超时重跑通过，完成后端测试、前端 lint/test/build、Docker Compose 构建与健康检查。

## 未覆盖风险

- 当前模型 Provider、平台只读工具和沙箱执行器为最小可验证实现，尚未接真实模型网关、真实合同文档服务或对象存储。
- 当前验证报告入口为运维审计面最小实现，独立 QA 仍需基于本报告和测试证据另行复核。

## 阻断问题

无。

## 首次 QA 阻断修复追加证据

### 修复范围

- 仅修复 Task 6 的 `agent-os` 工具结果契约、断对检测和验证报告治理挂点。
- 未实现长期记忆、人工确认控制台、跨 Session 委派、`integration-hub` 主能力或合同核心主链路。

### TDD 红灯证据

1. 先新增/调整失败测试：`AgentOsQueryEngineKernelTests` 覆盖 `NORMAL` 与 `MODEL_FAIL` 模型调用必须写入 `ao_tool_result` 和 `TOOL_INVOKED` / `TOOL_RESULT_RECORDED` 成对审计。
2. 先新增失败测试：`AgentOsToolSandboxGovernanceTests` 覆盖一个 invocation 缺 result、另一个 invocation 重复 result 时必须按 `tool_invocation_id` 判定 `BROKEN`。
3. 先新增失败测试：`AgentOsToolSandboxGovernanceTests` 覆盖验证报告在缺少工具审计配对时不得生成 `PASSED`。
4. 红灯命令：`mvn -Dtest=AgentOsQueryEngineKernelTests test`。
5. 红灯结果：失败 2 项，`NORMAL` 与 `MODEL_FAIL` 路径均因 `ao_tool_result` 记录数为 0 失败。
6. 红灯命令：`mvn -Dtest=AgentOsToolSandboxGovernanceTests test`。
7. 红灯结果：失败 2 项，总数式断对检查将缺失+重复误判为 `PAIRED`，验证报告缺少严格检查项并误生成 `PASSED`。

### 逐项关闭说明

- 已关闭问题 1：`createModelInvocation` 现在通过 `model.generate_text` 工具定义写入 `ao_tool_invocation`、`ao_tool_result`、`ao_provider_usage`，并为成功和失败路径写入 `TOOL_INVOKED` / `TOOL_RESULT_RECORDED` 成对审计；`MODEL_FAIL` 写入失败 result 和 `MODEL_CALL_FAILED`。
- 已关闭问题 2：`toolPairCheck` 改为按 `tool_invocation_id` 聚合，分别统计缺失 result、重复 result、孤儿 result；任一异常均返回 `BROKEN` 并写入 `TOOL_PAIR_BROKEN` 审计。当前未新增唯一约束，以便保留检测层对历史脏数据和手工插入重复数据的显式防御能力。
- 已关闭问题 3：验证报告新增 `TOOL_RESULT_PAIRING` 和 `TOOL_AUDIT_PAIRING` 两个检查项，只有结果配对和审计配对均通过时才生成 `PASSED`；任一断链时生成 `FAILED` 和失败证据。
- 已关闭问题 4：新增测试覆盖 `NORMAL`、`MODEL_FAIL`、缺失+重复 result、验证报告断链不得通过四类场景。
- 已关闭问题 5：本节追加了修复 TDD 证据、关闭说明、验证结果和剩余风险。

### 修复后验证结果

- `mvn -Dtest=AgentOsToolSandboxGovernanceTests test`：通过，`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`。
- `mvn -Dtest=AgentOsQueryEngineKernelTests test`：通过，`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`。
- `mvn test`：通过，`Tests run: 42, Failures: 0, Errors: 0, Skipped: 0`。
- `scripts/verify-all.sh`：第一次 300s 超时，已完成后端、前端 lint/test/build 和容器健康检查，超时发生在 Docker Compose 收尾停止阶段。
- `scripts/verify-all.sh`：使用 600s 超时重跑通过，完成后端测试、前端 lint/test/build、Docker Compose 构建、健康检查与清理。

### 剩余风险

- 当前模型 Provider 和平台工具仍是 Task 6 最小可验证实现，尚未接真实模型网关、真实合同文档服务或对象存储。
- 当前重复 result 通过检测层防御并阻断通过，未在迁移层添加唯一约束；后续如进入真实数据库治理，可在数据清理后补充唯一约束。

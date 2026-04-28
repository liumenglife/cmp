# 第二批流程引擎审批定义与平台运行时基础能力验证报告

## 状态

- 结论：通过。
- 范围：仅覆盖第二批 Task 4 `workflow-engine` 审批定义与平台运行时基础能力。
- 未推进范围：未实现 Task 5 合同文档挂接闭环，未实现 Task 6 统一审批入口、`OA` 主路径桥接与合同状态回写闭环。

## RED 失败证据

1. 流程定义发布测试先失败。
   - 命令：`mvn -Dtest=Batch2CoreChainContractTests#workflowDefinitionPublishesVersionOnlyWhenApprovalNodesHaveOrganizationBinding test`
   - 失败原因：`POST /api/approval-engine/process-definitions` 返回 `404`，期望 `201`。
   - 结论：流程定义草稿、发布与组织绑定校验接口尚未实现，失败原因明确。

2. 平台审批运行时测试先失败。
   - 命令：`mvn -Dtest=Batch2CoreChainContractTests#workflowRuntimeStartsPublishedProcessAndAdvancesSerialParallelCountersignRejectAndTerminatePaths test`
   - 失败原因：已发布流程版本后，`POST /api/approval-engine/processes` 返回 `404`，期望 `202`。
   - 结论：平台审批实例发起与任务运行时接口尚未实现，失败原因明确。

## GREEN 验证结果

1. 流程定义发布测试通过。
   - 命令：`mvn -Dtest=Batch2CoreChainContractTests#workflowDefinitionPublishesVersionOnlyWhenApprovalNodesHaveOrganizationBinding test`
   - 结果：`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。

2. 平台审批运行时测试通过。
   - 命令：`mvn -Dtest=Batch2CoreChainContractTests#workflowRuntimeStartsPublishedProcessAndAdvancesSerialParallelCountersignRejectAndTerminatePaths test`
   - 结果：`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。

3. 第二批核心链路测试通过。
   - 命令：`mvn -Dtest=Batch2CoreChainContractTests test`
   - 结果：`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`。

4. 后端完整测试通过。
   - 命令：`mvn test`
   - 结果：`Tests run: 88, Failures: 0, Errors: 0, Skipped: 0`。

## 覆盖范围

- 流程定义草稿创建、当前草稿版本、发布版本快照、发布审计、停用审计。
- 审批节点组织绑定必填校验，未绑定审批节点发布失败。
- 基于已发布版本发起平台审批实例。
- 生成审批任务、参与人快照、任务中心挂接摘要。
- 审批通过、驳回、终止动作记录和审批摘要推进。
- 串行、并行、会签基础推进。

## 范围边界

- 平台审批运行时已具备独立承接骨架，不依赖 `OA` 才能运行。
- 本次未把流程结果回写合同主状态，避免越界到 Task 6。
- 本次未实现合同文档挂接增强，避免越界到 Task 5。
- 当前实现仍使用既有内存态最小实现方式，未下沉正式数据库表结构。

## 遗留问题

- 正式持久化、并发锁、幂等键、超时扫描、通知投递、`OA` 桥接补偿仍待后续批次或专项任务按计划推进。

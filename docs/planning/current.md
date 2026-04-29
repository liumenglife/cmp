# 当前批次真相

## 主目标

- 第一批底座主线已合并到 `main` 并完成本地收口，当前真相切换为第二批合同核心主链路编码阶段。
- 第二批计划目标是推进 `contract-core`、`document-center`、`workflow-engine` 三条主链路，形成合同创建、编辑、文档挂接、审批承接与状态回写的核心闭环。
- 当前分支为 `feature/contract-core-chain`，隔离工作区为 `.worktrees/feature/contract-core-chain`，第二批编码只在该工作树推进。

## 成功定义

- 第一批底座主线已通过独立 QA 与发布前门禁，`PR #2` 已合并到 `main`，功能分支和旧工作树已清理。
- 已基于最新 `main` 创建第二批隔离工作区和分支 `feature/contract-core-chain`。
- 新工作树基线验证 `./scripts/verify-all.sh` 已通过：后端 80 个测试通过，前端检查、测试、构建通过，容器健康检查通过并完成清理。
- 第二批必须按测试驱动开发与独立 QA 闭环执行，每个功能点全绿后先提交，再进入下一个功能点。

## 非目标

- 不在 `main` 或已清理的第一批分支继续实现第二批合同核心主链路代码。
- 不提前实现第三批依赖业务能力、第四批智能增强能力或第五批联调上线准备任务。
- 不绕过第一批 PR 审查流程直接合并。
- 不让 `current.md` 同时保留第一批已完成长列表和第二批当前状态。

## 当前阶段

- 阶段：第二批合同核心主链路编码阶段。
- 分支：`feature/contract-core-chain`。
- 隔离工作区：`.worktrees/feature/contract-core-chain`。
- 规格：[`102-cmp-implementation-execution-spec.md`](../superpowers/specs/102-cmp-implementation-execution-spec.md)。
- 当前批次计划：[`102-02-batch-2-core-chain-implementation-plan.md`](../superpowers/plans/102-02-batch-2-core-chain-implementation-plan.md)。

## 编码阶段任务清单

- [✓] 第一批底座主线合并、同步、旧分支和旧工作树清理。
- [✓] 第二批工作树与分支创建，基线验证通过。
- [✓] 第二批批次启动门禁与接口冻结：已通过独立 QA 复审，没有问题。
- [✓] `contract-core` 合同主档与编辑基础能力：已通过独立 QA，没有问题。
- [✓] `document-center` 文档主档与版本链基础能力：已通过独立 QA，没有问题。
- [✓] `workflow-engine` 审批定义与平台运行时基础能力：已通过独立 QA，没有问题。
- [✓] 合同文档挂接闭环：已通过独立 QA，没有问题。
- [✓] 审批发起、审批承接与合同状态回写闭环：已通过独立 QA，没有问题。
- [✓] 第二批最小端到端闭环验证：已通过独立 QA，没有问题，提交为 `79523ff`。
- [✓] 第二批整体质量收口：已通过独立 QA，没有问题。

## 子代理执行协议

- 每个编码任务由新的实现子代理执行，主 Agent 只负责派发、收口和回写 planning 真相。
- 实现子代理必须遵循测试驱动开发：没有先失败的测试或验证，不得编写对应生产代码。
- 每个功能点完成后必须派发新的独立 QA 子代理；QA 结论必须基于事实，存在问题就明确列出问题点，不存在问题就明确报告通过。
- 首次 QA 通过且未发生修复时，不执行第二次 QA；QA 未通过时，不进入下一个功能点，主 Agent 继续派发修复子代理，并在修复后再次派发独立 QA 子代理复审。
- 功能点全绿后，在进入下一个功能点前使用 `liumenglife <liumenglife@163.com>` 作为提交者信息提交。

## 当前正在做

- [✓] `PR #2` 已合并到 `main`，合并提交为 `dc05193`。
- [✓] 本地 `main` 已同步到最新状态，旧分支与旧工作树已清理。
- [✓] 已创建第二批分支与工作树：`feature/contract-core-chain`。
- [✓] 第二批第一项已完成实现交付、问题修复与独立 QA 复审，结论为通过，没有问题。
- [✓] 第二批第一项已提交，提交为 `0554cdf`。
- [✓] 第二批第二项 `contract-core` 合同主档与编辑基础能力已完成实现与独立 QA，结论为通过，没有问题。
- [✓] 第二批第二项已提交，提交为 `f427682`。
- [✓] 第二批第三项 `document-center` 文档主档与版本链基础能力已完成实现与独立 QA，结论为通过，没有问题。
- [✓] 第二批第三项已提交，提交为 `02abc87`。
- [✓] 第二批第四项 `workflow-engine` 审批定义与平台运行时基础能力已完成实现与独立 QA，结论为通过，没有问题。
- [✓] 第二批第四项已提交，提交为 `5100490`。
- [✓] 第二批第五项合同文档挂接闭环已完成实现与独立 QA，结论为通过，没有问题。
- [✓] 第二批第五项已提交，提交为 `4632600`。
- [✓] 第二批第六项审批发起、审批承接与合同状态回写闭环已完成实现与独立 QA，结论为通过，没有问题。
- [✓] 第二批第六项已提交，提交为 `7a7c4df`。
- [✓] 第二批第七项最小端到端闭环验证已完成实现与独立 QA，结论为通过，没有问题。
- [✓] 第二批第七项已提交，提交为 `79523ff`。
- [✓] 第二批整体质量收口已完成，报告为 [`102-18-batch2-release-gate-qa.md`](../reports/qa/102-18-batch2-release-gate-qa.md)，结论为通过，没有问题。

## 已完成里程碑

- [✓] `101` 最小可运行工程骨架已完成、推送 PR 并合并到 `main`。
- [✓] 第一批底座主线全部功能点、跨主线联调、独立 QA、发布前门禁与 PR 合并已完成。
- [✓] 第一批发布前门禁报告为 [`102-10-batch1-release-gate-qa.md`](../reports/qa/102-10-batch1-release-gate-qa.md)，结论为通过，没有问题。
- [✓] 第二批隔离工作树启动基线验证通过。

## 当前阻塞

- 当前无需求阻塞。
- 当前无验证阻塞。

## 活跃支线

- [✓] `feature/batch1-foundations` 分支收尾：归档、命名收口、推送、PR 创建、合并与清理。
- [✓] `feature/contract-core-chain` 第二批合同核心主链路编码与整体质量收口。

## 下一步唯一动作

- 第二批合同核心主链路已完成并通过整体质量收口；下一步等待选择集成方式。

## 恢复提示

- 恢复时先读本文件、[`history.md`](./history.md)、[`decisions.md`](./decisions.md)。
- 第一批底座主线历史已迁入 [`history.md`](./history.md)，不要把第一批长列表写回 `current.md`。
- 第二批正式执行依据为 [`102-cmp-implementation-execution-spec.md`](../superpowers/specs/102-cmp-implementation-execution-spec.md) 与 [`102-02-batch-2-core-chain-implementation-plan.md`](../superpowers/plans/102-02-batch-2-core-chain-implementation-plan.md)。
- 第二批当前工作树为 `.worktrees/feature/contract-core-chain`，不要在主工作区直接编码。

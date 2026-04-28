# 当前批次真相

## 主目标

- 第一批底座主线已完成并归档，当前真相切换为第二批合同核心主链路启动前状态。
- 第二批计划目标是推进 `contract-core`、`document-center`、`workflow-engine` 三条主链路，形成合同创建、编辑、文档挂接、审批承接与状态回写的核心闭环。
- 当前分支 `feature/batch1-foundations` 仅用于第一批收口、推送和创建面向 `main` 的 PR；第二批编码不得在本分支继续实施。

## 成功定义

- 第一批底座主线已通过独立 QA 与发布前门禁，并保留完整历史恢复入口。
- Superpowers 第二至第五批计划文件已按 `102-02` 至 `102-05` 编号规范重命名，引用同步更新。
- `feature/batch1-foundations` 已推送到 `origin` 并创建面向 `main` 的 PR，等待审查，不自动合并。
- 下一批编码启动前，必须基于最新 `main` 创建新的隔离工作区和分支，再按第二批计划进入 TDD 与独立 QA 闭环。

## 非目标

- 不在 `feature/batch1-foundations` 分支继续实现第二批合同核心主链路代码。
- 不提前实现第三批依赖业务能力、第四批智能增强能力或第五批联调上线准备任务。
- 不绕过第一批 PR 审查流程直接合并。
- 不让 `current.md` 同时保留第一批已完成长列表和第二批当前状态。

## 当前阶段

- 阶段：第一批底座主线完成后的归档、命名收口、推送与 PR 创建。
- 分支：`feature/batch1-foundations`。
- 隔离工作区：`.worktrees/feature/batch1-foundations`。
- 规格：[`102-cmp-implementation-execution-spec.md`](../superpowers/specs/102-cmp-implementation-execution-spec.md)。
- 下一批计划：[`102-02-batch-2-core-chain-implementation-plan.md`](../superpowers/plans/102-02-batch-2-core-chain-implementation-plan.md)。

## 编码阶段任务清单

- [✓] 第一批底座主线实现、跨主线联调、独立 QA 与发布前门禁已完成。
- [•] 第一批归档、后续计划文件编号重命名、推送和 PR 创建。
- [ ] 第二批合同核心主链路启动前规划核对。

## 子代理执行协议

- 每个编码任务由新的实现子代理执行，主 Agent 只负责派发、收口和回写 planning 真相。
- 实现子代理必须遵循测试驱动开发：没有先失败的测试或验证，不得编写对应生产代码。
- 每个功能点完成后必须派发新的独立 QA 子代理；QA 结论必须基于事实，存在问题就明确列出问题点，不存在问题就明确报告通过。
- 首次 QA 通过且未发生修复时，不执行第二次 QA；QA 未通过时，不进入下一个功能点，主 Agent 继续派发修复子代理，并在修复后再次派发独立 QA 子代理复审。
- 功能点全绿后，在进入下一个功能点前使用 `liumenglife <liumenglife@163.com>` 作为提交者信息提交。

## 当前正在做

- [✓] 第一批底座主线已通过发布前门禁并提交 `b529fc2`。
- [•] 将第一批 planning 真相迁入 `history.md`，并把 `current.md` 切换到第二批启动前状态。
- [•] 将第二至第五批 Superpowers 计划文件重命名为 `102-02` 至 `102-05` 编号。
- [ ] 推送 `feature/batch1-foundations` 并创建面向 `main` 的 PR。

## 已完成里程碑

- [✓] `101` 最小可运行工程骨架已完成、推送 PR 并合并到 `main`。
- [✓] 第一批底座主线全部功能点、跨主线联调、独立 QA 与发布前门禁已完成。
- [✓] 第一批发布前门禁报告为 [`102-10-batch1-release-gate-qa.md`](../reports/qa/102-10-batch1-release-gate-qa.md)，结论为通过，没有问题。

## 当前阻塞

- 当前无需求阻塞。
- 当前无验证阻塞。

## 活跃支线

- [•] `feature/batch1-foundations` 分支收尾：归档、命名收口、推送和 PR 创建。

## 下一步唯一动作

- 完成命名收口、验证、提交、推送并创建面向 `main` 的 PR。

## 恢复提示

- 恢复时先读本文件、[`history.md`](./history.md)、[`decisions.md`](./decisions.md)。
- 第一批底座主线历史已迁入 [`history.md`](./history.md)，不要把第一批长列表写回 `current.md`。
- 第二批正式执行依据为 [`102-cmp-implementation-execution-spec.md`](../superpowers/specs/102-cmp-implementation-execution-spec.md) 与 [`102-02-batch-2-core-chain-implementation-plan.md`](../superpowers/plans/102-02-batch-2-core-chain-implementation-plan.md)。

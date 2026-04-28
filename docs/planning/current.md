# 当前批次真相

## 主目标

- 在 `.worktrees/feature/batch1-foundations` 隔离工作区推进第一批底座主线实现。
- 本批次以 `identity-access`、`agent-os`、`integration-hub` 三条底座主线为范围，建立统一身份、组织、权限、任务、审计、集成边界与 `agent-os` 最小 `QueryEngine / Harness Kernel` 闭环。
- 每个功能点必须按 `编码 -> QA -> 修复（如需要）-> QA（有修复）` 循环推进，全绿后先提交，再进入下一个功能点；首次 QA 通过且未发生修复时，不重复执行第二次 QA。

## 成功定义

- `identity-access` 成为统一身份、组织、权限、数据权限和授权判定真相源。
- `agent-os` 完成最小 `QueryEngine / Harness Kernel` 可运行、可恢复、可审计闭环。
- `integration-hub` 成为唯一外部接入、入站、出站、回调、绑定、补偿与对账治理入口。
- 三条主线之间无重复底座、无绕行入口、无审计断链。
- 每个功能点均有 TDD 证据、自动化验证证据、独立 QA 结论和对应提交。

## 非目标

- 不提前实现第二批合同核心主链路、第三批依赖业务能力、第四批智能增强能力或第五批联调上线准备任务。
- 不让业务模块私建用户、组织、权限、任务、审计、外部交换、合同、文档或流程真相。
- 不把 `agent-os` 漂移为面向开发者写代码的 Coding Agent OS。
- 不把 `OA`、企业微信、`CRM`、`SF`、`SRM`、`SAP` 本体改造写成 `CMP` 内部实现责任。

## 当前阶段

- 阶段：第一批底座主线编码实现。
- 分支：`feature/batch1-foundations`。
- 隔离工作区：`.worktrees/feature/batch1-foundations`。
- 规格：[`102-cmp-implementation-execution-spec.md`](../superpowers/specs/102-cmp-implementation-execution-spec.md)。
- 计划：[`102-01-batch-1-foundations-implementation-plan.md`](../superpowers/plans/102-01-batch-1-foundations-implementation-plan.md)。

## 编码阶段任务清单

- [✓] 第一批启动门禁核对：核对正式输入、范围边界、环境条件与验收样例。
- [•] `identity-access` 统一主体与身份协议治理。
- [ ] `identity-access` 组织、角色、权限与数据权限。
- [ ] `identity-access` 统一授权判定与解密下载授权。
- [ ] `agent-os` 最小 `QueryEngine / Harness Kernel`。
- [ ] `agent-os` 工具契约、沙箱与治理挂点。
- [ ] `integration-hub` 统一接入与适配基础。
- [ ] `integration-hub` 入站、出站、回调、补偿与对账。
- [ ] 第一批跨主线联调。
- [ ] 第一批质量审查与发布前门禁。

## 子代理执行协议

- 每个编码任务由新的实现子代理执行，主 Agent 只负责派发、收口和回写 planning 真相。
- 实现子代理必须遵循测试驱动开发：没有先失败的测试或验证，不得编写对应生产代码。
- 每个功能点完成后必须派发新的独立 QA 子代理；QA 结论必须基于事实，存在问题就明确列出问题点，不存在问题就明确报告通过。
- 首次 QA 通过且未发生修复时，不执行第二次 QA；QA 未通过时，不进入下一个功能点，主 Agent 继续派发修复子代理，并在修复后再次派发独立 QA 子代理复审。
- 功能点全绿后，在进入下一个功能点前使用 `liumenglife <liumenglife@163.com>` 作为提交者信息提交。

## 当前正在做

- [✓] 删除错误创建在主工作区的 `feature/batch1-foundations` 分支。
- [✓] 在 `.worktrees/feature/batch1-foundations` 基于最新 `main` 创建同名隔离分支。
- [✓] 将第2套 Superpowers 规格与计划按 `102-*` 编号规范命名，并同步引用和 planning 真相。
- [✓] 完成第一批启动门禁核对报告与独立 QA 复核。
- [•] 派发子代理执行 `identity-access` 统一主体与身份协议治理。

## 已完成里程碑

- [✓] `101` 最小可运行工程骨架已完成、推送 PR 并合并到 `main`。
- [✓] 已删除本地无用分支 `skeleton-101`、`feature/minimal-runnable-skeleton` 和错误位置的 `feature/batch1-foundations`。
- [✓] 当前批次隔离工作区为 `.worktrees/feature/batch1-foundations`。
- [✓] 第一批启动门禁核对通过，报告为 [`102-01-batch1-startup-gate-check.md`](../reports/verification/102-01-batch1-startup-gate-check.md)。

## 当前阻塞

- 当前无需求阻塞。
- 当前无验证阻塞。

## 活跃支线

- [✓] 工作区纠偏：当前批次已改为在 `.worktrees/feature/batch1-foundations` 隔离工作区执行。
- [✓] 文档命名纠偏：将日期命名的第2套 Superpowers 规格与第一批计划收口到 `102-*` 编号命名。

## 下一步唯一动作

- 派发子代理执行 `identity-access` 统一主体与身份协议治理，并按 TDD / QA 闭环推进。

## 恢复提示

- 恢复时先读本文件、[`history.md`](./history.md)、[`decisions.md`](./decisions.md)。
- 当前编码分支为 `feature/batch1-foundations`，隔离工作区为 `.worktrees/feature/batch1-foundations`。
- 本批次正式执行依据为 [`102-cmp-implementation-execution-spec.md`](../superpowers/specs/102-cmp-implementation-execution-spec.md) 与 [`102-01-batch-1-foundations-implementation-plan.md`](../superpowers/plans/102-01-batch-1-foundations-implementation-plan.md)。
- 不要把已归档的 `101` 最小骨架任务写回 `current.md`；历史批次只在 `history.md` 维护。

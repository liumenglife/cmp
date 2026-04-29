# 当前批次真相

## 主目标

- 第三批目标是完成依赖第二批主链路真相源的业务能力，包括电子签章、加密软件、合同生命周期三条主线。
- 本批次必须围绕第二批已经形成的合同主档、文档版本链、审批摘要、任务、权限与审计能力运行，不复制合同、文档或审批主真相。
- 当前分支为 `feature/batch3-dependent-business-capabilities`，隔离工作区为 `.worktrees/feature/batch3-dependent-business-capabilities`，第三批编码只在该工作树推进。

## 成功定义

- 已基于最新 `main` 创建第三批隔离工作区和业务分支。
- 新工作树基线验证 `./scripts/verify-all.sh` 已通过：后端 95 个测试通过，前端检查、测试、构建通过，容器健康检查通过并完成清理。
- 电子签章可基于审批通过后的正式合同和文档中心版本发起申请，并完成会话、结果回写、验签摘要、合同摘要和时间线回写。
- 加密软件可在文件入库后自动加密，平台内读取统一走受控解密访问；管理端可按部门、人员授权解密下载，且全过程可审计。
- 履约、变更、终止、归档均围绕同一 `contract_id` 运行，不创建新的合同主档。
- 每个功能点都按测试驱动开发与独立质量审查闭环执行；首次质量审查有问题时必须先修复并再次质量审查，通过后才进入下一个功能点。

## 非目标

- 不在 `main` 或第二批已完成分支继续实现第三批代码。
- 不重写第二批合同主档、文档版本链、审批运行时基础真相。
- 不把电子签章、加密、生命周期过程表升级为合同主档、文档主档或审批主档。
- 不提前实现第四批智能增强能力或第五批综合联调上线准备任务。

## 当前阶段

- 阶段：第三批依赖主链路真相源的业务能力编码阶段。
- 分支：`feature/batch3-dependent-business-capabilities`。
- 隔离工作区：`.worktrees/feature/batch3-dependent-business-capabilities`。
- 规格：[`102-cmp-implementation-execution-spec.md`](../superpowers/specs/102-cmp-implementation-execution-spec.md)。
- 当前批次计划：[`102-03-batch-3-dependent-business-capabilities-implementation-plan.md`](../superpowers/plans/102-03-batch-3-dependent-business-capabilities-implementation-plan.md)。

## 编码阶段任务清单

- [✓] 第三批工作树与分支创建，基线验证通过。
- [✓] 第三批挂载点验收与共享契约冻结：已通过独立质量审查复审，没有问题。
- [✓] 电子签章申请与准入能力：已通过独立质量审查复审，没有问题。
- [✓] 电子签章会话、结果回写与纸质备案：已通过独立质量审查复审，没有问题。
- [ ] 加密软件自动加密与平台内受控访问。
- [ ] 加密软件授权解密下载与高敏审计闭环。
- [ ] 合同生命周期履约基础能力。
- [ ] 合同生命周期变更、终止与归档能力。
- [ ] 第三批跨模块综合验证。
- [ ] 第三批整体质量收口。

## 子代理执行协议

- 每个编码任务由新的实现子代理执行，主 Agent 只负责派发、收口和回写 planning 真相。
- 实现子代理必须遵循测试驱动开发：没有先失败的测试或验证，不得编写对应生产代码。
- 每个功能点完成后必须派发新的独立质量审查子代理；质量审查结论必须基于事实，存在问题就明确列出问题点，不存在问题就明确报告通过。
- 首次质量审查通过且未发生修复时，不执行第二次质量审查；质量审查未通过时，不进入下一个功能点，主 Agent 继续派发修复子代理，并在修复后再次派发独立质量审查子代理复审。
- 功能点全绿后，在进入下一个功能点前更新真相文件、归档历史真相，并使用 `liumenglife <liumenglife@163.com>` 作为提交者信息提交。

## 当前正在做

- [✓] `PR #5` 已合并到 `main`，合并提交为 `8641ed7`。
- [✓] 本地 `main` 已同步到 `origin/main`，第三批从最新主线启动。
- [✓] 已创建第三批分支与工作树：`feature/batch3-dependent-business-capabilities`。
- [✓] 第三批基线验证已通过：`./scripts/verify-all.sh`。
- [✓] 第三批第一项挂载点验收与共享契约冻结已完成实现、问题修复与独立质量审查复审，结论为通过，没有问题。
- [✓] 第三批第二项电子签章申请与准入能力已完成实现、问题修复与独立质量审查复审，结论为通过，没有问题。
- [✓] 第三批第三项电子签章会话、结果回写与纸质备案已完成实现、问题修复与独立质量审查复审，结论为通过，没有问题。
- [•] 准备派发第三批第四项：加密软件自动加密与平台内受控访问。

## 已完成里程碑

- [✓] `101` 最小可运行工程骨架已完成、推送 PR 并合并到 `main`。
- [✓] 第一批底座主线全部功能点、跨主线联调、独立质量审查、发布前门禁与 PR 合并已完成。
- [✓] 第二批合同核心主链路全部功能点、独立质量审查、整体质量收口与 PR 合并已完成。
- [✓] 第三批隔离工作树启动基线验证通过。
- [✓] 第三批第一项挂载点验收与共享契约冻结已通过完整验证。
- [✓] 第三批第二项电子签章申请与准入能力已通过完整验证。
- [✓] 第三批第三项电子签章会话、结果回写与纸质备案已通过完整验证。

## 当前阻塞

- 当前无需求阻塞。
- 当前无验证阻塞。

## 活跃支线

- [✓] `feature/contract-core-chain` 第二批合同核心主链路编码、整体质量收口、PR 合并与清理。
- [•] `feature/batch3-dependent-business-capabilities` 第三批依赖主链路真相源的业务能力编码。

## 下一步唯一动作

- 派发实现子代理执行第三批第四项“加密软件自动加密与平台内受控访问”，并强制按测试驱动开发推进。

## 恢复提示

- 恢复时先读本文件、[`history.md`](./history.md)、[`decisions.md`](./decisions.md)。
- 第一批底座主线历史、第二批合同核心主链路历史已迁入 [`history.md`](./history.md)，不要把已完成批次长列表写回 `current.md`。
- 第三批正式执行依据为 [`102-cmp-implementation-execution-spec.md`](../superpowers/specs/102-cmp-implementation-execution-spec.md) 与 [`102-03-batch-3-dependent-business-capabilities-implementation-plan.md`](../superpowers/plans/102-03-batch-3-dependent-business-capabilities-implementation-plan.md)。
- 第三批当前工作树为 `.worktrees/feature/batch3-dependent-business-capabilities`，不要在主工作区直接编码。

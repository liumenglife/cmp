# 当前批次真相

## 主目标

- 第四批目标是完成智能与增强能力的一期实现基准，围绕智能应用主线交付文字识别、全文检索、智能辅助、智能推荐、多语言知识治理、结果回写与运维验收闭环。
- 本批必须以合同主档为业务真相源、文档中心为文件真相源、`Agent OS` 为智能运行时底座，智能应用只承接受控派生结果域。
- 当前分支为 `feature/multilingual-knowledge-governance`，隔离工作区为 `.worktrees/feature/multilingual-knowledge-governance`。

## 成功定义

- 已基于最新 `main` 创建多语言知识治理隔离工作区和业务分支。
- 新工作树基线验证 `./scripts/verify-all.sh` 通过。
- 文字识别可基于文档中心受控版本创建作业，完成权限校验、幂等、引擎适配、结果归一、失败重试、版本失效与搜索补索引事件。
- 全文检索可消费合同、文档、条款与文字识别派生结果，支持权限裁剪、稳定快照、导出二次校验、索引重建与降级。
- 智能辅助通过 `Agent OS` 和模型抽象层运行，输出具备来源引用、护栏决策、人工确认挂点与审计链路。
- 智能推荐、多语言知识治理、结果回写与运维验收均达到第四批计划定义的完成标志。
- 每个功能点都按测试驱动开发与独立质量审查闭环执行；首次质量审查有问题时必须先修复并再次质量审查，通过后才进入下一个功能点。

## 非目标

- 不在 `main` 或第三批已完成分支继续实现第四批代码。
- 不重写合同主档、文档中心、条款库、权限、审计、任务中心或 `Agent OS` 主真相。
- 不把文字识别文本、搜索索引、智能输出、候选快照或术语快照升级为新的业务真相源。
- 不提前实现第五批综合联调验收与上线切换事项。

## 当前阶段

- 阶段：多语言知识治理已完成分支与工作树创建，基线验证通过；下一步派发实现子代理按测试驱动开发推进。
- 分支：`feature/multilingual-knowledge-governance`。
- 隔离工作区：`.worktrees/feature/multilingual-knowledge-governance`。
- 规格：[`102-cmp-implementation-execution-spec.md`](../superpowers/specs/102-cmp-implementation-execution-spec.md)。
- 当前批次计划：[`102-04-batch-4-intelligent-applications-implementation-plan.md`](../superpowers/plans/102-04-batch-4-intelligent-applications-implementation-plan.md)。

## 编码阶段任务清单

- [✓] 第四批工作树与分支创建。
- [✓] 第四批启动门禁核验与基线验证：基线验证通过；六项上游可消费性缺口已按测试驱动开发修复，独立质量审查结论为通过，没问题。
- [✓] 文字识别稳定输入闭环：已通过独立质量审查复审，没问题。
- [✓] 全文检索与索引重建：已通过独立质量审查最终复审，没问题。
- [✓] 智能辅助应用与输出护栏：已通过独立质量审查复审，没问题。
- [✓] 智能推荐、候选排序与质量评估分支与工作树创建：基于 `origin/main` 创建 `feature/candidate-ranking-quality-evaluation`，基线验证 `./scripts/verify-all.sh` 通过。
- [✓] 智能推荐、候选排序与质量评估：已通过独立质量审查复审，没问题；完整验证 `./scripts/verify-all.sh` 通过。
- [✓] 拉取请求十合并与分支清理：已合并到 `main`，本地 `main` 已同步，候选排序质量评估本地分支、远端分支和工作树均已删除。
- [✓] 多语言知识治理分支与工作树创建：基于 `origin/main` 创建 `feature/multilingual-knowledge-governance`，基线验证 `./scripts/verify-all.sh` 通过。
- [✓] 多语言知识治理红灯测试：覆盖术语新增发布、翻译单元审核、术语废弃、混语文档处理、术语快照缓存失效、输出术语错误拦截、历史任务术语快照不漂移。
- [✓] 多语言知识治理生产实现：已实现 `TermEntry`、`TranslationUnit`、`TerminologyProfile` 生命周期、`ia_i18n_context` 快照绑定、语言归一、混语降级、术语一致性护栏、审计与缓存回退。
- [✓] 多语言知识治理定向验证与完整验证：最终 `Batch4MultilingualKnowledgeGovernanceTests` 25 个测试通过，后端 188 个测试通过，`./scripts/verify-all.sh` 通过。
- [✓] 多语言知识治理独立质量审查：规格复审结论为通过，没问题；代码质量终审结论为通过，没问题。
- [✓] 多语言知识治理修复与复审：已关闭规格缺口、权限状态机缺口、事务回滚与缓存一致性缺口。
- [ ] 结果回写与冲突处理。
- [ ] 运维监控、恢复与上线验收。
- [ ] 第四批跨能力综合验证。
- [ ] 第四批整体质量收口。

## 子代理执行协议

- 每个编码任务由新的实现子代理执行，主 Agent 只负责派发、收口和回写 planning 真相。
- 实现子代理必须遵循测试驱动开发：没有先失败的测试或验证，不得编写对应生产代码。
- 每个功能点完成后必须派发新的独立质量审查子代理；质量审查结论必须基于事实，存在问题就明确列出问题点，不存在问题就明确报告通过。
- 首次质量审查通过且未发生修复时，不执行第二次质量审查；质量审查未通过时，不进入下一个功能点，主 Agent 继续派发修复子代理，并在修复后再次派发独立质量审查子代理复审。
- 功能点全绿后，在进入下一个功能点前先归档历史真相，再更新当前真相，并使用 `liumenglife <liumenglife@163.com>` 作为提交者信息提交。

## 当前正在做

- [✓] 拉取请求十已合并到 `main`，合并提交为 `961fde5`。
- [✓] 本地 `main` 已同步到 `origin/main`。
- [✓] 候选排序质量评估分支和工作树已清理。
- [✓] 智能推荐、候选排序与质量评估已完成实现、问题修复与独立质量审查复审，结论为通过，没问题。
- [✓] 智能推荐、候选排序与质量评估完整验证已通过：`./scripts/verify-all.sh`，后端 163 个测试通过，前端检查、测试、构建通过，镜像构建、编排启动、冒烟验证和清理均通过。
- [✓] 已按用户选择创建多语言知识治理分支与工作树：`feature/multilingual-knowledge-governance`。
- [✓] 新工作树基线验证已通过：`./scripts/verify-all.sh`，后端 163 个测试通过，前端 lint、前端 vitest、前端 build、镜像构建、编排启动、冒烟验证和清理均通过。
- [✓] 多语言知识治理已完成测试驱动实现、规格复审、代码质量终审与完整验证。

## 已完成里程碑

- [✓] `101` 最小可运行工程骨架已完成、推送 PR 并合并到 `main`。
- [✓] 第一批底座主线全部功能点、跨主线联调、独立质量审查、发布前门禁与 PR 合并已完成。
- [✓] 第二批合同核心主链路全部功能点、独立质量审查、整体质量收口与 PR 合并已完成。
- [✓] 第三批依赖业务能力全部功能点、跨模块综合验证、整体质量收口、PR 合并与清理已完成。
- [✓] 第四批前半段阶段性交付已合并。
- [✓] 第四批智能推荐、候选排序与质量评估已合并。

## 当前阻塞

- 当前无需求阻塞。
- 当前无验证阻塞。

## 活跃支线

- [✓] 多语言知识治理测试驱动实现已完成。

## 下一步唯一动作

- 提交 `feature/multilingual-knowledge-governance` 当前全绿变更，并准备后续 PR 收口。

## 恢复提示

- 恢复时先读本文件、[`history.md`](./history.md)、[`decisions.md`](./decisions.md)。
- 已完成批次只读取 [`history.md`](./history.md)，不要把已完成批次长列表写回 `current.md`。
- 第四批正式执行依据为 [`102-cmp-implementation-execution-spec.md`](../superpowers/specs/102-cmp-implementation-execution-spec.md) 与 [`102-04-batch-4-intelligent-applications-implementation-plan.md`](../superpowers/plans/102-04-batch-4-intelligent-applications-implementation-plan.md)。
- 当前多语言知识治理工作树为 `.worktrees/feature/multilingual-knowledge-governance`；实现必须在该隔离工作区内推进。

# 当前批次真相

## 主目标

- 在 `feature/minimal-skeleton-pr` 分支完成 `CMP` 首个最小可运行工程骨架。
- 本批次只建立后端、前端、本地编排、仓库级验证入口和范围边界验收。
- 本批次不得提前实现身份、组织、权限、Agent、集成、合同、文档、流程等业务对象。

## 成功定义

- 后端可通过 `Maven` 测试与打包，健康检查端点返回 `UP`。
- 前端可通过 `pnpm` 安装、lint、测试与构建，最小页面可渲染。
- `Docker Compose` 可启动后端、前端、`MySQL`、`Redis` 并完成健康验证。
- 仓库级验证入口 `scripts/verify-all.sh` 可一次性通过。
- 独立质量验收明确结论为通过，没有提前实现业务对象。

## 非目标

- 不实现身份、组织、角色、菜单权限、功能权限、数据权限、授权判定或审计业务对象。
- 不实现 `AgentTask`、`AgentRun`、`QueryEngine`、工具调用、模型调用或 Harness 业务闭环。
- 不实现入站、出站、回调、绑定、补偿、对账或原始报文治理对象。
- 不实现合同、业务文稿、业务流程、签章、履约、归档或智能应用功能。
- 不创建正式业务表结构、业务迁移脚本或可被后续业务依赖的进程内状态。

## 当前阶段

- 阶段：首个最小可运行工程骨架编码实现与最终验收。
- 分支：`feature/minimal-skeleton-pr`。
- 隔离工作区：`.worktrees/minimal-skeleton-pr`。
- 规格：[`101-minimal-runnable-skeleton-design.md`](../superpowers/specs/101-minimal-runnable-skeleton-design.md)。
- 计划：[`101-minimal-runnable-skeleton-implementation-plan.md`](../superpowers/plans/101-minimal-runnable-skeleton-implementation-plan.md)。

## 编码阶段任务清单

- [✓] 后端最小工程与健康检查：已按测试驱动开发完成，独立质量审查首次发现 `backend/target/` 提交卫生问题，修复后复审通过，并已提交 `6a56fe5`。
- [✓] 前端最小工程与渲染测试：已按测试驱动开发完成，独立质量审查首次发现依赖验证与提交卫生问题，修复后复审通过，并已提交 `e8142fe`。
- [✓] 本地编排与容器健康验证：已按测试驱动开发完成，独立质量审查首次发现前端服务就绪问题，修复后复审通过，并已提交 `f61e391`。
- [✓] 仓库级验证脚本：已按测试驱动开发完成，独立质量审查通过，并已提交 `4baf1f0`。
- [✓] 范围边界与最终质量验收：独立最终质量验收通过，范围扫描仅命中允许的边界说明文案，`scripts/verify-all.sh` 通过，当前仅剩 planning 真相收口。

## 子代理执行协议

- 每个编码任务由新的实现子代理执行，主 Agent 只负责派发、收口和回写 planning 真相。
- 实现子代理必须遵循测试驱动开发：没有先失败的测试或验证，不得编写对应生产代码。
- 每个功能点完成后必须派发新的独立质量审查子代理；审查结论必须基于事实，存在问题就明确列出问题点，不存在问题就明确报告通过。
- 审查未通过时，不进入下一个功能点；主 Agent 继续派发修复子代理，并再次派发独立质量审查子代理复审。

## 当前正在做

- [✓] 将文档阶段 planning 长列表从 `current.md` 迁入 `history.md`。
- [✓] 将主工作区与隔离工作区的 `current.md` 同步收敛为当前编码实现阶段 Todo。

## 已完成里程碑

- [✓] 后端最小 `Spring Boot + Maven + Actuator` 骨架完成。
- [✓] 前端最小 `React SPA + Vite + Tailwind CSS v4 + pnpm` 骨架完成。
- [✓] 本地 `Docker Compose` 编排完成，前端服务就绪问题已修复为静态资源服务与健康门禁。
- [✓] 仓库级验证脚本完成。
- [✓] 最终质量验收通过。
- [✓] 当前 PR 分支为 `feature/minimal-skeleton-pr`。

## 当前阻塞

- 当前无需求阻塞。
- 当前无验证阻塞。
- 当前仅剩 planning 真相同步与提交收口。

## 活跃支线

- [✓] 当前 PR 分支为 `feature/minimal-skeleton-pr`，隔离工作区为 `.worktrees/minimal-skeleton-pr`。
- [✓] planning 真相纠偏：已将文档阶段历史迁入 `history.md`，避免当前批次真相混入历史任务。

## 下一步唯一动作

- 提交最终 planning 状态，并准备进入下一批实现任务。

## 恢复提示

- 恢复时先读本文件、[`history.md`](./history.md)、[`decisions.md`](./decisions.md)。
- 当前编码分支为 `feature/minimal-skeleton-pr`，工作区为 `.worktrees/minimal-skeleton-pr`。
- 本批次正式执行依据为 [`101-minimal-runnable-skeleton-design.md`](../superpowers/specs/101-minimal-runnable-skeleton-design.md) 与 [`101-minimal-runnable-skeleton-implementation-plan.md`](../superpowers/plans/101-minimal-runnable-skeleton-implementation-plan.md)。
- 不要把已归档的文档阶段长列表写回 `current.md`；历史批次只在 `history.md` 维护。

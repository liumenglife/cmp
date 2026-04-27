# 历史规划记录

## 说明

本文件用于记录已经结束、已经切换批次、或者不再作为当前真相文档维护的 planning 历史内容。

## 2026-04-02 归档说明

- 自 2026-04-02 起，planning 真相体系改为只承认正式招标文件作为当前需求来源。
- 此前基于非正式来源形成的 planning 阶段描述、范围判断、命名口径与分析表述，整体归入历史留痕，不再作为当前批次真相。
- 如需回溯旧 planning 演进，应将其视为历史背景，而不是当前执行依据。

## 当前状态

- 已记录“旧来源口径整体归档”的历史边界。

## 2026-04-27 文档阶段批次归档

### 归档原因

- 当前项目已进入编码实现阶段，`current.md` 不应继续保留文档阶段长列表。
- 文档阶段已完成事项迁入本文件，后续只作为恢复背景和历史记录，不再作为当前批次 Todo。
- 当前批次真相已收敛到最小可运行工程骨架编码实现阶段。

### 已归档任务批次

- [✓] 技术文档质量修复批次：按质量审查报告问题一至问题十二修复 `docs/technicals/` 技术文档，并输出修复与复核报告到 `docs/reports/fixed/`。
- [✓] 正式文档目录重构批次：收口为 `docs/specifications/`、`docs/technicals/`、`docs/technicals/foundations/`、`docs/technicals/modules/` 体系。
- [✓] 正式招标文件引用收口批次：统一到 `docs/specifications/湖南星邦智能装备股份有限公司合同管理平台招标技术要求.pdf`。
- [✓] 总平台技术文档批次：补齐总平台 5 件套，统一总图与正文结构到 `Foundations`、`Modules`、`平台共享能力`、`基础设施层（含 Infrastructure abstraction layer）`。
- [✓] `docs/docs-overview.md` 同步批次：与当前正式文件树同步。
- [✓] 实现排期矩阵批次：`docs/technicals/implementation-batch-plan.md` 收口为唯一正式排期矩阵文档。
- [✓] 专项设计总表批次：梳理 10 份 `Detailed Design` 中继续下沉内容，并收口为 `docs/technicals/special-design-plan.md`。
- [✓] `identity-access` 专项设计批次：5 份专项设计全部完成、挂接父文档，并经独立复审通过。
- [✓] `integration-hub` 专项设计批次：5 份专项设计全部完成，并经独立复审通过。
- [✓] `agent-os` 专项设计与 Harness 结构性设计手术批次：主脊柱文档、运行时机制专项、验证 / 自演进 / 协作专项已分批完成，并由独立 QA 复审通过。
- [✓] `document-center` 专项设计批次：7 份专项设计全部完成，并经独立复审通过。
- [✓] `workflow-engine` 专项设计批次：7 份专项设计全部完成，并经独立复审通过。
- [✓] `contract-core` 专项设计批次：7 份专项设计全部完成，并经独立复审通过。
- [✓] `e-signature` 专项设计批次：6 份专项设计全部完成，并经独立复审通过。
- [✓] `encrypted-document` 专项设计批次：7 份专项设计全部完成，并经独立复审通过。
- [✓] `contract-lifecycle` 专项设计批次：6 份专项设计全部完成，并经独立复审通过。
- [✓] `intelligent-applications` 专项设计批次：7 份专项设计全部完成，并经独立复审通过。
- [✓] Superpowers 实现阶段执行基准批次：统一执行规格与 5 份批次计划已写入 `docs/superpowers/specs/`、`docs/superpowers/plans/`，并经独立复核通过。
- [✓] 首个可编码功能点准备批次：最小可运行工程骨架设计与实现计划已完成，入口为 `docs/superpowers/specs/101-minimal-runnable-skeleton-design.md` 与 `docs/superpowers/plans/101-minimal-runnable-skeleton-implementation-plan.md`。

### 迁移说明

- 上述批次不再写入 `current.md`。
- 如需恢复文档阶段背景，只读取本节；当前执行状态以 `current.md` 的编码阶段任务清单为准。

## 2026-04-27 最小可运行工程骨架批次归档

### 归档原因

- `101` 最小可运行工程骨架已经完成、推送 PR 并合并到 `main`。
- 当前开发主线切换到 `102` 第一批底座主线实现，`current.md` 只保留当前批次真相。

### 已归档任务批次

- [✓] 后端最小工程与健康检查：已按测试驱动开发完成，独立质量审查修复后通过，提交 `6a56fe5`。
- [✓] 前端最小工程与渲染测试：已按测试驱动开发完成，独立质量审查修复后通过，提交 `e8142fe`。
- [✓] 本地编排与容器健康验证：已按测试驱动开发完成，独立质量审查修复后通过，提交 `f61e391`。
- [✓] 仓库级验证脚本：已按测试驱动开发完成，独立质量审查通过，提交 `4baf1f0`。
- [✓] 范围边界与最终质量验收：独立最终质量验收通过，`scripts/verify-all.sh` 通过。

### 迁移说明

- `101` 批次不再写入 `current.md`。
- 如需恢复最小工程骨架背景，读取 [`101-minimal-runnable-skeleton-design.md`](../superpowers/specs/101-minimal-runnable-skeleton-design.md) 与 [`101-minimal-runnable-skeleton-implementation-plan.md`](../superpowers/plans/101-minimal-runnable-skeleton-implementation-plan.md)。

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

## 2026-04-28 第一批底座主线实现批次归档

### 归档原因

- 第一批底座主线已经完成全部实现、跨主线联调、独立 QA 与发布前门禁。
- `current.md` 只保留当前批次真相，第一批完成长列表迁入本文件作为恢复锚点。
- 后续主线切换到第二批合同核心主链路启动前规划核对，编码必须基于新的隔离工作区和分支推进。

### 已归档任务批次

- [✓] 第一批启动门禁核对：正式输入、范围边界、环境条件与验收样例已核对，报告为 [`102-01-batch1-startup-gate-check.md`](../reports/verification/102-01-batch1-startup-gate-check.md)。
- [✓] `identity-access` 统一主体与身份协议治理：已按 TDD 完成，经历独立 QA、修复与复审，提交 `5c1322a`，验证报告为 [`102-02-identity-access-subject-protocol-implementation.md`](../reports/verification/102-02-identity-access-subject-protocol-implementation.md)。
- [✓] `identity-access` 组织、角色、权限与数据权限：已按 TDD 完成，经历独立 QA、修复与复审，提交 `0b865ce`，验证报告为 [`102-03-identity-access-org-role-permission-implementation.md`](../reports/verification/102-03-identity-access-org-role-permission-implementation.md)。
- [✓] `identity-access` 统一授权判定与解密下载授权：已按 TDD 完成，经历独立 QA、修复与复审，提交 `9454df9`，验证报告为 [`102-04-identity-access-authorization-decrypt-implementation.md`](../reports/verification/102-04-identity-access-authorization-decrypt-implementation.md)。
- [✓] `agent-os` 最小 `QueryEngine / Harness Kernel`：已按 TDD 完成，经历独立 QA、修复与复审，提交 `35b1eba`，验证报告为 [`102-05-agent-os-query-engine-kernel-implementation.md`](../reports/verification/102-05-agent-os-query-engine-kernel-implementation.md)。
- [✓] `agent-os` 工具契约、沙箱与治理挂点：已按 TDD 完成，经历独立 QA、修复与复审，提交 `84adcdb`，验证报告为 [`102-06-agent-os-tool-sandbox-governance-implementation.md`](../reports/verification/102-06-agent-os-tool-sandbox-governance-implementation.md)。
- [✓] `integration-hub` 统一接入与适配基础：已按 TDD 完成，经历独立 QA、修复与复审，提交 `5efb748`；风险处理提交为 `f4432ac`，验证报告为 [`102-07-integration-hub-access-adapter-implementation.md`](../reports/verification/102-07-integration-hub-access-adapter-implementation.md)。
- [✓] `integration-hub` 入站、出站、回调、补偿与对账：已按 TDD 完成，经历首次 QA、多轮修复与第五次 QA 复核，提交 `c61e0ad`，验证报告为 [`102-08-integration-hub-flow-compensation-reconciliation-implementation.md`](../reports/verification/102-08-integration-hub-flow-compensation-reconciliation-implementation.md)。
- [✓] 第一批跨主线联调：已按 TDD 完成，经历独立 QA、修复与复审，提交 `c867d03`，验证报告为 [`102-09-batch1-cross-line-integration-implementation.md`](../reports/verification/102-09-batch1-cross-line-integration-implementation.md)。
- [✓] 第一批质量审查与发布前门禁：独立 QA 结论为通过，没有问题，提交 `b529fc2`，报告为 [`102-10-batch1-release-gate-qa.md`](../reports/qa/102-10-batch1-release-gate-qa.md)。

### 已知风险处置

- [✓] `integration-hub` 第七项第二次 QA 报告中的生产级签名与密钥托管、可配置重放窗口、`request_digest` 业务等价边界不回阻已全绿任务。
- [✓] 真实外部 SDK、生产密钥托管、证书链校验和密钥轮换服务后续作为安全生产化专项处理，不夹带进第八项。
- [✓] 第八项已复核 `request_digest` 的业务字段等价规则，继续排除 `trace_id` 等追踪元数据，避免合法重试误判为幂等冲突。

### 迁移说明

- 第一批底座主线不再写入 `current.md`。
- 如需恢复第一批执行背景，读取 [`102-01-batch-1-foundations-implementation-plan.md`](../superpowers/plans/102-01-batch-1-foundations-implementation-plan.md)、各验证报告与发布前门禁报告。

## 2026-04-28 第二批合同核心主链路批次归档

### 归档原因

- 第二批合同核心主链路已经完成全部实现、独立 QA 与整体质量收口。
- `contract-core`、`document-center`、`workflow-engine` 已形成合同创建、编辑、文档挂接、审批承接与状态回写的核心闭环。

### 已归档任务批次

- [✓] 批次启动门禁与接口冻结：已按 TDD 完成，经历独立 QA、修复与复审，提交 `0554cdf`，验证报告为 [`102-11-batch2-contract-interface-freeze.md`](../reports/verification/102-11-batch2-contract-interface-freeze.md)。
- [✓] `contract-core` 合同主档与编辑基础能力：已按 TDD 完成，独立 QA 结论为通过，没有问题，提交 `f427682`，验证报告为 [`102-12-contract-core-master-edit-implementation.md`](../reports/verification/102-12-contract-core-master-edit-implementation.md)。
- [✓] `document-center` 文档主档与版本链基础能力：已按 TDD 完成，独立 QA 结论为通过，没有问题，提交 `02abc87`，验证报告为 [`102-13-document-center-asset-version-implementation.md`](../reports/verification/102-13-document-center-asset-version-implementation.md)。
- [✓] `workflow-engine` 审批定义与平台运行时基础能力：已按 TDD 完成，独立 QA 结论为通过，没有问题，提交 `5100490`，验证报告为 [`102-14-workflow-engine-runtime-implementation.md`](../reports/verification/102-14-workflow-engine-runtime-implementation.md)。
- [✓] 合同文档挂接闭环：已按 TDD 完成，独立 QA 结论为通过，没有问题，提交 `4632600`，验证报告为 [`102-15-contract-document-binding-implementation.md`](../reports/verification/102-15-contract-document-binding-implementation.md)。
- [✓] 审批发起、审批承接与合同状态回写闭环：已按 TDD 完成，独立 QA 结论为通过，没有问题，提交 `7a7c4df`，验证报告为 [`102-16-approval-status-writeback-implementation.md`](../reports/verification/102-16-approval-status-writeback-implementation.md)。
- [✓] 第二批最小端到端闭环验证：已按 TDD 完成，独立 QA 结论为通过，没有问题，提交 `79523ff`，验证报告为 [`102-17-batch2-end-to-end-implementation.md`](../reports/verification/102-17-batch2-end-to-end-implementation.md)。
- [✓] 第二批整体质量收口：独立 QA 结论为通过，没有问题，报告为 [`102-18-batch2-release-gate-qa.md`](../reports/qa/102-18-batch2-release-gate-qa.md)。

### 迁移说明

- 第二批合同核心主链路不再作为未完成任务处理。
- 后续第三批启动前，应基于第二批合并后的最新 `main` 创建新的隔离工作区和分支。

## 2026-04-28 第三批依赖业务能力阶段性归档

### 归档原因

- 第三批第一项挂载点验收与共享契约冻结已经完成实现、修复、独立质量审查复审与完整验证。
- 第三批第二项电子签章申请与准入能力已经完成实现、修复、独立质量审查复审与完整验证。
- 第三批第三项电子签章会话、结果回写与纸质备案已经完成实现、修复、独立质量审查复审与完整验证。
- `current.md` 继续维护第三批当前推进状态，本节记录已经全绿的阶段性完成事实。

### 已归档任务批次

- [✓] 第三批工作树与分支创建：基于最新 `main` 创建 `feature/batch3-dependent-business-capabilities` 与 `.worktrees/feature/batch3-dependent-business-capabilities`，基线验证 `./scripts/verify-all.sh` 通过。
- [✓] 第三批挂载点验收与共享契约冻结：已按测试驱动开发完成，经历独立质量审查、修复与复审，复审结论为通过，没问题；完整验证 `./scripts/verify-all.sh` 通过。
- [✓] 电子签章申请与准入能力：已按测试驱动开发完成，经历独立质量审查、修复与复审，复审结论为通过，没问题；完整验证 `./scripts/verify-all.sh` 通过。
- [✓] 电子签章会话、结果回写与纸质备案：已按测试驱动开发完成，经历独立质量审查、修复与复审，复审结论为通过，没问题；完整验证 `./scripts/verify-all.sh` 通过。

### 迁移说明

- 第三批后续功能点继续以 [`102-03-batch-3-dependent-business-capabilities-implementation-plan.md`](../superpowers/plans/102-03-batch-3-dependent-business-capabilities-implementation-plan.md) 为执行依据。
- 已全绿的第三批第一项、第二项、第三项不再作为未完成任务处理。

## 2026-04-29 第三批第四项阶段性归档

### 归档原因

- 第三批第四项“加密软件自动加密与平台内受控访问”已经完成实现、修复、独立质量审查复审与完整验证。
- `current.md` 继续维护第三批后续功能点推进状态，本节记录已经全绿的第四项完成事实。

### 已归档任务批次

- [✓] 加密软件自动加密与平台内受控访问：已按测试驱动开发完成，首次独立质量审查结论为不通过，问题包括访问票据未校验、访问场景缺少白名单、平台外明文拒绝未持久审计；修复后独立复审结论为通过，没问题；完整验证 `./scripts/verify-all.sh` 通过。

### 迁移说明

- 第三批第五项“加密软件授权解密下载与高敏审计闭环”继续以 [`102-03-batch-3-dependent-business-capabilities-implementation-plan.md`](../superpowers/plans/102-03-batch-3-dependent-business-capabilities-implementation-plan.md) 为执行依据。
- 已全绿的第三批第四项不再作为未完成任务处理。

## 2026-04-29 第三批第五项阶段性归档

### 归档原因

- 第三批第五项“加密软件授权解密下载与高敏审计闭环”已经完成实现、修复、独立质量审查复审与完整验证。
- `current.md` 继续维护第三批后续功能点推进状态，本节记录已经全绿的第五项完成事实。

### 已归档任务批次

- [✓] 加密软件授权解密下载与高敏审计闭环：已按测试驱动开发完成，首次独立质量审查结论为不通过，问题包括非合同范围授权审计不可回查、下载作业状态机允许非法流转、底座测试时间戳 helper 参数被架空；修复后独立复审结论为通过，没问题；完整验证 `./scripts/verify-all.sh` 的后端、前端阶段通过，容器栈验证因镜像仓库临时 `EOF` 重试后通过。

### 迁移说明

- 第三批第六项“合同生命周期履约基础能力”继续以 [`102-03-batch-3-dependent-business-capabilities-implementation-plan.md`](../superpowers/plans/102-03-batch-3-dependent-business-capabilities-implementation-plan.md) 为执行依据。
- 已全绿的第三批第五项不再作为未完成任务处理。

## 2026-04-29 第三批第六项阶段性归档

### 归档原因

- 第三批第六项“合同生命周期履约基础能力”已经完成实现、修复、独立质量审查复审与完整验证。
- `current.md` 继续维护第三批后续功能点推进状态，本节记录已经全绿的第六项完成事实。

### 已归档任务批次

- [✓] 合同生命周期履约基础能力：已按测试驱动开发完成，首次独立质量审查结论为不通过，问题包括履约主记录和摘要没有持久事实、状态机允许非法完成、时间线与审计没有独立可回查事实；修复后独立复审仍发现逾期完成阻断和状态机测试覆盖不足；二次修复后独立复审结论为通过，没问题；完整验证 `./scripts/verify-all.sh` 通过。

### 迁移说明

- 第三批第七项“合同生命周期变更、终止与归档能力”继续以 [`102-03-batch-3-dependent-business-capabilities-implementation-plan.md`](../superpowers/plans/102-03-batch-3-dependent-business-capabilities-implementation-plan.md) 为执行依据。
- 已全绿的第三批第六项不再作为未完成任务处理。

## 2026-04-29 第三批第七项阶段性归档

### 归档原因

- 第三批第七项“合同生命周期变更、终止与归档能力”已经完成实现、修复、独立质量审查复审与完整验证。
- `current.md` 继续维护第三批跨模块综合验证与整体质量收口推进状态，本节记录已经全绿的第七项完成事实。

### 已归档任务批次

- [✓] 合同生命周期变更、终止与归档能力：已按测试驱动开发完成，首次独立质量审查结论为不通过，问题包括审批驳回污染完成状态、生命周期摘要未形成持久事实、审批流程引用状态未回写、归档输入集校验不足、借阅归还状态机约束不足；修复后独立复审结论为通过，没问题；完整验证 `./scripts/verify-all.sh` 通过，后端 132 个测试通过，前端检查、测试、构建通过，容器健康检查通过并完成清理。

### 迁移说明

- 第三批跨模块综合验证继续以 [`102-03-batch-3-dependent-business-capabilities-implementation-plan.md`](../superpowers/plans/102-03-batch-3-dependent-business-capabilities-implementation-plan.md) 为执行依据。
- 已全绿的第三批第七项不再作为未完成任务处理。

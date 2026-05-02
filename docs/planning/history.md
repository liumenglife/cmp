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

## 2026-04-29 第三批跨模块综合验证阶段性归档

### 归档原因

- 第三批跨模块综合验证已经完成实现、修复、规格复审、代码质量复审与完整验证。
- `current.md` 继续维护第三批整体质量收口推进状态，本节记录已经全绿的跨模块综合验证完成事实。

### 已归档任务批次

- [✓] 第三批跨模块综合验证：已按测试驱动开发完成，覆盖电子签章、加密软件、合同生命周期与第二批合同主档、文档版本链、审批摘要、任务、权限、审计主真相的最小闭环；首次规格审查发现归档/终止后授权明文下载限制和任务主真相断言不足，修复后规格复审结论为通过，没问题；首次代码质量审查发现任务主真相断言可能假阳性，修复后代码质量复审结论为通过，没问题；完整验证 `./scripts/verify-all.sh` 通过，后端 133 个测试通过，前端检查、测试、构建通过，容器健康检查通过并完成清理。

### 迁移说明

- 第三批整体质量收口继续以 [`102-03-batch-3-dependent-business-capabilities-implementation-plan.md`](../superpowers/plans/102-03-batch-3-dependent-business-capabilities-implementation-plan.md) 为执行依据。
- 已全绿的第三批跨模块综合验证不再作为未完成任务处理。

## 2026-04-29 第三批第七项分支整体质量收口归档

### 归档原因

- `feature/contract-lifecycle-change-termination-archive` 已完成第三批第七项、跨模块综合验证与整体质量收口审查。
- 工作树干净、完整验证通过、规划真相与分支决策一致，已具备创建 PR 的条件。

### 已归档任务批次

- [✓] 第三批整体质量收口：独立质量收口审查结论为通过，没问题；`git status --short --branch` 显示工作树干净；`git diff --check origin/main..HEAD` 通过；完整验证 `./scripts/verify-all.sh` 通过，后端 133 个测试通过，前端检查、测试、构建通过，容器健康检查通过并完成清理；敏感信息抽查未发现新增真实密钥或凭据。

### 迁移说明

- 本分支已具备 PR 准备状态；后续应创建 PR，并等待 checks 通过后再合并。
- 第三批第七项、跨模块综合验证与整体质量收口不再作为未完成任务处理。

## 2026-04-29 第三批合并与清理归档

### 归档原因

- `PR #8` 已完成检查、合并到 `main`，本地 `main` 已同步，第三批最后一个功能分支和工作树已清理。
- 第四批已经创建新的隔离工作树，`current.md` 只保留第四批当前真相。

### 已归档任务批次

- [✓] `PR #8` 合并：合并提交为 `7b57b29`，标题为“第三批合同生命周期收口与跨模块验证”。
- [✓] 本地 `main` 同步：`main` 已快进到 `origin/main` 的 `7b57b29`。
- [✓] 第三批分支清理：`feature/contract-lifecycle-change-termination-archive` 本地分支、远端分支和隔离工作树均已删除。
- [✓] 第三批整体完成：电子签章、加密软件、合同生命周期、跨模块综合验证与整体质量收口全部完成并合并。

### 迁移说明

- 第三批不再作为当前任务处理。
- 后续开发切换到第四批“智能与增强能力”，以 [`102-04-batch-4-intelligent-applications-implementation-plan.md`](../superpowers/plans/102-04-batch-4-intelligent-applications-implementation-plan.md) 为执行依据。

## 2026-04-29 第四批启动门禁阶段性归档

### 归档原因

- 第四批启动门禁核验、基线验证、缺口修复与独立质量审查已经完成。
- `current.md` 继续维护第四批后续功能点推进状态，本节记录已经全绿的启动门禁完成事实。

### 已归档任务批次

- [✓] 第四批工作树与分支创建：基于最新 `main` 创建 `feature/batch4-intelligent-enhancement-capabilities` 与 `.worktrees/feature/batch4-intelligent-enhancement-capabilities`。
- [✓] 第四批规划真相对齐：第三批合并与清理事实迁入历史真相，`current.md` 收敛为第四批当前真相。
- [✓] 第四批启动门禁核验：首次核验结论为不通过，问题包括合同主档分类主链缺失、条款库 / 模板库读取入口缺失、文档中心能力挂接摘要缺失、文档版本切换稳定事件入口缺失、`Agent OS` 人工确认接口缺失、统一任务中心正式入口缺失。
- [✓] 第四批启动门禁缺口修复：已按测试驱动开发补齐六项上游可消费性缺口，独立质量审查结论为通过，没问题。
- [✓] 第四批启动门禁完整验证：修复后 `./scripts/verify-all.sh` 通过，后端 134 个测试通过，前端检查、测试、构建通过，容器健康检查通过并完成清理。

### 迁移说明

- 第四批启动门禁不再作为未完成任务处理。
- 第四批下一功能点为“文字识别稳定输入闭环”，继续以 [`102-04-batch-4-intelligent-applications-implementation-plan.md`](../superpowers/plans/102-04-batch-4-intelligent-applications-implementation-plan.md) 为执行依据。

## 2026-04-29 第四批文字识别稳定输入闭环阶段性归档

### 归档原因

- 第四批文字识别稳定输入闭环已经完成实现、修复、独立质量审查复审与完整验证。
- `current.md` 继续维护第四批后续功能点推进状态，本节记录已经全绿的文字识别完成事实。

### 已归档任务批次

- [✓] 文字识别稳定输入闭环：已按测试驱动开发完成，支持基于文档中心受控版本创建作业、固化文档版本和内容指纹、输入权限校验、幂等、引擎适配边界、失败重试、结果归一、`READY` / `PARTIAL` / `FAILED` / `SUPERSEDED` 状态、版本切换失效、审计、文档中心能力挂接补偿和搜索补索引事件。
- [✓] 文字识别首次质量审查：结论为不通过，问题为 `PARTIAL` 与 `FAILED` 结果状态缺少测试驱动证据。
- [✓] 文字识别问题修复：已补齐低质量 `PARTIAL` 状态与最终失败 `FAILED` 状态测试证据，未修改生产代码。
- [✓] 文字识别复审：独立质量审查结论为通过，没问题。
- [✓] 文字识别完整验证：修复后 `./scripts/verify-all.sh` 通过，后端 139 个测试通过，前端检查、测试、构建通过，容器健康检查通过并完成清理。

### 迁移说明

- 第四批文字识别稳定输入闭环不再作为未完成任务处理。
- 第四批下一功能点为“全文检索与索引重建”，继续以 [`102-04-batch-4-intelligent-applications-implementation-plan.md`](../superpowers/plans/102-04-batch-4-intelligent-applications-implementation-plan.md) 为执行依据。

## 2026-04-29 第四批全文检索与索引重建阶段性归档

### 归档原因

- 第四批全文检索与索引重建已经完成实现、多轮修复、独立质量审查最终复审与完整验证。
- `current.md` 继续维护第四批后续功能点推进状态，本节记录已经全绿的全文检索完成事实。

### 已归档任务批次

- [✓] 全文检索与索引重建：已按测试驱动开发完成，覆盖合同主档、文档中心、文字识别、条款库搜索输入准入，`SearchSourceEnvelope`、`SearchDocument` 映射、精准查询、模糊查询、筛选、排序、分页、导出、聚合、稳定结果快照、权限裁剪、降级查询、增量刷新、范围重建、全量重建、补数、双代构建和别名切换。
- [✓] 首次独立质量审查：结论为不通过，问题包括快照重放权限和状态校验不足、查询模型未真实实现、重建与双代切换表层化、权限裁剪覆盖过窄、测试驱动证据不足。
- [✓] 首轮修复：补齐快照重放权限和状态校验、精准查询、筛选排序、范围重建、补数、导出字段裁剪和相关失败测试证据。
- [✓] 二次独立质量审查：结论为不通过，问题包括双代构建未真实并存、快照过期未校验、测试缺少旧代可用和过期重放拒绝。
- [✓] 二轮修复：补齐 `search_doc_id + rebuild_generation` 并存、快照过期拒绝、`V13` 双代并存迁移和相关测试证据。
- [✓] 三次独立质量审查：结论为不通过，问题为范围重建 `switch_alias=true` 后范围外旧代文档从默认查询中消失。
- [✓] 三轮修复：默认查询改为每个 `search_doc_id` 选择不超过活跃代的最新可用代，保证范围内新代可见且范围外旧代继续可见。
- [✓] 最终独立质量审查：结论为通过，没问题。
- [✓] 全文检索完整验证：最终 `./scripts/verify-all.sh` 通过，后端 148 个测试通过，前端检查、测试、构建通过，容器健康检查通过并完成清理。

### 迁移说明

- 第四批全文检索与索引重建不再作为未完成任务处理。
- 第四批下一功能点为“智能辅助应用与输出护栏”，继续以 [`102-04-batch-4-intelligent-applications-implementation-plan.md`](../superpowers/plans/102-04-batch-4-intelligent-applications-implementation-plan.md) 为执行依据。

## 2026-04-29 第四批智能辅助应用与输出护栏阶段性归档

### 归档原因

- 第四批智能辅助应用与输出护栏已经完成实现、修复、独立质量审查复审与完整验证。
- 用户新增要求：当前功能点闭环后，先诊断并优化完整验证脚本性能，再继续后续功能点。

### 已归档任务批次

- [✓] 智能辅助应用与输出护栏：已按测试驱动开发完成，覆盖摘要、问答、风险识别、比对提取统一任务受理，`AiContextEnvelope` 六层装配，证据预算、裁剪、降级、来源绑定、无证据拒绝、输出护栏、受保护结果快照和人工确认链路。
- [✓] 首次独立质量审查：结论为不通过，问题包括业务模块直接写入 `Agent OS` 运行时表、人审链路未锚定真实 `agent_task_id`、护栏偏模拟字段、证据预算不足不阻断、测试覆盖不足。
- [✓] 问题修复：新增 `AgentOsGateway`，业务侧通过 `Agent OS` 服务入口创建任务和人工确认；人审 `source_task_id` 锚定真实 `agent_task_id`；护栏基于真实 `agent_output` 结构校验；预算不足执行前拒绝并留存保护快照。
- [✓] 独立质量审查复审：结论为通过，没问题。
- [✓] 智能辅助完整验证：修复后 `./scripts/verify-all.sh` 通过，后端 158 个测试通过，前端检查、测试、构建通过，容器健康检查通过并完成清理。

### 迁移说明

- 第四批智能辅助应用与输出护栏不再作为未完成任务处理。
- 后续进入下一功能点前，先按用户要求完成 `./scripts/verify-all.sh` 性能诊断与优化专项。

## 2026-04-29 完整验证脚本性能诊断与优化专项归档

### 归档原因

- 用户要求在当前功能点闭环后，专门诊断并优化 `./scripts/verify-all.sh` 性能问题。
- 该专项已经完成实现、问题修复、独立质量审查复审与完整验证。

### 已归档任务批次

- [✓] 阶段级耗时统计：`./scripts/verify-all.sh` 已覆盖后端测试、前端依赖检查、前端检查、前端测试、前端构建、编排配置、镜像构建、编排启动、健康检查 / 冒烟验证、编排清理。
- [✓] 性能瓶颈定位：优化后完整验证显示主要耗时集中在后端测试与 `docker compose up`。
- [✓] 重复执行优化：后端验证改为一次 `mvn package` 完成测试与打包；前端依赖检查改为 `pnpm install --frozen-lockfile --prefer-offline`，保证锁文件一致性；镜像构建消费本地产物，避免在 Dockerfile 内重复构建；编排启动使用 `--no-build` 避免重复构建。
- [✓] 等待与清理优化：健康检查保留上限和失败诊断；清理阶段单独计时，清理失败时退出处理会再次尝试。
- [✓] 契约测试：新增脚本契约测试，使用 stub 命令验证关键质量门禁入口和清理重试路径。
- [✓] 首次独立质量审查：结论为不通过，问题包括前端依赖同步可能误跳过、契约测试只做文本检查、清理标记设置过早。
- [✓] 问题修复：前端依赖检查强制通过 `pnpm install --frozen-lockfile --prefer-offline` 校验 `package.json` 与锁文件一致；契约测试升级为命令调用验证；清理标记改为清理成功后设置。
- [✓] 独立质量审查复审：结论为通过，没问题。
- [✓] 优化后完整验证：`./scripts/verify-all.sh` 通过，后端 158 个测试通过，前端检查、测试、构建通过，镜像构建、编排启动、冒烟验证和清理均通过。

### 迁移说明

- 完整验证脚本性能诊断与优化专项不再作为未完成任务处理。
- 第四批下一功能点为“智能推荐、候选排序与质量评估”，继续以 [`102-04-batch-4-intelligent-applications-implementation-plan.md`](../superpowers/plans/102-04-batch-4-intelligent-applications-implementation-plan.md) 为执行依据。

## 2026-04-30 第四批前半段阶段性交付合并与清理归档

### 归档原因

- `PR #9` 已完成检查、合并到 `main`，本地 `main` 已同步，第四批前半段阶段性交付分支和工作树已清理。
- 后续智能推荐、候选排序与质量评估使用新的独立分支推进，`current.md` 只维护当前功能点状态。

### 已归档任务批次

- [✓] `PR #9` 合并：合并提交为 `037d327`，标题为“第四批智能增强能力阶段性交付”。
- [✓] 本地 `main` 同步：`main` 已快进到 `origin/main` 的 `037d327`。
- [✓] 第四批前半段分支清理：`feature/batch4-intelligent-enhancement-capabilities` 本地分支、远端分支和隔离工作树均已删除。
- [✓] 第四批前半段阶段性交付完成：启动门禁、文字识别、全文检索、智能辅助应用与输出护栏、完整验证脚本性能优化均已完成并合并。

### 迁移说明

- 第四批前半段阶段性交付不再作为当前未完成任务处理。
- 第四批下一功能点为“智能推荐、候选排序与质量评估”，使用 `feature/candidate-ranking-quality-evaluation` 与 `.worktrees/feature/candidate-ranking-quality-evaluation` 推进。

## 2026-04-30 第四批智能推荐、候选排序与质量评估归档

### 归档原因

- 第四批智能推荐、候选排序与质量评估已经完成实现、修复、独立质量审查复审与完整验证。
- `current.md` 继续维护第四批后续功能点推进状态，本节记录已经全绿的候选排序完成事实。

### 已归档任务批次

- [✓] 智能推荐、候选排序与质量评估：已按测试驱动开发完成，覆盖搜索召回、文字识别字段候选、条款 / 模板命中、智能证据片段和版本化规则命中归一为 `SemanticCandidate`，覆盖八类槽位、槽内排序、槽间配额、同源压缩、冲突消解、解释摘要、强否决规则、质量分层和放行决策映射。
- [✓] 首次独立质量审查：结论为不通过，问题包括新增候选独立正式表违反设计边界、候选来源存在固定拼装、槽位治理不足、放行状态映射错误、重启后只依赖内存状态、冲突和低质量测试依赖模拟开关。
- [✓] 问题修复：删除候选独立正式表迁移，候选快照、候选清单、质量评估和门控信息落到 `ia_ai_application_job`、`ia_ai_application_result` 与 `ia_ai_audit_event`；候选来源改为消费语义引用、证据片段和版本化规则命中；补齐八槽位治理、正式表恢复、真实冲突与低质量路径；`REJECT` 任务状态映射修正为 `FAILED` 并补齐测试断言。
- [✓] 独立质量审查复审：结论为通过，没问题。
- [✓] 智能推荐完整验证：`./scripts/verify-all.sh` 通过，后端 163 个测试通过，前端检查、测试、构建通过，镜像构建、编排启动、冒烟验证和清理均通过。

### 迁移说明

- 第四批智能推荐、候选排序与质量评估不再作为未完成任务处理。
- 第四批下一功能点为“多语言知识治理”，进入前需先按新业务分支规则汇报分支名称备选项并等待用户选择。

## 2026-04-30 第四批候选排序质量评估合并与清理归档

### 归档原因

- 拉取请求十已完成检查、合并到 `main`，本地 `main` 已同步，候选排序质量评估分支和工作树已清理。
- 后续开发切换到第四批下一功能点“多语言知识治理”，当前真相只维护下一功能点启动状态。

### 已归档任务批次

- [✓] 拉取请求十合并：合并提交为 `961fde5`，标题为“第四批候选排序质量评估”。
- [✓] 本地 `main` 同步：`main` 已快进到 `origin/main` 的 `961fde5`。
- [✓] 候选排序质量评估分支清理：`feature/candidate-ranking-quality-evaluation` 本地分支、远端分支和隔离工作树均已删除。
- [✓] 第四批候选排序质量评估完成并合并：智能推荐、候选排序、质量分层、放行决策映射、正式载荷落点和规划真相归档均已进入 `main`。

### 迁移说明

- 候选排序质量评估不再作为当前未完成任务处理。
- 第四批下一功能点为“多语言知识治理”，使用 `feature/multilingual-knowledge-governance` 与 `.worktrees/feature/multilingual-knowledge-governance` 推进。

## 2026-04-30 第四批多语言知识治理阶段性归档

### 归档原因

- 第四批多语言知识治理已经完成测试驱动开发、规格复审、代码质量终审与完整验证。
- `current.md` 继续维护第四批后续功能点推进状态，本节记录已经全绿的多语言知识治理完成事实。

### 已归档任务批次

- [✓] 多语言知识治理：已按测试驱动开发完成，覆盖 `TermEntry`、`TranslationUnit`、`TerminologyProfile` 的创建、提交审核、审核、发布、废弃、版本快照与生命周期审计；实现 `ia_i18n_context` 与 `terminology_profile_code + profile_version` 固化绑定，支持 `zh-CN`、`en-US`、`es-ES` 与其他语言降级，覆盖混语片段保留、术语缺失降级、`i18n_status=FAILED` 下游降级、AI 输出术语一致性护栏、术语快照缓存失效后数据库正式记录回退读取。
- [✓] 规格审查与修复：首次规格审查发现主链未接入 `ia_i18n_context`、未发布 profile 仍可 `APPLIED`、术语废弃硬编码 profile、缓存失效测试依赖反射；后续复审继续发现缓存未真实回退、主链 profile 错误被吞、AI protected snapshot 前置副作用、`UNKNOWN` 显式 profile 绕过校验、生命周期审计不完整；均已按红灯测试修复，最终规格复审结论为通过，没问题。
- [✓] 代码质量审查与修复：代码质量审查发现历史 `DEPRECATED` profile 缓存驱逐后不可回放、治理接口缺权限、状态机可绕过提审、错误路径残留审计、废弃 / 修订接口副作用、近似权限误放行、多步写入缺事务边界、事务回滚后缓存可能暴露未提交快照；均已按红灯测试修复，最终代码质量终审结论为通过，没问题。
- [✓] 多语言知识治理完整验证：最终 `Batch4MultilingualKnowledgeGovernanceTests` 25 个测试通过，后端 `mvn test` 188 个测试通过，`./scripts/verify-all.sh` 通过，覆盖后端测试、前端依赖检查、lint、vitest、build、Docker Compose 配置、镜像构建、编排启动、healthcheck / smoke 与清理。

### 迁移说明

- 第四批多语言知识治理不再作为未完成任务处理。
- 第四批下一功能点为“结果回写与冲突处理”，继续以 [`102-04-batch-4-intelligent-applications-implementation-plan.md`](../superpowers/plans/102-04-batch-4-intelligent-applications-implementation-plan.md) 为执行依据。

## 2026-04-30 第四批多语言知识治理合并与清理归档

### 归档原因

- 拉取请求十一已完成检查、合并到 `main`，本地 `main` 已同步，多语言知识治理分支和工作树已清理。
- 后续开发切换到第四批下一功能点“结果回写与冲突处理”，当前真相只维护下一功能点启动状态。

### 已归档任务批次

- [✓] 拉取请求十一合并：合并提交为 `d5f3184`，标题为“第四批多语言知识治理”。
- [✓] 本地 `main` 同步：`main` 已快进到 `origin/main` 的 `d5f3184`。
- [✓] 多语言知识治理分支清理：`feature/multilingual-knowledge-governance` 本地分支、远端分支和隔离工作树均已删除。
- [✓] 第四批多语言知识治理完成并合并：术语、翻译单元、术语快照、语言治理、主链 i18n context 绑定、缓存回退、事务提交后缓存一致性和规划真相归档均已进入 `main`。

### 迁移说明

- 多语言知识治理不再作为当前未完成任务处理。
- 第四批下一功能点为“结果回写与冲突处理”，进入前需先按新业务分支规则汇报分支名称备选项并等待用户选择。

## 2026-05-01 第四批结果回写与冲突处理启动归档

### 归档原因

- 用户已选定第四批下一功能点“结果回写与冲突处理”的业务分支名。
- 已基于最新 `main` 创建新的隔离工作树，当前真相从分支命名准备切换为功能实现推进。

### 已归档任务批次

- [✓] 结果回写与冲突处理分支命名准备：已选定 `feature/batch-4-result-writeback-conflict-resolution`。
- [✓] 最新主线同步：`main` 已确认最新，基线提交为 `a2496bb`。
- [✓] 结果回写与冲突处理工作树创建：已创建 `.worktrees/feature/batch-4-result-writeback-conflict-resolution`。
- [✓] 新工作树基线验证：`./scripts/verify-all.sh` 通过，覆盖后端测试、前端依赖检查、lint、vitest、build、Docker Compose 配置、镜像构建、编排启动、healthcheck / smoke 与清理。

### 迁移说明

- 分支命名准备不再作为当前未完成任务处理。
- 当前真相切换到“结果回写与冲突处理”测试驱动实现。

## 2026-05-01 第四批结果回写与冲突处理阶段性归档

### 归档原因

- 第四批结果回写与冲突处理已经完成测试驱动开发、规格终审、代码质量复审与完整验证。
- `current.md` 继续维护当前分支提交与拉取请求收口状态，本节记录已经全绿的结果回写完成事实。

### 已归档任务批次

- [✓] 结果回写与冲突处理：已按测试驱动开发完成，覆盖正式结果回写准入、`CONTRACT_SUMMARY`、`CONTRACT_RISK_VIEW`、`CONTRACT_EXTRACTION_VIEW` 三目标视图投影、禁止写入清单、`ia_writeback_record` 生命周期、版本冲突、同槽位冲突、置信度冲突、人工否决冲突、`UNRESOLVED_EQUAL` 人工升级、幂等、短时锁、乐观锁、死信恢复和全链路审计。
- [✓] 规格审查与修复：多轮规格审查发现请求载荷伪造、人工确认不可信、冲突裁决依赖模拟开关、生命周期不完整、跨合同污染、部分发布校验不足、死信恢复缺失、摘要部分发布结构校验不足等问题；均已按红灯测试修复，最终规格终审结论为通过，没问题。
- [✓] 代码质量审查与修复：首轮代码质量审查发现回写主流程缺事务边界、缺省 `target_snapshot_version` 绕过乐观锁、锁获取非原子、死信恢复误删其他有效锁、人工升级空 `trace_id` 空指针和 planning 真相滞后；均已修复，代码质量复审结论为通过，没问题。
- [✓] 结果回写完整验证：`./scripts/verify-all.sh` 通过，后端 `202` 个测试通过，前端依赖检查、lint、vitest、build、Docker Compose 配置、镜像构建、编排启动、healthcheck / smoke 与清理通过。

### 迁移说明

- 第四批结果回写与冲突处理不再作为未完成实现任务处理。
- 当前分支后续进入提交、拉取请求与合并清理收口；合并后第四批下一功能点为“运维监控、恢复与上线验收”。

## 2026-05-01 第四批结果回写与冲突处理提交前真相归档

### 归档原因

- 用户确认当前全绿变更可以提交、推送并创建面向 `main` 的拉取请求，但不要合并。
- 当前真相从实现和质量闭环切换到提交与拉取请求创建动作。

### 已归档任务批次

- [✓] 结果回写与冲突处理实现闭环：测试驱动实现、规格终审、代码质量复审与完整验证均已完成。
- [✓] 验证证据：`./scripts/verify-all.sh` 通过，后端 `202` 个测试通过，前端依赖检查、lint、vitest、build、Docker Compose 配置、镜像构建、编排启动、healthcheck / smoke 与清理通过。
- [✓] 提交前状态：当前分支为 `feature/batch-4-result-writeback-conflict-resolution`，下一步为提交当前变更、推送到 `origin`，并创建面向 `main` 的拉取请求。

### 迁移说明

- 当前实现和质量闭环不再作为未完成任务处理。
- 拉取请求创建后仍不合并；等待用户后续确认合并与清理。

## 2026-05-01 第四批结果回写与冲突处理合并清理归档

### 归档原因

- 拉取请求十三已合并到 `main`，本地 `main` 已同步，结果回写与冲突处理分支和工作树已清理。
- 第四批剩余范围切换为完全收口：运维监控、恢复与上线验收，第四批跨能力综合验证，第四批整体质量收口。

### 已归档任务批次

- [✓] 拉取请求十三合并：合并提交为 `ba93443`，标题为“实现第四批结果回写冲突处理”。
- [✓] 本地 `main` 同步：`main` 已快进到 `origin/main` 的 `ba93443`。
- [✓] 结果回写与冲突处理分支清理：`feature/batch-4-result-writeback-conflict-resolution` 本地分支、远端分支和隔离工作树均已删除。
- [✓] 第四批结果回写与冲突处理完成并合并：受控结果回写、目标视图投影、冲突裁决、死信恢复和审计追溯均已进入 `main`。

### 迁移说明

- 结果回写与冲突处理不再作为当前未完成任务处理。
- 第四批尚未全部收口；下一阶段必须完成第四批运维监控、恢复与上线验收、跨能力综合验证和整体质量收口后，才能进入第五批。

## 2026-05-01 第四批完全收口分支启动归档

### 归档原因

- 用户确认继续推进第四批次，并选定完全收口分支 `feature/batch-4-ops-monitoring-recovery-acceptance`。
- 已基于最新 `main` 创建新的隔离工作树，当前真相从结果回写收口切换为第四批完全收口实施。

### 已归档任务批次

- [✓] 第四批完全收口分支命名准备：已选定 `feature/batch-4-ops-monitoring-recovery-acceptance`。
- [✓] 最新主线同步：`main` 已确认最新，基线提交为 `ba93443`。
- [✓] 第四批完全收口工作树创建：已创建 `.worktrees/feature/batch-4-ops-monitoring-recovery-acceptance`。
- [✓] 新工作树基线验证：`./scripts/verify-all.sh` 通过，后端 `202` 个测试通过，前端依赖检查、lint、vitest、build、Docker Compose 配置、镜像构建、编排启动、healthcheck / smoke 与清理通过。

### 迁移说明

- 第四批当前阶段目标为完全收口，不只实现单点运维能力。
- 当前分支必须依次完成运维监控、恢复与上线验收，第四批跨能力综合验证，第四批整体质量收口。

## 2026-05-01 第四批运维监控、恢复与上线验收归档

### 归档原因

- 第四批 `Task 8: 运维监控、恢复与上线验收` 已完成测试驱动实现、规格复审、代码质量复审和完整验证。
- 当前真相切换到第四批跨能力综合验证，继续在同一完全收口分支内推进第四批剩余收口任务。

### 已归档任务批次

- [✓] 运维监控、恢复与上线验收：已实现总览层、子系统层、明细层监控面板，覆盖 `OCR`、搜索、`AI` 任务与护栏、候选排序、多语言治理、结果回写六个子系统。
- [✓] 告警与运维治理：已覆盖 `P1`、`P2`、`P3` 告警分级、阈值矩阵、静默收敛、维护窗口、通知渠道路由、运维权限矩阵和敏感操作双人复核。
- [✓] 恢复与回滚：已覆盖恢复脚本清单、回滚手册、真实恢复副作用、`NO_TARGET` / `NOT_IMPLEMENTED` 状态和 `ia_recovery_operation_log` 审计。
- [✓] 健康检查：已实现 `/health/liveness` 与 `/health/readiness`，聚合 `READY`、`DEGRADED`、`UNAVAILABLE`，并处理依赖健康事实缺失与过期状态。
- [✓] 规格复审：多轮规格复审最终结论为通过，没问题。
- [✓] 代码质量复审：复审结论为通过，没问题。
- [✓] 完整验证：`./scripts/verify-all.sh` 通过，后端 `210` 个测试通过，前端依赖检查、lint、vitest、build、Docker Compose 配置、镜像构建、编排启动、healthcheck / smoke 与清理通过。

### 迁移说明

- 运维监控、恢复与上线验收不再作为未完成任务处理。
- 第四批剩余任务为跨能力综合验证与整体质量收口；完成后才能进入第五批综合联调、验收与上线准备。

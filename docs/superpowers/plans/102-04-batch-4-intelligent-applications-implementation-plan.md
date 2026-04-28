# 第四批智能与增强能力 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按第四批“智能与增强能力”完成 `intelligent-applications` 主线的一期实现基准，交付 OCR、全文检索、AI 辅助、智能推荐、多语言知识治理、结果回写与冲突处理的可验收闭环。

**Architecture:** 本批以合同主档为业务真相源、文档中心为文件真相源、`Agent OS` 为 AI 运行时底座，`intelligent-applications` 只承接受控派生结果域。实现顺序遵循“稳定输入 -> 稳定召回 -> AI 应用 -> 候选质量治理 -> 多语言治理 -> 受控回写 -> 运维验收”，不得把 OCR 文本、搜索索引或 AI 输出升级为新的业务真相源。

**Tech Stack:** 后端沿用项目既定技术栈，数据库交付口径为 `MySQL` 并保留 `DB abstraction layer`；缓存、锁和短期快照使用 `Redis`；前端为 `React (SPA) + Tailwind CSS + shadcn/ui`；AI 通过 `Agent OS` 与模型抽象层接入；部署基线为 `Docker Compose / 企业内网`。

---

## 1. 批次定位

本计划是 [`Implementation Batch Plan`](../../technicals/implementation-batch-plan.md) 中“第四批：智能与增强能力”的 Superpowers 执行基准，只覆盖 [`intelligent-applications`](../../technicals/modules/intelligent-applications/implementation-plan.md) 主线。

本批完成以下功能：

- `OCR`：基于文档中心受控版本完成作业受理、引擎适配、版面解析、结果回收、失败重试、版本切换后失效与重建。
- 全文检索：建立合同、文档、条款、OCR 派生文本和阶段后 AI 结果摘要的可重建索引、稳定召回、筛选排序、分页导出和权限裁剪。
- AI 辅助：通过 `Agent OS` 完成摘要、问答、风险识别、比对提取的受控输入装配、输出护栏和人工确认挂点。
- 智能推荐：基于搜索候选、OCR 字段候选、条款 / 模板语义引用、规则命中和质量评估形成可解释候选排序、质量分层与放行建议。
- 多语言知识治理：覆盖中文、英文、西文的术语库、翻译单元、术语快照、语言归一、混语处理和输出术语一致性。
- 结果回写与冲突处理：把通过护栏和必要人工确认的摘要、风险、提取结果受控投影到合同侧关联视图，并处理版本冲突、槽位冲突、置信度冲突和人工否决冲突。

本批允许与第三批后段进行预集成，但全面铺开必须依赖前置主链路已提供稳定合同状态、文档版本、审批摘要、任务事件、异步入口和权限审计能力。

## 2. 输入文档

- 项目原则：[`PRINCIPLE.md`](../../../PRINCIPLE.md)
- 当前真相：[`docs/planning/current.md`](../../planning/current.md)
- 决策记录：[`docs/planning/decisions.md`](../../planning/decisions.md)
- 总平台实施计划：[`docs/technicals/implementation-plan.md`](../../technicals/implementation-plan.md)
- 跨模块批次计划：[`docs/technicals/implementation-batch-plan.md`](../../technicals/implementation-batch-plan.md)
- 本主线架构设计：[`architecture-design.md`](../../technicals/modules/intelligent-applications/architecture-design.md)
- 本主线接口设计：[`api-design.md`](../../technicals/modules/intelligent-applications/api-design.md)
- 本主线详细设计：[`detailed-design.md`](../../technicals/modules/intelligent-applications/detailed-design.md)
- 本主线实施计划：[`implementation-plan.md`](../../technicals/modules/intelligent-applications/implementation-plan.md)
- OCR 专项：[`ocr-engine-and-layout-analysis-design.md`](../../technicals/modules/intelligent-applications/special-designs/ocr-engine-and-layout-analysis-design.md)
- 搜索专项：[`search-index-and-rebuild-design.md`](../../technicals/modules/intelligent-applications/special-designs/search-index-and-rebuild-design.md)
- AI 上下文与护栏专项：[`ai-context-assembly-and-output-guardrails-design.md`](../../technicals/modules/intelligent-applications/special-designs/ai-context-assembly-and-output-guardrails-design.md)
- 候选排序与质量评估专项：[`candidate-ranking-and-quality-evaluation-design.md`](../../technicals/modules/intelligent-applications/special-designs/candidate-ranking-and-quality-evaluation-design.md)
- 多语言知识治理专项：[`multilingual-knowledge-governance-design.md`](../../technicals/modules/intelligent-applications/special-designs/multilingual-knowledge-governance-design.md)
- 结果回写与冲突消解专项：[`result-writeback-and-conflict-resolution-design.md`](../../technicals/modules/intelligent-applications/special-designs/result-writeback-and-conflict-resolution-design.md)
- 运维监控与恢复专项：[`ops-monitoring-alert-and-recovery-design.md`](../../technicals/modules/intelligent-applications/special-designs/ops-monitoring-alert-and-recovery-design.md)

## 3. 文件职责映射

本计划不预设具体代码路径，由执行 Agent 在进入仓库实现时按现有工程结构落位；实现拆分必须保持以下职责边界：

- `ocr-orchestrator`：承接 OCR 作业受理、输入版本校验、引擎路由、结果归一、失败重试、结果失效和重建。
- `search-runtime`：承接索引输入准入、索引文档映射、查询归一、权限裁剪、结果快照、导出、补数与双代重建。
- `ai-application-orchestrator`：承接摘要、问答、风险识别、比对提取的任务受理、上下文装配、`Agent OS` 调用、结果归一和护栏状态投影。
- `semantic-reference-hub`：承接条款 / 模板语义引用、适用范围过滤、版本快照和证据锚点。
- `language-governor`：承接术语库、翻译单元、术语快照、`ia_i18n_context`、语言归一和降级。
- `candidate-ranking`：承接候选标准化、槽位化排序、质量评估、放行决策和解释摘要。
- `result-writeback-gateway`：承接受控回写前置校验、`ia_writeback_record` 生命周期、冲突裁决、死信与审计。
- `ops-governor`：承接监控面板、告警阈值、恢复脚本、回滚手册、运维权限和健康检查。

## 4. 可执行任务清单

### Task 1: 第四批启动门禁核验

**Files:**
- Read: `docs/technicals/implementation-batch-plan.md`
- Read: `docs/technicals/modules/intelligent-applications/implementation-plan.md`
- Inspect implementation entrypoints selected by the executing Agent

- [ ] 核验上游合同主档已提供 `contract_id`、合同摘要、分类主链、条款 / 模板读取和受控回写入口。
- [ ] 核验文档中心已提供 `document_asset_id`、`document_version_id`、受控读取句柄、版本切换事件和能力挂接摘要。
- [ ] 核验 `Agent OS` 已提供统一任务入口、结果查询、人工确认和审计视图。
- [ ] 核验权限、审计、任务中心和 `Docker Compose / 企业内网` 环境可支撑本批异步任务。
- [ ] 输出启动门禁记录，确认未满足项被登记为上游可消费性问题，而不是改写本批范围。

**Completion Flag:** 第四批依赖清单均有明确可消费接口、状态字段、事件或挂载点。

**Verification:** 使用最小样例合同、文档版本、条款版本和测试用户验证读取、权限裁剪、任务受理和审计写入可执行。

### Task 2: OCR 稳定输入闭环

**Files:**
- Baseline: [`ocr-engine-and-layout-analysis-design.md`](../../technicals/modules/intelligent-applications/special-designs/ocr-engine-and-layout-analysis-design.md)

- [ ] 实现基于文档中心受控版本创建 `OcrJob`，支持 `document_asset_id` 或 `document_version_id` 输入并固化 `document_version_id + content_fingerprint`。
- [ ] 实现输入权限校验、幂等键 `document_version_id + job_purpose + idempotency_key` 和任务中心执行记录。
- [ ] 实现 `OcrEngineAdapter`、引擎路由、降级、熔断和结果归一，不向业务接口暴露厂商私有协议。
- [ ] 实现 `OcrResultAggregate`、文本层、版面块、表格、印章、字段候选、语言片段和页级坐标落点。
- [ ] 实现 `READY`、`PARTIAL`、`FAILED`、`SUPERSEDED` 结果状态，文档主版本切换后旧结果退出默认消费并触发新版本重建。
- [ ] 实现 OCR 审计、失败重试、文档中心能力挂接补偿和搜索补索引事件。

**Completion Flag:** 扫描件、图片稿和原生文档均可从文档中心受控版本进入 OCR，并生成可供搜索和 AI 消费的稳定派生结果。

**Verification:** 覆盖 OCR 作业创建、重复幂等、权限拒绝、引擎失败重试、低质量 `PARTIAL`、文档版本切换 `SUPERSEDED`、搜索消费事件的自动化测试和最小端到端演示。

### Task 3: 全文检索与索引重建

**Files:**
- Baseline: [`search-index-and-rebuild-design.md`](../../technicals/modules/intelligent-applications/special-designs/search-index-and-rebuild-design.md)

- [ ] 实现合同主档、文档中心、OCR、条款库的搜索输入准入和 `SearchSourceEnvelope`。
- [ ] 实现 `SearchDocument` 映射，主键按 `doc_type + source_object_id + source_version_digest` 生成。
- [ ] 实现精准查询、模糊查询、筛选、排序、分页、导出、聚合和稳定结果快照。
- [ ] 实现查询前权限裁剪、召回后对象裁剪、返回前字段裁剪，防止越权快照和越权导出。
- [ ] 实现增量刷新、范围重建、全量重建、补数、快照重放、双代构建和别名切换。
- [ ] 实现搜索降级策略，在搜索引擎不可用时允许有限结构化查询但不污染正式来源。

**Completion Flag:** 用户和 AI 应用均可获得可筛选、可排序、可追溯、可重建且权限安全的搜索候选集。

**Verification:** 覆盖合同、文档、条款、OCR 文本查询；权限变化后快照失效；文档版本切换后旧版本退出默认曝光；全量重建双代切换；导出权限二次校验。

### Task 4: AI 辅助应用与输出护栏

**Files:**
- Baseline: [`ai-context-assembly-and-output-guardrails-design.md`](../../technicals/modules/intelligent-applications/special-designs/ai-context-assembly-and-output-guardrails-design.md)

- [ ] 实现摘要、问答、风险识别、比对提取统一任务受理，任务通过 `Agent OS` 而非直接绑定模型或 Provider。
- [ ] 实现 `AiContextEnvelope` 六层装配：任务意图、合同锚点、文档证据、搜索召回、语义引用、护栏配置。
- [ ] 实现证据片段预算、裁剪、降级、来源绑定和无证据拒绝执行。
- [ ] 实现输出 schema 校验、引用校验、敏感信息约束、无来源结论拦截、冲突结果降级和拒答策略。
- [ ] 实现 `ProtectedResultSnapshot` 留存护栏失败、等待人工确认和拒绝结果。
- [ ] 实现人工确认单与 `Agent OS` 人审链路的输入输出契约。

**Completion Flag:** 四类 AI 辅助能力均只能消费受控输入，输出必须有引用、护栏决策和必要人工确认状态。

**Verification:** 覆盖摘要、问答、风险识别、比对提取成功路径；无来源输出拦截；越权问题拒答；高风险转人工；`Agent OS` 超时失败；护栏失败重放。

### Task 5: 智能推荐、候选排序与质量评估

**Files:**
- Baseline: [`candidate-ranking-and-quality-evaluation-design.md`](../../technicals/modules/intelligent-applications/special-designs/candidate-ranking-and-quality-evaluation-design.md)

- [ ] 实现搜索召回、OCR 字段候选、条款 / 模板命中、AI 证据片段和规则命中到 `SemanticCandidate` 的归一。
- [ ] 实现 `SUMMARY_FACT`、`ANSWER_SUPPORT`、`RISK_EVIDENCE`、`RISK_BASELINE`、`DIFF_BASELINE`、`DIFF_DELTA`、`FIELD_VALUE`、`RULE_OVERRIDE` 槽位化排序。
- [ ] 实现候选去重、同源压缩、冲突消解、解释摘要和强否决规则。
- [ ] 实现 `CandidateRankingProfile`、`CandidateQualityProfile` 版本固化。
- [ ] 实现质量分层 `TIER_A`、`TIER_B`、`TIER_C`、`TIER_D` 和放行决策 `PUBLISH`、`PARTIAL_PUBLISH`、`ESCALATE_TO_HUMAN`、`REJECT`。
- [ ] 实现放行决策与护栏、人审、回写的映射，不绕过护栏直接发布结果。

**Completion Flag:** 智能推荐结果具备可解释候选来源、排序理由、质量分层和放行建议。

**Verification:** 覆盖五类候选来源、四类任务差异化质量评估、冲突转人工、低质量拒绝、版本切换后快照失效、放行决策阻断回写。

### Task 6: 多语言知识治理

**Files:**
- Baseline: [`multilingual-knowledge-governance-design.md`](../../technicals/modules/intelligent-applications/special-designs/multilingual-knowledge-governance-design.md)

- [ ] 实现 `TermEntry`、`TranslationUnit`、`TerminologyProfile` 的创建、审核、发布、废弃和版本快照。
- [ ] 实现 `ia_i18n_context` 与术语快照绑定，确保 `terminology_profile_code + profile_version` 在任务生命周期内不可漂移。
- [ ] 实现中文、英文、西文的输入语言、归一语言、输出语言、展示标签语言治理。
- [ ] 实现混语文档语言片段保留、术语缺失降级、`i18n_status=FAILED` 下游降级路径。
- [ ] 实现 AI 输出术语一致性护栏，拦截未命中术语快照或翻译不一致结果。
- [ ] 实现术语生命周期审计和缓存失效后数据库正式记录回退读取。

**Completion Flag:** 多语言能力覆盖 OCR、搜索、AI 应用、候选排序、回写展示主链，术语快照可审计、可回放、可发布。

**Verification:** 覆盖术语新增发布、翻译单元审核、术语废弃、混语文档处理、术语快照缓存失效、输出术语错误拦截、历史任务术语快照不漂移。

### Task 7: 结果回写与冲突处理

**Files:**
- Baseline: [`result-writeback-and-conflict-resolution-design.md`](../../technicals/modules/intelligent-applications/special-designs/result-writeback-and-conflict-resolution-design.md)

- [ ] 实现 `result-writeback-gateway`，只允许已通过护栏和必要人工确认的结果进入回写候选。
- [ ] 实现 `CONTRACT_SUMMARY`、`CONTRACT_RISK_VIEW`、`CONTRACT_EXTRACTION_VIEW` 三个目标视图字段级映射。
- [ ] 实现禁止写入清单，拦截合同主字段、业务状态、文档版本、条款正文、模型私有信息和越权字段。
- [ ] 实现 `ia_writeback_record` 生命周期 `PENDING -> WRITING -> WRITTEN / SKIPPED / FAILED`。
- [ ] 实现版本冲突、同槽位冲突、置信度冲突、人工否决冲突和 `UNRESOLVED_EQUAL` 人工升级。
- [ ] 实现回写幂等、短时互斥锁、乐观锁、死信队列、失败重试和全链路审计。

**Completion Flag:** 摘要、风险、提取结果可受控投影到合同侧关联视图，且不改写合同业务真相、文件真相或条款正式内容。

**Verification:** 覆盖摘要回写、风险回写、提取结果回写、低置信度阻断、人工确认后回写、并发写冲突、版本冲突、死信恢复和审计追溯链。

### Task 8: 运维监控、恢复与上线验收

**Files:**
- Baseline: [`ops-monitoring-alert-and-recovery-design.md`](../../technicals/modules/intelligent-applications/special-designs/ops-monitoring-alert-and-recovery-design.md)

- [ ] 实现总览层、子系统层、明细层监控面板，覆盖 OCR、搜索、AI 任务与护栏、候选排序、多语言治理、结果回写六个子系统。
- [ ] 实现 P1 / P2 / P3 告警分级、阈值矩阵、静默收敛、维护窗口和通知渠道路由。
- [ ] 实现恢复脚本清单，覆盖 OCR 死信重推、搜索补数、AI 任务重推、候选重排、多语言重处理、回写死信重推。
- [ ] 实现搜索双代回滚、OCR 引擎路由回滚、回写批量失效、AI 结果批量标记失效等回滚手册。
- [ ] 实现运维权限矩阵、敏感操作双人复核、`ia_recovery_operation_log` 审计。
- [ ] 实现 `/health/liveness` 与 `/health/readiness`，聚合 `READY`、`DEGRADED`、`UNAVAILABLE` 状态。

**Completion Flag:** 本主线具备可观测、可告警、可恢复、可回滚、可审计的上线运行能力。

**Verification:** 执行故障演练：OCR 死信、搜索索引失败、`Agent OS` 超时、回写死信、术语快照加载失败、搜索别名回滚、健康检查降级。

## 5. 综合验证方式

- 单元测试：覆盖状态机、幂等、权限裁剪、候选排序、质量分层、护栏校验、回写冲突和术语版本规则。
- 集成测试：覆盖“文档进入 -> OCR -> 索引刷新 -> 搜索召回 -> AI 应用 -> 候选排序 -> 护栏 / 人审 -> 受控回写 -> 审计回查”。
- 权限测试：覆盖跨部门、跨合同、跨文档、跨语言、可看不可导、可问不可回写等权限边界。
- 数据一致性测试：确认合同主档、文档中心、条款库不被 OCR、搜索或 AI 派生结果反向污染。
- 恢复测试：覆盖 OCR 重试、搜索补数、AI 护栏重放、候选重排、术语快照恢复、回写死信重推。
- 性能测试：覆盖 OCR 队列积压、搜索 P95 / P99、AI 任务耗时、候选排序耗时、回写耗时和监控刷新延迟。
- 部署测试：在 `Docker Compose / 企业内网` 基线环境完成依赖服务启动、配置注入、健康检查和日志审计验证。

## 6. 完成标志

- OCR、全文检索、AI 辅助、智能推荐、多语言知识治理、结果回写与冲突处理均达到本计划任务清单的完成标志。
- 所有 AI 应用结果均带来源引用、护栏决策、质量评估锚点和审计链路。
- 搜索索引、OCR 结果、AI 结果、候选快照、术语快照和回写记录均可失效、可重建、可追溯。
- 中文、英文、西文覆盖 OCR、搜索、AI 输出、候选排序和回写展示主链。
- `intelligent-applications` 在不破坏合同主档、文档中心、条款库和 `Agent OS` 边界的前提下，进入可联调、可回归、可验收状态。

## 7. 质量审查要求

- 审查是否严格对应第四批“智能与增强能力”，不得扩展到第五批外部联调和上线切换事项。
- 审查是否严格保持合同主档、文档中心、条款库、`Agent OS` 的真相源和运行时边界。
- 审查是否存在模型 / Provider 绑定、厂商 OCR 协议泄露、搜索引擎私有 DSL 外露或 AI 结果直接改写正式事实。
- 审查所有状态枚举、幂等键、冲突码、质量分层、护栏决策与专项设计一致。
- 审查每个可展示或可回写结果是否有来源引用、权限校验、人工确认策略和审计追溯。
- 审查测试证据是否覆盖正常路径、异常路径、权限路径、恢复路径、性能路径和企业内网部署路径。

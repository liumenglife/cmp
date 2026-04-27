# 检索 / OCR / AI 业务应用主线 Detailed Design

## 1. 文档说明

本文档是“检索 / OCR / AI 业务应用主线”的第一份正式
`Detailed Design`。

本文只描述该主线的内部实现层设计，用于把已在上游文档中收口的主线边界，
继续下沉为可实现、可落库、可恢复、可审计的内部模型与运行机制。

### 1.1 输入

- 上游需求基线：
  [`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- 总平台架构边界：
  [`Architecture Design`](../../architecture-design.md)
- 总平台接口边界：
  [`API Design`](../../api-design.md)
- 总平台共享内部边界：
  [`Detailed Design`](../../detailed-design.md)
- 本主线架构边界：
  [`Architecture Design`](./architecture-design.md)
- 本主线接口边界：
  [`API Design`](./api-design.md)
- `Agent OS` 详细设计：
  [`Detailed Design`](../../foundations/agent-os/detailed-design.md)
- 文档中心详细设计：
  [`Detailed Design`](../document-center/detailed-design.md)
- 合同管理本体详细设计：
  [`Detailed Design`](../contract-core/detailed-design.md)

### 1.2 输出

- 本文：[`Detailed Design`](./detailed-design.md)
- 为后续本主线专项实现、表级 DDL、测试设计、运维治理提供内部设计基线

### 1.3 阅读边界

本文只回答以下问题：

- 本主线内部如何拆分并协作
- `OCR`、搜索、AI 业务应用如何在同一内部模型下运行
- 核心物理表、状态、引用、回写、审计如何设计
- 条款库 / 模板库语义引用、多语言、任务、补偿、恢复如何落点
- 与合同主档、文档中心、`Agent OS` 如何内部挂接

本文不承担以下内容：

- 不复述需求范围、业务收益、验收口径或实施排期
- 不重写总平台或本主线架构总览
- 不重写对外 API 资源清单、请求响应字段或错误码全集
- 不写 Prompt 正文、向量参数、模型超参数、算法代码细节
- 不把本文写成 `Implementation Plan`

## 2. 设计目标与约束落点

本主线内部实现以“一个业务锚点、一个文件锚点、一套受控派生结果域”为核心。

### 2.1 真相源与派生结果落点

- 合同主档是业务真相源。本主线全部任务、结果、回写、审计都围绕
  `contract_id` 组织，不生成第二份合同真相。
- 文档中心是文件真相源。本主线只引用 `document_asset_id` /
  `document_version_id`，不复制文件版本链或生成长期私有文件库。
- `Agent OS` 是 AI 运行时底座。本主线只描述业务应用如何接入该底座，
  不接管模型 / `Provider` 路由、Prompt 装配、工具系统与人工确认内核。
- 条款库是正式能力资源和 AI 重要底座。本主线引用条款编码、版本、语义标签、
  适用范围和多语言内容，不长出“AI 专用条款库”。
- 多语言是正式能力，不是附加备注。内部模型必须覆盖语言识别、语言归一、
  跨语言召回、结果输出语言、术语一致性和回写展示语言。
- `OCR` 结果、搜索结果、AI 应用结果都是派生结果，只能形成引用、快照、
  候选结论和受控回写，不得升级为新的业务真相源。

### 2.2 一致性与抽象边界落点

- 正式状态落 `MySQL`，缓存、幂等键、短态锁、短期候选集快照落 `Redis`。
- 所有 AI 业务应用统一通过 `Agent OS` 发起运行，不允许业务模块直接绑定
  具体模型或具体 `Provider`。
- 搜索索引和搜索结果集是读模型，只服务稳定召回、筛选、排序、引用与证据定位。
- `OCR` 结果是识别中间层，负责把文档中心受控文件版本转成文本、版面和字段候选，
  但不改写文件真相。
- 结果回写只能回写合同主档关联摘要区、风险视图、提取结果视图或应用结果区；
  不能直接覆盖合同主字段、文件主版本或条款正式内容。
- 高风险结果必须保留人工确认挂点；人工确认对象归 `Agent OS`，本主线只持有
  确认状态快照与业务放行状态。

### 2.3 事务与失败边界落点

- 单个作业受理事务只覆盖：任务主表、输入引用表、初始状态、幂等记录、审计事件。
- 引擎执行、外部识别、索引刷新、AI 运行、结果回写、索引回补均通过异步任务推进。
- 任一派生结果失败不得破坏合同主档与文档中心已成立的正式状态。
- 恢复以“任务可重领、结果可重建、回写可重放、审计可追踪”为最低要求。

## 3. 模块内部拆分

本主线按“输入治理、结果治理、应用编排、支撑治理”四层拆分。

### 3.1 `ocr-orchestrator`

- 负责 `OCR` 作业受理、输入版本校验、识别引擎路由、结果回收、重试与失败分流。
- 输出统一 `OcrResultAggregate`，供搜索和 AI 应用消费。
- 不持有文件对象真相，也不直接切换文档中心版本。

### 3.2 `search-runtime`

- 负责查询归一、范围裁剪、召回、过滤、排序、分页与结果集持久化。
- 维护合同级、文档级、条款级、AI 结果级搜索读模型的聚合口径。
- 不解释合同主状态、不解释文件主版本、不承诺任何结果为业务真相。

### 3.3 `ai-application-orchestrator`

- 负责摘要、问答、风险识别、比对提取四类应用的统一任务受理与编排。
- 组装合同主档摘要、文档引用、`OCR` 结果、搜索候选集、条款 / 模板引用后，
  调用 `Agent OS`。
- 统一沉淀应用结果、引用、回写状态和人工确认需求。

### 3.4 `semantic-reference-hub`

- 负责条款库 / 模板库语义引用、版本快照、适用范围过滤、引用稳定编码治理。
- 为搜索和 AI 应用提供稳定的 `ClauseSemanticRef` / `TemplateSemanticRef`。
- 不拥有条款正文真相和模板正文真相；正式资源仍归合同管理本体。

### 3.5 `language-governor`

- 负责语言识别、语言归一、术语映射、跨语言召回辅助与输出语言控制。
- 管理文档片段语言、查询语言、回答语言、回写展示语言的统一口径。
- 不把翻译缓存升级为正式业务真相。

### 3.6 `result-writeback-gateway`

- 负责把摘要、风险、提取结果以受控方式回写到合同主档关联视图。
- 管理回写幂等、版本快照、冲突校验、人工确认前置条件和审计落库。
- 不直接写合同主字段，不绕过合同管理本体的受控写入口。

### 3.7 `ops-governor`

- 负责缓存、锁、幂等、并发、异步重试、补偿、指标与恢复脚本入口。
- 管理内部任务状态推进和结果重建策略。

### 3.8 内部协作主链路

1. 文档中心或业务侧提交 `OCR`、搜索、AI 应用请求。
2. 受理层创建作业主记录、输入引用和幂等记录。
3. `OCR` 或搜索先形成稳定派生结果；AI 应用读取这些受控输入。
4. AI 应用通过 `Agent OS` 执行，得到统一结果对象与确认状态。
5. 需要回写时由 `result-writeback-gateway` 调用合同管理本体受控写入口。
6. 全链路写审计、日志、指标与恢复锚点。

## 4. 核心物理表设计

本节只列主线核心表与必要引用 / 回写表，不展开完整 DDL。
全部表默认包含基础字段：`created_at`、`created_by`、`updated_at`、
`updated_by`、`is_deleted`、`version_no`。

### 4.1 `ia_ocr_job`

- 用途：`OCR` 作业主表，承接一次受控文件识别任务。
- 关键主键：`ocr_job_id`
- 关键字段：
  - `contract_id`
  - `document_asset_id`、`document_version_id`
  - `job_purpose`：`TEXT_EXTRACTION`、`LAYOUT_EXTRACTION`、`FIELD_ASSIST`、
    `SEARCH_INDEX_INPUT`
  - `job_status`：`ACCEPTED`、`QUEUED`、`RUNNING`、`SUCCEEDED`、`FAILED`、
    `CANCELLED`
  - `language_hint_json`
  - `quality_profile_code`
  - `detected_language_json`
  - `ocr_engine_code`
  - `current_attempt_no`、`max_attempt_no`
  - `result_id`
  - `failure_code`、`failure_reason`
  - `idempotency_key`
  - `trace_id`
- 关键索引 / 唯一约束：
  - `uk_ocr_idempotency(document_version_id, job_purpose, idempotency_key)`
  - `idx_ocr_pick(job_status, created_at)`
  - `idx_ocr_contract(contract_id, created_at)`
  - `idx_ocr_document(document_asset_id, document_version_id)`
- 关联对象：合同主档、文档中心版本、`ia_ocr_result`、任务中心、审计中心。

### 4.2 `ia_ocr_result`

- 用途：`OCR` 稳定结果表，承接文本、版面、字段候选和引用摘要。
- 关键主键：`ocr_result_id`
- 关键字段：
  - `ocr_job_id`
  - `contract_id`
  - `document_asset_id`、`document_version_id`
  - `result_status`：`READY`、`PARTIAL`、`FAILED`、`SUPERSEDED`
  - `result_schema_version`
  - `quality_profile_code`
  - `full_text_ref`
  - `page_payload_json`
  - `layout_block_payload_json`
  - `field_candidate_payload_json`
  - `citation_payload_json`
  - `language_segment_payload_json`
  - `content_fingerprint`
  - `quality_score`
  - `used_for_search_flag`
  - `used_for_ai_flag`
- 关键索引 / 唯一约束：
  - `uk_ocr_result_job(ocr_job_id)`
  - `idx_ocr_result_document(document_version_id, result_status)`
  - `idx_ocr_result_contract(contract_id, result_status)`
  - `idx_ocr_result_fingerprint(content_fingerprint)`
- 关联对象：搜索索引输入、AI 应用上下文、文档中心版本。

### 4.3 `ia_search_query`

- 用途：搜索请求主表，持久化一次查询受理与查询条件快照。
- 关键主键：`search_query_id`
- 关键字段：
  - `requester_user_id`
  - `contract_id_scope_json`
  - `document_asset_scope_json`
  - `query_text`
  - `query_language`
  - `normalized_query_text`
  - `search_scope_json`
  - `filter_payload_json`
  - `sort_by`
  - `page`、`page_size`
  - `query_status`：`ACCEPTED`、`RUNNING`、`READY`、`FAILED`
  - `result_set_id`
  - `trace_id`
  - `cache_key`
- 关键索引 / 唯一约束：
  - `idx_search_query_user(requester_user_id, created_at)`
  - `idx_search_query_status(query_status, created_at)`
  - `idx_search_query_contract(created_at, query_language)`
- 关联对象：`ia_search_result_set`、权限裁剪、审计查询。

### 4.4 `ia_search_result_set`

- 用途：搜索稳定结果集表，表达一次查询得到的候选集快照。
- 关键主键：`result_set_id`
- 关键字段：
  - `search_query_id`
  - `result_status`：`READY`、`PARTIAL`、`FAILED`、`EXPIRED`
  - `query_text`
  - `matched_language_json`
  - `item_payload_json`
  - `facet_payload_json`
  - `total`
  - `ranking_profile_code`
  - `expires_at`
  - `cache_hit_flag`
- 关键索引 / 唯一约束：
  - `uk_result_set_query(search_query_id)`
  - `idx_result_set_status(result_status, expires_at)`
  - `idx_result_set_created(created_at)`
- 关联对象：AI 业务应用候选集、前端检索结果展示、审计追踪。

### 4.5 `ia_ai_application_job`

- 用途：AI 业务应用任务主表，统一承接摘要、问答、风险识别、比对提取。
- 关键主键：`ai_application_job_id`
- 关键字段：
  - `application_type`：`SUMMARY`、`QA`、`RISK_ANALYSIS`、`DIFF_EXTRACTION`
  - `contract_id`
  - `document_asset_scope_json`
  - `document_version_scope_json`
  - `clause_scope_json`
  - `template_scope_json`
  - `request_language`
  - `response_language`
  - `context_scope`
  - `job_status`：`ACCEPTED`、`QUEUED`、`PREPARING_CONTEXT`、`RUNNING`、
    `WAITING_HUMAN_CONFIRMATION`、`SUCCEEDED`、`FAILED`、`CANCELLED`
  - `ranking_snapshot_id`
  - `quality_evaluation_id`
  - `ranking_profile_code`
  - `ranking_profile_version`
  - `quality_profile_code`
  - `quality_profile_version`
  - `agent_task_id`
  - `result_id`
  - `idempotency_key`
  - `failure_code`、`failure_reason`
  - `trace_id`
- 关键索引 / 唯一约束：
  - `uk_ai_job_idempotency(application_type, contract_id, idempotency_key)`
  - `idx_ai_job_status(job_status, created_at)`
  - `idx_ai_job_contract(contract_id, application_type, created_at)`
  - `idx_ai_job_agent(agent_task_id)`
- 关联对象：合同主档、搜索结果集、语义引用表、`Agent OS` 任务对象、
  `ia_ai_application_result`。

### 4.6 `ia_ai_application_result`

- 用途：统一 AI 业务应用结果主表，表达稳定应用结果视图。
- 关键主键：`result_id`
- 关键字段：
  - `ai_application_job_id`
  - `application_type`
  - `contract_id`
  - `result_status`：`READY`、`PARTIAL`、`FAILED`、`REJECTED`、`SUPERSEDED`
  - `result_summary`
  - `structured_payload_json`
  - `citation_payload_json`
  - `evidence_digest`
  - `ranking_snapshot_id`
  - `quality_evaluation_id`
  - `guardrail_decision`
  - `guardrail_failure_code`
  - `ranking_profile_code`
  - `ranking_profile_version`
  - `quality_profile_code`
  - `quality_profile_version`
  - `human_confirmation_required`
  - `human_confirmation_status`
  - `written_back_status`：`NOT_REQUIRED`、`PENDING`、`WRITING`、`WRITTEN`、
    `FAILED`
  - `writeback_record_id`
  - `agent_result_id`
- 关键索引 / 唯一约束：
  - `uk_ai_result_job(ai_application_job_id)`
  - `idx_ai_result_contract(contract_id, application_type, result_status)`
  - `idx_ai_result_writeback(written_back_status, updated_at)`
  - `idx_ai_result_agent(agent_result_id)`
- 关联对象：`Agent OS` 结果对象、专项结果表、回写记录、审计事件。

### 4.7 `ia_summary_result`

- 用途：摘要场景专用结果表，承接面向展示与回写的摘要结构。
- 关键主键：`summary_result_id`
- 关键字段：
  - `result_id`
  - `contract_id`
  - `summary_scope`：`CONTRACT_OVERVIEW`、`DOCUMENT_OVERVIEW`、`CLAUSE_FOCUS`、
    `RISK_FOCUS`
  - `summary_text`
  - `section_payload_json`
  - `citation_payload_json`
  - `display_language`
  - `result_status`
  - `summary_digest`
- 关键索引 / 唯一约束：
  - `uk_summary_result(result_id)`
  - `idx_summary_contract(contract_id, summary_scope, result_status)`
  - `idx_summary_digest(summary_digest)`
- 关联对象：`ia_ai_application_result`、合同主档摘要区、搜索结果级引用。

### 4.8 `ia_qa_session`

- 用途：问答会话主表，承接围绕合同或文档范围的多轮问答上下文外壳。
- 关键主键：`qa_session_id`
- 关键字段：
  - `contract_id`
  - `session_language`
  - `answer_language`
  - `document_scope_json`
  - `clause_scope_json`
  - `session_status`：`ACTIVE`、`WAITING_ANSWER`、`CLOSED`、`EXPIRED`
  - `latest_message_id`
  - `last_result_id`
  - `context_snapshot_ref`
  - `expires_at`
- 关键索引 / 唯一约束：
  - `idx_qa_session_contract(contract_id, session_status, updated_at)`
  - `idx_qa_session_expire(session_status, expires_at)`
- 关联对象：问答消息、AI 任务、条款 / 模板引用、搜索候选集。

### 4.9 `ia_risk_analysis`

- 用途：风险识别专用结果表，承接风险项、等级、依据和建议。
- 关键主键：`risk_analysis_id`
- 关键字段：
  - `result_id`
  - `contract_id`
  - `risk_level`
  - `risk_item_payload_json`
  - `clause_gap_payload_json`
  - `recommendation_payload_json`
  - `evidence_payload_json`
  - `requires_manual_review`
  - `writeback_scope`：`CONTRACT_RISK_VIEW`、`APPLICATION_ONLY`
  - `result_status`
- 关键索引 / 唯一约束：
  - `uk_risk_result(result_id)`
  - `idx_risk_contract(contract_id, risk_level, result_status)`
  - `idx_risk_review(requires_manual_review, updated_at)`
- 关联对象：`ia_ai_application_result`、合同风险视图、条款引用表。

### 4.10 `ia_diff_extraction_result`

- 用途：比对提取专用结果表，承接字段提取、条款提取、差异对照结果。
- 关键主键：`diff_extraction_result_id`
- 关键字段：
  - `result_id`
  - `contract_id`
  - `comparison_mode`：`DOCUMENT_TO_TEMPLATE`、`DOCUMENT_TO_DOCUMENT`、
    `DOCUMENT_TO_CLAUSE_SET`
  - `extracted_field_payload_json`
  - `clause_match_payload_json`
  - `diff_payload_json`
  - `confidence_payload_json`
  - `requires_manual_review`
  - `result_status`
- 关键索引 / 唯一约束：
  - `uk_diff_result(result_id)`
  - `idx_diff_contract(contract_id, comparison_mode, result_status)`
  - `idx_diff_review(requires_manual_review, updated_at)`
- 关联对象：`ia_ai_application_result`、模板 / 条款语义引用、合同视图回写。

### 4.11 `ia_i18n_context`

- 用途：主线多语言上下文表，统一管理语言识别、归一与术语快照。
- 关键主键：`i18n_context_id`
- 关键字段：
  - `owner_type`：`OCR_RESULT`、`SEARCH_QUERY`、`SEARCH_RESULT_SET`、
    `AI_JOB`、`AI_RESULT`、`QA_SESSION`
  - `owner_id`
  - `source_language`
  - `normalized_language`
  - `response_language`
  - `language_confidence`
  - `terminology_profile_code`
  - `translation_needed_flag`
  - `segment_language_payload_json`
  - `i18n_status`：`DETECTED`、`NORMALIZED`、`APPLIED`、`FAILED`
- 关键索引 / 唯一约束：
  - `uk_i18n_owner(owner_type, owner_id)`
  - `idx_i18n_language(normalized_language, i18n_status)`
  - `idx_i18n_profile(terminology_profile_code)`
- 关联对象：`OCR` 结果、搜索查询、AI 任务 / 结果、问答会话。

### 4.12 `ia_semantic_reference`

- 用途：条款库 / 模板库语义引用表，持有 AI 与搜索使用的稳定资源快照。
- 关键主键：`semantic_reference_id`
- 关键字段：
  - `reference_type`：`CLAUSE_VERSION`、`TEMPLATE_VERSION`
  - `reference_id`
  - `contract_id`
  - `application_job_id`
  - `search_query_id`
  - `semantic_role`：`GROUNDING`、`RISK_BASELINE`、`DIFF_BASELINE`、
    `ANSWER_EVIDENCE`
  - `snapshot_payload_json`
  - `scope_match_score`
  - `language_code`
  - `is_primary`
- 关键索引 / 唯一约束：
  - `idx_semantic_contract(contract_id, reference_type, semantic_role)`
  - `idx_semantic_job(application_job_id, semantic_role)`
  - `idx_semantic_search(search_query_id, semantic_role)`
- 关联对象：合同主档、`cc_clause_version`、`cc_template_version`、AI 任务、搜索查询。

### 4.13 `ia_writeback_record`

- 用途：结果回写记录表，表达一次受控回写的对象、动作、状态与冲突结果。
- 关键主键：`writeback_record_id`
- 关键字段：
  - `result_id`
  - `target_type`：`CONTRACT_SUMMARY`、`CONTRACT_RISK_VIEW`、
    `CONTRACT_EXTRACTION_VIEW`
  - `target_id`
  - `writeback_action`：`UPSERT_REFERENCE`、`UPSERT_VIEW`、`MARK_SUPERSEDED`
  - `writeback_status`：`PENDING`、`WRITING`、`WRITTEN`、`FAILED`、`SKIPPED`
  - `target_snapshot_version`
  - `conflict_code`
  - `failure_reason`
  - `operator_type`
  - `completed_at`
- 关键索引 / 唯一约束：
  - `uk_writeback_result_target(result_id, target_type, target_id)`
  - `idx_writeback_status(writeback_status, updated_at)`
  - `idx_writeback_target(target_type, target_id, completed_at)`
- 关联对象：AI 结果、合同管理本体受控写接口、审计事件。

### 4.14 `ia_result_candidate`

- 用途：应用前置候选集表，保存搜索召回、字段候选、条款候选等中间可审计结果。
- 关键主键：`candidate_id`
- 关键字段：
  - `owner_type`：`AI_JOB`、`OCR_RESULT`、`SEARCH_RESULT_SET`
  - `owner_id`
  - `candidate_type`：`CLAUSE`、`TEMPLATE`、`DOCUMENT_SEGMENT`、`FIELD_MATCH`
  - `candidate_ref_id`
  - `candidate_score`
  - `candidate_rank`
  - `candidate_payload_json`
  - `selected_flag`
- 关键索引 / 唯一约束：
  - `idx_candidate_owner(owner_type, owner_id, candidate_type)`
  - `idx_candidate_selected(owner_type, owner_id, selected_flag)`
- 关联对象：搜索结果集、语义引用、AI 作业。

### 4.15 `ia_recovery_operation_log`

- 用途：`ops-governor` 专有恢复操作日志表，承接所有恢复脚本与回滚动作的执行记录。
- 关键主键：`recovery_log_id`
- 关键字段：
  - `script_name`
  - `script_version`
  - `operator_id`
  - `operator_role`
  - `action_type`：`RECOVERY`、`ROLLBACK`、`MANUAL_INTERVENTION`
  - `target_subsystem`：`OCR`、`SEARCH`、`AI`、`RANKING`、`I18N`、`WRITEBACK`
  - `target_scope_json`：受影响的 `contract_id`、`document_version_id`、`task_id` 等业务锚点摘要
  - `input_params_json`
  - `execution_status`：`STARTED`、`SUCCEEDED`、`FAILED`、`ROLLED_BACK`
  - `output_summary_json`：受影响记录数、处理摘要、错误摘要
  - `started_at`
  - `completed_at`
  - `trace_id`
  - `review_operator_id`：双人复核场景下的复核人
- 关键索引 / 唯一约束：
  - `idx_recovery_log_subsystem(target_subsystem, created_at)`
  - `idx_recovery_log_script(script_name, created_at)`
  - `idx_recovery_log_operator(operator_id, created_at)`
  - `idx_recovery_log_trace(trace_id)`
- 关联对象：审计中心、运维权限体系、各子系统恢复脚本上下文。
- 说明：该表是 `ops-governor` 的内部运维表，仅用于操作审计与故障复盘，不直接关联业务主链，不对普通业务查询暴露。

## 5. OCR 内部模型

### 5.1 输入模型

- `OcrInputRef`：
  - 必须引用文档中心受控版本
  - 最小锚点是 `document_version_id`
  - 可选补充 `contract_id`、`document_role`、`language_hint`
- `OcrExecutionProfile`：
  - 表达业务目的，而不是底层引擎参数
  - 目的类型决定结果最小产出要求

### 5.2 结果模型

- `OcrTextLayer`：按页、段、行组织的文本视图
- `OcrLayoutBlock`：标题、正文、表格、印章区、签署区、页眉页脚等版面块
- `OcrFieldCandidate`：金额、日期、相对方、条款标题等字段候选
- `OcrCitation`：从结果回指文档页码、块位置、文本范围的引用坐标
- `OcrLanguageSegment`：用于多语言分段归属和后续搜索 / AI 消费
- `OcrQualityProfile`：`OCR` 质量配置对象；`ia_ocr_job` 在受理时固化 `quality_profile_code`，`ia_ocr_result` 持有同一 `quality_profile_code` 与 `result_schema_version`，字段候选载荷需能表达候选命中的字段阈值分组，用于解释 `LOW_CONFIDENCE` 等质量标记来源

### 5.3 状态语义

- `ACCEPTED`：已受理但尚未进入执行队列
- `QUEUED`：已准备执行
- `RUNNING`：识别引擎处理中
- `SUCCEEDED`：已形成稳定结果
- `FAILED`：本轮执行失败，可按策略重试
- `CANCELLED`：被人工或系统取消，不再推进

### 5.4 关键实现约束

- 结果按 `document_version_id + content_fingerprint` 判定是否可复用，避免同一版本重复识别。
- `OCR` 结果一旦产生，只能被新结果 `SUPERSEDED`，不能原地覆盖，以保留审计链路。
- 字段候选只进入候选域，不直接更新合同字段。
- 表格、印章、签名区域等复杂版面只保留抽象块和定位，不在本层写死具体识别算法。

## 6. 搜索内部模型

### 6.1 索引输入模型

- `ContractSearchDoc`：来自合同主档摘要和分类主链
- `DocumentSearchDoc`：来自文档中心引用和 `OCR` 文本
- `ClauseSearchDoc`：来自条款库启用版本和语义标签
- `AiResultSearchDoc`：作为阶段三后的增量扩展对象，可来自已稳定的 AI 应用结果，但必须回指原始合同 / 文档 / 条款来源

### 6.2 查询模型

- `SearchIntent`：关键词、范围、过滤、排序和语言意图的归一化对象
- `SearchScopeResolver`：把用户查询范围裁剪为有权限、有效版本、有效资源集合
- `SearchCandidateSet`：召回后的候选对象集合
- `SearchResultItem`：稳定输出项，包含标题、摘要、命中片段、来源引用、语言、得分

### 6.3 结果集治理

- 搜索结果集持久化到 `ia_search_result_set`，生命周期短于正式业务对象。
- 结果集允许缓存命中，但缓存失效后可按相同查询重新构建。
- `AI_RESULT` 类型只作为阶段三后的增强召回入口，不允许成为无来源证据的孤立条目。

### 6.4 关键实现约束

- 搜索必须先做权限裁剪，再做结果持久化，避免形成越权缓存结果集。
- 搜索命中片段统一回指合同、文档页、条款版本或 AI 结果引用，不返回无锚点文本。
- 多语言查询按“查询语言归一 + 术语映射 + 候选语言标记”推进，不把翻译结果当作真相。
- 搜索引擎内部实现可替换，但查询受理、结果集对象、引用和审计口径保持不变。

## 7. AI 业务应用内部模型

### 7.1 统一任务模型

- `AiApplicationContext` 由五类输入组成：
  - 合同主档摘要
  - 文档中心受控引用
  - `OCR` 结构化结果
  - 搜索稳定候选集
  - 条款 / 模板语义引用
- `AiApplicationPolicy` 负责定义：
  - 是否允许自动回写
  - 是否必须人工确认
  - 允许消费的工具和输入范围

### 7.2 摘要模型

- 摘要结果统一分为总摘要、章节摘要、风险聚焦摘要三类视图。
- 同一合同允许存在多个摘要结果，但只允许一个“当前推荐摘要”进入回写候选。
- 摘要回写只更新合同关联摘要区引用，不覆盖合同主字段。

### 7.3 问答模型

- `QaSession` 是会话壳，不是答案真相源。
- 每轮问答都形成独立 AI 任务和独立结果，答案通过会话关系串联。
- 会话上下文只持有稳定引用和摘要，不把全部历史消息无限堆入运行时。

### 7.4 风险识别模型

- 风险识别以条款库和模板库为基线，以合同正文和 `OCR` 结果为输入。
- 风险项至少包含：风险类型、风险等级、证据片段、参照条款、建议动作。
- 风险视图可回写合同风险区，但只能作为辅助视图，不自动改写主状态。

### 7.5 比对提取模型

- 比对提取包含字段提取、条款对齐、差异检测三层结果。
- 输出必须同时保留“识别值”和“来源证据”，避免只有结论没有依据。
- 对低置信度字段进入候选确认态，不直接回写。

### 7.6 与 `Agent OS` 的内部连接模型

- 本主线向 `Agent OS` 提交的是业务语义任务，而不是模型调用参数。
- 关键关联键：`ai_application_job_id -> agent_task_id -> agent_result_id`。
- 本主线只消费 `Agent OS` 的统一结果对象、确认状态、失败摘要和审计锚点。
- 候选排序与质量评估层生成的 `ranking_snapshot_id`、`quality_evaluation_id` 会在任务主表和结果主表留锚；护栏层最终写入 `guardrail_decision` 与 `guardrail_failure_code`，形成“候选选择 -> 质量判断 -> 护栏处理”完整追踪链。
- 一旦 `Agent OS` 返回 `REJECTED_BY_HUMAN` 或待确认态，本主线同步投影为
  `WAITING_HUMAN_CONFIRMATION` 或 `REJECTED`，不自行越过确认闸门。

## 8. 条款库 / 模板库语义引用、多语言、结果回写与审计内部模型

### 8.1 语义引用模型

- `ClauseSemanticRef`：
  - 基于 `cc_clause_version`
  - 携带条款编码、版本、风险标签、适用范围、语义标签、多语言内容摘要
- `TemplateSemanticRef`：
  - 基于 `cc_template_version`
  - 携带模板分类主链、结构摘要、变量摘要、条款快照摘要
- 语义引用落到 `ia_semantic_reference`，作为任务级或查询级快照使用。

### 8.2 适用范围裁剪

- 在引用条款和模板前，必须按合同分类主链、组织范围、语言和启停状态进行过滤。
- 不允许把停用条款、历史废弃版本或不匹配分类的模板直接送入 grounding。
- 当条款 / 模板在任务执行后被更新，已形成的引用快照不回改，只允许新任务使用新版本。

### 8.3 多语言模型

- 主线统一支持中文、英文、西文。
- 多语言分为四层：
  - 输入语言：用户查询、问句、文档片段原始语言
  - 归一语言：内部检索和术语治理主语言
  - 输出语言：摘要、答案、风险说明、提取结果展示语言
  - 展示标签语言：面向前端展示的 `label` / `i18n_key`
- `ia_i18n_context` 不保存全部翻译正文，只保存语言决策、术语档案和段落归属。

### 8.4 回写模型

- 回写入口统一走 `ia_writeback_record`。
- 回写动作只允许：
  - 关联摘要引用
  - 关联风险视图引用
  - 关联提取结果引用
  - 标记旧结果失效
- 不允许：
  - 直接改合同编号、名称、相对方、状态
  - 直接切换文档中心主版本
  - 直接改条款正式内容或模板正式版本

### 8.5 审计模型

- 每次语义引用都要保留：引用对象、版本、适用范围判断、语言判断、任务来源。
- 每次回写都要保留：结果对象、目标对象、写前快照版本、写后状态、操作人 / 系统。
- 审计上优先记录“为什么用了这个条款 / 模板版本”和“为什么允许这次回写”，
  而不仅是“调用了某个模型”。

## 9. 与合同主档、文档中心、Agent OS 的内部挂接设计

### 9.1 与合同主档的挂接

- 本主线所有任务优先以 `contract_id` 作为业务锚点。
- 读取口径只消费合同主档、合同摘要、分类主链、模板 / 条款引用信息和风险视图。
- 回写口径只走合同管理本体受控写接口，写入摘要引用、风险结果引用、提取结果引用。
- 合同主档若发生分类、主状态或主文档变化，应触发本主线异步刷新搜索索引、
  语义候选和推荐结果。

### 9.2 与文档中心的挂接

- `OCR`、搜索索引、AI 应用上下文全部以 `document_asset_id` /
  `document_version_id` 取数。
- 本主线不保存私有文件副本，只保存结果引用、内容摘要引用和页级坐标。
- 当文档中心主版本切换时：
  - 原有 `OCR` / 搜索 / AI 结果不删除
  - 但标记为 `SUPERSEDED` 或失效候选
  - 由异步任务基于新版本重建派生结果

### 9.3 与 `Agent OS` 的挂接

- 本主线只通过统一任务接口与 `Agent OS` 通信。
- `Agent OS` 负责运行时、工具、模型 / `Provider` 选择、记忆、人工确认与审计。
- 本主线负责业务输入装配、结果消费、结果投影和回写治理。
- 两边通过稳定对象主键关联，不共享底层表。

### 9.4 挂接原则

- 不跨模块直写对方私有表。
- 不把搜索索引、`OCR` 文本、AI 结果反向提升为合同或文档真相。
- 允许保留读模型和结果快照，但必须可重建、可失效、可审计。

## 10. 缓存、锁、幂等与并发控制

### 10.1 缓存

- `Redis` 只缓存以下内容：
  - 搜索结果集短期快照
  - 语义候选集短期快照
  - 问答会话短期上下文摘要
  - 语言识别短期结果
  - 作业轮询状态热点数据
- 缓存失效后必须能从 `MySQL` 主表和正式来源重建。
- 缓存键不得成为正式对象主键，也不得成为唯一审计依据。

### 10.2 锁

- `OCR` 锁：`document_version_id + job_purpose`
- 搜索重建锁：`search_doc_ref + rebuild_type`
- AI 作业锁：`application_type + contract_id + normalized_scope_digest`
- 回写锁：`target_type + target_id`
- 锁只用于短时互斥和去重，正式状态仍以数据库记录判断。

### 10.3 幂等

- 作业创建接口统一要求 `idempotency_key`。
- 相同业务对象、相同输入范围、相同作业意图命中幂等时，返回既有作业。
- 如果相同 `idempotency_key` 对应不同请求体，直接判定为幂等冲突。

### 10.4 并发控制

- 表级更新统一使用 `version_no` 乐观锁。
- 回写前校验目标快照版本，避免旧结果覆盖新结果。
- 同一合同允许多个查询和问答会话并发存在，但同一“自动回写槽位”只允许一个写入者。
- 文档版本切换与结果回写冲突时，以文档中心最新有效版本为准，旧结果转入待重建或失效态。

## 11. 异步任务、补偿与恢复

### 11.1 异步任务分类

- `OCR` 作业
- 搜索索引构建 / 刷新
- AI 应用上下文准备
- AI 应用执行
- 结果回写
- 版本切换后的派生结果重建
- 失败作业重试与死信转人工

### 11.2 统一推进规则

- 任务正式状态落库，执行器可重启重领。
- 每次尝试都保留尝试次数、失败码、失败摘要和下一次调度时间。
- 达到最大重试次数后转入 `FAILED`，而是否需要人工复核由任务中心与审计链路承接，不额外扩展 `ia_ocr_job.job_status` 正式枚举。

### 11.3 典型补偿场景

- `OCR` 成功但搜索索引失败：保留 `OCR` 结果，单独补索引任务。
- AI 结果已生成但回写失败：保留结果对象，单独重试回写，不重新跑模型。
- 文档主版本切换后旧结果仍被引用：将旧结果标记为 `SUPERSEDED`，并补建新版本结果。
- 条款 / 模板版本变更后旧语义快照被查询：旧结果继续可查，但新任务必须使用新版本。

### 11.4 恢复边界

- 可恢复对象：作业、结果、回写记录、候选集、语言上下文。
- 不保证恢复的对象：短期缓存、短期锁、临时翻译缓存。
- 恢复原则：优先恢复正式状态一致性，其次恢复读模型与缓存命中率。

## 12. 审计、日志、指标与恢复边界

### 12.1 审计

- 关键审计动作至少包括：
  - `OCR` 作业创建、失败、重试、结果生效
  - 搜索查询受理、结果生成、越权裁剪
  - AI 应用任务创建、人工确认、结果生成、结果拒绝
  - 条款 / 模板语义引用建立
  - 结果回写、回写失败、回写冲突
  - 文档版本变更导致结果失效或重建
- 审计记录必须能追到：`contract_id`、`document_version_id`、`ai_application_job_id`、
  `agent_task_id`、`result_id`、`writeback_record_id`、`ranking_snapshot_id`、
  `quality_evaluation_id`、`guardrail_decision`。

### 12.2 日志

- 应用日志聚焦调度、失败原因、耗时、外部调用摘要、索引刷新摘要。
- 不在普通应用日志中落全文合同正文、全文 `OCR` 文本或完整问答内容。
- 大文本、证据、片段走对象引用或摘要字段，避免日志变成隐式数据副本。

### 12.3 指标

- 吞吐指标：作业受理数、执行数、完成数、失败数
- 时延指标：`OCR` 耗时、搜索查询耗时、AI 任务耗时、回写耗时
- 质量指标：`OCR` 置信度、字段提取置信度、风险人工驳回率、回写失败率
- 健康指标：重试堆积量、待人工确认量、结果重建积压量、缓存命中率

### 12.4 恢复边界

- 数据恢复以 `MySQL` 正式表为核心，搜索读模型和缓存按需重建。
- 文档中心和合同主档恢复优先级高于本主线派生结果恢复。
- 若 `Agent OS` 暂不可用，本主线允许暂停新增 AI 任务，但不影响既有合同主档、
  文档中心和已落库结果查询。
- 若搜索引擎不可用，允许降级为有限结构化查询或返回“结果暂不可用”，但不篡改业务真相。

## 13. 继续下沉到后续专项设计或实现的内容

- 搜索索引文档结构、重建策略与引擎适配细节： [search-index-and-rebuild-design.md](./special-designs/search-index-and-rebuild-design.md)
- `OCR` 引擎适配、版面解析、表格 / 印章识别细节： [ocr-engine-and-layout-analysis-design.md](./special-designs/ocr-engine-and-layout-analysis-design.md)
- AI 应用上下文装配模板、输出校验模板和人审策略细则： [ai-context-assembly-and-output-guardrails-design.md](./special-designs/ai-context-assembly-and-output-guardrails-design.md)
- 语义候选排序、候选淘汰规则和质量评估策略： [candidate-ranking-and-quality-evaluation-design.md](./special-designs/candidate-ranking-and-quality-evaluation-design.md)
- 条款 / 模板多语言内容治理流程与术语库维护流程： [multilingual-knowledge-governance-design.md](./special-designs/multilingual-knowledge-governance-design.md)
- 结果回写到合同侧各视图的字段级映射和冲突优先级： [result-writeback-and-conflict-resolution-design.md](./special-designs/result-writeback-and-conflict-resolution-design.md)
- 监控面板、告警阈值、恢复脚本和运维手册： [ops-monitoring-alert-and-recovery-design.md](./special-designs/ops-monitoring-alert-and-recovery-design.md)

## 14. 小结

本文将“检索 / `OCR` / AI 业务应用主线”下沉为一套受控派生结果域：

- 合同主档继续是业务真相源
- 文档中心继续是文件真相源
- `Agent OS` 继续是 AI 运行时底座
- 条款库继续是正式能力资源和 AI 重要底座
- 多语言继续是正式能力
- `OCR`、搜索、AI 应用结果继续保持派生结果定位

该口径与本主线既有 [`Architecture Design`](./architecture-design.md) 和
[`API Design`](./api-design.md) 保持一致，并为后续实现提供了稳定的内部模型、
表设计、状态、回写、并发、补偿与恢复基线。

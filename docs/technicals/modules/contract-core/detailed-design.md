# 合同管理本体子模块 Detailed Design

## 1. 文档说明

本文档是 `CMP` 合同管理本体子模块的第一份正式
`Detailed Design`。

本文只下沉合同管理本体内部实现层设计，用于把以下上游约束
收口为可落库、可编排、可恢复、可观测的内部实现方案：

### 1.1 输入

- 上游需求基线：
  [`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- 总平台架构：[`Architecture Design`](../../architecture-design.md)
- 总平台接口规范：[`API Design`](../../api-design.md)
- 总平台共享内部边界：[`Detailed Design`](../../detailed-design.md)
- 本模块架构：[`Architecture Design`](./architecture-design.md)
- 本模块接口规范：[`API Design`](./api-design.md)

### 1.2 输出

- 本文：[`Detailed Design`](./detailed-design.md)
- 为后续本模块实现、测试、专项设计提供内部实现基线

### 1.3 阅读边界

本文只回答以下问题：

- 合同管理本体子模块内部如何拆分与协作
- 合同主档、模板库、条款库、起草会话如何落到内部模型和物理表
- 台账视图、详情聚合、状态收口、跨模块挂接如何实现
- 多语言、缓存、锁、幂等、异步任务、补偿恢复如何在本模块内落点

本文不承担以下内容：

- 不复述一期需求范围、验收口径和建设目标
- 不重写总平台或子模块架构总览
- 不重写对外 API 资源清单、请求响应字段或错误码全集
- 不写实施排期、里程碑、负责人和任务拆分
- 不展开下游模块自身私有过程表和执行细节

## 2. 设计目标与约束落点

本模块的内部实现以“一个合同主链、一个合同真相源、多个受控投影”
为核心原则。

### 2.1 真相源与边界落点

- 合同主档是业务真相源，负责统一持有 `contract_id`、业务身份、
  生命周期主状态、关键摘要引用与主链路准入条件。
- 文档中心是文件真相源，合同管理本体只保存文件引用、文档摘要、
  有效版本引用和业务语义，不复制文件版本链。
- 流程引擎通过 `contract_id` 绑定合同，不拥有合同主档；本模块只维护
  审批入口条件、审批摘要和状态映射结果。
- 模板库 / 条款库是正式能力资源，既服务起草，也服务审查、比对和后续
  合同 AI grounding。
- 条款库是后续合同 AI 的重要底座，因此条款必须有稳定编码、版本、
  适用范围、多语言文本和风险标签，不能只做附件式材料堆放。
- 多语言是正式能力，不是备注字段；内部模型必须在主档展示文本、模板、
  条款、枚举文案、索引和 AI 输入输出入口中有明确落点。
- 不允许周边模块各自长出合同真相源；周边模块只能回写摘要、里程碑、
  引用键和受控事件。

### 2.2 一致性落点

- `cc_contract_master` 是写模型中心。
- `cc_contract_summary` 是跨模块复用摘要快照。
- `cc_contract_ledger_projection` 是列表查询和统计优先的读模型。
- `ContractDetailAggregate` 运行时聚合读取主档、摘要、文件摘要、审批摘要、
  签章 / 履约 / 变更 / 终止 / 归档摘要，不落成第二主档。
- 所有状态推进都先写主档，再更新摘要和投影；搜索索引和 AI 缓存属于后续
  异步消费结果。

### 2.3 MySQL 与平台约束落点

- 物理表按 `MySQL` 设计，但经 `DB abstraction layer` 访问。
- `Redis` 仅承担缓存、锁、幂等键、短期任务状态与去重，不承担正式合同状态。
- 异步协作用任务表 + 事件外箱模式实现，不以 `MQ` 为前提。
- 所有关键状态切换必须可审计、可重放、可恢复。

## 3. 模块内部拆分

### 3.1 内部模块清单

合同管理本体内部按“写模型、读模型、资源库、聚合编排、集成挂接、运行治理”
六类职责拆分。

| 内部模块 | 主要职责 | 直接持有对象 | 不直接持有对象 |
| --- | --- | --- | --- |
| `ContractMasterDomain` | 合同主档创建、编辑、生命周期准入、主状态推进 | 合同主档、主体、主状态、主引用 | 文件版本链、流程实例、签章过程 |
| `ContractSummaryDomain` | 统一摘要快照、跨模块消费面 | 合同摘要、待办摘要、风险摘要 | 周边模块私有过程明细 |
| `ContractLedgerProjectionDomain` | 台账读模型、排序筛选、统计友好字段 | 台账投影、排序字段、搜索辅助字段 | 合同正式编辑真相 |
| `TemplateClauseDomain` | 模板库、条款库、版本治理、起草输入装配 | 模板、条款、版本、绑定关系 | 合同实例生命周期 |
| `DraftingDomain` | 起草会话、草稿装配、正文候选生成、建稿确认 | 起草会话、候选内容、来源绑定 | 正式文件介质真相 |
| `LifecycleMappingDomain` | 生命周期主状态、周边状态映射、状态准入 | 状态映射规则、转换记录 | 周边模块内部状态机 |
| `ContractIntegrationFacade` | 文档中心、流程引擎、签章、履约、归档、搜索、AI 挂接 | 适配命令、回写命令、集成引用 | 外部系统业务真相 |
| `ContractOpsDomain` | 缓存、锁、幂等、异步任务、补偿、指标 | 锁键、幂等键、任务元数据、恢复策略 | 业务字段本身 |

### 3.2 内部调用原则

- 合同主链相关写操作统一经 `ContractMasterDomain` 进入。
- 模板、条款、起草会话不能直接写合同主状态，只能通过建稿确认或受控动作
  生成 / 更新合同主档。
- 周边模块回写合同时，不直接改台账投影，而是发给状态映射与摘要刷新器，
  由其统一更新主档、摘要和读模型。
- 详情聚合是读时组装，不跨表保存一份新的“详情主记录”。

### 3.3 事务边界

- 单合同主链写入默认以 `contract_id` 为事务边界。
- 同步事务只覆盖：主档、主体、摘要、状态流转、外箱事件。
- 文档摘要回填、流程发起、签章发起、索引刷新、AI 任务创建等均经事务提交后
  的异步任务承接。

## 4. 核心物理表设计

### 4.1 建表约定

- 表前缀统一为 `cc_`。
- 主键统一使用平台生成字符串主键。
- 所有正式主表默认包含：`created_at`、`created_by`、`updated_at`、
  `updated_by`、`is_deleted`、`version_no`。
- 枚举状态统一使用稳定编码，不把多语言文案直接作为状态值落库。
- `JSON` 字段只用于可控扩展块，不承载本应成为检索主键的核心字段。

### 4.2 `cc_contract_master`

- 用途：合同主档主表，是合同业务真相源。
- 关键主键：`contract_id`。
- 关键字段：
  - 身份字段：`contract_no`、`contract_name`、`contract_title_i18n_key`
  - 分类字段：`document_form`、`business_domain`、`contract_subcategory`、
    `contract_detail_type`
  - 来源字段：`source_type`、`source_system`、`source_business_id`
  - 归属字段：`owner_org_unit_id`、`owner_user_id`
  - 业务字段：`amount`、`currency`、`sign_date`、`effective_date`、
    `expire_date`
  - 主状态字段：`contract_status`、`current_stage`、`approval_mode`
  - 关键引用：`template_id`、`current_template_version_id`、
    `main_document_id`、`current_summary_id`
  - 过程快照键：`latest_approval_ref`、`latest_signature_ref`、
    `latest_performance_ref`、`latest_change_ref`、`latest_termination_ref`、
    `latest_archive_ref`
  - 数据治理字段：`classification_confirm_status`、`data_quality_status`、
    `last_state_changed_at`
- 关键索引 / 唯一约束：
  - `uk_contract_no(contract_no)`
  - `uk_source_ref(source_system, source_business_id)`
  - `idx_owner_status(owner_org_unit_id, owner_user_id, contract_status)`
  - `idx_category(document_form, business_domain, contract_subcategory,
    contract_detail_type)`
  - `idx_stage_changed(current_stage, last_state_changed_at)`
- 关联对象：合同主体、摘要、台账投影、模板版本、文档引用、状态流转记录。

### 4.3 `cc_contract_party`

- 用途：合同主体表，承载甲方、乙方、内部签约主体、担保方等参与方。
- 关键主键：`contract_party_id`。
- 关键字段：
  - `contract_id`
  - `party_role`：如 `PARTY_A`、`PARTY_B`、`GUARANTOR`、`INTERNAL_OWNER`
  - `party_name`
  - `party_name_i18n_key`
  - `party_type`、`party_code`
  - `country_code`、`contact_person`、`contact_phone`
  - `signing_entity_flag`、`display_order`
- 关键索引 / 唯一约束：
  - `idx_contract_role(contract_id, party_role)`
  - `uk_contract_party(contract_id, party_role, party_name, is_deleted)`
- 关联对象：`cc_contract_master`、详情聚合、签章上下文、归档摘要。

### 4.4 `cc_contract_summary`

- 用途：合同统一摘要快照，服务详情摘要、跨模块嵌入、消息卡片、待办区。
- 关键主键：`summary_id`。
- 关键字段：
  - `contract_id`
  - `summary_version`
  - `contract_status`、`current_stage`
  - `approval_summary_json`
  - `signature_summary_json`
  - `performance_summary_json`
  - `change_summary_json`
  - `termination_summary_json`
  - `archive_summary_json`
  - `risk_summary_json`
  - `pending_action_json`
  - `main_document_summary_json`
  - `timeline_anchor_at`
- 关键索引 / 唯一约束：
  - `uk_contract_summary(contract_id)`
  - `idx_stage(contract_status, current_stage)`
- 关联对象：`cc_contract_master`、详情聚合、搜索摘要、通知摘要。

### 4.5 `cc_contract_ledger_projection`

- 用途：合同台账读模型，面向列表筛选、排序、统计和大屏复用。
- 关键主键：`ledger_projection_id`。
- 关键字段：
  - `contract_id`
  - `contract_no`、`contract_name_display`
  - `document_form`、`business_domain`、`contract_subcategory`、
    `contract_detail_type`
  - `owner_org_unit_id`、`owner_org_unit_name_display`
  - `owner_user_id`、`owner_user_name_display`
  - `counterparty_display`
  - `amount`、`currency`
  - `contract_status`、`archive_status`、`signature_status`
  - `risk_level`、`pending_action_count`
  - `signed_date`、`effective_date`、`expire_date`
  - `search_text`
  - `locale_code`
  - `projection_updated_at`
- 关键索引 / 唯一约束：
  - `uk_contract_locale(contract_id, locale_code)`
  - `idx_status_owner(contract_status, owner_org_unit_id, owner_user_id)`
  - `idx_signed_date(signed_date, contract_status)`
  - `idx_amount(amount, currency)`
  - `idx_projection_updated(projection_updated_at)`
- 关联对象：合同台账、统计报表、搜索预热、电视端列表。

### 4.6 `cc_template`

- 用途：模板资源主表，承载模板稳定身份与适用范围。
- 关键主键：`template_id`。
- 关键字段：
  - `template_code`、`template_name`
  - `template_name_i18n_key`
  - `template_type`
  - `document_form`、`business_domain`、`contract_subcategory`、
    `contract_detail_type`
  - `template_status`
  - `default_locale`
  - `current_version_id`
  - `owner_org_unit_id`
  - `applicable_scope_json`
  - `ai_grounding_enabled`
- 关键索引 / 唯一约束：
  - `uk_template_code(template_code)`
  - `idx_template_category(document_form, business_domain, contract_subcategory,
    contract_detail_type, template_status)`
- 关联对象：模板版本、条款绑定、起草会话、合同主档来源字段。

### 4.7 `cc_template_version`

- 用途：模板版本表，承载模板正文骨架、版本状态和发布信息。
- 关键主键：`template_version_id`。
- 关键字段：
  - `template_id`
  - `version_no`、`version_status`
  - `default_locale`
  - `main_document_id`
  - `structure_schema_json`
  - `variable_schema_json`
  - `clause_snapshot_json`
  - `published_at`
  - `published_by`
- 关键索引 / 唯一约束：
  - `uk_template_version(template_id, version_no)`
  - `idx_template_status(template_id, version_status)`
- 关联对象：模板主表、条款绑定快照、起草会话、合同主档来源模板版本。

### 4.8 `cc_clause`

- 用途：条款资源主表，承载条款稳定身份、分类、风险和适用范围。
- 关键主键：`clause_id`。
- 关键字段：
  - `clause_code`、`clause_name`
  - `clause_name_i18n_key`
  - `clause_type`、`clause_category`
  - `risk_level`
  - `default_locale`
  - `current_version_id`
  - `applicable_scope_json`
  - `ai_grounding_weight`
  - `clause_status`
- 关键索引 / 唯一约束：
  - `uk_clause_code(clause_code)`
  - `idx_clause_category(clause_type, clause_category, clause_status)`
  - `idx_clause_risk(risk_level, clause_status)`
- 关联对象：条款版本、模板版本、起草会话、AI grounding。

### 4.9 `cc_clause_version`

- 用途：条款版本表，承载条款内容、适用版本和语义投影。
- 关键主键：`clause_version_id`。
- 关键字段：
  - `clause_id`
  - `version_no`、`version_status`
  - `default_locale`
  - `content_body`
  - `content_structure_json`
  - `semantic_tags_json`：正式语义画像的快照或消费投影，不替代
    `cc_clause_semantic_profile`
  - `fallback_keywords_json`
  - `published_at`
- 关键索引 / 唯一约束：
  - `uk_clause_version(clause_id, version_no)`
  - `idx_clause_version_status(clause_id, version_status)`
- 关联对象：条款主表、条款语义画像、模板版本快照、起草候选内容、AI 语义检索。

### 4.9.1 `cc_clause_semantic_profile`

- 用途：条款语义画像表，承接 `ClauseSemanticProfile` 的正式持久化落点，保存条款版本的正式语义标签、标签来源、适用范围和消费约束。
- 关键主键：`clause_semantic_profile_id`。
- 关键字段：
  - `contract_id`：语义画像从合同实例或历史确认结果沉淀时的合同引用；纯条款库治理画像可为空
  - `drafting_session_id`：语义画像来自起草会话候选晋升时的会话引用；非会话来源可为空
  - `template_version_id`：语义画像继承或约束于模板版本时的模板版本引用；非模板来源可为空
  - `clause_id`
  - `clause_version_id`
  - `semantic_profile_version`
  - `profile_status`：`DRAFT`、`PENDING_REVIEW`、`ACTIVE`、`SUPERSEDED`、`DISABLED`、`REJECTED`
  - `semantic_tags_json`：正式标签编码、层级、权重和消费约束的当前快照
  - `applicable_scope_snapshot_json`：合同分类、主体关系、生命周期阶段和模板槽位适用范围快照
  - `source_type`：`AUTHORITATIVE_RULE`、`RESOURCE_BINDING`、`HUMAN_REVIEWED_INFERENCE`、`MACHINE_CANDIDATE`
  - `source_snapshot_json`：来源规则、模板绑定、历史决策、文本分析或 AI 候选观察的证据快照
  - `confirmed_flag`
  - `confirmed_by`
  - `confirmed_at`
  - `confirmation_action`：`APPROVE`、`REJECT`、`PROMOTE`、`DISABLE`、`OVERRIDE`
  - `confirmation_reason`
  - `idempotency_key`
  - 审计字段：`created_at`、`created_by`、`updated_at`、`updated_by`、`is_deleted`、`version_no`
- 关键索引 / 唯一约束：
  - `uk_clause_profile_version(clause_version_id, semantic_profile_version)`
  - `uk_clause_profile_idempotency(idempotency_key)`
  - `idx_clause_profile_active(clause_version_id, profile_status, is_deleted)`
  - `idx_profile_context(contract_id, drafting_session_id, template_version_id)`
  - `idx_profile_confirmed(confirmed_flag, confirmed_at)`
- 关联对象：条款版本、模板版本、起草会话、合同主档、条款推荐候选、条款选择决策。

### 4.9.2 `cc_clause_recommendation_candidate`

- 用途：条款推荐候选表，承接 `ClauseRecommendationCandidate` 的正式持久化落点，保存某次起草、套版、补齐或审查场景下的候选条款、推荐理由、来源快照、排序结果和候选状态。
- 关键主键：`clause_recommendation_candidate_id`。
- 关键字段：
  - `contract_id`
  - `drafting_session_id`
  - `template_version_id`
  - `template_clause_binding_id`
  - `slot_code`
  - `clause_id`
  - `clause_version_id`
  - `semantic_profile_id`
  - `recommendation_batch_id`
  - `candidate_type`：`FILL_REQUIRED`、`REPLACE_OPTION`、`RISK_ALERT`、`KNOWLEDGE_REFERENCE`
  - `candidate_status`：`PENDING_CONFIRM`、`ADOPTED`、`IGNORED`、`REJECTED`、`EXPIRED`、`RECALCULATED`
  - `risk_level`
  - `rank_no`
  - `score_snapshot`
  - `reason_snapshot_json`：正式标签、上下文缺口、模板槽位、风险提示和差异说明快照
  - `source_snapshot_json`：合同分类、主体结构、模板版本、条款语义画像、正文观察和 AI 任务引用快照
  - `requires_manual_confirmation`
  - `confirmed_flag`
  - `confirmed_by`
  - `confirmed_at`
  - `confirmation_action`：`ADOPT`、`IGNORE`、`REJECT`、`REPLACE`、`EXPIRE`
  - `confirmation_reason`
  - `idempotency_key`
  - 审计字段：`created_at`、`created_by`、`updated_at`、`updated_by`、`is_deleted`、`version_no`
- 关键索引 / 唯一约束：
  - `uk_candidate_batch_slot_clause(recommendation_batch_id, contract_id, drafting_session_id, slot_code, clause_version_id)`
  - `uk_candidate_idempotency(idempotency_key)`
  - `idx_candidate_session_status(drafting_session_id, candidate_status, rank_no)`
  - `idx_candidate_contract_status(contract_id, candidate_status, updated_at)`
  - `idx_candidate_template_slot(template_version_id, slot_code, candidate_type)`
  - `idx_candidate_clause_profile(clause_version_id, semantic_profile_id)`
- 关联对象：合同主档、起草会话、模板版本、模板条款绑定、条款版本、条款语义画像、条款选择决策。

### 4.9.3 `cc_clause_selection_decision`

- 用途：条款选择决策表，承接 `ClauseSelectionDecision` 的正式持久化落点，保存人工确认后的选用、替换、忽略、驳回和覆盖事实；它是正式人工决策对象，不是起草会话内的临时视图块。
- 关键主键：`clause_selection_decision_id`。
- 关键字段：
  - `contract_id`
  - `drafting_session_id`
  - `template_version_id`
  - `template_clause_binding_id`
  - `slot_code`
  - `clause_id`
  - `clause_version_id`
  - `semantic_profile_id`
  - `clause_recommendation_candidate_id`
  - `decision_status`：`EFFECTIVE`、`SUPERSEDED`、`REVOKED`
  - `decision_action`：`ADOPT`、`IGNORE`、`REJECT`、`REPLACE`、`OVERRIDE`
  - `decision_source`：`MANUAL_CONFIRM`、`LEGAL_REVIEW`、`TEMPLATE_GOVERNANCE`、`DRAFT_CONFIRM`
  - `decision_snapshot_json`：决策时的条款文本摘要、语义画像、推荐理由、合同上下文和模板槽位快照
  - `source_snapshot_json`：推荐候选、审查动作、模板治理动作或起草确认动作来源快照
  - `confirmed_flag`
  - `confirmed_by`
  - `confirmed_at`
  - `confirmation_reason`
  - `supersedes_decision_id`
  - `idempotency_key`
  - 审计字段：`created_at`、`created_by`、`updated_at`、`updated_by`、`is_deleted`、`version_no`
- 关键索引 / 唯一约束：
  - `uk_decision_candidate_action(clause_recommendation_candidate_id, decision_action, is_deleted)`
  - `uk_decision_context_slot(contract_id, drafting_session_id, template_version_id, slot_code, clause_version_id, decision_action, is_deleted)`
  - `uk_decision_idempotency(idempotency_key)`
  - `idx_decision_contract_status(contract_id, decision_status, confirmed_at)`
  - `idx_decision_session_slot(drafting_session_id, slot_code, decision_status)`
  - `idx_decision_clause(clause_version_id, decision_action)`
- 关联对象：合同主档、起草会话、模板版本、模板条款绑定、条款版本、条款语义画像、推荐候选、审计日志。

### 4.10 `cc_drafting_session`

- 用途：起草会话表，承载从模板 / 空白 / 导入到建稿确认之间的内部过程状态。
- 关键主键：`drafting_session_id`。
- 关键字段：
  - `contract_id`
  - `draft_mode`
  - `draft_status`
  - `template_id`、`template_version_id`
  - `selected_clause_version_ids_json`
  - `source_document_id`
  - `working_document_id`
  - `default_locale`
  - `draft_payload_json`
  - `suggestion_snapshot_json`
  - `last_autosave_at`
  - `confirmed_at`
- 关键索引 / 唯一约束：
  - `uk_contract_active_session(contract_id, draft_status, is_deleted)`
  - `idx_draft_owner(created_by, draft_status)`
  - `idx_template_session(template_id, template_version_id)`
- 关联对象：合同主档、模板版本、条款版本、文档中心工作稿、AI 草拟任务。

### 4.11 `cc_contract_state_transition`

- 用途：合同状态流转表，记录主状态收口、映射来源和触发动作。
- 关键主键：`state_transition_id`。
- 关键字段：
  - `contract_id`
  - `from_contract_status`、`to_contract_status`
  - `from_stage`、`to_stage`
  - `trigger_type`
  - `trigger_source`：`MANUAL`、`OA`、`CMP_WORKFLOW`、`SIGNATURE`、
    `PERFORMANCE`、`CHANGE`、`TERMINATION`、`ARCHIVE`、`RECOVERY`
  - `trigger_ref_id`
  - `mapping_rule_code`
  - `transition_result`
  - `occurred_at`
  - `reason_code`
  - `reason_message`
- 关键索引 / 唯一约束：
  - `idx_contract_occurred(contract_id, occurred_at)`
  - `idx_trigger(trigger_source, trigger_ref_id)`
  - `idx_to_status(to_contract_status, occurred_at)`
- 关联对象：合同主档、摘要刷新、审计日志、恢复作业。

### 4.12 `cc_contract_document_link`

- 用途：合同文档“双锚点绑定投影”表；以稳定文件锚点 + 当前业务生效版本锚点承接合同对文档中心真相的业务解释，不复制文件版本链。
- 关键主键：`contract_document_link_id`。
- 关键字段：
  - `contract_id`
  - `document_role`：`WORKING_DRAFT`、`TEMPLATE_BODY`、`MAIN_BODY`、
    `SIGNED_COPY`、`CHANGE_BODY`、`ARCHIVE_COPY`
  - `document_id`：稳定文件锚点
  - `effective_document_version_id`：当前业务生效版本锚点
  - `binding_status`：`DRAFT_ONLY`、`ACTIVE`、`SUPERSEDED`、
    `LOCKED_BY_SIGNATURE`、`LOCKED_BY_ARCHIVE`、`REVOKED`
  - `is_current_effective`：当前角色下是否为默认暴露绑定
  - `origin_type`：`DRAFT_CONFIRM`、`TEMPLATE_APPLY`、`APPROVAL_CONFIRM`、
    `SIGNATURE_COMPLETE`、`CHANGE_COMPLETE`、`ARCHIVE_COMPLETE`、`RECOVERY`
  - `origin_ref_id`
  - `effective_from_stage`
  - `effective_from_event`
  - `replaces_contract_document_link_id`：指向被替代的旧绑定，用于承接当前版本切换链
  - `document_summary_json`
- 角色语义补充：`MAIN_BODY` 是合同当前正式正文角色，始终回答“当前正文是什么”；`CHANGE_BODY` 是某次变更动作产出的正文角色，用于承接替代型变更候选正文或补充协议正文。
- 变更切换规则：变更完成前，`MAIN_BODY` 保持当前正式正文，`CHANGE_BODY` 只表示变更产物；变更完成后，如业务规则认定该次变更替代当前正文，则新增新的 `MAIN_BODY` 绑定并把旧 `MAIN_BODY` 转为 `SUPERSEDED`，原 `CHANGE_BODY` 继续保留“该次变更产物”的历史语义；如仅为补充协议，则只保留 `CHANGE_BODY`，不替代 `MAIN_BODY`。
- 关键索引 / 唯一约束：
  - `uk_contract_doc_role_version(contract_id, document_role,
    effective_document_version_id)`
  - `idx_doc_role_current(contract_id, document_role, is_current_effective)`
  - `idx_doc_binding_origin(origin_type, origin_ref_id)`
  - `idx_doc_replaces(replaces_contract_document_link_id)`
- 关联对象：合同主档、详情聚合、签章、变更、归档、文档中心挂接。

### 4.13 `cc_template_clause_binding`

- 用途：模板与条款的绑定表，支撑模板装配、版本快照和 AI grounding 来源回溯。
- 关键主键：`template_clause_binding_id`。
- 关键字段：
  - `template_version_id`
  - `clause_id`
  - `clause_version_id`
  - `binding_type`
  - `required_flag`
  - `display_order`
  - `slot_code`
  - `binding_config_json`
- 关键索引 / 唯一约束：
  - `uk_template_clause_slot(template_version_id, slot_code, clause_version_id)`
  - `idx_clause_binding(clause_id, clause_version_id)`
- 关联对象：模板版本、条款版本、起草会话、条款推荐和对比。

### 4.14 `cc_contract_i18n_text`

- 用途：合同管理本体多语言文本表，承载合同、模板、条款的多语展示文本。
- 关键主键：`contract_i18n_text_id`。
- 关键字段：
  - `owner_type`：`CONTRACT`、`TEMPLATE`、`TEMPLATE_VERSION`、`CLAUSE`、
    `CLAUSE_VERSION`
  - `owner_id`
  - `field_name`
  - `locale_code`
  - `text_value`
  - `text_hash`
  - `translation_status`
  - `source_locale`
- 关键索引 / 唯一约束：
  - `uk_owner_field_locale(owner_type, owner_id, field_name, locale_code)`
  - `idx_locale_owner(locale_code, owner_type, owner_id)`
- 关联对象：合同主档显示文本、模板 / 条款多语言内容、台账投影、搜索、AI。

### 4.15 `cc_contract_integration_link`

- 用途：合同与周边模块 / 外围实例之间的稳定引用表。
- 关键主键：`contract_integration_link_id`。
- 关键字段：
  - `contract_id`
  - `target_system`：`OA`、`CMP_WORKFLOW`、`DOC_CENTER`、`SIGNATURE`、
    `PERFORMANCE`、`CHANGE`、`TERMINATION`、`ARCHIVE`、`SEARCH`、`AI`
  - `target_resource_type`
  - `target_resource_id`
  - `link_status`
  - `last_synced_at`
  - `ext_payload_json`
- 关键索引 / 唯一约束：
  - `uk_contract_target(contract_id, target_system, target_resource_type,
    target_resource_id)`
  - `idx_target_lookup(target_system, target_resource_id)`
- 关联对象：流程实例、签章申请、履约计划、归档记录、AI 作业、搜索文档。

## 5. 合同主档 / 台账视图 / 详情聚合内部模型

### 5.1 `ContractMaster`

`ContractMaster` 是合同管理本体最核心的写模型，内部职责如下：

- 持有一级业务身份与分类主链
- 统一持有生命周期主状态与阶段
- 维护文档、模板、流程、签章、履约、归档等稳定引用
- 对外只暴露受控更新动作，不允许任意字段散写

建议内部结构按三层组织：

- `IdentityBlock`：`contract_id`、`contract_no`、来源、归属、分类
- `BusinessBlock`：名称、主体、金额、日期、主责任组织、关键属性
- `ControlBlock`：状态、摘要引用、外部引用、数据质量、审计标记

### 5.2 `ContractSummaryModel`

`ContractSummaryModel` 是统一消费面，不是第二主档。

内部字段重点：

- 主身份摘要：编号、名称、分类、主体展示
- 阶段摘要：`current_stage`、`contract_status`
- 流程摘要：审批路径、当前处理节点、待办人、超时标记
- 文档摘要：主正文、签章稿、归档稿引用和状态
- 风险摘要：风险等级、异常数、关键预警
- 行动摘要：当前允许动作、阻塞原因、下一步建议

生成策略：

- 主档变更后同步更新基本摘要字段
- 周边模块回写后增量刷新对应摘要块
- 搜索、消息、工作台优先消费摘要，不直接跨多个模块实时拼详情

### 5.3 `ContractLedgerViewModel`

`ContractLedgerViewModel` 是面向查询优化的读模型。

设计要点：

- 按 `contract_id + locale_code` 形成一条投影记录，支持多语言展示
- 将高频筛选字段扁平化，避免台账查询时频繁跨模块组装
- 保留 `search_text`、`counterparty_display`、`risk_level`、`pending_action_count`
  等列表友好字段
- 只支持刷新，不支持直接业务编辑

刷新触发：

- 合同主档创建 / 更新
- 状态映射完成
- 主体变化
- 多语言文本变化
- 摘要变化影响列表展示时

### 5.4 `ContractDetailAggregate`

`ContractDetailAggregate` 是读时聚合模型，按 `contract_id` 组织以下块：

- `contract_master`
- `party_list`
- `document_summary`
- `approval_summary`
- `signature_summary`
- `performance_summary`
- `change_summary`
- `termination_summary`
- `archive_summary`
- `timeline_list`
- `action_entry_list`
- `ai_panel_summary`

下游专项设计中如使用 `master_block`、`document_block`、`lifecycle_block` 等 block 命名，只表示对以上既有对象 / 摘要的语义分组与归并解释，不表示再定义一套并行聚合对象模型。

聚合规则：

- 主档、主体、摘要从本模块本地表读取
- 文件摘要从 `cc_contract_document_link` 和文档中心摘要接口读取
- 流程、签章、履约、归档等只读取各模块受控摘要，不拉取其全量过程表
- 详情聚合可以做短期缓存，但缓存失效后必须能完全由正式源重建

## 6. 模板库 / 条款库 / 起草会话内部模型

### 6.1 模板库内部模型

模板库采用“资源主表 + 版本表 + 条款绑定”的三层模型。

- `TemplateResource`：模板稳定身份、分类主链、启停状态、默认语言
- `TemplateVersion`：模板正文骨架、变量 schema、发布状态、正文文档引用
- `TemplateClauseBinding`：模板版本与条款版本的绑定快照

实现原则：

- 模板启用以版本为单位，不直接覆盖历史版本
- 合同创建时记录来源 `template_id + template_version_id`，后续模板升级不反向
  改写已生成合同
- 模板正文文件仍由文档中心持有；模板版本只保存稳定引用和结构 schema

### 6.2 条款库内部模型

条款库采用“资源主表 + 版本表 + 正式语义画像”的模型。

- `ClauseResource`：稳定编码、分类、风险级别、默认语言、适用范围
- `ClauseVersion`：条款内容、结构化片段、语义投影、关键词、发布状态
- `ClauseSemanticProfile`：条款版本语义治理的正式对象；`semantic_tags_json`、
  `fallback_keywords_json` 与适用范围只是在条款版本模型上的快照或消费投影，
  不替代 `cc_clause_semantic_profile` 这类正式语义治理对象；AI grounding
  也只是其中一个消费投影

实现原则：

- 条款内容变化必须走版本化，不能覆盖历史内容
- 模板绑定和起草引用使用条款版本，而不是只引用条款主键
- 条款版本要支持“正式启用”“已废弃但可追溯”“草稿中”三类治理状态

### 6.3 起草会话内部模型

起草会话是内部过程对象，对外仍围绕 `contract_id` 组织。

内部结构建议如下：

- `DraftSourceBlock`：草拟模式、来源模板版本、来源条款版本、导入源文档
- `DraftWorkingBlock`：工作稿文档引用、变量填充、候选条款组合、自动保存快照
- `DraftDecisionBlock`：起草会话内的工作态 / 视图块，用于承接人工选用记录、AI 建议记录、冲突标记与待确认结果

这里需要明确分层：`DraftDecisionBlock` 只服务于 `drafting_session` 内的起草过程，不是正式人工决策对象。人工一旦完成选用、替换、忽略、驳回或覆盖确认，正式结果应以 `ClauseSelectionDecision` 落入 `contract-core` 的人工决策层；前者负责会话内组织与展示，后者负责围绕 `contract_id` 承接可审计、可回放、可复用的正式决策事实，并允许关联来源 `drafting_session_id`、推荐候选和条款槽位。

状态建议收口：

- `INIT`
- `COLLECTING_INPUT`
- `GENERATING_CONTENT`
- `EDITING`
- `READY_TO_CONFIRM`
- `CONFIRMED`
- `ABANDONED`

约束：

- 同一合同同一时刻只允许一个活动起草会话
- 起草会话可反复自动保存，但只有 `CONFIRMED` 后才能推动主档进入正式下一步
- 起草工作稿仍由文档中心保存，本模块只管理其业务语义和确认状态

## 7. 生命周期状态与状态映射内部模型

### 7.1 主状态收口模型

合同主档只维护统一主状态和阶段，不吸收周边模块的细粒度过程状态。

建议主状态编码：

- `DRAFTING`
- `UNDER_REVIEW`
- `APPROVING`
- `APPROVED_PENDING_SIGNATURE`
- `SIGNING`
- `EFFECTIVE`
- `PERFORMING`
- `CHANGING`
- `TERMINATING`
- `TERMINATED`
- `ARCHIVING`
- `ARCHIVED`
- `VOIDED`

建议阶段编码：

- `DRAFT_STAGE`
- `APPROVAL_STAGE`
- `SIGNATURE_STAGE`
- `EFFECTIVE_STAGE`
- `PERFORMANCE_STAGE`
- `CHANGE_STAGE`
- `TERMINATION_STAGE`
- `ARCHIVE_STAGE`

### 7.2 周边状态映射模型

状态映射由 `LifecycleMappingDomain` 统一维护，输入包括：

- 流程引擎审批状态
- `OA` 回写状态
- 签章结果状态
- 履约节点摘要状态
- 变更、终止、归档模块结果状态

映射规则示例：

- `OA_APPROVING` / `CMP_FLOW_RUNNING` -> `APPROVING`
- `OA_APPROVED` / `CMP_FLOW_APPROVED` -> `APPROVED_PENDING_SIGNATURE`
- `SIGN_TASK_RUNNING` -> `SIGNING`
- `SIGN_COMPLETED + EFFECTIVE_CONFIRMED` -> `EFFECTIVE`
- `PERFORMANCE_ACTIVE` -> `PERFORMING`
- `CHANGE_RUNNING` -> `CHANGING`
- `TERMINATION_RUNNING` -> `TERMINATING`
- `TERMINATION_DONE` -> `TERMINATED`
- `ARCHIVE_RUNNING` -> `ARCHIVING`
- `ARCHIVE_DONE` -> `ARCHIVED`

### 7.3 状态推进约束

- 同一时刻只有一个合同主状态为当前有效状态
- 所有状态推进都必须写入 `cc_contract_state_transition`
- 回写事件必须携带 `trigger_source`、`trigger_ref_id` 和映射规则编码
- 状态不能直接从周边模块“硬覆盖”，必须通过映射器校验当前前置条件
- 遇到乱序回调时，按“状态单调性 + 事件时间 + 幂等键”判定是否接受

## 8. 与周边模块的内部挂接设计

### 8.1 与文档中心的挂接

- 本模块只保存 `document_id + effective_document_version_id` 双锚点、
  `document_role`、`binding_status`、来源阶段 / 事件、替代链和摘要快照
- 创建合同或起草会话时，先生成业务对象，再调用文档中心建立工作稿 / 正文引用
- 主正文切换时新增新的 `cc_contract_document_link` 绑定记录，并把旧绑定转为
  `SUPERSEDED` 或锁定态；不原地覆盖历史绑定，也不复制文件元数据真相
- 文档中心回写加密状态、预览状态、版本切换结果后，只更新合同侧摘要字段或受控绑定投影，不把文件内部版本链写回合同侧

### 8.2 与流程引擎的挂接

- 流程引擎通过 `contract_id` 绑定合同，不拥有合同主档
- 合同侧保存 `approval_mode`、最新流程引用键和审批摘要，不保存流程图和任务明细
- 发起审批时创建 `cc_contract_integration_link`，记录 `OA` 或 `CMP_WORKFLOW`
  的实例标识；该表是 `latest_approval_ref` 解引用、补写、重放、恢复时的正式绑定锚点
- 审批回调统一进入状态映射器；在 `cc_contract_integration_link` 绑定可解引用后，按“先写主档，再写状态流转，再更新摘要与投影”收口，禁止直接改台账投影或绕过主档状态

### 8.3 与签章模块的挂接

- 合同侧发起签章申请并提供合同摘要、签署主体、正文引用
- 签章模块返回签章申请号、结果状态、签署完成时间、签章稿文档引用
- 本模块只回收 `signature_summary`、签章里程碑和签章稿引用，不接收签章步骤表

### 8.4 与履约模块的挂接

- 合同侧在进入生效阶段后开放履约入口
- 履约模块按 `contract_id` 建立计划与节点过程
- 回写仅包括履约阶段摘要、风险等级、关键节点里程碑、异常计数

### 8.5 与变更模块的挂接

- 变更发起前校验合同是否允许进入变更阶段
- 变更完成后更新主档当前有效版本摘要、变更摘要和相关文档引用
- 变更模块的审批明细和补充协议过程仍由其自身治理

### 8.6 与终止模块的挂接

- 终止入口只负责合同侧准入校验和发起记录
- 终止模块回写终止结果、终止时间、终止原因摘要和后续处理状态
- 合同主档保留完整历史身份，不因终止而删除或覆盖历史状态轨迹

### 8.7 与归档模块的挂接

- 归档入口根据主档状态、文档完备度和审批 / 签章结果做前置校验
- 归档模块回写归档记录号、归档状态、归档包文档引用、借阅能力摘要
- 合同侧不保存档案借阅过程，只保存归档摘要和归档引用键

### 8.8 与搜索模块的挂接

- 搜索消费 `cc_contract_summary`、`cc_contract_ledger_projection`、
  文档中心受控文本和多语言文本
- 搜索索引不回写主档，只通过异步投影刷新
- 本模块为搜索提供稳定跳转目标、分类字段、标签、主体展示和多语文本源

### 8.9 与 AI 的挂接

- 本模块向 AI 暴露合同主档、模板版本、条款版本、文档摘要、详情聚合上下文
- AI 结果作为建议、比对、风险提示、推荐条款、问答 grounding 结果存在
- AI 不直接更新合同主档；如要采纳，必须转化为显式业务动作并写审计
- 条款库是 AI 的重要底座，AI 只读取正式启用条款版本或显式允许的草稿版本

## 9. 多语言在内部模型中的落点

### 9.1 多语言建模原则

- 语义编码与展示文本分离
- 主档、模板、条款的多语文本统一进入 `cc_contract_i18n_text`
- 状态编码、分类编码、风险等级编码使用稳定值，不用翻译文本作主键
- 台账投影按语言生成显示列，详情聚合按请求语言装配展示文本

### 9.2 合同主档中的落点

- `contract_name` 保存主业务值
- `contract_title_i18n_key` 指向多语言文本键
- 参与方名称、组织显示名、摘要展示文案按语言读取并回填投影

### 9.3 模板与条款中的落点

- 模板 / 条款主表保存 `default_locale`
- 各版本正文保存默认语种正文
- 其他语种正文或标题经 `cc_contract_i18n_text` 关联到
  `TEMPLATE_VERSION` / `CLAUSE_VERSION`
- 起草会话记录当前工作语言，确保模板、条款、AI 建议和工作稿语言一致

### 9.4 搜索与 AI 中的落点

- `cc_contract_ledger_projection` 以 `contract_id + locale_code` 建立多语投影
- 搜索索引按语种分词或字段路由建立多语字段
- AI 调用时携带 `locale_code`、术语域和条款语种，避免跨语种误用条款文本

## 10. 缓存、锁、幂等与并发控制

### 10.1 缓存策略

- 缓存对象：
  - 合同摘要
  - 详情聚合短期结果
  - 模板启用版本
  - 条款启用版本与语义标签
  - 状态映射规则
- 缓存原则：
  - 主档写入成功后主动失效相关缓存
  - 缓存只加速读取，不承担恢复责任
  - 详情聚合缓存使用短 TTL，摘要缓存可采用事件驱动失效

建议缓存键：

- `contract:summary:{contract_id}`
- `contract:detail:{contract_id}:{locale}`
- `template:active:{template_id}`
- `clause:active:{clause_id}`
- `contract:state-map:{rule_code}`

### 10.2 锁策略

- 单合同写操作使用 `contract:lock:{contract_id}` 分布式锁
- 模板启用使用 `template:lock:{template_id}`
- 条款启用使用 `clause:lock:{clause_id}`
- 起草确认使用 `draft:confirm:{drafting_session_id}`

加锁原则：

- 只在跨表写入或状态推进关键段加锁
- 锁超时后允许重试，但必须结合版本号校验
- 锁失效时以数据库 `version_no` 乐观并发控制兜底

### 10.3 幂等策略

- 创建合同、发起审批、发起签章、发起归档、起草确认、外围回调处理均要求幂等
- 幂等键来源：
  - 前端写接口：`Idempotency-Key`
  - 外围回调：`source_system + source_business_id + event_type + event_id`
  - 异步任务：`job_type + owner_id + business_hash`
- 首次执行结果写入幂等记录；重复请求直接返回首个稳定结果

### 10.4 并发控制

- 主档、模板、条款、起草会话均使用 `version_no` 乐观锁
- 详情聚合不做强一致锁，只接受最终一致的摘要拼装
- 状态推进时执行“当前状态 + 版本号 + 触发来源”三重校验

## 11. 异步任务、补偿与恢复

### 11.1 异步任务范围

以下场景采用异步任务：

- 工作稿生成、模板套版、条款推荐
- 文档摘要刷新、主正文切换后的摘要回填
- 审批发起后的外部实例绑定与结果轮询
- 搜索索引刷新
- AI 风险识别、条款比对、问答预处理
- 台账多语投影重建
- 归档前资料完整性预检

### 11.2 任务设计原则

- 正式任务状态落平台统一任务中心，本模块只保存业务引用和重建入口
- 每个任务都必须能根据 `contract_id` 或资源主键重新计算目标结果
- 执行结果分为：成功、可重试失败、需人工介入失败

### 11.3 补偿策略

- 文档中心写入成功但合同侧摘要未刷新：补偿任务按 `document_id` 反查
  `contract_id`，重新生成文档摘要并更新 `cc_contract_summary`
- 审批实例发起成功但合同侧未绑定实例号：按幂等键查询流程实例，补写
  `cc_contract_integration_link`
- 签章完成回调到达但状态映射失败：保留回调事件和失败原因，人工修复后
  允许重放映射任务
- 搜索投影失败：不影响合同真相；后续按 `projection_updated_at` 补刷

### 11.4 恢复边界

- 恢复前先校验 `latest_approval_ref` 与 `cc_contract_integration_link` 的绑定锚点可解引用；正式恢复顺序固定为：主档 -> 状态流转 -> 摘要 -> 台账投影 -> 搜索 / AI 缓存
- 任一增强层损坏时，必须能仅依赖 `MySQL` 主表与周边模块正式摘要接口恢复
- 不允许依赖缓存或搜索索引恢复合同主状态

## 12. 审计、日志、指标与恢复边界

### 12.1 审计落点

以下动作必须写审计事件：

- 合同创建、编辑、删除标记、撤回
- 模板启用 / 停用、条款启用 / 停用
- 起草确认、审批发起、签章发起、归档发起
- 生命周期主状态变更
- 解密下载授权引用变更
- AI 建议采纳和人工覆盖

审计最少包含：操作者、对象类型、对象主键、动作类型、前后状态、
触发来源、结果、追踪号。

### 12.2 日志落点

- 业务日志：记录主链关键动作、状态映射命中、摘要刷新结果
- 集成日志：记录与文档中心、流程引擎、签章、AI 的请求响应摘要
- 异常日志：记录补偿触发、恢复失败、乱序回调、幂等冲突

### 12.3 指标落点

建议最少建设以下指标：

- 合同创建成功率、起草确认耗时
- 主状态切换成功率、状态映射失败数
- 摘要刷新延迟、台账投影刷新延迟
- 模板启用版本命中率、条款推荐命中率
- 详情聚合缓存命中率
- AI 任务成功率、人工采纳率

### 12.4 恢复边界

- 本模块负责恢复合同主档、摘要、投影和状态流转记录的一致性
- 文档中心文件版本链、流程实例任务链、签章内部步骤、履约明细、归档借阅记录
  不在本模块内恢复
- 本模块只能通过正式引用键和正式摘要接口与周边模块重新对齐

## 13. 继续下沉到后续专项设计或实现的内容

以下内容应继续下沉到专项设计或实现阶段，不在本文展开为更细 DDL 或代码级方案：

- 文档中心工作稿、模板正文、签章稿、变更正文与归档稿的文件版本链细节，以及 `cc_contract_document_link` 双锚点绑定投影规则。中文说明： [contract-document-version-binding-design.md](./special-designs/contract-document-version-binding-design.md)
- 合同主档与流程实例 / 审批桥接绑定、审批摘要回写与状态推进边界。中文说明： [contract-approval-bridge-design.md](./special-designs/contract-approval-bridge-design.md)
- 条款语义标签对象、标签层级、标签来源、推荐候选治理、人工确认边界，以及 `DraftDecisionBlock` 作为起草会话工作态 / 视图块与 `ClauseSelectionDecision` 作为正式人工决策对象之间的承接关系；并进一步说明它们与合同主链、模板 / 条款库 / AI 的关系。中文说明： [clause-semantic-tagging-and-recommendation-design.md](./special-designs/clause-semantic-tagging-and-recommendation-design.md)
- 搜索索引字段映射、分词策略、多语召回策略。中文说明： [contract-search-index-design.md](./special-designs/contract-search-index-design.md)
- 合同详情聚合对象模型、主档 / 文档 / 审批 / 生命周期 / 审计摘要的聚合边界，以及缓存失效、回放重建与消费口径分离。中文说明： [contract-detail-aggregation-design.md](./special-designs/contract-detail-aggregation-design.md)
- 多语言正式治理边界，包括翻译流程、术语库治理、校对与发布边界、审计与回滚。中文说明： [multilingual-governance-design.md](./special-designs/multilingual-governance-design.md)
- 合同主链路历史对象迁移治理、迁移批次、负载验证面、切换窗口、回滚边界、核查口径与证据留存规则。中文说明： [migration-loadtest-and-cutover-design.md](./special-designs/migration-loadtest-and-cutover-design.md)

## 14. 一致性结论

本模块内部设计的核心收口如下：

- 合同主档是业务真相源，`cc_contract_master` 是写模型中心
- 文档中心是文件真相源，本模块只保留双锚点绑定投影、业务角色解释与业务摘要
- 流程引擎通过 `contract_id` 绑定合同，不拥有合同主档
- 模板库 / 条款库是正式能力资源，版本化治理并服务起草与 AI
- 多语言是正式能力，已在主档、模板、条款、台账投影、搜索和 AI 输入中落点
- 周边模块只允许围绕同一 `contract_id` 挂接与回写，不允许形成第二合同真相源

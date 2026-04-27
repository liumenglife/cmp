# 检索 / OCR / AI 业务应用主线专项设计：候选排序与质量评估

## 1. 文档说明

本文档用于继续下沉 `intelligent-applications` 主线中“语义候选排序、候选淘汰、冲突消解、质量评估与放行决策”能力，重点回答以下问题：

- 搜索召回、`OCR` 字段候选、条款 / 模板命中、AI 上下文证据片段、结构化规则命中如何归一为同一候选模型
- 候选如何按统一打分维度、排序规则、淘汰规则和冲突消解规则形成稳定可解释的入模候选集与结果候选集
- 摘要、问答、风险识别、比对提取如何共享同一排序骨架，并在质量评估上体现差异化判断
- 质量评估如何形成可发布、部分发布、转人工、拒绝四类放行结论，并与 AI 输出护栏、人工确认、结果回写建立稳定契约
- 并发、幂等、缓存、失败恢复、监控指标、审计和验收口径如何闭环

本文是以下文档的下游专项设计：

- [`intelligent-applications Detailed Design`](../detailed-design.md)

本文不展开以下内容：

- 不写搜索索引字段调优、`OCR` 引擎识别算法或模型参数调优细节
- 不写多语言术语治理专项、结果回写冲突专项和运维手册专项的完整正文
- 不写训练集构造、评测脚本、算法代码或实施排期
- 不保留历史口径、兼容路径或“后续再决定”占位描述

## 2. 目标与边界

### 2.1 设计目标

- 让所有 AI 业务应用在进入模型前先经过同一候选治理层，而不是各自拼装候选、各自解释置信度
- 让候选排序既可复用搜索、`OCR`、条款 / 模板和规则能力，又不把任何单一来源误升级为真相源
- 让结果放行建立在可解释评分、可追踪证据和稳定质量分层之上，而不是建立在模型自评之上
- 让高风险结果在护栏、人审、回写和审计链路之间具备一致的契约边界
- 让候选快照、质量结论和放行决策可重放、可复盘、可审计

### 2.2 边界原则

- 合同主档是业务真相源；候选排序和质量评估只服务派生结果选择，不解释合同正式业务状态。
- 文档中心是文件真相源；候选模型只引用 `document_asset_id` / `document_version_id` 和页级锚点，不持有私有文件副本。
- 搜索结果、`OCR` 结果、条款 / 模板命中、AI 证据片段、规则命中都属于候选输入，不直接升级为业务结论。
- `Agent OS` 负责运行时与人工确认底座；本文只定义候选治理层和质量评估层如何向其提交输入、消费结果并形成放行判定。
- 质量评估负责“结果能否放行”，不负责“业务是否采纳”；正式采纳仍由人工确认和受控回写链路承接。

## 3. 输入来源与候选准入

### 3.1 候选输入来源

统一候选输入只允许来自以下正式来源：

| 来源类型 | 上游对象 | 最小锚点 | 可提供内容 | 禁止直接透传内容 |
| --- | --- | --- | --- | --- |
| 搜索召回 | `ia_search_result_set` | `result_set_id + item_id` | 稳定命中项、来源锚点、召回得分、命中摘要 | 未落库实时召回中间态 |
| `OCR` 字段候选 | `ia_ocr_result` | `ocr_result_id + field_candidate_id` | 字段值、页号、坐标、识别置信度、证据文本 | 原始引擎私有载荷 |
| 条款 / 模板命中 | `cc_clause_version`、`cc_template_version`、`ia_semantic_reference` | `clause_version_id` / `template_version_id` / `semantic_ref_id` | 标准语义摘要、适用范围、风险标签、结构基线 | 草稿、停用版本、无适用范围资源 |
| AI 上下文证据片段 | `AiContextEnvelope`、`EvidenceSegment` | `result_context_id + evidence_segment_id` | 已裁剪证据片段、片段角色、引用锚点、优先级 | 无锚点自由文本 |
| 结构化规则命中 | 规则引擎结果、策略快照 | `rule_hit_id + rule_version` | 校验结论、风险标签、结构约束、冲突提示 | 未版本化临时规则 |

### 3.2 候选准入规则

所有来源进入排序层前必须满足以下条件：

1. 来源对象存在且处于可消费状态。
2. 候选能回指到稳定主键、稳定版本和可展示证据锚点。
3. 候选未被标记为 `SUPERSEDED`、`EXPIRED`、`HIDDEN` 或仅内部调试可见。
4. 当前调用方对合同、文档、条款、模板和结果具备可见权限。
5. 候选文本、字段值、标签和规则摘要通过当前任务类型的最小质量门槛。

不满足任一条件的输入不进入排序层，只进入审计和失败诊断链路。

## 4. 候选标准化模型

### 4.1 统一对象骨架

所有来源统一归一为 `SemanticCandidate`，用于表达“本次任务中一个可排序、可淘汰、可解释的候选单元”。

最小字段如下：

| 字段 | 说明 |
| --- | --- |
| `candidate_id` | 候选主键 |
| `candidate_type` | `SEARCH_HIT` / `OCR_FIELD` / `CLAUSE_REF` / `TEMPLATE_REF` / `EVIDENCE_SEGMENT` / `RULE_HIT` |
| `application_type` | `SUMMARY` / `QA` / `RISK_ANALYSIS` / `DIFF_EXTRACTION` |
| `contract_id` | 业务归属锚点 |
| `document_version_id` | 文件版本锚点，可为空 |
| `source_language` | 来源文本或字段原始语言 |
| `normalized_language` | 与 `ia_i18n_context.normalized_language` 对齐的归一语言 |
| `response_language` | 当前任务目标输出语言 |
| `source_object_id` | 来源对象主键 |
| `source_anchor_json` | 页号、片段、条款版本、规则命中等稳定锚点 |
| `semantic_slot` | 候选所属槽位，如 `SUMMARY_FACT`、`ANSWER_SUPPORT`、`RISK_EVIDENCE`、`DIFF_BASELINE`、`FIELD_VALUE` |
| `candidate_payload_json` | 候选正文、字段值、标签、结构化摘要 |
| `source_score` | 来源原始得分或置信度 |
| `normalized_score_json` | 归一化后的多维评分明细 |
| `ranking_score` | 最终排序总分 |
| `elimination_status` | `ACTIVE` / `ELIMINATED` / `RESERVED` / `CONFLICTED` |
| `explanation_digest` | 解释性输出摘要 |
| `candidate_digest` | 用于幂等与去重的候选摘要 |

### 4.2 槽位化建模

为避免不同任务直接混排不同语义对象，排序层先按 `semantic_slot` 把候选分槽，再在槽内排序、槽间配额。

最小槽位集合如下：

- `SUMMARY_FACT`：摘要事实骨架
- `ANSWER_SUPPORT`：问答支撑证据
- `RISK_EVIDENCE`：风险证据片段
- `RISK_BASELINE`：标准条款 / 模板基线
- `DIFF_BASELINE`：被比对对象基线片段
- `DIFF_DELTA`：差异证据片段
- `FIELD_VALUE`：结构化提取候选值
- `RULE_OVERRIDE`：规则覆盖或否决信号

### 4.3 候选快照对象

排序层输出 `CandidateRankingSnapshot`，表达一次任务在某个输入快照下形成的稳定候选集。

`CandidateRankingSnapshot` 是运行时对象，不新增独立正式主表。其正式落点统一收口为：

1. `ia_ai_application_job.ranking_snapshot_id`，承接当前任务正在消费的候选快照锚点。
2. `ia_ai_application_result.ranking_snapshot_id`，承接正式结果所依赖的候选快照锚点。
3. 审计事件载荷，承接 `candidate_list_ref`、`selected_candidate_ref`、`source_digest`、解释摘要和淘汰摘要。

最小字段如下：

- `ranking_snapshot_id`
- `ai_application_job_id`
- `application_type`
- `source_digest`
- `ranking_profile_code`
- `quality_profile_code`
- `candidate_list_ref`
- `selected_candidate_ref`
- `snapshot_status`：`READY`、`PARTIAL`、`FAILED`、`SUPERSEDED`
- `expires_at`

### 4.4 质量评估对象正式落点

`QualityEvaluationReport` 同样是运行时对象，不新增独立正式主表。其正式落点统一收口为：

1. `ia_ai_application_job.quality_evaluation_id`，承接当前任务最新质量评估锚点。
2. `ia_ai_application_result.quality_evaluation_id`，承接正式结果所依赖的质量评估锚点。
3. `ia_ai_application_result.structured_payload_json.quality_evaluation_summary`，承接质量分层、缺口说明和理由码摘要。
4. 审计事件载荷，承接完整维度分、放行决策和降级原因。

`ranking_snapshot_id` 与 `quality_evaluation_id` 在本主线中的职责只限于“候选选择依据”和“质量判断依据”追踪，不升级为业务主状态。

### 4.5 配置档位对象

候选排序与质量评估统一依赖两类版本化档位对象：`CandidateRankingProfile` 与 `CandidateQualityProfile`。

两类对象允许由平台配置中心或同等正式配置源承载，但任务运行时必须落为不可变快照，并在父文档正式模型 `ia_ai_application_job`、`ia_ai_application_result` 以及审计事件中保留 `profile_code + profile_version`。运行中不允许只引用“当前最新配置”而不固化版本。

#### `CandidateRankingProfile`

表达候选排序阶段的可执行配置档位。

最小字段如下：

- `ranking_profile_code`
- `profile_version`
- `application_type`
- `slot_quota_json`
- `dimension_weight_json`
- `freshness_threshold_json`
- `duplicate_merge_rule_json`
- `conflict_resolution_rule_json`
- `language_alignment_rule_json`
- `profile_status`

#### `CandidateQualityProfile`

表达质量评估阶段的可执行配置档位。

最小字段如下：

- `quality_profile_code`
- `profile_version`
- `application_type`
- `coverage_threshold_json`
- `citation_threshold_json`
- `consistency_threshold_json`
- `partial_publish_threshold_json`
- `human_review_trigger_rule_json`
- `release_mapping_rule_json`
- `profile_status`

其中：

- `ranking_profile_code` 决定排序阶段的槽位配额、维度权重、语言对齐规则和冲突裁决规则。
- `quality_profile_code` 决定质量分层阈值、部分发布阈值、转人工触发规则和 `release_decision` 映射规则。
- `profile_version` 必须与当次任务快照绑定；后续配置更新不回改既有结果。

## 5. 排序模型与打分维度

### 5.1 共享排序骨架

所有任务类型统一遵循以下排序骨架：

1. 来源准入与权限裁剪。
2. 候选标准化与槽位归类。
3. 候选去重与同源压缩。
4. 维度打分与分值归一。
5. 槽内排序与阈值淘汰。
6. 槽间配额分配与冲突消解。
7. 形成候选快照并输出解释摘要。

### 5.2 打分维度

排序层统一计算以下维度分：

| 维度 | 说明 | 典型来源 |
| --- | --- | --- |
| `source_reliability_score` | 来源可信度，衡量来源本身稳定性 | 搜索稳定快照、正式条款版本、规则版本 |
| `relevance_score` | 与任务意图、问题或摘要范围的相关度 | 搜索召回、上下文片段、模板命中 |
| `evidence_integrity_score` | 证据完整度，衡量是否具备稳定锚点、页号、引用和上下文完整性 | `OCR` 片段、搜索命中、证据片段 |
| `semantic_alignment_score` | 与合同分类、模板结构、条款语义和字段语义的对齐程度 | 条款 / 模板命中、规则命中 |
| `language_alignment_score` | 候选原始语言、归一语言、目标输出语言之间的一致性 | `ia_i18n_context`、搜索语言标记、`OCR` 语言片段 |
| `freshness_score` | 与当前有效文档版本、当前条款版本、当前任务快照的一致性 | 文档版本、条款版本、快照版本 |
| `consistency_score` | 与其他高分候选是否相互印证 | 多来源交叉校验 |
| `risk_penalty_score` | 高风险、不确定或越权候选的惩罚项 | 规则否决、低置信字段、冲突结论 |

### 5.3 总分计算原则

- 排序总分 `ranking_score` 采用“正向加权分 - 风险惩罚分”的统一模型。
- 正向加权分必须至少覆盖 `source_reliability_score`、`relevance_score`、`evidence_integrity_score`、`semantic_alignment_score`、`language_alignment_score`、`consistency_score`。
- `freshness_score` 作为硬门槛与加分双重因子：低于最小阈值直接淘汰，满足阈值后再参与加分。
- `risk_penalty_score` 不允许被其他高分完全抵消；命中强否决规则时直接触发淘汰。

### 5.4 最小语言一致性约束

候选排序层不承担完整多语言治理，但必须满足以下最小语言契约：

1. 每个 `SemanticCandidate` 必须携带 `source_language`、`normalized_language`、`response_language` 三元组，且与 `ia_i18n_context`、搜索命中语言标记、`OCR` 语言片段保持一致。
2. `source_language` 与 `normalized_language` 不一致时，必须保留原始语言锚点，禁止把翻译结果冒充原始证据。
3. `response_language` 与候选语言不一致时，只有在条款 / 模板语义锚点、证据引用锚点仍可稳定回指的前提下，候选才允许参与排序。
4. 同槽位候选若语言不一致且无法证明语义等价，默认按冲突候选处理，不做静默合并。

### 5.5 槽间配额规则

为避免高召回来源挤压关键证据，槽间统一采用配额控制：

- 每个 `semantic_slot` 预设最小保留数和最大放行数。
- 高置信 `RULE_OVERRIDE` 可覆盖普通排序结果，但必须保留解释摘要。
- `SUMMARY`、`QA` 场景优先保证证据覆盖广度；`RISK_ANALYSIS`、`DIFF_EXTRACTION` 优先保证关键证据深度和冲突显式暴露。

## 6. 候选淘汰规则与冲突消解

### 6.1 通用淘汰规则

以下任一命中即淘汰：

1. 锚点缺失，无法回指合同、文档页、条款版本或规则版本。
2. 来源对象已失效、已过期、已被替代或与当前版本不一致。
3. 候选值与任务范围无关，或越过权限边界。
4. 候选质量低于最小阈值，如字段置信度过低、证据残缺、片段截断严重或语言对齐失败。
5. 同源高重复候选中，当前候选被更高分候选完全覆盖。
6. 命中强否决规则，如规则明确禁止自动放行、证据与标准模板硬冲突。

### 6.2 去重与同源压缩

- 同一 `document_version_id + page_no + citation_ref + semantic_slot` 只保留一个主候选，其余进入 `RESERVED`。
- 同一字段值若来自多个来源，优先保留“正式规则命中 + `OCR` 字段 + 搜索证据”三者印证的候选。
- 同一条款 / 模板命中若只是不同摘要视角，保留语义最完整版本，不重复占配额。

### 6.3 冲突消解规则

候选冲突按以下优先级处理：

1. 先判断是否为版本冲突；旧版本候选直接降为 `CONFLICTED` 或淘汰。
2. 再判断是否为语义冲突；规则否决优先于普通检索命中。
3. 再判断是否为语言冲突；原始语言、归一语言和目标输出语言无法建立稳定对齐时，直接升级为冲突候选。
4. 再判断是否为证据强弱冲突；高完整度、高一致性候选优先。
5. 仍无法消解时，不做强行自动选择，直接保留冲突并提升到质量评估层触发转人工或部分发布。

### 6.4 解释性输出

每个入选候选都必须生成 `CandidateExplanation`，至少包含：

- `selected_reason_code_list`
- `eliminated_peer_id_list`
- `top_score_dimension_list`
- `risk_penalty_reason_list`
- `source_anchor_summary`

解释性输出既服务前端展示，也服务审计、人工确认和回放。

## 7. 任务类型差异化策略

### 7.1 摘要

- 共享骨架：优先围绕 `SUMMARY_FACT` 排序，强调覆盖面、去重复和主线完整性。
- 差异化质量关注：事实覆盖率、章节均衡度、关键字段漏失率。
- 放行偏好：可接受少量低风险缺口，但必须能回指主要事实来源。

### 7.2 问答

- 共享骨架：优先围绕 `ANSWER_SUPPORT` 和 `RULE_OVERRIDE` 排序，强调与问题的语义贴合度。
- 差异化质量关注：答案可证实率、问题范围命中率、无来源结论比例。
- 放行偏好：宁可拒答或部分回答，也不放行无锚点确定性结论。

### 7.3 风险识别

- 共享骨架：同时消费 `RISK_EVIDENCE` 和 `RISK_BASELINE`，强调标准基线与合同内容的偏差度。
- 差异化质量关注：风险证据充分性、规则一致性、标准条款映射准确率。
- 放行偏好：高风险结论默认更保守，优先转人工而不是自动放行。

### 7.4 比对提取

- 共享骨架：对称处理 `DIFF_BASELINE`、`DIFF_DELTA` 和 `FIELD_VALUE`，强调左右侧输入预算对齐。
- 差异化质量关注：字段配对完整率、差异定位准确率、双侧证据对称性。
- 放行偏好：不允许只给差异结论而不给双侧证据；关键字段缺证时只能部分发布或转人工。

## 8. 质量评估模型与分层

### 8.1 质量评估对象

质量评估不直接评估原始来源，而是评估“排序后形成的候选快照是否足以支撑结果放行”。统一对象命名为 `QualityEvaluationReport`。

最小字段如下：

- `quality_evaluation_id`
- `ranking_snapshot_id`
- `application_type`
- `quality_profile_code`
- `coverage_score`
- `citation_validity_score`
- `consistency_score`
- `conflict_penalty_score`
- `completeness_score`
- `publishability_score`
- `quality_tier`
- `release_decision`
- `decision_reason_code_list`

`QualityEvaluationReport` 的 `quality_evaluation_id` 必须与 `ranking_snapshot_id` 形成一对一当前版本关系；若同一候选快照因护栏策略或人工确认规则变化而重评，必须生成新的 `quality_evaluation_id`，旧报告只保留审计可查，不回改既有结果。

### 8.2 质量评估维度

| 维度 | 说明 |
| --- | --- |
| `coverage_score` | 候选是否覆盖当前任务所需事实、字段、风险点或比对对象 |
| `citation_validity_score` | 最终结果引用是否都能稳定回指到正式来源 |
| `consistency_score` | 高分候选之间和结果结论之间是否一致 |
| `language_consistency_score` | 候选语言、结果语言、引用语言是否保持一致且可解释 |
| `completeness_score` | 当前任务要求的结构是否完整，如摘要章节、问答答案结构、风险项结构、比对对称性 |
| `conflict_penalty_score` | 是否存在未消解冲突、版本冲突、规则硬冲突 |
| `publishability_score` | 结合护栏、人审阈值和业务场景后的最终可放行分 |

### 8.3 质量分层

统一质量分层如下：

- `TIER_A`：证据充分、冲突已消解、可直接进入可发布判定。
- `TIER_B`：主体可用但存在局部缺口，只能进入部分发布或受限展示。
- `TIER_C`：存在明显冲突、关键证据缺失或高风险不确定，必须转人工。
- `TIER_D`：无法形成可信结果，必须拒绝自动输出。

### 8.4 放行决策规则

`release_decision` 统一为四类：

| 决策 | 规则 |
| --- | --- |
| `PUBLISH` | `quality_tier=TIER_A`，且未命中人工确认强制规则 |
| `PARTIAL_PUBLISH` | `quality_tier=TIER_B`，且允许以明确缺口说明对外展示 |
| `ESCALATE_TO_HUMAN` | `quality_tier=TIER_C`，或命中高风险、人审必选、冲突未消解规则 |
| `REJECT` | `quality_tier=TIER_D`，或关键锚点缺失、结果不可解释、越权风险存在 |

### 8.5 `release_decision` 与护栏 / 正式状态映射

`release_decision` 不直接替代护栏判定或正式结果状态，而是作为护栏输入之一。映射规则统一如下：

| `release_decision` | 护栏专项 `guardrail_decision` | `ia_ai_application_result.result_status` | `ia_ai_application_job.job_status` | `ProtectedResultSnapshot` |
| --- | --- | --- | --- | --- |
| `PUBLISH` | `PASS` | `READY` | `SUCCEEDED` | 不要求；如需保留审计摘要可仅留审计事件 |
| `PARTIAL_PUBLISH` | `PASS_PARTIAL` | `PARTIAL` | `SUCCEEDED` | 不要求；如需保留缺口说明可写审计事件 |
| `ESCALATE_TO_HUMAN` | `REVIEW_REQUIRED` | 不切换到新的等待态结果枚举，保持当前正式结果未放行 | `WAITING_HUMAN_CONFIRMATION` | 必须写入，用于承接受保护结果快照 |
| `REJECT` | `REJECT`；若结构或引用校验先失败，也可被护栏收口为 `BLOCK` | `REJECTED`；若未形成正式结果，可维持失败并不生成可展示结果 | `FAILED` 或 `CANCELLED`，若已进入人工确认再被驳回则由确认链路结束任务 | 必须写入，用于留存原始结果、拒绝原因和重放依据 |

补充约束如下：

- `release_decision=ESCALATE_TO_HUMAN` 时，正式等待态只落在 `ia_ai_application_job.job_status=WAITING_HUMAN_CONFIRMATION` 与 `Agent OS` 人工确认链路，不新增 `ia_ai_application_result` 平行等待态。
- `release_decision=REJECT` 只表达质量层结论；若护栏在 schema、引用或敏感信息校验阶段先失败，统一由 `guardrail_decision=BLOCK/REJECT` 收口，并由 `ProtectedResultSnapshot` 承接。
- `ProtectedResultSnapshot` 只承接受保护结果，不替代 `ia_ai_application_result` 正式状态。

## 9. 与上下游的契约边界

### 9.1 与 AI 上下文装配层的契约

- 上下文装配层提供 `EvidenceSegment`、来源快照和护栏档位，不直接决定候选排序结果。
- 排序层返回 `CandidateRankingSnapshot` 和入选候选摘要，供上下文装配层二次预算裁剪时复用。
- 若上下文装配预算裁剪导致关键候选丢失，必须回写新的 `source_digest`，重新触发质量评估。

### 9.2 与 AI 输出护栏的契约

- 护栏层消费 `QualityEvaluationReport.release_decision` 作为结果放行前置条件。
- 护栏层若检测到无来源结论、结构不合规或敏感越权，可把 `PUBLISH` 降级为 `ESCALATE_TO_HUMAN` 或 `REJECT`。
- 护栏层必须把最终 `guardrail_decision`、`guardrail_failure_code`、`ranking_snapshot_id`、`quality_evaluation_id` 一并写入结果元数据或受保护结果快照，避免质量层与护栏层追踪断链。
- 排序层和质量层不绕过护栏直接放行业务结果。

### 9.3 与人工确认的契约

- `ESCALATE_TO_HUMAN` 必须生成带候选解释和冲突摘要的人审上下文。
- 人工确认对象归 `Agent OS` 管理；本主线只记录确认状态快照、确认结果和业务放行状态。
- `ProtectedResultSnapshot` 必须同时承接 `guardrail_decision`、`ranking_snapshot_id`、`quality_evaluation_id`、`decision_reason_code_list` 和原始结果摘要，作为等待态与驳回态的唯一正式留痕载体。
- 人工驳回后，当前候选快照进入 `SUPERSEDED` 或 `REJECTED`，不得继续复用为默认候选。

### 9.4 与结果回写链路的契约

- 只有 `release_decision in (PUBLISH, PARTIAL_PUBLISH)` 的结果才允许进入回写候选队列。
- `PARTIAL_PUBLISH` 的结果回写时必须显式标注缺口说明和受限范围，不得伪装成完整结果。
- 回写链路必须保留 `ranking_snapshot_id`、`quality_evaluation_id` 和 `decision_reason_code_list`，作为审计和回放锚点。
- 若回写前发现 `ranking_snapshot_id` 或 `quality_evaluation_id` 缺失，必须阻断回写，因为这意味着候选选择依据或质量判断依据不可追踪。

## 10. 并发、幂等、缓存与失败恢复

### 10.1 并发控制

- 同一 `ai_application_job_id + source_digest + ranking_profile_code` 只允许一个有效排序任务在运行。
- 候选快照生成使用乐观锁和短时互斥锁双重控制，避免重复生成多个当前快照。
- 文档版本切换、条款版本切换或搜索快照更新时，新任务可并发创建，但旧快照必须进入 `SUPERSEDED`。

### 10.2 幂等规则

- 候选排序请求幂等键为 `application_type + contract_id + source_digest + ranking_profile_code`。
- 质量评估请求幂等键为 `ranking_snapshot_id + quality_profile_code`。
- 同一幂等键命中时返回既有快照 / 报告；若请求体摘要不同，则判定为幂等冲突。

### 10.3 缓存策略

- `Redis` 只缓存短期候选快照、分值明细和解释摘要，不缓存正式放行决策的唯一真相。
- 缓存失效后必须可由 `CandidateRankingSnapshot` 和正式来源重建。
- 缓存命中必须校验 `source_digest`、权限摘要和版本摘要，任一不一致直接失效。

### 10.4 失败恢复

- 排序失败但来源快照存在时，允许按同一 `source_digest` 重排，不重跑上游搜索或 `OCR`。
- 质量评估失败时，保留候选快照，允许单独重跑评估，不重建候选。
- 任一关键来源对象失效时，当前候选快照直接转为 `SUPERSEDED`，由异步任务基于新来源重建。
- 恢复优先级为：正式放行决策一致性 > 候选快照可重建性 > 缓存命中率恢复。

## 11. 监控、审计与验收口径

### 11.1 监控指标

- 吞吐：候选生成数、排序完成数、质量评估完成数、放行决策数。
- 时延：候选标准化耗时、排序耗时、质量评估耗时、转人工耗时。
- 质量：候选淘汰率、冲突率、无来源结论拦截率、部分发布率、人工驳回率。
- 稳定性：幂等命中率、排序重试率、快照重建率、缓存命中率。

### 11.2 审计要求

关键审计事件至少包括：

- 候选来源准入 / 拒绝
- 候选去重 / 淘汰 / 冲突升级
- 候选快照生成 / 失效 / 重建
- 质量评估完成 / 降级 / 拒绝
- 放行决策生成 / 转人工 / 人工驳回 / 人工放行
- profile 版本命中 / 配置切换 / 语言对齐失败

审计记录至少能追到：`contract_id`、`document_version_id`、`ai_application_job_id`、`ranking_snapshot_id`、`quality_evaluation_id`、`result_id`、`writeback_record_id`、`ranking_profile_code`、`quality_profile_code`。

### 11.3 验收清单

- [ ] 五类输入来源均可归一到统一 `SemanticCandidate` 模型，且来源锚点可回指。
- [ ] 四类任务类型共用同一排序骨架，但质量评估维度和放行规则体现差异化。
- [ ] 候选淘汰、冲突消解和解释性输出具备稳定对象与明确规则。
- [ ] `ranking_snapshot_id`、`quality_evaluation_id`、profile 版本和语言一致性约束具备正式落点与审计锚点。
- [ ] 质量分层可稳定导出 `PUBLISH`、`PARTIAL_PUBLISH`、`ESCALATE_TO_HUMAN`、`REJECT` 四类决策。
- [ ] 与 AI 输出护栏、人工确认、结果回写链路的契约边界清晰，且不互相越权。
- [ ] 并发、幂等、缓存、失败恢复、监控和审计口径完整，无需依赖临时解释。

## 12. 小结

本专项设计把 `intelligent-applications` 主线中“候选如何被选出来、为什么能放行、何时必须转人工”三件事收口为同一治理层：

- 候选来源统一，避免各任务各自拼接
- 排序骨架统一，避免各任务各自定义置信度
- 质量分层统一，避免结果不可比较、不可解释、不可审计
- 放行契约统一，避免护栏、人审、回写各自为政

该口径与 `OCR`、搜索、AI 上下文装配和输出护栏专项保持一致，可作为 AI 结果生成前的稳定选择层与结果质量判断层。

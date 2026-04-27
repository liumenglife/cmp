# 检索 / OCR / AI 业务应用主线专项设计：AI 上下文装配与输出护栏

## 1. 文档说明

本文档用于继续下沉 `intelligent-applications` 主线中“AI 应用输入装配、上下文裁剪、证据绑定、输出校验、人工确认与安全护栏”能力，重点回答以下问题：

- AI 应用可以消费哪些正式来源，以及哪些内容必须挡在上下文装配边界之外
- 合同主档、文档中心、`OCR`、搜索、条款库 / 模板库、用户问题与任务意图如何归一为统一上下文模型
- 上下文片段如何裁剪、排序、预算、降级，并保持来源可回指、结果可审计
- 摘要、问答、风险识别、比对提取如何复用同一装配骨架，并在输出约束上体现差异化规则
- AI 输出如何经过结构化 schema 校验、引用校验、敏感信息约束、无来源结论拦截、冲突降级和拒答策略后再放行
- 与搜索、`OCR`、结果回写、人工确认、审计链路之间的输入输出契约、失败恢复、并发幂等、缓存、监控与验收口径如何收口

本文是以下文档的下游专项设计：

- [`intelligent-applications Detailed Design`](../detailed-design.md)

本文不展开以下内容：

- 不写搜索排序算法、索引字段调优或召回算法实现细节
- 不写 `OCR` 引擎适配、版面识别、表格识别和字段候选生成的专项细节
- 不写结果回写字段级冲突优先级专项或多语言治理专项的完整正文
- 不写模型 / `Provider` 选型、Prompt 正文、SDK 调用代码或实施排期

## 2. 目标与边界

### 2.1 设计目标

- 让 AI 业务应用只消费受控输入，不允许绕过合同主档、文档中心、`OCR`、搜索和语义资源边界直接把原始材料送入模型
- 让上下文装配成为独立可治理层，把输入归一、裁剪、证据绑定、预算控制和降级策略统一收口
- 让 AI 输出先过护栏再放行，避免无来源结论、越权信息、敏感信息扩散和高风险自动回写
- 让摘要、问答、风险识别、比对提取共用一套任务骨架和对象模型，避免每类应用各自拼接上下文和各自解释结果
- 让结果放行建立在“来源可回指、结构可校验、风险可识别、必要时有人审”的链路上，而不是建立在模型自解释上

### 2.2 边界原则

- 合同主档是业务真相源；AI 应用只读取合同摘要、分类、主体、里程碑、风险视图等正式摘要，不直写主字段。
- 文档中心是文件真相源；AI 应用只读取 `document_asset_id` / `document_version_id` 对应的受控版本与受控片段，不保存长期私有文件副本。
- `OCR` 结果和搜索结果都是派生结果；只有满足可消费状态的稳定结果才能进入上下文装配层。
- 条款库 / 模板库是正式语义资源；AI 只能引用正式启用版本或显式允许暴露的版本快照，不消费草稿资源。
- `Agent OS` 是 AI 运行时底座；本文只定义业务层如何准备输入和校验输出，不接管模型路由、底层工具协议和人工确认引擎内核。
- AI 结果仍是派生结果；在通过护栏、必要时通过人工确认之前，不得成为正式业务结论。

## 3. 输入来源与受控装配边界

### 3.1 正式输入来源

AI 应用输入统一按“真相锚点 + 派生证据 + 任务意图”三类来源装配：

| 来源类型 | 正式上游对象 | 最小锚点 | 可装配内容 | 禁止直接装配内容 |
| --- | --- | --- | --- | --- |
| 合同主档 | `cc_contract_master`、`cc_contract_summary`、`cc_contract_ledger_projection` | `contract_id` | 合同编号、标题、相对方、分类、阶段、摘要、风险摘要、主文档引用 | 未发布编辑态、仅写模型中间字段、越权业务字段 |
| 文档中心 | `dc_document_asset`、`dc_document_version`、能力挂接摘要 | `document_asset_id`、`document_version_id` | 文档标题、文档角色、主版本摘要、版本状态、页级引用入口 | 裸文件副本、脱离版本锚点的正文全文直传 |
| `OCR` 结果 | `ia_ocr_result` | `ocr_result_id`、`document_version_id` | 页级文本片段、版面块、字段候选、语言片段、引用坐标 | 原始引擎私有载荷、质量未达标噪声块 |
| 搜索结果 | `ia_search_result_set` | `result_set_id` | 稳定候选集、命中片段、来源锚点、分页快照 | 未持久化实时召回结果、无锚点高亮文本 |
| 条款库 / 模板库 | 条款版本、模板版本、语义引用 | `clause_version_id`、`template_version_id`、`semantic_ref_id` | 标准条款摘要、模板结构摘要、风险标签、适用范围、标准语义锚点 | 草稿正文、未启用版本、无适用范围说明的材料 |
| 用户问题与任务意图 | 任务请求、问答问题、摘要范围、审查意图 | `ai_application_job_id` | 问题文本、目标语种、任务类型、关注范围、用户显式指定对象 | 未经过权限裁剪的自由文本外链、无归属任务的临时提示 |

### 3.2 装配准入规则

上下文装配层对每个候选输入执行统一准入检查：

1. 对象存在且处于可消费状态。
2. 调用方对合同、文档、条款、模板具备可见权限。
3. 来源对象能回指到稳定主键和稳定版本。
4. 片段大小、语种、敏感级别、质量得分满足当前任务档位。
5. 候选输入未被标记为 `SUPERSEDED`、`EXPIRED`、`HIDDEN` 或“仅内部调试可见”。

不满足任一条件的内容不得进入最终上下文包，只允许进入审计和失败诊断链路。

### 3.3 非法输入边界

以下内容一律不得绕过装配层直入模型：

- 用户手工粘贴但系统无法建立对象锚点的合同长文本
- 文档中心受控版本之外的本地导出副本或临时下载副本
- 搜索实时召回临时结果、排序中间态、未落库候选集
- `OCR` 原始引擎响应、调试载荷、未通过质量门槛的版面噪声
- 条款库 / 模板库草稿、停用版本、未过适用范围校验的资源
- 已被权限裁剪剔除但仍想以“参考资料”身份混入的片段

## 4. 上下文装配模型

### 4.1 统一装配骨架

AI 应用上下文统一装配为 `AiContextEnvelope`，由以下六层组成：

1. `TaskIntentLayer`：任务类型、目标语种、输出模式、用户问题、关注范围。
2. `ContractAnchorLayer`：合同主档摘要、合同阶段、主体、主文档锚点、风险摘要。
3. `DocumentEvidenceLayer`：文档版本摘要、页级片段、`OCR` 版面块、字段候选、语言片段。
4. `RetrievalLayer`：搜索候选集、命中片段、稳定来源锚点、召回得分摘要。
5. `SemanticReferenceLayer`：条款库 / 模板库标准语义引用、标准结构摘要、风险标签。
6. `GuardrailLayer`：输出 schema、必带引用要求、敏感信息约束、拒答条件、人工确认阈值。

该对象只表达“本次任务能用什么、必须怎么答”，不保存模型私有参数和厂商特定字段。

### 4.2 核心对象

#### `AiContextAssemblyJob`

表达一次 AI 上下文准备任务。

最小字段如下：

- `context_assembly_job_id`
- `ai_application_job_id`
- `application_type`：`SUMMARY` / `QA` / `RISK_ANALYSIS` / `DIFF_EXTRACTION`
- `contract_id`
- `context_status`：`ACCEPTED`、`ASSEMBLING`、`READY`、`PARTIAL`、`FAILED`、`SUPERSEDED`
- `intent_digest`
- `scope_digest`
- `budget_profile_code`
- `result_context_id`
- `failure_code` / `failure_reason`
- `idempotency_key`
- `trace_id`

#### `AiContextEnvelope`

表达可送入 `Agent OS` 的稳定上下文包。

最小字段如下：

- `result_context_id`
- `ai_application_job_id`
- `application_type`
- `contract_anchor_snapshot`
- `document_scope_snapshot`
- `evidence_segment_list`
- `retrieval_snapshot_ref`
- `semantic_reference_list`
- `guardrail_profile_code`
- `token_budget_snapshot`
- `source_digest`
- `assembled_at`

#### `AiOutputGuardrailProfile`

表达 AI 上下文装配与输出护栏使用的可执行配置档位。

最小字段如下：

- `guardrail_profile_code`
- `application_type`
- `schema_profile_code`
- `single_segment_token_cap`
- `min_evidence_coverage_ratio`
- `max_conclusion_divergence_ratio`
- `min_partial_publish_coverage_ratio`
- `confirmation_trigger_threshold_json`
- `sensitive_masking_profile_code`
- `protected_result_retention_hours`
- `profile_status`
- `profile_version`

其中：

- `single_segment_token_cap` 用于界定单片段阈值；超过该阈值的片段必须先做页内压缩。
- `min_evidence_coverage_ratio` 用于界定可放行结果的最低证据覆盖率。
- `max_conclusion_divergence_ratio` 用于界定同任务多次运行结论差异阈值；超过该阈值不得自动放行确定性结果。
- `min_partial_publish_coverage_ratio` 用于界定 `PARTIAL` 结果允许保留的最低覆盖比例。

#### `ProtectedResultSnapshot`

表达“受保护结果”的正式承接对象。受保护结果不是新的正式结果状态，而是对尚未放行、需保留审计与重试价值的结果快照留存。

最小字段如下：

- `protected_result_snapshot_id`
- `ai_application_job_id`
- `result_id`
- `agent_task_id`
- `agent_result_id`
- `guardrail_decision`
- `guardrail_failure_code`
- `confirmation_required_flag`
- `protected_payload_ref`
- `expires_at`

受保护结果的正式定义如下：

- 已经从 `Agent OS` 回收到 `agent_result_id`，但尚未通过护栏放行为业务正式结果，或仍在等待人工确认的结果快照。
- 它只用于审计、人工确认、重放校验和受控重试，不对业务默认展示，也不改变父文档 `ia_ai_application_result.result_status` 正式枚举。

#### `EvidenceSegment`

表达可被模型消费且可被输出引用回指的最小片段。

最小字段如下：

- `evidence_segment_id`
- `source_type`：`CONTRACT_SUMMARY`、`DOCUMENT_SNIPPET`、`OCR_BLOCK`、`SEARCH_HIT`、`CLAUSE_REF`、`TEMPLATE_REF`
- `source_object_id`
- `document_version_id`
- `page_no`
- `citation_ref`
- `language_code`
- `segment_text`
- `segment_role`：`FACT`、`SUMMARY_SEED`、`RISK_EVIDENCE`、`DIFF_BASELINE`、`ANSWER_SUPPORT`
- `priority_score`
- `token_cost`
- `sensitivity_level`

### 4.3 片段裁剪与分层优先级

上下文片段按“先真相锚点、再稳定证据、再语义参照、最后补充候选”的顺序裁剪：

1. 必选层：任务意图、合同锚点、当前文档范围摘要。
2. 高优先级证据层：当前文档版本命中的 `OCR` 片段、页级坐标、必要字段候选。
3. 稳定召回层：搜索快照中得分最高且与任务范围一致的候选片段。
4. 语义参照层：命中适用范围的标准条款、模板结构摘要、标准风险标签。
5. 低优先级补充层：跨文档辅助片段、历史稳定结果摘要、低分候选说明。

裁剪规则如下：

- 同一来源对象优先保留覆盖不同页、不同条款或不同风险点的片段，避免重复堆叠。
- 同一文档页内优先保留带 `citation_ref` 的结构化片段，不优先保留长段裸文本。
- `EvidenceSegment` 超出 `AiOutputGuardrailProfile.single_segment_token_cap` 时必须先做页内摘要压缩，再考虑跨来源淘汰。
- 若任务要求比对，则两个被比对对象必须保持预算对称，避免一侧上下文严重稀释。

### 4.4 Token 预算与降级策略

上下文预算统一按 `budget_profile_code` 管理，至少包含以下维度：

- `max_input_tokens`
- `max_output_tokens`
- `reserved_guardrail_tokens`
- `reserved_citation_tokens`
- `per_document_cap`
- `per_source_type_cap`
- `max_evidence_segment_count`

其中与输出护栏直接联动的阈值统一由 `guardrail_profile_code` 绑定的 `AiOutputGuardrailProfile` 承接，不在任务执行时临时拍板。

预算不足时按以下顺序降级：

1. 先裁掉低优先级补充层。
2. 再压缩长文档摘要，不压缩已命中的精确证据段。
3. 再缩减跨文档辅助片段数量。
4. 再限制语义参照层只保留最相关标准条款 / 模板结构摘要。
5. 若仍超预算，则拒绝自动执行并转人工缩小范围。

任何降级都必须保留合同锚点、来源清单和至少一组可解释证据；没有证据的上下文包不得继续执行。

### 4.5 证据绑定规则

所有进入模型的证据片段都必须建立 `evidence_segment_id -> source_anchor` 的稳定映射，最小回指粒度如下：

- 合同摘要：`contract_id + summary_version`
- 文档片段 / `OCR` 片段：`document_version_id + page_no + citation_ref`
- 搜索命中：`result_set_id + item_id + source_anchor_json`
- 条款 / 模板引用：`semantic_ref_id` 或版本级主键

模型输出中的每一条结论、风险项、问答项、比对差异项，都必须引用至少一个 `evidence_segment_id`。没有引用映射的内容视为无来源结论。

## 5. 任务类型共享骨架与差异化规则

### 5.1 共享骨架

摘要、问答、风险识别、比对提取统一遵循以下处理骨架：

1. 受理任务与权限校验。
2. 组装 `AiContextAssemblyJob`。
3. 读取合同锚点、文档范围、`OCR` 结果、搜索快照和语义资源。
4. 执行片段裁剪、预算控制和证据绑定。
5. 生成 `AiContextEnvelope` 并提交 `Agent OS`。
6. 回收结构化结果并执行输出护栏。
7. 根据风险等级决定直接放行、降级展示、拒答或转人工确认。

### 5.2 摘要

- 输入重点：合同摘要、主文档正文、条款结构摘要、关键字段候选。
- 输出要求：必须产出分段摘要、关键事实列表和引用列表。
- 差异化规则：摘要可以做段内压缩，但不得跨来源拼接出未被证据覆盖的事实。

### 5.3 问答

- 输入重点：用户问题、搜索候选集、命中页片段、条款标准语义引用。
- 输出要求：答案必须逐条对应问题，并附可回指的证据列表。
- 差异化规则：若问题超出受控范围、无足够证据或涉及越权信息，必须拒答或提示缩小范围。

### 5.4 风险识别

- 输入重点：标准条款、模板结构摘要、风险标签、合同现状片段、字段候选。
- 输出要求：每条风险至少包含 `risk_code`、`risk_level`、`evidence_list`、`reason`、`suggestion`。
- 差异化规则：高风险结论若证据不足、标准条款冲突或模型结论不一致，必须降级为“待确认风险”。

### 5.5 比对提取

- 输入重点：两个或多个文档 / 条款对象的对称证据片段、结构摘要、字段候选。
- 输出要求：必须明确 `baseline_source`、`target_source`、`difference_type`、`difference_evidence_list`。
- 差异化规则：若基线对象不完整、版本不一致或两侧证据不对称，不允许输出确定性差异结论，只能输出“范围不足”。

## 6. 输出校验与护栏

### 6.1 统一输出模型

AI 结果进入业务侧前统一归一为 `AiGuardedResult`，最小结构如下：

- `result_id`
- `application_type`
- `result_status`：沿用父文档 `ia_ai_application_result` 正式枚举，即 `READY`、`PARTIAL`、`FAILED`、`REJECTED`、`SUPERSEDED`
- `structured_payload`
- `citation_list`
- `evidence_coverage_ratio`
- `guardrail_decision`：`PASS`、`PASS_PARTIAL`、`BLOCK`、`REVIEW_REQUIRED`、`REJECT`
- `confirmation_required_flag`
- `writeback_allowed_flag`
- `risk_flag_list`

这里需要显式收口两层语义：

- `result_status` 是正式结果状态，必须与父文档一致，不新增平行状态。
- `guardrail_decision` 是护栏判定结果，只表达“当前输出经过校验后应如何处理”，不进入正式结果状态枚举。

当护栏要求人工确认时，正式等待态落在 `ia_ai_application_job.job_status=WAITING_HUMAN_CONFIRMATION` 与 `Agent OS` 人工确认链路，而不是落在 `ia_ai_application_result.result_status`。

### 6.2 Schema 校验

不同任务类型各自定义结构化 schema，但共享以下校验原则：

- 顶层字段必须完整，缺失关键字段直接拦截。
- 枚举值必须命中正式编码，不能输出自由文本状态值替代正式枚举。
- `citation_list` 中的每个引用必须能映射到输入阶段生成的 `evidence_segment_id`。
- `structured_payload` 中涉及金额、日期、主体名、风险等级等关键字段时，必须符合字段类型和格式约束。

schema 不通过时，`guardrail_decision=BLOCK`，并生成 `ProtectedResultSnapshot` 留存审计与重放依据；该结果不得直接展示为正式结果。

### 6.3 引用校验

引用校验分三层执行：

1. 存在性校验：输出引用的 `evidence_segment_id` 必须真实存在于本次上下文包。
2. 可见性校验：引用对象当前仍对请求方可见，且未被 `SUPERSEDED` / `HIDDEN`。
3. 覆盖性校验：结果中的每条关键结论必须至少有一条引用；高风险或比对类结论必须至少有两条独立证据或一条证据加一条标准语义参照。

未通过引用校验的内容直接标记为无来源内容并拦截；若需要保留原始输出，只能进入 `ProtectedResultSnapshot`，不能新增正式结果状态。

### 6.4 敏感信息约束

输出护栏必须校验以下敏感信息规则：

- 不返回超出调用方权限范围的文档正文、附件正文或条款正文。
- 不在默认回答中输出身份证号、银行卡号、印章证书号等高敏字段全文；如业务允许展示，必须走字段级脱敏。
- 不把搜索结果中被字段裁剪的隐藏内容重新拼接回回答。
- 不泄露模型内部推理、底层 `Provider` 信息、隐藏系统指令或调试信息。

### 6.5 无来源结论拦截

以下情况一律视为无来源结论并阻断：

- 结论没有任何 `citation_list`
- 引用存在但无法回指到正式对象锚点
- 结论超出了输入证据表达范围，例如从单页片段推断整份合同结论
- 输出给出了“建议已确认”“合同一定存在某风险”等确定性表述，但输入仅提供候选或局部证据

### 6.6 冲突结果降级

当出现以下冲突时，系统不得输出确定性正式结论：

- `OCR` 字段候选与文档正文片段冲突
- 搜索命中片段与条款标准语义引用冲突
- 同一任务多次运行结论差异超过 `AiOutputGuardrailProfile.max_conclusion_divergence_ratio`
- 当前文档版本与被引用的旧版本证据冲突

冲突处理统一降级为以下之一：

- `PASS_PARTIAL`：仅放行已被证据覆盖且达到 `min_partial_publish_coverage_ratio` 的部分，正式结果状态记为 `PARTIAL`
- `REVIEW_REQUIRED`：展示冲突项并要求人工确认，等待态落到 job / 人工确认链路，当前结果快照进入 `ProtectedResultSnapshot`
- `REJECT`：直接拒绝输出正式结果，正式结果状态记为 `REJECTED`

### 6.7 拒答策略

以下场景必须拒答或要求缩小范围：

- 问题超出权限范围或超出当前合同 / 文档范围
- 上下文预算不足且降级后仍无法保留必要证据
- 搜索与 `OCR` 无法提供足够证据支撑结论
- 用户要求输出法律承诺、审批结论或替代人工决策的绝对性意见

拒答时仍需返回原因码、可操作建议和已识别的可用范围，不返回空白失败；拒答留痕同样通过 `ProtectedResultSnapshot` 承接。

## 7. 人工确认策略

### 7.1 触发条件

满足任一条件时必须转人工确认：

- 结果将进入合同摘要区、风险视图或提取结果区的正式回写链路
- 风险等级达到高风险，或冲突证据未能自动消解
- 比对提取涉及金额、主体、日期等关键字段且证据覆盖率低于 `AiOutputGuardrailProfile.min_evidence_coverage_ratio`
- 问答结果被用户明确要求“用于正式答复”但证据链不够稳定
- 结果包含敏感字段展示申请、越权边界例外或跨组织可见性调整

### 7.2 确认单内容

提交给 `Agent OS` 的人工确认单最小应包含：

- 任务类型与目标对象
- 结果摘要与结构化 payload 摘要
- 冲突项 / 风险项摘要
- 证据包引用 `evidence_bundle_ref`
- 推荐动作：放行、驳回、要求重跑、缩小范围后重跑
- 当前结果是否允许回写、是否允许对外展示

### 7.3 人审后的业务状态

- `APPROVE`：结果进入 `READY`，可按业务规则继续展示或回写。
- `REQUEST_CHANGES`：回到上下文装配或执行阶段重跑，不复用旧放行状态；原始待确认结果继续保留在 `ProtectedResultSnapshot`。
- `REJECT`：结果进入 `REJECTED`，保留审计但不进入业务默认展示。

## 8. 与上下游模块的输入输出契约

### 8.1 与搜索的契约

- 输入：`result_set_id`、查询范围摘要、权限裁剪摘要、命中片段与来源锚点。
- 输出：上下文装配层只消费稳定结果快照，不消费实时未落库召回结果。
- 约束：若搜索快照过期或代次变化导致结果不再稳定，必须重新取快照，不继续使用旧上下文。

### 8.2 与 `OCR` 的契约

- 输入：`ocr_result_id`、页级片段、版面块、字段候选、语言片段、质量摘要。
- 输出：上下文装配层只消费 `READY` / `PARTIAL` 且允许用于 AI 的结果。
- 约束：引用必须能回指到 `document_version_id + page_no + citation_ref`。

### 8.3 与 `Agent OS` 的契约

- 提交输入：`ai_application_job_id`、`context_assembly_job_id`、`result_context_id`、`guardrail_profile_code`、业务侧 `idempotency_key`、`trace_id`。
- 任务绑定：业务侧创建 `ia_ai_application_job` 后，必须把 `agent_task_id` 回填到任务主表；`Agent OS` 返回结果时必须回填 `agent_result_id` 到 `ia_ai_application_result` 或 `ProtectedResultSnapshot`。
- 幂等边界：业务侧以 `application_type + contract_id + idempotency_key` 保证任务幂等；提交给 `Agent OS` 时沿用同一业务幂等键，不另发明无关幂等主语义。
- 超时边界：`Agent OS` 在业务约定超时内未返回时，`ia_ai_application_job` 保持 `RUNNING` 或转 `FAILED`，不得伪造业务结果；是否重试由业务侧重试策略和 `Agent OS` 任务状态共同决定。
- 重试边界：只有当 `agent_task_id` 未产出可用 `agent_result_id`，或明确判定为运行时失败时，才允许基于既有 `AiContextEnvelope` 重试；已经产出结果但护栏失败时，不重新生成新上下文，优先重放护栏或转人工确认。
- 护栏失败处理：若 `Agent OS` 已返回 `agent_result_id` 但护栏判定 `BLOCK` / `REVIEW_REQUIRED` / `REJECT`，业务侧必须保留 `agent_result_id` 绑定，写入 `ProtectedResultSnapshot`，并根据规则终止、等待人工确认或拒绝回写。
- 终止边界：护栏已判定拒绝或人工确认拒绝后，业务侧终止本次结果放行，不再向回写链路传播；如需重跑，必须新建业务任务或显式发起重跑。

### 8.4 与结果回写的契约

- 输入：已通过护栏且必要时已通过人工确认的 `AiGuardedResult`。
- 输出：回写侧只接收结构化结果摘要、引用列表、确认状态和写前快照摘要。
- 约束：未通过护栏、未完成确认或 `writeback_allowed_flag=false` 的结果不得进入回写主链。

### 8.5 与人工确认的契约

- 输入：结果摘要、证据包、风险标记、推荐动作。
- 输出：确认决定、确认范围、确认意见、恢复策略。
- 约束：业务模块只消费确认结果快照，不读取人工确认底层私有表。

### 8.6 与审计链路的契约

- 输入：装配来源清单、预算快照、降级动作、护栏决策、确认结果。
- 输出：全链路审计事件和可回放证据包引用。
- 约束：审计必须能追溯到 `contract_id`、`document_version_id`、`result_set_id`、`ai_application_job_id`、`agent_task_id`、`result_id`，以及必要时的 `protected_result_snapshot_id`。

## 9. 并发、幂等、缓存与失败恢复

### 9.1 并发控制

- 同一 `application_type + contract_id + scope_digest` 在同一时刻只允许一个上下文装配任务进入最终提交阶段。
- 比对提取任务以 `baseline_scope_digest + target_scope_digest` 作为并发互斥键，避免同一比对对象同时生成多份漂移上下文。
- 人工确认中的结果对象不可被新的自动回写覆盖，只允许新任务生成新结果版本。

### 9.2 幂等规则

- 任务请求必须提供 `idempotency_key`。
- 相同任务意图、相同范围摘要、相同来源摘要命中幂等时，返回已有 `AiContextAssemblyJob` 或已有 `ProtectedResultSnapshot`。
- 相同 `idempotency_key` 对应不同范围或不同来源摘要时，判定为幂等冲突并拒绝受理。

### 9.3 缓存策略

- 可缓存：`AiContextEnvelope` 摘要、搜索快照引用、片段排序结果、预算计算结果。
- 不可缓存为长期真相：完整上下文正文、完整模型输出、敏感原文拼接结果。
- 缓存失效后必须能基于正式来源重新装配；缓存命中只提速，不改变结果语义。

### 9.4 失败恢复

典型恢复场景如下：

- 上下文装配成功但 `Agent OS` 调用失败：复用已落库 `AiContextEnvelope` 重试执行，不重新裁剪来源。
- 模型执行成功但护栏校验失败：保留原始结果摘要到 `ProtectedResultSnapshot` 用于审计，不直接展示；允许按修正后的护栏策略重放校验。
- 结果通过护栏但人工确认超时：`ia_ai_application_job` 保持 `WAITING_HUMAN_CONFIRMATION`，不自动降级为已放行；结果快照继续由 `ProtectedResultSnapshot` 承接。
- 来源对象被新版本替换：原结果标记为 `SUPERSEDED`，新任务必须基于新版本重装上下文。

## 10. 监控指标与验收口径

### 10.1 监控指标

- 吞吐：上下文装配受理数、完成数、失败数、被护栏拦截数、转人工确认数。
- 时延：装配耗时、证据裁剪耗时、护栏校验耗时、人工确认等待时长。
- 质量：证据覆盖率、无来源结论拦截率、冲突结果降级率、人工驳回率、回写放行率。
- 健康：预算超限率、缓存命中率、重跑率、来源失效导致的 `SUPERSEDED` 比例、`ProtectedResultSnapshot` 堆积量。

### 10.2 验收清单

- [ ] AI 应用只能消费合同主档、文档中心、`OCR`、搜索、条款库 / 模板库和任务意图等正式受控来源。
- [ ] 任一输出结论都能回指到稳定 `evidence_segment_id` 和正式对象锚点。
- [ ] 预算不足时系统会按既定顺序降级或拒答，而不是静默截断证据。
- [ ] 单片段阈值、证据覆盖率阈值、结论差异阈值都由 `AiOutputGuardrailProfile` 统一承接，可审计、可复用、可回放。
- [ ] 摘要、问答、风险识别、比对提取共用统一装配骨架，但各自输出 schema 和确认规则清晰可区分。
- [ ] 无来源结论、敏感信息越权、冲突证据未消解的结果会被拦截、降级或转人工确认。
- [ ] 护栏失败或等待人工确认的结果由 `ProtectedResultSnapshot` 承接，而不是发明新的正式结果状态。
- [ ] 通过护栏和人审的结果才允许进入受控回写链路。
- [ ] 上下文装配、护栏决策、确认状态、回写放行全链路可审计、可重放、可恢复。

## 11. 小结

本文把 AI 业务应用的“直接调模型”收口为“受控输入装配 + 证据绑定 + 输出护栏 + 人审放行”四段式主链：

- 输入只来自正式来源和稳定派生结果
- 上下文按统一骨架装配、裁剪、预算和降级
- 输出先做 schema、引用、敏感信息和冲突校验
- 高风险结果必须经人工确认后才能进入业务正式链路

该设计与 `intelligent-applications` 主线既有 `Detailed Design`、`OCR` 专项设计、搜索专项设计保持一致，为后续摘要、问答、风险识别、比对提取和受控回写提供统一输入输出护栏。

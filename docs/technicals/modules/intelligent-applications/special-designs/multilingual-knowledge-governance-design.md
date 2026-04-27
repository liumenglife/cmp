# 检索 / OCR / AI 业务应用主线专项设计：多语言知识治理

## 1. 文档说明

本文档用于继续下沉 `intelligent-applications` 主线中"条款 / 模板多语言内容治理流程与术语库维护流程"能力，重点回答以下问题：

- 多语言知识的输入来源如何与主线各层（`OCR`、搜索、AI 上下文装配、候选排序）形成稳定语言契约
- 术语库（`TermEntry`）和翻译单元（`TranslationUnit`）如何定义、版本化、审核与发布
- 语言归一策略、冲突消解与回退如何与 `ia_i18n_context` 正式模型对齐
- 权限、审计、幂等、失败恢复与验收口径如何闭环

本文是以下文档的下游专项设计：

- [`intelligent-applications Detailed Design`](../detailed-design.md)

本文不展开以下内容：

- 不写 `OCR` 引擎适配、版面解析的详细设计（见 `ocr-engine-and-layout-analysis-design.md`）
- 不写搜索索引结构与重建策略（见 `search-index-and-rebuild-design.md`）
- 不写 AI 上下文装配模板与护栏（见 `ai-context-assembly-and-output-guardrails-design.md`）
- 不写候选排序与质量评估（见 `candidate-ranking-and-quality-evaluation-design.md`）
- 不写结果回写冲突优先级（见 `result-writeback-and-conflict-resolution-design.md`）
- 不写 UI 多语言 i18n 展示实现、前端语言包构建

## 2. 目标与边界

### 2.1 设计目标

- 建立可版本化、可审核、可发布的术语库和翻译单元，作为主线各层共享的语言知识底座
- 保证跨语言候选（`OCR` 片段、搜索结果、条款/模板内容、AI 输出）在进入候选排序和上下文装配前已完成语言归一
- 让语言归一策略、术语版本和翻译状态可追踪、可回放，不出现"同一任务前后术语解释不一致"的问题
- 明确多语言知识层与各专项的契约边界，下游只消费稳定术语快照，不直接改术语库

### 2.2 边界原则

- 术语库和翻译单元是本主线内的知识资源，不替代合同主档的业务字段、不替代条款库的正式法律文本。
- `OCR`、搜索、AI 上下文装配、候选排序只通过 `ia_i18n_context.terminology_profile_code` 引用已发布术语快照，不直接读写术语库原始数据。
- 多语言内容治理只涉及中文、英文、西文三种正式支持语言（见决策 8）；其他语言作为降级处理，不进入正式发布流程。
- 术语审核与发布只能由授权操作人通过正式入口完成，不允许 AI 结果自动回写到术语库正式版本。

## 3. 输入来源与依赖

### 3.1 多语言知识输入来源

| 来源 | 对象 | 关键字段 | 角色 |
|---|---|---|---|
| 条款库 | `cc_clause_version` | `language_code`、`content`、`effective_at` | 正式多语言法律文本来源 |
| 模板库 | `cc_template_version` | `language_code`、`template_body` | 正式多语言模板来源 |
| `OCR` 语言片段 | `OcrLanguageSegment` | `language_code`、`normalized_language`、`confidence_score` | 文件语言识别输入 |
| 搜索语言标记 | `ia_search_result_set` 查询上下文 | `matched_language_json` | 查询语言归一输入 |
| AI 任务语言上下文 | `ia_i18n_context` | `source_language`、`normalized_language`、`response_language` | 任务级语言归一与术语快照绑定 |
| 人工维护入口 | 术语库管理后台 | 操作人、审核状态、发布版本 | 术语与翻译单元正式来源 |

### 3.2 依赖能力

- 条款库：提供正式多语言条款版本，版本状态变更后通知术语库同步核查相关术语引用。
- 模板库：提供正式多语言模板版本，同上。
- `OCR` 主链：通过 `OcrLanguageSegment` 提供文件语言归因，作为语言识别的输入来源之一。
- 候选排序专项：通过 `terminology_profile_code` 消费已发布术语快照，进行语言一致性约束（见 `candidate-ranking-and-quality-evaluation-design.md`）。
- AI 上下文装配：通过 `ia_i18n_context` 引用已发布术语版本，不直接消费原始术语库。
- 权限体系：管理术语库的读写权限、审核权限和发布权限。
- 审计中心：承接术语创建、修改、审核、发布、失效和版本切换事件。

## 4. 术语与翻译单元模型

### 4.1 `TermEntry`

`TermEntry` 是术语库的基础单元，表达一个跨语言的标准术语条目。

核心字段：

| 字段 | 说明 |
|---|---|
| `term_entry_id` | 术语条目主键 |
| `term_key` | 术语唯一标识键，业务层引用入口 |
| `domain` | 术语领域：`CONTRACT`、`CLAUSE`、`RISK`、`LEGAL_GENERAL` |
| `status` | `DRAFT`、`REVIEW`、`PUBLISHED`、`DEPRECATED` |
| `canonical_language` | 权威语言，默认 `zh-CN` |
| `created_by` | 创建人 |
| `published_at` | 最近发布时间 |
| `version_no` | 当前发布版本号 |

### 4.2 `TranslationUnit`

`TranslationUnit` 表达一个 `TermEntry` 在某语言下的翻译文本和审核状态。

核心字段：

| 字段 | 说明 |
|---|---|
| `translation_unit_id` | 翻译单元主键 |
| `term_entry_id` | 归属术语条目 |
| `language_code` | 目标语言，如 `zh-CN`、`en-US`、`es-ES` |
| `surface_form` | 标准展示文本 |
| `alt_forms_json` | 可接受变体列表（别名、缩写） |
| `status` | `DRAFT`、`REVIEW`、`APPROVED`、`DEPRECATED` |
| `reviewed_by` | 最近审核人 |
| `reviewed_at` | 最近审核时间 |
| `version_no` | 当前版本号 |
| `superseded_by_id` | 被替代时的目标翻译单元 |

规则：

- 每个 `TermEntry` 在每种正式语言下只允许一个 `APPROVED` 状态的 `TranslationUnit`。
- 不允许在没有审核通过的 `TranslationUnit` 的情况下发布 `TermEntry`。
- `canonical_language` 对应的 `TranslationUnit` 必须是发布先决条件。

### 4.3 `TerminologyProfile`

`TerminologyProfile` 是可发布的术语快照配置，供下游任务链路通过 `terminology_profile_code` 引用。

核心字段：

| 字段 | 说明 |
|---|---|
| `profile_code` | 快照标识码，如 `CONTRACT_BASELINE_V2` |
| `profile_version` | 快照版本号 |
| `domain_filter` | 适用领域过滤 |
| `language_scope_json` | 包含的语言列表 |
| `included_term_keys_json` | 包含的 `term_key` 集合（或策略：全量/按域） |
| `published_at` | 快照发布时间 |
| `status` | `DRAFT`、`PUBLISHED`、`DEPRECATED` |

规则：

- 下游（`OCR`、搜索、AI 上下文装配、候选排序）只能引用 `PUBLISHED` 状态的 `TerminologyProfile`。
- 同一任务运行过程中，`terminology_profile_code + profile_version` 必须固化为不可变引用。
- 术语库更新不自动触发已运行任务的重算，只触发后续新任务使用新快照。

### 4.4 `ia_i18n_context` 对齐

本专项设计中的术语治理结果通过 `ia_i18n_context.terminology_profile_code` 落到正式模型，覆盖 `OCR` 结果、搜索查询、AI 任务 / 结果、问答会话等所有 `owner_type`。

`ia_i18n_context` 正式字段见父文档 `detailed-design.md §4.11`，本专项不新增字段，只收口使用规则：

- `terminology_profile_code` 必须引用 `PUBLISHED` 状态的 `TerminologyProfile`。
- `i18n_status=APPLIED` 时才允许 AI 上下文装配和候选排序消费该上下文的术语归一结果。
- `i18n_status=FAILED` 时，下游必须降级为不使用术语归一，而不能伪造术语命中。

## 5. 版本治理

### 5.1 版本状态机

`TermEntry` 与 `TranslationUnit` 共享相同的状态机：

```text
DRAFT -> REVIEW -> APPROVED/PUBLISHED
APPROVED/PUBLISHED -> DEPRECATED
DEPRECATED -> （不可逆）
```

`TerminologyProfile`：

```text
DRAFT -> PUBLISHED -> DEPRECATED
```

规则：

- `TermEntry` 只有在至少 `canonical_language` 的 `TranslationUnit` 为 `APPROVED` 时，才允许发布。
- `TermEntry` 发布时，所有关联 `APPROVED` 的 `TranslationUnit` 自动归入当前版本快照。
- 旧版本发布后的 `TerminologyProfile` 不受新版本术语更新影响；已使用旧快照的任务结果不回改。

### 5.2 变更触发重建

以下事件触发 `TerminologyProfile` 评估更新：

- `TermEntry` 状态变为 `PUBLISHED` 或 `DEPRECATED`
- 任意 `TranslationUnit` 状态变为 `APPROVED` 或 `DEPRECATED`
- 条款库/模板库发布新版本且涉及语言变更

评估结果：

- 若变更影响当前 `PUBLISHED` 快照，运营人员可选择发布新版本快照；新任务自动使用新快照。
- 若不发布新快照，当前快照继续有效，不强制触发存量任务重跑。

### 5.3 幂等与版本快照固化

- 每次新建 `ia_i18n_context` 时，必须同时固化当时有效的 `terminology_profile_code + profile_version`。
- 同一 `owner_type + owner_id` 的 `ia_i18n_context` 只能有一条有效记录（唯一约束 `uk_i18n_owner`）。
- 重试或重建场景下，若 `terminology_profile_code` 已固化，不允许漂移到更新版本；若需要使用新版本，必须显式创建新任务而不是复用旧上下文。

## 6. 语言归一策略

### 6.1 语言识别优先级

归一语言按以下优先级确定：

1. 任务显式指定的目标语言（`response_language`）
2. `OCR` 语言片段的主语言（按文本占比最高的 `language_code`）
3. 条款库/模板库版本的 `language_code`
4. 系统默认语言 `zh-CN`
   - 系统默认归一语言与 `TermEntry.canonical_language` 是两个独立概念：前者是运行时语言识别失败时的兜底，后者是术语条目的权威法律语言；二者当前默认值均为 `zh-CN` 属于巧合，不可混用语义。

### 6.2 混语处理

- 混语文档（如中英混排合同）：按语言片段分别记录 `source_language`，归一到 `normalized_language` 时选择权威语言。
- 若无法确定权威语言（各语言占比相近），默认归一为 `zh-CN` 并设置 `language_confidence < 0.7`，触发候选排序的语言一致性降分规则。
- AI 上下文装配必须感知混语标记，在构造上下文时保留各片段原始语言标记，不强行合并为单一语言。

### 6.3 翻译降级策略

- 若目标语言无 `APPROVED` 的 `TranslationUnit`，降级到 `canonical_language`（`zh-CN`）并标记 `translation_needed_flag=true`。
- 不允许使用 AI 生成结果直接替代正式审核术语；AI 可以提供翻译建议但必须走审核流程。
- 术语降级不影响任务正常推进，但必须在 `ia_i18n_context` 中留下降级标记，下游可据此调整输出语言。

## 7. 审核与发布流程

### 7.1 术语新增流程

1. 操作人通过管理后台提交 `TermEntry`（`status=DRAFT`）和 `canonical_language` 的 `TranslationUnit`（`status=DRAFT`）。
2. 操作人（或自动）为各正式语言补充 `TranslationUnit`（`status=DRAFT`）。
3. 提交审核，`TermEntry.status=REVIEW`，所有关联 `TranslationUnit.status=REVIEW`。
4. 审核人逐一审批 `TranslationUnit`（`status=APPROVED` 或打回 `DRAFT`）。
5. 所有正式语言的 `TranslationUnit` 均 `APPROVED` 后，审核人发布 `TermEntry`（`status=PUBLISHED`）。
6. 触发 `TerminologyProfile` 评估更新。

### 7.2 术语修订流程

- 修订时新建一个 `TranslationUnit` 副本（`status=DRAFT`），原版本保持 `APPROVED` 继续有效，直到修订版本 `APPROVED` 后替代原版本（`superseded_by_id` 标记）。
- `TermEntry.version_no` 在每次正式发布后递增。
- 修订不回改已固化在历史任务中的术语快照。

### 7.3 术语废弃流程

- `TermEntry.status=DEPRECATED` 后，所有关联 `TranslationUnit` 同步标记 `DEPRECATED`。
- 引用该术语的历史任务结果不受影响，但后续新任务的快照不再包含该术语。
- 废弃操作必须检查当前是否有正在运行中的任务引用了该术语所在快照，若有则必须等待这些任务完成或手动确认后再废弃。

### 7.4 权限边界

| 操作 | 最低权限要求 |
|---|---|
| 提交术语草稿 | 术语库编辑权限 |
| 提交审核 | 术语库编辑权限 |
| 审核通过/打回 | 术语库审核权限 |
| 发布 `TermEntry` | 术语库发布权限 |
| 发布 `TerminologyProfile` | 术语库发布权限 |
| 废弃术语 | 术语库发布权限 + 二次确认 |

## 8. 上下游契约

### 8.1 与 OCR 专项的契约

- `OCR` 主链通过 `OcrLanguageSegment` 提供每个文本片段的 `language_code`，作为语言识别的基础输入。
- 多语言治理层消费 `language_code`，但不回写 `OcrLanguageSegment`；如需修正，通过管理后台新建术语条目。
- `OCR` 结果创建时，多语言治理层为对应 `ia_i18n_context` 选择并固化 `terminology_profile_code`。

### 8.2 与搜索专项的契约

- 搜索查询受理时，多语言治理层为 `SEARCH_QUERY` 类型的 `ia_i18n_context` 分配归一语言和术语快照。
- 搜索候选结果集合中，各候选对象的 `normalized_language` 须与查询归一语言一致，否则降分（见候选排序专项）。
- 搜索索引重建时，若 `TerminologyProfile` 发布新版本，可选择触发相关语言字段的增量索引刷新。

### 8.3 与 AI 上下文装配专项的契约

- AI 任务创建时，多语言治理层为 `AI_JOB` 类型的 `ia_i18n_context` 分配归一语言和术语快照；`terminology_profile_code + profile_version` 固化后不允许在任务执行期间变更。
- `AiContextEnvelope` 中的证据片段和条款/模板引用须携带 `language_code`，AI 上下文装配层不替代多语言治理层的术语校验。
- AI 输出结果中的术语使用须与任务的 `terminology_profile_code` 一致；护栏层负责拦截使用了未在快照中的术语或翻译错误的输出。

### 8.4 与候选排序专项的契约

- 候选排序层通过 `ia_i18n_context` 引用 `terminology_profile_code`，作为语言一致性打分的基础；`CandidateRankingProfile` 不直接持有 `terminology_profile_code`。
- 语言一致性约束由候选排序专项执行，多语言治理层只提供术语快照，不直接参与候选打分计算。
- 若 `terminology_profile_code` 在快照中不存在某候选的 `normalized_language` 对应术语，候选必须降分而不是报错中断。

### 8.5 与结果回写的契约

- AI 结果回写到合同视图时，`ia_writeback_record` 中的字段值必须使用当前任务 `terminology_profile_code` 对应的标准术语。
- 术语不一致导致的回写冲突，按回写冲突优先级规则处理（见 `result-writeback-and-conflict-resolution-design.md`），不在本文展开。

## 9. 冲突消解与回退

### 9.1 术语版本冲突

- 若同一 `TermEntry` 存在多个候选的 `TranslationUnit`（如审核中的新版本与已发布的旧版本并存），下游只消费已 `APPROVED/PUBLISHED` 状态的版本，不消费 `DRAFT/REVIEW` 版本。
- 若已发布版本被废弃但尚未有新版本发布，降级策略见 §6.3。

### 9.2 语言归一冲突

- 若同一内容片段的 `source_language` 与 `normalized_language` 不一致（如英文片段但归一为中文），必须在 `ia_i18n_context.segment_language_payload_json` 中保留原始语言映射，供下游审计和人工复核。
- 不允许静默丢弃语言冲突信息；若下游任务无法处理混语情况，必须记录为 `i18n_status=FAILED` 并走降级路径。

### 9.3 缓存回退

- `TerminologyProfile` 的快照内容可以按 `profile_code + profile_version` 做读缓存，TTL 按业务配置，默认不超过 1 小时。
- 缓存失效时，回退到直接读取数据库正式记录，不允许使用过期缓存或本地存根替代正式快照。

## 10. 失败恢复

### 10.1 语言识别失败

- 若 `OCR` 语言片段置信度低于阈值（由 `OcrQualityProfile` 配置），标记该片段 `language_code=UNKNOWN`。
- 多语言治理层遇到 `UNKNOWN` 语言时，不选择术语，直接为 `ia_i18n_context` 设置 `i18n_status=FAILED`，后续任务使用降级路径。

### 10.2 术语快照加载失败

- 若 `terminology_profile_code` 对应的快照不存在或 `status != PUBLISHED`，返回空术语集并记录失败原因；下游任务必须感知空术语集并按降级策略执行。
- 不允许回退到任意历史版本快照（防止使用已废弃术语）；只允许使用明确 `PUBLISHED` 状态的版本。

### 10.3 审核流程中断

- 若审核人未在规定时间内完成审核，`TermEntry` 保持 `REVIEW` 状态；系统不自动超时发布。
- 若审核全部被打回，`TermEntry` 回退到 `DRAFT`，等待重新提交。

## 11. 监控指标

- **覆盖率指标**：已发布 `TermEntry` 数、各语言 `APPROVED` 覆盖率、未覆盖语言的条款数量。
- **时延指标**：术语从草稿到发布的平均周期、审核等待时长。
- **质量指标**：`i18n_status=FAILED` 比率、语言降级发生率、术语空命中率。
- **健康指标**：`TerminologyProfile` 发布频率、缓存命中率、已废弃但仍被引用的术语数量。

## 12. 审计要求

以下事件必须写入审计中心：

- `TermEntry` 创建、提交审核、审核通过/打回、发布、废弃
- `TranslationUnit` 创建、修改、审核通过/打回、废弃、被替代
- `TerminologyProfile` 创建、发布、废弃
- `ia_i18n_context` 创建、语言归一失败、术语快照加载失败、降级触发

审计字段至少包含：`operator_type`（`HUMAN` / `SYSTEM`）、`operator_id`、`action`、`target_type`、`target_id`、`profile_code + profile_version`（如适用）、`trace_id`、`timestamp`。

## 13. 验收清单

- [ ] 可以通过 `term_key` 查询到 `PUBLISHED` 状态的 `TermEntry` 及各语言的 `APPROVED TranslationUnit`。
- [ ] 新建 `ia_i18n_context` 时，能够正确分配 `terminology_profile_code`，且快照为 `PUBLISHED` 状态。
- [ ] 当 `TermEntry` 发布后，后续新建的 `ia_i18n_context` 能够引用到包含该术语的最新快照。
- [ ] 废弃 `TermEntry` 后，新任务的 `terminology_profile_code` 不包含已废弃术语；历史任务不受影响。
- [ ] 混语文档的 `ia_i18n_context` 中 `segment_language_payload_json` 能正确保留各片段语言归因。
- [ ] `i18n_status=FAILED` 场景下，下游任务均通过降级路径执行，不报错中断。
- [ ] 审计记录能追溯到 `TermEntry` 的完整生命周期，包含操作人、时间和版本号。
- [ ] `TerminologyProfile` 缓存失效后，系统能正确回退到数据库读取，不使用过期缓存。
- [ ] AI 输出护栏层能正确识别违反 `terminology_profile_code` 规定的术语用法，触发 `BLOCK` 或 `REVIEW_REQUIRED`。
- [ ] `terminology_profile_code + profile_version` 在同一任务生命周期内保持不可变，不漂移。

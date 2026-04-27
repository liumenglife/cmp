# 检索 / OCR / AI 业务应用主线专项设计：结果回写与冲突消解

## 1. 文档说明

本文档用于继续下沉 `intelligent-applications` 主线中"结果回写到合同侧各视图的字段级映射和冲突优先级"能力，重点回答以下问题：

- AI 应用结果（摘要、风险分析、比对提取）如何以受控方式投影到合同主档的三个关联视图
- 每个目标视图的字段级映射如何定义，哪些字段允许写入、哪些禁止写入
- 同一视图槽位出现多份候选结果时，冲突如何判定、优先级如何排序、冲突如何消解
- `ia_writeback_record` 正式模型如何承接全部回写动作，以及回写全生命周期如何落库
- 与 AI 结果层、候选排序层、质量评估层、AI 输出护栏层、人工确认层的契约边界如何收口
- 并发幂等、失败恢复、审计与验收口径如何闭环

本文是以下文档的下游专项设计：

- [`intelligent-applications Detailed Design`](../detailed-design.md)

本文不展开以下内容：

- 不写 OCR 引擎适配、搜索索引结构、AI 上下文装配模板或 Prompt 正文
- 不写候选排序规则、质量评估维度和护栏校验逻辑的完整正文
- 不写多语言术语治理、运维监控面板、告警阈值和恢复脚本的完整正文
- 不写合同管理本体的受控写接口内部实现、DDL 或合同主字段校验逻辑

## 2. 目标与边界

### 2.1 设计目标

- 把 AI 应用结果的回写动作收口为统一、可审计、可回滚的受控写链路，不允许各应用类型各自直写合同视图
- 定义三个目标视图的字段级写入白名单，保证 AI 输出只更新其有能力解释的字段，不覆盖合同业务真相
- 建立冲突判定与优先级规则体系，处理同视图多候选、版本不一致、置信度差异和人工否决等情况
- 让回写记录能追踪到 AI 结果、候选排序快照、质量评估报告、护栏决策和人工确认结果的全链路锚点
- 让回写失败可重放、回写冲突可解释、回写审计可追溯

### 2.2 边界原则

- 合同主档是业务真相源。回写只更新关联视图的引用型字段和辅助字段，不直接改写合同主键字段（合同编号、名称、相对方、主状态）。
- 文档中心是文件真相源。回写不涉及文档版本切换、文件对象改写或文件内容更新。
- AI 结果是派生结果。回写只把已通过护栏且必要时已通过人工确认的结果投影到合同视图，不把 AI 中间输出、调试载荷或未放行结果写入正式视图。
- 回写入口统一走 `ia_writeback_record`。不绕过合同管理本体的受控写接口直接操作合同视图表。
- 回写只做投影，不做决策。最终是否采纳回写内容由合同管理本体和人工确认链路共同决定，回写层不替代业务审批。
- 章节级设计如需要配合调整父文档中的既有结构，应将变更项同步到对应文档，不在本专项中留待补标记或未决占位。

## 3. 回写目标视图与受控写入口

### 3.1 三个目标视图

回写目标严格限定为以下三个合同侧视图：

| 视图 | 视图标识 | 写入权限 | 来源 AI 应用类型 | 写入频率 |
| --- | --- | --- | --- | --- |
| 合同摘要视图 | `CONTRACT_SUMMARY` | 只写摘要引用与摘要正文快照 | `SUMMARY` | 每个合同只保留一个当前推荐摘要；新摘要可覆盖旧摘要引用 |
| 合同风险视图 | `CONTRACT_RISK_VIEW` | 只写风险项引用与风险快照 | `RISK_ANALYSIS` | 每次风险分析可新增风险项；旧风险项可标记为已失效但不删除 |
| 合同提取结果视图 | `CONTRACT_EXTRACTION_VIEW` | 只写字段提取结果引用与提取快照 | `DIFF_EXTRACTION` | 同一字段可存多份候选；当前推荐值只有一个 |

每个视图对应合同管理本体的一个受控写入口。`result-writeback-gateway` 不持有视图表的所有权，只通过合同管理本体的受控写接口提交写请求。

### 3.2 受控写入口

合同管理本体暴露以下受控写入口供回写网关调用：

- `write_contract_summary_reference(contract_id, summary_reference, operator_context)`
- `write_contract_risk_items(contract_id, risk_item_list, operator_context)`
- `write_contract_extraction_fields(contract_id, extraction_field_list, operator_context)`
- `mark_contract_view_items_superseded(contract_id, view_type, superseded_id_list, operator_context)`

每个入口内部的校验、版本号推进和审计由合同管理本体负责。回写网关提交前必须校验 `writeback_allowed_flag=true`、`release_decision in (PUBLISH, PARTIAL_PUBLISH)` 且人工确认已完成（如适用）。

### 3.3 禁止写入清单

以下字段在任何回写动作中一律不得写入合同主档或关联视图：

- 合同编号、合同名称、合同相对方、合同金额、合同起止日期等合同主键级字段
- 合同主状态、审批状态、签署状态、归档状态等业务状态字段
- 文档中心版本号、文档分类、文档权限、文档加密状态等
- 条款正文、模板正文、条款生效状态、模板启用状态等
- AI 模型标识、Provider 标识、Prompt 版本号、推理中间输出、调试信息等
- 任何超出当前调用方权限范围的越权字段

## 4. 字段级映射

### 4.1 CONTRACT_SUMMARY 字段映射

来源：`ia_summary_result`，经 `ia_ai_application_result` 聚合后回写。

| 目标视图字段 | 来源字段 | 映射规则 |
| --- | --- | --- |
| `summary_reference_id` | `ia_writeback_record.writeback_record_id` | 回写成功后回填到视图 |
| `summary_text` | `ia_summary_result.summary_text` | 全文透传；若为 `PARTIAL_PUBLISH` 结果，前缀标注缺口说明 |
| `summary_scope` | `ia_summary_result.summary_scope` | 透传枚举值 |
| `section_list_json` | `ia_summary_result.section_payload_json` | 透传结构化分段摘要 |
| `citation_reference_json` | `ia_summary_result.citation_payload_json` | 透传引用锚点列表 |
| `display_language` | `ia_summary_result.display_language` | 透传 |
| `summary_digest` | `ia_summary_result.summary_digest` | 透传 |
| `ranking_snapshot_id` | `ia_ai_application_result.ranking_snapshot_id` | 审计追溯锚点 |
| `quality_evaluation_id` | `ia_ai_application_result.quality_evaluation_id` | 审计追溯锚点 |
| `guardrail_decision` | `ia_ai_application_result.guardrail_decision` | 放行结论 |
| `written_at` | `ia_writeback_record.completed_at` | 回写完成时间 |

额外约束：

- 若该合同已存在一个 `CURRENT` 状态的摘要引用，新回写前必须先将旧引用标记为 `SUPERSEDED`。
- `PARTIAL_PUBLISH` 的摘要回写时，必须在 `summary_text` 前缀附加 `[缺口说明] <reason_code_list>`，不得伪装为完整结果。
- 同一合同同一 `ranking_snapshot_id` 的摘要不允许重复回写为两个 `CURRENT` 引用（幂等去重）。

### 4.2 CONTRACT_RISK_VIEW 字段映射

来源：`ia_risk_analysis`，经 `ia_ai_application_result` 聚合后回写。

| 目标视图字段 | 来源字段 | 映射规则 |
| --- | --- | --- |
| `risk_reference_id` | `ia_writeback_record.writeback_record_id` | 回写成功后回填到视图 |
| `risk_level` | `ia_risk_analysis.risk_level` | 透传枚举值 |
| `risk_item_list_json` | `ia_risk_analysis.risk_item_payload_json` | 透传风险项列表 |
| `clause_gap_summary_json` | `ia_risk_analysis.clause_gap_payload_json` | 透传条款差距分析 |
| `recommendation_summary_json` | `ia_risk_analysis.recommendation_payload_json` | 透传建议动作 |
| `evidence_reference_json` | `ia_risk_analysis.evidence_payload_json` | 透传证据引用列表 |
| `requires_manual_review` | `ia_risk_analysis.requires_manual_review` | 透传；回写前已确认则该字段为 `false` |
| `ranking_snapshot_id` | `ia_ai_application_result.ranking_snapshot_id` | 审计追溯锚点 |
| `quality_evaluation_id` | `ia_ai_application_result.quality_evaluation_id` | 审计追溯锚点 |
| `guardrail_decision` | `ia_ai_application_result.guardrail_decision` | 放行结论 |
| `written_at` | `ia_writeback_record.completed_at` | 回写完成时间 |

额外约束：

- 每条风险项由 `risk_item_id`（取自 `ia_risk_analysis.risk_item_payload_json` 内各条目）唯一标识。同一 `risk_item_id` 的新回写替代旧值，旧值标记为 `SUPERSEDED`。
- `risk_level=HIGH` 的风险项回写前必须已完成人工确认，`human_confirmation_status` 必须为已放行。
- `PARTIAL_PUBLISH` 的风险结果回写时，必须标注各风险项的发布状态（`FULL` / `PARTIAL` / `WITHHELD`），不发布的条目不入视图。

### 4.3 CONTRACT_EXTRACTION_VIEW 字段映射

来源：`ia_diff_extraction_result`，经 `ia_ai_application_result` 聚合后回写。

| 目标视图字段 | 来源字段 | 映射规则 |
| --- | --- | --- |
| `extraction_reference_id` | `ia_writeback_record.writeback_record_id` | 回写成功后回填到视图 |
| `comparison_mode` | `ia_diff_extraction_result.comparison_mode` | 透传 |
| `extracted_field_list_json` | `ia_diff_extraction_result.extracted_field_payload_json` | 透传字段提取结果 |
| `clause_match_summary_json` | `ia_diff_extraction_result.clause_match_payload_json` | 透传条款对齐结果 |
| `diff_summary_json` | `ia_diff_extraction_result.diff_payload_json` | 透传差异对照结果 |
| `confidence_summary_json` | `ia_diff_extraction_result.confidence_payload_json` | 透传各字段置信度 |
| `requires_manual_review` | `ia_diff_extraction_result.requires_manual_review` | 透传；回写前已确认则为 `false` |
| `ranking_snapshot_id` | `ia_ai_application_result.ranking_snapshot_id` | 审计追溯锚点 |
| `quality_evaluation_id` | `ia_ai_application_result.quality_evaluation_id` | 审计追溯锚点 |
| `guardrail_decision` | `ia_ai_application_result.guardrail_decision` | 放行结论 |
| `written_at` | `ia_writeback_record.completed_at` | 回写完成时间 |

额外约束：

- 提取结果中每个独立字段（金额、日期、主体名等）由字段路径唯一标识。同一字段路经只保留一个当前推荐值。
- 低置信度字段（`confidence < extraction_confidence_threshold`，阈值由 `CandidateQualityProfile.coverage_threshold_json` 内的字段级置信度下限配置决定，通过 `quality_profile_code` 引用）回写时必须标记为 `LOW_CONFIDENCE` 并禁止自动进入默认展示，只能作为辅助参考。
- 若提取结果来自 `PARTIAL_PUBLISH` 质量分层，缺失字段必须显式标注 `EXTRACTION_FAILED` 或 `OUT_OF_SCOPE`，不可留空伪装完整。

## 5. 冲突判定与优先级

### 5.1 冲突场景分类

回写冲突统一分为四类：

| 冲突类型 | 触发条件 | 消解原则 |
| --- | --- | --- |
| 版本冲突 | 回写时目标视图的 `target_snapshot_version` 与数据库当前版本不一致 | 以数据库当前版本为准；旧回写进入 `FAILED` 或 `SKIPPED` |
| 同槽位写入冲突 | 同一目标视图同一槽位（同一摘要位、同一 `risk_item_id`、同一字段路径）同时存在多个 `PENDING` 或 `WRITING` 状态的回写 | 按优先级规则自动裁决；被淘汰者进入 `SKIPPED` |
| 置信度冲突 | 新回写结果的置信度低于当前已落库结果的置信度 | 新结果不覆盖；进入 `SKIPPED` 并记录冲突码 |
| 人工否决冲突 | 新回写结果尚未通过人工确认但试图覆盖已人工确认的结果 | 新结果阻断；进入 `FAILED` |

### 5.2 同槽位写入优先级

当同一槽位存在多个候选回写时，按以下优先级排序：

1. **人工确认状态**：已人工确认放行的结果（`human_confirmation_status=APPROVED`）优先于未人工确认的结果。
2. **质量分层**：`release_decision=PUBLISH` 优先于 `release_decision=PARTIAL_PUBLISH`。
3. **结果生成时间**：同质量分层下，最新生成的 `ia_ai_application_result` 优先（`created_at` 降序）。
4. **候选排序快照版本**：比较两个 `ranking_snapshot_id` 对应快照的 `source_digest` 中来源类型集合（`source_type` 种类数），种类更多者优先；若种类数相等，取该快照中引用的文档版本号 `document_version_id` 更新者优先；若文档版本也相等，取快照 `assembled_at` 更晚者优先。
5. **护栏判定**：`guardrail_decision=PASS` 优先于 `guardrail_decision=PASS_PARTIAL`。
6. **源置信度**：同以上条件均相等时，按应用类型取对应聚合置信度分，得分高者优先：
   - `SUMMARY`：`ia_ai_application_result.structured_payload_json.summary_confidence`（如该路径不存在则取 `QualityEvaluationReport.publishability_score`）
   - `RISK_ANALYSIS`：`ia_risk_analysis.risk_item_payload_json` 中各风险项置信度的算数均值
   - `DIFF_EXTRACTION`：`ia_diff_extraction_result.confidence_payload_json` 中各字段置信度的算数均值

当优先级规则均无法裁决时（得分相同、来源相同、时间相近），系统不自动选择，将冲突升级，同时保留所有候选并记录 `conflict_code=UNRESOLVED_EQUAL`，等待人工裁决。

### 5.3 冲突码枚举

`ia_writeback_record.conflict_code` 统一使用以下枚举：

| 冲突码 | 含义 | 触发场景 |
| --- | --- | --- |
| `NO_CONFLICT` | 无冲突 | 正常回写成功 |
| `VERSION_CONFLICT` | 版本冲突 | `target_snapshot_version` 落后于数据库当前版本 |
| `SLOT_OCCUPIED` | 槽位已被更高优先级回写占用 | 优先级规则裁决淘汰 |
| `SUPERSEDED_BY_NEWER` | 被更新回写替代 | 同槽位已存在更新结果 |
| `CONFIDENCE_LOWER` | 新回写置信度低于现有值 | 低置信度结果无法覆盖高置信度结果 |
| `HUMAN_REJECTED` | 结果已被人工否决 | 已人工驳回的结果不得再次回写 |
| `UNRESOLVED_EQUAL` | 无法自动裁决 | 多个候选得分相同且无法自动区分 |
| `WRITE_GATE_DENIED` | 受控写入口拒绝 | 合同管理本体校验失败 |

### 5.4 冲突升级与人工介入

以下情况必须升级为人工介入，禁止自动裁决：

- 风险等级为 `HIGH` 的风险项出现槽位冲突
- 提取结果中涉及金额、主体、日期的字段出现置信度冲突
- 同一 `UNRESOLVED_EQUAL` 冲突重复出现超过 2 次
- 回写目标为已归档合同的视图

人工介入由 `Agent OS` 人工确认底座承接，回写网关将冲突详情提交为确认单，等待人工裁决。裁决结果通过 `Agent OS` 回调写入 `ia_writeback_record.conflict_code` 并触发终态推进。

## 6. 回写流程与生命周期

### 6.1 回写触发条件

回写任务由以下事件触发：

- AI 应用任务（`ia_ai_application_job`）进入 `SUCCEEDED` 且关联结果满足放行条件
- 人工确认结果为 `APPROVE`
- 旧结果被显式标记为失效后的新结果补写
- 重试队列中恢复的失败回写

回写前置条件中，`ia_ai_application_result.written_back_status` 的 `NOT_REQUIRED` 状态具有最高优先阻断语义：若 AI 结果在创建时即被判定不需要回写（`written_back_status=NOT_REQUIRED`），后续不创建 `ia_writeback_record`，也不进入以下前置条件校验。该判定由护栏层或业务策略在结果生成时做出，不在回写网关层反转。

对于 `written_back_status != NOT_REQUIRED` 的结果，触发时校验以下前置条件，任一不满足则回写进入 `PENDING` 等待：

1. `ia_ai_application_result.result_status` 为 `READY` 或 `PARTIAL`
2. `ia_ai_application_result.writeback_allowed_flag = true`（由护栏专项在 `AiGuardedResult` 中设定）
3. `ia_ai_application_result.guardrail_decision in (PASS, PASS_PARTIAL)`
4. 若 `release_decision=PARTIAL_PUBLISH`，按应用类型分别校验：
   - `SUMMARY`：缺失事实/章节已在 `summary_text` 前缀和 `section_payload_json` 中显式标注缺口，不得伪装为完整摘要
   - `RISK_ANALYSIS`：`ia_risk_analysis.writeback_scope` 不为空（该字段仅 `RISK_ANALYSIS` 类型持有），且各风险项的发布状态（`FULL` / `PARTIAL` / `WITHHELD`）已明确标注
   - `DIFF_EXTRACTION`：缺失字段已在 `extracted_field_payload_json` 中显式标注 `EXTRACTION_FAILED` 或 `OUT_OF_SCOPE`
5. 若 `human_confirmation_required=true`，`human_confirmation_status` 必须为已放行
6. `ranking_snapshot_id` 和 `quality_evaluation_id` 均非空

### 6.2 回写生命周期

```text
PENDING → WRITING → WRITTEN
PENDING → SKIPPED
WRITING → FAILED → PENDING（重试有限次）
WRITING → FAILED（重试耗尽）
```

- `PENDING`：回写记录已创建，等待执行或等待前置条件满足。
- `WRITING`：正在调用合同管理本体受控写入口。
- `WRITTEN`：回写成功，目标视图已更新。
- `FAILED`：回写执行失败或受控写入口拒绝。失败原因为临时的（网络、锁）可重试；失败原因为永久的（版本冲突、权限拒绝、人工否决）不重试。
- `SKIPPED`：因冲突裁决被淘汰，不执行写动作。

`WRITTEN`、`SKIPPED` 和终态 `FAILED` 均为不可逆终态。

### 6.3 写前快照与写后校验

每次回写执行前：

1. 读取目标视图当前版本号，记为 `target_snapshot_version`。
2. 校验当前版本号与回写记录中预期版本号一致，不一致则判为 `VERSION_CONFLICT`。
3. 提交写请求时携带 `target_snapshot_version`，合同管理本体做乐观锁校验。

回写完成后（`WRITTEN`）：

1. 读取目标视图最新状态，校验写入值与回写请求一致。
2. 写入不一致则记录异常并触发告警，不回滚已落库的 `WRITTEN` 状态。
3. 更新 `ia_writeback_record.completed_at` 和 `writeback_status=WRITTEN`。
4. 同步更新 `ia_ai_application_result.written_back_status=WRITTEN` 和 `writeback_record_id`。

## 7. ia_writeback_record 正式模型对齐

### 7.1 字段职责收口

`ia_writeback_record` 的完整设计见父文档 `detailed-design.md §4.13`。本专项在父文档基线之上补充以下字段的使用规则：

| 字段 | 使用规则 |
| --- | --- |
| `writeback_record_id` | 回写记录主键，全链路唯一锚点 |
| `result_id` | 必须为 `ia_ai_application_result.result_id`，不为空。若回写被跳过（`SKIPPED`），仍需保留 `result_id` 以追踪被跳过的结果 |
| `target_type` | 严格限定为 `CONTRACT_SUMMARY`、`CONTRACT_RISK_VIEW`、`CONTRACT_EXTRACTION_VIEW` 之一 |
| `target_id` | 对应视图的记录主键；当 `writeback_action=UPSERT_REFERENCE` 时可为空（待写入口返回后回填） |
| `writeback_action` | `UPSERT_REFERENCE`：新增或更新摘要/风险/提取引用；`UPSERT_VIEW`：更新视图内具体条目；`MARK_SUPERSEDED`：标记旧条目失效 |
| `writeback_status` | 生命周期严格按 §6.2 推进 |
| `target_snapshot_version` | 写前目标视图版本号；用于乐观锁与版本冲突检测 |
| `conflict_code` | 使用 §5.3 枚举；`WRITTEN` 状态下为 `NO_CONFLICT`；`SKIPPED` 状态下必须非空 |
| `failure_reason` | `FAILED` 状态下记录详细失败原因；包含受控写入口返回的错误码与消息 |
| `operator_type` | `SYSTEM`：自动回写；`HUMAN`：人工确认后触发的回写 |
| `completed_at` | 回写终态时间（`WRITTEN`、`SKIPPED`、终态 `FAILED`） |

### 7.2 唯一约束语义

`uk_writeback_result_target(result_id, target_type, target_id)` 的唯一语义如下：

- 同一个 `result_id` 对同一个 `target_type + target_id` 只能生成一条回写记录。
- 如果 `target_id` 在回写执行前为空（`UPSERT_REFERENCE` 场景），回写成功回填 `target_id` 后唯一约束才生效。
- 若同一 `result_id` 需要对同一视图的不同槽位分别回写（如风险分析结果包含多个风险项分别写），每个槽位（由 `target_id` 区分）生成独立的 `writeback_record`。

### 7.3 关联对象的级联规则

`ia_writeback_record` 与以下正式模型保持一对多或一对一关联：

- `ia_writeback_record.result_id → ia_ai_application_result.result_id`：一个 AI 结果可产生多条回写记录（针对不同视图）。
- `ia_ai_application_result.writeback_record_id`：指向该结果最新的非 `SKIPPED` 回写记录（优先 `WRITTEN`，其次 `PENDING` / `WRITING`）。
- 审计事件载荷：每个回写记录的终态变更均生成一条审计事件，载荷包含 `writeback_record_id`、`result_id`、`ranking_snapshot_id`、`quality_evaluation_id`、`conflict_code`。

## 8. 与上下游模块的输入输出契约

### 8.1 与 AI 应用结果的契约

- 输入：`ia_ai_application_result` 的 `result_id`、`written_back_status`、`ranking_snapshot_id`、`quality_evaluation_id`、`guardrail_decision`；来自护栏层的运行时投影 `AiGuardedResult.writeback_allowed_flag`（非 `ia_ai_application_result` 物理列，而是护栏校验后生成的放行标记）。
- 输出：回写网关读取这些字段作为回写前置条件，不回写不满足放行条件的结果。
- `written_back_status=NOT_REQUIRED` 语义：护栏层或业务策略在结果生成时已判定该结果不需要回写，回写网关于入口处直接阻断，不创建 `ia_writeback_record`，也不发起任何写入动作。
- 约束：`written_back_status` 由回写网关维护其 `PENDING → WRITING → WRITTEN / FAILED` 转场；`NOT_REQUIRED` 终态由护栏层在结果生成时写入，回写网关不改变该状态。

### 8.2 与候选排序与质量评估的契约

- 输入：`CandidateRankingSnapshot` 的 `ranking_snapshot_id`、`source_digest`；`QualityEvaluationReport` 的 `quality_evaluation_id`、`release_decision`、`decision_reason_code_list`。
- 输出：回写网关消费这些对象作为审计追溯锚点和放行依据，不回写 `release_decision in (ESCALATE_TO_HUMAN, REJECT)` 的结果。
- 约束：`ranking_snapshot_id` 或 `quality_evaluation_id` 缺失的结果一律阻止回写（见 §6.1 前置条件）。

### 8.3 与 AI 输出护栏的契约

- 输入：`AiGuardedResult.guardrail_decision`、`AiGuardedResult.writeback_allowed_flag`、`AiGuardedResult.confirmation_required_flag`、`ProtectedResultSnapshot`（如适用）。
- 输出：回写网关只消费护栏放行后的结果；护栏要求人工确认的结果，必须在人工确认完成后才允许回写。
- 约束：护栏拦截（`BLOCK`）或拒绝（`REJECT`）的结果不得进入回写主链；已进入 `ProtectedResultSnapshot` 的结果仅在人工放行后才允许回写。
- 回写范围控制：护栏层如果在输出校验中发现“低置信度字段”“无来源结论”“冲突未消解”等问题并写入了 `AiGuardedResult.risk_flag_list`，回写网关会根据这些风险标记调整写入视图时的条目发布状态（`FULL` / `PARTIAL` / `WITHHELD`）。

### 8.4 与人工确认的契约

- 输入：`Agent OS` 人工确认结果（`APPROVE` / `REQUEST_CHANGES` / `REJECT`）、确认范围、确认意见。
- 输出：`APPROVE` 后回写网关将对应结果写入目标视图；`REQUEST_CHANGES` 或 `REJECT` 后对应回写记录进入 `FAILED` 且 `conflict_code=HUMAN_REJECTED`。
- 约束：人工确认对象归 `Agent OS` 管理；回写网关只读取确认结果快照，不读取人工确认底层私有表。

### 8.5 与合同管理本体的契约

- 输入：合同管理本体提供的受控写接口（见 §3.2）。
- 输出：回写网关向受控写接口提交写请求；写接口返回成功/失败/冲突/拒绝。
- 约束：回写网关不绕过受控写接口直写合同表；受控写接口内部校验（字段合法性、权限、乐观锁）失败时，回写网关记录为 `FAILED` 且 `conflict_code=WRITE_GATE_DENIED`。

## 9. 并发、幂等与乐观锁

### 9.1 并发控制

- 同一 `target_type + target_id` 的槽位在同一时刻只允许一个回写处于 `WRITING` 状态。
- 回写网关使用 `Redis` 短时互斥锁（键为 `lock:writeback:{target_type}:{target_id}`，TTL 不超过 30 秒）防止并发写。
- 获取锁失败时，回写保持 `PENDING` 并等待下一轮调度；超过最大等待次数后进入 `FAILED`。
- 合同管理本体受控写接口内部使用 `version_no` 乐观锁，双重保证不会出现丢失更新。

### 9.2 幂等规则

- 回写请求的幂等键为 `result_id + target_type + target_id`。
- 同一幂等键命中已有记录时，若该记录为终态（`WRITTEN`、`SKIPPED`、终态 `FAILED`），直接返回已有记录。
- 若该记录为中间态（`PENDING` 或 `WRITING`）且未超时，返回已有记录并等待；若已超时且仍在 `WRITING`，将其置为 `FAILED`（超时原因）并创建新回写记录。
- `target_id` 在回写执行前为空时，由 `result_id + target_type` 加 `writeback_action` 作为幂等键；回写成功后由数据库唯一约束兜底。

### 9.3 乐观锁校验

- 回写网关在发起写请求前，必须已获取 `target_snapshot_version`。
- 合同管理本体受控写接口以 `target_snapshot_version` 作为乐观锁入参，数据库执行 `UPDATE ... WHERE version_no = :target_snapshot_version`。
- 更新行数为 0 则判为版本冲突，回写记录进入 `FAILED`（`conflict_code=VERSION_CONFLICT`），不重试。

## 10. 失败恢复与补偿

### 10.1 失败分类与恢复策略

| 失败类型 | 恢复策略 | 最大重试次数 | 终态处理 |
| --- | --- | --- | --- |
| 网络超时 / 临时不可用 | 指数退避重试 | 3 | 转入死信队列，人工介入 |
| 受控写入口拒绝（`WRITE_GATE_DENIED`） | 不重试 | 0 | `FAILED`，记录原因并告警 |
| 版本冲突（`VERSION_CONFLICT`） | 不重试 | 0 | `FAILED`；如有更新版本结果，创建新回写任务 |
| 前置条件不满足（如人工确认尚未完成） | 保持 `PENDING`，定时重检 | 不限（由前置条件推进驱动） | 前置条件满足后进入 `WRITING` |
| 锁获取超时 | 退避重试 | 5 | `FAILED`，记录锁竞争情况 |

### 10.2 典型补偿场景

- AI 结果已生成且合法，但回写因网络抖动失败：保留 `ia_ai_application_result` 不变，单独重试回写任务，不重新生成结果。
- 回写成功但目标视图后续被人工直接修改导致不一致：不回滚已落库回写记录；由合同管理本体维护新版本视图；后续新任务基于新版本重建。
- 文档版本切换导致回写引用的 `evidence_segment` 锚点失效：回写结果本身不受影响（结果已落库）；但关联的 `ranking_snapshot_id` 标记为 `SUPERSEDED`；后续新任务的回写使用新版本锚点。
- 条款/模板版本更新导致回写的风险基线变更：已落库的风险回写结果不自动撤回；但旧风险项标记来源已变化，由异步任务提示人工复核。

### 10.3 死信队列与人工接管

- 重试耗尽后，回写进入终态 `FAILED`，同时写入回写死信队列。
- 死信队列按合同和视图类型分组，提供人工接管界面：查看失败原因、手动重试、手动标记跳过或人工直接写入视图。
- 人工接管操作生成独立的审计事件，`operator_type=HUMAN`。

### 10.4 恢复优先级

- 优先恢复已人工确认且影响业务展示的回写（如风险视图中的高风险项）。
- 其次恢复已自动放行的摘要与提取结果回写。
- 最后恢复低优先级、低影响的补充回写。
- 无论优先级，恢复流程必须保留原 `ia_writeback_record` 的审计链路，不覆盖原始失败记录。

## 11. 审计

### 11.1 审计事件

以下事件必须写入审计中心：

- 回写记录创建（`writeback_status=PENDING`），载荷包含 `result_id`、`target_type`、`target_id`、`ranking_snapshot_id`、`quality_evaluation_id`、`operator_type`。
- 回写开始执行（`writeback_status=WRITING`），载荷包含 `target_snapshot_version`。
- 回写成功（`writeback_status=WRITTEN`），载荷包含 `target_id`（如回填）、`completed_at`、最终写入值摘要。
- 回写失败（`writeback_status=FAILED`），载荷包含 `failure_reason`、`conflict_code`、重试次数、受控写入口错误码（如适用）。
- 回写跳过（`writeback_status=SKIPPED`），载荷包含 `conflict_code`、被优先回写的 `writeback_record_id`。
- 冲突升级为人工裁决，载荷包含所有冲突候选的 `result_id` 和 `ranking_score`。
- 人工裁决完成，载荷包含裁决结果和裁决操作人。
- 死信队列接管操作，载荷包含操作人、操作动作（重试/跳过/直写）。

### 11.2 审计追溯链

每条审计事件必须能追溯到以下锚点：

- `contract_id`：合同业务锚点
- `document_version_id`：相关文档版本（如适用）
- `ai_application_job_id`：AI 任务锚点
- `result_id`：AI 结果锚点
- `writeback_record_id`：回写记录锚点
- `ranking_snapshot_id`：候选排序锚点
- `quality_evaluation_id`：质量评估锚点
- `guardrail_decision`：护栏决策

全链路必须可追溯：AI 任务 → AI 结果 → 候选快照 → 质量评估 → 护栏决策 → 人工确认 → 回写记录 → 目标视图。

## 12. 监控指标

本节定义回写链路的可观测性指标口径，不构成完整的运维监控面板、告警阈值或恢复脚本设计（上述内容由 `ops-monitoring-alert-and-recovery-design.md` 专项承接）。

- **吞吐指标**：回写记录创建数、成功写入数、跳过数、失败数。
- **时延指标**：从 `PENDING` 到 `WRITTEN` 的端到端时长、受控写接口调用耗时、锁等待时长。
- **冲突指标**：版本冲突率、槽位冲突率、置信度冲突率、人工否决触发率、`UNRESOLVED_EQUAL` 率。
- **健康指标**：重试堆积量、死信队列积压量、`PENDING` 超时量、锁竞争次数。
- **质量指标**：`PARTIAL_PUBLISH` 回写缺口标注覆盖率、回写后数据一致性校验异常率。

## 13. 验收清单

- [ ] 摘要、风险分析和比对提取三类 AI 结果均能通过 `result-writeback-gateway` 回写到对应目标视图。
- [ ] `CONTRACT_SUMMARY`、`CONTRACT_RISK_VIEW`、`CONTRACT_EXTRACTION_VIEW` 三个视图的字段级映射完整，写入字段不越权。
- [ ] 回写禁止写入清单（合同主键、业务状态、文档版本、条款正文等）在运行期被有效拦截。
- [ ] 同槽位多候选回写按优先级规则正确裁决，被淘汰回写进入 `SKIPPED` 并有明确 `conflict_code`。
- [ ] 版本冲突、置信度冲突和人工否决冲突被正确检测并阻断。
- [ ] `UNRESOLVED_EQUAL` 冲突正确升级为人工裁决，而不进行静默自动选择。
- [ ] `ia_writeback_record` 承担全部回写动作，`ia_ai_application_result.written_back_status` 与 `writeback_record_id` 同步更新。
- [ ] 不满足放行条件（护栏未通过、质量评估未放行、人工确认未完成）的结果被正确阻止回写。
- [ ] 回写失败可重试、可重放；死信队列积压可人工接管。
- [ ] 乐观锁和短时互斥锁双重控制下，同一槽位不出现并发覆写。
- [ ] 回写幂等：同一 `result_id + target_type + target_id` 重复请求不产生重复记录。
- [ ] 全链路审计可追溯：AI 任务 → AI 结果 → 候选快照 → 质量评估 → 护栏决策 → 人工确认 → 回写记录 → 目标视图。
- [ ] `PARTIAL_PUBLISH` 回写结果的缺口说明和发布状态标注清晰、完整、不回补伪装数据。

## 14. 小结

本文把 `intelligent-applications` 主线中 AI 应用结果的回写动作收口为受控投影层：

- 三个目标视图（`CONTRACT_SUMMARY`、`CONTRACT_RISK_VIEW`、`CONTRACT_EXTRACTION_VIEW`）各有独立字段级映射白名单，确保 AI 输出不覆盖合同业务真相。
- 冲突场景（版本、同槽位、置信度、人工否决）有明确判定规则与优先级排序，不可自动裁决时升级人工干预。
- 回写全生命周期由 `ia_writeback_record` 承接，与 AI 结果层、候选排序层、质量评估层、护栏层和人工确认层的契约边界清晰，不互相越权。
- 并发、幂等、乐观锁、失败恢复和审计体系完整闭环。

该口径与 `intelligent-applications` 主线既有 `Detailed Design`、候选排序与质量评估专项、AI 上下文装配与输出护栏专项保持一致，为 AI 结果到合同视图的受控投影提供了稳定、可审计、可恢复的运行基线。

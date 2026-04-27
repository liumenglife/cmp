# 检索 / OCR / AI 业务应用主线专项设计：OCR 引擎适配与版面解析

## 1. 文档说明

本文档用于继续下沉 `intelligent-applications` 主线中“`OCR` 引擎适配、版面解析、表格 / 印章识别”能力，重点回答以下问题：

- `OCR` 作业如何基于文档中心受控版本受理、校验、路由、执行、回收和重试
- `OCR` 派生结果如何表达文本、版面、表格、印章、字段候选、页级坐标和语言片段
- 文档版本切换后，旧识别结果如何进入 `SUPERSEDED` / 失效 / 重建链路
- `OCR` 如何向搜索、AI 上下文装配和合同侧受控回写提供稳定输入边界
- 权限、审计、幂等、并发控制、降级、失败恢复、监控指标和验收口径如何闭环

本文是以下文档的下游专项设计：

- [`intelligent-applications Detailed Design`](../detailed-design.md)

本文不展开以下内容：

- 不写搜索索引结构、召回策略、排序策略和重建计划
- 不写 AI Prompt、上下文模板、模型参数或输出校验模板
- 不写合同侧回写字段级映射、冲突优先级和采纳流程
- 不写具体识别算法、模型训练、引擎 SDK 代码或实施排期

## 2. 目标与边界

### 2.1 设计目标

- 让 `OCR` 成为文档中心受控版本到搜索和 AI 应用的稳定输入转换层
- 让扫描件、图片稿、版式文档在不覆盖原文件的前提下形成可治理派生结果
- 让版面块、表格、印章、字段候选、语言片段和页级坐标具备统一对象模型
- 让识别失败、版本切换、引擎降级和结果重建都有可审计、可恢复的状态链路
- 让搜索、AI 上下文装配、合同侧受控回写只消费稳定 `OCR` 结果，不直连引擎私有输出

### 2.2 边界原则

- 文档中心是文件真相源；`OCR` 只接收 `document_asset_id` / `document_version_id` 引用，不保存私有文件副本。
- `OCR` 结果是派生结果；不覆盖原文件、不切换文档中心主版本、不改写文件版本链。
- 合同主档是业务真相源；`OCR` 可以持有 `contract_id` 归属锚点，但不拥有合同字段真相。
- 搜索、AI 应用和合同侧回写是下游消费方；本文只定义输入输出契约边界，不展开其专项设计。
- 识别引擎可替换；业务侧只面向 `OcrJob`、`OcrResultAggregate` 和结果片段对象，不绑定单一厂商协议。

## 3. 输入来源与依赖

### 3.1 唯一输入来源

`OCR` 作业对外受理时只接受文档中心受控对象引用，内部执行时统一收口到文档中心受控版本。

最小输入锚点如下：

| 输入锚点 | 说明 | 约束 |
| --- | --- | --- |
| `document_asset_id` | 文件对象稳定身份 | 对外受理时允许传入；系统必须先解析出可执行的 `document_version_id` |
| `document_version_id` | 文件内容版本身份 | 对外受理时允许直接传入；内部执行与结果落库必须落到明确版本 |
| `contract_id` | 合同业务归属 | 可为空；存在时用于权限裁剪、审计归属和下游消费过滤 |
| `content_fingerprint` | 文档中心版本内容摘要 | 用于幂等、复用、失效判断和结果重建校验 |
| `mime_type` / `file_size_bytes` / `page_count` | 文件处理基础摘要 | 用于输入准入、引擎路由、容量控制和超限降级 |

对外受理契约收口如下：

- 调用方可提交 `document_asset_id` 或 `document_version_id`，二选一。
- 若只提交 `document_asset_id`，系统必须在受理阶段解析出当前可执行的 `document_version_id`，并把该版本固化到 `OcrJob` 输入快照中。
- 一旦 `OcrJob` 创建成功，后续执行、重试、回调、结果落库全部只围绕该次固化的 `document_version_id` 推进，不再跟随“当前主版本”漂移。

`OCR` 不保存原始文件字节、平台外导出文件、临时下载副本或引擎私有文件副本。执行器只通过文档中心受控读取句柄获取一次性输入流，识别完成后释放句柄。

### 3.2 依赖能力

- 文档中心：提供受控版本读取、文件摘要、版本状态、能力挂接状态和版本切换事件。
- 合同管理本体：提供 `contract_id`、合同归属、合同分类和受控回写锚点。
- 权限体系：提供用户、部门、角色、数据范围和文件访问权限裁剪。
- 审计中心：承接作业创建、输入读取、引擎调用、结果生效、重试、失效和重建事件。
- 任务中心：承接异步执行、重试调度、死信分流和恢复重领。
- 对象存储：保存超大识别文本、表格结构和证据包引用；主表只保存摘要与引用。

## 4. 总体链路

### 4.1 作业受理链路

1. 调用方提交 `OCR` 作业请求，传入 `document_asset_id` 或 `document_version_id`、可选 `contract_id`、`job_purpose`、语言提示和幂等键。
2. `ocr-orchestrator` 校验调用方对合同和文档版本的访问权限。
3. `ocr-orchestrator` 如有必要先从 `document_asset_id` 解析出当前可执行的 `document_version_id`，再读取版本摘要并校验版本存在、状态可读、内容摘要有效。
4. 以 `document_version_id + job_purpose + content_fingerprint + idempotency_key` 做幂等判定。
5. 创建 `OcrJob`、输入快照、初始审计事件和任务中心执行记录。
6. 返回 `ocr_job_id`、`job_status=ACCEPTED` 和可查询的结果占位引用。

### 4.2 执行与回收链路

1. 执行器领取 `ACCEPTED` / `QUEUED` 作业，条件更新为 `RUNNING`。
2. 根据输入类型、作业目的、语言提示、页数、文件大小和引擎健康状态选择 `OcrEngineRoute`。
3. 执行器通过文档中心受控句柄读取输入流，不落私有文件副本。
4. 引擎返回原始识别载荷后，适配器归一为 `OcrResultAggregate`、`OcrLayoutBlock`、`OcrFieldCandidate`、`OcrLanguageSegment` 等对象。
5. 结果通过结果模型版本校验、引用坐标校验、质量规则校验后落库或写入对象存储引用。
6. 作业正式状态更新为 `SUCCEEDED`，结果状态更新为 `READY` 或 `PARTIAL`；如需记录部分页失败、待人工复核、等待下一次调度，只作为任务中心或审计中的内部过程态，不进入 `ia_ocr_job.job_status` 正式枚举。
7. 通知文档中心更新 `dc_capability_binding` 摘要，并向搜索和 AI 消费面发布可消费事件。

### 4.3 失败分流链路

识别失败按失败性质分流：

- 输入失败：版本不存在、权限不足、文件损坏、格式不支持，进入不可重试失败。
- 引擎失败：超时、限流、服务异常、回调丢失，进入可重试失败。
- 质量失败：页数缺失、坐标越界、关键结构不完整，进入 `PARTIAL` 结果或作业失败，并由审计与任务中心记录是否需人工复核。
- 版本失败：执行期间版本被替换或内容摘要不一致，当前作业停止生效并触发新版本重建判断。

达到最大重试次数后，作业正式状态进入 `FAILED`；是否转人工复核由任务中心和审计事件记录，不额外扩展 `OcrJob` 正式状态枚举。

## 5. 核心对象模型

### 5.1 `OcrJob`

`OcrJob` 表达一次基于受控文档版本的识别作业。

核心字段如下：

| 字段 | 说明 |
| --- | --- |
| `ocr_job_id` | 作业主键 |
| `contract_id` | 合同归属锚点，可为空 |
| `document_asset_id` | 文件对象稳定身份 |
| `document_version_id` | 文件内容版本身份 |
| `input_content_fingerprint` | 受理时读取到的内容摘要 |
| `job_purpose` | `TEXT_EXTRACTION` / `LAYOUT_EXTRACTION` / `FIELD_ASSIST` / `SEARCH_INDEX_INPUT` |
| `job_status` | 作业状态 |
| `language_hint_json` | 调用方语言提示 |
| `quality_profile_code` | 受理时固化的质量配置档位 |
| `engine_route_code` | 实际路由的引擎策略 |
| `current_attempt_no` / `max_attempt_no` | 当前尝试次数与最大重试次数 |
| `result_aggregate_id` | 稳定结果聚合对象引用 |
| `failure_code` / `failure_reason` | 失败摘要 |
| `idempotency_key` | 作业创建幂等键 |
| `trace_id` | 全链路追踪号 |

### 5.2 `OcrResultAggregate`

`OcrResultAggregate` 表达某次作业归一化后的稳定识别结果。

核心字段如下：

| 字段 | 说明 |
| --- | --- |
| `ocr_result_aggregate_id` | 结果聚合主键 |
| `ocr_job_id` | 来源作业 |
| `contract_id` | 合同归属锚点 |
| `document_asset_id` / `document_version_id` | 来源文件锚点 |
| `result_status` | `READY` / `PARTIAL` / `FAILED` / `SUPERSEDED` |
| `result_schema_version` | 结果模型版本 |
| `quality_profile_code` | 生成该结果时命中的质量配置档位 |
| `full_text_ref` | 全文文本对象引用 |
| `page_summary_json` | 页级摘要、页尺寸、旋转角度、页质量 |
| `layout_block_ref` | 版面块集合引用 |
| `field_candidate_ref` | 字段候选集合引用 |
| `language_segment_ref` | 语言片段集合引用 |
| `table_payload_ref` | 表格结构集合引用 |
| `seal_payload_ref` | 印章识别集合引用 |
| `citation_payload_ref` | 引用坐标集合引用 |
| `quality_score` | 质量总分 |
| `content_fingerprint` | 输入内容摘要 |
| `superseded_by_result_id` | 被新结果替代时的目标结果 |

### 5.3 `OcrLayoutBlock`

`OcrLayoutBlock` 表达页内可引用版面块。

最小语义如下：

- `layout_block_id`
- `ocr_result_aggregate_id`
- `page_no`
- `block_type`：`TITLE`、`PARAGRAPH`、`TABLE`、`SEAL`、`SIGNATURE_AREA`、`HEADER`、`FOOTER`、`IMAGE`、`UNKNOWN`
- `text_ref` 或 `text_excerpt`
- `bbox`：页级坐标，包含 `x`、`y`、`width`、`height`、`unit`、`page_width`、`page_height`
- `reading_order`
- `confidence_score`
- `parent_block_id`
- `source_engine_code`

### 5.4 `OcrFieldCandidate`

`OcrFieldCandidate` 表达可被搜索、AI 或合同侧待确认消费的字段候选。

最小语义如下：

- `field_candidate_id`
- `ocr_result_aggregate_id`
- `field_type`：`AMOUNT`、`CURRENCY`、`PARTY_NAME`、`SIGN_DATE`、`EFFECTIVE_DATE`、`EXPIRE_DATE`、`CONTRACT_NO`、`CLAUSE_TITLE`、`SEAL_NAME`
- `candidate_value`
- `normalized_value`
- `source_layout_block_id`
- `page_no`
- `bbox`
- `confidence_score`
- `quality_profile_code`
- `field_threshold_code`
- `evidence_text`
- `candidate_status`：`CANDIDATE`、`LOW_CONFIDENCE`、`REJECTED_BY_RULE`、`SELECTED_FOR_CONTEXT`

字段候选只进入候选域，不直接更新合同主档字段。合同侧如需采纳，必须通过受控回写与人工确认链路承接。

其中：

- `quality_profile_code` 表达该候选是在什么质量档位下生成与评估的。
- `field_threshold_code` 表达该候选命中的字段阈值分组，至少按 `field_type` 维度区分。
- 候选是否落为 `LOW_CONFIDENCE`，必须基于 `field_threshold_code` 对应的字段阈值判断，而不是运行时临时拍板。

### 5.5 `OcrLanguageSegment`

`OcrLanguageSegment` 表达文本片段的语言归属。

最小语义如下：

- `language_segment_id`
- `ocr_result_aggregate_id`
- `page_no`
- `layout_block_id`
- `language_code`：如 `zh-CN`、`en-US`、`es-ES`
- `text_range`
- `bbox`
- `confidence_score`
- `normalization_profile_code`

语言片段服务搜索语言路由、AI 上下文装配和多语言展示，不替代原始文本。

### 5.6 `OcrQualityProfile`

`OcrQualityProfile` 表达 `OCR` 结果发布、候选分级和告警判断所使用的质量配置档位。

最小语义如下：

- `quality_profile_code`
- `job_purpose`
- `mime_type_group`
- `table_required_flag`
- `seal_required_flag`
- `min_page_coverage_ratio`
- `min_text_confidence_score`
- `min_table_confidence_score`
- `min_seal_confidence_score`
- `min_publish_quality_score`
- `max_coordinate_error_ratio`
- `field_threshold_map`
- `alert_threshold_profile_code`
- `profile_status`
- `profile_version`

其中 `field_threshold_map` 至少要覆盖当前文中已经引用的字段阈值类型：

- `default_min_field_confidence_score`
- `amount_min_field_confidence_score`
- `party_name_min_field_confidence_score`
- `date_min_field_confidence_score`
- `contract_no_min_field_confidence_score`
- `seal_name_min_field_confidence_score`

对象落点规则如下：

- `OcrJob` 在受理时固化 `quality_profile_code`，后续重试不重新匹配配置档位。
- `OcrResultAggregate` 持有同一个 `quality_profile_code`，用于解释该结果是在什么发布阈值下形成的。
- `OcrFieldCandidate` 同时持有 `quality_profile_code` 与 `field_threshold_code`，用于解释候选值为什么进入 `CANDIDATE` 或 `LOW_CONFIDENCE`。
- 下游读取 `OCR` 结果时，不直接重算阈值，只读取结果对象与候选对象已携带的 `quality_profile_code` / `field_threshold_code` 及其质量标记。

### 5.7 表格与印章对象

表格识别对象 `OcrTableRegion` 至少包含：

- `table_region_id`
- `page_no`
- `bbox`
- `row_count`、`column_count`
- `cell_list`：单元格坐标、行列跨度、文本、置信度
- `header_candidate_list`
- `table_confidence_score`

印章识别对象 `OcrSealRegion` 至少包含：

- `seal_region_id`
- `page_no`
- `bbox`
- `seal_text_candidate`
- `seal_shape`：`ROUND`、`OVAL`、`RECTANGLE`、`UNKNOWN`
- `color_hint`
- `overlap_signature_flag`
- `confidence_score`

表格与印章对象只表达识别到的结构和证据，不表达签章法律状态或合同业务结论。

## 6. 状态机

### 6.1 作业状态

`OcrJob` 正式状态必须与父 `detailed-design.md` 中 `ia_ocr_job.job_status` 保持一致，不新增平行枚举。

`OcrJob` 正式状态流转如下：

```text
ACCEPTED -> QUEUED -> RUNNING -> SUCCEEDED
ACCEPTED -> QUEUED -> RUNNING -> FAILED
ACCEPTED -> QUEUED -> CANCELLED
```

状态语义如下：

- `ACCEPTED`：作业已受理，输入快照和幂等记录已落库。
- `QUEUED`：已进入执行队列，等待执行器领取。
- `RUNNING`：执行器已锁定并调用识别引擎。
- `SUCCEEDED`：形成稳定完整结果。
- `FAILED`：不可继续自动推进或达到最大重试次数。
- `CANCELLED`：被系统或授权操作取消。

不进入正式枚举、只允许存在于任务调度或审计描述中的内部过程态包括：

- 等待下一次重试调度
- 需要人工复核
- 部分页识别失败但结果仍可受控消费

这些过程态只能作为 `failure_reason`、任务中心状态或审计事件摘要存在，不能写成 `ia_ocr_job.job_status` 正式值。

### 6.2 结果状态

`OcrResultAggregate` 正式状态必须与父 `detailed-design.md` 中 `ia_ocr_result.result_status` 保持一致。

`OcrResultAggregate` 正式状态流转如下：

```text
READY
PARTIAL
FAILED
READY -> SUPERSEDED
PARTIAL -> SUPERSEDED
```

状态语义如下：

- `READY`：可作为搜索和 AI 应用的稳定输入。
- `PARTIAL`：可受控消费，但消费方必须感知质量缺口和缺失页范围。
- `FAILED`：结果不可用。
- `SUPERSEDED`：同一文件新版本或同一输入新结果已替代该结果。

结果生成中的“归一化中、落库中、待发布”只允许作为写入事务或任务执行过程描述，不进入 `ia_ocr_result.result_status` 正式枚举。

## 7. 结果模型与坐标规则

### 7.1 页级坐标

所有可定位对象必须使用统一页级坐标：

- 页码从 `1` 开始。
- 坐标原点为页面左上角。
- 坐标单位固定记录在 `unit`，默认使用归一化比例坐标，必要时保留像素尺寸摘要。
- 每个 `bbox` 必须携带 `page_width`、`page_height` 和 `rotation`，用于跨预览渲染与审计回放。
- 坐标不得脱离 `document_version_id + page_no + content_fingerprint` 单独消费。

### 7.2 文本层

文本层按 `page -> block -> line -> token` 组织。

文本层必须保留：

- 文本内容或对象存储引用
- 读取顺序
- 坐标范围
- 置信度
- 来源引擎
- 语言归属

全文文本用于搜索索引和 AI 上下文装配，不能作为文件正文真相回写文档中心。

### 7.3 版面解析

版面解析至少区分：标题、正文、表格、印章、签署区、页眉页脚、图片和未知块。

规则如下：

- 版面块必须能回指页级坐标和输入版本。
- 父子块用于表达表格内文本、印章覆盖区域、签署区内签名或日期片段。
- 无法稳定分类的区域归入 `UNKNOWN`，不得强行标记为合同字段。
- 消费方可按块类型过滤上下文，但不得把块类型当作业务事实。

### 7.4 字段候选

字段候选按“候选值 + 证据 + 置信度 + 来源坐标”表达。

规则如下：

- 同一字段类型允许多个候选，不在 `OCR` 层裁定最终值。
- 规则校验失败的候选保留为 `REJECTED_BY_RULE`，用于质量分析和人工复核。
- 低置信度候选可进入 AI 上下文，但必须携带质量标记。
- 合同侧字段采纳必须通过合同管理本体受控入口，不由 `OCR` 自动写入。

### 7.5 语言片段

语言片段用于支持中文、英文、西文混排文档。

规则如下：

- 语言识别以片段为单位，不强制整份文档只有一个语言。
- 搜索消费时可按语言片段建立字段或索引路由。
- AI 上下文装配时必须保留原语言文本和必要的归一语言摘要。
- 翻译或术语归一是派生处理，不替代原始识别文本。

### 7.6 质量规则与配置载体

文中涉及的“质量阈值 / 可消费阈值 / 告警阈值”统一收口为 `OcrQualityProfile` 配置，不再使用无来源阈值表述。

`OcrQualityProfile` 至少按以下粒度管理：

- `job_purpose`
- 文档类型或 `mime_type` 大类
- 是否要求表格识别
- 是否要求印章识别

配置内容至少包括：

- `min_page_coverage_ratio`
- `min_text_confidence_score`
- `min_table_confidence_score`
- `min_seal_confidence_score`
- `min_publish_quality_score`
- `max_coordinate_error_ratio`
- `field_threshold_map`
- `alert_threshold_profile_code`

其中 `field_threshold_map` 至少覆盖：

- 通用字段阈值
- 金额字段阈值
- 相对方字段阈值
- 日期字段阈值
- 合同编号字段阈值
- 印章名称字段阈值

配置载体与默认处理方式如下：

- 正式配置载体为主线内部治理配置，按 `quality_profile_code` 持久化并由运行时缓存加载。
- `OcrJob` 在受理时固化命中的 `quality_profile_code`，同一作业重试不漂移配置版本。
- 未命中特殊配置时，使用主线默认 `OCR_BASELINE` 质量档。
- 未达到可消费阈值但仍保留部分结构时，结果状态为 `PARTIAL`。
- 低于最低可发布阈值时，作业进入 `FAILED`，并通过审计与任务中心标记是否需人工复核。
- 告警阈值只决定是否触发运维告警，不改变正式状态枚举。

## 8. 引擎适配设计

### 8.1 引擎适配层职责

`OcrEngineAdapter` 只负责把平台统一作业转成引擎可执行请求，并把引擎响应归一到平台对象模型。

它承担：

- 输入流读取与临时访问控制
- 引擎请求参数映射
- 超时、限流、熔断和重试分类
- 原始结果 `result_schema_version` 校验
- 坐标、文本、表格、印章、字段候选归一化
- 引擎调用审计摘要和质量指标上报

它不承担：

- 文件版本链管理
- 合同字段采纳
- 搜索索引构建
- AI 上下文模板装配
- 引擎私有结果长期暴露

### 8.2 路由策略

`OcrEngineRoute` 根据以下因素选择引擎和执行档位：

- `job_purpose`
- `mime_type`
- 页数和文件大小
- 是否扫描件或图片稿
- 是否需要表格 / 印章结构
- 语言提示和历史识别质量
- 引擎健康状态、队列积压、配额和熔断状态

路由输出只记录 `engine_route_code`、`engine_profile_code` 和能力标签，不把具体厂商参数暴露给业务接口。

### 8.3 降级策略

降级按“保留可用派生结果、不破坏真相源”的原则推进：

- 表格识别失败但文本层成功：若命中的 `OcrQualityProfile` 允许文本优先发布，则结果进入 `PARTIAL`，表格对象标记缺失。
- 印章识别失败但版面区域可定位：保留 `SEAL` 版面块，印章文本候选为空。
- 高精度引擎不可用：切换通用文本识别引擎，质量分下调并记录降级原因；是否仍可发布由 `OcrQualityProfile` 判定。
- 全部引擎不可用：作业按重试策略继续调度，或在无法继续推进时进入 `FAILED`，不伪造空结果。

### 8.4 引擎健康与熔断

按引擎维度记录：

- 调用成功率
- 平均耗时和 P95 / P99 耗时
- 超时率
- 限流次数
- 熔断次数
- 质量失败率
- 降级命中次数

连续失败达到阈值后，引擎进入短期熔断；熔断期间新作业路由到备用引擎或排队等待，不影响已落库结果查询。

## 9. 版本切换、失效与重建

### 9.1 版本切换事件

文档中心发生当前主版本切换时，`OCR` 域接收 `document_version_changed` 事件，至少包含：

- `document_asset_id`
- `old_document_version_id`
- `new_document_version_id`
- `old_content_fingerprint`
- `new_content_fingerprint`
- `changed_at`
- `change_reason`

### 9.2 失效规则

失效判断如下：

- 旧版本结果保留历史可查，但退出默认消费资格。
- 旧版本结果状态从 `READY` / `PARTIAL` 更新为 `SUPERSEDED`，并记录 `superseded_by_result_id` 或待重建任务引用。
- 搜索和 AI 默认只消费新主版本对应的 `READY` / `PARTIAL` 结果。
- 若下游显式请求历史版本审计回放，可按权限读取旧结果和坐标。

### 9.3 重建规则

重建以新 `document_version_id + content_fingerprint` 为输入，不复用旧版本结果。

重建触发条件：

- 文档主版本切换
- 同一版本内容摘要变化
- 结果模型版本升级且旧结果不满足消费要求
- 引擎质量策略升级后需要重新生成字段候选或版面块
- 人工复核确认旧结果不可作为稳定输入

重建不会删除旧结果。旧结果通过 `SUPERSEDED` 和审计事件解释其消费资格变化；对“当前质量规则已不再推荐默认消费”的情况，使用消费侧过滤和审计说明表达，不新增与父文档冲突的结果状态。

## 10. 失败恢复与补偿

### 10.1 重试策略

可重试失败包括：引擎超时、引擎限流、临时网络异常、对象存储临时不可用、任务执行器异常退出。

重试规则如下：

- 每次尝试生成独立 `attempt_no` 和审计事件。
- 重试前重新校验文档版本摘要，发现输入版本已切换则停止当前作业并转入重建判断。
- 指数退避与最大重试次数由 `job_purpose`、文件大小和引擎错误类型决定。
- 达到最大重试次数后进入 `FAILED`；是否创建人工复核任务由任务中心和审计链路承接。

### 10.2 人工复核分流

以下场景进入人工复核：

- 多页文档缺页或页序异常
- 坐标明显越界或大面积重叠
- 表格结构低于 `OcrQualityProfile` 中对应的最小可发布阈值且业务目的要求表格结果
- 印章区域识别冲突且影响签署证据解释
- 字段候选关键值置信度低于 `OcrQualityProfile` 的字段阈值且被 AI 或合同侧高风险消费请求引用

人工复核只改变结果质量标记、候选选择和消费放行状态，不改写原文件。

### 10.3 补偿场景

| 场景 | 补偿动作 |
| --- | --- |
| 作业成功但结果写入失败 | 保留作业尝试记录，重放结果归一化与写入步骤 |
| 结果已生成但文档中心挂接失败 | 保留 `OcrResultAggregate`，重试 `dc_capability_binding` 更新 |
| `OCR` 成功但搜索消费失败 | 不重跑 `OCR`，单独触发搜索补索引任务 |
| 版本切换时旧结果仍被默认消费 | 标记旧结果 `SUPERSEDED`，发布新版本重建事件 |
| 引擎回调重复到达 | 通过 `ocr_job_id + attempt_no + engine_request_id` 幂等吸收 |

## 11. 权限、审计、幂等与并发控制

### 11.1 权限控制

- 创建作业前必须校验调用方对 `contract_id`、`document_asset_id`、`document_version_id` 的访问权限。
- 结果查询必须再次校验当前调用方是否仍有查看该合同和文档版本的权限。
- 搜索和 AI 消费 `OCR` 结果前必须先做业务范围裁剪，不能因派生结果存在而绕过源文件权限。
- 历史版本结果默认不进入普通查询曝光；审计回放和显式历史版本查询需要专项权限。

### 11.2 审计事件

必须审计以下动作：

- `OCR` 作业创建、取消、领取、重试、失败、成功
- 文档中心输入句柄申请和释放
- 引擎路由、降级、熔断、恢复
- 结果归一化、质量校验、结果生效
- 版本切换导致的 `SUPERSEDED`
- 人工复核创建、处理和放行
- 下游消费事件发布

审计最少包含：`ocr_job_id`、`ocr_result_aggregate_id`、`contract_id`、`document_asset_id`、`document_version_id`、`content_fingerprint`、`actor_id`、`trace_id`、`action_type`、`result_status`。

### 11.3 幂等

作业创建幂等键：

```text
document_version_id + job_purpose + content_fingerprint + idempotency_key
```

结果写入幂等键：

```text
ocr_job_id + attempt_no + result_schema_version
```

回调幂等键：

```text
engine_code + engine_request_id + callback_sequence
```

相同幂等键且请求体一致时返回首个稳定作业；相同幂等键但请求体不一致时返回幂等冲突。

与父文档唯一约束的兼容口径如下：

- `OcrJob` 的正式幂等唯一约束保持为 `uk_ocr_idempotency(document_version_id, job_purpose, idempotency_key)`。
- `OcrResultAggregate` 的正式唯一关系保持为“一次 `OcrJob` 对应零个或一个稳定结果”，不在同一作业下生成第二份正式结果。
- 同一 `document_version_id` 如因不同 `idempotency_key` 受理到新作业，系统可在受理阶段复用已存在且仍兼容当前 `quality_profile_code` 与 `result_schema_version` 的稳定结果，但这种复用表现为返回既有结果引用，而不是为同一作业或同一版本强行生成“新摘要”并突破父文档唯一约束。
- 如需基于同一版本重新识别，必须创建新作业并保留新的作业主键；旧结果是否被替代只通过 `SUPERSEDED` 关系表达，不改变“一作业一稳定结果”的正式约束。

### 11.4 并发控制

- 同一 `document_version_id + job_purpose` 同时只允许一个活跃作业进入执行。
- 同一 `document_asset_id` 的主版本切换事件与结果生效事件通过短锁和数据库条件更新串行化。
- `OcrJob`、`OcrResultAggregate` 使用 `version_no` 乐观锁。
- 执行器领取任务采用“状态条件更新 + 短锁”双保险，避免重复执行。
- `SUPERSEDED` 更新必须带原状态条件，避免新结果被旧补偿任务覆盖。

## 12. 上下游契约边界

### 12.1 与文档中心

输入契约：

- `OCR` 对外受理时只接收文档中心 `document_asset_id` 或 `document_version_id`；内部执行时统一解析并固化为 `DocumentVersion`。
- 文档中心提供受控读取句柄和版本摘要。
- `OCR` 不保存私有文件副本，不生成新文件版本。

输出契约：

- `OCR` 向文档中心回写能力挂接摘要：`capability_type=OCR`、状态、结果引用、`quality_profile_code`、质量摘要、时间戳。
- 文档中心主版本切换时通知 `OCR` 域进行旧结果失效和新版本重建判断。

### 12.2 与搜索

搜索只消费 `READY` / `PARTIAL` 且权限可见的 `OcrResultAggregate`。

交付给搜索的最小内容包括：

- 全文文本引用
- 页级文本片段
- 版面块类型和读取顺序
- 语言片段
- 来源坐标和 `document_version_id`
- 质量标记、`quality_profile_code` 和缺失页范围

搜索不得直接消费引擎原始响应，也不得把 `OCR` 文本升级为文件真相。

### 12.3 与 AI 上下文装配

AI 应用只消费经 `OCR` 域归一化后的结构化结果。

交付给 AI 上下文装配的最小内容包括：

- 合同归属和文档版本引用
- 页级文本片段与引用坐标
- 版面块层级
- 表格结构摘要
- 印章区域摘要
- 字段候选与置信度
- 语言片段、`quality_profile_code` 和质量标记

AI 上下文装配必须保留引用来源，不得把低置信度字段候选当作已确认合同事实。

### 12.4 与合同侧受控回写

`OCR` 不直接写合同主档字段。

可供合同侧使用的内容是候选和证据：

- `OcrFieldCandidate`
- 证据文本
- 页级坐标
- 置信度
- 来源文档版本
- 结果质量状态

合同侧如需把金额、相对方、日期、合同编号等字段写入业务视图，必须通过合同管理本体受控写入口、人工确认和审计链路承接。

## 13. 监控指标与告警

### 13.1 作业指标

- 作业受理量、排队量、运行量、成功量、失败量
- `ACCEPTED -> READY` 端到端耗时
- 队列等待耗时
- 重试次数分布
- 死信和人工复核积压量

### 13.2 引擎指标

- 引擎调用成功率
- 引擎 P95 / P99 耗时
- 超时率、限流率、熔断次数
- 降级路由次数
- 不同文件类型的失败率

### 13.3 质量指标

- 页级识别覆盖率
- 平均文本置信度
- 表格识别成功率
- 印章区域识别成功率
- 字段候选低置信度比例
- 坐标校验失败率
- 版本切换后重建完成率

### 13.4 告警规则

- 队列积压超过阈值告警。
- 引擎连续失败或熔断告警。
- `READY` 结果质量分低于 `OcrQualityProfile` 对应告警阈值告警。
- 版本切换后重建任务积压告警。
- 结果写入失败或文档中心挂接失败告警。
- 历史结果仍被默认消费命中告警。

## 14. 验收清单

### 14.1 功能验收

- 能基于文档中心受控版本创建 `OCR` 作业。
- 能完成输入版本校验、权限校验、幂等受理和状态查询。
- 能按作业目的完成引擎路由和结果回收。
- 能按 `quality_profile_code` 判定 `READY` / `PARTIAL` / `FAILED` 发布结果。
- 能生成文本层、版面块、表格对象、印章对象、字段候选、语言片段和页级坐标。
- 能在识别失败时进入可重试、不可重试或人工复核分支。

### 14.2 真相源验收

- `OCR` 不保存私有文件副本。
- `OCR` 不覆盖文档中心原文件和文件版本链。
- `OCR` 不直接改写合同主档字段。
- 所有结果均能回指 `document_version_id`、页码和坐标。

### 14.3 版本治理验收

- 文档主版本切换后，旧结果退出默认消费资格。
- 旧结果可进入 `SUPERSEDED`，且审计可追溯。
- 新版本能触发重建任务。
- 搜索和 AI 默认消费新版本稳定结果。

### 14.4 安全与审计验收

- 作业创建、结果查询和下游消费均做权限校验。
- 引擎调用、降级、失败、重试、结果生效和失效均有审计事件。
- 普通日志不输出全文合同正文、完整识别文本或临时读取句柄。
- 幂等冲突、重复回调和并发领取均能被稳定处理。

### 14.5 恢复验收

- 执行器重启后可重领未完成作业。
- 引擎临时失败后可按策略重试。
- 结果写入失败可重放归一化写入。
- 文档中心挂接失败可单独补偿，不重跑识别。
- `OCR` 成功但搜索失败时可单独补索引。

## 15. 本文结论

`OCR` 引擎适配与版面解析的核心收口如下：

1. `OCR` 的唯一文件输入是文档中心受控版本，作业执行不保存私有文件副本。
2. `OcrJob` 承接受理、校验、路由、执行、失败分流与重试；`OcrResultAggregate` 承接稳定派生结果。
3. 版面块、表格、印章、字段候选、语言片段和页级坐标统一作为可引用派生结果建模。
4. 文档版本切换后，旧结果通过 `SUPERSEDED` 退出默认消费，新版本按受控规则重建。
5. 搜索、AI 上下文装配和合同侧回写只消费归一化结果与候选证据，不直接消费引擎私有输出，也不把派生结果升级为合同或文件真相。
6. 权限、审计、幂等、并发、降级、失败恢复和监控验收已形成闭环，可作为本主线阶段一“稳定输入基础能力”的实现基线。

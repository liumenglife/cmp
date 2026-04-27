# 检索 / OCR / AI 业务应用主线 API Design

## 1. 文档说明

本文档是“检索 / OCR / AI 业务应用主线”的第一份正式
`API Design`。

本文在以下输入文档约束下，定义本主线对外可见的资源边界、
请求/响应契约、鉴权约束、错误码复用策略、异步任务与回调边界，
并明确与合同主档、文档中心、`Agent OS`、条款库 / 模板库的接口边界。

### 1.1 输入

- 上游需求基线：[`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- 总平台架构约束：[`Architecture Design`](../../architecture-design.md)
- 总平台接口规范：[`API Design`](../../api-design.md)
- 总平台共享内部边界：[`Detailed Design`](../../detailed-design.md)
- 本主线架构边界：[`Architecture Design`](./architecture-design.md)
- `Agent OS` 接口边界：[`API Design`](../../foundations/agent-os/api-design.md)
- 文档中心接口边界：[`API Design`](../document-center/api-design.md)
- 合同管理本体接口边界：[`API Design`](../contract-core/api-design.md)

### 1.2 输出

- 本文：[`API Design`](./api-design.md)
- 为后续本主线 `Detailed Design` 提供下沉边界

### 1.3 阅读边界

本文只描述接口契约，不展开以下内容：

- 不复述需求范围、建设目标、技术选型理由
- 不写物理表结构、索引结构、向量参数、缓存键
- 不写模型 / Provider 参数、Prompt 模板、模型超参数
- 不写任务拆解、实施排期、负责人安排

## 2. API 边界

### 2.1 主线接口边界总览

- 本主线对外只暴露四类正式接口：`OCR`、搜索、AI 业务应用、
  结果查询 / 回调。
- 合同主档仍是业务真相源；本主线所有接口都围绕 `contract_id`
  组织结果归属，但不生成第二份合同真相。
- 文档中心仍是文件真相源；本主线所有文件输入都应以
  `document_asset_id` 或 `document_version_id` 引用受控文件对象，
  不接收长期脱离文档中心治理的应用私有文件库。
- `Agent OS` 仍是 AI 运行时底座；本主线只定义业务应用层如何发起、
  查询、回收与消费 AI 结果，不把具体模型或 Provider 暴露成 API 主语义。
- 搜索结果、`OCR` 结果、AI 应用结果都是派生结果，允许回指真相源、
  允许形成视图或待确认结果，但不得被定义为新的业务真相源。

### 2.2 `OCR` 接口边界

资源定位：`OcrJob` 表达一次 `OCR` 作业受理；`OcrResult` 表达该作业的
稳定结果视图。

#### `POST /api/intelligent-applications/ocr-jobs`

用途：基于合同相关受控文件创建 `OCR` 作业。

请求重点字段：

- `contract_id`：可选；有业务归属时应传
- `document_asset_id` 或 `document_version_id`：二选一，优先指向受控版本
- `job_purpose`：`TEXT_EXTRACTION` / `LAYOUT_EXTRACTION` /
  `FIELD_ASSIST` / `SEARCH_INDEX_INPUT`
- `language_hint_list`：可选语言提示，支持 `zh-CN`、`en-US`、`es-ES`
- `callback_url`
- `callback_secret_ref`

响应重点字段：

- `ocr_job_id`
- `job_status`
- `accepted_at`
- `result_id`

#### `GET /api/intelligent-applications/ocr-jobs/{ocr_job_id}`

用途：查询 `OCR` 作业状态、输入引用与结果摘要。

#### `GET /api/intelligent-applications/ocr-results/{ocr_result_id}`

用途：查询 `OCR` 结果。

`OcrResult` 对外可见字段：

- `ocr_result_id`
- `ocr_job_id`
- `contract_id`
- `document_asset_id`
- `document_version_id`
- `result_status`
- `detected_language_list`
- `full_text`
- `page_list`
- `layout_block_list`
- `field_candidate_list`
- `citation_list`
- `generated_at`

边界约束：

- `OcrResult` 只表达识别结果，不改写原文件、不切换文档中心主版本。
- `full_text`、`layout_block_list`、`field_candidate_list` 都是派生结果，
  只能被搜索和 AI 应用消费，不能替代合同主档或文档中心正式字段。
- `OCR` 失败不影响文档中心文件真相成立；失败语义通过作业状态与错误码表达。

### 2.3 搜索接口边界

资源定位：`SearchQuery` 表达一次受理后的查询请求；
`SearchResultSet` 表达稳定结果集。

#### `POST /api/intelligent-applications/search-queries`

用途：提交搜索查询，返回一次可追踪的结果集句柄。

请求重点字段：

- `query_text`
- `search_scope_list`：`CONTRACT` / `DOCUMENT` / `CLAUSE` /
  `AI_APPLICATION_RESULT`
- `contract_id_list`
- `document_asset_id_list`
- `language`
- `keyword_language`
- `filter_fields`
- `sort_by`
- `page`
- `page_size`

响应重点字段：

- `search_query_id`
- `result_set_id`
- `query_status`

#### `GET /api/intelligent-applications/search-queries/{search_query_id}`

用途：查询搜索请求摘要。

#### `GET /api/intelligent-applications/search-result-sets/{result_set_id}`

用途：查询搜索结果集。

`SearchResultSet` 对外可见字段：

- `result_set_id`
- `search_query_id`
- `result_status`
- `query_text`
- `language`
- `item_list`
- `facet_list`
- `total`
- `page`
- `page_size`

`item_list` 中的单项至少包含：

- `item_type`：`CONTRACT` / `DOCUMENT` / `CLAUSE` / `AI_RESULT`
- `item_id`
- `title`
- `snippet`
- `highlight_list`
- `score`
- `source_ref`
- `matched_language`

边界约束：

- 搜索索引是读模型，搜索结果只表达“可稳定召回的候选集”，
  不表达合同主状态、文件主版本或条款正式启停状态的最终解释。
- 搜索可暴露 `AI_APPLICATION_RESULT` 类型结果，但该类结果仍是派生结果视图，
  必须同时回指原始合同、文档或条款来源。

### 2.4 AI 业务应用接口边界

资源定位：`AiApplicationJob` 表达一次业务应用任务受理；
`AiApplicationResult` 表达任务产出的稳定结果视图。

#### `POST /api/intelligent-applications/ai-application-jobs`

用途：按统一入口受理 AI 业务应用任务。

请求重点字段：

- `application_type`：`SUMMARY` / `QA` / `RISK_ANALYSIS` / `DIFF_EXTRACTION`
- `contract_id`
- `document_asset_id_list`
- `document_version_id_list`
- `clause_id_list`
- `template_id`
- `language`
- `response_language`
- `context_scope`
- `callback_url`
- `callback_secret_ref`

响应重点字段：

- `ai_application_job_id`
- `application_type`
- `job_status`
- `result_id`

#### `GET /api/intelligent-applications/ai-application-jobs/{ai_application_job_id}`

用途：查询 AI 业务应用任务状态。

#### `GET /api/intelligent-applications/ai-application-results/{result_id}`

用途：查询统一 AI 业务应用结果。

`AiApplicationResult` 对外可见字段：

- `result_id`
- `ai_application_job_id`
- `application_type`
- `contract_id`
- `result_status`
- `result_summary`
- `structured_payload`
- `citation_list`
- `human_confirmation_required`
- `written_back_status`
- `generated_at`

边界约束：

- `application_type` 是对外主语义；不允许出现 `provider`、`model_name`、
  `temperature` 等底层运行时字段作为业务接口必填项。
- AI 业务应用只能消费合同主档、文档中心、`OCR`、搜索、条款库、模板库等
  受控输入；不接受绕过权限体系的原始底层数据直读语义。
- `human_confirmation_required=true` 的结果，只能作为待确认结果消费，
  不得直接成为正式业务结论。

### 2.5 摘要接口边界

资源定位：`SummaryResult` 是 `AiApplicationResult` 在摘要场景下的专用结果资源。

#### `POST /api/intelligent-applications/summary-jobs`

用途：发起合同或文档摘要任务。

请求重点字段：

- `contract_id`
- `document_asset_id_list`
- `document_version_id_list`
- `summary_scope`：`CONTRACT_OVERVIEW` / `DOCUMENT_OVERVIEW` /
  `CLAUSE_FOCUS` / `RISK_FOCUS`
- `response_language`

#### `GET /api/intelligent-applications/summary-results/{summary_result_id}`

用途：查询摘要结果。

`SummaryResult` 对外可见字段：

- `summary_result_id`
- `ai_application_job_id`
- `contract_id`
- `summary_scope`
- `summary_text`
- `section_list`
- `citation_list`
- `result_status`

边界约束：

- 摘要是面向消费的派生结论，不替代合同主档主字段。
- 摘要若回写，只能回写到合同主档关联摘要区或应用结果区，
  不直接覆盖合同名称、状态、相对方等业务字段。

### 2.6 问答接口边界

资源定位：`QaSession` 表达围绕某一合同或文档范围的问答会话。

#### `POST /api/intelligent-applications/qa-sessions`

用途：创建问答会话。

请求重点字段：

- `contract_id`
- `document_asset_id_list`
- `document_version_id_list`
- `clause_id_list`
- `session_language`
- `answer_language`

响应重点字段：

- `qa_session_id`
- `session_status`

#### `POST /api/intelligent-applications/qa-sessions/{qa_session_id}/messages`

用途：向问答会话发送问题。

请求重点字段：

- `question_text`
- `question_language`
- `response_language`
- `question_scope`

响应重点字段：

- `message_id`
- `job_id`
- `session_status`

#### `GET /api/intelligent-applications/qa-sessions/{qa_session_id}`

用途：查询会话摘要与最近答案。

`QaSession` 对外可见字段：

- `qa_session_id`
- `contract_id`
- `session_status`
- `session_language`
- `answer_language`
- `message_list`
- `latest_answer`
- `citation_list`

边界约束：

- 问答输出必须携带引用依据，不提供“无引用自由回答”契约。
- 问答会话是交互资源，不是长期业务真相源；其答案只能作为辅助消费结果。

### 2.7 风险识别接口边界

资源定位：`RiskAnalysis` 表达一次风险识别结果。

#### `POST /api/intelligent-applications/risk-analyses`

用途：发起合同风险识别任务。

请求重点字段：

- `contract_id`
- `document_asset_id_list`
- `document_version_id_list`
- `clause_id_list`
- `template_id`
- `analysis_scope`
- `response_language`

#### `GET /api/intelligent-applications/risk-analyses/{risk_analysis_id}`

用途：查询风险识别结果。

`RiskAnalysis` 对外可见字段：

- `risk_analysis_id`
- `ai_application_job_id`
- `contract_id`
- `result_status`
- `risk_item_list`
- `overall_risk_level`
- `citation_list`
- `human_confirmation_required`

`risk_item_list` 中的单项至少包含：

- `risk_type`
- `risk_level`
- `risk_summary`
- `evidence_list`
- `suggestion_list`

边界约束：

- 风险识别结果是辅助判断，不自动改写 `contract_status`、审批结论或归档结论。
- 风险标签如需进入合同视图，应通过受控回写接口或上游业务确认动作承接。

### 2.8 比对提取接口边界

资源定位：`DiffExtractionResult` 表达字段提取、条款提取或差异比对结果。

#### `POST /api/intelligent-applications/diff-extraction-jobs`

用途：发起比对提取任务。

请求重点字段：

- `contract_id`
- `left_document_version_id`
- `right_document_version_id`
- `template_id`
- `clause_id_list`
- `extraction_scope`：`FIELD_EXTRACTION` / `CLAUSE_EXTRACTION` /
  `DOCUMENT_DIFF`
- `response_language`

#### `GET /api/intelligent-applications/diff-extraction-results/{diff_extraction_result_id}`

用途：查询比对提取结果。

`DiffExtractionResult` 对外可见字段：

- `diff_extraction_result_id`
- `ai_application_job_id`
- `contract_id`
- `result_status`
- `comparison_pair`
- `field_extraction_list`
- `clause_diff_list`
- `citation_list`
- `human_confirmation_required`

边界约束：

- 提取结果可作为待确认结构化结果，不直接覆盖合同主档正式字段。
- 差异比对返回的是“识别到的差异与证据”，不是“已确认变更”的业务结论。

## 3. 核心资源划分

### 3.1 `OcrJob`

- 资源职责：表达一次 `OCR` 任务受理、执行状态与结果关联
- 核心字段：`ocr_job_id`、`contract_id`、`document_asset_id`、
  `document_version_id`、`job_purpose`、`job_status`、`result_id`

### 3.2 `OcrResult`

- 资源职责：表达 `OCR` 稳定结果视图
- 核心字段：`ocr_result_id`、`ocr_job_id`、`result_status`、`full_text`、
  `page_list`、`layout_block_list`、`field_candidate_list`、`citation_list`

### 3.3 `SearchQuery`

- 资源职责：表达一次可追踪搜索请求
- 核心字段：`search_query_id`、`query_text`、`search_scope_list`、
  `language`、`query_status`、`result_set_id`

### 3.4 `SearchResultSet`

- 资源职责：表达搜索结果集
- 核心字段：`result_set_id`、`search_query_id`、`result_status`、
  `item_list`、`facet_list`、`total`

### 3.5 `AiApplicationJob`

- 资源职责：表达一次 AI 业务应用任务受理
- 核心字段：`ai_application_job_id`、`application_type`、`contract_id`、
  `job_status`、`result_id`

### 3.6 `AiApplicationResult`

- 资源职责：表达统一 AI 业务应用结果视图
- 核心字段：`result_id`、`application_type`、`contract_id`、
  `result_status`、`result_summary`、`structured_payload`、`citation_list`

### 3.7 `SummaryResult`

- 资源职责：表达摘要类结果
- 核心字段：`summary_result_id`、`summary_scope`、`summary_text`、
  `section_list`、`citation_list`

### 3.8 `QaSession`

- 资源职责：表达问答会话与消息上下文
- 核心字段：`qa_session_id`、`contract_id`、`session_status`、
  `session_language`、`answer_language`、`message_list`

### 3.9 `RiskAnalysis`

- 资源职责：表达风险识别结果
- 核心字段：`risk_analysis_id`、`contract_id`、`result_status`、
  `risk_item_list`、`overall_risk_level`、`citation_list`

### 3.10 `DiffExtractionResult`

- 资源职责：表达字段提取、条款提取与差异比对结果
- 核心字段：`diff_extraction_result_id`、`contract_id`、`result_status`、
  `comparison_pair`、`field_extraction_list`、`clause_diff_list`、`citation_list`

### 3.11 资源关系约束

- 一个 `OcrJob` 对应零个或一个 `OcrResult`
- 一个 `SearchQuery` 对应零个或一个 `SearchResultSet`
- 一个 `AiApplicationJob` 对应零个或一个 `AiApplicationResult`
- `SummaryResult`、`RiskAnalysis`、`DiffExtractionResult` 是
  `AiApplicationResult` 在特定应用类型下的稳定视图
- 一个 `QaSession` 可关联多个问答消息与多个 AI 任务，但会话本身不等于最终结果

## 4. 统一约定

### 4.1 协议

- 默认继承 [`总平台 API Design`](../../api-design.md) 的统一约定
- 协议采用 `HTTPS + JSON`
- 编码采用 `UTF-8`
- 时间字段采用 `ISO 8601`
- 分页参数采用 `page`、`page_size`
- 成功、失败、分页响应结构继承总平台统一响应壳层

### 4.2 鉴权

- 业务前端访问时，使用平台统一登录态与访问令牌
- 平台内部模块访问时，使用服务账号或系统间凭证
- 任务创建、结果查询、回写、审计查询都必须同时校验功能权限与数据权限
- 回调接口必须支持签名校验、时间戳校验与重放防护
- 涉及跨合同、跨部门、跨语言内容汇聚时，权限边界优先于智能能力边界

### 4.3 幂等

- `POST /api/intelligent-applications/ocr-jobs`
- `POST /api/intelligent-applications/search-queries`
- `POST /api/intelligent-applications/ai-application-jobs`
- `POST /api/intelligent-applications/summary-jobs`
- `POST /api/intelligent-applications/qa-sessions`
- `POST /api/intelligent-applications/risk-analyses`
- `POST /api/intelligent-applications/diff-extraction-jobs`

以上接口应支持 `Idempotency-Key`。

- 相同幂等键且请求体一致时，返回首个受理结果
- 相同幂等键但请求体不一致时，复用总平台 `40905 IDEMPOTENCY_CONFLICT`

### 4.4 命名规范

- 路径资源段统一使用 `kebab-case`
- 路径参数、请求字段、响应字段、查询参数统一使用 `snake_case`
- 主键字段遵循 `<resource>_id`
- 状态字段遵循 `<domain>_status`
- 枚举值统一使用 `UPPER_SNAKE_CASE`
- 命名规范整体继承 [`总平台 API Design`](../../api-design.md)

### 4.5 错误码复用策略

本主线默认复用总平台错误码体系，不单独复制通用错误域。

优先复用的错误码包括：

- 通用校验：`40001 INVALID_PAYLOAD`、`40002 INVALID_FIELD_VALUE`、
  `40003 INVALID_QUERY_PARAMS`
- 鉴权与权限：`40101 AUTH_REQUIRED`、`40102 AUTH_TOKEN_INVALID`、
  `40103 CALLBACK_SIGNATURE_INVALID`、`40301 PERMISSION_DENIED`
- 资源不存在：`40401 CONTRACT_NOT_FOUND`、`40403 EXTERNAL_INSTANCE_NOT_FOUND`
- 状态与幂等：`40905 IDEMPOTENCY_CONFLICT`
- 文件与智能任务：`42201 FILE_VALIDATION_FAILED`、`42205 AI_TASK_FAILED`、
  `42206 OCR_TASK_FAILED`
- 平台异常：`50001 INTERNAL_SERVER_ERROR`、`50201 EXTERNAL_SYSTEM_UNAVAILABLE`、
  `50301 ASYNC_JOB_BACKLOG`

本主线仅补充主线专属错误语义：

| HTTP 状态码 | 业务错误码 | 错误名称 | 使用场景 |
| --- | --- | --- | --- |
| `404` | `40421` | `OCR_JOB_NOT_FOUND` | `ocr_job_id` 不存在 |
| `404` | `40422` | `OCR_RESULT_NOT_FOUND` | `ocr_result_id` 不存在 |
| `404` | `40423` | `SEARCH_QUERY_NOT_FOUND` | `search_query_id` 不存在 |
| `404` | `40424` | `SEARCH_RESULT_SET_NOT_FOUND` | `result_set_id` 不存在 |
| `404` | `40425` | `AI_APPLICATION_JOB_NOT_FOUND` | `ai_application_job_id` 不存在 |
| `404` | `40426` | `AI_APPLICATION_RESULT_NOT_FOUND` | `result_id` 不存在 |
| `404` | `40427` | `QA_SESSION_NOT_FOUND` | `qa_session_id` 不存在 |
| `409` | `40921` | `AI_APPLICATION_STATUS_CONFLICT` | 当前结果状态不允许回写、确认或继续追问 |
| `422` | `42221` | `SEARCH_SCOPE_INVALID` | 查询范围与权限或资源类型不兼容 |
| `422` | `42222` | `CITATION_REQUIRED` | 问答、风险识别等结果缺少引用依据 |
| `422` | `42223` | `HUMAN_CONFIRMATION_REQUIRED` | 结果尚需人工确认，不能直接释放 |

## 5. 与合同主档的接口边界

- 合同主档是业务真相源，本主线 API 只引用 `contract_id`，不重新定义合同一级资源。
- 本主线允许读取合同摘要、合同分类、合同归属、主文档引用等受控字段，
  作为搜索筛选与 AI 应用上下文。
- 本主线不直接更新 `contract_status`、`archive_status`、`signature_status` 等
  一级业务状态。
- 本主线若需要将摘要、风险标签、提取结果回写到合同相关视图，
  应通过合同管理本体暴露的受控写入口承接，而不是在本主线内部把派生结果写成新主档。

推荐交互边界：

- 读取：复用 [`合同管理本体 API Design`](../contract-core/api-design.md)
  中 `GET /api/contracts/{contract_id}/master`、
  `GET /api/contracts/{contract_id}`、
  `GET /api/contracts/{contract_id}/summary`
- 回写：由合同管理本体后续受控写接口承接摘要引用、风险视图引用、
  提取结果引用，不在本主线 API 中越权定义合同主档写入语义

## 6. 与文档中心的接口边界

- 文档中心是文件真相源；本主线只接收 `document_asset_id`、
  `document_version_id` 等稳定引用。
- `OCR`、搜索索引、AI 应用都以文档中心受控版本为输入基线。
- 本主线不创建长期“AI 专用文件对象”，不定义文档主档、版本链、
  预览产物、下载授权等资源。
- `OCR` 结果、搜索文本片段、AI 引用片段都必须回指文档中心对象坐标。

推荐交互边界：

- 输入读取：复用 [`文档中心 API Design`](../document-center/api-design.md)
  中 `GET /api/document-center/assets/{document_asset_id}`、
  `GET /api/document-center/versions/{document_version_id}`、
  文档摘要视图与挂接视图接口
- 内部挂接：与文档中心内部能力挂接时，复用其
  `POST /api/internal/document-center/ocr-jobs`、
  `POST /api/internal/document-center/search-index-jobs`
  所表达的“基于受控版本创建作业”边界

## 7. 与 Agent OS 的接口边界

- `Agent OS` 是 AI 运行时底座；本主线不定义模型调度、工具路由、
  人格装配、运行时循环的内部契约。
- 本主线的摘要、问答、风险识别、比对提取都通过统一 AI 任务入口向
  `Agent OS` 发起受控调用。
- 本主线对外返回业务应用结果，不直接返回 `Agent OS` 的底层运行实例、
  Provider 原始响应或 Prompt 组装细节。
- 高风险结果的人机确认能力由 `Agent OS` 底座承接，本主线只暴露
  `human_confirmation_required` 之类业务可见状态。

推荐交互边界：

- 任务受理与运行：复用 [`Agent OS API Design`](../../foundations/agent-os/api-design.md)
  中 `POST /api/agent-os/tasks`、`GET /api/agent-os/tasks/{task_id}/result`
- 人工确认与审计：复用 `GET /api/agent-os/human-confirmations/{confirmation_id}`、
  `GET /api/agent-os/tasks/{task_id}/audit-view`

## 8. 与条款库 / 模板库的接口边界

- 条款库是正式业务资源，也是 AI 应用的重要底座。
- 模板库是正式业务资源，用于提供合同标准结构、标准条款绑定与上下文背景。
- 本主线可以读取条款与模板，但不拥有条款、模板资源的生命周期管理接口。
- 搜索可把条款库纳入正式搜索范围；AI 应用可把条款、模板作为 grounding
  与比对基线，但条款、模板的启停与版本归口仍在合同管理本体。

推荐交互边界：

- 条款读取：复用 [`合同管理本体 API Design`](../contract-core/api-design.md)
  中 `GET /api/clauses`、`GET /api/clauses/{clause_id}`
- 模板读取：复用 `GET /api/templates`、`GET /api/templates/{template_id}`

边界约束：

- 本主线不新增“AI 专用条款库”资源族
- 条款命中、模板匹配、风险对照、差异比对结果都必须回指正式条款 / 模板资源

## 9. 多语言支持在 API 层的体现

- 一期正式支持中文、英文、西文；多语言不是备注项，而是正式接口约束。
- 读取类接口支持 `Accept-Language` 请求头；需要显式指定输入 / 输出语言时，
  同时支持请求字段 `language`、`response_language`。
- 搜索接口可区分 `keyword_language` 与结果 `matched_language`，
  以表达跨语言查询与跨语言召回。
- `OCR` 结果返回 `detected_language_list`，便于后续搜索、问答、摘要、
  风险识别按语言路由。
- 问答与摘要类接口应允许“输入语言”和“输出语言”分离，
  但引用依据仍需保留原始语言坐标。
- 返回对象中的可翻译枚举、标签、应用名称等字段，继承总平台规则，
  可返回 `label` 或 `i18n_key`。

边界约束：

- 多语言处理不改变合同主档和文档中心的真相归属
- 翻译文本、跨语言检索结果、跨语言摘要都属于派生结果，不替代原始正文

## 10. 异步与回调边界

### 10.1 异步任务边界

- `OCR`、摘要、问答、风险识别、比对提取默认按异步任务受理
- 搜索默认支持同步结果返回；当请求范围过大或需要复杂聚合时，
  可退化为异步结果集生成，但对外仍以 `SearchQuery` / `SearchResultSet`
  契约表达
- 异步任务创建成功返回 `202` 与任务资源主键
- 任务最终结果通过任务查询接口或结果接口查询，统一返回 `job_status`、
  `result_status`、`result_code`、`result_message`

### 10.2 回调接口边界

#### `POST /api/intelligent-applications/callbacks/ocr-jobs/{ocr_job_id}`

用途：接收外部或底层识别作业回调结果。

#### `POST /api/intelligent-applications/callbacks/ai-application-jobs/{ai_application_job_id}`

用途：接收底层 AI 作业回调结果。

回调请求重点字段：

- `job_status`
- `result_status`
- `result_ref`
- `result_code`
- `result_message`
- `callback_timestamp`
- `signature`

回调边界约束：

- 回调只表达结果回传与状态推进，不暴露底层模型或识别引擎私有协议
- 回调成功与否都应返回总平台统一响应壳层
- 回调鉴权、时间戳、重放防护继承总平台规则

## 11. 需要下沉到本主线 Detailed Design 的内容边界

以下内容应下沉到后续本主线 [`Detailed Design`](./detailed-design.md)：

- `OCR` 结果内部结构、版面块模型、字段候选质量分级
- 搜索索引模型、召回策略、排序策略、结果融合策略
- AI 应用上下文装配、工具调用、结果校验与人工确认触发细节
- 条款 / 模板 / 合同 / 文档 / 搜索结果在应用层的内部组合策略
- 多语言术语归一、语言识别、跨语言召回与翻译缓存的内部实现
- 异步任务编排、失败恢复、重试、补偿与审计落库细节

以下内容不在本文继续展开：

- 物理表结构与索引设计
- 向量参数、召回参数、Prompt 模板、模型超参数
- 具体模型、Provider、识别引擎、排序实现的选型细节

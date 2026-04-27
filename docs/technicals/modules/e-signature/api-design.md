# 电子签章子模块 API Design

## 1. 文档说明

本文档是 `CMP` 电子签章子模块的第一份正式 `API Design`。

本文在以下输入文档约束下，定义电子签章子模块的资源边界、
请求/响应契约、鉴权约束、错误码复用策略、异步与回调边界，
以及与合同主档、文档中心、流程引擎、归档、搜索、AI 等模块
之间的接口边界。

### 1.1 输入

- 上游需求基线：[`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- 总平台架构：[`Architecture Design`](../../architecture-design.md)
- 总平台接口规范：[`API Design`](../../api-design.md)
- 总平台共享内部边界：[`Detailed Design`](../../detailed-design.md)
- 电子签章子模块架构：[`Architecture Design`](./architecture-design.md)

### 1.2 输出

- 电子签章子模块的正式 API 资源与接口分组
- 签章申请、签章会话、签章结果、签章摘要的契约边界
- 面向合同主档、文档中心、流程引擎及周边模块的接口边界
- 需要继续下沉到本模块 [`Detailed Design`](./detailed-design.md)
  的内部设计边界

### 1.3 阅读边界

本文只描述 API 可见契约，不展开以下内容：

- 不复述需求范围、建设目标、技术选型理由
- 不写物理表结构、证书存储、印章介质、坐标算法、验签算法细节
- 不写签章内部子任务状态机、补偿参数、重试编排与存储实现
- 不写签章坐标定位、盖章渲染、文件二进制处理细节
- 不写实施排期、联调日程、负责人安排

## 2. API 边界

### 2.1 子模块对外接口总览

- 电子签章子模块对外暴露五类正式接口：签章申请接口、签章会话接口、
  签章结果查询接口、签章回写接口边界、纸质备案辅助接口边界。
- 签章动作建立在审批通过后的正式合同主链上；默认审批主路径优先走 `OA`，
  但签章准入判断以平台已承接的审批通过事实为准。
- 合同主档是业务真相源，文档中心是文件真相源；电子签章不拥有这两类真相源。
- 子模块可以维护签章专业语义，但不得把内部过程状态整体外泄为合同主档字段，
  也不得把待签稿、签章稿、验签产物写成模块私有文件真相。
- 本模块是平台内部正式子模块，不以外部测试账号作为成立前提；
  系统间协作通过平台内部接口和受控回调边界完成。

### 2.2 签章申请接口

资源定位：`SignatureRequest` 表示一笔基于正式合同主链发起的签章申请，
用于固化“针对哪份合同、哪一份正式待签版本、按什么签署策略发起签章”。

#### `POST /api/contracts/{contract_id}/signatures/apply`

用途：发起电子签章申请。

请求重点字段：

- `main_document_asset_id`
- `main_document_version_id`
- `signature_mode`：如 `ELECTRONIC` / `PAPER_RECORD`
- `seal_scheme_id`
- `signer_list`
- `sign_order_mode`
- `biz_note`

响应重点字段：

- `signature_request_id`
- `contract_id`
- `request_status`
- `signature_status`
- `current_session_id`
- `summary_ref`

边界说明：

- 申请接口只受理签章准入，不直接承诺签章动作同步完成。
- 申请前置校验至少包括：合同处于允许签章的业务状态、审批通过事实已成立、
  待签署文件版本仍为有效正式输入稿、调用方具备签章发起权限。
- 申请接口只接收文档中心中的文件引用，不接收脱离文档中心治理的独立文件副本。

#### `GET /api/signature-requests/{signature_request_id}`

用途：查询单笔签章申请详情。

响应重点字段：

- `signature_request_id`
- `contract_id`
- `request_status`
- `signature_status`
- `main_document_asset_id`
- `main_document_version_id`
- `current_session_id`
- `latest_result_id`
- `created_at`
- `created_by`

### 2.3 签章任务 / 会话接口

资源定位：`SignatureSession` 表示一次对外可见的签署会话或签署执行窗口，
用于表达当前签章进行到哪一轮、当前需要谁参与、当前会话是否完成。

API 边界判断：

- 对外主资源统一使用 `SignatureSession`，不把更细粒度的内部任务拆成公开资源。
- 会话接口只暴露当前会话级进度与参与方摘要，不暴露内部子步骤、
  临时渲染状态、坐标计算过程或底层签章引擎执行细节。

#### `GET /api/signature-sessions/{signature_session_id}`

用途：查询单个签章会话详情。

响应重点字段：

- `signature_session_id`
- `signature_request_id`
- `session_status`
- `sign_order_mode`
- `current_signer_list`
- `pending_signer_count`
- `started_at`
- `expires_at`
- `completed_at`

#### `GET /api/signature-requests/{signature_request_id}/sessions`

用途：查询某笔签章申请下的会话列表或当前会话摘要。

响应项重点字段：

- `signature_session_id`
- `session_status`
- `current_signer_list`
- `started_at`
- `completed_at`

### 2.4 签章结果查询接口

资源定位：`SignatureResult` 表示一次签章执行后的受控结果视图，
承接签署结果、验签结果、结果文件引用与失败摘要。

#### `GET /api/signature-results/{signature_result_id}`

用途：查询单个签章结果详情。

响应重点字段：

- `signature_result_id`
- `signature_request_id`
- `signature_session_id`
- `result_status`
- `verification_status`
- `signed_document_asset_id`
- `signed_document_version_id`
- `result_message`
- `completed_at`

#### `GET /api/contracts/{contract_id}/signatures/summary`

用途：查询合同侧签章摘要，供合同详情、台账、归档准备页、搜索投影等复用。

响应重点字段：

- `contract_id`
- `signature_status`
- `latest_signature_request_id`
- `latest_signature_result_id`
- `latest_signed_document_asset_id`
- `latest_signed_document_version_id`
- `signed_at`
- `verification_status`
- `display_text`

边界说明：

- `SignatureResult` 是签章结果视图，不等于文档中心文件真相本体。
- 结果查询接口只返回签章模块可见的结果摘要和文档引用，
  不返回签章坐标、证书原文、文件二进制或内部引擎调试信息。
- 合同侧摘要只保留业务需要的稳定结果，不承接签章模块全部过程态。

### 2.5 签章回写接口边界

本组接口是平台内部接口边界，用于把签章结果回收到合同主档与文档中心。
它们不作为普通业务用户直接调用的入口。

#### `POST /api/internal/e-signature/contracts/{contract_id}/writeback`

用途：向合同主档回写签章摘要、签章状态和时间线事件。

请求重点字段：

- `signature_request_id`
- `signature_result_id`
- `signature_status`
- `signed_at`
- `verification_status`
- `summary_payload`
- `timeline_event_type`

响应重点字段：

- `contract_id`
- `writeback_status`
- `accepted_at`

边界说明：

- 回写合同主档时，只回写合同业务需要的摘要、状态判断和时间线事件。
- 不把签章会话内部过程状态、参与方临时态、引擎细节直接塞进合同主档接口。
- 回写必须以 `contract_id + signature_request_id + signature_result_id` 或等价键具备幂等性。

#### `POST /api/internal/e-signature/document-center/writeback`

用途：向文档中心回写签章结果稿、验签产物引用或结果摘要。

请求重点字段：

- `contract_id`
- `signature_request_id`
- `signature_result_id`
- `source_document_asset_id`
- `source_document_version_id`
- `signed_document_role`
- `signed_file_ref`
- `verification_artifact_ref_list`

响应重点字段：

- `signed_document_asset_id`
- `signed_document_version_id`
- `writeback_status`
- `accepted_at`

边界说明：

- 文档中心负责结果稿版本成立；电子签章只负责提交受控结果引用和业务语义。
- 如回写文档中心失败，签章模块可保留结果状态并进入补偿链路，
  但不得在模块内长期沉淀第二套文件真相。

### 2.6 纸质备案辅助接口边界

资源定位：纸质备案辅助接口用于登记线下签署已完成的纸质合同备案事实，
它属于签署链路的辅助边界，但不替代电子签章主资源。

#### `POST /api/contracts/{contract_id}/paper-records`

用途：登记纸质合同扫描件、签约人、签约日期及备案说明。

请求重点字段：

- `paper_record_type`
- `recorded_sign_date`
- `recorded_signer_list`
- `paper_document_asset_id`
- `paper_document_version_id`
- `record_note`

响应重点字段：

- `paper_record_id`
- `contract_id`
- `record_status`
- `linked_signature_summary_ref`

边界说明：

- 纸质备案接口只表达纸质签署事实登记与文件绑定，不扩展成纸质审批、归档、
  借阅等其他模块能力。
- 纸质扫描件仍必须由文档中心提供文件引用；电子签章模块不接收游离文件。

## 3. 核心资源划分

### 3.1 `SignatureRequest`

用途：表达一次签章申请的业务入口。

API 可见核心字段：

- `signature_request_id`
- `contract_id`
- `request_status`
- `signature_status`
- `main_document_asset_id`
- `main_document_version_id`
- `signature_mode`
- `seal_scheme_id`
- `current_session_id`
- `latest_result_id`
- `created_at`
- `created_by`

说明：

- `SignatureRequest` 只表达签章申请级语义。
- 更细的签章参数组合、内部策略求值与任务分裂逻辑下沉到
  [`Detailed Design`](./detailed-design.md)。

### 3.2 `SignatureSession`

用途：表达当前对外可见的一次签署会话。

API 可见核心字段：

- `signature_session_id`
- `signature_request_id`
- `session_status`
- `sign_order_mode`
- `current_signer_list`
- `pending_signer_count`
- `started_at`
- `expires_at`
- `completed_at`

说明：

- `SignatureSession` 用于承接会话级运行时视图。
- 内部任务拆分、执行步进、补偿步和网关交互细节不在本文展开。

### 3.3 `SignatureResult`

用途：表达一次签章执行后的结果视图。

API 可见核心字段：

- `signature_result_id`
- `signature_request_id`
- `signature_session_id`
- `result_status`
- `verification_status`
- `signed_document_asset_id`
- `signed_document_version_id`
- `result_message`
- `completed_at`

说明：

- `SignatureResult` 只表达受控结果，不替代文档中心版本资源。
- 验签原始细节、证书链、底层签章引擎返回载荷不在本文展开。

### 3.4 `SignatureSummary`

用途：向合同详情、台账、归档、搜索、AI 提供统一签章摘要。

API 可见核心字段：

- `contract_id`
- `signature_status`
- `latest_signature_request_id`
- `latest_signature_result_id`
- `latest_signed_document_asset_id`
- `latest_signed_document_version_id`
- `signed_at`
- `verification_status`
- `display_text`

说明：

- `SignatureSummary` 是稳定读模型，不等于完整签章过程记录。
- 其设计目标是让周边模块消费摘要，而不是依赖签章内部过程数据。

## 4. 统一约定

### 4.1 协议

- 协议采用 `HTTPS + JSON`。
- 编码采用 `UTF-8`。
- 时间字段采用 `ISO 8601`。
- 成功、失败、分页响应结构继承总平台
  [`API Design`](../../api-design.md) 的统一响应约定。
- 同步创建返回 `201`，同步查询返回 `200`，异步受理型回写或回调处理可返回 `202`。

### 4.2 鉴权

- 业务用户接口要求平台登录态、合同访问权限以及签章相关模块权限。
- 合同侧摘要查询至少要求合同查看权限；签章申请要求签章发起权限。
- 平台内部回写接口使用系统身份或服务间可信调用，不依赖普通用户登录态。
- 异步回调接口使用系统级签名校验、时间戳校验和重放校验。

### 4.3 幂等

- 签章申请、回写接口、回调接收接口均支持 `Idempotency-Key`。
- 同一 `contract_id` 上的重复签章申请，应基于合同状态、待签文件版本、
  业务幂等键返回首次结果或稳定冲突错误。
- 签章结果回写必须保证重复通知、乱序通知下仍得到稳定结果。

### 4.4 命名规范

- 路径、查询参数、请求字段、响应字段统一继承总平台
  [`API Design`](../../api-design.md) 的 `snake_case` 规范。
- 资源主键统一使用 `<resource>_id`，例如 `signature_request_id`、
  `signature_session_id`、`signature_result_id`。
- 状态枚举统一使用 `UPPER_SNAKE_CASE`。
- 合同侧状态字段继续复用总平台既有 `signature_status`，不新增平行命名体系。

### 4.5 错误码复用策略

本模块优先复用总平台 [`API Design`](../../api-design.md)
中的通用错误码，不新增平行体系。

优先复用的错误码包括：

- `40001 INVALID_PAYLOAD`
- `40002 INVALID_FIELD_VALUE`
- `40003 INVALID_QUERY_PARAMS`
- `40101 AUTH_REQUIRED`
- `40103 CALLBACK_SIGNATURE_INVALID`
- `40301 PERMISSION_DENIED`
- `40401 CONTRACT_NOT_FOUND`
- `40901 CONTRACT_STATUS_CONFLICT`
- `40903 SIGNATURE_STATUS_CONFLICT`
- `40905 IDEMPOTENCY_CONFLICT`
- `42201 FILE_VALIDATION_FAILED`
- `42202 ENCRYPTION_CHECK_FAILED`
- `42207 EXTERNAL_CALLBACK_PROCESSING_FAILED`
- `50001 INTERNAL_SERVER_ERROR`
- `50201 EXTERNAL_SYSTEM_UNAVAILABLE`

模块级补充原则：

- 仅当总平台错误码无法准确表达签章申请、会话或结果资源语义时，
  才在后续正式 `OpenAPI` 中补充签章域错误码。
- 新增签章域错误码时，仍需遵守总平台错误域分段规则，不单独定义新的返回结构。

## 5. 与合同主档的接口边界

### 5.1 边界说明

- 合同主档是业务真相源，签章模块通过 `contract_id` 挂接，不反向生成合同事实。
- 签章模块从合同主链接收“允许发起签章”的业务上下文，
  并向合同主档回写签章摘要、签章状态和时间线事件。
- 合同主档侧只消费 `SignatureSummary` 和稳定结果引用，
  不消费签章内部会话步骤、参与方临时态或引擎过程明细。

### 5.2 合同主档向签章模块发起

#### `POST /api/contracts/{contract_id}/signatures/apply`

用途：合同主链在审批通过后发起签章申请。

合同主档提供的最小上下文字段：

- `contract_id`
- `contract_status`
- `approval_summary_ref` 或等价审批通过事实引用
- `main_document_asset_id`
- `main_document_version_id`
- `signature_status`

### 5.3 签章模块向合同主档回写

#### `POST /api/internal/e-signature/contracts/{contract_id}/writeback`

回写最小结果字段：

- `signature_request_id`
- `signature_result_id`
- `signature_status`
- `signed_at`
- `verification_status`
- `summary_payload`

回写边界：

- 合同主档只保留对业务链有意义的签章摘要、关键时间和时间线事件。
- 签章模块不得要求合同主档承接完整签章过程状态树。

## 6. 与文档中心的接口边界

### 6.1 边界说明

- 文档中心是文件真相源，电子签章只消费和回写文档引用。
- 待签稿、签章结果稿、验签产物引用都必须落在文档中心版本治理下。
- 签章模块不直接维护文件版本链，也不以文件二进制作为长期模块私有状态。

### 6.2 从文档中心读取待签稿

#### `GET /api/document-center/assets/{document_asset_id}`

用途：读取待签署文档主档摘要，用于签章准入校验。

#### `GET /api/document-center/versions/{document_version_id}`

用途：读取待签署正式版本摘要，用于确认签章输入稿。

读取最小字段要求：

- `document_asset_id`
- `document_version_id`
- `document_role`
- `document_status`
- `encryption_status`
- `owner_type`
- `owner_id`

### 6.3 向文档中心回写签章结果

#### `POST /api/internal/e-signature/document-center/writeback`

用途：提交签章结果稿与验签结果引用，由文档中心建立正式结果版本。

文档中心返回的最小字段：

- `signed_document_asset_id`
- `signed_document_version_id`
- `writeback_status`

## 7. 与流程引擎的接口边界

### 7.1 边界说明

- 默认审批主路径优先走 `OA`，但签章模块不直接以外部系统原始状态作为准入依据。
- 签章准入统一消费平台承接后的审批通过事实。
- 流程引擎负责审批真相，签章模块负责签章真相；二者通过合同主链和审批摘要引用联动。

### 7.2 读取审批通过事实

#### `GET /api/contracts/{contract_id}/approval-summary`

用途：查询合同对应的统一审批摘要，确认是否满足签章申请前置条件。

最小字段要求：

- `contract_id`
- `approval_mode`
- `summary_status`
- `finished_at`
- `source_system`
- `source_system_instance_id`

边界说明：

- 签章模块只消费审批摘要或等价审批事实引用，不拉取完整审批过程明细。
- 审批被撤回、驳回、终止或待确认时，不得发起正式签章申请。

## 8. 与归档、搜索、AI 的接口边界

### 8.1 与归档的接口边界

- 归档模块消费签章摘要和已回收到文档中心的签章结果稿引用。
- 电子签章不直接创建归档主记录，不承担借阅、归还、归档封装等职责。

建议消费资源：

- `GET /api/contracts/{contract_id}/signatures/summary`

### 8.2 与搜索的接口边界

- 搜索模块只消费稳定签章摘要字段，例如 `signature_status`、`signed_at`、
  `verification_status` 和结果文档引用。
- 搜索索引不直接承接签章会话内部过程状态。

建议消费资源：

- `GET /api/contracts/{contract_id}/signatures/summary`

### 8.3 与 AI 的接口边界

- AI 只消费合同主档摘要、文档中心结果稿摘要和必要的签章摘要。
- 签章模块不向 AI 默认暴露内部签署过程、证书细节或引擎调试信息。
- AI 不直接驱动签章结果成立，只能读取受控摘要用于问答、提醒或审查辅助。

建议消费资源：

- `GET /api/contracts/{contract_id}/signatures/summary`
- `GET /api/signature-results/{signature_result_id}`

## 9. 异步与回调边界

### 9.1 异步边界

- 签章申请的受理可以是同步的，但会话建立、结果文件生成、验签结果整理、
  结果回写可按异步方式完成。
- API 对外只承诺申请受理结果、当前会话状态和最终结果查询接口，
  不承诺内部异步步骤拆分方式。

### 9.2 回调边界

#### `POST /api/internal/e-signature/callbacks/results`

用途：接收签章执行结果或等价系统回执，并推进结果登记与回写链路。

请求重点字段：

- `signature_request_id`
- `signature_session_id`
- `external_event_id`
- `result_status`
- `verification_status`
- `signed_file_ref`
- `event_occurred_at`

响应重点字段：

- `accept_status`
- `accepted_at`
- `trace_id`

回调约束：

- 必须执行签名校验、时间戳校验、重放校验和幂等校验。
- 重复回调、乱序回调不得破坏最终稳定结果。
- 回调接口只承接平台内受控协作边界，不写成“依赖外部测试账号先联调才能成立”的前置接口。

## 10. 需要下沉到 Detailed Design 的内容边界

下列内容应继续下沉到本模块 [`Detailed Design`](./detailed-design.md)，
而不在本文展开：

- `SignatureRequest`、`SignatureSession`、`SignatureResult` 的内部模型、表结构与状态机细分
- 签署参与方编排、内部子任务拆分、补偿机制、重试机制与恢复策略
- 印章授权、签章样式、坐标定位、证书存储、验签算法与结果持久化细节
- 结果文件生成、临时文件处理、文件引用转换与存储实现细节
- 平台内部服务鉴权介质、回调签名算法、任务主题与事件载荷细节

本文到此为止，保持在子模块 `API Design` 层回答“接口契约如何成立、
与谁交互、哪些结果可见、哪些实现细节必须继续下沉”。

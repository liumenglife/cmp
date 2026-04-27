# 合同后续业务组 API Design

## 1. 文档说明

本文档是“合同后续业务组 / `contract-lifecycle`”的第一份正式
`API Design`。

本文在以下输入文档约束下，定义履约、变更、终止、归档这组业务模块的
API 资源边界、请求/响应契约、鉴权约束、错误码复用策略，以及与合同主档、
文档中心、流程引擎、搜索、AI、审计、通知之间的接口边界。

### 1.1 输入

- 上游需求基线：[`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- 总平台架构：[`Architecture Design`](../../architecture-design.md)
- 总平台接口规范：[`API Design`](../../api-design.md)
- 总平台共享内部边界：[`Detailed Design`](../../detailed-design.md)
- 合同后续业务组架构：[`Architecture Design`](./architecture-design.md)
- 合同管理本体子模块接口：[`contract-core API Design`](../contract-core/api-design.md)

### 1.2 输出

- 本文：[`API Design`](./api-design.md)
- 合同后续业务组的正式资源与接口分组
- 与合同主档、文档中心、流程引擎及搜索、AI、审计、通知的接口边界
- 需要继续下沉到本业务组 [`Detailed Design`](./detailed-design.md)
  的内部设计边界

### 1.3 阅读边界

本文只描述 API 可见契约，不展开以下内容：

- 不复述需求范围、建设目标、技术选型理由
- 不写物理表结构、索引、归档目录、文件封包格式、搜索映射
- 不展开履约、变更、终止、归档的节点级状态机与流程编排细节
- 不写流程节点规则、文档版本链、AI Prompt、通知模板实现
- 不写实施排期、任务拆分、负责人安排

## 2. API 边界

### 2.1 业务组对外接口总览

- 本业务组对外暴露五类正式接口：履约、变更、终止、归档、摘要 / 时间线。
- 四类过程资源各自独立建模，不合并为单一“生命周期大资源”。
- 四类过程资源共享同一条 `contract_id` 主链，均以合同主档为业务入口与归属键。
- 合同主档是业务真相源，后续业务组负责过程资源、稳定摘要、里程碑与时间线出口。
- 文档中心是文件真相源；本业务组接口只暴露文件引用与文件摘要，不暴露文件版本链。
- 流程引擎是审批运行时真相源；本业务组接口只暴露审批发起、审批摘要引用与结果回写边界。
- 搜索、AI、审计、通知只消费本业务组输出的稳定摘要与事件，不直接把过程记录当作唯一来源。

### 2.2 履约接口

资源定位：履约由 `PerformanceRecord` 与 `PerformanceNode` 共同组成。
其中 `PerformanceRecord` 表达围绕单合同的履约聚合视图，`PerformanceNode`
表达可独立跟踪的履约节点。

#### `GET /api/contracts/{contract_id}/performance-record`

用途：查询单合同履约总览。

响应重点字段：

- `performance_record_id`
- `contract_id`
- `performance_status`
- `performance_progress`
- `risk_level`
- `latest_node_due_at`
- `open_issue_count`
- `summary_text`

#### `PATCH /api/contracts/{contract_id}/performance-record`

用途：更新履约聚合层允许直接维护的说明性字段。

请求重点字段：

- `performance_owner_user_id`
- `performance_owner_org_unit_id`
- `summary_text`
- `risk_level`
- `remark`

边界说明：

- 该接口不直接编辑具体履约节点，不替代 `PerformanceNode` 写接口。
- 聚合层字段用于承接履约负责人、总体说明、风险摘要等稳定信息。

#### `GET /api/contracts/{contract_id}/performance-nodes`

用途：按合同查询履约节点列表。

查询参数重点：

- `node_type`
- `node_status`
- `due_at_start`
- `due_at_end`
- `has_risk`
- `page`
- `page_size`

#### `POST /api/contracts/{contract_id}/performance-nodes`

用途：新增履约节点；该接口同时是合同主链进入履约跟踪的正式入口。

请求重点字段：

- `node_type`
- `node_name`
- `planned_at`
- `due_at`
- `owner_user_id`
- `owner_org_unit_id`
- `document_reference_list`
- `remark`

响应重点字段：

- `performance_record_id`
- `performance_node_id`
- `contract_id`
- `performance_status`
- `summary_ref`

#### `GET /api/performance-nodes/{performance_node_id}`

用途：查询单个履约节点详情。

#### `PATCH /api/performance-nodes/{performance_node_id}`

用途：更新履约节点进展、结果摘要与凭证引用。

请求重点字段：

- `node_status`
- `actual_at`
- `progress_percent`
- `result_summary`
- `risk_level`
- `document_reference_list`
- `issue_list`

边界说明：

- `PerformanceNode` 是履约执行的最小正式资源。
- 履约节点可回指文档中心中的交付证明、验收材料、付款凭证等文件，但不复制文件内容。
- 履约接口只维护履约过程，不直接改写合同主档分类、编号或主文档引用。

### 2.3 变更接口

资源定位：`ContractChange` 表达原合同主链上的一次正式变更事项。
变更不会创建新合同主档，只会在同一 `contract_id` 下形成新的变更记录与有效摘要。

#### `GET /api/contracts/{contract_id}/changes`

用途：查询某合同的变更记录列表。

查询参数重点：

- `change_status`
- `change_type`
- `submitted_at_start`
- `submitted_at_end`
- `page`
- `page_size`

#### `POST /api/contracts/{contract_id}/changes`

用途：发起合同变更。

请求重点字段：

- `change_type`
- `change_reason`
- `change_summary`
- `impact_scope`
- `effective_date`
- `document_reference_list`
- `workflow_mode`

响应重点字段：

- `change_id`
- `contract_id`
- `change_status`
- `workflow_instance_ref`
- `summary_ref`

#### `GET /api/changes/{change_id}`

用途：查询单次变更详情。

#### `PATCH /api/changes/{change_id}`

用途：更新尚未封存的变更说明、影响摘要与附件引用。

请求重点字段：

- `change_reason`
- `change_summary`
- `impact_scope`
- `effective_date`
- `document_reference_list`
- `remark`

#### `POST /api/changes/{change_id}/submit`

用途：提交变更进入正式审批或确认流程。

响应重点字段：

- `change_id`
- `contract_id`
- `change_status`
- `workflow_instance_ref`
- `accepted`

边界说明：

- 变更接口管理的是“变更事项”资源，不直接暴露补充协议文件版本链。
- 变更完成后输出的是变更结果摘要、时间线事件与合同侧当前有效信息回写边界。
- 是否形成新的业务有效版本、如何映射合同字段变更，留待本业务组 `Detailed Design`。

### 2.4 终止接口

资源定位：`ContractTermination` 表达原合同主链上的一次终止事项。
终止是合同后续阶段推进，不是删除合同，也不是创建新的终止合同主档。

#### `GET /api/contracts/{contract_id}/terminations`

用途：查询某合同的终止记录列表。

查询参数重点：

- `termination_status`
- `termination_type`
- `submitted_at_start`
- `submitted_at_end`
- `page`
- `page_size`

#### `POST /api/contracts/{contract_id}/terminations`

用途：发起合同终止。

请求重点字段：

- `termination_type`
- `termination_reason`
- `termination_summary`
- `requested_termination_date`
- `settlement_summary`
- `document_reference_list`
- `workflow_mode`

响应重点字段：

- `termination_id`
- `contract_id`
- `termination_status`
- `workflow_instance_ref`
- `summary_ref`

#### `GET /api/terminations/{termination_id}`

用途：查询单次终止详情。

#### `PATCH /api/terminations/{termination_id}`

用途：更新终止说明、依据材料引用与善后摘要。

请求重点字段：

- `termination_reason`
- `termination_summary`
- `requested_termination_date`
- `settlement_summary`
- `document_reference_list`
- `remark`

#### `POST /api/terminations/{termination_id}/submit`

用途：提交终止进入正式审批或确认流程。

边界说明：

- 终止接口只承接终止事项、终止依据、终止摘要与善后摘要。
- 终止完成后回写的是合同主档状态、终止里程碑与后续限制摘要，不暴露内部审批步骤明细。
- 终止相关协议、交接证明等文件仍由文档中心治理。

### 2.5 归档接口

资源定位：`ArchiveRecord` 表达围绕单合同产生的一次正式归档记录。
归档记录属于归档模块真相，但不能替代合同主档的业务真相地位。

#### `GET /api/contracts/{contract_id}/archive-records`

用途：查询合同归档记录列表。

查询参数重点：

- `archive_status`
- `archive_type`
- `archive_batch_no`
- `page`
- `page_size`

#### `POST /api/contracts/{contract_id}/archive`

用途：从合同主链发起归档，创建归档记录或归档任务。

请求重点字段：

- `archive_type`
- `archive_reason`
- `archive_scope`
- `document_reference_list`
- `archive_keeper_user_id`
- `archive_location_code`
- `remark`

响应重点字段：

- `archive_record_id`
- `contract_id`
- `archive_status`
- `accepted`
- `job_id`
- `summary_ref`

#### `GET /api/archive-records/{archive_record_id}`

用途：查询单个归档记录详情。

#### `PATCH /api/archive-records/{archive_record_id}`

用途：更新归档记录允许补录的归档元信息。

请求重点字段：

- `archive_keeper_user_id`
- `archive_location_code`
- `retention_policy_code`
- `remark`

#### `POST /api/archive/jobs`

用途：执行批量归档或重建归档产物的异步任务。

请求重点字段：

- `contract_id_list`
- `archive_type`
- `archive_reason`

边界说明：

- 单合同归档入口仍沿用合同主链路径，归档记录详情读取走 `ArchiveRecord` 资源路径。
- 归档接口返回的是归档记录、归档摘要与引用键，不暴露归档封包内部结构。
- 借阅、归还、调阅等档案过程资源属于归档子域，可在后续细化文档中继续展开。

### 2.6 摘要 / 时间线接口边界

资源定位：`LifecycleSummary` 是面向合同详情、搜索、AI、审计、通知复用的统一摘要资源；
时间线是围绕同一 `contract_id` 聚合后的稳定事件视图。

#### `GET /api/contracts/{contract_id}/lifecycle-summary`

用途：查询单合同后续业务摘要。

响应重点字段：

- `contract_id`
- `current_stage`
- `performance_summary`
- `change_summary`
- `termination_summary`
- `archive_summary`
- `latest_milestone`
- `pending_action_list`
- `timeline_ref`

#### `POST /api/contracts/lifecycle-summary-query`

用途：按合同集合批量查询后续业务摘要。

请求重点字段：

- `contract_id_list`
- `owner_org_unit_id`
- `current_stage`

#### `GET /api/contracts/{contract_id}/timeline`

用途：查询合同后续业务时间线。

查询参数重点：

- `event_type`
- `occurred_at_start`
- `occurred_at_end`
- `page`
- `page_size`

响应项重点字段：

- `timeline_event_id`
- `contract_id`
- `event_type`
- `event_title`
- `event_summary`
- `occurred_at`
- `source_resource_type`
- `source_resource_id`

边界说明：

- `LifecycleSummary` 是跨模块消费视图，不替代履约、变更、终止、归档四类过程资源。
- 时间线接口返回稳定事件摘要，不暴露流程节点图、文件版本链或内部补偿细节。

## 3. 核心资源划分

### 3.1 `PerformanceRecord`

用途：围绕单个 `contract_id` 汇总履约过程的正式聚合资源。

API 可见核心字段：

- `performance_record_id`
- `contract_id`
- `performance_status`
- `performance_progress`
- `risk_level`
- `performance_owner_user_id`
- `performance_owner_org_unit_id`
- `latest_node_due_at`
- `summary_text`

说明：

- `PerformanceRecord` 是履约聚合，不取代 `PerformanceNode`。
- 一个合同通常对应一个有效履约聚合视图，但其内部节点可有多个。

### 3.2 `PerformanceNode`

用途：履约执行中的最小正式资源。

API 可见核心字段：

- `performance_node_id`
- `performance_record_id`
- `contract_id`
- `node_type`
- `node_name`
- `node_status`
- `planned_at`
- `due_at`
- `actual_at`
- `progress_percent`
- `result_summary`
- `risk_level`
- `document_reference_list`

说明：

- `PerformanceNode` 承接交付、验收、付款、回款、服务、质保等节点语义。
- `document_reference_list` 只保存文档资产引用，不保存文件内容。

### 3.3 `ContractChange`

用途：原合同主链上的正式变更事项资源。

API 可见核心字段：

- `change_id`
- `contract_id`
- `change_type`
- `change_status`
- `change_reason`
- `change_summary`
- `impact_scope`
- `effective_date`
- `workflow_instance_ref`
- `document_reference_list`

说明：

- `ContractChange` 表达一次变更事项，不表达变更后完整合同主档。
- 变更结果通过合同主档摘要与时间线消费，不在本资源中复制新的合同主数据全集。

### 3.4 `ContractTermination`

用途：原合同主链上的正式终止事项资源。

API 可见核心字段：

- `termination_id`
- `contract_id`
- `termination_type`
- `termination_status`
- `termination_reason`
- `termination_summary`
- `requested_termination_date`
- `settlement_summary`
- `workflow_instance_ref`
- `document_reference_list`

说明：

- `ContractTermination` 表达一次终止处理，不替代合同主档和归档记录。
- 终止完成后，合同仍以同一 `contract_id` 继续被检索、审计与归档引用。

### 3.5 `ArchiveRecord`

用途：正式归档记录资源。

API 可见核心字段：

- `archive_record_id`
- `contract_id`
- `archive_type`
- `archive_status`
- `archive_batch_no`
- `archive_location_code`
- `archive_keeper_user_id`
- `retention_policy_code`
- `archived_at`
- `document_reference_list`

说明：

- `ArchiveRecord` 属于归档模块真相。
- `ArchiveRecord` 只表达归档结果与归档元信息，不接管合同主状态解释权。

### 3.6 `LifecycleSummary`

用途：统一后续业务摘要资源。

API 可见核心字段：

- `contract_id`
- `current_stage`
- `performance_summary`
- `change_summary`
- `termination_summary`
- `archive_summary`
- `latest_milestone`
- `pending_action_list`
- `timeline_ref`

说明：

- `LifecycleSummary` 面向工作台、搜索、AI、审计、通知等跨模块消费。
- 它是读模型资源，不接受直接编辑。

## 4. 统一约定

### 4.1 协议

- 协议采用 `HTTPS + JSON`。
- 编码采用 `UTF-8`。
- 时间字段采用 `ISO 8601`。
- 成功、失败、分页响应结构继承总平台
  [`API Design`](../../api-design.md) 的统一约定。
- 同步创建返回 `201`，同步查询和更新返回 `200`，异步受理返回 `202`。

### 4.2 鉴权

- 履约、变更、终止、归档、摘要、时间线接口要求平台登录态与合同数据访问权限。
- 履约节点维护要求合同操作权限及对应组织范围访问权限。
- 变更、终止、归档发起接口要求合同操作权限和当前阶段前置条件满足。
- 归档批量任务、归档元数据维护等管理接口要求归档管理权限。
- 与合同主档、文档中心、流程引擎、搜索、AI、审计、通知之间的内部调用，
  应通过平台统一系统身份或受控服务身份传递调用上下文。

### 4.3 幂等

- `POST /api/contracts/{contract_id}/performance-nodes`
- `POST /api/contracts/{contract_id}/changes`
- `POST /api/changes/{change_id}/submit`
- `POST /api/contracts/{contract_id}/terminations`
- `POST /api/terminations/{termination_id}/submit`
- `POST /api/contracts/{contract_id}/archive`
- `POST /api/archive/jobs`

以上写接口均支持 `Idempotency-Key`。

- 对同一合同、同一动作语义的重复提交，应返回首次处理结果或稳定冲突错误。
- 幂等键至少绑定 `contract_id + action_type` 或等价业务主键。
- 异步任务接口的幂等键应同时约束请求体摘要，避免同键异参覆盖。

### 4.4 命名规范继承

- 路径资源段使用 `kebab-case`，字段与查询参数使用 `snake_case`。
- 资源主键统一使用 `<resource>_id`。
- 状态枚举统一使用 `UPPER_SNAKE_CASE`。
- 路径、参数、响应字段命名整体继承总平台
  [`API Design`](../../api-design.md) 的规范，不新增平行风格。

### 4.5 错误码复用策略

本业务组优先复用总平台 [`API Design`](../../api-design.md)
中的通用错误码，不新增平行错误体系。

优先复用的错误码包括：

- `40001 INVALID_PAYLOAD`
- `40002 INVALID_FIELD_VALUE`
- `40003 INVALID_QUERY_PARAMS`
- `40101 AUTH_REQUIRED`
- `40301 PERMISSION_DENIED`
- `40302 CONTRACT_ACCESS_DENIED`
- `40401 CONTRACT_NOT_FOUND`
- `40901 CONTRACT_STATUS_CONFLICT`
- `40902 ARCHIVE_STATUS_CONFLICT`
- `40905 IDEMPOTENCY_CONFLICT`
- `42201 FILE_VALIDATION_FAILED`
- `42202 ENCRYPTION_CHECK_FAILED`
- `42207 EXTERNAL_CALLBACK_PROCESSING_FAILED`
- `50001 INTERNAL_SERVER_ERROR`
- `50201 EXTERNAL_SYSTEM_UNAVAILABLE`

模块级补充原则：

- 履约、变更、终止、归档优先通过资源字段与业务状态表达语义，不把业务阶段写进新错误码。
- 仅当总平台错误码无法准确表达模块级冲突时，才在后续正式 `OpenAPI`
  中补充业务组错误码。
- 新增错误码时仍沿用总平台统一响应结构。

## 5. 与合同主档的接口边界

### 5.1 边界说明

- 合同主档是业务真相源，统一持有 `contract_id`、主状态、分类主链和合同一级身份。
- 本业务组的全部正式资源都围绕同一 `contract_id` 建立，不创建新的合同主档。
- 履约、变更、终止、归档可以维护各自过程资源，但必须把稳定摘要、里程碑与时间线回写到合同主档消费面。
- 变更、终止推进的是原合同主链，不是复制合同后另起一条平行主链。
- 归档完成后，合同主档仍保留业务真相解释权；归档记录只表达归档域事实。

### 5.2 接口边界

#### `GET /api/contracts/{contract_id}/master`

用途：读取本业务组执行所需的合同主档基础上下文。

#### `GET /api/contracts/{contract_id}/summary`

用途：读取合同侧统一摘要，用于后续业务入口校验和跨模块工作台展示。

#### `POST /api/internal/contract-lifecycle/contracts/{contract_id}/summary-sync`

用途：向合同主档回写后续业务摘要、阶段里程碑与时间线引用。

请求重点字段：

- `current_stage`
- `performance_summary`
- `change_summary`
- `termination_summary`
- `archive_summary`
- `latest_milestone`
- `timeline_ref`

边界说明：

- 本业务组回写的是稳定摘要，不是合同主档全集字段覆盖。
- 合同编号、分类、主文档、主体信息等合同一级字段仍由合同主档治理。

## 6. 与文档中心的接口边界

### 6.1 边界说明

- 文档中心是文件真相源。
- 本业务组只维护履约凭证、补充协议、终止协议、交接材料、归档材料等文件引用关系。
- 文件上传、切主版本、预览生成、加密访问、受控解密下载都由文档中心承接。
- 归档记录可引用归档材料集合，但不复制文档版本链或存储定位细节。

### 6.2 接口边界

#### `POST /api/internal/contract-lifecycle/document-references`

用途：为履约节点、变更、终止、归档记录登记文档引用关系。

请求重点字段：

- `source_resource_type`
- `source_resource_id`
- `contract_id`
- `document_asset_id`
- `document_role`

#### `GET /api/internal/contract-lifecycle/contracts/{contract_id}/document-summary`

用途：读取后续业务侧所需的文件摘要视图。

响应重点字段：

- `contract_id`
- `document_reference_list`
- `encryption_status`
- `preview_status`
- `archive_material_ready`

边界说明：

- 本业务组不定义 `DocumentAsset`、`DocumentVersion` 的内部字段全集。
- 是否允许解密下载、如何生成归档稿、如何校验文件版本合法性，均回到文档中心。

## 7. 与流程引擎的接口边界

### 7.1 边界说明

- 流程引擎通过 `contract_id` 绑定合同，不拥有合同主档。
- 变更、终止、归档等正式动作通过流程引擎承接审批运行时。
- 履约过程如触发审批，也只提交流程请求，不在履约资源中复制流程实例结构。
- 本业务组只暴露流程发起、流程摘要引用与结果回写边界。

### 7.2 接口边界

#### `POST /api/internal/workflow-engine/lifecycle-requests`

用途：由本业务组发起变更、终止、归档等流程请求。

请求重点字段：

- `contract_id`
- `business_type`
- `source_resource_id`
- `workflow_mode`
- `submission_comment`

#### `GET /api/internal/workflow-engine/contracts/{contract_id}/workflow-summary`

用途：读取合同后续业务相关流程摘要。

响应重点字段：

- `contract_id`
- `business_type`
- `workflow_instance_ref`
- `summary_status`
- `current_node_name`
- `current_approver_list`

#### `POST /api/internal/contract-lifecycle/workflow-callbacks`

用途：流程引擎回写审批结果、撤回结果或异常结果。

请求重点字段：

- `contract_id`
- `business_type`
- `source_resource_id`
- `workflow_instance_ref`
- `callback_result`
- `callback_at`

边界说明：

- 回调只改变对应过程资源与摘要，不直接绕过本业务组写合同主档。
- 流程节点结构、组织绑定、会签聚合等细节下沉到流程引擎文档与后续详细设计。

## 8. 与搜索、AI、审计、通知的接口边界

### 8.1 与搜索的接口边界

- 搜索消费 `LifecycleSummary` 与时间线稳定事件，不直接读取过程资源内部草稿字段。
- 搜索结果入口应回到合同详情、履约节点详情、变更详情、终止详情或归档详情。
- 搜索索引是读模型，不反向定义业务真相。

代表接口：

- `GET /api/contracts/{contract_id}/lifecycle-summary`
- `GET /api/contracts/{contract_id}/timeline`
- `GET /api/internal/contract-lifecycle/contracts/{contract_id}/search-document`

### 8.2 与 AI 的接口边界

- AI 消费合同主档摘要、本业务组稳定摘要、时间线与文档中心正式文件上下文。
- AI 输出仅作为履约风险识别、变更影响分析、终止依据辅助判断、归档完整性检查的辅助结果。
- AI 不直接改写 `PerformanceRecord`、`ContractChange`、`ContractTermination`、`ArchiveRecord`。

代表接口：

- `GET /api/internal/contract-lifecycle/contracts/{contract_id}/analysis-context`
- `POST /api/ai/extractions`

### 8.3 与审计的接口边界

- 审计覆盖履约节点更新、变更提交、终止提交、归档发起、摘要回写、回调处理等关键动作。
- 审计日志是治理证据，不替代业务过程资源。
- 审计事件应能串联 `contract_id`、过程资源主键、流程实例引用与文档引用。

代表接口：

- `POST /api/internal/audit/events`

### 8.4 与通知的接口边界

- 通知消费履约节点到期、变更待办、终止审批、归档完成等稳定里程碑事件。
- 通知记录是投递结果，不是业务状态真相。
- 所有通知事件都应围绕同一 `contract_id` 组织上下文。

代表接口：

- `POST /api/internal/notifications/events`

## 9. 异步与回调边界

### 9.1 异步边界

- 批量归档、归档产物重建、摘要重算、搜索索引刷新、AI 分析建议等场景采用异步接口。
- 异步受理成功返回 `202`、`job_id` 与最小结果摘要。
- 任务最终状态通过任务查询接口或资源详情中的任务摘要字段读取。

代表接口：

- `POST /api/archive/jobs`
- `GET /api/archive/jobs/{job_id}`
- `POST /api/internal/contract-lifecycle/contracts/{contract_id}/summary-rebuild-jobs`

### 9.2 回调边界

- 流程引擎回调、文档中心异步校验结果、搜索索引刷新回执等都通过受控内部回调进入本业务组。
- 回调接口必须支持来源身份校验、重放防护、幂等处理与 `trace_id` 透传。
- 回调成功只表示平台已接收并完成校验，不等于业务结果一定成功落库；业务失败仍通过统一错误结构返回。

代表接口：

- `POST /api/internal/contract-lifecycle/workflow-callbacks`
- `POST /api/internal/contract-lifecycle/document-callbacks`
- `POST /api/internal/contract-lifecycle/index-callbacks`

## 10. 需要下沉到本业务组 Detailed Design 的内容边界

- `PerformanceRecord` 与 `PerformanceNode` 的内部状态映射、节点类型细分与完成判定规则
- `ContractChange`、`ContractTermination`、`ArchiveRecord` 的内部字段全集与状态迁移细节
- 变更、终止、归档与流程引擎之间的时序、补偿、回调重试与失败恢复策略
- 履约凭证、补充协议、终止材料、归档材料的 `document_role` 字典与校验规则
- `LifecycleSummary` 的生成规则、时间线去重规则、事件排序规则与回写触发条件
- 归档批量任务、摘要重建任务、索引刷新任务的任务编排与重试策略
- 合同主档字段回写映射、权限细粒度校验与跨模块事务边界
- 搜索文档结构、AI 上下文拼装、通知模板路由、审计事件明细结构

## 11. 本文结论

合同后续业务组的 API 设计采用“分资源、同主链”的边界：

- 履约由 `PerformanceRecord` 与 `PerformanceNode` 承接
- 变更由 `ContractChange` 承接
- 终止由 `ContractTermination` 承接
- 归档由 `ArchiveRecord` 承接
- 跨模块消费统一通过 `LifecycleSummary` 与时间线接口承接

在这一边界下：

- 合同主档继续保持业务真相源地位
- 文档中心继续保持文件真相源地位
- 流程引擎继续通过 `contract_id` 绑定合同并提供审批运行时
- 搜索、AI、审计、通知继续只消费稳定摘要与事件

这也是本业务组后续进入 `Detailed Design` 与正式 `OpenAPI` 定稿前的统一接口基线。

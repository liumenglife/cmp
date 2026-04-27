# 合同管理本体子模块 API Design

## 1. 文档说明

本文档是 `CMP` 合同管理本体子模块的第一份正式 `API Design`。

本文在以下输入文档约束下，定义合同管理本体子模块的资源边界、
请求/响应契约、鉴权约束、错误码复用策略，以及与文档中心、流程引擎、
签章、履约、变更、终止、归档、搜索、AI 等模块的接口边界。

### 1.1 输入

- 上游需求基线：[`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- 总平台架构：[`Architecture Design`](../../architecture-design.md)
- 总平台接口规范：[`API Design`](../../api-design.md)
- 总平台共享内部边界：[`Detailed Design`](../../detailed-design.md)
- 合同管理本体子模块架构：[`Architecture Design`](./architecture-design.md)

### 1.2 输出

- 合同管理本体子模块的正式 API 资源与接口分组
- 合同主档、台账视图、详情视图、模板、条款、合同草稿、合同摘要的契约边界
- 合同主链与文档中心、流程引擎及其他周边模块之间的接口边界
- 需要继续下沉到本模块 [`Detailed Design`](./detailed-design.md)
  的内部设计边界

### 1.3 阅读边界

本文只描述 API 可见契约，不展开以下内容：

- 不复述需求范围、建设目标、技术选型理由
- 不写物理表结构、索引、存储目录、搜索映射或缓存键
- 不展开合同生命周期内部状态机、流程编排细节、补偿细节
- 不写正文编辑器实现、条款推荐算法、AI Prompt、模型选择细节
- 不写实施排期、任务拆分、负责人安排

## 2. API 边界

### 2.1 子模块对外接口总览

- 合同管理本体对外暴露六类正式接口：合同主档、合同台账与详情视图、
  模板库 / 条款库、合同草稿入口、生命周期分拆资源入口、合同摘要。
- 合同主档是业务真相源资源；合同台账视图与详情视图是读模型资源；
  二者不得混写为同一资源。
- 模板库与条款库都属于正式业务资源，不是后台配置附件区。
- 生命周期动作入口只定义“从合同主链发起什么业务动作”，不吸收签章、履约、
  变更、终止、归档等子模块的内部过程状态。
- 文档中心是文件真相源；合同管理本体不暴露文件版本链内部契约，
  只暴露对合同主链必要的文件引用与摘要。
- 流程引擎是审批运行时真相源；合同管理本体只暴露审批发起入口、
  合同侧审批摘要引用与业务回写边界。

### 2.2 合同主档接口

资源定位：`Contract` 是合同一级业务资源，承载统一 `contract_id`、
业务身份、分类、主体信息、生命周期主状态和稳定关联键。

#### `POST /api/contracts`

用途：创建合同主档，可由模板起草、空白起草、外围导入确认后入库等场景触发。

请求重点字段：

- `contract_name`
- `document_form`
- `business_domain`
- `contract_subcategory`
- `contract_detail_type`
- `owner_org_unit_id`
- `owner_user_id`
- `counterparty_list`
- `amount`
- `currency`
- `source_type`：`DRAFT` / `EXTERNAL_IMPORT` / `MANUAL_CREATE`
- `template_id`
- `main_document_asset_id`

响应重点字段：

- `contract_id`
- `contract_no`
- `contract_status`
- `main_document_asset_id`

#### `GET /api/contracts/{contract_id}/master`

用途：查询合同主档，不返回跨模块聚合视图，仅返回合同一级业务真相字段。

响应重点字段：

- `contract_id`
- `contract_no`
- `contract_name`
- `contract_status`
- `document_form`
- `business_domain`
- `contract_subcategory`
- `contract_detail_type`
- `owner_org_unit_id`
- `owner_user_id`
- `template_id`
- `main_document_asset_id`
- `summary_ref`

#### `PATCH /api/contracts/{contract_id}`

用途：更新合同主档中允许编辑的业务字段。

边界说明：

- 仅允许更新合同主档自身字段，不直接更新文档版本、审批实例、签章结果、
  履约过程、归档过程。
- 允许编辑范围由合同当前 `contract_status` 和权限共同约束。
- 对正文或附件的变更，应通过文档中心接口完成，再由合同主档更新引用或摘要。

### 2.3 合同台账与详情视图接口

#### `GET /api/contracts`

用途：查询合同台账视图，返回 `ContractLedgerView` 分页结果。

查询参数重点：

- `keyword`
- `document_form`
- `business_domain`
- `contract_subcategory`
- `contract_detail_type`
- `contract_status`
- `archive_status`
- `owner_org_unit_id`
- `owner_user_id`
- `counterparty_keyword`
- `signed_date_start`
- `signed_date_end`
- `language`
- `page`
- `page_size`

响应项重点字段：

- `contract_id`
- `contract_no`
- `contract_name`
- `contract_status`
- `approval_summary`
- `signature_summary`
- `performance_summary`
- `archive_summary`
- `pending_action_list`
- `display_text`

边界说明：

- 台账视图只服务查询、筛选、排序、统计与待办观察。
- 台账接口不承担正式编辑真相，不接受“直接修改台账字段”的写操作。

#### `GET /api/contracts/{contract_id}`

用途：查询合同详情视图，返回 `ContractDetailView`。

响应聚合范围：

- 合同主档基础信息
- 当前正文与附件引用摘要
- 审批摘要与审批入口信息
- 签章摘要、履约摘要、变更摘要、终止摘要、归档摘要
- 时间线摘要
- AI 辅助入口摘要

边界说明：

- 详情视图是聚合工作台，不是新的业务主档。
- 详情接口返回的是正式源头的受控聚合结果，不定义周边模块内部过程模型。

### 2.4 模板库 / 条款库接口

#### `GET /api/templates`

用途：查询模板列表。

查询参数重点：

- `template_type`
- `document_form`
- `business_domain`
- `contract_subcategory`
- `contract_detail_type`
- `template_status`
- `language`
- `page`
- `page_size`

#### `POST /api/templates`

用途：创建模板资源。

请求重点字段：

- `template_code`
- `template_name`
- `template_type`
- `document_form`
- `business_domain`
- `contract_subcategory`
- `contract_detail_type`
- `clause_binding_list`
- `default_locale`
- `locale_content_list`

#### `GET /api/templates/{template_id}`

用途：查询单个模板详情。

#### `POST /api/templates/{template_id}/activate`

用途：启用模板版本，使其进入可选起草范围。

#### `GET /api/clauses`

用途：查询条款库列表。

查询参数重点：

- `clause_type`
- `clause_category`
- `language`
- `enabled_only`
- `page`
- `page_size`

#### `POST /api/clauses`

用途：创建条款资源。

请求重点字段：

- `clause_code`
- `clause_name`
- `clause_type`
- `clause_category`
- `risk_level`
- `default_locale`
- `locale_content_list`
- `applicable_scope`

#### `GET /api/clauses/{clause_id}`

用途：查询单个条款详情。

#### `POST /api/clauses/{clause_id}/activate`

用途：启用条款版本，使其进入模板编排、起草引用和 AI grounding 范围。

边界说明：

- 模板资源表达“如何形成标准合同骨架”。
- 条款资源表达“哪些标准条款可被引用、比较、推荐和解释”。
- 模板与条款都不是合同实例，不承担 `contract_status`。

### 2.5 起草入口接口

资源定位：合同草稿通过 `ContractDraft` 语义承接模板选用、条款引用、正文生成、
补录信息和建稿确认动作；如需表达“起草会话”，仅作为建稿过程语义说明，不再作为
独立对外资源族。

#### `POST /api/contracts/drafts`

用途：基于模板、空白方式或导入场景创建合同草稿。

请求重点字段：

- `draft_mode`：`BY_TEMPLATE` / `BLANK` / `BY_IMPORT`
- `template_id`
- `clause_id_list`
- `document_form`
- `business_domain`
- `contract_subcategory`
- `contract_detail_type`
- `language`

响应重点字段：

- `contract_id`
- `drafting_session_id`
- `draft_status`
- `suggested_template_list`
- `suggested_clause_list`

边界说明：

- 合同草稿是 `Contract` 在 `DRAFT` 阶段的正式资源形态，不再单独暴露旧起草会话资源族。
- 如需查询或更新草稿，应围绕 `contract_id` 使用合同主档与详情接口组织，不额外
  发明与总平台冲突的新资源族。
- “起草会话”可作为实现与交互层语义存在，但不再作为 API 路径主语义。

### 2.6 生命周期动作入口接口

本组接口统一表达“从合同主链发起业务动作”，采用与总平台一致的分拆资源路径，
不吸收各下游模块的内部过程接口。

#### `POST /api/contracts/{contract_id}/signatures/apply`

用途：从合同主档发起签章入口，由签章模块承接签章过程。

#### `POST /api/contracts/{contract_id}/paper-records`

用途：登记纸质合同备案信息或纸质签约留痕。

#### `POST /api/contracts/{contract_id}/effectiveness/confirm`

用途：确认合同生效入口。

#### `POST /api/contracts/{contract_id}/performance-nodes`

用途：开启履约跟踪入口，由履约模块承接履约计划与节点过程。

#### `POST /api/contracts/{contract_id}/changes`

用途：发起合同变更入口。

#### `POST /api/contracts/{contract_id}/terminations`

用途：发起合同终止入口。

#### `POST /api/contracts/{contract_id}/breaches`

用途：登记合同违约事项与证据入口。

#### `POST /api/contracts/{contract_id}/archive`

用途：发起归档入口，由归档模块承接归档收口过程。

统一响应重点字段：

- `contract_id`
- `accepted`
- `target_module`
- `target_resource_id`
- `contract_status`
- `summary_ref`

边界说明：

- 生命周期动作入口负责前置校验、业务准入和合同侧状态推进。
- 下游模块的实例状态、任务状态、步骤状态不并入合同主档写接口。

### 2.7 合同摘要接口

资源定位：`ContractSummary` 是面向工作台、消息中心、关联模块复用的统一摘要资源。

#### `GET /api/contracts/{contract_id}/summary`

用途：查询单合同摘要。

响应重点字段：

- `contract_id`
- `contract_no`
- `contract_name`
- `contract_status`
- `current_stage`
- `approval_summary`
- `signature_summary`
- `performance_summary`
- `archive_summary`
- `pending_action_list`
- `main_document_summary`

#### `POST /api/contracts/summary-query`

用途：按 `contract_id_list`、业务归属或待办范围批量查询摘要。

边界说明：

- 合同摘要是统一消费视图，不替代台账视图或详情视图。
- 它服务消息中心、搜索结果卡片、待办聚合和跨模块嵌入场景。

## 3. 核心资源划分

### 3.1 `Contract`

用途：合同一级业务资源。

API 可见核心字段：

- `contract_id`
- `contract_no`
- `contract_name`
- `contract_status`
- `document_form`
- `business_domain`
- `contract_subcategory`
- `contract_detail_type`
- `owner_org_unit_id`
- `owner_user_id`
- `counterparty_list`
- `amount`
- `currency`
- `template_id`
- `main_document_asset_id`

说明：

- `Contract` 只表达合同业务真相，不承载文件版本链、审批实例明细、履约明细表象。

### 3.2 `ContractLedgerView`

用途：面向列表管理、筛选、排序、统计和待办观察的读模型视图。

API 可见核心字段：

- `contract_id`
- `contract_no`
- `contract_name`
- `contract_status`
- `owner_org_unit_name`
- `amount`
- `signed_date`
- `approval_summary`
- `pending_action_list`
- `display_text`

说明：

- `ContractLedgerView` 是查询资源，不接受直接编辑。

### 3.3 `ContractDetailView`

用途：围绕单个合同组织主档、文件、审批、时间线和周边摘要的聚合视图。

API 可见核心字段：

- `contract_master`
- `document_summary`
- `approval_summary`
- `signature_summary`
- `performance_summary`
- `change_summary`
- `termination_summary`
- `archive_summary`
- `timeline_summary`
- `action_entry_list`

说明：

- `ContractDetailView` 是工作台视图，不是新的业务主档。

### 3.4 `Template`

用途：标准合同骨架资源。

API 可见核心字段：

- `template_id`
- `template_code`
- `template_name`
- `template_type`
- `template_status`
- `document_form`
- `business_domain`
- `contract_subcategory`
- `contract_detail_type`
- `default_locale`
- `available_locale_list`
- `clause_binding_list`

说明：

- `Template` 服务起草，不直接承载合同实例生命周期。

### 3.5 `Clause`

用途：标准条款资源。

API 可见核心字段：

- `clause_id`
- `clause_code`
- `clause_name`
- `clause_type`
- `clause_category`
- `risk_level`
- `default_locale`
- `available_locale_list`
- `applicable_scope`
- `clause_status`

说明：

- `Clause` 是正式业务资源，既服务模板和起草，也服务审查、比对和 AI grounding。

### 3.6 `ContractDraft`

用途：合同草稿资源。

API 可见核心字段：

- `contract_id`
- `drafting_session_id`
- `draft_mode`
- `draft_status`
- `template_id`
- `clause_id_list`
- `language`
- `candidate_contract_fields`
- `candidate_document_asset_id`

说明：

- `ContractDraft` 是 `Contract` 在 `DRAFT` 阶段的资源语义；`drafting_session_id`
  如存在，仅作为建稿过程追踪标识，不再对应独立对外资源路径。

### 3.7 `ContractSummary`

用途：统一摘要资源。

API 可见核心字段：

- `contract_id`
- `contract_name`
- `contract_status`
- `current_stage`
- `main_document_summary`
- `approval_summary`
- `signature_summary`
- `performance_summary`
- `archive_summary`
- `pending_action_list`

说明：

- `ContractSummary` 面向跨模块消费，不承载全文详情和内部过程细节。

## 4. 统一约定

### 4.1 协议

- 协议采用 `HTTPS + JSON`。
- 编码采用 `UTF-8`。
- 时间字段采用 `ISO 8601`。
- 成功、失败、分页响应结构继承总平台
  [`API Design`](../../api-design.md) 的统一响应约定。
- 同步创建返回 `201`，同步查询和更新返回 `200`，异步受理型动作返回 `202`。

### 4.2 鉴权

- 合同台账、详情、摘要和主档查询要求平台登录态及合同数据访问权限。
- 模板、条款的创建、启停、维护要求模块管理权限。
- 合同草稿创建要求平台登录态及起草权限。
- 生命周期动作入口要求平台登录态、合同操作权限和当前阶段前置条件满足。
- 与文档中心、流程引擎、签章、履约、归档等模块的内部调用，应通过平台统一
  系统身份或受控服务身份传递调用上下文。

### 4.3 幂等

- 合同创建、起草提交、生命周期动作入口、模板启用、条款启用等写接口均支持
  `Idempotency-Key`。
- 对同一合同、同一动作语义的重复提交，应返回首次处理结果或稳定冲突错误。
- 生命周期动作入口的幂等键，应至少绑定 `contract_id + action_type` 或等价语义。

### 4.4 命名规范

- 路径、查询参数、请求字段、响应字段统一继承总平台
  [`API Design`](../../api-design.md) 的命名规范。
- 路径资源段使用 `kebab-case`，字段与参数使用 `snake_case`。
- 资源主键统一使用 `<resource>_id`。
- 状态枚举统一使用 `UPPER_SNAKE_CASE`。
- 多语言可翻译字段优先返回稳定语义编码、`label`、`i18n_key` 或等价显示对象，
  不以纯展示文案作为唯一契约。

### 4.5 错误码复用策略

本模块优先复用总平台 [`API Design`](../../api-design.md)
中的通用错误码，不新增平行体系。

优先复用的错误码包括：

- `40001 INVALID_PAYLOAD`
- `40002 INVALID_FIELD_VALUE`
- `40003 INVALID_QUERY_PARAMS`
- `40101 AUTH_REQUIRED`
- `40301 PERMISSION_DENIED`
- `40302 CONTRACT_ACCESS_DENIED`
- `40401 CONTRACT_NOT_FOUND`
- `40402 TEMPLATE_NOT_FOUND`
- `40901 CONTRACT_STATUS_CONFLICT`
- `40905 IDEMPOTENCY_CONFLICT`
- `50001 INTERNAL_SERVER_ERROR`
- `50201 EXTERNAL_SYSTEM_UNAVAILABLE`

模块级补充原则：

- 仅当总平台错误码无法准确表达模板、条款、起草会话等资源语义时，
  才在后续正式 `OpenAPI` 中补充合同域错误码。
- 新增错误码时仍沿用总平台返回结构，不单独定义新的错误响应格式。

## 5. 与文档中心的接口边界

### 5.1 边界说明

- 合同主档是业务真相源，文档中心是文件真相源。
- 合同管理本体只保存正文、附件、签章稿、归档稿等文件对象的稳定引用与摘要，
  不复制文件版本链。
- 文档内容新增、替换、切主版本、预览生成、批注修订、加密访问都由文档中心承接。
- 合同详情和合同摘要只消费文档中心返回的受控摘要，不回写文档内部结构。

### 5.2 接口边界

#### `POST /api/internal/contract-core/contracts/{contract_id}/document-bindings`

用途：为合同主档登记正文或附件引用关系，绑定到文档中心资源。

请求重点字段：

- `document_asset_id`
- `document_role`
- `binding_reason`

#### `GET /api/internal/contract-core/contracts/{contract_id}/document-summary`

用途：读取合同侧所需的文件摘要视图，供详情、摘要、动作校验复用。

响应重点字段：

- `main_document_asset_id`
- `attachment_count`
- `preview_status`
- `encryption_status`
- `document_role_list`

边界说明：

- 合同管理本体不定义 `DocumentAsset`、`DocumentVersion` 内部字段全集。
- 是否允许某类文档切换主版本、是否生成预览、是否触发加密，均回到文档中心。

## 6. 与流程引擎的接口边界

### 6.1 边界说明

- 默认审批主路径优先走 `OA`，但对合同主链暴露统一审批入口。
- 合同管理本体不拥有流程定义、流程实例、审批任务和节点绑定。
- 合同管理本体负责审批前置校验、审批发起入口、合同侧摘要引用和结果回写入口。
- 审批执行差异由流程引擎屏蔽，对合同详情与合同摘要统一输出审批摘要。

### 6.2 接口边界

#### `POST /api/integrations/oa/approval-requests`

用途：由合同主链统一发起 `OA` 审批；平台审批流承接口径回到总平台审批资源分组。

请求重点字段：

- `approval_mode`
- `definition_id` 或 `oa_flow_code`
- `submission_comment`

#### `GET /api/contracts/{contract_id}/approval-summary`

用途：读取合同侧审批摘要视图。

响应重点字段：

- `contract_id`
- `approval_mode`
- `summary_status`
- `current_node_name`
- `current_approver_list`
- `source_system_instance_id`

边界说明：

- 审批摘要是统一消费视图，不替代流程引擎原始记录。
- 流程节点结构、组织绑定结构、并行聚合细节下沉到流程引擎文档。

## 7. 与其他模块的接口边界

### 7.1 与签章的接口边界

- 合同管理本体暴露签章发起入口，不暴露签章模块内部签署步骤。
- 签章模块需要的合同上下文由合同详情摘要和文档引用提供。
- 合同侧只接收 `signature_summary`、结果状态和关键时间点回写。

代表接口：

- `POST /api/contracts/{contract_id}/signatures/apply`
- `GET /api/contracts/{contract_id}/summary`

### 7.2 与履约的接口边界

- 合同管理本体暴露履约开启入口和履约摘要读取面。
- 履约计划、节点执行、证据、异常过程由履约模块治理。
- 合同侧只接收履约阶段摘要、风险摘要和关键里程碑回写。

代表接口：

- `POST /api/contracts/{contract_id}/performance-nodes`
- `GET /api/contracts/{contract_id}`

### 7.3 与变更的接口边界

- 合同管理本体暴露变更发起入口。
- 变更模块负责变更申请、审批、结果和新版本形成过程。
- 合同侧只更新当前有效业务状态、变更摘要和时间线引用。

代表接口：

- `POST /api/contracts/{contract_id}/changes`

### 7.4 与终止的接口边界

- 合同管理本体暴露终止发起入口。
- 终止原因、材料、审批和善后过程由终止模块治理。
- 合同侧回收的是终止摘要和主状态变更，不接收终止模块内部步骤状态。

代表接口：

- `POST /api/contracts/{contract_id}/terminations`

### 7.5 与归档的接口边界

- 合同管理本体暴露归档请求入口和归档摘要读取面。
- 归档模块负责归档收集、归档记录、借阅归还等档案过程资源。
- 合同侧只保留 `archive_summary`、归档状态和归档引用键。

代表接口：

- `POST /api/contracts/{contract_id}/archive`
- `GET /api/contracts/{contract_id}/summary`

### 7.6 与搜索的接口边界

- 搜索模块消费合同主档、合同摘要和文档中心受控文本，不反向定义合同真相。
- 合同管理本体对搜索提供的是统一摘要、受控标签、分类字段和结果跳转目标。
- 搜索命中结果应回到合同台账视图、详情视图或摘要视图。

代表接口：

- `GET /api/contracts`
- `GET /api/contracts/{contract_id}/summary`

### 7.7 与 AI 的接口边界

- 合同管理本体向 AI 暴露的是合同主档、模板、条款、文档摘要、详情聚合上下文。
- AI 输出只能作为辅助结果挂接回合同详情或合同草稿，不能直接覆盖合同主档。
- 合同管理本体不把模型参数、Prompt 或推理链条写入本模块 API。

代表接口：

- `GET /api/contracts/{contract_id}`
- `GET /api/clauses/{clause_id}`
- `GET /api/contracts/{contract_id}/summary`

## 8. 模板库 / 条款库在 API 层的能力边界

- 模板库与条款库都以正式资源暴露，具备列表、详情、创建、启停等标准接口面。
- 模板库负责模板分类、模板内容、条款绑定、语言版本与启停状态。
- 条款库负责条款内容、条款分类、适用范围、风险级别、语言版本与启停状态。
- 模板可引用条款，但模板与条款都不直接生成合同状态。
- 合同草稿可以消费模板与条款，但模板库 / 条款库不直接持有起草过程状态。
- 条款库在 API 层必须可被模板编排、起草引用、合同审查、差异比对和 AI grounding
  复用，不能退化成仅后台维护的附件仓。

## 9. 多语言支持在 API 层的体现

- 读取接口支持 `Accept-Language` 请求头，并允许使用 `language` 查询参数显式指定
  期望语言。
- 对 `Contract`、`ContractLedgerView`、`ContractDetailView` 这类业务资源，
  分类、状态、动作名称等可翻译字段返回稳定语义编码和显示对象，避免把中文文案
  写死为唯一契约。
- 对 `Template`、`Clause` 这类内容资源，写接口支持 `default_locale` 与
  `locale_content_list`，读接口返回 `available_locale_list` 与当前语言内容。
- 合同草稿创建需要显式带入 `language`，用于模板选择、条款回填和候选文本生成。
- 多语言支持只改变展示文本与内容版本选择，不改变 `contract_id`、状态编码、
  分类编码和跨模块引用键。

## 10. 需要下沉到该模块 Detailed Design 的内容边界

- 合同主档模型、台账读模型、详情聚合模型的内部组装方式
- 模板与条款的版本模型、适用范围判定、冲突解决和启停内部规则
- 合同草稿如何与文档中心、AI、模板匹配、条款推荐协作的时序细节
- 生命周期分拆资源入口与签章、履约、变更、终止、归档等模块之间的补偿、幂等、
  一致性和回写策略
- 合同摘要、详情聚合、台账查询的缓存、索引、投影和刷新机制
- 多语言内容存储、回退策略、术语一致性和检索适配策略

## 11. 本文结论

合同管理本体子模块的 API 设计应围绕“合同主档是业务真相源，文档中心是文件
真相源，流程引擎是审批运行时真相源”这一边界展开。

在这一前提下：

- `Contract`、`ContractLedgerView`、`ContractDetailView` 必须分离
- `Template`、`Clause`、`ContractDraft` 必须作为正式资源存在
- 生命周期动作只暴露入口，不吸收周边模块内部过程状态
- 合同摘要为跨模块消费提供统一视图，但不替代主档、台账或详情
- 具体状态机、内部聚合、存储与补偿细节继续下沉到
  [`Detailed Design`](./detailed-design.md)

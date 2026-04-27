# 湖南星邦智能装备股份有限公司合同管理平台 API Design

## 1. 文档定位

本文档定义 `湖南星邦智能装备股份有限公司合同管理平台`（`CMP`）的 API 资源边界、请求/响应契约、错误码、异步任务契约，以及与 `OA`、企业微信、`CRM`、`SF`、`SRM`、`SAP` 和文档中心内部加密服务的接口边界。

本文档只描述 API 可见契约，不展开需求范围复述、技术选型论证、存储结构、索引组织和内部状态编排。此类内容留待 `Detailed Design` 或正式 `OpenAPI` 定稿。

## 2. API 边界

### 2.1 资源与接口边界

- 认证与会话：由 `identity-access` 主线统一承接的登录、会话、认证回调与当前用户信息查询。
- 合同资源：草稿、合同台账、合同详情、附件、分类、状态、归档相关视图。
- 模板资源：模板、条款、模板启用。
- 审批资源：`OA` 审批桥接接口、平台审批流定义与实例接口、审批摘要查询。
- 文档资源：`identity-access` 主线负责解密下载授权配置、授权查询与命中判定；`encrypted-document` 主线负责入库加密、受控解密访问、解密下载任务执行、加密作业查询与审计视图。
- 生命周期动作：签章、生效、履约、变更、终止、违约、归档。
- 集成接口：外围系统合同入站、主数据同步、企业微信用户同步与消息通知。
- 智能任务：`OCR`、AI 提取、草稿生成、审核、风险预警及人工确认。

- `CMP` 只定义平台侧 API 边界，不定义外围系统本体改造接口。
- 电视端复用通用业务 API，不单独定义电视端专属接口。
- 微信支付不属于本文 API 范围。
- 正式 `OpenAPI schema`、签名算法细节和部署拓扑不在本文展开。

### 2.2 审批边界

- 一期默认审批主路径为 `OA`，合同审批主链路优先通过 `OA` 发起、流转与回写。
- 同时，`CMP` 必须建设可独立运行的审批流引擎，而不是停留在轻量接口预留层面；该引擎属于一期正式建设项。
- 平台审批流引擎最少支持串行、并行、会签、转办四类基础审批方式，并支持流程节点、参与人、条件路由、时限规则的可视化配置。
- 流程可视化配置中的每一个审批节点都必须与组织架构绑定，不允许出现脱离部门、人员或组织规则的裸节点；节点参与人必须能直接绑定部门、人员，或基于组织架构规则选人。
- `CMP` 需要同时提供两类审批接口：`OA` 审批桥接接口，以及平台审批流引擎接口。
- 当 `OA` 无法承接某类审批规则、节点组合、时限控制、转办要求或特殊业务场景时，平台审批流引擎具备正式承接条件并作为替代主执行路径启用。

## 3. 统一约定

### 3.1 协议与基础约定

- 协议：`HTTPS + JSON`。
- 编码：`UTF-8`。
- 时间格式：`ISO 8601`。
- 列表分页：`page`、`page_size`。
- 幂等写接口：支持 `Idempotency-Key`。
- 外围回调接口：必须支持请求签名、时间戳和重放校验。

### 3.1A 接口抽象边界

- API 契约保持前端无关，不暴露页面组件结构或前端状态管理实现。
- API 契约保持存储无关，不承诺具体表结构、索引、方言函数或内部持久化模型。
- AI / OCR 接口按能力类型、任务类型和标准结果结构对外收口，不把具体模型名称、厂商参数或单一 SDK 语义写成业务接口必填项。
- 回调、文件传输和异步任务通知按企业内网部署场景约束设计，但不把部署编排方式写成 API 字段契约。

### 3.2 国际化与终端兼容约定

- 一期支持中文、英文、西文；接口需支持 `Accept-Language` 请求头，响应中的可翻译枚举字段可返回 `label` 或 `i18n_key`。
- 电视端复用通用业务 API，不新增电视端专属数据模型；列表、详情、统计 API 需保证大屏场景可复用，避免强绑定移动端交互分页方式。

### 3.3 字段命名规范

- 路径资源段统一使用 `kebab-case`；路径参数、请求字段、响应字段、查询参数统一使用 `snake_case`；不再混用 `camelCase`、其他路径风格或 `field[]` 记法。
- `ID` 字段统一使用 `<resource>_id`；外部系统标识统一使用 `<system>_<resource>_id`，例如 `contract_id`、`template_id`、`oa_instance_id`、`wecom_user_id`。
- 时间字段中，仅日期语义使用 `*_date`，完整时间戳使用 `*_at`；查询区间统一使用 `*_start`、`*_end`。
- 布尔字段新增命名统一使用 `is_*`、`has_*`、`*_enabled`、`*_required`；一期已有高成本字段可暂保留，但不再新增无前缀布尔字段。
- 状态字段统一使用 `<domain>_status`，例如 `contract_status`、`archive_status`、`signature_status`、`encryption_status`；禁止继续使用泛化 `status` 表示合同状态。
- 数组字段名不写 `[]`；结构化集合优先使用业务名词复数或稳定集合名，如 `contract_tags`、`payment_nodes`；明确清单型对象数组统一使用 `*_list`，如 `attachment_list`、`approver_list`、`comment_list`。
- 对象字段采用具象业务名词；通用信息对象使用 `*_info`，字段分组对象使用 `*_fields`，避免使用语义过宽的 `data`、`ext`、`extra` 作为业务字段名。
- 枚举值统一使用全大写下划线风格，即 `UPPER_SNAKE_CASE`，包括分类枚举、状态枚举、来源系统、审批模式和任务结果状态。
- 中文概念映射到英文字段名时，遵循“一种中文语义只保留一个英文名”的原则；同一语义在列表、详情、入站、回调中保持同名，不因接口位置变化改名。

### 3.4 统一响应结构

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

失败响应：

```json
{
  "code": 40001,
  "message": "invalid payload",
  "details": {
    "field_name": "contract_name"
  },
  "trace_id": "req_20260402_xxx"
}
```

分页响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "item_list": [],
    "page": 1,
    "page_size": 20,
    "total": 0
  }
}
```

### 3.5 状态码 / 错误码设计原则

- `HTTP` 状态码只表达请求处理结果，不承载合同生命周期、审批节点或分类字段语义；合同、归档、签章、加密、分类确认等业务进度仍分别使用 `contract_status`、`archive_status`、`signature_status`、`encryption_status`、`classification_confirmation_status` 等业务字段表达。
- 成功类接口按动作区分：同步读取或更新成功返回 `200`，创建成功返回 `201`，异步任务受理成功返回 `202`，无响应体的删除或撤销类动作可返回 `204`。
- 失败类接口同时返回 `HTTP` 状态码和业务错误码；`HTTP` 状态码用于网关、监控和联调快速分流，业务错误码用于前后端、外围系统和日志定位具体失败原因。
- 业务错误码统一为 5 位数字，按“错误域 + 细分类”分段；同一错误码在同步接口、异步任务结果、回调回执中保持语义一致，不因接口分组变化改名。
- `4xx` 错误优先表示调用方可修正的问题，例如参数不合法、签名失败、权限不足、资源不存在、状态冲突、幂等冲突、分类字段待确认；`5xx` 错误用于平台内部异常或外围系统不可用。
- 批量导入、`OCR`、AI 审核、报表导出、批量归档等异步任务，任务创建接口优先返回 `202` 和任务主键；最终失败原因通过任务详情中的 `job_status`、`result_code`、`result_message` 返回，并复用同一套业务错误码。
- 外围系统回调接口即使返回非 `2xx`，也应保证响应体中的 `code`、`message`、`trace_id` 完整可读，便于 `OA`、企业微信、AI / OCR 服务侧直接记录失败原因。

### 3.6 错误码体系

建议按“通用层、鉴权层、权限层、资源层、状态层、集成层、智能能力层”收口，先固定错误域，再在正式 `OpenAPI` 中补齐更细的字段级错误字典。

| HTTP 状态码 | 业务错误码 | 错误名称 | 使用场景 |
| --- | --- | --- | --- |
| `400` | `40001` | `INVALID_PAYLOAD` | 请求体不是合法 `JSON`，或基础结构不符合接口定义 |
| `400` | `40002` | `INVALID_FIELD_VALUE` | 通用字段校验失败，如 `contract_name` 为空、日期格式错误、枚举值非法 |
| `400` | `40003` | `INVALID_QUERY_PARAMS` | 查询参数、分页参数、筛选条件不合法 |
| `400` | `40004` | `INVALID_CLASSIFICATION_COMBINATION` | `document_form`、`business_domain`、`contract_subcategory`、`contract_detail_type` 上下级组合不合法 |
| `400` | `40005` | `CLASSIFICATION_CONFIRMATION_REQUIRED` | 分类字段未确认，当前请求不能直接进入正式合同主数据或发起后续动作 |
| `400` | `40006` | `CLASSIFICATION_MAPPING_MISSING` | 外围系统仅提供粗粒度分类，且当前映射规则无法唯一确定正式分类 |
| `401` | `40101` | `AUTH_REQUIRED` | 未登录、缺少访问令牌或会话已失效 |
| `401` | `40102` | `AUTH_TOKEN_INVALID` | 访问令牌、票据或单点登录凭证无效 |
| `401` | `40103` | `CALLBACK_SIGNATURE_INVALID` | 外围回调签名错误、时间戳非法或重放校验失败 |
| `403` | `40301` | `PERMISSION_DENIED` | 当前用户或系统账号无权访问该接口 |
| `403` | `40302` | `CONTRACT_ACCESS_DENIED` | 当前用户无权查看、编辑、审批或归档指定合同 |
| `403` | `40303` | `APPROVAL_ACTION_FORBIDDEN` | 当前节点无权执行审批动作、撤回、转审或终止 |
| `404` | `40401` | `CONTRACT_NOT_FOUND` | `contract_id` 对应合同不存在 |
| `404` | `40402` | `TEMPLATE_NOT_FOUND` | `template_id`、模板条款、模板附件等模板相关静态资源不存在 |
| `404` | `40403` | `EXTERNAL_INSTANCE_NOT_FOUND` | `oa_instance_id`、外围同步任务、AI / OCR 外围结果等外围系统实例或业务对象不存在 |
| `409` | `40901` | `CONTRACT_STATUS_CONFLICT` | 合同当前 `contract_status` 不允许执行该动作，如未审批通过即发起签章 |
| `409` | `40902` | `ARCHIVE_STATUS_CONFLICT` | 当前 `archive_status` 不允许重复归档、借阅或归还 |
| `409` | `40903` | `SIGNATURE_STATUS_CONFLICT` | 当前 `signature_status` 与操作前置条件冲突 |
| `409` | `40904` | `CLASSIFICATION_STATUS_CONFLICT` | `classification_confirmation_status` 仍为待人工确认，禁止发起审批、签章、归档等正式流程 |
| `409` | `40905` | `IDEMPOTENCY_CONFLICT` | 相同 `Idempotency-Key` 对应不同请求体，或重复请求与首个结果不兼容 |
| `409` | `40906` | `DUPLICATE_EXTERNAL_REQUEST` | 外围系统重复推送同一业务主键，且当前入站策略不允许覆盖 |
| `422` | `42201` | `FILE_VALIDATION_FAILED` | 文件格式、大小、页数、病毒校验或附件元数据校验失败 |
| `422` | `42202` | `ENCRYPTION_CHECK_FAILED` | 文档中心内部加密服务校验不通过，文件未满足白名单、密级或受控解密前置条件 |
| `422` | `42203` | `OA_SYNC_FAILED` | `OA` 审批发起、状态同步、结果回写失败，但请求本身已被平台接收 |
| `422` | `42204` | `WECOM_SYNC_FAILED` | 企业微信用户、部门、消息发送或登录态换取失败 |
| `422` | `42205` | `AI_TASK_FAILED` | AI 草稿生成、智能审核、风险预警处理失败 |
| `422` | `42206` | `OCR_TASK_FAILED` | `OCR` 识别、版面解析、字段提取失败 |
| `422` | `42207` | `EXTERNAL_CALLBACK_PROCESSING_FAILED` | 外围系统回调到达成功，但平台侧验签后处理、落库或状态推进失败 |
| `500` | `50001` | `INTERNAL_SERVER_ERROR` | 平台内部未分类异常 |
| `502` | `50201` | `EXTERNAL_SYSTEM_UNAVAILABLE` | 外围系统不可用、超时或返回非预期响应 |
| `503` | `50301` | `ASYNC_JOB_BACKLOG` | 异步任务队列拥塞，暂时无法受理新的批量或智能任务 |

错误返回建议继续使用统一响应结构，并在 `details` 中补充字段级上下文，例如：

```json
{
  "code": 40005,
  "message": "classification confirmation required",
  "details": {
    "contract_id": "ctr_20260402_001",
    "classification_confirmation_status": "PENDING_MANUAL_CONFIRM",
    "field_list": [
      "contract_subcategory",
      "contract_detail_type"
    ]
  },
  "trace_id": "req_20260402_xxx"
}
```

### 3.7 异步任务约束

- 以下场景统一设计为异步任务接口：文件导入、`OCR`、AI 审核、批量归档、批量加密校验、报表生成。
- 异步任务创建成功返回 `202` 与 `job_id`；最终结果通过任务查询接口返回 `job_status`、`result_code`、`result_message`。
- 列表接口默认 `page_size <= 100`；超大结果集通过导出任务处理。
- 外围系统入站接口必须支持幂等、防重复提交、失败重试和结果回执。

## 4. 主数据与字段契约

### 4.1 合同主数据核心字段

本节只保留合同资源对外可见的核心字段。更细的对象层级、内部全集字段、临时接收结构、索引字段和物理存储拆分不在本文展开，留待 `Detailed Design` 与正式 `OpenAPI` 定稿。

| 字段 | 类型 | 是否 API 可见 | 说明 |
| --- | --- | --- | --- |
| `contract_id` | string | 是 | 合同资源主键 |
| `source_system` | string | 是 | 来源系统标识，如 `MANUAL`、`OA`、`CRM`、`SF`、`SRM`、`SAP` |
| `source_business_id` | string | 是 | 外围系统业务主键 |
| `contract_name` | string | 是 | 合同名称 |
| `contract_no` | string | 是 | 合同编号 |
| `document_form` | string | 是 | 文书形态字典值 |
| `business_domain` | string | 是 | 业务域字典值 |
| `contract_subcategory` | string | 是 | 合同子类字典值 |
| `contract_detail_type` | string | 是 | 合同细分类型字典值；HR 子类可为空 |
| `contract_tags` | string[] | 是 | 受控标签集合，仅用于补充检索与统计 |
| `department_id` | string | 是 | 合同归属部门 |
| `template_id` | string | 是 | 关联模板标识 |
| `approval_mode` | string | 是 | 审批承载方式，取值为 `OA` / `CMP` |
| `party_a_name` | string | 是 | 甲方名称 |
| `party_b_name` | string | 是 | 乙方名称 |
| `sign_date` | string | 是 | 签署日期 |
| `effective_date` | string | 是 | 生效日期 |
| `expire_date` | string | 是 | 到期日期 |
| `amount` | number | 是 | 合同金额；无金额场景可为空 |
| `currency` | string | 是 | 币种 |
| `attachment_list` | array | 是 | 附件清单 |
| `common_fields` | object | 是 | 通用合同扩展字段对象 |
| `commercial_fields` | object | 是 | 商业合同扩展字段对象 |
| `hr_fields` | object | 是 | HR 合同扩展字段对象 |
| `special_fields` | object | 是 | 子类特殊字段对象 |
| `contract_status` | string | 是 | 合同状态 |
| `archive_status` | string | 是 | 归档状态 |
| `signature_status` | string | 是 | 签章状态 |
| `encryption_status` | string | 是 | 文档加密状态 |

字段口径说明：

- 分类主链使用 `document_form`、`business_domain`、`contract_subcategory`、`contract_detail_type`；不再使用单一 `contract_type` 承载全部分类语义。
- `contract_tags` 仅用于补充检索、统计和运营标记，不参与模板主路由。
- `common_fields`、`commercial_fields`、`hr_fields`、`special_fields` 作为 API 可见对象壳层保留；对象内部字段明细和必填规则留待 `Detailed Design` 与正式 `OpenAPI` 定稿。

### 4.1A 分类字段字典

本节将分类字段从说明性字段提升为可用于接口约定、模板匹配、外围映射和检索统计的正式字段字典。字段使用规则如下：

- `document_form`、`business_domain`、`contract_subcategory`、`contract_detail_type` 为单选字段。
- `contract_tags` 为多选字段，仅接受受控字典中的标签值。
- 模板选择主键建议使用 `document_form + business_domain + contract_subcategory + contract_detail_type`；其中仅 HR 子类允许 `contract_detail_type` 为空。
- 检索和统计建议使用全部分类字段；但 `contract_tags` 仅参与检索、统计和运营标注，不参与模板路由。
- 返回对象建议统一包含 `value`、`label`、`parent_value`、`enabled` 等基础属性；本文先锁定 `value` 与 `label`，其余为后续 `OpenAPI` 定稿项。

#### 4.1A.1 字段级约束总表

| 字段名 | 枚举值 | 中文名 | 上级约束 | 是否必填 | 是否多选 | 用途说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `document_form` | 见 4.1A.2 | 文书形态 | 无 | 是 | 否 | 主分类顶层字段，用于模板选择、字段显隐、检索、统计 |
| `business_domain` | 见 4.1A.3 | 业务域 | 无 | 是 | 否 | 主分类顶层字段，与 `document_form` 一起约束下级分类 |
| `contract_subcategory` | 见 4.1A.4 | 合同子类 | 必须满足 `document_form` + `business_domain` 组合 | 是 | 否 | 主分类核心字段，用于模板路由、字段校验、外围映射 |
| `contract_detail_type` | 见 4.1A.5 | 合同细分类型 | 必须属于已选 `contract_subcategory` | 条件必填 | 否 | 销售、采购、框架子类用于进一步选择模板和特殊字段；HR 子类可为空 |
| `contract_tags` | 见 4.1A.6 | 合同标签 | 不得反向改写主分类字段 | 否 | 是 | 仅用于补充检索、统计、运营标记，不参与模板选择 |

#### 4.1A.2 `document_form` 字典

| 字段名 | 枚举值 | 中文名 | 上级约束 | 是否必填 | 是否多选 | 用途说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `document_form` | `CONTRACT` | 合同 | 无 | 是 | 否 | 用于普通合同类模板、检索和统计；可与 `SALES`、`PROCUREMENT`、`HR` 组合 |
| `document_form` | `AGREEMENT` | 协议 | 无 | 是 | 否 | 当前主要承接 HR 协议类模板、检索和统计；不与销售、采购合同子类混用 |
| `document_form` | `FRAMEWORK` | 框架 | 无 | 是 | 否 | 当前用于框架协议类模板、检索和统计；应与销售、采购框架子类对应 |

#### 4.1A.3 `business_domain` 字典

| 字段名 | 枚举值 | 中文名 | 上级约束 | 是否必填 | 是否多选 | 用途说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `business_domain` | `SALES` | 销售 | 无 | 是 | 否 | 对应销售合同和销售框架协议的业务域 |
| `business_domain` | `PROCUREMENT` | 采购 | 无 | 是 | 否 | 对应采购合同和采购框架协议的业务域 |
| `business_domain` | `HR` | 人力资源 | 无 | 是 | 否 | 对应劳动合同及各类 HR 协议；当前不再继续细分业务域 |

#### 4.1A.4 `contract_subcategory` 字典

| 字段名 | 枚举值 | 中文名 | 上级约束 | 是否必填 | 是否多选 | 用途说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `contract_subcategory` | `MAIN_BUSINESS_SALES_CONTRACT` | 主营业务销售合同 | `document_form=CONTRACT` 且 `business_domain=SALES` | 是 | 否 | 用于主营业务销售合同模板选择、检索、统计；`contract_detail_type` 必填 |
| `contract_subcategory` | `OTHER_BUSINESS_SALES_CONTRACT` | 其他业务销售合同 | `document_form=CONTRACT` 且 `business_domain=SALES` | 是 | 否 | 用于其他业务销售合同模板选择、检索、统计；`contract_detail_type` 必填 |
| `contract_subcategory` | `PRODUCTION_PROCUREMENT_CONTRACT` | 生产性采购合同 | `document_form=CONTRACT` 且 `business_domain=PROCUREMENT` | 是 | 否 | 用于生产性采购合同模板选择、检索、统计；`contract_detail_type` 必填 |
| `contract_subcategory` | `ADHOC_PROCUREMENT_CONTRACT` | 零星采购合同 | `document_form=CONTRACT` 且 `business_domain=PROCUREMENT` | 是 | 否 | 用于零星采购合同模板选择、检索、统计；`contract_detail_type` 必填 |
| `contract_subcategory` | `SALES_FRAMEWORK_AGREEMENT` | 销售框架协议 | `document_form=FRAMEWORK` 且 `business_domain=SALES` | 是 | 否 | 用于销售框架协议模板选择、检索、统计；`contract_detail_type` 必填 |
| `contract_subcategory` | `PROCUREMENT_FRAMEWORK_AGREEMENT` | 采购框架协议 | `document_form=FRAMEWORK` 且 `business_domain=PROCUREMENT` | 是 | 否 | 用于采购框架协议模板选择、检索、统计；`contract_detail_type` 必填 |
| `contract_subcategory` | `LABOR_CONTRACT` | 劳动合同 | `document_form=CONTRACT` 且 `business_domain=HR` | 是 | 否 | 用于劳动合同模板选择、检索、统计；`contract_detail_type` 可为空 |
| `contract_subcategory` | `CONFIDENTIALITY_AGREEMENT` | 保密协议 | `document_form=AGREEMENT` 且 `business_domain=HR` | 是 | 否 | 用于 HR 保密协议模板选择、检索、统计；`contract_detail_type` 可为空 |
| `contract_subcategory` | `NON_COMPETE_AGREEMENT` | 竞业限制协议 | `document_form=AGREEMENT` 且 `business_domain=HR` | 是 | 否 | 用于 HR 竞业限制协议模板选择、检索、统计；`contract_detail_type` 可为空 |
| `contract_subcategory` | `TRAINING_SERVICE_PERIOD_AGREEMENT` | 培训服务期协议 | `document_form=AGREEMENT` 且 `business_domain=HR` | 是 | 否 | 用于 HR 培训服务期协议模板选择、检索、统计；`contract_detail_type` 可为空 |
| `contract_subcategory` | `TERMINATION_SEPARATION_AGREEMENT` | 离职/解除协议 | `document_form=AGREEMENT` 且 `business_domain=HR` | 是 | 否 | 用于 HR 离职/解除协议模板选择、检索、统计；`contract_detail_type` 可为空 |

#### 4.1A.5 `contract_detail_type` 字典

| 字段名 | 枚举值 | 中文名 | 上级约束 | 是否必填 | 是否多选 | 用途说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `contract_detail_type` | `MACHINE_SALES` | 主机销售 | `contract_subcategory=MAIN_BUSINESS_SALES_CONTRACT` | 是 | 否 | 用于主营业务主机销售模板选择、检索、统计，并约束主机销售特殊字段 |
| `contract_detail_type` | `PARTS_SALES` | 配件销售 | `contract_subcategory=MAIN_BUSINESS_SALES_CONTRACT` | 是 | 否 | 用于主营业务配件销售模板选择、检索、统计，并约束配件销售特殊字段 |
| `contract_detail_type` | `SCRAP_MATERIAL_SALES` | 废旧物资销售 | `contract_subcategory=OTHER_BUSINESS_SALES_CONTRACT` | 是 | 否 | 用于其他业务废旧物资销售模板选择、检索、统计 |
| `contract_detail_type` | `RESIDUAL_VALUE_SALES` | 残值物销售 | `contract_subcategory=OTHER_BUSINESS_SALES_CONTRACT` | 是 | 否 | 用于其他业务残值物销售模板选择、检索、统计 |
| `contract_detail_type` | `OFFCUT_SCRAP_SALES` | 边角料/报废物资销售 | `contract_subcategory=OTHER_BUSINESS_SALES_CONTRACT` | 是 | 否 | 用于其他业务边角料或报废物资销售模板选择、检索、统计 |
| `contract_detail_type` | `HAZARDOUS_SPECIAL_DISPOSAL_SALES` | 危废/特殊处置类销售 | `contract_subcategory=OTHER_BUSINESS_SALES_CONTRACT` | 是 | 否 | 用于危废或特殊处置类销售模板选择、检索、统计 |
| `contract_detail_type` | `RAW_MATERIAL` | 原材料 | `contract_subcategory=PRODUCTION_PROCUREMENT_CONTRACT` | 是 | 否 | 用于原材料采购模板选择、检索、统计 |
| `contract_detail_type` | `CORE_COMPONENT` | 核心零部件 | `contract_subcategory=PRODUCTION_PROCUREMENT_CONTRACT` | 是 | 否 | 用于核心零部件采购模板选择、检索、统计 |
| `contract_detail_type` | `OUTSOURCED_PROCESSING` | 外协加工 | `contract_subcategory=PRODUCTION_PROCUREMENT_CONTRACT` | 是 | 否 | 用于外协加工采购模板选择、检索、统计 |
| `contract_detail_type` | `TOOLING_MOLD` | 工装模具 | `contract_subcategory=PRODUCTION_PROCUREMENT_CONTRACT` | 是 | 否 | 用于工装模具采购模板选择、检索、统计 |
| `contract_detail_type` | `R_AND_D_TRIAL` | 研发试制 | `contract_subcategory=PRODUCTION_PROCUREMENT_CONTRACT` | 是 | 否 | 用于研发试制采购模板选择、检索、统计 |
| `contract_detail_type` | `PACKAGING_AUXILIARY` | 包装辅材 | `contract_subcategory=PRODUCTION_PROCUREMENT_CONTRACT` | 是 | 否 | 用于包装辅材采购模板选择、检索、统计 |
| `contract_detail_type` | `OFFICE_PROCUREMENT` | 办公 | `contract_subcategory=ADHOC_PROCUREMENT_CONTRACT` | 是 | 否 | 用于办公类零星采购模板选择、检索、统计 |
| `contract_detail_type` | `LOGISTICS_PROCUREMENT` | 后勤 | `contract_subcategory=ADHOC_PROCUREMENT_CONTRACT` | 是 | 否 | 用于后勤类零星采购模板选择、检索、统计 |
| `contract_detail_type` | `EMPLOYEE_BENEFIT_PROCUREMENT` | 员工福利 | `contract_subcategory=ADHOC_PROCUREMENT_CONTRACT` | 是 | 否 | 用于员工福利类零星采购模板选择、检索、统计 |
| `contract_detail_type` | `IT_SERVICE_PROCUREMENT` | IT 服务 | `contract_subcategory=ADHOC_PROCUREMENT_CONTRACT` | 是 | 否 | 用于 IT 服务类零星采购模板选择、检索、统计 |
| `contract_detail_type` | `CONSULTING_SERVICE_PROCUREMENT` | 咨询服务 | `contract_subcategory=ADHOC_PROCUREMENT_CONTRACT` | 是 | 否 | 用于咨询服务类零星采购模板选择、检索、统计 |
| `contract_detail_type` | `MAIN_BUSINESS_SALES_FRAMEWORK` | 主营业务销售框架 | `contract_subcategory=SALES_FRAMEWORK_AGREEMENT` | 是 | 否 | 用于主营业务销售框架模板选择、检索、统计，并联动框架订单释放规则字段 |
| `contract_detail_type` | `PARTS_SALES_FRAMEWORK` | 配件销售框架 | `contract_subcategory=SALES_FRAMEWORK_AGREEMENT` | 是 | 否 | 用于配件销售框架模板选择、检索、统计 |
| `contract_detail_type` | `OTHER_BUSINESS_SALES_FRAMEWORK` | 其他业务销售框架 | `contract_subcategory=SALES_FRAMEWORK_AGREEMENT` | 是 | 否 | 用于其他业务销售框架模板选择、检索、统计 |
| `contract_detail_type` | `PRODUCTION_PROCUREMENT_FRAMEWORK` | 生产性采购框架 | `contract_subcategory=PROCUREMENT_FRAMEWORK_AGREEMENT` | 是 | 否 | 用于生产性采购框架模板选择、检索、统计 |
| `contract_detail_type` | `ADHOC_PROCUREMENT_FRAMEWORK` | 零星采购框架 | `contract_subcategory=PROCUREMENT_FRAMEWORK_AGREEMENT` | 是 | 否 | 用于零星采购框架模板选择、检索、统计 |

补充约束：

- `LABOR_CONTRACT`、`CONFIDENTIALITY_AGREEMENT`、`NON_COMPETE_AGREEMENT`、`TRAINING_SERVICE_PERIOD_AGREEMENT`、`TERMINATION_SEPARATION_AGREEMENT` 当前不配置 `contract_detail_type`，接口应允许为空。
- 对销售、采购、框架相关子类，`contract_detail_type` 为必填且必须进入模板选择键；对 HR 子类，不进入模板选择键。
- 列表筛选中，若只传 `business_domain` 不传下级字段，应返回该业务域下全部子类数据；若传入 `contract_subcategory`，则必须满足其上级组合关系；若继续传 `contract_detail_type`，则必须属于该子类。

#### 4.1A.6 `contract_tags` 字典

当前规则：一期只保留 `contract_tags` 单一字段，且只接受受控字典标签值，不正式引入 `contract_tag_extensions`。

- 受控字典用于统一高频检索、统计和运营口径，由平台维护标签码表。
- 未纳入字典的新标签不直接入参；如业务确有新增标签需求，应先补字典，再通过 `contract_tags` 传值。
- 模板选择、特殊字段显隐、字段必填校验一律不得依赖 `contract_tags`。
- `contract_tags` 不得替代 `document_form`、`business_domain`、`contract_subcategory`、`contract_detail_type` 的主分类职责。

当前建议的受控标签首批范围如下：

| 字段名 | 枚举值 | 中文名 | 上级约束 | 是否必填 | 是否多选 | 用途说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `contract_tags` | `OVERSEAS` | 海外业务 | 不得替代主分类字段 | 否 | 是 | 用于补充检索、统计和运营标记，不驱动模板 |
| `contract_tags` | `STRATEGIC_CUSTOMER` | 战略客户 | 不得替代主分类字段 | 否 | 是 | 用于补充检索、统计和运营标记，不驱动模板 |
| `contract_tags` | `LONG_TERM_COOPERATION` | 长期合作 | 不得替代主分类字段 | 否 | 是 | 用于补充检索、统计和运营标记，不驱动模板 |
| `contract_tags` | `URGENT` | 紧急 | 不得替代主分类字段 | 否 | 是 | 用于补充检索、统计和运营标记，不驱动模板 |
| `contract_tags` | `HIGH_AMOUNT` | 大额 | 不得替代主分类字段 | 否 | 是 | 用于补充检索、统计和运营标记，不驱动模板 |
| `contract_tags` | `FRAME_RELEASE_ORDER` | 框架下订单 | 不得替代主分类字段 | 否 | 是 | 用于框架执行场景补充标记，不驱动模板 |
| `contract_tags` | `TEMPLATE_EXCEPTION` | 模板例外 | 不得替代主分类字段 | 否 | 是 | 用于标识偏离标准模板的场景，不驱动模板 |
| `contract_tags` | `ENCRYPTION_REQUIRED` | 需加密处理 | 不得替代主分类字段 | 否 | 是 | 用于补充检索、统计和运营标记，不替代加密状态字段 |

### 4.2 合同状态枚举

规则说明：状态枚举按一期业务主链路阶段抽象，覆盖起草、审批、签订、生效、履约、变更、终止、归档，不额外扩展超出需求基线的中间业务态；合同分类字段与合同状态字段相互独立，不能用状态替代分类，也不能用分类推导流程状态。接口字段统一命名为 `contract_status`。

| 状态值 | 说明 |
| --- | --- |
| `DRAFT` | 草稿中 |
| `APPROVING` | 审批中 |
| `APPROVED` | 审批通过 |
| `REJECTED` | 审批驳回 |
| `SIGNING` | 签订中 |
| `EFFECTIVE` | 已生效 |
| `PERFORMING` | 履约中 |
| `CHANGED` | 已变更 |
| `TERMINATED` | 已终止 |
| `ARCHIVED` | 已归档 |

### 4.3 字段责任边界

- 来自 `CRM`、`SF`、`SRM`、`SAP` 的字段，以外围系统推送为准，`CMP` 只做接收、映射、校验、展示和流程承接。
- 来自 `OA` 的审批实例号、审批状态、审批意见、审批完成时间，以 `OA` 回传结果为准。
- 来自企业微信的用户身份、部门信息、登录态，仅作为身份认证和组织映射来源，不直接改写合同业务字段。
- 文档进入文档中心后，由平台内部加密服务在入库链路完成自动加密；默认情况下，密文文档不可脱离平台解密或使用。管理端针对“解密下载”的授权配置、授权查询与命中判定由 `identity-access` 主线负责；命中授权后的解密下载执行、结果、失败原因和审计信息由平台内部加密服务负责。获授权对象执行解密下载后，导出的明文文件可脱离 `CMP` 使用。
- `CMP` 自治字段包括合同内部主键、归档状态、审计字段和对外可见的任务结果字段。
- 分类字段的优先级规则为：模板默认值或外围系统映射结果先进入校验；只有主分类字段满足字典约束后，才能落入正式合同主数据。`document_form`、`business_domain`、`contract_subcategory`、`contract_detail_type` 为主分类字段，必须满足字典约束。
- `contract_subcategory` 必须同时校验上级 `document_form`、`business_domain` 是否匹配；`contract_detail_type` 必须校验是否属于所选 `contract_subcategory`。
- `contract_tags` 可补充维护，但仅允许写入受控字典值，不得反向改写 `document_form`、`business_domain`、`contract_subcategory`、`contract_detail_type`，也不得承担模板路由职责。
- 外围系统若只提供粗粒度分类，`CMP` 可先接收入站记录并标记 `classification_confirmation_status=PENDING_MANUAL_CONFIRM`，等待人工补齐主分类后再进入正式合同主数据；若请求声明为“必须直入正式合同”，则应直接校验失败而不是自动猜测。入站暂存结构留待 `Detailed Design` 定义。
- `special_fields` 由 `contract_subcategory` 和 `contract_detail_type` 共同约束；对象内部字段定义留待 `Detailed Design` 与正式 `OpenAPI` 定义。

## 5. 接口分组

### 5.1 认证与统一身份

分组边界说明：本组仅覆盖 `identity-access` 主线统一承接的会话建立、身份换取、认证回调与当前用户信息查询，不扩展外围身份中心本体能力；一期主收口对象为 `OA` / 统一认证中心 / 企业微信一键登录。

#### `POST /api/auth/password/sessions`

用途：账号密码登录并建立 `CMP` 会话；保留为补充路径，不作为企业微信和统一认证的替代主路径。

#### `POST /api/auth/sessions/exchanges`

用途：接收 `OA` / 统一认证中心 / 企业微信等外部认证结果，换取 `CMP` 会话。

请求重点字段：

- `provider`：`OA` / `SSO` / `WECOM` / `LDAP`。
- `ticket` 或 `code`。
- `redirect_uri`。

#### `POST /api/auth/callbacks/sso`

用途：接收统一认证或 `SSO` 回调结果，并生成后续换会话引用。

#### `POST /api/auth/callbacks/wecom`

用途：接收企业微信认证回调结果，并生成后续换会话引用。

#### `POST /api/auth/callbacks/ldap`

用途：接收目录认证承接结果，并生成后续换会话引用。

#### `GET /api/auth/me`

用途：获取当前用户、组织、角色、权限、多语言偏好。

### 5.2 模板、条款与起草

分组边界说明：本组只覆盖模板库、条款库、在线起草、合同信息录入及创建期附件承接，不提前把审批、签章、生效、归档混入创建接口。

#### `GET /api/templates`

用途：查询模板列表，支持模板分类、状态、版本过滤。

#### `POST /api/templates`

用途：新增模板。

#### `POST /api/templates/{template_id}/activate`

用途：模板启用。

#### `GET /api/clauses`

用途：查询条款库。

#### `POST /api/contracts/drafts`

用途：基于模板或空白方式创建合同草稿。

请求重点字段：

- `template_id`。
- `draft_mode`：`BY_TEMPLATE` / `BLANK`。
- `contract_name`。
- `document_form`：必填，取 4.1A 正式字典中的 `value`。
- `business_domain`：必填，取 4.1A 正式字典中的 `value`。
- `contract_subcategory`：必填，需满足上级约束。
- `contract_detail_type`：销售、采购、框架子类必填；HR 子类允许为空。
- `contract_tags`：选填，多选；仅接受受控字典标签值，不参与模板选择。
- `party_a_name`。
- `party_b_name`。
- `amount`。
- `currency`。
- `department_id`。
- `attachment_list`。
- `commercial_fields` / `hr_fields` / `special_fields`。

分类字段联调约束：

- `draft_mode=BY_TEMPLATE` 时，前端可先传完整分类字段组合，再由后端返回可匹配模板集合；若同时显式传入 `template_id`，则后端仍需校验模板与分类组合是否一致。
- `draft_mode=BLANK` 时，仍必须提供主分类字段，以保证后续字段校验、审批映射和检索口径一致。
- 对 `contract_detail_type` 为空的 HR 子类，不得因空值阻塞建稿。

### 5.3 合同台账与详情

分组边界说明：本组承接一期合同台账、详情、基础修改和批量导入能力；不在该组内直接承载审批动作、签章动作或归档审批动作。

#### `GET /api/contracts`

用途：查询合同列表。

请求参数：

- `keyword`。
- `document_form`：单选过滤，取正式字典枚举值。
- `business_domain`：单选过滤，取正式字典枚举值。
- `contract_subcategory`：单选过滤，需满足上级约束。
- `contract_detail_type`：单选过滤，需属于所选子类。
- `contract_tags`：多选过滤，采用“命中任一标签即可返回”的并集语义；当同时叠加其他筛选条件时，先满足其他条件，再按标签任一命中返回。
- `contract_status`。
- `archive_status`。
- `department_id`。
- `source_system`。
- `sign_date_start`。
- `sign_date_end`。
- `page`。
- `page_size`。

#### `GET /api/contracts/{contract_id}`

用途：查询合同详情，返回台账字段、附件、审批摘要、签章摘要、履约摘要、归档摘要。

#### `PATCH /api/contracts/{contract_id}`

用途：修改合同基础信息；仅允许在草稿、驳回后重提或明确允许的补录场景下更新。

#### `POST /api/import/jobs`

用途：发起批量导入任务，承接 `CRM` / `SRM` 合同文件批量导入与模板匹配场景。

#### `GET /api/import/jobs/{job_id}`

用途：查询批量导入任务状态、失败原因和模板匹配结果。

### 5.4 `OA` 审批桥接接口

分组边界说明：本组只定义 `CMP` 与 `OA` 主审批链路之间的桥接、回写与摘要接口，不承诺完整替代 `OA` 原始审批记录展示。

#### `POST /api/integrations/oa/approval-requests`

用途：由 `CMP` 发起 `OA` 审批实例，是一期审批主链路接口。

请求重点字段：

- `contract_id`。
- `oa_flow_code`。
- `form_fields`。
- `approver_list`。
- `attachment_list`。

#### `POST /api/integrations/oa/approval-callback`

用途：接收 `OA` 审批状态回调。

请求重点字段：

- `oa_instance_id`。
- `contract_id`。
- `approval_status`。
- `approved_at`。
- `comment_list`。

#### `GET /api/contracts/{contract_id}/approval-summary`

用途：查询 `CMP` 侧保存的审批摘要；一期仅保证摘要同步，不承诺完整替代 `OA` 原始审批记录展示。

### 5.5 平台审批流引擎接口

分组边界说明：本组对应一期正式建设的 `CMP` 审批流引擎能力，不是轻量接口预留。默认业务主路径仍优先走 `OA`；当 `OA` 无法满足相应审批场景时，由平台审批流引擎正式承接。

- 平台审批流引擎是一期正式能力，不是仅预留接口的后备方案。
- 本组接口覆盖流程定义、发布、实例、任务、动作与查询等 API 资源边界。
- 流程定义支持可视化配置，但本文只约束 API 资源和高阶载荷，不展开内部节点建模、规则表达或运行时快照结构。
- 审批节点必须绑定组织架构；API 仅保留该约束，不在本文展开节点绑定结构与解析细节，具体口径回指 Requirement / Architecture。

#### `POST /api/approval-engine/process-definitions`

用途：创建或导入审批流程定义，用于可视化配置后的发布入库。

请求重点字段：

- `process_code`。
- `process_name`。
- `process_version`。
- `definition_payload`：高阶流程定义载荷，用于承接可视化配置结果、节点与组织架构绑定关系以及路由编排；内部结构留待正式 `OpenAPI` 或 `Detailed Design` 定义。

#### `PUT /api/approval-engine/process-definitions/{definition_id}`

用途：更新审批流程定义与可视化配置内容。

#### `POST /api/approval-engine/process-definitions/{definition_id}/publish`

用途：发布审批流程定义，使其可被合同审批实例引用。

#### `GET /api/approval-engine/process-definitions/{definition_id}`

用途：查询审批流程定义详情、版本状态与可视化配置结果。

#### `POST /api/approval-engine/processes`

用途：创建平台审批流实例；当命中平台承接条件时，合同审批从此入口进入平台审批流引擎。

请求重点字段：

- `contract_id`。
- `definition_id`。
- `starter_user_id`。
- `business_context`。

#### `GET /api/approval-engine/tasks`

用途：查询当前用户或指定范围内的平台审批待办、已办、可处理任务。

#### `GET /api/approval-engine/tasks/{task_id}`

用途：查询单个审批任务详情、可执行动作与关联流程摘要。

#### `POST /api/approval-engine/processes/{process_id}/actions`

用途：平台审批动作处理，如同意、驳回、转办、撤回、终止、催办。

请求重点字段：

- `action_type`：如 `APPROVE`、`REJECT`、`TRANSFER`、`WITHDRAW`、`TERMINATE`、`REMIND`。
- `operator_user_id`。
- `target_user_id`：转办时必填。
- `comment`。

#### `GET /api/approval-engine/processes/{process_id}`

用途：查询平台审批流程详情、节点状态、审批历史与当前待办。

#### `GET /api/approval-engine/processes/{process_id}/diagram`

用途：查询平台审批实例当前流程图，用于前端可视化展示节点流转状态。

### 5.6 外围系统合同入站接口

分组边界说明：本组只定义合同管理平台侧统一入站壳层、字段映射和校验，不把外围系统本体改造责任写入 `CMP` 接口范围。

#### `POST /api/integrations/contracts/inbound`

用途：统一接收 `CRM`、`SF`、`SRM`、`SAP` 等外围系统推送的合同主数据或主数据补充信息。

请求重点字段：

- `source_system`。
- `source_business_id`。
- `contract_name`。
- `document_form`：正式入库必填；若入站缺失但可由映射规则唯一确定，可在入站校验阶段自动补齐；若不能唯一确定，则创建待人工确认的入站记录。
- `business_domain`：正式入库必填；若入站缺失但可由映射规则唯一确定，可在入站校验阶段自动补齐；若不能唯一确定，则创建待人工确认的入站记录。
- `contract_subcategory`：正式入库必填；若只能传上级分类或无法唯一确定子类，则创建待人工确认的入站记录，并标记 `classification_confirmation_status=PENDING_MANUAL_CONFIRM`。
- `contract_detail_type`：销售、采购、框架子类正式入库必填；HR 子类允许为空。若销售、采购、框架子类缺失该字段，同样创建待人工确认的入站记录，不得直接落正式合同主数据。
- `contract_tags`：选填，多选；仅接受受控字典标签值，仅用于补充检索与统计。
- `party_b_name`。
- `amount`。
- `currency`。
- `department_id`。
- `common_fields`。
- `commercial_fields`。
- `hr_fields`。
- `special_fields`。
- `attachment_list`。

说明：外围系统字段明细在实施调研阶段确认，本接口先定义 `CMP` 平台侧承接壳层，不在本文固定外围系统全部字段明细。

入站分类处理规则：

- 只有在主分类字段满足对应字典约束后，入站数据才可创建或更新正式合同记录；其中 HR 子类允许 `contract_detail_type` 为空。
- 对 HR 子类，`contract_detail_type` 可为空；对销售、采购、框架子类，`contract_detail_type` 缺失即视为分类未完成。
- 分类未完成的数据应标记 `classification_confirmation_status=PENDING_MANUAL_CONFIRM`；人工补齐并通过校验后，再进入正式合同主数据。
- 分类待确认记录不参与正式模板路由、正式合同台账统计和正式审批发起。

#### `POST /api/integrations/master-data/sap`

用途：接收 `SAP` 推送的客户、供应商等基础主数据。

### 5.7 企业微信接口

分组边界说明：本组覆盖企业微信身份同步与消息通知，不把企业微信写成二期可选能力。

#### `POST /api/integrations/wecom/users/sync`

用途：同步企业微信用户与部门映射关系。

#### `POST /api/integrations/wecom/messages`

用途：发送审批提醒、催办、履约提醒、变更通知等企微消息。

### 5.8 文档中心内部加密服务接口

分组边界说明：本组不是外围系统对接接口，而是 `CMP` 文档中心与 `encrypted-document` 子模块之间的内部服务契约，覆盖入库加密、受控解密访问、解密下载任务、内部作业查询与审计查询。默认情况下，密文文档不可脱离平台解密或使用；“解密下载”授权配置、授权查询与命中判定由 `identity-access` 主线负责，命中授权后的执行由本组接口负责。异步处理统一通过内部任务查询接口体现。

#### `POST /api/internal/document-center/encryption/check-in`

用途：文档进入文档中心时执行入库校验并触发自动加密。

#### `POST /api/internal/document-center/encryption/decrypt-access`

用途：平台内部用户或内部服务在满足权限校验后，申请受控自动解密访问。

#### `POST /api/internal/document-center/encryption/decrypt-downloads`

用途：在文件访问权限与 `identity-access` 主线授权命中均成立后，触发解密下载并生成受控下载任务或下载结果；导出的明文文件可脱离 `CMP` 使用。

请求重点字段：

- `contract_id`。
- `document_id` / `attachment_id`。
- `reason`。

#### `GET /api/internal/document-center/encryption/decrypt-downloads/{job_id}`

用途：查询解密下载任务状态、结果和失败原因。

#### `GET /api/internal/document-center/encryption/jobs/{job_id}`

用途：查询内部加密作业状态、失败原因与审计结果；仅用于平台内异步任务查询，不作为外部回调接口。

#### `GET /api/internal/document-center/encryption/audit-logs`

用途：按合同、文档、部门、人员、动作类型查询加密访问、受控解密、解密下载等审计日志。

说明：本组接口分别承载入库加密、受控解密访问、解密下载、任务查询与审计查询等内部资源边界；“解密下载”授权配置、授权查询与命中判定已收口到 `identity-access` 主线。默认语义仍为密文文档不可脱离平台使用；命中授权后的解密下载属于受控例外，且导出明文文件可脱离 `CMP` 使用。

### 5.9 签章、生效与纸质备案

分组边界说明：本组覆盖审批通过后的签章申请、纸质备案和生效确认，不提前混入履约、变更或归档动作。

#### `POST /api/contracts/{contract_id}/signatures/apply`

用途：发起电子签章申请。

#### `POST /api/contracts/{contract_id}/paper-records`

用途：登记纸质合同扫描件、签约人、签约日期。

#### `POST /api/contracts/{contract_id}/effectiveness/confirm`

用途：确认合同生效。

### 5.10 履约、变更、终止、违约、归档

分组边界说明：本组覆盖生效后履约跟踪、变更、终止、违约处理与归档闭环，是一期主业务闭环收口分组。

#### `POST /api/contracts/{contract_id}/performance-nodes`

用途：新增履约节点。

#### `POST /api/contracts/{contract_id}/changes`

用途：发起合同变更。

#### `POST /api/contracts/{contract_id}/terminations`

用途：发起合同终止。

#### `POST /api/contracts/{contract_id}/breaches`

用途：登记违约事项与证据。

#### `POST /api/contracts/{contract_id}/archive`

用途：执行单合同归档。

#### `POST /api/archive/jobs`

用途：批量归档任务，采用异步方式。

#### `POST /api/archive/borrow-requests`

用途：发起档案借阅申请。

#### `POST /api/archive/borrow-requests/{request_id}/approve`

用途：处理档案借阅审批。

#### `POST /api/archive/borrow-requests/{request_id}/return`

用途：登记档案归还。

### 5.11 检索、通知、统计、日志

分组边界说明：本组承接运营与审计能力，包括检索、消息中心、通知偏好、统计导出与日志查询，不把 AI 任务接口混入本组。

#### `GET /api/search/contracts`

用途：合同结构化检索与全文检索。

#### `GET /api/notifications/feed`

用途：消息中心查询。

#### `POST /api/notifications/preferences`

用途：维护用户通知偏好，包括站内、邮件、企业微信。

#### `POST /api/reports/jobs`

用途：生成统计报表导出任务。

#### `GET /api/logs/audit`

用途：查询审计日志、作业日志、接口日志。

### 5.12 AI / OCR 辅助接口

分组边界说明：本组只保留 AI / OCR 辅助接口，并明确异步和人工确认约束，不把 AI 输出写成可替代人工审核或审批的主决策接口；接口对外按能力类型与标准结果结构收口，内部通过 Provider abstraction 路由到具体模型或服务实现。

#### `POST /api/ocr/jobs`

用途：创建 `OCR` 任务。

#### `GET /api/ocr/jobs/{job_id}`

用途：查询 `OCR` 任务结果。

#### `POST /api/ai/extractions`

用途：执行关键信息提取、摘要、辅助问答索引。

请求重点字段：

- `capability_type`：如 `EXTRACTION`、`SUMMARY`、`QA_INDEX`。
- `provider_key`：选填；仅表示抽象 Provider 标识，不直接暴露具体模型厂商名称，未传时由平台按策略路由。

#### `POST /api/ai/drafts`

用途：生成合同草案建议。

请求重点字段：

- `capability_type`：固定为 `DRAFT_GENERATION`。
- `provider_key`：选填；用于抽象 Provider 路由，不要求前端绑定具体模型。

#### `POST /api/ai/reviews`

用途：执行智能合同审核。

请求重点字段：

- `capability_type`：固定为 `CONTRACT_REVIEW`。
- `provider_key`：选填；用于抽象 Provider 路由，不暴露具体模型细节。

#### `POST /api/ai/risk-warnings`

用途：生成履约风险预警。

请求重点字段：

- `capability_type`：固定为 `RISK_WARNING`。
- `provider_key`：选填；用于抽象 Provider 路由，不暴露具体模型细节。

#### `POST /api/ai/results/{result_id}/confirm`

用途：人工确认 AI / OCR 结果。

说明：AI 输出仅作辅助，不替代人工审核、审批或业务判断。

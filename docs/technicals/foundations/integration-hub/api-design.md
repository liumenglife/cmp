# 外围系统集成主线 API Design

## 1. 文档说明

本文档是 `CMP` 外围系统集成主线的第一份正式 `API Design`。
它基于 [`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)、
[`总平台 Architecture Design`](../../architecture-design.md)、
[`总平台 API Design`](../../api-design.md)、
[`总平台 Detailed Design`](../../detailed-design.md) 与
[`integration-hub Architecture Design`](./architecture-design.md)，
用于固化外围系统集成主线的接口边界、资源契约、鉴权约束、错误码复用策略，
以及与平台真相源协作时对外暴露的接口面。

### 1.1 输入

- [`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- [`总平台 Architecture Design`](../../architecture-design.md)
- [`总平台 API Design`](../../api-design.md)
- [`总平台 Detailed Design`](../../detailed-design.md)
- [`integration-hub Architecture Design`](./architecture-design.md)

### 1.2 输出

- 本文：[`API Design`](./api-design.md)
- 为后续 [`Detailed Design`](./detailed-design.md) 提供明确下沉边界

### 1.3 阅读边界

本文只定义外围系统集成主线的接口契约，不展开以下内容：

- 不复述一期需求范围、系统集成范围说明或实施排期
- 不写外围系统 SDK、签名算法、字段映射细节、回调脚本或联调步骤
- 不写物理表结构、索引、队列主题、任务补偿实现或内部类划分
- 不把 `CRM`、`SF`、`SRM`、`SAP`、企业微信、`OA` 的本体改造接口写成 `CMP` 责任

## 2. API 边界

### 2.1 边界总则

- 外围系统只通过集成主线进入 `CMP`，业务模块不得私接外部入站、出站或回调入口。
- 集成主线暴露的是“平台受治理的交换接口”，不是外部系统原生接口的镜像。
- `CRM`、`SF`、`SRM`、`SAP` 提供外部主数据或业务事实，但不直接成为平台真相源。
- 企业微信是正式移动承载端之一，承接移动登录、消息触达与轻量业务动作；
  原生优先，小程序兜底，不回桌面 `Web` 形成正式业务闭环。
- `OA` 是默认主审批路径外部系统；平台保留正式审批承接能力，但该能力边界属于
  流程引擎主线，不由集成主线重新定义流程运行时真相。

### 2.2 企业微信接入接口边界

企业微信边界只覆盖移动入口票据交换、原始协议验签、组织同步、消息触达。
消息送达与轻量动作回执通过统一回调入口承接。
它不暴露合同主档、流程实例、文档主档的内部写入细节。
企业微信进入平台后的主体准入、绑定预检查、冲突冻结与平台会话签发统一归 `identity-access` 承接；集成主线只返回外部票据标准化结果或协议交换引用，不返回平台访问令牌。

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `GET /api/integrations/wecom/auth-url` | 获取企业微信登录跳转地址 | `redirect_uri`、`state` | `auth_url`、`expires_at` |
| `POST /api/integrations/wecom/protocol-exchanges` | 用企业微信票据换取标准化协议交换引用 | `code`、`state` | `protocol_exchange_ref`、`external_ticket_result`、`handoff_target` |
| `POST /api/integrations/wecom/users/sync` | 同步企业微信部门 / 人员原始目录数据 | `external_request_id`、`department_list`、`user_list` | `inbound_message_id`、`ingest_status` |
| `POST /api/integrations/wecom/messages` | 发送待办、催办、通知消息 | `message_type`、`receiver_list`、`template_code`、`business_ref` | `dispatch_id`、`dispatch_status` |

边界约束：

- 企业微信接口只承接移动身份、触达与轻量动作，不承接合同真相直接写入。
- 企业微信票据换取只完成外部换票、验签、原始协议适配和标准化输出；平台主体映射、会话准入和令牌签发不得在集成主线完成。
- 复杂业务动作由企业微信内正式承载面发起平台 API，不通过“跳回桌面 `Web`”兜底。
- 企业微信回执进入统一回调入口，不允许业务模块单独暴露企业微信回调地址。

### 2.3 `OA` 桥接接口边界

`OA` 边界只覆盖默认主审批路径的发起、状态回写、摘要查询与桥接绑定。
`OA` 审批状态回调通过统一回调入口承接。
`CMP` 不对外承诺复刻 `OA` 全量流程细节页面。

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/integrations/oa/approval-requests` | 发起 `OA` 审批桥接请求 | `contract_id`、`workflow_ref`、`form_payload`、`attachment_list` | `dispatch_id`、`oa_instance_id`、`dispatch_status` |
| `GET /api/integrations/oa/approval-requests/{dispatch_id}` | 查询桥接请求结果与外部实例绑定 | 路径参数 `dispatch_id` | `dispatch_status`、`oa_instance_id`、`last_result_code` |
| `GET /api/integrations/oa/approval-summaries/{contract_id}` | 查询平台侧保存的 `OA` 审批摘要 | 路径参数 `contract_id` | `approval_mode`、`current_node`、`approval_status`、`last_synced_at` |

边界约束：

- `OA` 只作为默认主审批外部路径，不成为合同主档或流程主档真相源。
- 集成主线只暴露桥接摘要，不定义 `OA` 原始流程图、节点脚本和表单细节契约。
- `OA` 回调统一进入集成主线，再由平台转译到流程引擎与合同主档边界。

### 2.4 `CRM` / `SF` / `SRM` / `SAP` 入站接口边界

这一组接口统一承接外部主数据、业务单据、合同补充信息与附件入站。
入站代表“外部事实到达平台”，不代表外部系统自动拥有平台正式对象主权。

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/integrations/inbound-messages` | 通用入站入口，承接业务事实、主数据、合同补充信息 | `source_system`、`message_type`、`external_request_id`、`payload` | `inbound_message_id`、`ingest_status` |
| `POST /api/integrations/inbound-messages/contracts` | 承接合同类入站壳层 | `source_system`、`source_business_id`、`contract_payload`、`attachment_list` | `inbound_message_id`、`binding_status` |
| `POST /api/integrations/inbound-messages/master-data` | 承接客户、供应商、组织、编码等主数据入站 | `source_system`、`master_data_type`、`record_list` | `inbound_message_id`、`accepted_count` |
| `GET /api/integrations/inbound-messages/{inbound_message_id}` | 查询入站受理、校验与承接结果 | 路径参数 `inbound_message_id` | `ingest_status`、`processing_status`、`binding_result` |

边界约束：

- `CRM`、`SF`、`SRM`、`SAP` 输入的是外部业务事实或主数据投影，不直接创建第二份
  合同真相源。
- 入站接口允许接收“尚不能直接落正式对象”的数据，但只形成待承接状态、绑定关系
  或待处理记录，不直接越过平台规则写入核心真相对象。
- 字段映射规则、码表对照、附件装配细节下沉到 `Detailed Design`。

### 2.5 平台事实出站接口边界

出站接口只发送平台受治理后的业务事实投影，不向外暴露内部私有运行细节。

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/integrations/outbound-dispatches` | 创建通用出站派发任务 | `target_system`、`dispatch_type`、`object_type`、`object_id` | `dispatch_id`、`dispatch_status` |
| `POST /api/integrations/outbound-dispatches/contracts` | 向外围系统发送合同事实投影 | `target_system`、`contract_id`、`projection_scope` | `dispatch_id`、`target_request_ref` |
| `POST /api/integrations/outbound-dispatches/documents` | 向外围系统发送文档事实投影 | `target_system`、`document_id`、`document_projection` | `dispatch_id`、`dispatch_status` |
| `GET /api/integrations/outbound-dispatches/{dispatch_id}` | 查询出站派发状态与最近结果 | 路径参数 `dispatch_id` | `dispatch_status`、`last_result_code`、`last_attempt_at` |

边界约束：

- 出站内容来源于合同主档、文档中心、流程摘要、通知事件等受控投影。
- 出站契约只承诺稳定业务语义，不承诺平台内部字段全集、状态机内部节点或私有审计字段。
- 同一业务对象可对不同外部系统形成不同出站投影，但都通过统一 `OutboundDispatch`
  资源治理。

### 2.6 回调接口边界

所有外部回调统一进入集成主线，以 `CallbackReceipt` 资源承接。

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/integrations/callback-receipts/wecom` | 接收企业微信回调 | `external_receipt_id`、`receipt_type`、`payload` | `callback_receipt_id`、`accepted` |
| `POST /api/integrations/callback-receipts/oa` | 接收 `OA` 回调 | `external_receipt_id`、`receipt_type`、`payload` | `callback_receipt_id`、`accepted` |
| `POST /api/integrations/callback-receipts/business-systems` | 接收 `CRM` / `SF` / `SRM` / `SAP` 回调 | `source_system`、`external_receipt_id`、`payload` | `callback_receipt_id`、`accepted` |
| `GET /api/integrations/callback-receipts/{callback_receipt_id}` | 查询回调处理结果 | 路径参数 `callback_receipt_id` | `receipt_status`、`processing_status`、`linked_object` |

边界约束：

- 不让业务模块私接外部回调入口。
- 回调受理成功仅表示平台已接收并进入处理，不等于业务状态已完成回写。
- 回调顺序控制、重试、去重与补偿属于实现细节，下沉到 `Detailed Design`。

### 2.7 集成任务与审计查询接口边界

集成主线需要暴露统一任务和审计查询视图，支持联调、运行治理与问题追踪。

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `GET /api/integrations/jobs` | 查询集成任务列表 | `job_type`、`job_status`、`source_system`、`target_system` | `job_list`、`total` |
| `GET /api/integrations/jobs/{job_id}` | 查询单个集成任务详情 | 路径参数 `job_id` | `job_status`、`result_code`、`related_resource` |
| `GET /api/integrations/audit-views` | 查询集成审计视图 | `object_type`、`object_id`、`direction`、`trace_id` | `item_list`、`total` |
| `GET /api/integrations/audit-views/{audit_view_id}` | 查询单条集成审计视图详情 | 路径参数 `audit_view_id` | `summary`、`resource_refs`、`latest_result` |

## 3. 核心资源划分

### 3.1 `InboundMessage`

`InboundMessage` 是外围系统入站请求在平台侧的统一受理资源。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `inbound_message_id` | string | 入站消息主键 |
| `source_system` | string | 来源系统，如 `CRM`、`SF`、`SRM`、`SAP`、`WECOM`、`OA` |
| `message_type` | string | 入站消息类型，如 `MASTER_DATA`、`CONTRACT_FACT`、`EVENT` |
| `external_request_id` | string | 外部请求幂等标识 |
| `object_type` | string | 目标业务对象类型 |
| `object_hint` | object | 外部传入的业务对象识别摘要 |
| `ingest_status` | string | 受理状态 |
| `processing_status` | string | 平台承接处理状态 |
| `received_at` | string | 接收时间 |
| `processed_at` | string | 处理完成时间，可为空 |

资源边界：

- 它表示“平台已接收到外部事实”，不等于“平台已形成正式业务对象”。
- 它可以关联合同主档、文档主档、流程摘要等平台对象，但不替代这些对象。

### 3.2 `OutboundDispatch`

`OutboundDispatch` 是平台向外围系统发起派发的统一出站资源。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dispatch_id` | string | 出站派发主键 |
| `target_system` | string | 目标系统 |
| `dispatch_type` | string | 派发类型，如 `CONTRACT_FACT`、`DOCUMENT_FACT`、`MESSAGE` |
| `object_type` | string | 出站对象类型 |
| `object_id` | string | 平台对象主键 |
| `dispatch_status` | string | 派发状态 |
| `target_request_ref` | string | 目标系统请求引用，可为空 |
| `last_result_code` | string | 最近结果码，可为空 |
| `dispatched_at` | string | 最近派发时间，可为空 |
| `completed_at` | string | 完成时间，可为空 |

资源边界：

- 它只描述出站受理、发送、结果与重试视图，不替代合同、文档、流程本体状态。
- 一个业务对象可关联多个 `OutboundDispatch`，分别面向不同目标系统或不同派发类型。

### 3.3 `CallbackReceipt`

`CallbackReceipt` 是外部系统回调在平台侧的统一承接资源。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `callback_receipt_id` | string | 回调回执主键 |
| `source_system` | string | 回调来源系统 |
| `receipt_type` | string | 回执类型，如 `APPROVAL_STATUS`、`MESSAGE_DELIVERY` |
| `external_receipt_id` | string | 外部回调主键 |
| `linked_dispatch_id` | string | 关联出站派发，可为空 |
| `receipt_status` | string | 回调受理状态 |
| `processing_status` | string | 回调处理状态 |
| `occurred_at` | string | 外部事件发生时间 |
| `received_at` | string | 平台接收时间 |

资源边界：

- 它表达“外部结果已回传到平台”，不直接等于业务主状态最终落地。
- 它统一承接回调可信性、幂等性和处理结果摘要，但不承担业务模块私有回执结构。

### 3.4 `IntegrationBinding`

`IntegrationBinding` 表示平台交换对象与外部系统对象之间的稳定绑定关系。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `binding_id` | string | 绑定关系主键 |
| `system_name` | string | 外部系统名称 |
| `object_type` | string | 平台对象类型 |
| `object_id` | string | 平台对象主键 |
| `external_object_type` | string | 外部对象类型 |
| `external_object_id` | string | 外部对象主键 |
| `binding_status` | string | 绑定状态 |
| `bound_at` | string | 绑定生效时间 |
| `last_verified_at` | string | 最近校验时间，可为空 |

资源边界：

- 它只表达外部交换引用或投影绑定，不表达身份、组织、合同、文档、流程的业务真相本体。
- 身份、组织相关绑定只允许保存 `identity-access` 已确认对象引用，不保存第二套用户或组织映射结论。
- `OA` 实例绑定、企业微信已确认主体引用、外部合同来源绑定都通过该资源统一表达。

### 3.5 `IntegrationJob`

`IntegrationJob` 是集成主线对异步处理单元的统一任务视图。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `job_id` | string | 任务主键 |
| `job_type` | string | 任务类型，如 `INBOUND_PROCESS`、`OUTBOUND_RETRY`、`CALLBACK_PROCESS` |
| `job_status` | string | 任务状态 |
| `resource_type` | string | 关联资源类型 |
| `resource_id` | string | 关联资源主键 |
| `result_code` | string | 任务结果码，可为空 |
| `result_message` | string | 任务结果摘要，可为空 |
| `scheduled_at` | string | 调度时间 |
| `finished_at` | string | 完成时间，可为空 |

资源边界：

- 它是集成主线的任务视图，不替代总平台统一任务中心的正式任务主键体系。
- 对外只暴露任务状态与资源关联，不暴露内部重试参数、执行器拓扑或调度实现细节。

### 3.6 `IntegrationAuditView`

`IntegrationAuditView` 是供运行治理与审计查询使用的集成只读视图资源。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `audit_view_id` | string | 审计视图主键 |
| `direction` | string | `INBOUND` / `OUTBOUND` / `CALLBACK` |
| `system_name` | string | 外部系统名称 |
| `object_type` | string | 关联对象类型 |
| `object_id` | string | 关联对象主键 |
| `trace_id` | string | 链路追踪标识 |
| `latest_result` | string | 最近处理结果 |
| `resource_refs` | array | 关联资源引用列表 |
| `occurred_at` | string | 事件时间 |

资源边界：

- 它是查询视图，不承担写入接口主资源职责。
- 它聚合入站、出站、回调与任务结果，但不替代底层审计中心完整留痕。

## 4. 统一约定

### 4.1 协议

- 继承 [`总平台 API Design`](../../api-design.md) 的统一约定，默认使用
  `HTTPS + JSON + UTF-8`。
- 同步查询与轻量同步写操作返回统一响应结构。
- 受理但需异步处理的接口返回 `202`，并返回对应资源主键或 `job_id`。
- 外部回调接口返回体保持统一结构，便于外围系统直接记录平台受理结果。

### 4.2 鉴权

- 面向平台前端或平台内部服务的接口，沿用总平台登录态、访问令牌或内部服务鉴权。
- 面向外部系统的入站、出站回执、回调接口，统一要求系统级身份凭证、请求签名、
  时间戳与重放保护。
- 具体签名算法、凭证换取方式、证书或密钥托管策略不在本文展开。
- 审计查询、任务查询、绑定关系查询默认属于平台内部或受控管理员接口，不直接向外部
  系统开放全量读取能力。

### 4.3 幂等

- 所有外部写接口都必须支持幂等控制。
- 入站优先使用 `external_request_id` 或等价外部业务键作为幂等主键。
- 出站优先使用 `dispatch_id` 作为平台内部幂等与追踪主键，对外可附带目标系统请求引用。
- 回调优先使用 `external_receipt_id` + `source_system` 作为回调去重标识。
- 若相同幂等键对应不同请求体，应返回 `40905` `IDEMPOTENCY_CONFLICT`。

### 4.4 命名规范继承总平台 API Design

- 路径段使用 `kebab-case`。
- 请求字段、响应字段、路径参数、查询参数使用 `snake_case`。
- 资源主键统一使用 `<resource>_id` 形式。
- 外部系统对象键统一使用 `<system>_<resource>_id` 或 `external_*` 形式。
- 枚举值统一使用 `UPPER_SNAKE_CASE`。

### 4.5 错误码复用策略

集成主线优先复用 [`总平台 API Design`](../../api-design.md) 的统一错误码，
不在本文件另起一套错误体系。

| 业务错误码 | 错误名称 | 集成主线使用场景 |
| --- | --- | --- |
| `40001` | `INVALID_PAYLOAD` | 回调、入站、出站创建请求结构非法 |
| `40002` | `INVALID_FIELD_VALUE` | `source_system`、`message_type`、`dispatch_type` 等字段值非法 |
| `40103` | `CALLBACK_SIGNATURE_INVALID` | 回调或入站签名校验失败 |
| `40301` | `PERMISSION_DENIED` | 无权限查询任务、审计或绑定关系 |
| `40403` | `EXTERNAL_INSTANCE_NOT_FOUND` | 查询不存在的外部实例、派发或回调对象 |
| `40905` | `IDEMPOTENCY_CONFLICT` | 相同幂等键对应不同请求语义 |
| `40906` | `DUPLICATE_EXTERNAL_REQUEST` | 外部系统重复推送且当前策略不允许覆盖 |
| `42203` | `OA_SYNC_FAILED` | `OA` 桥接发起、同步或回写失败 |
| `42204` | `WECOM_SYNC_FAILED` | 企业微信身份同步、消息发送或回执处理失败 |
| `42207` | `EXTERNAL_CALLBACK_PROCESSING_FAILED` | 回调已到达但平台内部承接失败 |
| `50201` | `EXTERNAL_SYSTEM_UNAVAILABLE` | 外部系统不可用、超时或返回非预期结果 |
| `50301` | `ASYNC_JOB_BACKLOG` | 集成异步任务积压，暂时无法继续受理 |

新增集成域错误码时，应在总平台错误码体系内扩展，不在本主线局部私有化命名。

## 5. 与合同主档的接口边界

集成主线与合同主档的关系是“受理外部合同事实并请求合同主档承接”，
而不是“直接把外部合同报文当作正式合同主档”。

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/internal/integrations/contracts/upsert-requests` | 将已校验的外部合同事实提交给合同主档承接 | `inbound_message_id`、`binding_id`、`contract_projection` | `contract_id`、`accepted` |
| `GET /api/internal/integrations/contracts/{contract_id}/projections/{target_system}` | 读取某合同面向指定外部系统的出站投影 | 路径参数 `contract_id`、`target_system` | `contract_projection`、`projection_version` |
| `POST /api/internal/integrations/contracts/{contract_id}/status-feedback` | 将外部状态结果转译后提交给合同主档 | `callback_receipt_id`、`status_type`、`status_payload` | `accepted`、`contract_status` |

边界约束：

- 合同主档负责决定合同是否创建、更新、推进状态或拒绝承接。
- 集成主线只提交“已校验、已归类、可追踪”的外部事实与出站投影请求。
- 外部来源绑定、外部实例号、外部单据号等跨系统标识由集成主线统一治理，
  不要求合同主档直接理解所有外部协议细节。

## 6. 与文档中心的接口边界

集成主线与文档中心的关系是“围绕文件真相做受控输入输出”，
不直接管理文件版本链、加密内部流水或文档协作结构。

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/internal/integrations/documents/check-in-requests` | 将外部附件提交给文档中心受理 | `inbound_message_id`、`document_source`、`file_ref` | `document_id`、`accepted` |
| `GET /api/internal/integrations/documents/{document_id}/projections/{target_system}` | 读取文档中心面向外部系统的文档投影 | 路径参数 `document_id`、`target_system` | `document_projection`、`projection_version` |
| `POST /api/internal/integrations/documents/{document_id}/dispatch-requests` | 创建面向外部系统的文档出站请求 | `target_system`、`projection_scope` | `dispatch_id`、`accepted` |

边界约束：

- 文档中心拥有文件真相、版本链与内部加密治理；集成主线不越界定义这些内部结构。
- 外部文件入站后必须先进入文档中心正式受理，再关联业务对象。
- 出站文档只允许基于文档中心提供的受控投影，不允许绕过文档中心从业务模块私有附件
  直接向外分发。

## 7. 与流程引擎的接口边界

集成主线与流程引擎的关系是“桥接外部审批事实与平台流程摘要”，
而不是“重写流程引擎本体接口”。

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/internal/integrations/workflows/oa-bridge-requests` | 将合同审批桥接到 `OA` | `contract_id`、`workflow_instance_id`、`oa_request_payload` | `dispatch_id`、`binding_id` |
| `POST /api/internal/integrations/workflows/callback-transfers` | 将外部审批回调转译给流程引擎 | `callback_receipt_id`、`workflow_ref`、`approval_projection` | `accepted`、`workflow_status` |
| `GET /api/internal/integrations/workflows/{workflow_instance_id}/external-summary` | 查询流程实例的外部审批摘要 | 路径参数 `workflow_instance_id` | `approval_mode`、`external_summary` |

边界约束：

- `OA` 是默认主审批路径外部系统，但流程实例、节点绑定、节点流转与平台承接逻辑
  仍由流程引擎治理。
- 集成主线只关心桥接请求、外部实例绑定、回调转译和摘要回写。
- 当审批由平台流程引擎直接承接时，集成主线只承担必要的外部通知或状态同步接口面。

## 8. 与通知、审计、搜索、AI 的接口边界

### 8.1 与通知的接口边界

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/internal/integrations/notifications/dispatch-requests` | 触发集成相关通知 | `event_type`、`channel_list`、`receiver_list`、`business_ref` | `notification_request_id`、`accepted` |

边界约束：

- 集成主线定义何时需要通知，通知中心定义如何分发。
- 企业微信可作为通知渠道之一，但通知治理不等于企业微信接口本身。

### 8.2 与审计的接口边界

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/internal/integrations/audit-events` | 记录关键集成审计事件 | `resource_type`、`resource_id`、`action_type`、`result_status` | `audit_event_id` |
| `GET /api/internal/integrations/audit-views/{trace_id}` | 按链路追踪读取聚合审计视图 | 路径参数 `trace_id` | `audit_view` |

边界约束：

- 集成主线只定义集成留痕写入点与查询视图，不替代总平台审计中心完整模型。

### 8.3 与搜索的接口边界

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/internal/integrations/search-index-refresh-requests` | 请求刷新集成相关索引投影 | `object_type`、`object_id`、`refresh_reason` | `job_id`、`accepted` |

边界约束：

- 搜索消费的是平台承接后的对象摘要与投影，不直接索引外围原始报文作为正式主对象。

### 8.4 与 AI 的接口边界

| 接口 | 用途 | 请求重点 | 响应重点 |
| --- | --- | --- | --- |
| `POST /api/internal/integrations/ai-context-requests` | 为 AI 提供集成摘要上下文 | `object_type`、`object_id`、`context_scope` | `context_ref`、`summary_payload` |

边界约束：

- AI 消费的是平台统一化后的集成摘要、异常语义和链路视图。
- 集成主线不把外部原始协议、签名串或供应商 SDK 细节直接暴露给 AI 业务接口。

## 9. 同步 / 异步 / 回调边界

### 9.1 同步边界

- 鉴权换取、轻量查询、摘要读取、部分消息发送受理可以同步返回。
- 同步成功只代表接口受理成功，不保证外部系统最终状态已完成闭环。

### 9.2 异步边界

- 入站承接、出站派发、回调处理、重试补偿、索引刷新、通知联动等场景统一允许异步化。
- 异步接口返回 `202` 时，必须返回 `inbound_message_id`、`dispatch_id`、
  `callback_receipt_id` 或 `job_id` 中的至少一种追踪主键。

### 9.3 回调边界

- 回调是外部系统对既有动作或既有业务事实的结果回传。
- 回调接口只承诺统一受理、统一校验、统一追踪和统一错误表达。
- 回调受理后的业务推进，由合同主档、文档中心、流程引擎、通知中心等真相源或平台底座
  分别承接。

### 9.4 选择原则

- 需要立即给调用方确认“是否接收”的场景优先同步受理。
- 需要跨系统等待、补偿、重试或人工介入的场景优先异步治理。
- 对同一外部系统，可以同时存在同步请求、异步任务和回调闭环，不强行合并成单一模式。

## 10. 需要下沉到该主线 Detailed Design 的内容边界

以下内容明确不在本文展开，应下沉到 [`Detailed Design`](./detailed-design.md)：

- 外围系统适配器内部组件拆分、类边界与调用时序
- 签名算法、验签流程、密钥托管、证书轮换与安全配置细节
- 外围字段映射、码表映射、枚举转译、附件装配与差异兼容规则
- 入站、出站、回调的内部状态机、补偿规则、顺序控制与重试策略
- `IntegrationJob` 与总平台任务中心之间的内部映射方式
- 审计事件落库模型、运行指标口径、告警规则与人工介入台账
- 外部系统联调顺序、测试账号准备、灰度切换与实施批次安排

## 11. 一致性检查结论

- 已覆盖企业微信、`OA`、`CRM` / `SF` / `SRM` / `SAP`、平台出站、统一回调、
  集成任务与审计查询等关键接口边界。
- 已将合同主档、文档中心、流程引擎、通知、审计、搜索、AI 的协作接口面单列，
  保持“集成主线只做边界治理，不持有平台真相源”的约束。
- 未展开外围系统 SDK、签名算法、字段映射、物理表结构、重试参数等 `Detailed Design`
  层内容。
- 文风、命名与统一约定继承 [`总平台 API Design`](../../api-design.md)，
  且边界判断与 [`integration-hub Architecture Design`](./architecture-design.md) 保持一致。

# 外围系统集成主线 Detailed Design

## 1. 文档说明

本文档是 `CMP` 外围系统集成主线的第一份正式 `Detailed Design`。
它在 [`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)、
[`总平台 Architecture Design`](../../architecture-design.md)、
[`总平台 API Design`](../../api-design.md)、
[`总平台 Detailed Design`](../../detailed-design.md)、
[`integration-hub Architecture Design`](./architecture-design.md)、
[`integration-hub API Design`](./api-design.md) 已经确定的边界之内，
继续下沉外围系统集成主线的内部实现层设计。

### 1.1 输入

- [`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- [`总平台 Architecture Design`](../../architecture-design.md)
- [`总平台 API Design`](../../api-design.md)
- [`总平台 Detailed Design`](../../detailed-design.md)
- [`integration-hub Architecture Design`](./architecture-design.md)
- [`integration-hub API Design`](./api-design.md)

### 1.2 输出

- 本文：[`Detailed Design`](./detailed-design.md)
- 为后续外围系统专项适配设计、字段映射定稿、联调脚本与实现代码提供内部基线

### 1.3 阅读边界

本文只写外围系统集成主线的内部实现层，不展开以下内容：

- 不复述一期需求范围、系统集成范围总览或验收口径
- 不重写总平台或本主线的架构总览、系统拓扑、责任分层
- 不重列对外 API 资源清单、路径、字段样例与错误码全集
- 不写实施排期、联调顺序、责任人拆分与上线计划
- 不把外围系统本体改造职责写成 `CMP` 内部实现责任

## 2. 设计目标与约束落点

### 2.1 设计目标

- 在平台内建立唯一正式的外部接入主链路，业务模块不得私接外部入站、出站或回调入口
- 用统一内部模型承接企业微信、`OA`、`CRM`、`SF`、`SRM`、`SAP` 的协议差异
- 保持合同主档是业务真相源、文档中心是文件真相源、流程引擎是运行时真相源
- 让入站、出站、回调都具备可追踪、可重试、可补偿、可审计、可恢复能力
- 控制外部系统差异停留在适配层，不把外部协议细节扩散到合同、文档、流程模块

### 2.2 约束落点

- `OA` 是默认主审批路径外部系统，因此审批桥接内部模型围绕
  “桥接请求 + 外部实例绑定 + 回调转译 + 平台摘要回写”组织，
  不把 `OA` 原始流程运行时直接升格为平台真相
- 企业微信是正式移动承载端之一，原生优先、小程序兜底，不回桌面 `Web`；
  因此企业微信适配器只承接身份、组织同步、消息触达、轻量动作回执，
  复杂业务动作仍回到平台正式 API
- `CRM` / `SF` / `SRM` / `SAP` 提供外部主数据或业务事实，
  不直接成为平台真相源；因此入站模型先形成“外部事实受理记录”，
  再交由合同主档、文档中心、流程引擎决定是否承接
- 平台不承担外部系统本体改造职责；因此适配层只保证平台侧协议转换、
  鉴权、重试、补偿、审计与对账，不设计外部系统内部改造方案
- 部署基线是 `Docker Compose / 企业内网`、数据库是 `MySQL`、
  且总平台保留 `DB abstraction layer`；因此主线采用表驱动任务与异步作业，
  不以 `MQ` 或分布式工作流引擎为前提

### 2.3 真相源与主线边界

- 集成主线持有的是“交换过程真相”和“跨系统交换对象绑定记录”
- 合同主档持有合同业务真相
- 文档中心持有文件对象与版本链真相
- 流程引擎持有流程定义、实例、节点流转真相
- `identity-access` 持有外部身份进入平台后的 `protocol_exchange_id`、绑定预检查、冲突冻结、人工处置与会话准入真相
- 集成主线不得再维护第二份合同状态、文件状态或流程状态主表
- 集成主线也不得维护第二份外部身份协议治理主对象；身份接入相关适配结果进入 `identity-access` 后，由后者继续承接正式治理语义

## 3. 模块内部拆分

### 3.1 内部模块清单

| 内部模块 | 主要职责 | 不负责内容 |
| --- | --- | --- |
| `IntegrationIngress` | 统一承接入站与回调受理、鉴权、验签、幂等预判、原始报文固化 | 不直接写合同/文档/流程真相表 |
| `IntegrationDispatch` | 统一创建出站派发、投影装配、目标路由、发送编排、结果归档 | 不持有业务对象主状态 |
| `IntegrationBinding` | 维护平台交换对象与外部对象、实例、已确认身份引用、已确认组织引用的绑定关系 | 不定义身份、组织或业务对象真相 |
| `IntegrationAdapterRuntime` | 适配器注册、协议封装、错误转译、统一请求上下文 | 不暴露外部 SDK 细节给业务模块 |
| `IntegrationJobRuntime` | 异步任务入队、领取、重试、补偿、恢复、人工介入挂起 | 不替代总平台任务中心 |
| `IntegrationAuditObserver` | 审计明细、链路追踪、运行指标、告警事件写入 | 不替代总平台通用监控平台 |
| `IntegrationRecoveryService` | 失败重放、死信处理、顺序修复、对账恢复、人工处理辅助 | 不修改外部系统本体状态 |

### 3.2 内部调用主链路

1. 外部请求先进入 `IntegrationIngress`。
2. 入口完成来源识别、验签、标准头解析、幂等键提取、原始报文落库。
3. 入口把报文归一成内部 `CanonicalEnvelope`，再交给对应路由器。
4. 路由器根据 `message_type`、`receipt_type`、`dispatch_type` 选择适配器与下游真相源。
5. 若需要异步承接，创建 `ih_integration_job` 并映射到总平台任务中心。
6. 真相源承接结果、出站结果、回调结果统一回写交换主表、绑定表、审计表。
7. 异常进入补偿、恢复、告警与人工介入闭环。

### 3.3 关键内部对象

| 内部对象 | 含义 | 说明 |
| --- | --- | --- |
| `CanonicalEnvelope` | 统一交换壳层 | 描述一次入站、出站或回调的统一上下文 |
| `SourceIdentity` | 来源身份 | 描述 `source_system`、凭证、签名摘要、租户上下文 |
| `BusinessProjection` | 业务投影 | 面向合同、文档、流程、通知等下游的稳定输入 |
| `DispatchPlan` | 派发计划 | 描述目标系统、发送模式、重试策略、回调预期 |
| `BindingRef` | 绑定引用 | 描述平台交换对象与外部对象、实例、已确认身份引用、已确认组织引用间关系 |
| `RecoveryTicket` | 恢复工单 | 描述失败链路的恢复方式、人工介入状态与最后结论 |

### 3.4 业务模块接入原则

- 业务模块只能调用集成主线内部服务创建出站或读取绑定结果
- 业务模块不得直接拼外部 URL、直接调用外部 SDK、直接暴露回调 Controller
- 业务模块需要入站承接时，只接收已经过适配层归一后的 `BusinessProjection`
- 外部系统差异由适配器隔离，业务模块只感知平台统一字段和统一错误语义

## 4. 核心物理表设计

### 4.1 建表原则

- 本主线主表统一记录交换过程真相，不复制下游业务真相
- 所有表统一包含基础审计字段：`created_at`、`created_by`、`updated_at`、
  `updated_by`、`is_deleted`
- 主键统一采用平台字符串主键，便于跨表追踪
- 原始报文、投影报文、错误上下文统一保留摘要字段与对象存储引用，
  避免在主表无限膨胀大字段
- 历史交换记录的安全解释锚点统一由 `security_profile_version`、`certificate_version`、
  `verification_result` 承接；其中出站记录的 `verification_result` 表示发送前安全校验与
  对端同步安全接受结论的统一结果字段

### 4.2 `ih_inbound_message`

用途：记录外围系统入站事实的统一受理主表，是入站处理的交换真相源。

| 项 | 说明 |
| --- | --- |
| 关键主键 | `inbound_message_id` |
| 关键字段 | `source_system`、`message_type`、`external_request_id`、`idempotency_key`、`object_type`、`object_hint`、`ingest_status`、`processing_status`、`route_target`、`binding_status`、`mapping_version`、`model_version`、`security_profile_version`、`certificate_version`、`verification_result`、`profile_version`、`evidence_group_id`、`raw_payload_ref`、`normalized_payload_json`、`received_at`、`processed_at` |
| 关键索引 / 唯一约束 | `uk_inbound_idem(source_system, idempotency_key)`；`uk_inbound_external(source_system, external_request_id, message_type)`；`idx_inbound_status(ingest_status, processing_status, received_at)`；`idx_inbound_object(object_type, object_hint)` |
| 关联对象 | 合同主档、文档中心、流程引擎、`ih_integration_binding`、`ih_integration_job`、`ih_integration_audit_event` |

状态建议：

- `ingest_status`：`RECEIVED`、`REJECTED`、`ACCEPTED`
- `processing_status`：`PENDING`、`ROUTING`、`PROCESSING`、`SUCCEEDED`、`FAILED`、`WAIT_MANUAL`
- `binding_status`：`UNBOUND`、`BOUND`、`PARTIAL_BOUND`

### 4.3 `ih_outbound_dispatch`

用途：记录平台向外围系统发起的统一出站派发主表，是出站治理主入口。

| 项 | 说明 |
| --- | --- |
| 关键主键 | `dispatch_id` |
| 关键字段 | `target_system`、`dispatch_type`、`object_type`、`object_id`、`projection_version`、`mapping_version`、`model_version`、`security_profile_version`、`certificate_version`、`verification_result`、`profile_version`、`dispatch_status`、`callback_expected`、`target_request_ref`、`idempotency_key`、`evidence_group_id`、`dispatch_payload_ref`、`last_result_code`、`last_result_message`、`attempt_count`、`next_retry_at`、`last_attempt_at`、`completed_at` |
| 关键索引 / 唯一约束 | `uk_dispatch_idem(target_system, idempotency_key)`；`idx_dispatch_object(object_type, object_id, target_system)`；`idx_dispatch_status(dispatch_status, next_retry_at)`；`idx_dispatch_target(target_system, dispatch_type, created_at)` |
| 关联对象 | 合同主档、文档中心、流程引擎、通知中心、`ih_callback_receipt`、`ih_integration_job`、`ih_integration_audit_event` |

状态建议：

- `dispatch_status`：`CREATED`、`READY`、`DISPATCHING`、`SENT`、`ACKED`、`FAILED`、`CANCELLED`、`WAIT_CALLBACK`

### 4.4 `ih_callback_receipt`

用途：记录外部系统回调回执的统一承接主表，用于回调受理、去重、顺序控制与回写追踪。

| 项 | 说明 |
| --- | --- |
| 关键主键 | `callback_receipt_id` |
| 关键字段 | `source_system`、`receipt_type`、`external_receipt_id`、`linked_dispatch_id`、`linked_binding_id`、`idempotency_key`、`receipt_status`、`processing_status`、`mapping_version`、`model_version`、`security_profile_version`、`certificate_version`、`verification_result`、`profile_version`、`evidence_group_id`、`event_sequence`、`occurred_at`、`received_at`、`raw_payload_ref`、`normalized_payload_json`、`conflict_reason` |
| 关键索引 / 唯一约束 | `uk_callback_idem(source_system, idempotency_key)`；`uk_callback_external(source_system, external_receipt_id, receipt_type)`；`idx_callback_dispatch(linked_dispatch_id, occurred_at)`；`idx_callback_status(receipt_status, processing_status, received_at)` |
| 关联对象 | `ih_outbound_dispatch`、`ih_integration_binding`、合同主档、流程引擎、通知中心、`ih_integration_job` |

状态建议：

- `receipt_status`：`RECEIVED`、`REJECTED`、`ACCEPTED`
- `processing_status`：`PENDING`、`ORDER_CHECKING`、`PROCESSING`、`SUCCEEDED`、`FAILED`、`CONFLICT`、`WAIT_MANUAL`

### 4.5 `ih_integration_binding`

用途：记录平台交换对象与外部对象、实例、身份主线已确认主体引用、组织主线已确认组织引用之间的稳定绑定关系，
是跨系统交换对象绑定表，不作为身份或组织映射真相表。

| 项 | 说明 |
| --- | --- |
| 关键主键 | `binding_id` |
| 关键字段 | `system_name`、`binding_type`、`object_type`、`object_id`、`external_object_type`、`external_object_id`、`external_parent_id`、`binding_status`、`binding_role`、`confirmed_ref_source`、`first_bound_at`、`last_verified_at`、`last_inbound_message_id`、`last_dispatch_id` |
| 关键索引 / 唯一约束 | `uk_binding(system_name, object_type, object_id, external_object_type, external_object_id)`；`idx_binding_external(system_name, external_object_type, external_object_id)`；`idx_binding_object(object_type, object_id, binding_status)` |
| 关联对象 | 合同主档、文档中心、流程引擎、`identity-access` 已确认主体与组织引用、`ih_inbound_message`、`ih_outbound_dispatch`、`ih_callback_receipt` |

绑定类型建议：

- `CONTRACT_SOURCE`
- `DOCUMENT_REF`
- `WORKFLOW_INSTANCE`
- `IDENTITY_CONFIRMED_REF`
- `ORG_UNIT_CONFIRMED_REF`
- `MASTER_DATA_REF`

边界约束：

- `IDENTITY_CONFIRMED_REF` 只保存 `identity-access` 已确认的 `user_id`、`binding_id` 或 `protocol_exchange_id` 引用，不保存外部身份到平台主体的判定逻辑。
- `ORG_UNIT_CONFIRMED_REF` 只保存 `identity-access` 已确认的 `org_unit_id` 或组织同步承接引用，不保存第二套组织树或部门映射真相。
- 身份冲突、组织冲突、绑定预检查、人工冻结与会话准入均由 `identity-access` 持有正式结论；本表只把这些结论作为交换链路的引用锚点。

### 4.6 `ih_integration_job`

用途：记录集成主线的异步处理单元，是集成域任务视图主表；
与总平台 `platform_job` 建立一对一或一对多映射，不替代总平台任务中心。

| 项 | 说明 |
| --- | --- |
| 关键主键 | `job_id` |
| 关键字段 | `platform_job_id`、`job_type`、`job_status`、`resource_type`、`resource_id`、`job_round_no`、`source_system`、`target_system`、`priority`、`attempt_no`、`max_attempts`、`runner_code`、`next_run_at`、`last_error_code`、`last_error_message`、`manual_action_required`、`finished_at` |
| 关键索引 / 唯一约束 | `uk_job_attempt(job_type, resource_type, resource_id, job_round_no, attempt_no)`；`idx_job_pick(job_status, next_run_at, priority)`；`idx_job_system(source_system, target_system, job_status)`；`idx_job_platform(platform_job_id)` |
| 关联对象 | `ih_inbound_message`、`ih_outbound_dispatch`、`ih_callback_receipt`、总平台 `platform_job`、`ih_integration_audit_event` |

任务类型建议：

- `INBOUND_PROCESS`
- `OUTBOUND_DISPATCH`
- `CALLBACK_PROCESS`
- `DISPATCH_RETRY`
- `CALLBACK_REPLAY`
- `BINDING_VERIFY`
- `RECONCILIATION`
- `RECOVERY_REPAIR`

### 4.7 `ih_integration_audit_event`

用途：记录集成主线内部审计与运行审计明细，用于链路追踪、对账定位与恢复回放。
关键事件同步写总平台审计中心，本表保留集成域专有上下文。

| 项 | 说明 |
| --- | --- |
| 关键主键 | `audit_event_id` |
| 关键字段 | `trace_id`、`direction`、`resource_type`、`resource_id`、`action_type`、`result_status`、`system_name`、`object_type`、`object_id`、`operator_type`、`operator_id`、`mapping_version`、`model_version`、`security_profile_version`、`certificate_version`、`verification_result`、`profile_version`、`evidence_group_id`、`error_code`、`error_message`、`payload_snapshot_ref`、`occurred_at` |
| 关键索引 / 唯一约束 | `idx_audit_trace(trace_id, occurred_at)`；`idx_audit_resource(resource_type, resource_id, occurred_at)`；`idx_audit_object(object_type, object_id, occurred_at)`；`idx_audit_result(result_status, system_name, occurred_at)` |
| 关联对象 | 全部交换主表、合同主档、文档中心、流程引擎、通知中心、搜索、AI |

### 4.8 `ih_object_mapping`

用途：记录外部主数据值、码表值、枚举值到平台统一值的映射规则，
避免把映射逻辑硬编码在适配器中。

| 项 | 说明 |
| --- | --- |
| 关键主键 | `mapping_id` |
| 关键字段 | `system_name`、`mapping_scope`、`external_type`、`external_key`、`platform_type`、`platform_key`、`mapping_status`、`effective_from`、`effective_to`、`priority` |
| 关键索引 / 唯一约束 | `uk_mapping(system_name, mapping_scope, external_type, external_key)`；`idx_mapping_platform(platform_type, platform_key)` |
| 关联对象 | `ih_inbound_message`、`ih_outbound_dispatch`、`ih_integration_binding` |

### 4.9 `ih_endpoint_profile`

用途：记录每个外围系统在平台内的端点、鉴权、限流、回调与能力开关配置。
敏感凭证不明文落本表，只保存凭证引用。

| 项 | 说明 |
| --- | --- |
| 关键主键 | `endpoint_profile_id` |
| 关键字段 | `system_name`、`endpoint_type`、`endpoint_code`、`base_url`、`auth_mode`、`credential_ref`、`timeout_ms`、`retry_policy_code`、`callback_enabled`、`rate_limit_bucket`、`profile_status` |
| 关键索引 / 唯一约束 | `uk_endpoint(system_name, endpoint_type, endpoint_code)`；`idx_endpoint_status(system_name, profile_status)` |
| 关联对象 | `IntegrationAdapterRuntime`、`ih_outbound_dispatch`、`ih_callback_receipt` |

### 4.10 `ih_recovery_ticket`

用途：记录失败交换、补偿失败、顺序冲突、人工介入事件的恢复工单，
避免恢复动作只存在日志中。

| 项 | 说明 |
| --- | --- |
| 关键主键 | `recovery_ticket_id` |
| 关键字段 | `resource_type`、`resource_id`、`failure_stage`、`ticket_round_no`、`root_ticket_id`、`diff_id`、`ledger_entry_id`、`result_evidence_group_id`、`result_evidence_object_id`、`last_audit_ref_id`、`recovery_status`、`recovery_strategy`、`manual_owner_id`、`root_cause_code`、`root_cause_summary`、`last_retry_at`、`resolved_at` |
| 关键索引 / 唯一约束 | `uk_recovery_ticket(resource_type, resource_id, failure_stage, ticket_round_no)`；`idx_recovery_status(recovery_status, manual_owner_id)` |
| 关联对象 | `ih_inbound_message`、`ih_outbound_dispatch`、`ih_callback_receipt`、`ih_integration_job`、`ih_integration_audit_event`、对账差异、人工台账 |

`ih_recovery_ticket` 对证据引用的正式承接语义固定如下：

- `diff_id`：恢复工单当前处置轮次所针对的正式对账差异主键；直接从差异建单时为必填
- `ledger_entry_id`：恢复工单所承接的人工台账项主键；仅在由人工台账升级为恢复工单时填写
- `result_evidence_group_id`：恢复动作当前轮次最终回写的正式证据组引用；凡发生补发、重放、补证据或失败固化，必须回写到该字段，工单关闭前不得为空
- `result_evidence_object_id`：当前轮次直接作为恢复输入、补丁输入或最终结果锚点的证据对象引用；没有单对象锚点时可为空，但不得替代 `result_evidence_group_id`
- `last_audit_ref_id`：最近一次恢复执行、证据访问、导出、比对或关闭动作形成的正式审计引用；用于把工单执行与证据操作串到同一审计链路

## 5. 入站 / 出站 / 回调内部模型

### 5.1 入站内部模型

入站内部模型拆成五层：

1. `RawInbound`：保留原始头、原始体、签名摘要、来源 IP、接收时间。
2. `CanonicalEnvelope`：提取 `source_system`、`message_type`、幂等键、对象提示。
3. `NormalizedPayload`：完成码表映射、字段标准化、时间与金额格式统一。
4. `BusinessProjection`：生成面向合同主档、文档中心、流程引擎的内部投影。
5. `AcceptanceResult`：记录承接成功、拒绝、待人工确认、待补全绑定等结果。

入站处理原则：

- 先落 `ih_inbound_message`，再做业务承接
- 先校验来源、签名、时间戳、幂等，再做语义映射
- 外部事实不满足平台正式入库条件时，停留在待处理或待人工确认状态
- 需要附件落档时，先由文档中心受理，再把 `document_id` 回填入业务投影
- 需要合同承接时，通过内部合同承接服务提交，不允许适配器直写合同主表

### 5.2 出站内部模型

出站内部模型拆成四层：

1. `DispatchIntent`：由合同、文档、流程、通知等模块提出“需要对外发送什么”。
2. `DispatchPlan`：集成主线补全目标系统、端点、协议、重试、回调预期。
3. `DispatchPayload`：按目标系统生成稳定投影，不泄漏平台私有字段。
4. `DispatchResult`：记录同步响应、异步回调期待、失败原因与后续动作。

出站处理原则：

- 先创建 `ih_outbound_dispatch`，再做实际发送
- 对同一对象的不同目标系统，采用独立派发表，不共享发送状态
- 同步成功只代表外部已受理，不代表业务链路最终完成
- 需要回调闭环时，将 `dispatch_status` 转为 `WAIT_CALLBACK`
- 出站失败是否重试由派发类型、错误类型、幂等风险共同决定

### 5.3 回调内部模型

回调内部模型拆成五层：

1. `RawReceipt`：保留回调原始头、原始体、签名摘要、时间戳。
2. `ReceiptEnvelope`：提取 `source_system`、`receipt_type`、外部回执号、顺序号。
3. `ReceiptLinking`：关联 `dispatch_id`、`binding_id`、业务对象。
4. `ReceiptProjection`：转译成合同状态反馈、流程摘要反馈、消息送达结果等。
5. `ReceiptCommitResult`：记录是否已成功回写平台真相源。

回调处理原则：

- 所有回调统一经 `ih_callback_receipt` 承接
- 回调受理成功仅表示平台已接收，不表示业务状态已回写完成
- 回调若缺少绑定关系，可先入库后等待补全绑定，不直接丢弃
- 对顺序敏感事件按 `event_sequence + occurred_at` 检查是否倒序
- 回调回写失败必须进入任务与恢复链路，不允许静默结束

## 6. 统一适配层内部模型

### 6.1 适配层组成

统一适配层由以下内部组件组成：

- `AdapterRegistry`：按 `system_name + capability` 注册适配器
- `EndpointResolver`：解析 `ih_endpoint_profile`，得到端点与认证配置
- `CredentialResolver`：从配置中心或密钥引用中取受控凭证
- `SignatureVerifier`：完成入站 / 回调验签与重放窗口校验
- `PayloadNormalizer`：完成字段归一、码表转译、结构补齐
- `ProjectionAssembler`：组装面向下游真相源和外部系统的投影对象
- `ErrorTranslator`：把外部错误转为平台统一错误语义
- `AdapterGuard`：执行限流、熔断、超时、重试边界控制

### 6.2 统一适配接口

每个适配器内部至少实现以下能力面：

- `parseInbound()`：把原始入站转成 `CanonicalEnvelope`
- `buildOutbound()`：把平台投影转成目标系统请求体
- `verifyCallback()`：校验回调来源可信性
- `parseCallback()`：把回调转成 `ReceiptProjection`
- `classifyError()`：把外部错误归入平台错误分类
- `extractBindingRefs()`：从报文或回调中提取可建立绑定关系的键

### 6.3 适配层边界控制

- 适配层可以维护字段映射、码表映射、端点差异、鉴权差异
- 适配层不得维护合同正式状态机、文件版本链、流程节点流转规则
- 适配层不得维护 `protocol_exchange_id` 生命周期、绑定预检查状态、冲突冻结状态或人工处置历史
- 适配层输出必须是平台统一语义，不向业务模块暴露第三方 SDK 实体
- 同一系统的入站、出站、回调适配器可以分开实现，但共享注册、鉴权、错误转译基座

### 6.4 系统分组策略

- 企业微信适配器重点处理外部换票、验签、原始协议适配、组织同步、消息触达、回执；换取后的身份协议交换记录、预检查、冲突冻结、主体准入与平台会话签发由 `identity-access` 接管
- `OA` 适配器重点处理审批桥接、实例绑定、审批结果回调、摘要同步
- `CRM` / `SF` / `SRM` / `SAP` 适配器重点处理主数据与业务事实入站、
  合同或单据投影出站、对象映射校验
- 共性逻辑统一放在适配基座，系统差异仅保留在具体适配器实现层

## 7. 错误处理、幂等、补偿、恢复内部设计

### 7.1 错误分类

内部错误统一分为五类：

- `AUTH_ERROR`：签名、票据、凭证、时间戳、重放校验失败
- `PAYLOAD_ERROR`：结构不合法、字段缺失、码表无法映射
- `STATE_ERROR`：状态冲突、顺序冲突、重复回调、绑定缺失
- `DOWNSTREAM_ERROR`：合同主档、文档中心、流程引擎承接失败
- `EXTERNAL_ERROR`：外部系统超时、拒绝、限流、不可用

错误处理原则：

- 可立即拒绝的错误在受理阶段返回并记审计
- 可重试的错误进入 `ih_integration_job` 重试链路
- 可人工修复的错误进入 `ih_recovery_ticket`
- 同一错误必须同时具备机器可处理码和人工可理解摘要

### 7.2 幂等设计

幂等控制分三层：

1. 接口幂等：基于 `external_request_id`、`external_receipt_id`、
   `dispatch_id` 或显式 `Idempotency-Key`
2. 资源幂等：通过主表唯一约束保证同一交换语义不重复受理，不把同一资源的一生压成一条记录
3. 任务幂等：`ih_integration_job` 对同一资源同一任务类型同一 `job_round_no` 只保留一个活跃尝试；进入重试、重放、对账或恢复新轮次时必须新开 `job_round_no`

幂等处理规则：

- 相同幂等键且请求摘要一致，返回既有结果
- 相同幂等键但请求摘要不一致，标记 `IDEMPOTENCY_CONFLICT`
- 回调去重先看 `source_system + external_receipt_id + receipt_type`，
  再看摘要哈希，避免外部系统错误复用回执号时误吞新事件
- `uk_job_attempt` 只约束单资源单任务类型单轮次内的唯一尝试号，允许同一资源因补发、重放、对账或恢复形成多轮历史任务

### 7.3 补偿设计

补偿不是回滚外部系统，而是修复平台交换链路与平台真相承接结果。

补偿策略分层：

- 入站补偿：重新路由、重做映射、重试下游承接、补齐绑定
- 出站补偿：重建投影、重新发送、取消等待中的错误回调预期、人工终止
- 回调补偿：重新关联派发、重放回调、按顺序重算摘要、重新回写平台真相源
- 对账补偿：按绑定关系拉取外部摘要，与平台状态比对后生成恢复工单

### 7.4 恢复设计

恢复以 `ih_recovery_ticket` 为中心：

- 自动恢复：适用于短暂超时、锁冲突、偶发下游不可用
- 半自动恢复：平台建议恢复动作，管理员确认执行
- 人工恢复：绑定缺失、业务语义冲突、外部数据错误、顺序冲突

恢复动作建议：

- `RETRY_FROM_LAST_STEP`
- `REPLAY_CALLBACK`
- `REBUILD_PROJECTION`
- `RELINK_BINDING`
- `MARK_MANUAL_RESOLVED`
- `SKIP_WITH_AUDIT`

恢复建单规则：

- 同一 `resource_type + resource_id + failure_stage` 在不同恢复轮次必须创建新的 `ticket_round_no`，关闭后的旧工单不得复用为新一轮正式工单
- `root_ticket_id` 用于串联同一资源同一故障阶段的升级、回写和多轮处置历史，不改变每一轮工单独立留痕

### 7.5 顺序与一致性控制

- 对审批进展、消息送达状态等顺序敏感事件，优先使用 `event_sequence`
- 若外部系统无顺序号，退化为 `occurred_at + receipt_type + status_weight`
- 已完成终态后的旧回调默认进入冲突检查，不直接覆盖新状态
- 集成主线只保证交换链路一致性，不替代下游真相源内部事务规则

## 8. 与合同主档、文档中心、流程引擎的内部挂接设计

### 8.1 与合同主档的挂接

挂接方式：

- 入站合同事实通过 `ContractProjectionAssembler` 生成合同承接投影
- 合同主档返回 `contract_id`、承接结果、拒绝原因或待人工确认原因
- 出站时由合同主档提供面向目标系统的受控事实投影，不允许集成主线自行拼装第二份合同视图
- 合同来源、外部单据号、外部实例号通过 `ih_integration_binding` 统一治理

实现要求：

- 集成主线可缓存合同摘要，但缓存不是合同真相源
- 合同状态回写只能经合同应用服务，不直写合同表
- 外部状态若仅是参考事实，可写合同时间线或外部摘要，不直接覆盖主状态

### 8.2 与文档中心的挂接

挂接方式：

- 外部附件入站先创建文档受理请求，由文档中心落正式 `document_id`
- 出站文档由文档中心提供受控版本引用、文件下载令牌或传输流
- 签章结果稿、归档件、扫描件都先回到文档中心，再建立外部绑定关系

实现要求：

- 集成主线只保存 `document_id`、版本摘要、传输引用，不保存文件真相
- 文档中心是文件真相源；加密、解密、版本链、存储定位由文档中心控制
- 解密下载如需要对外发送，必须先经过文档中心权限校验与审计确认

### 8.3 与流程引擎的挂接

挂接方式：

- `OA` 桥接请求由流程引擎提供 `workflow_instance_id` 与流程摘要
- 集成主线生成 `dispatch_id` 与 `binding_id`，建立 `workflow_instance_id <-> oa_instance_id` 绑定
- `OA` 回调经集成主线转译为统一审批结果，再提交给流程引擎承接
- 当审批由平台流程引擎直接承接时，集成主线仅承担对外通知、对外摘要同步或补充回写

实现要求：

- 流程实例与节点状态仍由流程引擎持有
- 集成主线只维护外部审批摘要、桥接关系、回调记录和补偿状态
- 若 `OA` 回调与平台实例状态冲突，以流程引擎校验结果为准，并记录冲突审计

## 9. 与通知、审计、搜索、AI 的内部挂接设计

### 9.1 与通知的挂接

- 集成主线只产出通知事件，不直接决定最终通道模板细节
- 关键事件包括：入站失败、出站失败、回调冲突、恢复待处理、对账异常、人工介入完成
- 企业微信可以是通知通道之一，但通知中心才是通知治理中心

### 9.2 与审计的挂接

- 每次受理、发送、回调、补偿、恢复都写 `ih_integration_audit_event`
- 安全敏感与业务关键事件同步写总平台 `audit_event`
- 审计最小字段包括：谁发起、来自哪套系统、处理到哪一步、结果如何、为何失败、是否人工介入

### 9.3 与搜索的挂接

- 搜索只消费平台承接后的对象摘要与集成运行摘要
- 不直接索引外部原始报文为正式搜索主对象
- 集成主线负责在绑定变化、合同承接完成、文档入库完成、审批摘要更新后触发索引刷新请求

### 9.4 与 AI 的挂接

- AI 只能读取平台统一化后的集成摘要、异常语义、恢复建议，不直接消费第三方原始协议
- 集成主线提供可审计的 `trace_id`、失败摘要、对象关联关系，供 AI 做异常分析、对账辅助、风险提示
- AI 输出只能作为辅助建议，不直接驱动重试、跳过或状态覆盖

## 10. 并发控制、异步任务与运行观测

### 10.1 并发控制

- 入站并发：基于 `source_system + idempotency_key` 做唯一约束，避免重复受理
- 绑定并发：同一对象绑定更新时按 `object_type + object_id + system_name` 加行级锁
- 派发并发：同一对象对同一目标系统的活跃派发按业务规则限制并发个数
- 回调并发：同一 `dispatch_id` 的回调处理串行化，避免顺序竞争
- 恢复并发：同一资源仅允许一个活跃恢复工单执行

### 10.2 异步任务设计

异步任务分层：

- 受理后异步：大报文入站处理、批量主数据承接、附件入库
- 派发后异步：外部系统重试、等待回调、结果对账
- 回调后异步：审批摘要重算、索引刷新、通知分发
- 运维异步：绑定校验、死信扫描、积压清理、恢复重放

执行原则：

- `ih_integration_job` 记录集成视图，`platform_job` 负责统一调度
- 任务领取按 `job_status + next_run_at + priority` 选择
- 重试采用指数退避上限策略，但由不同任务类型配置不同上限
- 超过上限后进入 `WAIT_MANUAL` 或生成 `ih_recovery_ticket`
- 已完成的新一轮重放、对账或恢复必须创建新的 `job_round_no`，历史轮次保持可追溯，不得回写覆盖旧任务

### 10.3 运行观测

运行观测分三层：

- 指标：入站成功率、出站成功率、回调处理成功率、重试次数、积压量、人工介入量、对账差异量、各系统平均耗时
- 日志：请求摘要日志、错误分类日志、重试日志、恢复动作日志
- 审计：可追踪到具体资源、对象、操作者和外部系统

关键观测口径：

- 必须可按 `trace_id` 串起入站、出站、回调、任务、审计、恢复工单
- 必须可按 `contract_id`、`document_id`、`workflow_instance_id` 反查所有集成动作
- 必须区分“外部系统未响应”“平台承接失败”“人工暂挂”三类不同失败语义

### 10.4 运维告警建议

- 单系统连续失败率超过阈值
- 回调积压超过阈值
- 死信或待人工恢复工单持续增长
- 与 `OA`、企业微信、`SAP` 等关键系统的绑定校验异常持续存在
- 审批桥接摘要与流程引擎状态长期不一致

## 11. 继续下沉到后续专项设计或实现的内容

以下内容继续下沉到后续专项设计、字段映射定稿或实现层：

- 各外围系统的具体字段映射表、码表字典与值转换规则： [external-field-mapping-design.md](./special-designs/external-field-mapping-design.md)
- 具体签名算法、证书轮换、密钥托管、票据换取细节： [signing-and-ticket-security-design.md](./special-designs/signing-and-ticket-security-design.md)
- 适配器运行时抽象、统一生命周期、调用上下文、方向模型与恢复边界： [adapter-runtime-design.md](./special-designs/adapter-runtime-design.md)
- 各任务类型的精确重试参数、退避曲线、超时值、告警阈值： [retry-timeout-and-alerting-design.md](./special-designs/retry-timeout-and-alerting-design.md)
- 对账任务治理、人工台账、原始报文证据关系，以及原始报文命名、分桶、保留、检索与清理归档边界： [reconciliation-and-raw-message-governance-design.md](./special-designs/reconciliation-and-raw-message-governance-design.md)
- 管理端恢复操作界面、联调开关、灰度开关的实现细节

## 12. 一致性检查结论

- 已覆盖外围系统集成主线内部实现层的核心设计，包括模块拆分、主表、内部模型、适配层、幂等补偿、挂接、并发与观测
- 未复述需求范围、架构总览、对外 API 资源清单与实施排期，未越界写成 `Requirement`、`Architecture`、`API Design` 或 `Implementation Plan`
- 与 [`integration-hub Architecture Design`](./architecture-design.md) 保持一致：
  业务模块不得私接外部入口、`OA` 为默认主审批路径、企业微信为正式移动承载端之一、
  `CRM` / `SF` / `SRM` / `SAP` 不直接成为平台真相源
- 与 [`integration-hub API Design`](./api-design.md) 保持一致：
  本文只下沉内部模型与实现边界，不重复资源清单与外部契约

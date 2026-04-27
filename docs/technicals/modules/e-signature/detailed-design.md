# 电子签章子模块 Detailed Design

## 1. 文档说明

本文档是 `CMP` 电子签章子模块的第一份正式 `Detailed Design`。

### 1.1 输入

- 上游需求基线：[`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- 总平台架构基线：[`Architecture Design`](../../architecture-design.md)
- 总平台接口基线：[`API Design`](../../api-design.md)
- 总平台共享内部基线：[`Detailed Design`](../../detailed-design.md)
- 子模块架构基线：[`电子签章子模块 Architecture Design`](./architecture-design.md)
- 子模块接口基线：[`电子签章子模块 API Design`](./api-design.md)

### 1.2 输出

- 本文：[`Detailed Design`](./detailed-design.md)
- 供后续表级 DDL、模块实现、专项设计与联调约束使用的内部设计基线

### 1.3 阅读边界

本文只写电子签章子模块的内部实现层设计，重点回答：

- 电子签章内部模块如何拆分
- 签章申请、会话、结果、摘要、纸质备案如何内部建模
- 待签输入稿、签章结果稿、验签产物引用如何与文档中心挂接
- 签章准入、签章回写、验签结果回写如何在内部落地
- 幂等、锁、并发、补偿、恢复、审计、指标如何控制

本文不承担以下内容：

- 不复述需求范围、一期功能清单、验收口径
- 不重写总平台架构总览、子模块定位或对外资源清单
- 不写 API 路径、请求样例、错误码全集或回调报文样板
- 不写签章坐标算法、证书介质存储、底层渲染细节
- 不写实施排期、联调顺序、负责人拆分

## 2. 设计目标与约束落点

### 2.1 设计目标

- 让电子签章成为平台内正式子模块，而不是挂在合同外侧的影子台账
- 让签章动作严格建立在审批通过后的正式合同主链上
- 让待签输入稿与签章结果稿都回到文档中心统一版本治理
- 让签章结果必须回写合同主档摘要与时间线，而不是只停留在模块内部
- 让纸质备案与电子签章共用同一合同挂接语义，但不混淆成同一执行链
- 让签章失败、验签失败、回写失败都可审计、可补偿、可恢复

### 2.2 约束落点

- 电子签章是平台内正式子模块：内部持有签章专业状态、签署编排、验签结果与回写控制
- 合同主档是业务真相源：签章模块只通过 `contract_id` 挂接，不能反向生成合同一级事实
- 文档中心是文件真相源：签章模块只引用 `document_asset_id` / `document_version_id`，不保存第二套文件主档
- 签章动作建立在审批通过后的正式合同主链上：签章准入必须消费平台统一审批摘要，而不是直接消费外部系统原始状态
- 签章结果必须回写合同主档摘要与时间线：合同侧只保留稳定摘要，不承接签章过程全量状态树
- 不把内部过程状态整体塞进合同主档接口：申请态、会话态、参与方临时态、回调过程态都留在本模块内部治理

## 3. 模块内部拆分

内部实现按八个组件拆分，统一围绕 `contract_id`、`signature_request_id`、
`signature_session_id`、`signature_result_id` 运转。

### 3.1 `request-admission`

- 负责签章申请受理、准入校验、业务幂等判定
- 读取合同主档摘要、审批摘要、文档中心版本摘要，形成申请快照
- 是唯一允许创建 `es_signature_request` 的入口

### 3.2 `document-binding-registry`

- 负责登记待签输入稿、签章结果稿、验签产物、纸质扫描件引用
- 统一管理“本模块引用了文档中心哪一个文件版本”
- 不保存文件二进制，不接管文档版本链

### 3.3 `signer-orchestrator`

- 负责签署参与方展开、顺序控制、会话推进、完成判定
- 负责把组织绑定快照固化为签署任务责任人快照
- 只输出模块内部参与方语义，不改写组织主数据

### 3.4 `session-runtime`

- 负责创建会话、会话续转、会话超时、会话关闭
- 承接回调归一后的运行时状态推进
- 是唯一允许推进 `es_signature_session` 主状态的组件

### 3.5 `result-registry`

- 负责登记签章结果、验签结果、失败摘要、回写进度
- 管理“签章已完成但回写未闭环”的中间状态
- 输出稳定 `SignatureResult` 与 `SignatureSummary` 读模型

### 3.6 `writeback-coordinator`

- 负责向文档中心回写签章结果稿与验签产物引用
- 负责向合同主档回写摘要、稳定 `signature_status` 与时间线事件
- 负责控制回写顺序、幂等与部分失败补偿

### 3.7 `paper-record-service`

- 负责纸质备案登记、扫描件绑定、线下签署事实回收
- 让纸质备案进入签章摘要统一出口，但不走电子签署会话编排
- 负责纸质备案与电子签章互斥 / 并存规则判断

### 3.8 `signature-control-plane`

- 负责幂等键、锁、异步作业、恢复任务、告警指标与审计路由
- 只提供运行时治理能力，不拥有业务真相

## 4. 核心物理表设计

### 4.1 建表原则

- 核心真相表落 `MySQL`，不依赖缓存保存正式状态
- 主键统一使用平台字符串主键
- 全部核心表默认包含 `created_at`、`created_by`、`updated_at`、
  `updated_by`、`is_deleted`、`row_version`
- 所有跨系统输入都要有正式唯一约束或正式去重键，不能只靠 `Redis`
- 摘要表允许重建，但申请、会话、结果、备案、回调、审计主表不可丢失

### 4.2 `es_signature_request`

用途：签章申请主表，承接一次基于正式合同主链发起的签章业务入口。

- 关键主键：`signature_request_id`
- 关键字段：
  - `contract_id`
  - `request_no`：模块内稳定申请编号
  - `request_status`：申请主状态
  - `signature_mode`：`ELECTRONIC` / `PAPER_RECORD`
  - `signature_scene`：如正式签署、补签、重签
  - `approval_mode`
  - `approval_summary_ref`
  - `contract_snapshot_version`
  - `source_document_asset_id`
  - `source_document_version_id`
  - `seal_scheme_id`
  - `sign_order_mode`
  - `request_fingerprint`：由合同、输入稿版本、签署策略摘要计算
  - `idempotency_key`
  - `admission_status`：准入判断结果
  - `admission_reason`
  - `current_session_id`
  - `latest_result_id`
- 关键索引 / 唯一约束：
  - `uk_request_no(request_no)`
  - `uk_contract_fingerprint(contract_id, request_fingerprint)`
  - `uk_idempotency(idempotency_key)`
  - `idx_contract_status(contract_id, request_status, signature_mode)`
  - `idx_current_session(current_session_id)`
- 关联对象：`Contract`、`es_signature_session`、`es_signature_result`、
  `es_signature_summary`、`es_request_document_binding`

设计说明：

- 申请表保存发起时业务快照，避免后续合同或审批摘要变化后无法解释准入结果
- 同一合同对同一正式输入稿重复发起时，以 `request_fingerprint` 控制稳定受理或稳定冲突
- 电子签章与纸质备案都通过本表挂到同一 `contract_id`，但运行链不同

### 4.3 `es_signature_session`

用途：签署会话主表，表达一次可推进、可超时、可结束的签署执行窗口。

- 关键主键：`signature_session_id`
- 关键字段：
  - `signature_request_id`
  - `contract_id`
  - `session_no`
  - `session_status`
  - `session_round`
  - `sign_order_mode`
  - `current_sign_step`
  - `pending_signer_count`
  - `completed_signer_count`
  - `started_at`
  - `expires_at`
  - `completed_at`
  - `close_reason`
  - `runtime_checkpoint`：运行时摘要，不保存底层引擎细节
  - `last_callback_event_id`
- 关键索引 / 唯一约束：
  - `uk_request_round(signature_request_id, session_round)`
  - `uk_request_active(signature_request_id, is_current_session)`
  - `idx_contract_status(contract_id, session_status)`
  - `idx_expire(session_status, expires_at)`
- 关联对象：`es_signature_request`、`es_signer_assignment`、
  `es_signature_result`、`es_signature_callback_event`

设计说明：

- 同一申请在任一时刻最多只有一个激活会话
- 会话是运行时真相，不把更细步骤拆成对外资源，但内部可通过参与方表推进
- 会话续轮通过 `session_round` 追加，不覆写旧会话记录

### 4.4 `es_signer_assignment`

用途：签署参与方与签署顺位表，固化谁在何时、以何角色参与哪一轮签署。

- 关键主键：`signer_assignment_id`
- 关键字段：
  - `signature_request_id`
  - `signature_session_id`
  - `contract_id`
  - `signer_type`：`USER`、`ORG_UNIT`、`EXTERNAL_PARTY`、`SYSTEM_PROXY`
  - `signer_id`
  - `signer_name_snapshot`
  - `signer_org_snapshot`
  - `assignment_role`：`APPLICANT`、`PRIMARY_SIGNER`、`COUNTERSIGNER`、`WITNESS`
  - `sign_sequence_no`
  - `assignment_status`
  - `assignment_source`：配置、组织规则解析、人工补录
  - `signed_at`
  - `action_token_ref`
  - `failure_reason`
- 关键索引 / 唯一约束：
  - `uk_session_sequence(signature_session_id, sign_sequence_no, signer_id)`
  - `idx_request_status(signature_request_id, assignment_status)`
  - `idx_signer_lookup(signer_type, signer_id, assignment_status)`
- 关联对象：`es_signature_session`、`user_account` / `org_unit`

设计说明：

- 参与方表保存执行时快照，避免组织变更影响已发起签章的责任解释
- 同一参与方在不同轮次允许重复出现，但在同一轮同一顺位不允许重复

### 4.5 `es_signature_result`

用途：签章结果主表，承接一次签署执行后的稳定结果、验签结果与回写进度。

- 关键主键：`signature_result_id`
- 关键字段：
  - `signature_request_id`
  - `signature_session_id`
  - `contract_id`
  - `result_status`
  - `verification_status`
  - `result_code`
  - `result_message`
  - `signed_document_asset_id`
  - `signed_document_version_id`
  - `verification_summary_json`
  - `document_writeback_status`
  - `contract_writeback_status`
  - `archive_projection_status`
  - `search_projection_status`
  - `ai_projection_status`
  - `completed_at`
  - `writeback_completed_at`
  - `external_result_ref`
- 关键索引 / 唯一约束：
  - `uk_session_result(signature_session_id, result_status, completed_at)`
  - `uk_request_current(signature_request_id, is_current_result)`
  - `idx_contract_completed(contract_id, result_status, completed_at)`
  - `idx_writeback(document_writeback_status, contract_writeback_status)`
- 关联对象：`es_signature_request`、`es_signature_session`、
  `es_request_document_binding`、`es_signature_summary`

设计说明：

- 结果表持有签章专业结论，不直接替代合同主档或文档中心真相
- 文档中心回写成功后再记录结果稿引用，避免模块长期持有游离结果文件
- 验签结果以摘要保存，底层证书链或算法细节不进入本层文档

### 4.6 `es_signature_summary`

用途：合同级签章稳定摘要表，供合同详情、台账、归档、搜索、AI 统一消费。

- 关键主键：`summary_id`
- 关键字段：
  - `contract_id`
  - `signature_status`
  - `latest_signature_request_id`
  - `latest_signature_result_id`
  - `latest_signed_document_asset_id`
  - `latest_signed_document_version_id`
  - `signature_mode`
  - `signed_at`
  - `verification_status`
  - `paper_record_id`
  - `display_text`
  - `timeline_sync_status`
  - `contract_summary_sync_status`
  - `last_rebuild_at`
- 关键索引 / 唯一约束：
  - `uk_contract(contract_id)`
  - `idx_signature_status(signature_status, signed_at)`
  - `idx_result(latest_signature_result_id)`
- 关联对象：`Contract`、`es_signature_request`、`es_signature_result`、
  `es_paper_record`

设计说明：

- 摘要表是稳定读模型，不承担完整过程真相
- 摘要必须可从申请、结果、备案、文档绑定重新构建
- 合同主档只消费本表输出的稳定业务摘要，不直接消费申请 / 会话细节

### 4.7 `es_paper_record`

用途：纸质备案主表，登记线下签署完成后的纸质合同备案事实。

- 关键主键：`paper_record_id`
- 关键字段：
  - `contract_id`
  - `signature_request_id`
  - `paper_record_type`
  - `record_status`
  - `recorded_sign_date`
  - `recorded_signer_snapshot_json`
  - `paper_document_asset_id`
  - `paper_document_version_id`
  - `verification_note`
  - `record_note`
  - `confirmed_by`
  - `confirmed_at`
- 关键索引 / 唯一约束：
  - `uk_contract_record(contract_id, paper_document_version_id)`
  - `idx_contract_status(contract_id, record_status, recorded_sign_date)`
  - `idx_request(signature_request_id)`
- 关联对象：`Contract`、`es_signature_request`、`es_signature_summary`、
  `es_request_document_binding`

设计说明：

- 纸质备案只表达线下签署事实登记，不扩展成纸质审批或归档主记录
- 纸质扫描件仍必须引用文档中心正式版本
- 纸质备案可更新签章摘要，但不创建电子签章会话

### 4.8 `es_signature_callback_event`

用途：签章回调事件表，记录外部或内部执行回执、验签回执与重试处理痕迹。

- 关键主键：`callback_event_id`
- 关键字段：
  - `signature_request_id`
  - `signature_session_id`
  - `contract_id`
  - `callback_source`
  - `external_event_id`
  - `event_type`
  - `event_status`
  - `signature_valid`
  - `replay_check_status`
  - `payload_checksum`
  - `payload_ref`
  - `received_at`
  - `processed_at`
  - `processing_result_code`
  - `processing_result_message`
- 关键索引 / 唯一约束：
  - `uk_source_event(callback_source, external_event_id)`
  - `uk_payload_checksum(callback_source, payload_checksum)`
  - `idx_request_received(signature_request_id, received_at)`
  - `idx_processing(event_status, processed_at)`
- 关联对象：`es_signature_session`、`es_signature_result`、`integration_exchange`

设计说明：

- 回调事件先落库，再推进业务状态，避免“处理成功但无原始事件”
- 乱序或重复回调不直接覆盖会话 / 结果，需要经状态机归一判断

### 4.9 `es_signature_audit_event`

用途：签章域高等级审计表，记录发起、准入拒绝、签署动作、验签、回写、恢复等关键事件。

- 关键主键：`signature_audit_event_id`
- 关键字段：
  - `contract_id`
  - `signature_request_id`
  - `signature_session_id`
  - `signature_result_id`
  - `paper_record_id`
  - `audit_action_type`
  - `audit_level`
  - `actor_type`
  - `actor_id`
  - `object_type`
  - `object_id`
  - `before_snapshot_ref`
  - `after_snapshot_ref`
  - `audit_result`
  - `trace_id`
  - `occurred_at`
- 关键索引 / 唯一约束：
  - `idx_contract_time(contract_id, occurred_at)`
  - `idx_request_action(signature_request_id, audit_action_type, occurred_at)`
  - `idx_actor(actor_type, actor_id, occurred_at)`
- 关联对象：`audit_event`、`es_signature_request`、`es_signature_result`

设计说明：

- 本表保留签章域专业审计，平台级审计中心再汇总关键摘要
- 需要支持按合同、申请、参与方、回写链路追踪完整责任闭环

### 4.10 `es_request_document_binding`

用途：签章与文档中心绑定表，统一表达输入稿、结果稿、验签产物、纸质扫描件等引用关系。

- 关键主键：`binding_id`
- 关键字段：
  - `contract_id`
  - `signature_request_id`
  - `signature_result_id`
  - `paper_record_id`
  - `document_asset_id`
  - `document_version_id`
  - `binding_role`：`SOURCE_MAIN`、`SOURCE_ATTACHMENT`、`SIGNED_MAIN`、`VERIFICATION_ARTIFACT`、`PAPER_SCAN`
  - `binding_status`
  - `is_current`
  - `bound_at`
  - `unbound_at`
- 关键索引 / 唯一约束：
  - `uk_request_role(signature_request_id, binding_role, document_version_id)`
  - `uk_result_role(signature_result_id, binding_role, document_version_id)`
  - `idx_contract_role(contract_id, binding_role, is_current)`
  - `idx_document_ref(document_asset_id, document_version_id)`
- 关联对象：`document_asset` / `dc_document_asset`、`dc_document_version`、
  `es_signature_request`、`es_signature_result`、`es_paper_record`

设计说明：

- 该表把“签章模块引用了哪个文档版本”显式化，避免把多个文档引用散落在申请表和结果表
- 输入稿与结果稿都只保存引用，不保存副本

## 5. 签章申请 / 会话 / 结果 / 摘要内部模型

### 5.1 `SignatureRequest` 内部模型

`SignatureRequest` 是签章链路的聚合根，负责固化准入时点的业务上下文。

内部最小语义包括：

- 合同上下文：`contract_id`、合同状态快照、审批摘要引用
- 输入稿上下文：主待签稿、补充附件、输入版本摘要
- 签署策略：签章模式、顺序、印章方案、参与方来源
- 准入结论：允许 / 拒绝 / 待人工处理
- 派生引用：当前会话、最新结果、摘要重建锚点

建议状态收口：

- `PENDING_ADMISSION`：已受理，准入校验中
- `ADMISSION_REJECTED`：准入失败，不进入签章会话
- `ADMITTED`：准入通过，允许建立会话
- `IN_PROGRESS`：已有活动会话或结果处理中
- `COMPLETED`：签章闭环完成
- `FAILED`：自动恢复后仍未完成
- `CANCELLED`：被业务侧撤销或被后续版本替换

状态推进原则：

- 只有 `request-admission` 和 `writeback-coordinator` 可以改写申请主状态
- 申请状态不直接映射到合同主档，只通过摘要输出稳定业务语义

### 5.2 `SignatureSession` 内部模型

`SignatureSession` 是一次运行时签署执行窗口，用于表达当前轮次由谁签、进行到哪一步、
是否到期或终止。

内部最小语义包括：

- 所属申请与合同
- 当前轮次、签署顺序与剩余参与方数量
- 当前活动参与方列表与顺位
- 超时边界、关闭原因、最近一次回调事件

建议状态收口：

- `CREATED`
- `OPEN`
- `PARTIALLY_SIGNED`
- `COMPLETED`
- `EXPIRED`
- `ABORTED`
- `FAILED`

状态推进原则：

- 同一申请只允许一个 `OPEN` / `PARTIALLY_SIGNED` 会话
- 会话状态推进必须同时更新参与方完成计数，避免仅靠事件推断当前进度

### 5.3 `SignatureResult` 内部模型

`SignatureResult` 是一次签章执行后的稳定专业结论，用于承接“签出来了什么、验出来了什么、
回写闭环做到哪一步”。

内部最小语义包括：

- 会话执行结论
- 验签结论与失败摘要
- 结果稿引用与验签产物引用
- 文档中心回写状态
- 合同主档摘要 / 时间线回写状态
- 周边投影状态

建议状态收口：

- `RESULT_PENDING`
- `SIGNED_PENDING_VERIFY`
- `SIGNED_VERIFIED`
- `VERIFY_FAILED`
- `WRITEBACK_PARTIAL`
- `WRITEBACK_COMPLETED`
- `FAILED`

其中：

- `verification_status` 建议独立收口为 `PENDING`、`PASSED`、`FAILED`、`WARNING`
- `document_writeback_status` 与 `contract_writeback_status` 独立记录，避免一个字段掩盖部分失败

### 5.4 `SignatureSummary` 内部模型

`SignatureSummary` 是对外稳定读模型，不承接过程细节，只承接业务需要消费的稳定结果。

最小输出语义包括：

- `contract_id`
- 稳定 `signature_status`
- 最新申请、最新结果、最新结果稿引用
- 最新签署时间
- 最新验签结论
- 纸质备案是否生效
- 用于合同详情 / 台账的展示文案

摘要生成原则：

- 合同主档、归档、搜索、AI 只消费摘要，不消费申请 / 会话内部态
- 摘要可重建，但每次重建必须有明确的结果优先级规则：
  `WRITEBACK_COMPLETED` 的电子签结果优先于未完成结果，纸质备案只在无有效电子签结果时占主显示位，或按合同策略显式覆盖

### 5.5 文档输入稿与结果稿引用模型

本模块不拥有文件真相，所有文件相关状态都通过 `es_request_document_binding`
表达引用关系。

绑定角色约束如下：

- `SOURCE_MAIN`：正式待签主稿，只允许引用审批通过后的正式版本
- `SOURCE_ATTACHMENT`：参与签章展示但不作为结果主稿的补充附件
- `SIGNED_MAIN`：签章结果主稿，由文档中心回写成功后建立
- `VERIFICATION_ARTIFACT`：验签产物、回执摘要或证明文件引用
- `PAPER_SCAN`：纸质备案扫描件引用

核心原则：

- 输入稿版本一旦被受理为签章输入稿，就以版本号冻结，不随文档中心最新版本变化自动漂移
- 结果稿只有在文档中心正式建链成功后，才切换为当前有效结果引用

## 6. 与合同主档、文档中心、流程引擎的内部挂接设计

### 6.1 与合同主档的挂接

合同主档是业务真相源，电子签章模块只围绕其挂接。

内部挂接方式如下：

- 受理阶段读取合同稳定摘要：`contract_id`、`contract_status`、`signature_status`、
  主文档引用、当前业务版本
- 签章完成后只回写稳定摘要：`signature_status`、`signed_at`、`verification_status`、
  `latest_signature_result_id`、展示文案
- 时间线回写以事件方式登记：至少覆盖签章申请受理、签章完成、验签完成、回写失败、纸质备案确认
- 不把 `request_status`、`session_status`、`assignment_status` 整体写回合同主档

合同回写顺序：

1. 结果表确认已形成稳定结果
2. 若结果稿需要进入文档中心，则先完成文档中心回写
3. 再回写合同主档摘要与时间线
4. 最后刷新 `es_signature_summary`

### 6.2 与文档中心的挂接

文档中心是文件真相源，电子签章只消费与回收文件引用。

内部挂接方式如下：

- 准入阶段读取输入稿资产与版本摘要，校验文档归属、文档角色、版本有效性、加密可读状态
- 签章过程中不持久化文件副本，只保存文档引用与运行时句柄引用
- 结果形成后由 `writeback-coordinator` 调用文档中心建立 `SIGNED_MAIN` 或 `VERIFICATION_ARTIFACT` 绑定
- 纸质备案只接受文档中心已有 `PAPER_SCAN` 引用，不接受游离上传文件直接入签章表

文档回写控制原则：

- 结果稿版本生成失败时，不改写合同主档为最终已签，只将结果停留在 `WRITEBACK_PARTIAL`
- 文档中心回写成功后才允许生成稳定结果稿引用

### 6.3 与流程引擎的挂接

签章准入必须消费平台承接后的审批通过事实，而不是外部审批原始过程态。

内部挂接方式如下：

- 读取审批摘要或等价审批事实引用：`summary_status`、`finished_at`、
  `approval_mode`、`source_system_instance_id`
- 对审批被撤回、终止、驳回、待确认的合同直接拒绝签章准入
- 在申请表保存 `approval_summary_ref` 与审批快照摘要，作为事后审计依据
- 签章完成后可以回写流程摘要引用或后置节点触发事实，但不直接改写流程实例真相

### 6.4 与归档、搜索、AI 的挂接

- 归档只消费 `es_signature_summary` 与文档中心中的结果稿引用，不读取会话过程表
- 搜索只消费稳定摘要字段，如 `signature_status`、`signed_at`、`verification_status`
- AI 只消费摘要和受控结果视图，不读取参与方临时态、回调原文或证书细节

## 7. 纸质备案内部模型

纸质备案属于签署链路的辅助形态，不替代电子签章主资源。

内部模型由三部分组成：

- `es_signature_request` 中一条 `signature_mode = PAPER_RECORD` 的申请记录
- `es_paper_record` 中的备案事实与扫描件绑定
- `es_signature_summary` 中的稳定摘要投影

建议状态收口：

- `PENDING_REVIEW`：已登记，待核验
- `CONFIRMED`：纸质备案确认成立
- `REJECTED`：材料不合法或与合同主链不一致
- `VOIDED`：被新备案或电子正式结果替代

处理原则：

- 纸质备案不创建 `SignatureSession`
- 纸质备案仍要做合同、审批、文档归属校验
- 纸质备案确认后，可以把合同摘要更新为纸质签署已登记，但仍只写稳定摘要，不写内部审核过程

## 8. 幂等、锁与并发控制

### 8.1 幂等键

- API 入口沿用 `Idempotency-Key`
- 申请受理同时依赖 `idempotency_key` 和 `contract_id + request_fingerprint` 双重控制
- 回调事件依赖 `callback_source + external_event_id` 与 `payload_checksum` 双重去重
- 合同回写依赖 `contract_id + signature_request_id + signature_result_id`
- 文档中心回写依赖 `signature_result_id + binding_role`

### 8.2 锁粒度

- `contract:{contract_id}:signature-admission`：控制同一合同同一时点只进入一个正式准入判定
- `request:{signature_request_id}:session`：控制会话创建 / 续轮互斥
- `session:{signature_session_id}:advance`：控制回调与超时任务不并发推进同一会话
- `result:{signature_result_id}:writeback`：控制结果回写顺序与防重复
- `paper:{paper_record_id}:confirm`：控制纸质备案确认动作互斥

### 8.3 数据库并发兜底

- 所有主表使用 `row_version` 做乐观并发控制
- 关键状态迁移使用条件更新，例如“只有当前为 `ADMITTED` 才能进入 `IN_PROGRESS`”
- 锁失效后仍依赖唯一约束和条件更新兜底，不能只靠 `Redis` 锁判断成功

### 8.4 典型冲突处理

- 同一合同在旧输入稿上已存在活动申请，新输入稿重新发起时：默认拒绝或要求先取消旧申请
- 回调先到、会话尚未建成：事件落 `es_signature_callback_event`，由恢复任务补处理，不直接丢弃
- 结果已回写合同但文档中心未回写成功：保持 `WRITEBACK_PARTIAL`，禁止把合同视为闭环完成

## 9. 异步任务、补偿与恢复

### 9.1 任务类型

本模块复用总平台 `platform_job`，建议定义以下任务类型：

- `ES_SESSION_OPEN`
- `ES_SESSION_EXPIRE_CHECK`
- `ES_RESULT_VERIFY`
- `ES_DOCUMENT_WRITEBACK`
- `ES_CONTRACT_WRITEBACK`
- `ES_SUMMARY_REBUILD`
- `ES_CALLBACK_RETRY`
- `ES_PAPER_RECORD_RECHECK`

### 9.2 异步边界

- 申请受理与准入判定可同步完成
- 会话建立、验签整理、结果稿回写、合同回写、摘要重建按异步方式执行
- 所有异步任务正式状态都回落到 `platform_job`，签章表只保留本域需要的进度摘要

### 9.3 补偿规则

- 会话创建失败：申请回退到 `ADMITTED`，允许重新拉起会话
- 验签失败：结果进入 `VERIFY_FAILED`，合同侧只回写失败时间线，不回写最终已签摘要
- 文档中心回写失败：结果进入 `WRITEBACK_PARTIAL`，持续重试或转人工
- 合同回写失败：文档中心结果稿保留，结果标记合同回写待补偿，避免重复生成结果稿
- 摘要重建失败：不影响申请 / 结果真相，但阻断搜索、归档、AI 新投影，需优先修复

### 9.4 恢复策略

- 服务重启后，以 `es_signature_request`、`es_signature_session`、
  `es_signature_result`、`platform_job` 恢复执行上下文
- `es_signature_summary` 可全量重建，不作为唯一恢复依据
- 未处理的回调事件以 `es_signature_callback_event` 重放，不依赖进程内内存
- 恢复顺序优先级：结果回写补偿 > 会话超时处理 > 摘要重建 > 周边投影刷新

### 9.5 人工介入边界

以下场景进入人工介入池，而不是无限自动重试：

- 同一合同出现互相冲突的多个有效签章结果
- 验签连续失败且与回调结果不一致
- 文档中心已建结果稿，但合同主档长期无法回写
- 纸质备案材料与合同主链、审批快照、文档归属不一致

## 10. 审计、日志、指标与恢复边界

### 10.1 审计与日志

必须记录的签章域事件包括：

- 签章申请受理、准入通过、准入拒绝
- 会话创建、续轮、超时、终止、关闭
- 参与方签署动作、失败动作、人工补录动作
- 验签通过、验签失败、验签警告
- 文档中心回写成功 / 失败
- 合同主档摘要回写成功 / 失败
- 纸质备案登记、核验、作废
- 补偿执行、恢复重放、人工介入结论

日志分层建议：

- 应用日志：运行异常、任务失败、代码错误
- 审计日志：签章关键动作、权限结果、人工处置
- 交换日志：回调验签、重放、防重与结果回执

### 10.2 指标与告警

核心指标：

- 签章申请成功率、准入拒绝率、平均准入耗时
- 会话打开率、签署完成率、会话超时率
- 验签失败率、回调重复率、回调乱序率
- 文档中心回写失败率、合同回写失败率、摘要重建耗时
- 人工介入量、补偿成功率、恢复任务积压量

核心告警：

- 连续出现 `WRITEBACK_PARTIAL`
- 验签失败率短时显著升高
- 回调验签失败或重放校验失败激增
- 同一合同出现多个有效结果竞争
- 摘要长时间未同步导致合同侧与签章侧不一致

### 10.3 恢复边界

必须保留并可恢复的数据：

- 申请、会话、参与方、结果、纸质备案、回调事件、审计事件
- 与文档中心的结果稿 / 输入稿绑定关系
- 回写状态、补偿状态、人工介入结论

允许重建的数据：

- 签章摘要表
- 搜索投影、归档消费投影、AI 摘要投影
- 运行时锁、幂等缓存、短期会话快照

恢复边界原则：

- 不允许仅凭缓存认定签章完成
- 不允许仅凭合同主档摘要反推完整签章过程真相
- 恢复后如发现文档中心引用与结果表不一致，以文档中心已建立的正式版本链和签章结果表联合对账，不以单方数据直接覆盖另一方

## 11. 继续下沉到后续专项设计或实现的内容

以下内容应继续下沉，但不在本文展开：

- 印章资源、印章样式、授权矩阵与证书介质的专项设计： [seal-resource-and-certificate-design.md](special-designs/seal-resource-and-certificate-design.md)
- 签章坐标与渲染专项设计： [signature-coordinate-and-rendering-design.md](special-designs/signature-coordinate-and-rendering-design.md)
- 签署参与方编排专项设计： [signing-party-orchestration-design.md](special-designs/signing-party-orchestration-design.md)
- 签章引擎适配专项设计： [signature-engine-adapter-design.md](special-designs/signature-engine-adapter-design.md)
- 批量签章与结果优化专项设计： [batch-resign-and-result-optimization-design.md](special-designs/batch-resign-and-result-optimization-design.md)
- 签章运行时参数专项设计： [signature-runtime-parameters-design.md](special-designs/signature-runtime-parameters-design.md)
- 不同签署场景下的参与方编排策略细则
- 签章引擎适配层参数映射细节（见上述专项设计）

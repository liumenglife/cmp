# 合同后续业务组 Detailed Design

## 1. 文档说明

本文档是“合同后续业务组 / `contract-lifecycle`”的第一份正式
`Detailed Design`。

本文只下沉履约、变更、终止、归档这一组业务模块的内部实现层设计，
用于把以下上游约束收口为可落库、可编排、可恢复、可观测的内部实现基线。

### 1.1 输入

- 上游需求基线：
  [`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- 总平台架构：[`Architecture Design`](../../architecture-design.md)
- 总平台接口规范：[`API Design`](../../api-design.md)
- 总平台共享内部边界：[`Detailed Design`](../../detailed-design.md)
- 本业务组架构：[`Architecture Design`](./architecture-design.md)
- 本业务组接口规范：[`API Design`](./api-design.md)
- 合同管理本体详细设计：
  [`contract-core Detailed Design`](../contract-core/detailed-design.md)

### 1.2 输出

- 本文：[`Detailed Design`](./detailed-design.md)
- 为后续履约、变更、终止、归档专项设计与实现提供统一内部基线

### 1.3 阅读边界

本文只回答以下问题：

- 本业务组内部如何拆分，以及四类后续能力如何围绕同一 `contract_id` 协作
- 履约、变更、终止、归档、摘要、时间线、里程碑如何落到内部模型和主表
- 与合同主档、文档中心、流程引擎、搜索、AI、审计、通知如何内部挂接
- 并发控制、异步任务、补偿、恢复、观测边界如何落点

本文不承担以下内容：

- 不复述一期需求范围、架构总览、模块能力宣传口径
- 不重写对外 API 资源、请求响应字段、错误码全集
- 不写页面交互、实施排期、负责人和任务拆分
- 不把文档中心、流程引擎、搜索、AI 的完整内部设计搬到本文

## 2. 设计目标与约束落点

本业务组的内部实现以“一个合同主链、四类过程真相、一个稳定消费面”为核心。

### 2.1 真相源与边界落点

- 合同主档是业务真相源，统一持有 `contract_id`、合同一级业务身份、
  生命周期主状态和跨模块主引用。
- 文档中心是文件真相源，履约凭证、补充协议、终止协议、归档材料、
  归档封包都只以文件引用方式挂接，不复制文件版本链。
- 流程引擎通过 `contract_id` 绑定合同，只持有流程定义、实例、任务与结论，
  不拥有合同主档。
- 履约、变更、终止、归档都围绕同一 `contract_id` 运行，不创建新的合同主档。
- 归档记录属于归档模块真相，但不能替代合同主档对该合同业务身份、
  主阶段和主历史的解释权。
- 变更、终止是在原合同主链上推进新的业务阶段和结果，不生成新的合同主档。
- 搜索、AI、审计、通知只消费稳定摘要、里程碑与时间线，不直接消费四类子域的
  私有过程表。

### 2.2 一致性与写入落点

- `cl_*` 表是本业务组写模型中心，保存后续业务过程真相。
- 对合同主档的影响统一通过 `LifecycleWritebackService` 回写到
  `contract-core` 提供的受控接口，不跨模块直改 `cc_contract_master`。
- `cl_lifecycle_summary` 是本业务组稳定消费面，服务合同详情、搜索、AI、
  审计、通知与列表聚合。
- `cl_lifecycle_timeline_event` 是后续业务稳定事件面，统一沉淀里程碑、
  状态变化和关键动作；里程碑不单独长出第二套真相表。
- 所有正式状态推进先写子域主表，再刷新摘要，再投递时间线 / 外箱事件；
  搜索、AI、通知和审计扩展消费在事务后异步执行。

### 2.3 平台约束落点

- 物理表按 `MySQL` 设计，但统一经 `DB abstraction layer` 访问。
- `Redis` 只承担锁、幂等键、短期任务状态、去重和热点摘要缓存，
  不承担履约、变更、终止、归档正式状态真相。
- 异步协作用任务表 + 外箱事件模式承接，不以 `MQ` 为前提。
- 所有关键动作必须能回放：谁发起、作用于哪个 `contract_id`、
  对哪条过程记录生效、是否已回写摘要、是否已出站通知。

## 3. 模块内部拆分

### 3.1 内部模块清单

本业务组内部按“过程子域、统一消费面、集成挂接、运行治理”四层拆分。

| 内部模块 | 主要职责 | 直接持有对象 | 不直接持有对象 |
| --- | --- | --- | --- |
| `PerformanceDomain` | 履约聚合、履约节点、履约异常、履约凭证引用 | 履约记录、履约节点 | 合同主档、文件版本链 |
| `ChangeDomain` | 变更申请、影响评估、变更结果、有效期调整 | 变更记录、变更文档引用 | 新合同主档 |
| `TerminationDomain` | 终止申请、终止依据、善后记录、结果收口 | 终止记录、终止材料引用 | 合同删除或替代主档 |
| `ArchiveDomain` | 归档记录、归档输入集、归档产物引用、档案状态 | 归档记录、归档材料引用 | 文件介质真相、合同主档 |
| `LifecycleSummaryDomain` | 稳定摘要、里程碑提炼、时间线事件沉淀 | 摘要、时间线事件 | 四类过程完整明细 |
| `LifecycleIntegrationFacade` | 合同主档、文档中心、流程引擎、搜索、AI、审计、通知挂接 | 引用键、回写命令、外箱事件 | 外部模块真相 |
| `LifecycleOpsDomain` | 幂等、锁、异步任务、补偿、指标、恢复 | 锁键、任务元数据、恢复指令 | 业务主字段本身 |

### 3.2 内部调用原则

- 围绕同一 `contract_id` 的写操作必须先经合同准入校验，再进入对应子域。
- 履约、变更、终止、归档四类写模型互不直写对方主表，只通过摘要域和受控查询面
  获取对方稳定状态。
- 需要写回合同主档的动作统一经 `LifecycleIntegrationFacade` 调用
  `contract-core` 内部回写接口。
- 需要读取文件对象时只读取文档中心稳定引用和摘要，不直连文件存储。
- 需要发起审批时只向流程引擎发起绑定命令，并保存流程引用，不复制流程实例全量。

### 3.3 事务边界

- 单合同写入默认以 `contract_id` 为事务边界。
- 同步事务只覆盖：子域主表、必要引用表、`cl_lifecycle_summary`、
  `cl_lifecycle_timeline_event`、外箱事件。
- 合同主档二次聚合刷新、搜索索引刷新、AI 分析、通知分发、归档封包重建
  通过事务提交后的异步任务承接。
- 同一合同上不做跨子域大事务拼接，避免履约、变更、终止、归档互相放大锁范围。

## 4. 核心物理表设计

### 4.1 建表约定

- 表前缀统一为 `cl_`。
- 主键统一使用平台生成字符串主键。
- 所有正式主表默认包含：`created_at`、`created_by`、`updated_at`、
  `updated_by`、`is_deleted`、`version_no`。
- 状态枚举统一使用稳定编码，不把中文文案直接落库。
- `JSON` 字段只用于受控摘要块、影响范围块、扩展上下文块，
  不承载本应独立建索引的核心筛选字段。

### 4.2 `cl_performance_record`

- 用途：单合同履约聚合主表，承接履约总体状态、负责人、风险、整体进度。
- 关键主键：`performance_record_id`。
- 关键字段：
  - 关联字段：`contract_id`
  - 状态字段：`performance_status`、`progress_percent`、`risk_level`
  - 归属字段：`owner_user_id`、`owner_org_unit_id`
  - 节点汇总：`open_node_count`、`overdue_node_count`、`latest_due_at`
  - 摘要字段：`summary_text`、`latest_milestone_code`
  - 过程控制：`last_evaluated_at`、`last_writeback_at`
- 关键索引 / 唯一约束：
  - `uk_contract_performance(contract_id)`
  - `idx_perf_status(performance_status, risk_level)`
  - `idx_perf_owner(owner_org_unit_id, owner_user_id, performance_status)`
  - `idx_perf_due(latest_due_at, performance_status)`
- 关联对象：合同主档、履约节点、生命周期摘要、时间线事件。

### 4.3 `cl_performance_node`

- 用途：履约执行最小正式资源表，表达交付、验收、付款、回款、服务、质保等节点。
- 关键主键：`performance_node_id`。
- 关键字段：
  - 关联字段：`performance_record_id`、`contract_id`
  - 语义字段：`node_type`、`node_name`、`milestone_code`
  - 计划字段：`planned_at`、`due_at`
  - 执行字段：`actual_at`、`node_status`、`progress_percent`
  - 风险字段：`risk_level`、`issue_count`、`is_overdue`
  - 结果字段：`result_summary`、`last_result_at`
  - 责任字段：`owner_user_id`、`owner_org_unit_id`
- 关键索引 / 唯一约束：
  - `idx_perf_node_record(performance_record_id, node_status, due_at)`
  - `idx_perf_node_contract(contract_id, node_type, due_at)`
  - `idx_perf_node_owner(owner_user_id, node_status)`
  - `uk_perf_node(contract_id, node_type, node_name, planned_at, is_deleted)`
- 关联对象：履约聚合、文档引用、时间线事件、通知待办。

### 4.4 `cl_contract_change`

- 用途：原合同主链上的正式变更事项主表。
- 关键主键：`change_id`。
- 关键字段：
  - 关联字段：`contract_id`
  - 业务字段：`change_type`、`change_reason`、`change_summary`
  - 影响字段：`impact_scope_json`、`effective_date`
  - 状态字段：`change_status`、`approval_required`
  - 流程字段：`workflow_mode`、`workflow_instance_id`
  - 结果字段：`approved_at`、`applied_at`、`change_result_summary`
  - 数据版本字段：`result_version_no`、`supersedes_change_id`
- 关键索引 / 唯一约束：
  - `idx_change_contract(contract_id, change_status, created_at)`
  - `idx_change_workflow(workflow_instance_id)`
  - `idx_change_effective(effective_date, change_status)`
  - `uk_change_version(contract_id, result_version_no, is_deleted)`
- 关联对象：合同主档、文档引用、流程引用、生命周期摘要、时间线事件。

### 4.5 `cl_contract_termination`

- 用途：原合同主链上的正式终止事项主表。
- 关键主键：`termination_id`。
- 关键字段：
  - 关联字段：`contract_id`
  - 业务字段：`termination_type`、`termination_reason`、`termination_summary`
  - 请求字段：`requested_termination_date`、`settlement_summary`
  - 状态字段：`termination_status`、`approval_required`
  - 流程字段：`workflow_mode`、`workflow_instance_id`
  - 结果字段：`approved_at`、`terminated_at`、`post_action_status`
  - 限制字段：`archive_block_reason`、`reopen_allowed_flag`
- 关键索引 / 唯一约束：
  - `idx_term_contract(contract_id, termination_status, created_at)`
  - `idx_term_workflow(workflow_instance_id)`
  - `idx_term_requested(requested_termination_date, termination_status)`
  - `uk_active_termination(contract_id, termination_status, is_deleted)`
- 关联对象：合同主档、文档引用、流程引用、归档准入判断、时间线事件。

### 4.6 `cl_archive_record`

- 用途：单合同正式归档记录主表，是归档子域过程真相中心。
- 关键主键：`archive_record_id`。
- 关键字段：
  - 关联字段：`contract_id`
  - 业务字段：`archive_type`、`archive_reason`、`archive_scope_json`
  - 状态字段：`archive_status`、`archive_integrity_status`
  - 管理字段：`archive_batch_no`、`archive_keeper_user_id`、`archive_location_code`
  - 策略字段：`retention_policy_code`、`retention_until`
  - 产物字段：`package_document_id`、`manifest_document_id`
  - 结果字段：`archived_at`、`last_rebuild_at`
- 关键索引 / 唯一约束：
  - `idx_archive_contract(contract_id, archive_status, archived_at)`
  - `idx_archive_batch(archive_batch_no, archive_status)`
  - `idx_archive_keeper(archive_keeper_user_id, archive_status)`
  - `uk_contract_archive(contract_id, archive_batch_no, is_deleted)`
- 关联对象：合同主档、文档中心、摘要、时间线事件、借阅子域扩展。

### 4.7 `cl_lifecycle_summary`

- 用途：本业务组统一稳定摘要表，作为搜索、AI、审计、通知和合同详情的消费面。
- 关键主键：`lifecycle_summary_id`。
- 关键字段：
  - 关联字段：`contract_id`
  - 阶段字段：`current_stage`、`stage_status`
  - 履约摘要：`performance_summary_json`
  - 变更摘要：`change_summary_json`
  - 终止摘要：`termination_summary_json`
  - 归档摘要：`archive_summary_json`
  - 风险字段：`risk_summary_json`
  - 里程碑字段：`latest_milestone_code`、`latest_milestone_at`
  - 待办字段：`pending_action_json`
  - 回写字段：`last_contract_writeback_at`、`summary_version`
- 关键索引 / 唯一约束：
  - `uk_lifecycle_summary(contract_id)`
  - `idx_stage(current_stage, stage_status)`
  - `idx_milestone(latest_milestone_at, latest_milestone_code)`
- 关联对象：合同主档摘要、搜索索引、AI 输入、通知卡片、审计串联。

### 4.8 `cl_lifecycle_timeline_event`

- 用途：围绕同一 `contract_id` 沉淀后续业务稳定事件与里程碑。
- 关键主键：`timeline_event_id`。
- 关键字段：
  - 关联字段：`contract_id`
  - 事件字段：`event_type`、`event_level`、`event_title`、`event_summary`
  - 时间字段：`occurred_at`
  - 来源字段：`source_resource_type`、`source_resource_id`
  - 里程碑字段：`milestone_code`、`milestone_status`
  - 可见性字段：`visible_to_search`、`visible_to_ai`、`visible_to_notify`
  - 去重字段：`dedupe_key`
- 关键索引 / 唯一约束：
  - `idx_timeline_contract(contract_id, occurred_at)`
  - `idx_timeline_type(contract_id, event_type, occurred_at)`
  - `idx_timeline_milestone(contract_id, milestone_code, occurred_at)`
  - `uk_timeline_dedupe(contract_id, dedupe_key)`
- 关联对象：四类子域主表、摘要表、搜索、AI、审计、通知。

### 4.9 `cl_lifecycle_document_ref`

- 用途：统一文件引用表，保存后续业务记录与文档中心文件对象的业务语义绑定。
- 关键主键：`lifecycle_document_ref_id`。
- 关键字段：
  - `contract_id`
  - `source_resource_type`、`source_resource_id`
  - `document_role`：如 `PERFORMANCE_EVIDENCE`、`CHANGE_AGREEMENT`、
    `TERMINATION_AGREEMENT`、`ARCHIVE_PACKAGE`
  - `document_id`
  - `document_version_ref`
  - `is_primary`
  - `display_order`
- 关键索引 / 唯一约束：
  - `idx_doc_ref_source(source_resource_type, source_resource_id, document_role)`
  - `idx_doc_ref_contract(contract_id, document_role)`
  - `uk_doc_ref(source_resource_type, source_resource_id, document_id, document_role)`
- 关联对象：文档中心、履约节点、变更、终止、归档记录。

### 4.10 `cl_lifecycle_process_ref`

- 用途：统一流程引用表，保存后续业务记录与流程引擎实例的绑定关系。
- 关键主键：`lifecycle_process_ref_id`。
- 关键字段：
  - `contract_id`
  - `source_resource_type`、`source_resource_id`
  - `workflow_instance_id`
  - `workflow_definition_id`
  - `process_purpose`
  - `process_status_snapshot`
  - `last_synced_at`
- 关键索引 / 唯一约束：
  - `uk_process_ref(source_resource_type, source_resource_id, workflow_instance_id)`
  - `idx_process_contract(contract_id, process_purpose, last_synced_at)`
  - `idx_process_instance(workflow_instance_id)`
- 关联对象：流程引擎、变更、终止、归档审批、审计与恢复。

## 5. 履约 / 变更 / 终止 / 归档内部模型

### 5.1 履约内部模型

履约采用“聚合记录 + 节点明细 + 文档引用 + 摘要回写”模型。

- `PerformanceRecord` 是单合同唯一有效履约聚合，负责汇总整体状态、负责人、
  进度、风险和最近节点到期信息。
- `PerformanceNode` 是履约执行最小正式资源，一个节点只表达一类明确业务责任，
  例如交付、验收、付款、回款、服务、质保。
- 履约异常不单独建立一级主表，先沉淀为节点上的 `issue_count`、`risk_level`、
  `result_summary` 和时间线事件；如后续异常闭环复杂度提升，再独立拆出专项子域。
- 履约完成判断采用聚合判定：所有必需节点完成，且不存在阻断性逾期或终止中的
  活动记录，才能把履约摘要写为完成。

### 5.2 变更内部模型

变更采用“事项单 + 影响评估块 + 流程引用 + 应用结果”模型。

- 一次变更对应一条 `ContractChange`，只表达这次变更事项本身。
- `impact_scope_json` 用于收口影响范围，如金额、期限、交付范围、责任义务、
  附件替换、补充协议生效范围。
- 变更审批完成后只回写稳定结果，如最新生效日期、当前有效版本号、
  最新变更摘要，不把变更详情平铺复制到合同主档。
- 同一合同允许存在多次历史变更，但同一时刻只允许一个处于正式推进中的主变更流。

### 5.3 终止内部模型

终止采用“事项单 + 依据材料 + 善后块 + 阶段封口”模型。

- 一次终止对应一条 `ContractTermination`。
- 终止事项承接终止类型、原因、请求时间、依据材料和善后说明。
- 终止通过后，业务侧只回写稳定结论：终止时间、终止摘要、后续限制、
  是否允许归档、是否还有善后事项待完成。
- 终止不是删除合同；合同主档仍保留原身份、原历史和终止后的可查询状态。

### 5.4 归档内部模型

归档采用“归档记录 + 输入集校验 + 归档产物引用 + 档案状态”模型。

- 一次正式归档对应一条 `ArchiveRecord`。
- 归档输入集来自合同主档摘要、文档中心正式文件引用、履约摘要、变更摘要、
  终止摘要，不直接扫描各私有过程表拼装给搜索或通知使用。
- 归档记录持有归档状态、档案位置、保管人、保管策略和归档产物引用。
- 归档记录属于归档模块真相，但它解释的是“归档过程和档案状态”，
  不是合同主档的业务身份。

### 5.5 四类模型的统一边界

- 四类过程模型都必须带 `contract_id`，不允许脱离合同主链独立存在。
- 四类过程模型都可以持有自己的状态机，但只能通过稳定摘要影响外部消费面。
- 四类过程模型都可以挂文档引用和流程引用，但文件和流程本体真相仍归上游模块。
- 对外稳定表达统一落在 `cl_lifecycle_summary` 和 `cl_lifecycle_timeline_event`。

## 6. 摘要 / 时间线 / 里程碑内部模型

### 6.1 摘要模型

`LifecycleSummary` 是本业务组唯一稳定消费面，不替代四类过程主表。

- 按合同唯一：每个 `contract_id` 一条当前有效摘要。
- 按模块分槽：履约、变更、终止、归档各有独立摘要块，避免一个大文本字段失控。
- 按消费对象裁剪：摘要字段只保留搜索、AI、通知、合同详情需要的稳定信息，
  不暴露私有中间状态和补偿细节。
- 按兼容版本治理：`summary_version` 只表示摘要结构与消费契约版本，用于判断摘要是否仍兼容既有消费面、是否需要重建。
- 按写回序号推进：每次正式写回都应形成独立写回序号或快照序号，用于去重、消费幂等和补偿先后判断，但该序号不是 `summary_version`。

### 6.2 时间线模型

时间线采用“稳定事件流”模型，而不是流程节点镜像表。

- 事件粒度以业务可理解为准，例如“履约节点逾期”“变更审批通过”
  “终止完成”“归档完成”，而不是“流程节点 3 从待办变已办”。
- 时间线一律围绕同一 `contract_id` 组织，保证合同详情、搜索和审计串联一致。
- 每条事件必须能回指来源资源，但不要求复制来源资源全量字段。
- 事件生成遵循幂等规则，由 `dedupe_key` 保证重复回调、重复任务不产生重复事件。

### 6.3 里程碑模型

里程碑不单独建立一级真相表，采用“时间线事件中的高等级事件 + 摘要快照字段”
双落点模型。

- 在 `cl_lifecycle_timeline_event` 中以 `event_level=MILESTONE`、
  `milestone_code` 标识正式里程碑。
- 在 `cl_lifecycle_summary` 中保留最新里程碑代码与时间，支持列表和详情快速读取。
- 里程碑候选包括：`PERFORMANCE_STARTED`、`PERFORMANCE_COMPLETED`、
  `CHANGE_APPROVED`、`TERMINATION_COMPLETED`、`ARCHIVE_COMPLETED`。
- 里程碑只描述对外稳定状态转折，不承载私有过程细节。

### 6.4 摘要回写策略

- 履约节点、变更结果、终止结果、归档结果变更后，先局部刷新对应摘要块，
  再统一重算顶层阶段与最新里程碑。
- 摘要刷新与时间线事件写入在同一事务内完成，保证消费面自洽。
- 合同主档回写采用事务后异步确认；若合同主档回写失败，本业务组摘要保持已更新，
  并由恢复任务补偿，避免过程真相回滚。

## 7. 与合同主档、文档中心、流程引擎的内部挂接设计

### 7.1 与合同主档的挂接

- 合同主档是业务真相源，本业务组不维护第二套合同身份、分类和主状态。
- 本业务组读取合同上下文时，只读取：`contract_id`、主状态、归属、关键日期、
  主文档引用、最近周边摘要引用。
- 本业务组回写合同主档时，只回写稳定结果：
  - 最新履约摘要引用、履约阶段状态
  - 最新变更引用、变更后的稳定业务摘要
  - 最新终止引用、终止后的主阶段限制
  - 最新归档引用、归档状态摘要
- 本业务组不直接修改合同编号、分类主链、模板归属等合同本体字段。

### 7.2 与文档中心的挂接

- 文档中心是文件真相源，所有正式文件必须先进入文档中心，再由
  `cl_lifecycle_document_ref` 挂接。
- 本业务组只保存：`document_id`、业务角色、是否主引用、显示顺序、
  使用时的版本引用。
- 归档封包和清单也是文档中心文件对象，本业务组只保存其业务引用和归档语义。
- 文档更新不会自动改写业务结论；只有当业务动作明确采用新文件版本时，
  才刷新摘要和时间线。

### 7.3 与流程引擎的挂接

- 流程引擎通过 `contract_id` 绑定合同，不拥有合同主档。
- 需要审批的变更、终止、归档动作都通过 `cl_lifecycle_process_ref`
  记录流程实例绑定。
- 本业务组只保存流程引用、流程用途、最近同步状态，不复制节点明细。
- 流程结论进入本业务组后，先更新对应子域主表，再刷新摘要和时间线，
  最后回写合同主档。
- 流程撤回、拒绝、重提都被视为过程状态变化，只影响子域过程和摘要，
  不改写合同主档一级身份。

## 8. 与搜索、AI、审计、通知的内部挂接设计

### 8.1 搜索挂接

- 搜索只消费 `cl_lifecycle_summary` 和 `cl_lifecycle_timeline_event` 中标记为可见的
  稳定字段。
- 搜索索引文档按 `contract_id` 聚合，履约、变更、终止、归档作为分区摘要块。
- 搜索刷新由事务后外箱事件触发，不在主写事务中同步刷索引。

### 8.2 AI 挂接

- AI 输入只读取合同主档摘要、本业务组稳定摘要和必要文件引用摘要。
- AI 可基于这些稳定输入执行履约风险识别、变更影响分析、终止依据辅助判断、
  归档完整性检查。
- AI 输出只写入辅助结果或任务结果，不回写四类过程主表正式状态。

### 8.3 审计挂接

- 四类正式动作、摘要回写、流程同步、归档重建、解密下载引用都必须产生日志事件。
- 审计中心消费的是业务动作事实和结果，不反向作为本业务组状态真相。
- 审计字段最小集包括：`contract_id`、资源类型、资源主键、动作、操作者、
  结果、来源端、关联流程实例、关联文档引用。

### 8.4 通知挂接

- 通知只消费里程碑和稳定待办，例如履约节点即将到期、变更待审批、
  终止已完成、归档已完成。
- 通知模板只读取摘要块和时间线事件，不读取私有过程中间字段。
- 通知失败不影响业务状态提交，由通知任务单独重试。

## 9. 并发控制、异步任务、补偿与恢复

### 9.1 并发控制

- 单合同写操作采用 `contract_id` 级别串行化策略。
- 主表更新统一依赖 `version_no` 乐观锁，避免多端覆盖写。
- 需要防止重复发起的动作使用幂等键：
  - 变更提交：`contract_id + change_id + action`
  - 终止提交：`contract_id + termination_id + action`
  - 归档创建：`contract_id + archive_batch_no + action`
  - 履约节点完成：`performance_node_id + action + result_version`
- 对“同一合同同一时刻只允许一个活动中的主变更流 / 主终止流 / 主归档流”
  采用数据库唯一约束 + 应用层前置校验双保险。

### 9.2 异步任务

以下动作统一走异步任务：

- 合同主档摘要二次刷新
- 搜索索引刷新
- AI 风险分析与归档完整性分析
- 通知分发
- 归档封包生成与重建
- 流程状态补拉同步

任务正式状态落平台任务中心，本业务组只保留任务关联键和恢复上下文。

### 9.3 补偿策略

- 子域主表提交成功但合同主档回写失败：记录补偿任务，按 `contract_id` 重新回写。
- 摘要写入成功但搜索 / 通知 / AI 失败：只重试外部消费，不回滚业务状态。
- 流程回调重复到达：通过 `workflow_instance_id + callback_seq` 或稳定回调指纹幂等。
- 归档封包生成失败：归档记录进入 `ARCHIVE_BUILD_FAILED`，允许重建，不回退已确认的
  合同主档历史事实。

### 9.4 恢复策略

- 恢复入口以 `contract_id` 为主键，从四类子域主表重算 `cl_lifecycle_summary`，
  必要时重建时间线缺口。
- 时间线重建遵循“来源主表为准、时间线为投影”的原则；若发现缺失事件，
  可按来源记录补写，但不得伪造不存在的业务动作。
- 合同主档回写补偿应按写回序号判断先后，只应用比主档已知写回序号更新的摘要；`summary_version` 仅用于兼容判断，不用于同版本内的先后比较。

## 10. 审计、日志、指标与恢复边界

### 10.1 审计与日志边界

- 审计记录的是关键业务事实和敏感动作，不记录高频无业务意义的轮询读操作。
- 应用日志用于排障，必须能关联 `trace_id`、`contract_id`、资源主键和任务主键。
- 过程状态变化、摘要刷新、时间线写入、合同回写、出站事件投递都要有结构化日志。

### 10.2 指标边界

核心指标至少包括：

- 履约节点逾期数、履约节点按时完成率
- 变更审批耗时、变更应用耗时
- 终止处理耗时、终止后善后未结清数
- 归档完成率、归档重建失败率
- 摘要回写延迟、时间线写入失败率、合同主档补偿成功率

### 10.3 恢复边界

- 可恢复对象：摘要、时间线、搜索索引、通知投递、AI 任务、归档封包。
- 不可通过投影自行恢复的对象：四类子域主表人工录入事实、流程引擎正式审批结论、
  文档中心文件对象本体。
- 若来源事实已缺失或被人工纠正，恢复流程必须以人工确认后的源事实为准，
  不允许仅凭时间线或搜索索引反推覆盖主表。

## 11. 继续下沉到后续专项设计或实现的内容

以下内容应继续下沉到后续专项设计、实现说明或测试方案，不继续留在本文：

- 履约节点类型字典、逾期判定规则、风险评分细则
  履约规则与风险评分专项设计： [fulfillment-rules-and-risk-scoring-design.md](special-designs/fulfillment-rules-and-risk-scoring-design.md)
- 变更影响评估块的字段级映射与与合同主档字段回写规则
  变更影响与回写专项设计： [change-impact-and-write-back-design.md](special-designs/change-impact-and-write-back-design.md)
- 终止善后清单、结算规则、终止后权限限制清单
  终止善后与访问控制专项设计： [termination-settlement-and-access-control-design.md](special-designs/termination-settlement-and-access-control-design.md)
- 归档输入集完整性规则、封包格式、目录结构、借阅 / 归还子域表设计、封包生成伪代码、幂等锁与补偿任务清单
  归档封包与借阅归还专项设计： [archive-package-and-borrow-return-design.md](special-designs/archive-package-and-borrow-return-design.md)
- 文档引用角色字典、流程用途字典、时间线事件字典和通知模板细则（履约节点类型字典治理以第一份专项为准，此处仅引用其结果）
  生命周期字典与通知专项设计： [lifecycle-dictionary-and-notification-design.md](special-designs/lifecycle-dictionary-and-notification-design.md)
- 摘要结构兼容口径、写回序号治理、重建与回填边界
  摘要重建与回填专项设计： [summary-rebuild-and-backfill-design.md](special-designs/summary-rebuild-and-backfill-design.md)

## 12. 本文结论

本业务组的内部实现基线已经明确为：

- 履约、变更、终止、归档围绕同一 `contract_id` 运行
- 合同主档是业务真相源，文档中心是文件真相源，流程引擎通过 `contract_id`
  绑定合同而不拥有合同主档
- 四类过程真相落在 `cl_*` 主表，稳定消费面收口在
  `cl_lifecycle_summary` 与 `cl_lifecycle_timeline_event`
- 里程碑通过“高等级时间线事件 + 摘要快照字段”实现，不额外长出第二套真相表
- 搜索、AI、审计、通知只消费稳定摘要、里程碑与时间线
- 合同主档回写、搜索刷新、通知分发、AI 分析、归档重建都通过受控异步与补偿机制
  保障最终一致性

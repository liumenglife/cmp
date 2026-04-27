# 流程引擎子模块 Detailed Design

## 1. 文档说明

本文档是 `CMP` 流程引擎子模块的第一份正式 `Detailed Design`。

本文在以下输入文档约束下，进一步收口流程引擎内部实现层设计：

- 上游需求基线：[`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- 总平台架构：[`Architecture Design`](../../architecture-design.md)
- 总平台接口边界：[`API Design`](../../api-design.md)
- 总平台共享内部边界：[`Detailed Design`](../../detailed-design.md)
- 流程引擎子模块架构：[`Architecture Design`](./architecture-design.md)
- 流程引擎子模块接口边界：[`API Design`](./api-design.md)

本文输出以下内容：

- 流程引擎子模块内部模块拆分与职责边界
- 流程定义、版本、节点、绑定、实例、任务、动作等主表级设计
- 运行时状态模型、组织绑定解析、并行与会签等核心内部机制
- `OA` 桥接、异步任务、并发控制、补偿恢复与观测边界
- 需继续下沉到后续专项设计或实现阶段的遗留边界

本文只回答“流程引擎内部如何实现并稳定运行”，不展开以下内容：

- 不复述需求范围、验收范围与业务背景
- 不重写技术选型理由、系统拓扑总览与总平台模块介绍
- 不重写对外 API 路径、请求字段、响应样例、错误码全集
- 不写成实施计划、排期、任务拆分或联调日程
- 不把节点 DSL、表达式语法、画布协议细化到代码级规范

## 2. 设计目标与约束落点

本子模块在内部设计上必须同时满足以下约束：

- 默认主审批路径仍优先走 `OA`
- 平台流程引擎是一期正式能力，不是仅保留接口的后备层
- 每个审批节点必须绑定组织架构中的部门、人员或组织规则
- 流程引擎不拥有合同主档，只通过 `contract_id` 绑定业务对象
- 流程引擎不得生成第二合同真相源，合同状态最终仍回写总平台合同主档
- 命名、资源口径、审批摘要口径继承总平台与本模块既定 `API Design`

## 3. 模块内部拆分

### 3.1 内部模块清单

1. `Definition Registry`
   - 管理流程定义主档、业务编码、启停状态与当前草稿引用。
   - 负责发布入口的唯一约束、版本定位与定义级缓存失效。
2. `Version Compiler`
   - 把草稿定义冻结为可运行版本快照。
   - 负责节点拓扑校验、组织绑定完整性校验、版本摘要生成。
3. `Runtime Orchestrator`
   - 负责实例创建、节点推进、分支收敛、终止、撤回、异常收敛。
   - 是平台引擎执行路径下唯一能修改实例主状态的内部协调器。
4. `Task Manager`
   - 负责审批任务生成、任务关闭、转办派生、抄送映射、催办与到期控制。
   - 对接任务中心时输出统一任务语义，不把流程私有状态泄漏给外部。
5. `Org Binding Resolver`
   - 负责把 `USER`、`ORG_UNIT`、`ORG_RULE` 绑定解析为运行时参与人集合。
   - 输出候选人、执行人快照、解析证据与失败原因。
6. `OA Bridge Adapter`
   - 负责默认 `OA` 主路径下的发起、回调承接、摘要同步与状态归一。
   - 只适配平台侧桥接，不承接 `OA` 本体规则设计。
7. `Workflow Collaboration Gateway`
   - 负责与合同主档、审计中心、通知中心、任务中心之间的内部协作，以及把流程引擎治理后的通知命令按标准契约输出到通知中心。
   - 保证审批事件能被统一消费，但不把外部中心写成流程引擎私有表。
8. `Runtime Control Plane`
   - 负责缓存、锁、幂等键、重试任务、恢复任务、指标汇总，以及异步任务注册表 / 治理参数 / 通知事件契约的正式治理对象承接。
   - 不拥有业务真相，只负责运行时治理。

### 3.2 内部调用原则

- 定义发布只允许 `Definition Registry -> Version Compiler`。
- 实例推进只允许 `Runtime Orchestrator` 改写实例主状态。
- 任务状态推进只允许 `Task Manager` 改写任务主状态。
- 组织解析结果必须由 `Org Binding Resolver` 输出，其他模块不得自行拼装选人。
- `OA` 相关外部输入必须先进入 `OA Bridge Adapter` 归一，再交由运行时或摘要更新逻辑处理。
- 审计、通知、任务中心协作统一经 `Workflow Collaboration Gateway` 发出。

## 4. 核心物理表设计

### 4.1 建表原则

- 主键统一使用平台字符串主键。
- 所有主表统一包含 `created_at`、`created_by`、`updated_at`、`updated_by`、
  `is_deleted`、`row_version`。
- 真相表落 `MySQL`；缓存、锁、幂等键只做增强层。
- 发布态、实例态、任务态都必须能在数据库中独立恢复，不依赖内存状态。

### 4.2 `wf_process_definition`

用途：流程定义主表，承接业务流程模板的稳定身份。

关键主键：`definition_id`

关键字段：

- `process_code`：跨版本稳定编码
- `workflow_domain`：流程域稳定标识，用于界定同一业务对象的实例互斥边界
- `process_name`
- `business_type`
- `approval_mode`：`OA` / `CMP`
- `definition_status`：定义主状态
- `current_draft_version_id`
- `latest_published_version_id`
- `organization_binding_required`

关键索引 / 唯一约束：

- `uk_process_code(process_code)`
- `idx_business_type(business_type, approval_mode, definition_status)`
- `idx_latest_published(latest_published_version_id)`

关联对象：

- 关联 `wf_process_version`
- 被 `wf_process_instance` 引用

### 4.3 `wf_process_version`

用途：冻结一次可运行版本，保存版本级快照、发布态、兼容摘要，以及发布校验与审计治理锚点。

关键主键：`version_id`

关键字段：

- `definition_id`
- `version_no`
- `version_status`：`DRAFT`、`PUBLISHED`、`DISABLED`、`ARCHIVED`
- `version_snapshot`
- `dsl_schema_version`
- `topology_checksum`
- `validation_ruleset_version`
- `publish_validation_report_ref`
- `publish_validation_summary`
- `publish_audit_event_id`
- `published_at`
- `published_by`
- `version_note`

关键索引 / 唯一约束：

- `uk_definition_version(definition_id, version_no)`
- `uk_definition_current_publish(definition_id, is_current_published)`
  其中 `is_current_published=1` 时同时只允许一条
- `idx_version_status(definition_id, version_status, published_at)`

关联对象：

- 关联 `wf_process_definition`
- 关联 `wf_node_definition`
- 稳定关联正式 `Validation Report` 与发布审计事件证据
- 被 `wf_process_instance` 引用

说明：`wf_process_version` 只保存发布校验的稳定治理锚点，不在主表内展开整份报告正文；完整 `Validation Report` 以不可变证据对象持久化，至少与 `version_id + validation_ruleset_version + publish_audit_event_id` 稳定关联。由此，规则集版本、发布校验结果摘要与审计事件引用成为正式版本治理的一部分，而不是发布前一次临时检查结果。

### 4.4 `wf_node_definition`

用途：保存版本内节点定义，是运行时推进的基础静态快照。

关键主键：`node_id`

关键字段：

- `version_id`
- `node_key`：版本内稳定节点键
- `node_name`
- `node_type`：`START`、`APPROVAL`、`PARALLEL_GATEWAY`、`END` 等
- `parent_node_id`：并行 / 会签子节点可回指父节点
- `join_group_key`：并行收敛组键
- `participant_mode`：`SINGLE`、`PARALLEL`、`COUNTERSIGN`；仅用于审批节点内部的参与人并发语义，不替代 `PARALLEL_GATEWAY` 的分叉 / 收敛拓扑语义
- `route_config`：冻结收敛模式、阈值参数、一票否决、动态追加约束等节点级聚合规则声明
- `time_limit_config`：冻结 `timeout_at` / `timeout_duration`、`timeout_strategy`、`timeout_params` 等超时治理声明，其中 `timeout_strategy` 正式枚举统一为 `ESCALATE`、`FORCE_PASS`、`FORCE_REJECT`、`EXCLUDE_FROM_BASE`
- `oa_mapping_config`

关键索引 / 唯一约束：

- `uk_version_node_key(version_id, node_key)`
- `idx_parent_node(parent_node_id)`
- `idx_join_group(version_id, join_group_key)`

关联对象：

- 关联 `wf_process_version`
- 关联 `wf_node_binding`
- 被 `wf_process_instance`、`wf_approval_task` 引用为运行时节点来源

### 4.5 `wf_node_binding`

用途：保存节点与组织架构的绑定配置，是节点可执行性的强制约束层。

关键主键：`binding_id`

关键字段：

- `version_id`
- `node_id`
- `binding_type`：`USER`、`ORG_UNIT`、`ORG_RULE`
- `binding_value`：对应用户、组织单元或规则标识
- `binding_order`
- `is_required`
- `resolver_config`
- `fallback_strategy`

关键索引 / 唯一约束：

- `uk_node_binding(node_id, binding_type, binding_value)`
- `idx_version_node(version_id, node_id, binding_order)`
- `idx_binding_type(binding_type, binding_value)`

关联对象：

- 关联 `wf_node_definition`
- 由 `Org Binding Resolver` 读取并生成运行时参与人快照

### 4.6 `wf_process_instance`

用途：流程实例主表，承接一次审批流程的运行时真相。

关键主键：`process_id`

关键字段：

- `definition_id`
- `version_id`
- `contract_id`
- `workflow_domain`：实例启动时从流程定义冻结的流程域标识，不因后续定义调整漂移
- `approval_mode`：`OA` / `CMP`
- `instance_status`
- `current_node_id`
- `current_node_key`
- `starter_user_id`
- `source_system`
- `source_system_instance_id`
- `route_mode`：`OA_PRIMARY`、`CMP_PRIMARY`、`CMP_TAKEOVER`
- `result_status`
- `started_at`
- `finished_at`

关键索引 / 唯一约束：

- `uk_contract_domain_active(contract_id, workflow_domain, active_instance_flag)`
  其中 `active_instance_flag=1` 时限制同一业务对象在同一流程域内仅存在一个激活实例；不同流程域可并存激活实例
- `uk_source_instance(source_system, source_system_instance_id)`
- `idx_runtime_lookup(instance_status, workflow_domain, current_node_id, updated_at)`
- `idx_contract(contract_id, started_at)`

关联对象：

- 关联定义与版本
- 关联 `wf_approval_task`、`wf_approval_action`
- 通过 `contract_id` 绑定合同主档，但不拥有合同主档

说明：`workflow_domain` 是实例互斥治理的正式维度，由 `wf_process_definition.workflow_domain` 在实例启动时冻结到 `wf_process_instance`。这样数据库唯一约束才能真正表达“同一业务对象同一流程域内仅存在一个激活实例”，而不是把所有流程定义错误地挤压到同一把锁上。

### 4.6.1 `wf_participant_snapshot`

用途：参与人快照主表，承接节点进入时冻结的正式 `ParticipantSnapshot` 证据，不再让任务内嵌投影承担求值真相。

关键主键：`snapshot_id`

关键字段：

- `process_id`
- `node_id`
- `node_key`
- `binding_id`
- `binding_type`
- `org_rule_id`
- `rule_version_no`
- `resolver_version`
- `context_checksum`
- `resolution_status`
- `candidate_user_snapshot`
- `filter_applied_snapshot`
- `resolved_assignee_snapshot`
- `fallback_detail`
- `evidence_summary`
- `snapshot_generated_at`
- `snapshot_generated_by`

关键索引 / 唯一约束：

- `uk_process_node_binding_snapshot(process_id, node_id, binding_id, context_checksum)`
- `idx_process_node(process_id, node_id, snapshot_generated_at)`
- `idx_org_rule(org_rule_id, rule_version_no)`

关联对象：

- 关联 `wf_process_instance`
- 关联 `wf_node_definition`
- 被 `wf_approval_task.resolver_snapshot.snapshot_id` 引用

说明：`wf_participant_snapshot` 是 `ParticipantSnapshot` 的正式持久化锚点。`wf_approval_task.candidate_snapshot` 只保留任务分发所需最小投影，`wf_approval_task.resolver_snapshot` 至少要稳定引用 `snapshot_id` 并携带必要执行摘要；完整求值证据、过滤链与候选集合统一以 `wf_participant_snapshot` 为准。

### 4.7 `wf_approval_task`

用途：审批任务主表，承接待办、已办、转办派生任务与会签子任务。

关键主键：`task_id`

关键字段：

- `process_id`
- `node_id`
- `node_key`
- `task_type`：`APPROVAL`、`COUNTERSIGN`、`COPY`、`SYSTEM`
- `task_status`
- `assignee_user_id`
- `assignee_org_unit_id`
- `candidate_snapshot`
- `resolver_snapshot`：至少包含 `snapshot_id` 引用与必要执行摘要
- `parent_task_id`
- `origin_task_id`
- `transfer_from_action_id`
- `due_at`
- `completed_at`
- `available_action_mask`

关键索引 / 唯一约束：

- `idx_process_node(process_id, node_id, task_status)`
- `idx_assignee(assignee_user_id, task_status, due_at)`
- `idx_origin(origin_task_id)`
- `idx_due(task_status, due_at)`

关联对象：

- 关联 `wf_process_instance`
- 关联 `wf_approval_action`
- 转办时通过 `origin_task_id` 保留任务链路

### 4.8 `wf_approval_action`

用途：审批动作事实表，记录审批、驳回、转办、催办、撤回、终止等动作。

关键主键：`action_id`

关键字段：

- `process_id`
- `task_id`
- `node_id`
- `action_type`
- `action_result`
- `operator_user_id`
- `target_user_id`
- `comment`
- `attachment_snapshot`
- `idempotency_key`
- `source_system`
- `source_event_id`
- `acted_at`

关键索引 / 唯一约束：

- `uk_action_idempotency(process_id, idempotency_key)`
- `uk_external_action(source_system, source_event_id)`
- `idx_task_action(task_id, acted_at)`
- `idx_process_action(process_id, acted_at)`

关联对象：

- 关联实例与任务
- 为审计中心、时间线、摘要刷新提供事实来源

### 4.9 `wf_node_aggregation`

用途：统一承接并行收敛组或会签节点的运行时聚合状态，避免把聚合计数和决策证据散落在实例、任务或缓存中。

关键主键：`aggregation_id`

关键字段：

- `process_id`
- `node_id`
- `node_key`
- `join_group_key`
- `aggregation_scope`：`PARALLEL_JOIN`、`COUNTERSIGN`
- `aggregation_status`：`OPEN`、`DECIDED`、`AWAITING_MANUAL_DECISION`、`RECOVERING`
- `rule_snapshot`
- `participant_snapshot`
- `counter_snapshot`
- `last_fact_action_id`
- `latest_decision_action_id`
- `latest_decision_snapshot_ref`
- `decided_at`

关键索引 / 唯一约束：

- `uk_process_node_scope(process_id, node_id, aggregation_scope)`
- `idx_join_group(process_id, join_group_key, aggregation_status)`
- `idx_last_fact(last_fact_action_id)`

关联对象：

- 关联 `wf_process_instance`
- 关联 `wf_node_definition`
- 关联 `wf_approval_action`

说明：

- `Aggregation State` 的正式落点是 `wf_node_aggregation` 当前行中的 `aggregation_status + rule_snapshot + participant_snapshot + counter_snapshot + last_fact_action_id`，用于恢复打开态聚合、核对当前计数并确定从哪个事实点继续重放。
- `Aggregation Decision` 的正式落点是 `latest_decision_action_id + latest_decision_snapshot_ref`：每次收敛、超时降级、冲突消解或人工裁定都必须写入一条正式 `wf_approval_action`，并把不可变判定快照挂到该引用上。
- 恢复时以 `wf_node_aggregation` 为锚点重建聚合上下文，再按 `last_fact_action_id` 之后的动作事实重放；审计与回放统一通过 `process_id + node_id + join_group_key + latest_decision_action_id` 引用对应决策证据，而不是回读临时内存态。

### 4.10 `wf_task_timer`

用途：统一承接催办节流、超时控制、升级处理与扫描定位，不为催办和超时分别再建主表。

关键主键：`timer_id`

关键字段：

- `process_id`
- `task_id`
- `timer_type`：`REMIND`、`TIMEOUT`、`ESCALATION`
- `timer_status`
- `next_trigger_at`
- `last_triggered_at`
- `trigger_count`
- `timer_policy_snapshot`

关键索引 / 唯一约束：

- `uk_task_timer(task_id, timer_type)`
- `idx_trigger(timer_status, next_trigger_at)`

关联对象：

- 关联 `wf_approval_task`
- 由任务中心调度扫描

说明：

- 催办历史、超时动作结果不单独建表，统一沉淀到 `wf_approval_action`。
- `wf_task_timer` 只管理触发计划与次数，不替代动作事实表。

### 4.11 `wf_oa_bridge_record`

用途：承接 `OA` 主路径下的平台桥接治理分面，稳定保存绑定真相、映射真相、承接真相与补偿真相。

关键主键：`bridge_record_id`

关键字段：

- `process_id`
- `contract_id`
- `oa_flow_code`
- `oa_instance_id`
- `mapping_template_version`
- `signature_evidence_ref`
- `bridge_status`
- `compensation_status`
- `request_payload_checksum`
- `participant_mapping_summary`
- `latest_callback_event_id`
- `latest_callback_status`
- `exception_category`
- `manual_intervention_flag`
- `last_synced_at`
- `sync_retry_count`

关键索引 / 唯一约束：

- `uk_oa_instance(oa_instance_id)`
- `idx_bridge_status(bridge_status, last_synced_at)`
- `idx_callback_sync(oa_instance_id, latest_callback_event_id)`
- `idx_contract(contract_id)`

关联对象：

- 关联 `wf_process_instance`
- 服务 `OA Bridge Adapter`

说明：`wf_oa_bridge_record` 不是“外部集成状态附表”，而是平台侧桥接治理锚点。它至少要能回答“谁与谁绑定”“按哪版模板和签名证据发起”“平台已承接到哪一步”“当前补偿和人工收口处于什么状态”，其余厂商原生明细仍留在外部系统或审计证据域，不复制为平台第二真相源。

### 4.11.1 `wf_async_task_type_registry`

用途：运行控制面任务类型注册表，承接流程引擎域内异步任务类型、业务归属、触发模式和失败归类。

关键主键：`task_type_id`

关键字段：

- `task_type_code`：如 `TASK_TIMEOUT_SCAN`、`TASK_REMIND_DISPATCH`、`NOTIFICATION_RETRY`、`CONTRACT_WRITEBACK`、`OA_DISPATCH`、`OA_CALLBACK_REPLAY`、`SUMMARY_REFRESH`
- `owner_object_type`：`PROCESS_INSTANCE`、`APPROVAL_TASK`、`TASK_TIMER`、`OA_BRIDGE_RECORD`、`NOTIFICATION_EVENT`
- `trigger_mode`：`SCHEDULED_SCAN`、`EVENT_TRIGGERED`、`COMPENSATION_RETRY`
- `default_param_set_id`
- `failure_category`：`AUTO_RECOVERABLE`、`MANUAL_REQUIRED`、`DEGRADED_ALLOWED`
- `registry_status`：`DRAFT`、`ENABLED`、`DISABLED`、`ARCHIVED`
- `latest_published_at`、`latest_published_by`

关键索引 / 唯一约束：

- `uk_task_type_code(task_type_code)`
- `idx_registry_status(registry_status, trigger_mode)`

关联对象：

- 被 `wf_governance_param_set`、`wf_async_task_record` 引用
- 任务中心只消费 `task_type_code + default_param_set_id`，不拥有任务类型主数据

### 4.11.2 `wf_governance_param_set`

用途：运行控制面治理参数集，承接扫描周期、并发上限、批量大小、锁租约、重试策略和告警阈值。

关键主键：`param_set_id`

关键字段：

- `task_type_id`
- `param_set_version`
- `environment_code`：`DEV`、`TEST`、`PROD`
- `scan_interval_ms`
- `max_concurrent_instances`
- `scan_batch_size`
- `lock_ttl_ms`
- `max_retry_count`
- `backoff_base_ms`
- `backoff_max_ms`
- `alert_threshold_snapshot`
- `param_status`：`DRAFT`、`ACTIVE`、`SUPERSEDED`、`DISABLED`
- `effective_at`、`published_by`

关键索引 / 唯一约束：

- `uk_task_env_param_version(task_type_id, environment_code, param_set_version)`
- `uk_task_env_active(task_type_id, environment_code, active_param_flag)`，其中 `active_param_flag=1` 时只允许一条
- `idx_param_status(param_status, effective_at)`

关联对象：

- 引用 `wf_async_task_type_registry`
- 被 `wf_async_task_record.param_snapshot_ref` 引用为执行时参数快照

### 4.11.3 `wf_async_task_record`

用途：流程引擎域内异步任务记录，映射总平台任务中心 `platform_job` 或 `jobs` 资源，承接调度视角状态与业务归属。

关键主键：`async_task_id`

关键字段：

- `platform_job_id`
- `task_type_id`
- `task_type_code`
- `owner_object_type`：`PROCESS_INSTANCE`、`APPROVAL_TASK`、`TASK_TIMER`、`OA_BRIDGE_RECORD`、`NOTIFICATION_EVENT`
- `owner_object_id`
- `process_id`
- `task_id`
- `timer_id`
- `param_snapshot_ref`：指向 `wf_governance_param_set.param_set_id + param_set_version`
- `job_status`：`PENDING`、`CLAIMED`、`SUCCEEDED`、`FAILED`、`EXHAUSTED`、`CANCELED`
- `claimed_at`
- `claimed_by_executor`
- `claim_expires_at`
- `retry_count`
- `next_trigger_at`
- `last_error_code`、`last_error_message`
- `result_code`、`result_message`

关键索引 / 唯一约束：

- `uk_platform_job(platform_job_id)`
- `idx_claim_scan(job_status, next_trigger_at, claim_expires_at)`
- `idx_owner(owner_object_type, owner_object_id, job_status)`
- `idx_process_task(process_id, task_id, job_status)`

关联对象：

- 映射总平台任务中心 `platform_job` / `jobs`
- 关联 `wf_process_instance`、`wf_approval_task`、`wf_task_timer`、`wf_oa_bridge_record`

说明：`wf_async_task_record` 不替代审批动作事实。任务执行成功后的业务结果仍必须写入 `wf_approval_action`、`wf_task_timer`、`wf_oa_bridge_record` 或合同回写补偿对象。

### 4.11.4 `wf_notification_event_registry`

用途：通知事件注册表，承接流程引擎侧通知事件类型、触发源、默认模板和接收人解析规则。

关键主键：`notification_event_id`

关键字段：

- `event_code`：`TASK_CREATED`、`TASK_REMIND`、`TASK_TIMEOUT`、`PROCESS_COMPLETED`、`PROCESS_REJECTED`、`PROCESS_WITHDRAWN`、`PROCESS_TERMINATED`、`ESCALATION_TRIGGERED`
- `trigger_source_type`：`TASK_CREATED`、`TIMER_TRIGGERED`、`ACTION_FACT`、`INSTANCE_STATUS_CHANGED`
- `default_template_code`
- `variable_contract_id`
- `notification_policy_id`
- `recipient_resolver_rule`
- `event_status`：`DRAFT`、`ENABLED`、`DISABLED`、`ARCHIVED`
- `contract_version`

关键索引 / 唯一约束：

- `uk_event_code(event_code)`
- `idx_event_status(event_status, trigger_source_type)`

关联对象：

- 引用 `wf_notification_variable_contract`
- 引用 `wf_notification_policy`
- 通知中心只消费事件命令，不拥有事件语义主数据

### 4.11.5 `wf_notification_variable_contract`

用途：通知变量契约表，承接模板变量清单、必填规则、变量来源和契约版本。

关键主键：`variable_contract_id`

关键字段：

- `event_code`
- `contract_version`
- `variable_schema_snapshot`
- `required_variable_list`
- `optional_variable_list`
- `computed_variable_list`
- `data_source_snapshot`
- `sensitive_mask_rule`
- `contract_status`：`DRAFT`、`ACTIVE`、`SUPERSEDED`、`DISABLED`
- `published_at`、`published_by`

关键索引 / 唯一约束：

- `uk_event_contract_version(event_code, contract_version)`
- `uk_event_active_contract(event_code, active_contract_flag)`，其中 `active_contract_flag=1` 时只允许一条
- `idx_contract_status(contract_status, published_at)`

关联对象：

- 被 `wf_notification_event_registry` 和运行时通知命令引用

### 4.11.6 `wf_notification_policy`

用途：通知策略表，承接渠道优先级、降级顺序、合并键、合并窗口和补偿策略。

关键主键：`notification_policy_id`

关键字段：

- `event_code`
- `policy_version`
- `channel_priority_snapshot`
- `fallback_channel_snapshot`
- `merge_enabled`
- `merge_key_expression`
- `merge_window_ms`
- `priority_level`
- `retry_task_type_id`
- `policy_status`：`DRAFT`、`ACTIVE`、`SUPERSEDED`、`DISABLED`

关键索引 / 唯一约束：

- `uk_event_policy_version(event_code, policy_version)`
- `uk_event_active_policy(event_code, active_policy_flag)`，其中 `active_policy_flag=1` 时只允许一条
- `idx_policy_status(policy_status, priority_level)`

关联对象：

- 引用 `wf_async_task_type_registry` 中的通知补偿任务类型
- 由 `Workflow Collaboration Gateway` 组装运行时通知命令时读取

### 4.11.7 `wf_governance_audit_change`

用途：运行控制面治理审计变更表，记录任务类型、参数集、通知事件、变量契约、通知策略的版本变更与发布证据。

关键主键：`governance_change_id`

关键字段：

- `governance_object_type`：`TASK_TYPE_REGISTRY`、`PARAM_SET`、`NOTIFICATION_EVENT`、`VARIABLE_CONTRACT`、`NOTIFICATION_POLICY`
- `governance_object_id`
- `object_code`
- `before_version`
- `after_version`
- `change_action`：`CREATE`、`UPDATE`、`PUBLISH`、`DISABLE`、`ARCHIVE`
- `change_reason`
- `change_snapshot_ref`
- `changed_by`
- `changed_at`
- `approval_ref`

关键索引 / 唯一约束：

- `idx_governance_object(governance_object_type, governance_object_id, changed_at)`
- `idx_object_code(object_code, changed_at)`
- `idx_changed_by(changed_by, changed_at)`

关联对象：

- 关联运行控制面全部治理对象
- 与审计中心同步高等级配置审计事件

### 4.12 关于转办、催办、超时的落表原则

- 转办不单独建独立主表。
  原因：转办本质是“动作事实 + 新任务派生 + 原任务关闭”三个状态变化，
  其真相分别落在 `wf_approval_action` 与 `wf_approval_task`。
- 催办、超时共享 `wf_task_timer`，触发后的事实动作统一落
  `wf_approval_action`。
- 终止、撤回、异常恢复均落动作事实，不额外拆出平行状态表。

## 5. 运行时状态模型

### 5.1 定义状态边界

`wf_process_definition.definition_status` 建议收口为：

- `DRAFTING`：存在草稿，可编辑，不可运行
- `PUBLISHED`：存在可运行发布版本
- `DISABLED`：定义停用，不允许新发起
- `ARCHIVED`：历史归档，不再面向配置端作为活跃定义

说明：定义状态表达“定义主档可见性”，不替代版本状态。

### 5.2 版本状态边界

`wf_process_version.version_status` 建议收口为：

- `DRAFT`：可编辑草稿版本
- `PUBLISHED`：当前可运行版本
- `DISABLED`：不再接受新实例，但历史实例继续引用
- `ARCHIVED`：仅用于追溯

说明：实例一旦创建，始终绑定启动时版本，不因后续重新发布而漂移。

补充约束：版本只有在运行时快照、正式 `Validation Report` 引用、发布校验摘要和发布审计事件同时落定后，才允许进入 `PUBLISHED`。

### 5.3 实例状态边界

`wf_process_instance.instance_status` 建议收口为：

- `PENDING_START`：已受理但未完成路由与快照装载
- `RUNNING`：流程运行中
- `WAITING_CALLBACK`：`OA` 主路径下等待外部推进或回调
- `COMPLETED`：流程完成且结果为通过
- `REJECTED`：流程因驳回结束
- `WITHDRAWN`：发起方撤回结束
- `TERMINATED`：管理员或系统终止结束
- `FAILED`：进入人工介入前的异常停滞态

状态边界原则：

- 只有实例主状态进入终态时，`finished_at` 才写入
- `FAILED` 不是业务结论，只表示运行时未能自动继续
- `WAITING_CALLBACK` 仅用于 `OA` 路径，平台本地运行路径不使用该态

### 5.4 任务状态边界

`wf_approval_task.task_status` 建议收口为：

- `READY`：任务已生成，待领取或待展示
- `PENDING_ACTION`：已明确到人，待处理
- `COMPLETED`：已完成并形成动作结果
- `TRANSFERRED`：原任务被转办关闭
- `REJECTED`：任务以驳回结果结束
- `TIMEOUT`：超过时限且已触发超时结果
- `CANCELED`：因实例结束、并行分支收敛失败或系统取消而关闭

说明：抄送类任务可用 `task_type=COPY` 区分，不新增平行状态体系。

### 5.5 动作状态边界

`wf_approval_action` 不使用复杂状态机，动作记录一经写入即为事实。

- `action_type` 表达动作语义
- `action_result` 表达动作结果，如 `ACCEPTED`、`REJECTED`、`NOOP`
- 幂等冲突时不新增第二条事实，而返回首次动作结果

动作表的边界是“不可回滚的事实流”，实例与任务状态由其派生。

## 6. 节点与组织架构绑定模型

### 6.1 绑定类型

节点绑定类型固定为三类：

- `USER`：直接绑定单个或多个用户
- `ORG_UNIT`：绑定部门 / 组织单元，由运行时按规则选出参与人
- `ORG_RULE`：绑定规则，由解析器访问组织底座计算参与人

每个审批节点至少存在一条有效绑定记录，不允许只有抽象角色名而无组织落点。

### 6.2 存储表达

`wf_node_binding` 的建议表达如下：

- `binding_type=USER` 时，`binding_value` 存 `user_id`
- `binding_type=ORG_UNIT` 时，`binding_value` 存 `org_unit_id`
- `binding_type=ORG_RULE` 时，`binding_value` 存 `org_rule_code` 或规则主键
- `resolver_config` 补充角色过滤、岗位过滤、是否取负责人、是否向上递归等参数；其中流程引擎消费侧过滤扩展槽统一命名为 `resolver_config.consumer_filter_spec`
- `fallback_strategy` 定义无人可选时的兜底动作，如升级、回退、失败待人工处理

补充约束：`binding_value` 可以继续使用 `org_rule_code` 或规则主键作为设计态引用，但一旦流程版本发布，运行时实际使用的 `org_rule_id + rule_version_no` 必须冻结到 `wf_process_version.version_snapshot` 中；已发布流程版本后续不自动跟随最新规则版本。

### 6.3 运行时解析输出

`Org Binding Resolver` 对每个节点输出三类结果：

- `candidate_user_list`：候选人集合
- `resolved_assignee_list`：本次真正生成任务的执行人集合
- `resolver_snapshot`：解析时使用的组织版本摘要、规则参数和过滤证据

解析约束如下：

- 必须过滤停用、离岗、无审批权限、跨组织越权用户
- 必须保留解析证据，便于后续审计与复盘
- 解析结果允许缓存，但节点进入时必须把正式求值结果冻结为 `ParticipantSnapshot` 并持久化到 `wf_participant_snapshot`；任务侧 `candidate_snapshot` / `resolver_snapshot` 只作为该快照的执行投影与引用，避免组织后续变化导致历史任务漂移

### 6.4 组织变更对实例的影响

- 定义态和草稿态节点绑定跟随组织底座实时读取
- 已创建的任务以 `wf_participant_snapshot` 锚定的正式快照为准，任务内 `candidate_snapshot` 和 `resolver_snapshot` 只承接执行投影
- 尚未生成任务的后续节点，进入节点时重新解析组织，但仍使用流程发布时已冻结的规则版本
- 若组织变更导致解析为空，实例进入 `FAILED` 或按节点兜底策略升级，不静默跳过

补充边界：规则版本升级后，流程引擎只对引用该规则的流程定义重新执行发布级预校验，用于判断“当前已发布流程版本是否仍可继续被接受为正式版本”以及“是否需要重新发布新流程版本来采纳新规则”。该动作不会直接改写已发布流程版本、运行中实例或尚未进入节点的规则版本绑定。

## 7. 并行、会签、转办、催办、超时、终止与异常处理

### 7.1 并行节点

- 并行分叉 / 收敛拓扑只由 `node_type=PARALLEL_GATEWAY` 建模，版本内通过 `join_group_key` 完成分叉节点与收敛节点配对。
- `participant_mode=PARALLEL` 只用于单个审批节点内部的多参与人并发处理，不得拿来表达流程拓扑分叉；凡是多分支路径，都必须显式使用 `PARALLEL_GATEWAY`。
- 进入并行分叉节点时，`Runtime Orchestrator` 为每个并行分支生成独立任务集；收敛阶段则统一落到对应 `wf_node_aggregation` 治理对象。
- 收敛条件由节点配置决定，建议支持：
  - `ALL`：全部分支完成才收敛
  - `ANY`：任一分支命中通过即收敛
  - `PERCENTAGE`：按比例收敛
- 未命中收敛条件前，实例 `current_node_id` 仍停留在并行组语义节点，而非任一子任务。

### 7.2 会签节点

- 会签是审批节点的一种参与模式，不另起独立实例。
- 每个会签参与人生成独立 `wf_approval_task`。
- 节点层通过 `wf_node_aggregation` 维护会签聚合结果：已提交数、通过数、驳回数、未完成数以及最新判定快照引用。
- 收敛规则建议支持：
  - 全员通过
  - 一票否决
  - 通过比例阈值
- `COUNTERSIGN_ADD` 只允许在节点仍处于会签打开态时追加任务。
- 当 `convergence_mode=PERCENTAGE` 且 `basis=SUBMITTED` 时，发布态必须同时声明“首次提交后禁止动态追加”；一旦 `submitted_count > 0`，不得再执行 `COUNTERSIGN_ADD`，避免基数在判定过程中漂移。

### 7.3 转办

- 转办通过 `TRANSFER` 动作触发。
- 原任务状态改为 `TRANSFERRED`，并生成新任务。
- 新任务继承原任务的 `process_id`、`node_id`、`due_at` 和解析快照摘要，
  同时记录 `origin_task_id` 与 `transfer_from_action_id`。
- 转办不改变节点定义绑定，只改变当前任务承接人链路。
- 是否允许连续转办、跨部门转办、转办后回退，由权限与节点策略共同约束。

### 7.4 催办

- 催办不改变实例和任务主状态。
- 催办只产生 `REMIND` 动作、刷新 `wf_task_timer` 触发计数，并通过通知中心分发。
- 同一任务催办必须受频控约束，例如最小催办间隔、每日上限、实例总上限。
- `OA` 主路径下的催办只对平台摘要、通知与桥接记录生效，不默认模拟 `OA` 原生催办。

### 7.5 超时

- 超时由 `wf_task_timer` 驱动，不在请求线程内轮询。
- 节点超时治理统一冻结在 `wf_node_definition.time_limit_config`，正式字段口径为 `timeout_at` / `timeout_duration`、`timeout_strategy`、`timeout_params`。
- `timeout_strategy` 正式枚举统一为：
  - `ESCALATE`：转入人工裁定或升级路径，聚合节点进入 `AWAITING_MANUAL_DECISION`
  - `FORCE_PASS`：把超时对象按通过结果纳入聚合判定
  - `FORCE_REJECT`：把超时对象按驳回结果纳入聚合判定；对单人审批节点也可直接形成驳回结论
  - `EXCLUDE_FROM_BASE`：将超时对象移出聚合基数后重算阈值，仅允许用于并行收敛或会签聚合节点
- `FORCE_PASS` 仅允许用于并行收敛或会签聚合节点；单人审批节点只允许 `ESCALATE` 或 `FORCE_REJECT`。
- 超时结果必须写入 `wf_approval_action`，不得只更新任务状态。

### 7.6 终止与撤回

- `TERMINATE` 是强制终止，面向管理员或系统补偿逻辑。
- `WITHDRAW` 是发起方撤回，需满足“尚未越过撤回边界”的前置条件。
- 终止或撤回时，所有未完成任务统一关闭为 `CANCELED`。
- `OA` 主路径下若平台已请求终止但外部状态未确认，实例先进入
  `WAITING_CALLBACK` 或异常桥接态，不直接宣告本地完成。

### 7.7 异常处理

内部异常按四类处理：

- 配置异常：如节点无绑定、拓扑断裂，阻断发布
- 运行异常：如组织解析为空、并行收敛异常，实例进入 `FAILED`
- 集成异常：如 `OA` 发起失败、回调处理失败，进入重试与人工介入链路
- 协作异常：如合同回写失败、通知失败，记录补偿任务但不回滚动作事实

## 8. 与 `OA` 桥接的内部适配设计

### 8.1 桥接定位

- `OA` 仍是默认主审批路径。
- 流程引擎内部仍要创建平台侧实例主记录，保证统一摘要、审计与业务回写。
- `OA Bridge Adapter` 只负责平台内部桥接和状态承接，不负责设计 `OA` 表结构、
  页面或原生流程规则。
- `wf_oa_bridge_record` 在本层按治理分面承接绑定、映射、承接、补偿四类真相；具体模板结构、签名证据和补偿编排细节继续下沉专项设计。

### 8.2 发起链路

1. 平台根据定义与承接策略决定 `approval_mode=OA`。
2. 创建 `wf_process_instance`，状态置为 `PENDING_START` 或 `WAITING_CALLBACK`。
3. 根据版本节点和绑定解析结果生成 `OA` 所需参与人映射与业务载荷，并确定映射模板版本与签名证据。
4. 调用 `OA` 发起后写入 `wf_oa_bridge_record`，同时固化绑定真相、映射真相与首个补偿状态。
5. 成功则记录 `oa_instance_id`，实例进入 `WAITING_CALLBACK`。
6. 失败则保留实例与桥接记录，进入重试或人工介入，不丢失受理事实。

### 8.3 回调承接链路

- 回调先按 `oa_instance_id + callback_event_id` 做幂等判重。
- 将外部节点状态归一为平台摘要事件，例如：待审批、通过、驳回、终止、异常。
- 对平台可承接的动作写入 `wf_approval_action` 或桥接事实摘要。
- 更新 `wf_process_instance` 与 `wf_oa_bridge_record`。
- 触发合同状态回写、通知和审计。

### 8.4 状态承接原则

- 平台对 `OA` 采用“摘要承接”而非“全量镜像”。
- `OA` 原生审批记录不是平台第二真相源的复制目标。
- 平台至少持有以下桥接治理事实：
  - 绑定真相：平台实例、业务对象、`oa_flow_code`、外部实例标识
  - 映射真相：模板版本、请求校验摘要、参与人映射摘要、签名治理证据
  - 承接真相：最近回调事件、当前节点摘要、当前审批人摘要、最终审批结论
  - 补偿真相：补偿状态、异常分类、重试进度、人工介入标记

### 8.5 `OA` 与本地引擎切换边界

- 一次实例启动后，不在正常路径下动态切换 `OA` / `CMP` 执行模式。
- 所谓“`CMP_TAKEOVER`”只用于启动时判断 `OA` 不满足要求而直接走平台引擎。
- 已发起的 `OA` 实例若桥接异常，只允许做补偿、终止、人工确认，不自动迁移为
  平台本地继续执行，以避免双实例失真。

## 9. 与其他模块的内部协作方式

### 9.1 合同主档

- 流程引擎只持有 `contract_id` 与审批摘要字段。
- 审批启动、完成、驳回、撤回、终止等关键事件通过协作网关回写合同状态或时间线。
- 回写失败进入补偿任务，但不回滚审批动作事实。

### 9.2 审计中心

- 定义发布、版本停用、节点绑定变更、承接模式切换记高等级审计。
- 审批动作、转办、催办、超时、终止、异常恢复记业务审计。
- `OA` 发起失败、回调异常、合同回写失败记集成审计。

### 9.3 通知中心

- 流程引擎负责通知事件语义、接收人解析、模板变量契约、渠道优先级声明与合并策略声明；这些正式治理对象统一由 `Runtime Control Plane` 承接，并经 `Workflow Collaboration Gateway` 输出。
- 通知中心负责模板资产、模板渲染、渠道路由、发送执行与发送回执。
- 通知失败不影响审批动作事实，但会产生补偿任务和告警。

### 9.4 任务中心

- `wf_approval_task` 是流程域任务真相。
- 任务中心承接统一待办视图、任务调度、超时扫描、重试执行器。
- 流程引擎向任务中心输出标准任务元数据，不要求任务中心理解流程内部拓扑。

## 10. 缓存、锁、幂等与并发控制

### 10.1 缓存范围

允许缓存：

- 已发布定义与版本快照
- 节点绑定解析结果的短期缓存
- 实例当前摘要只读视图
- `OA` 桥接回调去重短态

不允许只存在缓存：

- 实例主状态
- 任务主状态
- 动作事实
- 转办链路
- 计时器正式状态

### 10.2 锁粒度

- 发布锁：`workflow:def:{definition_id}`
- 实例启动锁：`workflow:contract:{contract_id}`
- 任务动作锁：`workflow:task:{task_id}`
- 回调处理锁：`workflow:oa:{oa_instance_id}:{callback_event_id}`

锁规则：

- 锁只做短期互斥
- 事务提交结果仍以数据库唯一约束和 `row_version` 为准
- 锁失效后必须允许依靠数据库状态重放恢复

### 10.3 幂等控制

- 创建实例按 `contract_id + definition_id + business_action_key` 控制幂等
- 审批动作按 `process_id + idempotency_key` 控制幂等
- `OA` 回调按 `oa_instance_id + callback_event_id` 控制幂等
- 催办按 `task_id + remind_window_key` 控制节流幂等

### 10.4 并发更新原则

- `wf_process_instance` 和 `wf_approval_task` 均使用 `row_version` 乐观并发控制
- 任务完成时必须校验“任务仍处于可处理状态”
- 并行 / 会签聚合必须在事务内同时更新聚合计数和实例推进结果
- 合同回写采用“动作事实先落库，业务回写异步补偿”的顺序，避免长事务跨模块锁表

## 11. 任务重试、补偿与人工介入边界

### 11.1 异步任务类型

流程引擎侧至少需要以下异步任务：

- `OA_DISPATCH`
- `OA_CALLBACK_REPLAY`
- `CONTRACT_WRITEBACK`
- `TASK_TIMEOUT_SCAN`
- `TASK_REMIND_DISPATCH`
- `SUMMARY_REFRESH`
- `NOTIFICATION_RETRY`

### 11.2 重试原则

- 外部系统错误、网络超时、中心协作失败允许自动重试
- 组织绑定为空、配置缺失、数据校验失败不自动重试，直接进入人工介入
- 重试必须记录尝试次数、最近错误、下次执行时间
- 达到上限后进入 `FAILED` 或人工待处理队列

### 11.3 补偿原则

- 补偿针对“副作用未完成”，不回滚已确认的审批事实
- 合同回写失败时补偿合同侧，不删除动作记录
- 通知失败时补偿消息发送，不改写任务结果
- `OA` 回调处理失败时补偿摘要同步和业务回写，不重复消费已成功动作

### 11.4 人工介入边界

以下场景必须明确允许人工介入：

- 节点绑定解析为空且无自动兜底
- `OA` 状态与平台桥接状态长期不一致
- 合同主档回写多次失败
- 并行 / 会签聚合结果出现数据不一致
- 历史数据迁移或摘要重建时发现源事实缺失

人工介入只允许修复控制状态、补录映射或触发重放，不允许手工改写既有审批事实。

## 12. 审计、日志、指标与恢复边界

### 12.1 审计边界

- 审计对象至少覆盖定义、版本、实例、任务、动作、聚合对象、桥接记录
- 审计字段至少包括操作者、来源系统、对象主键、结果、发生时间、追踪号
- 审计中心保存正式留痕，流程引擎内部日志只做运行诊断

### 12.2 日志边界

- 统一使用结构化日志，关键字段包括 `trace_id`、`process_id`、`task_id`、
  `contract_id`、`oa_instance_id`
- 错误日志必须区分配置错误、外部错误、并发冲突、数据损坏四类
- 不在日志中输出完整敏感审批意见和原始附件内容

### 12.3 指标边界

建议最少暴露以下指标：

- 定义发布成功率
- 实例启动成功率
- 当前运行中实例数
- 任务超时数
- 转办次数
- 会签收敛耗时
- `OA` 发起失败率与回调失败率
- 合同回写补偿积压量

### 12.4 恢复边界

- 缓存丢失后可由数据库重建定义缓存、实例摘要缓存和回调去重短态
- 进程重启后由 `wf_task_timer` 与异步任务表恢复待触发任务
- 打开态并行 / 会签节点可由 `wf_node_aggregation` 的 `rule_snapshot`、`participant_snapshot`、`counter_snapshot` 结合 `last_fact_action_id` 之后的动作事实重放恢复
- 实例终态、任务终态、动作事实必须仅依赖数据库即可重放摘要
- 聚合判定审计与争议回放统一引用 `latest_decision_action_id + latest_decision_snapshot_ref`
- 对于 `OA` 主路径，若平台在回调处理中断，可基于桥接记录重新拉齐摘要状态

## 13. 继续下沉到后续专项设计或实现的内容

以下内容已经超出本层 `Detailed Design`，需继续下沉到专项设计或实现阶段：

- 流程 DSL 正式治理对象、分层校验器与发布校验证据链。中文说明： [workflow-dsl-and-validator-design.md](./special-designs/workflow-dsl-and-validator-design.md)
- 画布保存协议、前端节点属性模型、版本比较视图。中文说明： [workflow-canvas-protocol-design.md](./special-designs/workflow-canvas-protocol-design.md)
- `ORG_RULE` 规则字典、岗位 / 角色过滤语言与求值器实现。中文说明： [org-rule-evaluator-design.md](./special-designs/org-rule-evaluator-design.md)
- 并行与会签聚合的精确算法参数、百分比取整规则、回退细节。中文说明： [parallel-and-countersign-aggregation-design.md](./special-designs/parallel-and-countersign-aggregation-design.md)
- `OA` 字段映射模板、签名算法、主动拉取补偿策略。中文说明： [oa-bridge-mapping-and-compensation-design.md](./special-designs/oa-bridge-mapping-and-compensation-design.md)
- 任务执行器并发模型、扫描周期、状态机与通知模板变量、渠道优先级、消息合并策略。中文说明： [task-executor-and-notification-runtime-design.md](./special-designs/task-executor-and-notification-runtime-design.md)
- 历史实例迁移、审批摘要重建工具与管理端人工干预界面。中文说明： [instance-migration-and-admin-intervention-design.md](./special-designs/instance-migration-and-admin-intervention-design.md)

## 14. 本文结论

本设计将流程引擎子模块收口为“定义与版本治理 + 运行时编排 +
组织绑定解析 + 任务与动作管理 + `OA` 桥接 + 运行时控制”六个核心内部面。

在此前提下：

- 默认主审批路径仍优先走 `OA`
- 平台流程引擎在一期具备正式承接能力
- 每个节点都必须落到组织架构绑定
- 流程引擎只通过 `contract_id` 绑定业务对象，不拥有合同主档
- 审批事实、任务状态、桥接状态、补偿状态均可独立落库与恢复

本文到此为止，不继续展开对外 API 契约、总平台架构复述或实施计划。

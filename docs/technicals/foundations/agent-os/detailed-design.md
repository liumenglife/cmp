# Agent OS 子模块 Detailed Design

## 1. 文档说明

本文档是 `Agent OS` 子模块的第一份正式 `Detailed Design`。
它在以下文档已经收口边界的前提下，继续下沉 `Agent OS`
内部实现层设计：

### 1.1 输入

- 上游需求基线：[`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- 总平台架构边界：[`Architecture Design`](../../architecture-design.md)
- 总平台接口边界：[`API Design`](../../api-design.md)
- 总平台共享内部边界：[`Detailed Design`](../../detailed-design.md)
- 本模块架构边界：[`Agent OS Architecture Design`](./architecture-design.md)
- 本模块接口边界：[`Agent OS API Design`](./api-design.md)

### 1.2 输出

- 本文：[`Agent OS Detailed Design`](./detailed-design.md)
- 为后续该子模块专项实现、测试设计、运维治理提供内部实现基线

### 1.3 阅读边界

本文只描述 `Agent OS` 内部实现层设计，不展开以下内容：

- 不复述一期需求范围、验收口径、业务收益或实施排期
- 不重写总平台或子模块架构总览
- 不重写对外 API 资源清单、字段契约或错误码全集
- 不写具体 Prompt 正文、SDK 调用代码、类级实现细节
- 不把本文件写成 `Implementation Plan`

## 2. 设计目标与约束落点

`Agent OS` 是平台 AI 能力底座 / 运行时操作系统。
本模块详细设计的目标不是把模型接入讲清，而是把运行时如何稳定、
可控、可恢复、可审计地工作讲清。

### 2.1 目标落点

- 让业务模块只面向 `Agent OS` 能力边界发起任务，不直接绑定具体模型 /
  `Provider`
- 让通用 Agent 人格先于专用 Agent 人格，形成统一行为底座
- 让提示词工程采用“静态底座 + 动态注入”，并保证静态底座输出约束适用于全部 Agent
- 让 `QueryEngine Runtime Loop` 成为内部执行主语义，而不是“调一次模型接口”的薄封装
- 让模型只是工具，不是运行时中心
- 让 AI 输入既包括业务模块输入，也包括环境输入
- 让多 Agent 协作采用跨 Session 委派，而不是同一长上下文内反复换人格
- 让记忆、反思、工具调用、人工确认、结果回写全部进入统一审计链路

内部设计以合同管理平台这类企业业务系统为主锚点：运行对象可承接合同起草 / 审查、审批流辅助、文档比对、签署状态解释、履约风险提示、风控预警和运维审计回放，但 `Agent OS` 不拥有合同、审批、签署或履约主档，只通过业务对象引用、工具观察和结果回写与业务模块协作。

### 2.2 约束落点

- 存储基线遵循总平台约束：`MySQL` 为正式真相源，`Redis` 仅作增强层
- 运行时必须保留模型 / `Provider` 抽象，业务模块永远不接触厂商私有 SDK 语义
- 高风险动作、越权动作、正式结果放行必须经过人工确认闸门
- 所有异步能力都必须可重试、可补偿、可恢复、可审计
- 委派、工具调用、Prompt 快照、记忆沉淀都必须保留可追踪对象主键
- 任何 AI 产出都只能作为平台辅助结果，不能绕过业务流程直接成为最终法律或审批结论

## 3. 模块内部拆分

### 3.1 拆分原则

- 按运行时职责拆分，不按单一技术手段拆分
- 把稳定边界收口到内部服务接口，避免业务模块直接拼装 Prompt、直连模型、私建记忆
- 把高副作用能力放在显式闸门之后，把高频只读能力前移到缓存与快照层

### 3.2 内部模块清单

#### 3.2.1 `task-ingress`

- 统一接收业务模块输入与环境输入
- 负责任务归一化、幂等校验、风险初筛、来源登记
- 产出 `AgentTask` 与首个 `AgentRun`

#### 3.2.2 `persona-kernel`

- 管理通用 Agent 人格与专用 Agent 人格的继承关系
- 负责能力白名单、工具白名单、输出约束继承
- 决定当前运行实例采用哪个人格组合

#### 3.2.3 `prompt-assembly`

- 维护静态底座提示词版本
- 组装动态注入片段，包括任务上下文、环境事件、记忆摘要、工具可用性、确认要求
- 产出可追踪的 Prompt 快照，不直接对外暴露 Prompt 正文

#### 3.2.4 `context-governor`

- 负责每轮上下文预算评估、动态注入裁剪、工具结果保留、压缩触发与压缩失败降级
- 内置微压缩、事实压缩、完整压缩与自动熔断，避免上下文窗口成为被动容器
- 强制工具调用与工具结果成对保留，压缩前执行记忆冲洗，失败时生成补偿作业或阻断高风险压缩
- 向 `prompt-assembly` 输出可审计的上下文治理快照，而不是直接拼接完整历史

#### 3.2.5 `runtime-core`

- 实现 `QueryEngine Runtime Loop`
- 负责步骤推进、状态检查点、终止条件、失败分支、结果收敛
- 决定何时调用工具、何时等待观察、何时申请人工确认、何时发起委派

#### 3.2.6 `tool-router`

- 把规则、检索、文件、数据库、外部 API、模型 / `Provider` 统一抽象为工具
- 负责路由、超时、隔离级别、参数校验、调用审计、结果归一化
- 不允许业务模块直接绕过该层使用模型或外部工具

#### 3.2.7 `provider-abstraction`

- 抽象文本生成、结构化提取、嵌入、重排、长上下文推理等模型能力
- 管理 `Provider` 配置、路由策略、降级顺序、失败黑名单、配额控制
- 对 `runtime-core` 仅暴露统一模型能力接口，不暴露厂商字段

#### 3.2.8 `memory-governor`

- 管理短期运行记忆、长期经验记忆、怀疑式记忆标签
- 负责记忆候选生成、可信度评估、去重、失效、冻结与召回
- 不把所有输出自动写入长期记忆

#### 3.2.9 `auto-dream-daemon`

- 异步整理既往运行中的可复用模式、失败教训、工具偏好、委派模式
- 输出候选记忆或策略建议，仍需经过记忆治理链路确认后才能进入正式记忆

#### 3.2.10 `human-gate`

- 管理人工确认单创建、催办、过期、决策回写
- 支持结果放行、危险动作授权、委派批准、权限升级批准等场景

#### 3.2.11 `delegation-coordinator`

- 管理跨 Session 委派
- 负责子运行实例创建、最小必要上下文裁剪、委派回收、主结果合并
- 保证多 Agent 协作是显式对象关系，而不是隐式上下文拼接

#### 3.2.12 `audit-observability`

- 统一写入审计事件、运行日志摘要、指标聚合、异常告警与恢复锚点
- 负责对任务、运行、工具、确认、委派、记忆、自动反思建立统一可观测性

### 3.3 内部模块协作主链路

1. `task-ingress` 创建任务与主运行实例。
2. `persona-kernel` 解析人格组合与工具范围。
3. `context-governor` 评估上下文预算、压缩策略、记忆冲洗和工具调用 / 结果配对完整性。
4. `prompt-assembly` 生成本轮 Prompt 快照。
5. `runtime-core` 推进 `QueryEngine Runtime Loop`。
6. `tool-router` 按需调度工具，模型能力经 `provider-abstraction` 调用。
7. `memory-governor` 提供记忆摘要，并在每轮结束后评估候选记忆。
8. 需要人工介入时由 `human-gate` 生成确认单。
9. 需要拆分任务时由 `delegation-coordinator` 发起跨 Session 委派。
10. `audit-observability` 全程记录事件、指标与恢复锚点。

## 4. 核心物理表设计

### 4.1 建表总原则

- 全部主表使用字符串主键，便于跨模块与跨 Session 追踪
- 全部主表统一包含基础字段：`created_at`、`created_by`、`updated_at`、`updated_by`、`is_deleted`
- 正式状态落 `MySQL`，短态、锁、幂等前置命中落 `Redis`
- 结果文本、上下文快照、工具原始结果等大字段按“摘要入主表 + 正文入扩展字段 / 对象存储引用”处理，避免主表无限膨胀

### 4.2 `ao_agent_task`

- 用途：`Agent OS` 统一任务主表，承接业务模块输入和环境输入归一化后的正式任务对象
- 主键：`task_id`
- 关键字段：
  - `task_type`：任务类型
  - `task_source`：`BUSINESS_MODULE`、`PLATFORM_EVENT`、`MANUAL_OPERATOR`
  - `requester_type`、`requester_id`
  - `persona_code`：期望专用 Agent，可为空
  - `general_persona_code`：实际采用的通用人格代码
  - `task_status`：`ACCEPTED`、`READY`、`RUNNING`、`WAITING_HUMAN_CONFIRMATION`、`SUCCEEDED`、`FAILED`、`CANCELLED`
  - `risk_level`
  - `business_module`、`object_type`、`object_id`
  - `current_run_id`、`final_result_id`
  - `idempotency_key`
  - `trace_id`
- 关键索引 / 约束：
  - `uk_task_idempotency(task_source, requester_id, idempotency_key)`
  - `idx_task_status(task_status, updated_at)`
  - `idx_task_object(object_type, object_id, created_at)`
  - `idx_task_trace(trace_id)`
- 关联对象：关联 `ao_agent_run`、`ao_agent_result`、`ao_human_confirmation`、`ao_delegation`、`ao_environment_event`

### 4.3 `ao_agent_run`

- 用途：任务的运行实例主表，表达一次实际执行链路；委派子运行也落该表
- 主键：`run_id`
- 关键字段：
  - `task_id`
  - `parent_run_id`：主运行为空，委派子运行指向父运行
  - `session_id`
  - `agent_code`、`general_persona_code`、`persona_code`
  - `run_status`：`PENDING`、`RUNNING`、`WAITING_TOOL`、`WAITING_HUMAN_CONFIRMATION`、`WAITING_DELEGATION_RETURN`、`SUCCEEDED`、`FAILED`、`CANCELLED`
  - `runtime_state`：当前 `QueryEngine` 阶段快照，取值以主状态模型为准，如 `INGRESS_PENDING`、`CONTEXT_GOVERNING`、`PROMPT_ASSEMBLING`、`BUDGET_CHECKING`、`TOOL_DECIDING`、`OBSERVATION_NORMALIZING`、`FINALIZING`
  - `loop_count`
  - `latest_prompt_snapshot_id`
  - `latest_checkpoint_summary`
  - `failure_code`、`failure_reason`
  - `started_at`、`finished_at`
  - `version_no`
- 关键索引 / 约束：
  - `idx_run_task(task_id, created_at)`
  - `idx_run_parent(parent_run_id, created_at)`
  - `idx_run_status(run_status, updated_at)`
  - `uk_run_session(session_id)`
- 关联对象：归属 `ao_agent_task`；关联 `ao_tool_invocation`、`ao_prompt_snapshot`、`ao_human_confirmation`、`ao_delegation`、`ao_agent_audit_event`

### 4.4 `ao_agent_result`

- 用途：运行实例产出的正式结果对象，供业务模块消费，不直接暴露 Provider 原始输出
- 主键：`result_id`
- 关键字段：
  - `task_id`、`run_id`
  - `result_status`：`SUCCEEDED`、`PARTIAL`、`FAILED`、`REJECTED_BY_HUMAN`、`SUPERSEDED`
  - `result_type`：`ANSWER`、`SUMMARY`、`STRUCTURED_EXTRACTION`、`ACTION_PLAN` 等
  - `output_text_summary`
  - `output_payload_json`
  - `citation_payload_json`
  - `human_confirmation_required`
  - `confirmation_status_snapshot`
  - `writeback_status`：`PENDING`、`WRITTEN_BACK`、`SKIPPED`、`FAILED`
  - `writeback_target_type`、`writeback_target_id`
  - `provider_result_digest`
- 关键索引 / 约束：
  - `idx_result_task(task_id, created_at)`
  - `idx_result_run(run_id, created_at)`
  - `idx_result_status(result_status, writeback_status)`
- 关联对象：归属 `ao_agent_task`、`ao_agent_run`；可关联 `ao_human_confirmation`

### 4.5 `ao_human_confirmation`

- 用途：人工确认单主表，承接结果放行、危险动作授权、委派批准、权限升级批准
- 主键：`confirmation_id`
- 关键字段：
  - `task_id`、`run_id`、`result_id`
  - `confirmation_type`
  - `confirmation_source`
  - `confirmation_status`：`CREATED`、`PENDING`、`IN_REVIEW`、`DECIDED`、`EXPIRED`、`CANCELLED`、`REVOKED`
  - `target_ref`
  - `impact_scope`
  - `trigger_reason`
  - `proposed_action_summary`
  - `evidence_bundle_ref`
  - `decision_policy_snapshot`
  - `decision_action`：`REJECT`、`APPROVE`、`APPROVE_ONCE`、`APPROVE_IN_SCOPE`、`REQUEST_CHANGES`
  - `decision_comment`
  - `decided_by`、`decided_at`
  - `expires_at`
  - `resume_policy`：批准后恢复、驳回后终止、要求修改后重跑等
- 关键索引 / 约束：
  - `idx_confirmation_task(task_id, created_at)`
  - `idx_confirmation_run(run_id, confirmation_status)`
  - `idx_confirmation_pending(confirmation_status, expires_at)`
- 关联对象：关联任务、运行、结果；`target_ref` 可正式指向结果、动作提案、委派回收项或例外授权申请；被 `ao_agent_audit_event` 审计

### 4.6 `ao_delegation`

- 用途：跨 Session 委派主表，表达父运行与子运行的正式关系
- 主键：`delegation_id`
- 关键字段：
  - `task_id`
  - `parent_run_id`
  - `delegated_run_id`
  - `delegated_agent_code`
  - `delegation_goal`
  - `delegation_context_summary`
  - `delegation_status`：`CREATED`、`DISPATCHED`、`RUNNING`、`RETURNED`、`MERGED`、`RECALLED`、`FAILED`、`CANCELLED`
  - `merge_policy`：摘要合并、结构化结果替换、人工挑选后合并
  - `latest_return_id`
  - `returned_at`
- 关键索引 / 约束：
  - `idx_delegation_parent(parent_run_id, created_at)`
  - `idx_delegation_task(task_id, created_at)`
  - `idx_delegation_status(delegation_status, updated_at)`
  - `uk_delegated_run(delegated_run_id)`
- 关联对象：连接父 `ao_agent_run` 与子 `ao_agent_run`；通过 `latest_return_id` 关联最近一次正式回收结论

### 4.7 `ao_delegation_return`

- 用途：委派回收结论主表，正式承接成功回收、部分成功回收、失败回收与冲突回收
- 主键：`delegation_return_id`
- 关键字段：
  - `delegation_id`
  - `return_status`：`SUCCEEDED`、`PARTIAL`、`FAILED`、`CONFLICTED`、`RECALLED`
  - `returned_result_id`
  - `return_summary`
  - `goal_match_level`
  - `merge_recommendation`
  - `requires_human_confirmation`
  - `conflict_bundle_ref`
  - `failure_code`、`failure_reason`
  - `next_action`
  - `returned_at`
- 关键索引 / 约束：
  - `idx_delegation_return_delegation(delegation_id, returned_at)`
  - `idx_delegation_return_status(return_status, returned_at)`
- 关联对象：归属 `ao_delegation`；可关联 `ao_agent_result`、`ao_human_confirmation` 与冲突证据包

内部态与 API 对外态映射说明：

- `ao_delegation.delegation_status` 是内部治理真相源，服务于调度、补偿、召回控制、审计与恢复；它允许保留比 API 更细的过程态。
- `Delegation API` 只暴露较粗粒度状态，是为了让调用方关注“是否已发起、是否仍在执行、是否已回收、是否已合并、是否已结束取消”，而不是绑定内部调度实现细节。
- 因此，`DISPATCHED`、`RECALLED`、`FAILED` 这类状态只作为内部治理态存在，不要求一一原样暴露到 API 枚举。

建议投影规则：

| 内部 `delegation_status` | API 对外投影 | 说明 |
| --- | --- | --- |
| `CREATED` | `CREATED` | 委派关系已建立，但子运行尚未进入稳定执行态。 |
| `DISPATCHED` | `RUNNING` 或 `WAITING_RECALL` | 已完成下发，进入子运行接管窗口；API 不区分“刚下发”和“已实际执行”，默认按进行中暴露。若查询视图需要强调“父运行正在等待子运行返回且仍可召回”，可投影为 `WAITING_RECALL`。 |
| `RUNNING` | `RUNNING` 或 `WAITING_RECALL` | 子运行正在执行；是否展示为 `WAITING_RECALL` 取决于 API 视图是否要突出“等待回收 / 可召回”的外部语义。 |
| `RETURNED` | `RETURNED` | 子运行结果或失败摘要已回收到父运行，但尚未完成最终合并。 |
| `MERGED` | `MERGED` | 子运行产出已被父运行正式接纳并完成合并。 |
| `RECALLED` | `CANCELLED` | 召回是内部治理动作，对外统一视为该次委派已被取消结束。 |
| `FAILED` | `RETURNED` 配合失败摘要 | 委派失败不新增 API 状态；对外通过 `RETURNED` + `result_summary` / 结果摘要中的失败标记暴露，让调用方知道“子运行已回收，但回收的是失败结论”，避免 API 枚举继续膨胀。 |
| `CANCELLED` | `CANCELLED` | 委派在内部被直接取消且不再继续推进。 |

- 其中 `DISPATCHED`、`RECALLED`、`FAILED` 都属于内部治理态：前者用于区分“关系已建立”与“已实际下发”，后两者用于表达召回收口和失败补偿，不要求调用方理解内部补偿路径。
- `WAITING_RECALL` 是 API 查询视图上的投影态，不要求在 `ao_delegation` 主表中单独持久化同名状态；实现上可由 `delegation_status in (DISPATCHED, RUNNING)` 且父运行处于 `WAITING_DELEGATION_RETURN` 时派生。
- 当内部为 `FAILED` 时，调用方应从结果摘要读取 `failure_code`、`failure_reason`、是否允许重派发 / 人工接管等结论；API 保持粗粒度枚举稳定，失败细节进入摘要与审计视图。

### 4.8 `ao_environment_event`

- 用途：环境输入事件主表，把错误、追问、回调异常、工具失败纳入正式输入域
- 主键：`event_id`
- 关键字段：
  - `event_type`
  - `event_source`
  - `severity`
  - `related_task_id`、`related_run_id`
  - `event_payload_json`
  - `event_fingerprint`
  - `processing_status`：`NEW`、`LINKED`、`IGNORED`、`TURNED_INTO_TASK`、`PROCESSED`
  - `derived_task_id`
- 关键索引 / 约束：
  - `idx_env_event_type(event_type, created_at)`
  - `idx_env_event_task(related_task_id, created_at)`
  - `uk_env_event_fingerprint(event_source, event_fingerprint)`
- 关联对象：可关联任务、运行；也可独立存在后续再绑定

### 4.9 `ao_memory_item`

- 用途：正式记忆项主表，承载短期摘要、长期经验、怀疑式候选结论
- 主键：`memory_item_id`
- 关键字段：
  - `memory_scope`：`RUN`、`TASK`、`SESSION`、`AGENT`、`GLOBAL`
  - `memory_type`：`FACT`、`PATTERN`、`FAILURE_LESSON`、`TOOL_HINT`、`CONSTRAINT`
  - `source_run_id`、`source_task_id`
  - `memory_text_summary`
  - `memory_payload_json`
  - `confidence_score`
  - `skepticism_level`
  - `verification_status`：`UNVERIFIED`、`PROBATION`、`VERIFIED`、`REJECTED`
  - `freshness_state`：`FRESH`、`STALE`、`NEEDS_REVIEW`
  - `conflict_state`：`NONE`、`SUSPECTED`、`CONFLICTING`、`SUPERSEDED`
  - `expiration_state`：`ACTIVE`、`FROZEN`、`PENDING_EXPIRATION`、`EXPIRED`、`REVOKED`、`FORGOTTEN`
  - `embedding_ref`
  - `last_used_at`、`expire_at`
- 关键索引 / 约束：
  - `idx_memory_scope(memory_scope, verification_status, freshness_state, expiration_state)`
  - `idx_memory_source(source_run_id, source_task_id)`
  - `idx_memory_expire(expire_at)`
  - `idx_memory_vector_ref(embedding_ref)`
- 关联对象：来源于运行、结果、自动反思；供 Prompt 动态注入与工具路由参考

### 4.10 `ao_prompt_snapshot`

- 用途：Prompt 装配快照表，记录一次运行轮次所用静态底座各层版本、专用人格补丁版本、装配 / 裁剪 / 预算策略版本与动态注入摘要
- 主键：`prompt_snapshot_id`
- 关键字段：
  - `run_id`
  - `snapshot_no`
  - `general_persona_code`、`persona_code`
  - `platform_root_version`
  - `runtime_framework_version`
  - `general_persona_version`
  - `persona_patch_version`
  - `dynamic_injection_digest`
  - `dynamic_injection_summary`
  - `trimmed_reason_summary`
  - `context_token_estimate`
  - `prompt_body_ref`：正文引用，避免主表存超长文本
  - `assembly_policy_version`
  - `trimming_policy_version`
  - `budget_policy_version`
  - `cache_prefix_policy_version`：稳定前缀、工具定义排序与缓存友好策略版本
- 关键索引 / 约束：
  - `uk_prompt_run_snapshot(run_id, snapshot_no)`
  - `idx_prompt_base_versions(platform_root_version, runtime_framework_version, general_persona_version, persona_patch_version, created_at)`
- 关联对象：归属 `ao_agent_run`；被 `ao_tool_invocation`、审计事件引用

### 4.11 `ao_tool_invocation`

- 用途：统一工具调用明细表，模型调用也作为工具调用之一落该表
- 主键：`tool_invocation_id`
- 关键字段：
  - `run_id`
  - `prompt_snapshot_id`
  - `tool_type`：`MODEL`、`SEARCH`、`RULE`、`FILE`、`DB`、`HTTP`、`INTERNAL_SERVICE`
  - `tool_name`
  - `provider_code`
  - `invocation_status`：`PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`、`TIMED_OUT`、`CANCELLED`
  - `input_digest`
  - `output_digest`
  - `output_artifact_ref`：超长工具输出卸载后的原文或对象引用
  - `output_truncated_reason`：进入 Prompt 的工具结果被摘要化、截断或卸载的原因
  - `latency_ms`
  - `token_in`、`token_out`
  - `error_code`、`error_message_summary`
  - `retry_no`
- 关键索引 / 约束：
  - `idx_tool_run(run_id, created_at)`
  - `idx_tool_type(tool_type, tool_name, created_at)`
  - `idx_tool_status(invocation_status, updated_at)`
  - `idx_tool_provider(provider_code, created_at)`
- 关联对象：归属运行与 Prompt 快照；被审计、指标、自动反思消费

### 4.12 `ao_auto_dream_job`

- 用途：`Auto Dream daemon` 后台作业主表，处理运行后反思、经验整理、记忆候选与技能候选提炼
- 主键：`dream_job_id`
- 关键字段：
  - `source_run_id`、`source_task_id`
  - `dream_job_type`：`PATTERN_MINING`、`FAILURE_SUMMARY`、`TOOL_TUNING_HINT`、`MEMORY_CANDIDATE`、`SKILL_CANDIDATE`
  - `job_status`：`PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`、`SKIPPED`
  - `input_digest`
  - `output_summary`
  - `derived_candidate_cluster_id`
  - `failure_reason`
  - `next_retry_at`
- 关键索引 / 约束：
  - `idx_dream_status(job_status, next_retry_at)`
  - `idx_dream_source(source_run_id, created_at)`
- 关联对象：来源于运行，产出候选簇而不是直接写正式记忆

### 4.13 `ao_dream_candidate_cluster`

- 用途：`Auto Dream daemon` 候选簇主表，正式承接候选记忆、候选模式、候选优化结论、候选技能及其冻结 / 替代 / 复核状态
- 主键：`candidate_cluster_id`
- 关键字段：
  - `source_job_id`
  - `candidate_type`：`MEMORY`、`PATTERN`、`OPTIMIZATION`、`SKILL`
  - `subject_scope`
  - `conclusion_direction`：`POSITIVE`、`NEGATIVE`、`CONDITIONAL`、`OBSERVATIONAL`
  - `candidate_status`：`COLLECTED`、`CLUSTERED`、`UNDER_REVIEW`、`PROMOTED`、`REJECTED`、`FROZEN`、`SUPERSEDED`
  - `evidence_bundle_ref`
  - `superseded_by_cluster_id`
  - `promoted_target_ref`
  - `review_required`
  - `review_outcome_summary`
- 关键索引 / 约束：
  - `idx_dream_cluster_type(candidate_type, candidate_status, updated_at)`
  - `idx_dream_cluster_source(source_job_id, created_at)`
  - `idx_dream_cluster_scope(subject_scope, updated_at)`
- 关联对象：归属 `ao_auto_dream_job`；被 `ao_candidate_quality_profile`、`ao_agent_audit_event` 引用

### 4.14 `ao_candidate_quality_profile`

- 用途：候选质量画像主表，正式承接候选簇的统一多维质量判断
- 主键：`quality_profile_id`
- 关键字段：
  - `candidate_cluster_id`
  - `credibility_score`
  - `reusability_score`
  - `freshness_score`
  - `conflict_score`
  - `risk_score`
  - `profile_summary`
  - `evaluated_at`
  - `evaluation_policy_version`
- 关键索引 / 约束：
  - `idx_candidate_quality_cluster(candidate_cluster_id, evaluated_at)`
  - `idx_candidate_quality_risk(risk_score, evaluated_at)`
- 关联对象：归属 `ao_dream_candidate_cluster`

### 4.15 `ao_agent_audit_event`

- 用途：`Agent OS` 领域审计事件主表，记录任务、运行、Prompt、工具、确认、委派、记忆相关关键动作
- 主键：`audit_event_id`
- 关键字段：
  - `object_type`、`object_id`
  - `parent_object_type`、`parent_object_id`
  - `action_type`
  - `action_summary`
  - `actor_type`、`actor_id`
  - `result_status`
  - `trace_id`
  - `risk_level`
  - `payload_digest`
  - `occurred_at`
- 关键索引 / 约束：
  - `idx_ao_audit_object(object_type, object_id, occurred_at)`
  - `idx_ao_audit_trace(trace_id)`
  - `idx_ao_audit_action(action_type, occurred_at)`
- 关联对象：可关联所有 `Agent OS` 核心对象

### 4.16 表间关系总览

- `ao_agent_task` 是任务根对象
- `ao_agent_run` 是执行根对象，一个任务至少有一个主运行实例
- `ao_agent_result` 是运行产出的正式结果对象
- `ao_human_confirmation` 是运行闸门对象，以生命周期状态承接确认单流转，以 `decision_action` 承接正式裁决动作
- `ao_delegation` 连接父运行与子运行，`ao_delegation_return` 承接正式回收结论并形成跨 Session 协作链
- `ao_environment_event` 既可作为任务输入前置对象，也可作为已有任务的追加观察输入
- `ao_prompt_snapshot` 与 `ao_tool_invocation` 构成运行时可追踪证据链
- `ao_auto_dream_job`、`ao_dream_candidate_cluster`、`ao_candidate_quality_profile` 构成候选治理链，晋升后再进入 `ao_memory_item` 或其他正式治理面
- `ao_agent_audit_event` 覆盖全链路对象审计

## 5. 通用 Agent 人格 / 专用 Agent 人格内部模型

### 5.1 分层原则

- 通用 Agent 人格是全部 Agent 的默认运行底座
- 专用 Agent 人格只在通用人格之上叠加领域能力，不替换静态底座
- 输出约束、安全约束、确认约束、审计约束统一归通用人格管理

### 5.2 通用 Agent 人格模型

通用人格内部最少包含以下维度：

- `identity_profile`：身份与职责边界
- `global_output_contract`：统一输出格式、安全禁令、引用要求、禁止越权规则
- `tool_capability_envelope`：默认可用工具范围与风险等级
- `decision_style`：保守、审慎、可追溯、先证据后结论
- `memory_policy`：可读哪些记忆、何种结果可进入候选记忆
- `confirmation_policy`：哪些动作必须经过人工确认

### 5.3 专用 Agent 人格模型

专用人格内部最少包含以下维度：

- `persona_code`：专用人格正式标识
- `domain_scope`：适用业务域或任务域
- `domain_tool_whitelist`
- `domain_prompt_patch`
- `result_preferences`：由“目标结果类型偏好 + 领域术语理解与结果重心 + 可覆盖位点中的结果表达差异”共同构成
- `delegation_policy`
- `risk_profile`：由“任务域风险语义 + 默认工具允许域与禁止域 + 默认确认敏感度 + 默认委派偏好”共同构成

### 5.4 继承与冲突解决

- 通用人格优先级高于专用人格中的冲突项
- 专用人格只能收窄工具范围，不能扩大通用人格禁止范围
- 专用人格可增加领域结果格式约束，但不能删除统一审计和确认要求
- 同一运行实例只允许一个通用人格底座和一个当前生效的专用人格

## 6. 静态底座提示词工程与动态注入内部模型

### 6.1 静态底座模型

静态底座不是单条 Prompt 文本，而是一组稳定层：

- 平台身份层：定义 Agent OS 是平台 AI 能力底座 / 运行时操作系统
- 安全约束层：禁止越权、禁止把 AI 输出当最终业务结论、禁止绕过人工确认
- 输出约束层：适用于全部 Agent 的输出结构、引用、审计和语言约束
- 协作约束层：要求通过工具、记忆、委派、确认等正式链路工作

静态底座按分层版本管理，平台根约束层、运行时框架层、通用人格层版本分别进入 `ao_prompt_snapshot.platform_root_version`、`ao_prompt_snapshot.runtime_framework_version`、`ao_prompt_snapshot.general_persona_version`；若命中专用人格补丁，则额外记录 `ao_prompt_snapshot.persona_patch_version`。

### 6.2 动态注入模型

动态注入按片段类型管理，至少包括：

- `task-brief`：任务目标与成功条件摘要
- `business-context`：业务对象与上下文摘要
- `environment-context`：错误、追问、回调异常、工具失败等环境输入
- `memory-context`：被召回的记忆摘要及可信度标签
- `tool-context`：当前可用工具和禁用工具说明
- `governance-context`：当前确认要求、委派要求、输出限制

### 6.3 装配顺序

1. 通用人格静态底座。
2. 专用人格补丁。
3. 任务摘要与业务上下文。
4. 环境事件与最新观察。
5. 记忆摘要。
6. 工具可用性与当前风险提示。
7. 本轮检查点与上轮观察结论。

### 6.4 上下文治理边界

- 动态注入只注入摘要，不盲目拼接全部原始输入
- 大文档、长日志、超长累积对话通过引用对象和摘要进入，不直接塞进 Prompt 正文
- 超长上下文优先裁剪低可信度记忆和低价值既往观察，而不是裁剪静态底座约束
- 每轮 Prompt 装配必须保留 `context_token_estimate`，超过阈值时触发裁剪策略或委派策略，并把命中的 `trimming_policy_version`、`budget_policy_version` 与 `trimmed_reason_summary` 写入 `ao_prompt_snapshot`
- 上下文压缩、预算分桶、稳定前缀与缓存友好排序的细则下沉到 Prompt 分层专项设计维护，本章只保留运行时需要消费的策略版本与摘要结果

## 7. `QueryEngine Runtime Loop` 内部设计

### 7.1 运行时中心定位

`QueryEngine Runtime Loop` 是 `Agent OS` 的运行时中心。它不是聊天轮次计数，也不是单次模型调用封装，而是薄 Harness 的确定性 `while loop`：每轮接收任务和观察，治理上下文，组装 Prompt，检查 Provider 预算，调用模型或规则，选择工具或治理动作，执行安全链，归一观察，写入检查点，并判断是否终止。

### 7.2 每轮循环顺序

每轮循环必须按以下顺序推进，任何实现不得把预算检查、`Guard Chain`、观察归一或检查点写入移出主链：

```text
Task Ingress
-> Context Governor
-> Prompt Assembly
-> Provider Budget Check
-> Model Call
-> Tool Decision
-> Guard Chain
-> Sandbox Executor
-> Observation Normalize
-> Memory Intake
-> State Checkpoint
-> Termination Check
```

| 步骤 | 输入 | 产出 | 关键约束 |
| --- | --- | --- | --- |
| `Task Ingress` | `AgentTask`、环境事件、人工追问、委派回收 | 本轮任务解释与运行状态 | 业务输入和环境输入都必须进入正式任务 / 运行对象。 |
| `Context Governor` | 任务、历史观察、记忆目录、工具结果、预算阈值 | 上下文治理快照、压缩结果、召回摘要 | 不允许无边界拼接完整历史；工具调用 / 结果必须成对保留。 |
| `Prompt Assembly` | 静态底座、专用人格补丁、治理后动态片段 | `PromptSnapshot` | Prompt 正文不对外暴露，只保留摘要、版本和正文引用。 |
| `Provider Budget Check` | Prompt 估算、模型能力、配额、熔断状态 | 可用 Provider 或降级决策 | 成本、速率、熔断不通过时禁止直接调用模型。 |
| `Model Call` | Prompt 快照、Provider 决策 | 模型响应摘要或结构化决策候选 | 模型调用作为工具调用落审计，不成为业务 API 直接结果。 |
| `Tool Decision` | 模型 / 规则决策、工具目录、风险级别 | 工具调用、人工确认、委派或最终结果候选 | 模型、检索、规则、文件、数据库和外部 API 都是统一工具能力。 |
| `Guard Chain` | 候选动作、权限、风险、确认策略 | 放行、拒绝、要求确认或降级 | 安全约束必须由确定性规则、LLM Judge、人工确认和沙箱共同承接。 |
| `Sandbox Executor` | 已放行动作、工具授权、沙箱策略 | `ToolInvocation` 与结果引用 | 高风险副作用必须隔离；超长输出必须卸载。 |
| `Observation Normalize` | 工具结果、失败、拒绝、确认、委派返回 | 统一观察摘要 | 失败也是观察，不能被日志吞掉。 |
| `Memory Intake` | 观察摘要、结果候选、失败教训 | 记忆候选或补偿作业 | 压缩前记忆冲洗失败不得静默继续。 |
| `State Checkpoint` | 本轮 Prompt、动作、观察、风险、成本 | `Checkpoint` 与 `AuditEvent` | 每轮必须可恢复、可回放、可审计。 |
| `Termination Check` | 运行状态、结果契约、成本、循环数、风险 | 继续下一轮或进入终态 | 终止条件固定顺序判断，不能由模型自行决定跳过。 |

### 7.3 主状态模型

`QueryEngine Runtime Loop` 的核心状态如下：

- `INGRESS_PENDING`
- `CONTEXT_GOVERNING`
- `PROMPT_ASSEMBLING`
- `BUDGET_CHECKING`
- `MODEL_CALLING`
- `TOOL_DECIDING`
- `GUARD_CHECKING`
- `SANDBOX_EXECUTING`
- `OBSERVATION_NORMALIZING`
- `MEMORY_INTAKING`
- `CHECKPOINTING`
- `TERMINATION_CHECKING`
- `WAITING_HUMAN_CONFIRMATION`
- `WAITING_DELEGATION_RETURN`
- `FINALIZING`
- `SUCCEEDED`
- `FAILED`
- `CANCELLED`

### 7.4 `Context Governor` 内部模块

`Context Governor` 是上下文窗口的主动治理者，不是 `Prompt Assembly` 的附属裁剪函数。它至少包含以下内部模块：

| 内部模块 | 作用 | 强制规则 |
| --- | --- | --- |
| `Micro Compression` | 基于规则保留最近高价值工具结果，清理低价值旧观察 | 只允许删除可安全移除的结果；不得破坏工具调用 / 结果配对。 |
| `Fact Compression` | 从对话和观察中提取结构化事实、约束、进度和用户偏好 | 事实进入记忆候选，不把原始长文本当事实。 |
| `Full Compression` | 在上下文接近阈值时生成完整摘要边界消息 | 执行前必须完成记忆冲洗；失败时不得继续假装压缩成功。 |
| `Auto Fuse` | 连续压缩失败或预算不可恢复时触发熔断 | 熔断后进入降级、委派或人工接管，不允许无限重试。 |
| `Tool Pair Keeper` | 校验工具调用与工具结果成对存在 | 发现断对时生成 `TOOL_PAIR_BROKEN`，阻断本轮 Prompt 组装。 |
| `Pre-compression Memory Flush` | 压缩前把高价值事实、失败教训和待办边界冲洗到记忆候选 | 冲洗失败生成 `MEMORY_FLUSH_FAILED` 与补偿作业。 |
| `Context Budget Estimator` | 估算静态前缀、动态注入、工具定义、记忆和结果摘要预算 | 超预算时先裁剪低价值动态内容，不裁剪安全底座。 |

### 7.5 `LoopStep` 对象承接方式

本文不新增独立 `LoopStep` 主表，使用现有对象组合承接一轮 step：

| `LoopStep` 组成 | 承接对象 | 说明 |
| --- | --- | --- |
| 本轮输入与上下文 | `AgentTask`、`AgentRun`、`EnvironmentEvent`、`PromptSnapshot` | `PromptSnapshot` 记录静态底座版本、动态注入摘要、上下文预算和正文引用。 |
| 本轮动作 | `ToolInvocation`、`HumanConfirmation`、`Delegation` | 模型调用也作为 `ToolInvocation` 记录；人工确认和委派是特殊治理动作。 |
| 本轮观察 | `ToolInvocation.output_digest`、`EnvironmentEvent`、`DelegationReturn`、`AuditEvent` | 成功、失败、拒绝、超时和回收都转成观察摘要。 |
| 本轮状态迁移 | `AgentRun.runtime_state`、`AgentRun.loop_count`、`Checkpoint` | `Checkpoint` 以 `ao_agent_run.latest_checkpoint_summary` 承接摘要，详单进入审计。 |
| 本轮审计与恢复 | `AuditEvent`、最近一次 `PromptSnapshot`、最近一次 `ToolInvocation` | 支持运行回放、失败诊断、成本追踪和补偿恢复。 |

因此，一轮 `LoopStep` 的最小证据链为：`PromptSnapshot + ToolInvocation + AuditEvent + Checkpoint`。若本轮没有工具执行，`ToolInvocation` 可由模型调用、人工确认申请或委派申请对应的治理动作记录替代，但审计事件和检查点不可省略。

### 7.6 终止条件

运行可在以下条件下终止，判定顺序固定为：

- 被人工明确驳回并终止
- 被外部取消
- 超过最大循环数
- 超过最大成本阈值
- Provider 预算不可恢复且无降级路径
- 上下文治理熔断且无法委派或人工接管
- 风险不可接受且无进一步动作空间
- 已生成满足契约的正式结果

### 7.7 运行时失败处理表

| 失败码 | 触发条件 | 运行态变化 | 处理策略 | 审计要求 |
| --- | --- | --- | --- | --- |
| `CONTEXT_BUDGET_EXCEEDED` | 上下文估算超过本轮预算，压缩后仍不可放入 | `CONTEXT_GOVERNING -> FAILED` 或降级继续 | 先微压缩，再事实压缩，再完整压缩；仍失败则委派、降级或人工接管。 | 记录预算估算、裁剪策略、压缩结果和未注入内容引用。 |
| `TOOL_PAIR_BROKEN` | 压缩或装配后工具调用与工具结果不成对 | `PROMPT_ASSEMBLING -> FAILED` | 阻断模型调用，恢复最近合法快照；恢复失败则人工接管。 | 记录断裂工具调用、结果引用、压缩策略版本和恢复结论。 |
| `MEMORY_FLUSH_FAILED` | 完整压缩前记忆冲洗失败 | `MEMORY_INTAKING -> FAILED` 或降级继续 | 生成补偿作业；高价值事实未落盘时禁止继续完整压缩。 | 记录待冲洗事实摘要、失败原因、补偿作业和降级策略。 |
| `PROVIDER_BUDGET_EXCEEDED` | Provider 成本、速率、配额或熔断检查不通过 | `BUDGET_CHECKING -> FAILED` 或降级继续 | 切换低成本模型、规则 / 检索路径或等待配额恢复；无路径则终止。 | 记录预算门输入、Provider 候选、拒绝原因和降级结果。 |
| `SANDBOX_REJECTED` | 沙箱拒绝危险命令、越权访问或高风险副作用 | `SANDBOX_EXECUTING -> WAITING_HUMAN_CONFIRMATION` 或 `FAILED` | 可转人工确认、请求修改动作或终止运行；不得绕过沙箱重试。 | 记录沙箱策略、拒绝命令摘要、风险级别和确认单引用。 |
| `HUMAN_CONFIRMATION_TIMEOUT` | 确认单超过过期时间未处理 | `WAITING_HUMAN_CONFIRMATION -> FAILED`、降级或人工接管 | 按确认策略终止、降级、重新规划或转人工接管，不允许默认放行。 | 记录确认单、过期策略、等待时长和恢复动作。 |

### 7.8 检查点设计

每轮循环结束都写运行检查点，至少包括：

- 当前 `QueryEngine` 状态
- 本轮 Prompt 快照引用
- 本轮动作摘要
- 新观察摘要
- 当前风险级别
- Provider 与成本摘要
- 已使用工具摘要
- 记忆冲洗 / 压缩状态
- 候选下一步
- 终止检查结果

检查点写入 `ao_agent_run.latest_checkpoint_summary`，详单进入审计事件。

## 8. 工具路由与模型 / Provider 抽象内部设计

### 8.1 工具统一模型

全部工具按统一元数据注册：

- `tool_type`
- `tool_name`
- `capability_code`
- `risk_level`
- `side_effect_level`
- `timeout_policy`
- `retry_policy`
- `result_schema`

工具发现采用分层入口：启动上下文只注入核心工具的完整定义；低频或扩展工具先以 `tool_name + search_hint + risk_level` 进入 `tool-context`，运行时需要时通过 `ToolSearch` 或等价工具目录查询完整 `schema`。查询结果应形成工具定义快照，并与后续 `ao_tool_invocation` 审计关联。

### 8.2 路由决策原则

- 只读工具优先于副作用工具
- 规则 / 检索优先于模型推理
- 本地内部工具优先于外部网络工具
- 主 `Provider` 失败时按策略切换备选 `Provider`
- 模型选择由能力标签驱动，不由业务模块直接指定厂商实现

### 8.3 模型 / Provider 抽象层

`provider-abstraction` 对上暴露统一能力：

- `generate_text`
- `extract_structured`
- `rank_or_rerank`
- `embed`
- `summarize`

对下维护：

- `provider_code`
- `model_code`
- `capability_matrix`
- `cost_tier`
- `latency_tier`
- `availability_status`
- `throttle_policy`

### 8.4 失败与降级

- 单次调用失败先按工具策略重试
- `Provider` 连续失败达到阈值后进入短期熔断
- 熔断期间自动切换备选 `Provider` 或回退到规则 / 检索型路径
- 所有降级动作必须留下审计与指标

## 9. `skeptical memory` 与 `Auto Dream daemon` 内部设计

### 9.1 `skeptical memory` 设计原则

- 记忆不是事实仓库，而是带可信度和怀疑等级的辅助材料
- 新记忆先进入候选区，不直接晋升为长期可信记忆
- 运行时召回记忆时必须带上 `verification_status` 和 `confidence_score`

### 9.2 记忆分层

- `working memory`：仅随运行实例生存，保存在运行检查点和临时缓存中
- `episodic memory`：按任务 / Session 记录一次运行经验
- `semantic memory`：提炼后的稳定约束、工具偏好、失败教训
- `skeptical memory`：可信度不足但值得保留观察的候选结论

### 9.3 记忆写入策略

- 最终结果成功且被业务消费，不等于自动进入长期记忆
- 只有满足“高复用、高可信、低时效性”的结论才可转为长期记忆
- 被人工驳回、来源工具失败、上下文不足的结论默认进入怀疑式记忆或直接丢弃

### 9.4 `Auto Dream daemon`

该守护进程在主运行完成后异步运行，处理以下工作：

- 汇总失败模式
- 比较不同工具 / Provider 路径效果
- 提炼复用性高的上下文裁剪方式
- 形成候选簇并生成质量画像
- 输出候选记忆、候选模式、候选优化结论，并可把候选技能作为下游治理对象之一交给技能资产治理链路复核

### 9.5 两者协作边界

- `Auto Dream daemon` 只能产出 `ao_dream_candidate_cluster` 与 `ao_candidate_quality_profile`，不直接写正式长期记忆
- 候选簇需经 `memory-governor` 或其他下游治理面消费后，才允许晋升为 `ao_memory_item` 或正式策略入口
- 低置信度候选允许保留为 `skeptical memory`，供未来再次验证

## 10. 人工确认与跨 Session 委派内部设计

### 10.1 人工确认触发条件

- 高风险副作用动作
- 涉及权限升级、越权访问、敏感信息释放
- 正式结果回写业务对象前
- 多条候选结论冲突且无法自动收敛时
- 委派到更高权限或高成本专用 Agent 前

### 10.2 人工确认状态流转

- `CREATED` -> `PENDING`
- `PENDING` -> `IN_REVIEW`
- `IN_REVIEW` -> `DECIDED`
- `PENDING` / `IN_REVIEW` -> `EXPIRED`
- `CREATED` / `PENDING` / `IN_REVIEW` -> `CANCELLED`
- `DECIDED` -> `REVOKED`

回写规则：

- `decision_action = APPROVE` / `APPROVE_ONCE` / `APPROVE_IN_SCOPE`：运行从等待态恢复，但只在已批准边界内继续
- `decision_action = REJECT`：运行终止或退回重新规划
- `decision_action = REQUEST_CHANGES`：生成新的观察输入，进入下一轮 `INGRESS_PENDING` 或 `CONTEXT_GOVERNING`
- `confirmation_status = EXPIRED`：按任务策略终止、降级或转人工接管
- `confirmation_status = REVOKED`：沿审计与补偿链追溯既有裁决影响

### 10.3 跨 Session 委派模型

- 委派不是父运行内的子线程，也不是同一上下文里的角色切换；每次委派都会形成独立 `child_run`。
- `child_run` 具备独立上下文快照、工具授权快照、权限范围快照、审计链、心跳 / lease 与结果回收对象。
- 父运行保留主线责任，子运行只承接合同审查、审批异常分析、签署状态解释、履约风险判断等明确目标和最小必要上下文。
- 默认物理执行形态由独立 Worker 进程或进程池槽位承接，不与父运行共享线程栈和上下文内存。
- 当命中高风险工具、敏感合同 / 文档处理、解密下载、批量导出、不可信文件解析器、外部插件、强网络隔离或强文件系统隔离要求时，委派升级到独立容器沙箱。
- 当前部署基线是 `Docker Compose / 企业内网`，不默认每个委派都动态起容器；默认动态起容器会增加编排复杂度、启动成本和 `Docker Socket` 风险，而企业业务委派主要是业务推理与工具编排，不是不可信代码执行。
- 容器不是替代工具权限治理的万能边界；即使进入独立容器沙箱，仍必须经过 `ToolGrant`、`Sandbox Executor`、权限快照、网络 / 文件 / 密钥 / 资源策略和审计链。
- 子运行结果必须先形成 `ao_delegation_return` 回收结论，再由父运行决定是否合并。

`P-A-O` 可作为“感知 / 行动 / 观察”的语义阶段描述，用于帮助解释运行意图，但不作为 `ao_agent_run.runtime_state` 的正式枚举；运行态持久化统一使用 `QueryEngine Runtime Loop` 主状态模型。

### 10.4 委派触发条件

- 需要不同专用 Agent 的领域能力
- 当前上下文已过长，需要切断支线任务
- 某类任务可并行探索多个方案
- 某类任务需要更高风险隔离边界

### 10.5 委派回收与合并

- 子运行返回摘要、结构化产物与证据引用
- 父运行进入 `WAITING_DELEGATION_RETURN`；回收成功后按回收内容进入 `INGRESS_PENDING` 或 `CONTEXT_GOVERNING`，由 `QueryEngine` 重新归一观察输入、上下文快照与下一步候选动作
- 回收失败或结论冲突时，不恢复到语义阶段名，而是进入 `TOOL_DECIDING`、`WAITING_HUMAN_CONFIRMATION` 或 `FAILED` 等正式运行状态，由父运行决定重试、人工确认或终止
- 回收阶段至少固化 `return_status`、`merge_recommendation`、`next_action` 与失败 / 冲突证据引用
- 合并策略由 `merge_policy` 控制，必要时先经过人工确认再合并

## 11. 缓存、锁、幂等与并发控制

### 11.1 缓存边界

允许缓存的对象：

- 人格装配结果摘要
- 静态底座版本元数据
- 工具注册表
- 已验证记忆的热点召回摘要
- `Provider` 可用性与熔断状态

不允许只存在缓存的对象：

- 任务正式状态
- 运行正式状态
- 结果对象
- 人工确认结果
- 委派关系
- 审计事件

### 11.2 锁策略

- `task:{task_id}`：防止同一任务重复启动主运行
- `run:{run_id}`：防止同一运行被多个执行器并发推进
- `confirmation:{confirmation_id}`：防止确认单重复处理
- `delegation:{delegation_id}`：防止重复回收 / 重复合并
- `memory-promote:{candidate_cluster_id}`：防止同一候选簇被重复晋升

### 11.3 幂等策略

- 任务创建按 `task_source + requester_id + idempotency_key` 保证幂等
- 环境事件接入按 `event_source + event_fingerprint` 防重复
- 委派发起按 `parent_run_id + delegated_agent_code + delegation_goal_digest` 防重复
- 人工确认处理按 `confirmation_id + decision_action + operator_id` 防重复提交

### 11.4 并发控制基线

- 主表全部带 `version_no` 或等效乐观锁字段
- 关键状态迁移采用条件更新，不依赖客户端按钮防重
- 执行器领取任务时采用“数据库状态条件更新 + 短锁”双保险
- 委派回收和结果写回采用单对象串行化，防止双写和覆盖

## 12. 异步任务、补偿与恢复

### 12.1 异步化范围

以下能力默认异步：

- 长文本或多工具循环任务
- `Auto Dream daemon` 作业
- 记忆向量化与索引更新
- 高成本外部工具调用重试
- 结果回写失败后的补偿重放
- 确认单过期扫描与催办

### 12.2 补偿原则

- 工具调用失败可重试，但结果回写必须防重复
- 委派子运行失败时，父运行不能假定成功，必须显式收到失败观察
- 人工确认超时后不能静默放行，只能按显式策略终止、退回或转人工接管
- 记忆写入失败不影响主结果生成，但要留下可补跑作业
- 完整压缩前的记忆冲洗若失败，不得静默继续压缩；必须生成补偿作业，保留失败原因、待冲洗事实摘要和可重试边界，再按策略降级或转人工接管

### 12.3 恢复策略

- 服务重启后按 `ao_agent_run.run_status` 恢复未完成运行
- `WAITING_HUMAN_CONFIRMATION` 运行依赖确认单状态恢复
- `WAITING_DELEGATION_RETURN` 运行依赖委派对象恢复
- `RUNNING` 且长时间无心跳的运行由恢复任务判定为僵尸运行，转为重试或人工接管

### 12.4 恢复锚点

恢复判断最少依赖以下正式对象：

- `ao_agent_task`
- `ao_agent_run`
- `ao_human_confirmation`
- `ao_delegation`
- `ao_agent_result`
- 最近一次 `ao_prompt_snapshot`
- 最近一次 `ao_agent_audit_event`

## 13. 审计、日志、指标与恢复边界

### 13.1 审计边界

必须进入审计的动作：

- 任务受理、取消、结束
- 运行启动、阶段切换、失败、恢复
- Prompt 快照生成
- 工具调用，尤其是模型 / 外部 API / 数据库 / 写操作工具
- 人工确认创建、处理、过期
- 委派发起、回收、合并
- 记忆晋升、驳回、过期
- `Auto Dream daemon` 候选簇收集、质量画像更新、冻结、替代与复核结论

### 13.2 日志边界

- 调试日志服务于排障，可采样、可裁剪、可过期
- 审计事件服务于合规和追踪，不可被普通调试日志替代
- Provider 原始响应、长 Prompt 正文、超长工具输出不直接入标准日志，改为摘要 + 引用

### 13.3 指标边界

核心指标至少包括：

- 任务受理量、成功率、失败率、取消率
- 平均循环数、平均工具调用次数、平均确认等待时长
- 各 `Provider` 延迟、错误率、熔断次数、降级次数
- 委派数量、委派成功率、合并成功率
- 记忆召回命中率、有效率、误召回率
- `Auto Dream` 候选转正式记忆比例

### 13.4 恢复边界

- 可重建对象：缓存、锁、熔断状态、热点记忆摘要、向量索引副本
- 不可丢失对象：任务、运行、结果、确认、委派、正式记忆、审计事件
- 恢复时以数据库主状态为准，缓存与短态全部视为派生层重建

## 14. 继续下沉到后续专项设计或实现的内容

以下内容应在后续专项设计或实现阶段继续下沉，而不在本文写死：

- Prompt 静态底座分层、正式版本管理、发布流程，以及动态注入裁剪优先级、上下文预算与治理边界： [prompt-layering-and-versioning-design.md](./special-designs/prompt-layering-and-versioning-design.md)
- 各类专用 Agent 人格目录、元数据与治理规则： [specialized-agent-persona-catalog-design.md](./special-designs/specialized-agent-persona-catalog-design.md)
- 工具参数协议、结果 JSON Schema、工具沙箱与权限白名单细节： [tool-contract-and-sandbox-design.md](./special-designs/tool-contract-and-sandbox-design.md)
- Provider 路由、主备切换与降级回退，以及成本预算、配额速率、熔断与计费归集： [provider-routing-and-quota-design.md](./special-designs/provider-routing-and-quota-design.md)
- 记忆检索入口、召回排序、冲突治理，以及失效、过期、撤销与遗忘闭环： [memory-retrieval-and-expiration-design.md](./special-designs/memory-retrieval-and-expiration-design.md)
- Auto Dream daemon 的候选质量治理与评分维度： [auto-dream-daemon-candidate-quality-design.md](./special-designs/auto-dream-daemon-candidate-quality-design.md)
- 人工确认对象、通知催办、超时升级、人工接管边界与控制台工作台边界： [human-confirmation-and-console-design.md](./special-designs/human-confirmation-and-console-design.md)
- 委派调度器的并行策略、隔离配额与回收冲突处理细则： [delegation-scheduler-design.md](./special-designs/delegation-scheduler-design.md)
- 正式验证面、测试矩阵、性能基线、负载模型与回归判定： [verification-and-performance-design.md](./special-designs/verification-and-performance-design.md)，演练与运维部分另行专项化
- 正式演练场景、故障注入范围、恢复演练对象与运维工作手册边界： [drill-and-operations-runbook-design.md](./special-designs/drill-and-operations-runbook-design.md)

## 15. 本文结论

本设计把 `Agent OS` 落到可实现的内部运行时结构：

- 以任务、运行、结果、确认、委派、环境事件、记忆、Prompt 快照、工具调用、自动反思、审计事件组成正式对象模型
- 以通用人格先于专用人格、静态底座先于动态注入作为人格与 Prompt 的基本组织方式
- 以 `QueryEngine Runtime Loop` 作为内部执行中心，而不是以模型调用为中心
- 以工具路由和模型 / `Provider` 抽象确保业务模块不绑定具体模型能力
- 以 `skeptical memory`、`Auto Dream daemon`、人工确认和跨 Session 委派形成可控、可恢复、可审计的 AI 运行时闭环

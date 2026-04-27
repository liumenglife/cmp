# Agent OS 子模块 API Design

## 1. 文档说明

本文档是 `Agent OS` 子模块的第一份正式 `API Design`。
它用于在 [`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)、
[`总平台 Architecture Design`](../../architecture-design.md)、
[`总平台 API Design`](../../api-design.md)、
[`总平台 Detailed Design`](../../detailed-design.md) 与
[`Agent OS Architecture Design`](./architecture-design.md) 已确定边界的基础上，
定义 `Agent OS` 对外可见的资源边界、请求/响应契约、鉴权约束、错误码复用、
异步任务与回调接口，以及人工确认、委派、审计查询等接口边界。

### 1.1 输入

- 上游需求基线：[`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- 总平台架构约束：[`Architecture Design`](../../architecture-design.md)
- 总平台接口规范：[`API Design`](../../api-design.md)
- 总平台共享内部边界：[`Detailed Design`](../../detailed-design.md)
- 本模块架构边界：[`Agent OS Architecture Design`](./architecture-design.md)

### 1.2 输出

- 本文：[`Agent OS API Design`](./api-design.md)
- 为后续 `Agent OS Detailed Design` 预留下沉边界

### 1.3 阅读边界

本文只描述 `Agent OS` 的 API 可见契约，不展开以下内容：

- 不复述一期需求范围、技术选型理由或实施排期
- 不写物理表结构、索引、缓存键、队列主题或内部状态机实现
- 不写 Prompt 模板正文、静态底座文本、动态注入变量明细
- 不写模型 / Provider SDK 适配细节、路由算法或降级策略细节

## 2. API 平面划分与边界

### 2.1 API 平面划分

`Agent OS` API 分为三个平面：外部业务 API、内部控制面 API、运维审计 API。三个平面必须隔离权限、字段和使用场景，避免把 `QueryEngine`、Prompt、Provider、工具 schema、Memory 原文或子 Session 上下文外泄给业务调用方。

外部业务 API 的主场景以合同管理平台为锚点：业务对象可以是合同、合同文档、审批流程、签署任务、履约节点、风控命中或审计回放请求；API 只承接这些对象引用和任务意图，不把内部 Harness 运行细节暴露给业务用户或管理端。

| API 平面 | 使用方 | 允许能力 | 禁止外泄内容 |
| --- | --- | --- | --- |
| 外部业务 API | 业务模块、平台任务发起方、业务管理端 | 提交任务、查询运行摘要、消费结果、提交人工确认、接入环境事件、查询委派摘要 | Prompt 正文、Provider 私参、完整工具 schema、Memory 原文、子 Session 完整上下文、内部状态机细节。 |
| 内部控制面 API | `Agent OS` 内部服务、运行时执行器、治理服务 | 管理工具、Prompt、Memory、Provider、人格、沙箱策略、预算策略、运行恢复 | 不作为业务模块直接调用入口；不得绕过审计和权限生成不可追踪变更。 |
| 运维审计 API | 运维、审计、安全、验证和平台治理角色 | 查询审计、指标、运行回放、失败诊断、成本配额、验证报告、演练结果 | 不提供修改业务结果的写能力；不返回完整敏感原文，默认返回摘要、引用和证据包。 |

### 2.2 外部业务 API 边界

外部业务 API 只暴露平台级任务与结果契约。业务调用方不得直接传入或要求返回以下内容：

- Prompt 正文、静态底座正文、动态注入片段全文
- Provider 私有参数、SDK 字段、密钥、模型供应商内部路由策略
- 完整工具 schema、工具沙箱策略、工具授权快照全文
- Memory 原文、记忆目录全文、压缩前对话全文
- 子 Session 完整上下文、子 Agent 内部 Prompt、内部思考链或原始工具结果全集

业务 API 可以接收业务上下文、对象引用、问题描述、结果格式偏好、回调地址和人工确认裁决；可以返回任务状态、运行摘要、结果对象、引用清单、确认状态、委派摘要和审计摘要。

### 2.3 内部控制面 API 边界

内部控制面 API 承接 `QueryEngine` 的运行时管理能力，包括：

- 工具管理：`Tool Registry`、`ToolSearch`、工具授权、完整 schema 快照、沙箱策略和输出卸载策略
- Prompt 管理：静态底座版本、专用人格补丁、动态注入策略、缓存前缀策略和 Prompt 快照引用
- Memory 管理：记忆候选、主动召回、压缩前记忆冲洗、事实压缩、记忆晋升、过期、撤销和冻结
- Provider 管理：模型能力画像、Provider 路由、预算门、配额、速率限制、熔断、降级和成本归集
- 运行控制：检查点恢复、失败补偿、人工确认恢复、委派恢复、运行暂停和取消

这些能力属于内部控制面，不进入业务 API 示例，也不允许业务模块用“高级参数”绕过平台治理。

### 2.4 运维审计 API 边界

运维审计 API 承接可观测性、治理和验证能力，包括：

- 审计查询：任务、运行、工具、确认、委派、记忆、Prompt 快照和 Provider 决策摘要
- 指标查询：成功率、失败率、循环数、工具调用数、Provider 成本、确认等待、委派耗时和记忆命中
- 运行回放：基于摘要、引用和审计事件重建运行轨迹，不回放完整 Prompt 原文
- 失败诊断：失败码、失败阶段、恢复动作、补偿作业、未覆盖风险和回归基线入口
- 成本配额：Provider 预算、速率、熔断、降级记录和配额消耗
- 验证报告：独立验证角色的证据包、检查项、结论、失败证据和回归建议

运维审计 API 可以比业务 API 看见更完整的证据链，但仍遵循最小必要披露：默认返回摘要和引用，只有具备权限的排障或审计场景才能读取受控原文。

### 2.5 资源与接口边界总览

`Agent OS` 对外只暴露以下资源边界：

- Agent 任务入口：接收业务模块或平台运行时发起的 Agent 任务
- Agent 运行会话 / 运行实例：暴露任务对应的执行上下文与当前运行态
- Agent 结果查询：返回最终结果、过程摘要、结构化输出与结果引用
- 人工确认接口：承接高风险动作、关键判断或越权操作的人工确认
- 审计查询接口：按任务、运行、确认、委派等维度查询留痕视图
- 环境输入接入接口：接收错误、告警、回调异常、工具失败等环境事件
- 多 Agent 委派接口：承接跨 Session 委派发起、状态查询、结果回收

以下内容不属于外部业务 API 边界：

- 业务模块内部页面交互状态
- Prompt 组装内部过程
- 工具路由算法与模型选择算法
- Provider 私有参数、SDK 请求字段与底层重试策略
- 完整工具 schema、Memory 原文与子 Session 完整上下文

### 2.6 Agent 任务入口

- 资源：`AgentTask`
- 作用：为业务模块、平台内部作业或运维触发方提供统一任务受理入口
- 入口语义：创建一个待执行或已受理的 Agent 任务，不直接暴露内部执行步骤
- 推荐路径：`POST /api/agent-os/tasks`

请求体核心字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `task_type` | string | 是 | 任务类型，如 `DOCUMENT_REVIEW`、`RISK_ANALYSIS`、`KNOWLEDGE_QA`、`ENVIRONMENT_TRIAGE` |
| `task_source` | string | 是 | 来源类型，如 `BUSINESS_MODULE`、`PLATFORM_EVENT`、`MANUAL_OPERATOR` |
| `requester_type` | string | 是 | 发起主体类型，如 `USER`、`SYSTEM`、`SERVICE_ACCOUNT` |
| `requester_id` | string | 是 | 发起主体标识 |
| `specialized_agent_code` | string | 否 | 期望叠加的专用 Agent 标识，不传则由平台决定默认能力域 |
| `input_context` | object | 是 | 任务上下文壳层，承载业务对象引用、问题描述、约束摘要 |
| `input_payload` | object | 否 | 结构化输入正文 |
| `callback_url` | string | 否 | 需要异步回调时提供 |
| `callback_secret_ref` | string | 否 | 回调签名所引用的密钥标识 |
| `idempotency_key` | string | 否 | 幂等提交标识；也可通过请求头 `Idempotency-Key` 传递 |

请求体示例：

```json
{
  "task_type": "DOCUMENT_REVIEW",
  "task_source": "BUSINESS_MODULE",
  "requester_type": "USER",
  "requester_id": "usr_001",
  "specialized_agent_code": "CONTRACT_REVIEW_AGENT",
  "input_context": {
    "business_module": "CONTRACT",
    "object_type": "CONTRACT",
    "object_id": "ctr_001",
    "session_scope": "CONTRACT_DETAIL"
  },
  "input_payload": {
    "question": "请给出合同风险提示",
    "document_id_list": [
      "doc_001"
    ]
  },
  "callback_url": "https://cmp-internal.example.com/callback/agent-task",
  "callback_secret_ref": "secret_agent_task_callback"
}
```

成功响应：

- 同步受理返回 `202`
- 已命中幂等且结果可复用时返回 `200` 或 `202`
- 响应体返回 `task_id`、初始 `task_status`、关联 `run_id`（如已创建）

```json
{
  "code": 0,
  "message": "accepted",
  "data": {
    "task_id": "agt_task_001",
    "task_status": "ACCEPTED",
    "run_id": "agt_run_001",
    "result_id": null
  }
}
```

### 2.7 Agent 运行会话 / 运行实例

- 资源：`AgentRun`
- 作用：暴露任务在某次执行中的运行实例，而不是暴露内部循环细节
- 设计原则：一个 `AgentTask` 可对应一个主运行实例；跨 Session 委派产生独立子运行实例

接口边界：

- `GET /api/agent-os/runs/{run_id}`：查询单个运行实例
- `GET /api/agent-os/tasks/{task_id}/runs`：查询任务关联运行实例列表
- `POST /api/agent-os/runs/{run_id}/cancel`：请求取消尚未结束的运行实例

`AgentRun` 对外可见字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `run_id` | string | 运行实例标识 |
| `task_id` | string | 所属任务标识 |
| `parent_run_id` | string | 父运行实例；主运行为空 |
| `run_status` | string | 运行状态，如 `PENDING`、`RUNNING`、`WAITING_HUMAN_CONFIRMATION`、`SUCCEEDED`、`FAILED`、`CANCELLED` |
| `agent_code` | string | 当前执行的 Agent 标识 |
| `session_id` | string | Session 标识；委派时为独立 Session |
| `started_at` | string | 开始时间 |
| `finished_at` | string | 结束时间 |
| `latest_checkpoint` | object | 最新对外可见检查点摘要 |
| `waiting_confirmation_id` | string | 当前等待的人工确认标识 |

`latest_checkpoint` 只保留 API 可见摘要，不展开内部思考链或 Prompt 内容。

### 2.8 Agent 结果查询

- 资源：`AgentResult`
- 作用：暴露任务或运行实例的可消费结果，而不是内部中间态全集

接口边界：

- `GET /api/agent-os/tasks/{task_id}/result`
- `GET /api/agent-os/results/{result_id}`

`AgentResult` 对外可见字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `result_id` | string | 结果标识 |
| `task_id` | string | 关联任务 |
| `run_id` | string | 产出该结果的运行实例 |
| `result_status` | string | 结果状态，如 `SUCCEEDED`、`PARTIAL`、`FAILED`、`REJECTED_BY_HUMAN` |
| `result_type` | string | 结果类型，如 `ANSWER`、`SUMMARY`、`SUGGESTION`、`STRUCTURED_EXTRACTION`、`ACTION_PLAN` |
| `output_text` | string | 面向调用方的结果文本摘要 |
| `output_payload` | object | 结构化结果对象 |
| `citation_list` | array | 可选引用对象清单 |
| `human_confirmation_required` | boolean | 该结果是否仍需人工确认后才能消费 |
| `is_written_back` | boolean | 是否已完成业务结果回写 |
| `written_back_at` | string | 结果回写时间 |

返回原则：

- 业务模块只消费 `AgentResult`，不直接消费 Provider 原始输出
- 高风险结果即使已生成，也可处于 `human_confirmation_required=true`
- 结构化结果字段采用稳定对象壳层，不承诺内部算法产物细节

### 2.9 人工确认接口

- 资源：`HumanConfirmation`
- 作用：为高风险动作、关键判断、越权访问和结果回写建立显式人工确认关口

接口边界：

- `GET /api/agent-os/human-confirmations/{confirmation_id}`
- `GET /api/agent-os/tasks/{task_id}/human-confirmations`
- `POST /api/agent-os/human-confirmations/{confirmation_id}/decisions`

正式写接口统一采用“提交裁决”语义，由请求体中的 `decision_action` 明确表达最终裁决动作，避免把路径命名与裁决种类绑定后只覆盖旧三分法。

请求体核心字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `decision_action` | string | 是 | 正式裁决动作，仅允许 `APPROVE`、`APPROVE_ONCE`、`APPROVE_IN_SCOPE`、`REJECT`、`REQUEST_CHANGES` |
| `decision_comment` | string | 是 | 处理意见摘要 |
| `decision_scope` | object | 否 | 当 `decision_action=APPROVE_IN_SCOPE` 时必填，表达对象范围、数据范围、步骤范围或时间窗口 |
| `client_decision_id` | string | 否 | 客户端侧幂等提交标识，用于避免重复裁决 |

请求体示例：

```json
{
  "decision_action": "APPROVE_IN_SCOPE",
  "decision_comment": "仅允许对合同 ctr_001 当前版本执行本次回写。",
  "decision_scope": {
    "object_type": "CONTRACT",
    "object_id": "ctr_001",
    "allowed_step": "WRITEBACK_CURRENT_RESULT",
    "expires_at": "2026-04-23T18:00:00Z"
  },
  "client_decision_id": "hc_decision_001"
}
```

`HumanConfirmation` 对外可见字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `confirmation_id` | string | 人工确认标识 |
| `task_id` | string | 关联任务 |
| `run_id` | string | 关联运行实例 |
| `confirmation_type` | string | 确认类型，如 `HIGH_RISK_ACTION`、`RESULT_RELEASE`、`DELEGATION_APPROVAL` |
| `confirmation_status` | string | 生命周期状态，如 `CREATED`、`PENDING`、`IN_REVIEW`、`DECIDED`、`EXPIRED`、`CANCELLED`、`REVOKED` |
| `decision_action` | string | 正式裁决动作，如 `APPROVE`、`APPROVE_ONCE`、`APPROVE_IN_SCOPE`、`REJECT`、`REQUEST_CHANGES`；未裁决前为空 |
| `confirmation_reason` | string | 触发人工确认的摘要理由 |
| `proposed_action` | object | 待确认动作摘要 |
| `proposed_result_ref` | object | 待确认结果引用 |
| `decision_comment` | string | 人工处理意见 |
| `decided_by` | string | 确认处理人 |
| `decided_at` | string | 确认时间 |

契约约束：

- `confirmation_status` 只表达确认单生命周期，不承接放行 / 驳回 / 要求修改等裁决语义
- `POST /decisions` 必须承接全部正式裁决动作；`APPROVE`、`APPROVE_ONCE`、`APPROVE_IN_SCOPE`、`REJECT`、`REQUEST_CHANGES` 都通过同一写接口提交
- 裁决提交成功后，系统写入对应 `decision_action`，并将 `confirmation_status` 推进到 `DECIDED`
- 当 `decision_action=APPROVE_IN_SCOPE` 时，必须同时固化 `decision_scope` 快照；当 `decision_action=APPROVE_ONCE` 时，作用范围默认收口到当前确认单绑定的目标对象与当前一次恢复动作
- 决策类接口必须记录处理意见摘要
- 已结束的确认单不可重复处理
- 确认结果必须影响对应 `AgentRun` 与 `AgentResult` 的可见状态

### 2.10 审计查询接口

- 资源：`AgentAuditView`
- 作用：提供分层审计查询视图；业务用户和业务管理端只可见最小披露摘要，完整运行回放、失败诊断和审计证据链归入运维审计 API，不直接暴露底层审计事件存储

接口边界：

- `GET /api/agent-os/audit-events`
- `GET /api/agent-os/tasks/{task_id}/audit-view`
- `GET /api/agent-os/runs/{run_id}/audit-view`

`AgentAuditView` 查询维度包括：

- 任务受理与发起主体
- 运行实例创建、结束、取消
- 人工确认创建与处理结果
- 委派发起、回收与合并
- 结果生成与结果回写
- 工具调用摘要与异常摘要
- 环境事件接入与处理结论

披露边界按 API 平面分层：外部业务 API 只返回与当前业务对象相关的状态摘要、处理结论、确认记录和必要引用；运维审计 API 才可在权限控制下查询运行回放、工具调用摘要、验证报告、失败证据和受控原文引用。任何平面都不得默认返回完整 Prompt、完整工具 schema、Memory 原文或子 Session 完整上下文。

审计查询返回的每条记录至少包含：

- `audit_event_id`
- `object_type`
- `object_id`
- `action_type`
- `action_summary`
- `actor_type`
- `actor_id`
- `result_status`
- `occurred_at`
- `trace_id`

### 2.11 环境输入接入接口

- 资源：`EnvironmentEvent`
- 作用：把业务模块外但对 Agent 判断有影响的运行时输入纳入正式 API 边界

接口边界：

- `POST /api/agent-os/environment-events`
- `GET /api/agent-os/environment-events/{event_id}`
- `GET /api/agent-os/tasks/{task_id}/environment-events`

`EnvironmentEvent` 核心字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `event_type` | string | 是 | 事件类型，如 `FILE_READ_ERROR`、`DB_QUERY_ERROR`、`EXTERNAL_CALLBACK_ERROR`、`TOOL_TIMEOUT`、`USER_FOLLOW_UP` |
| `event_source` | string | 是 | 来源系统或来源模块 |
| `severity` | string | 是 | 严重级别，如 `LOW`、`MEDIUM`、`HIGH`、`CRITICAL` |
| `related_task_id` | string | 否 | 关联任务 |
| `related_run_id` | string | 否 | 关联运行实例 |
| `event_payload` | object | 是 | 事件正文 |
| `agent_processing_required` | boolean | 否 | 是否需要直接转入 Agent 处理 |

边界约束：

- 该接口只定义事件接入与查询契约，不定义事件分类算法
- 环境输入可独立存在，也可与既有任务 / 运行实例关联
- 当 `agent_processing_required=true` 时，可由平台创建新的 `AgentTask`

### 2.12 多 Agent 委派接口边界

- 资源：`Delegation`
- 作用：表达主运行实例对其他 Agent 的跨 Session 委派，而不是同 Session 内人格切换

接口边界：

- `POST /api/agent-os/delegations`
- `GET /api/agent-os/delegations/{delegation_id}`
- `GET /api/agent-os/tasks/{task_id}/delegations`
- `POST /api/agent-os/delegations/{delegation_id}/recall`
- `POST /api/agent-os/delegations/{delegation_id}/merge-result`

`Delegation` 对外可见字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `delegation_id` | string | 委派标识 |
| `task_id` | string | 所属主任务 |
| `parent_run_id` | string | 发起委派的父运行实例 |
| `delegated_run_id` | string | 被委派运行实例 |
| `delegated_agent_code` | string | 被委派 Agent 标识 |
| `delegation_status` | string | 状态，如 `CREATED`、`RUNNING`、`WAITING_RECALL`、`RETURNED`、`MERGED`、`CANCELLED` |
| `delegation_goal` | string | 委派目标摘要 |
| `delegation_context` | object | 下发的最小必要上下文 |
| `result_summary` | string | 回收摘要 |
| `returned_at` | string | 回收时间 |

委派契约原则：

- 委派必须生成独立 `delegated_run_id` 与独立 `session_id`
- 委派输入只传递最小必要上下文，不复制全部长上下文
- 主运行实例只通过委派结果摘要和结构化回执消费子结果
- 委派 API 不返回子 Session 完整上下文、子 Agent Prompt 或子运行完整工具结果

## 3. 核心资源划分

| 资源 | 资源定位 | 对外用途 | 不承诺内容 |
| --- | --- | --- | --- |
| `AgentTask` | 统一任务受理对象 | 接收入站请求、承载任务级状态 | 不暴露内部执行步骤图 |
| `AgentRun` | 运行实例对象 | 表达某次执行、等待、取消、完成状态 | 不暴露内部思考链与 Prompt 正文 |
| `AgentResult` | 结果对象 | 向业务模块提供可消费结果 | 不直接暴露 Provider 原始响应 |
| `HumanConfirmation` | 人工确认对象 | 处理中高风险决策与结果放行 | 不定义内部风控评分算法 |
| `Delegation` | 跨 Session 委派对象 | 记录主运行与子运行的委派关系 | 不描述委派调度算法 |
| `EnvironmentEvent` | 环境输入对象 | 统一接入错误、追问、告警等事件 | 不描述内部分类与聚合算法 |
| `AgentAuditView` | 审计查询视图对象 | 向业务端与管理端提供最小披露留痕摘要；向运维审计面提供受控运行回放入口 | 不直接暴露底层审计表结构、完整 Prompt、完整工具 schema、Memory 原文或子 Session 完整上下文 |

资源关系约束：

- 一个 `AgentTask` 可关联零个或多个 `EnvironmentEvent`
- 一个 `AgentTask` 至少对应一个主 `AgentRun`
- 一个 `AgentRun` 可触发零个或多个 `HumanConfirmation`
- 一个主 `AgentRun` 可发起零个或多个 `Delegation`
- 一个 `AgentTask` 产生零个或一个最终 `AgentResult`，也可保留多个中间 `AgentResult`
- `AgentAuditView` 是查询视图，不作为独立写入入口

## 4. 统一约定

### 4.1 协议

- 继承 [`总平台 API Design`](../../api-design.md) 的统一约定，默认使用 `HTTPS + JSON`
- 编码采用 `UTF-8`
- 时间字段采用 `ISO 8601`
- 分页参数采用 `page`、`page_size`
- 成功、失败、分页响应结构继承总平台统一响应壳层

### 4.2 鉴权

- 业务客户端或管理端访问时，使用平台统一登录态与访问令牌
- 平台内服务访问时，使用服务账号或系统间凭证
- 回调接口与环境输入接口必须支持签名校验、时间戳校验与重放防护
- 审计查询、人工确认、委派回收等敏感接口必须额外校验操作权限和数据权限

### 4.3 幂等

- `POST /api/agent-os/tasks`、`POST /api/agent-os/environment-events`、`POST /api/agent-os/delegations`、
  人工确认处理类接口应支持 `Idempotency-Key`
- 相同幂等键且请求体一致时，返回首个受理结果
- 相同幂等键但请求体不一致时，复用总平台 `40905 IDEMPOTENCY_CONFLICT`

### 4.4 命名规范

- 路径资源段统一使用 `kebab-case`；路径参数、请求字段、响应字段、查询参数统一使用 `snake_case`
- 主键字段遵循 `<resource>_id`，如 `task_id`、`run_id`、`result_id`
- 状态字段采用 `<domain>_status`，如 `task_status`、`run_status`、`result_status`
- 枚举值使用 `UPPER_SNAKE_CASE`
- 命名规范整体继承 [`总平台 API Design`](../../api-design.md)

### 4.5 错误码复用策略

`Agent OS` 子模块默认复用总平台错误码体系，不单独创造第二套通用错误域。

优先复用的错误码包括：

- 通用校验：`40001 INVALID_PAYLOAD`、`40002 INVALID_FIELD_VALUE`、`40003 INVALID_QUERY_PARAMS`
- 鉴权与权限：`40101 AUTH_REQUIRED`、`40102 AUTH_TOKEN_INVALID`、`40103 CALLBACK_SIGNATURE_INVALID`、`40301 PERMISSION_DENIED`
- 幂等与状态冲突：`40905 IDEMPOTENCY_CONFLICT`
- 平台异常：`50001 INTERNAL_SERVER_ERROR`、`50201 EXTERNAL_SYSTEM_UNAVAILABLE`、`50301 ASYNC_JOB_BACKLOG`
- 智能任务失败：`42205 AI_TASK_FAILED`

本模块补充专属业务错误码时，应只新增 `Agent OS` 领域语义，而不复制总平台已有错误语义。
建议保留以下子模块专属错误：

| HTTP 状态码 | 业务错误码 | 错误名称 | 使用场景 |
| --- | --- | --- | --- |
| `404` | `40411` | `AGENT_TASK_NOT_FOUND` | `task_id` 不存在 |
| `404` | `40412` | `AGENT_RUN_NOT_FOUND` | `run_id` 不存在 |
| `404` | `40413` | `HUMAN_CONFIRMATION_NOT_FOUND` | `confirmation_id` 不存在 |
| `404` | `40414` | `DELEGATION_NOT_FOUND` | `delegation_id` 不存在 |
| `409` | `40911` | `AGENT_RUN_STATUS_CONFLICT` | 当前运行状态不允许取消、回收或回写 |
| `409` | `40912` | `HUMAN_CONFIRMATION_STATUS_CONFLICT` | 已处理或已过期确认单再次处理 |
| `409` | `40913` | `DELEGATION_STATUS_CONFLICT` | 委派已结束或不可回收 |
| `422` | `42211` | `HUMAN_CONFIRMATION_REQUIRED` | 任务结果尚需人工确认，不可直接释放 |
| `422` | `42212` | `ENVIRONMENT_EVENT_UNPROCESSABLE` | 环境事件合法但无法路由为可处理任务 |

## 5. 与业务模块 / 环境输入的接口边界

### 5.1 与业务模块的接口边界

- 业务模块通过 `AgentTask` 提交任务，通过 `AgentResult` 消费结果
- 业务模块提交的是业务上下文、问题描述、对象引用和结果接收需求，不提交模型参数、SDK 细节、Prompt 正文、完整工具 schema 或 Memory 原文
- 业务模块可查询运行状态、人工确认状态、结果状态和审计摘要
- 业务模块不得绕过 `Agent OS` 直接访问内部运行态或 Provider 原始返回

### 5.2 与环境输入的接口边界

- 环境输入统一通过 `EnvironmentEvent` 接口进入 `Agent OS`
- 环境输入可来自文件系统、数据库、工具执行、外部回调、用户追问或运行时观测
- 环境输入进入后可仅作为审计留痕，也可进一步派生新的 `AgentTask`
- 环境输入接口不承诺事件归并、优先级排序和自动处置算法

## 6. 与工具系统 / 模型 Provider 的接口边界

### 6.1 API 可见边界

- `Agent OS` 对业务调用方只暴露任务、运行、结果、确认、委派、审计等平台语义
- 模型与 Provider 被视为 `Agent OS` 内部可调度工具能力，不直接成为外部 API 资源
- 对外 API 不要求调用方传入具体模型名、Provider SDK 参数或底层路由策略
- 如需表达能力偏好，只允许传递平台级能力提示字段，如 `specialized_agent_code`、`task_type`、结果格式要求等稳定契约
- 工具、Prompt、Memory、Provider 管理能力统一归入内部控制面，不通过业务 API 提供写入口

### 6.2 工具调用结果的对外契约

- 工具调用细节不作为独立外部写接口暴露
- 审计查询中只返回工具调用摘要、结果状态、异常摘要与追踪标识
- Provider 原始响应、Prompt 片段、工具内部参数不属于默认返回内容
- 如业务确需引用工具证据，应通过 `citation_list`、`action_summary` 或结果引用对象暴露

## 7. 多 Agent 跨 Session 委派的 API 边界

- 委派是独立资源，不内嵌为 `AgentRun` 的隐式字段行为
- 被委派 Agent 必须运行在独立 `session_id` 下
- 主运行只能通过 `Delegation` 查询子运行状态、回收结果摘要、触发结果合并
- 委派结果合并只承诺“摘要回收 + 结构化引用”，不承诺复制完整子 Session 全量上下文
- 跨 Session 委派的审计必须可按 `delegation_id`、`parent_run_id`、`delegated_run_id` 追溯

## 8. 人工确认与结果回写接口边界

- 高风险结果、关键动作建议、委派放行等场景可生成 `HumanConfirmation`
- 未经确认的结果可以存在，但其 `human_confirmation_required` 必须为 `true`
- 人工确认完成后，可由 `Agent OS` 执行结果释放或结果回写
- 结果回写对业务模块表现为 `AgentResult.is_written_back` 状态变化与对应审计记录
- 本文只定义“是否允许回写、何时可见、如何查询”的 API 边界，不展开内部补偿逻辑与事务编排

## 9. 异步任务与回调边界

### 9.1 异步任务边界

- `AgentTask` 默认按异步任务受理，创建成功返回 `202`
- 调用方通过任务、运行或结果查询接口获取最终状态
- 长耗时任务、委派任务、等待人工确认任务均保持异步语义
- 是否拆成多个内部执行步骤属于详细设计，不进入本文

### 9.2 回调边界

- 当调用方提供 `callback_url` 时，`Agent OS` 可在任务终态或关键状态变化时回调
- 回调只承诺发送标准任务结果通知，不承诺发送内部全量执行过程
- 回调请求头必须包含签名、时间戳和追踪标识
- 回调消息体建议包含：`task_id`、`run_id`、`result_id`、`task_status`、`result_status`、`trace_id`

回调消息体示例：

```json
{
  "task_id": "agt_task_001",
  "run_id": "agt_run_001",
  "result_id": "agt_result_001",
  "task_status": "SUCCEEDED",
  "result_status": "SUCCEEDED",
  "trace_id": "req_20260405_xxx"
}
```

## 10. 需要下沉到该模块 Detailed Design 的内容边界

以下内容不在本文展开，应下沉到后续 `Agent OS Detailed Design`：

- `AgentTask`、`AgentRun`、`Delegation`、`HumanConfirmation` 的内部状态机
- `QueryEngine Runtime Loop` 的步骤推进与检查点持久化策略
- Prompt 装配、静态底座与动态注入的内部模型
- 工具路由、模型 / Provider 选择、降级与重试算法
- 记忆分层、怀疑式校验、后台反思与经验沉淀机制
- 人工确认触发规则、回写补偿机制、审计事件落库模型
- 委派摘要裁剪策略、跨 Session 上下文裁剪规则与合并策略

## 11. 本文结论

`Agent OS` 子模块 API 以外部业务 API、内部控制面 API、运维审计 API 三个平面划分边界。外部业务 API 以 `AgentTask`、`AgentRun`、`AgentResult`、`HumanConfirmation`、`Delegation`、`EnvironmentEvent`、`AgentAuditView` 为统一资源边界，对外只暴露平台级任务与结果契约。

这样可以保证：

- 业务模块面向稳定平台语义接入 AI 能力，而非面向某个模型 SDK 编程
- 模型、工具、Prompt、Memory 与 Provider 管理被收口为内部控制面能力，不污染对外 API 契约
- 多 Agent 协作建立在跨 Session 委派之上，边界清晰且可审计
- 审计、指标、运行回放、失败诊断、成本配额和验证报告进入运维审计面
- 人工确认、结果回写、异步回调和环境事件都进入统一可追踪接口面

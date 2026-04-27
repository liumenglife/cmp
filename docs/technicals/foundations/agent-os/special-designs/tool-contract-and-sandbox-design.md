# Agent OS 子模块专项设计：Tool Registry / ToolSearch / Sandbox Executor / Output Offloader

## 1. 文档说明

本文档下沉 `Agent OS` 的工具运行时机制，说明模型、检索、规则、文件、数据库、外部服务和平台动作如何通过统一工具契约进入 `QueryEngine` 每轮循环。

本文是 [`Agent OS Detailed Design`](../detailed-design.md) 的下游专项设计，直接承接 `Tool Decision -> Guard Chain -> Sandbox Executor -> Observation Normalize` 段。

本文不展开以下内容：

- 不写具体 SDK 封装、类名、函数签名、容器镜像或部署脚本。
- 不定义对外 API 路径、请求体、响应体或错误码。
- 不把单一模型、数据库、中间件或第三方服务私有协议写成平台标准。
- 不定义控制台页面或人工审批流实现。

## 2. 运行时四组件

工具运行时由四个组件承接。

| 组件 | 职责 | 对 QueryEngine 的输出 |
| --- | --- | --- |
| `Tool Registry` | 管理工具元数据、风险等级、schema 引用、沙箱策略和输出卸载策略 | 可发现的工具目录与注册快照。 |
| `ToolSearch` | 根据 `search_hint` 按需载入完整 schema，形成定义快照和授权前置材料 | 可用于授权检查的工具定义快照。 |
| `Sandbox Executor` | 在授权和安全链通过后执行工具，落实数据、网络、文件、进程、时间、成本边界 | `ToolInvocationEnvelope` 与执行结果。 |
| `Output Offloader` | 对超长结果、敏感结果和大 artifact 做摘要 + 引用卸载 | 可进入观察和 Prompt 的有限结果摘要。 |

四组件共同保证工具不是 Prompt 内的自由文本能力，而是可注册、可发现、可授权、可隔离、可卸载、可审计的运行时能力。

## 3. 保留的四类契约对象

### 3.1 ToolDefinition

`ToolDefinition` 描述工具是什么、能做什么、风险在哪里、完整 schema 在哪里。

### 3.2 ToolGrant

`ToolGrant` 描述某次运行当前被允许调用哪些工具、可触达哪些资源、可执行哪些动作，以及授权来源。

### 3.3 ToolInvocationEnvelope

`ToolInvocationEnvelope` 描述实际调用的输入、运行阶段、授权快照、预算、沙箱策略和审计上下文。

### 3.4 ToolResultEnvelope

`ToolResultEnvelope` 描述工具返回、失败、拒绝、需确认、输出卸载和副作用摘要。

## 4. Tool Registry

### 4.1 注册字段语义

工具注册表必须稳定表达以下字段：

| 字段 | 语义 |
| --- | --- |
| `tool_name` | 工具唯一名称，供调用、审计和 Prompt 线索引用。 |
| `tool_family` | 工具家族，如 `MODEL`、`SEARCH`、`FILE`、`DB`、`HTTP`、`RULE`。 |
| `risk_level` | 默认风险等级，如 `L1_READONLY`、`L2_CONTROLLED_COMPUTE`、`L3_GUARDED_WRITE`、`L4_DANGEROUS_ACTION`。 |
| `search_hint` | 低频或扩展工具进入 Prompt 的发现线索。 |
| `schema_ref` | 完整输入 / 输出 schema 的受控引用。 |
| `definition_stability` | 定义稳定性，区分内置稳定工具与高变更扩展工具。 |
| `cache_order_group` | 进入 `Prompt Assembly` 静态前缀时的排序组。 |
| `sandbox_policy_ref` | 执行沙箱策略引用。 |
| `offload_policy_ref` | 输出卸载策略引用。 |

### 4.2 注册分层

- 内置工具：完整定义可进入 `Prompt Assembly` 静态前缀，按 `Tool Definition Ordering` 固定排序。
- 扩展工具：Prompt 只注入 `search_hint`，完整 schema 由 `ToolSearch` 按需载入。
- 高风险工具：必须声明沙箱策略、输出卸载策略、人工确认策略和最小授权边界。

合同管理平台中的典型企业业务工具包括：合同文档读取、条款抽取、审批流查询、签署状态查询、履约节点查询、风控规则命中和审计证据导出。它们仍按统一工具契约注册，不因业务语义绕过 `ToolSearch`、授权检查、`Runtime Sandbox` 或 `Output Offloader`。

## 5. ToolSearch

`ToolSearch` 负责从线索到可调用定义的按需发现，不允许模型凭 `search_hint` 直接调用扩展工具。

### 5.1 状态机

```text
DISCOVER_HINT -> REQUEST_SCHEMA -> SNAPSHOT_DEFINITION -> GRANT_CHECK -> READY_TO_INVOKE
```

| 状态 | 语义 | 输出 |
| --- | --- | --- |
| `DISCOVER_HINT` | 根据任务和 Prompt 中的 `search_hint` 发现候选工具 | 候选工具名称与风险摘要。 |
| `REQUEST_SCHEMA` | 从 `Tool Registry` 获取完整 schema 和策略引用 | 工具定义草案。 |
| `SNAPSHOT_DEFINITION` | 固化本轮看到的工具定义 | `tool_definition_snapshot_ref`。 |
| `GRANT_CHECK` | 结合人格、任务、资源、风险和确认策略做授权检查 | `ToolGrant` 或拒绝原因。 |
| `READY_TO_INVOKE` | 工具可以进入调用 envelope 构造 | 可执行工具定义快照。 |

### 5.2 发现约束

- `ToolSearch` 输出必须进入审计，保证可回答“当时看见了哪个 schema”。
- schema 变化不得影响已形成的定义快照。
- 未通过 `GRANT_CHECK` 的工具不得进入 `Sandbox Executor`。

## 6. 安全硬链路

工具动作执行前必须经过固定安全硬链路：

```text
Deterministic Guard -> LLM Judge -> Human Confirmation -> Runtime Sandbox
```

| 层级 | 职责 | 结果 |
| --- | --- | --- |
| `Deterministic Guard` | 硬规则、白名单、黑名单、资源句柄、注入风险、路径逃逸、批量删除、凭据读取等确定性拦截 | 放行、拒绝或升级。 |
| `LLM Judge` | 仅处理确定性规则无法充分判断的语义风险和意图风险 | 放行建议、拒绝建议或转人工确认。 |
| `Human Confirmation` | 安全硬链路第 3 层，只处理仍无法自动收口且继续会放大风险的事项 | 暂停、放行、要求修改、拒绝或过期降级。 |
| `Runtime Sandbox` | 最终执行隔离，落实数据、网络、文件、进程、时间和成本边界 | 执行结果、沙箱拒绝或超时。 |

人工确认不是高风险动作默认路径。能被确定性规则或 LLM Judge 明确拒绝的动作必须直接拒绝；能安全放行的动作不得为了形式进入人工确认。

## 7. Sandbox Executor

### 7.1 输入 envelope

`ToolInvocationEnvelope` 至少包含：

- `tool_name`、`tool_family`、`tool_definition_snapshot_ref`。
- `run_id`、`task_id`、`prompt_snapshot_id`、`trace_id`。
- `caller_stage`：记录触发工具调用的 `QueryEngine` 状态，如 `TOOL_DECIDING`、`GUARD_CHECKING`、`SANDBOX_EXECUTING`、`OBSERVATION_NORMALIZING`；`P-A-O` 只可作为语义阶段标签，不作为运行态枚举。
- `grant_snapshot_ref`。
- `input_payload` 与 `input_schema_version`。
- `timeout_budget`、`quota_bucket`、`sandbox_policy_ref`、`offload_policy_ref`。

### 7.2 沙箱边界

`Runtime Sandbox` 至少覆盖：

- 数据边界：可读 / 可写数据域、密级、对象范围。
- 网络边界：允许域名、内部服务范围、出口策略。
- 文件边界：平台句柄和工作区限制。
- 进程边界：子进程、长期驻留、并发扩散限制。
- 时间边界：单次超时、单轮累计预算、整次运行预算。
- 成本边界：调用次数、token、外部计费、数据量。

### 7.3 失败分类

统一失败分类：

- `CONTRACT_INVALID`
- `AUTH_REJECTED`
- `SANDBOX_REJECTED`
- `QUOTA_EXCEEDED`
- `RUNTIME_FAILURE`
- `HUMAN_CONFIRMATION_REQUIRED`

`SANDBOX_REJECTED` 不允许自动绕过；只能形成观察、转人工确认、请求修改动作或终止运行。

## 8. Output Offloader

### 8.1 卸载触发

以下结果必须进入 `Output Offloader`：

- 超过本轮上下文预算的长文本、日志、列表或文档。
- 含敏感正文、凭据、个人信息或业务秘密的结果。
- 二进制 artifact、附件、导出物或 Provider 原始响应。
- 失败输出、拒绝原因和超时上下文中的长载荷。

### 8.2 输出形式

卸载后进入 `ToolResultEnvelope` 的内容只包括：

- 摘要。
- 条数、行数、页数或片段范围。
- 截断 / 卸载原因。
- `artifact_ref` / `evidence_ref`。
- 需要继续读取时的定向召回提示。

`Prompt Assembly` 只能消费卸载后的摘要与引用，不得把原始长结果直接放入动态后缀。

## 9. ToolResultEnvelope

结果 envelope 至少表达：

- `result_status`：`SUCCEEDED`、`FAILED`、`REJECTED`、`TIMED_OUT`、`PARTIAL`、`NEEDS_CONFIRMATION`。
- `result_class`：读取观察、动作建议、动作执行、结构化抽取、artifact、失败观察。
- `normalized_payload`。
- `payload_schema_ref`。
- `evidence_ref_list`。
- `side_effect_summary`。
- `resource_usage`。
- `failure_class` 与 `failure_code`。
- `confirmation_required`。
- `retryable`。

## 10. 审计要求

每次工具调用至少能回答：

- 工具如何被发现，完整 schema 来自哪个快照。
- 为什么授权、拒绝或转人工确认。
- 命中了哪些安全链路判断和沙箱策略。
- 输入、输出、失败和副作用如何被标准化。
- 哪些原始结果被卸载到引用，哪些摘要进入观察。
- 本次调用如何影响下一轮 `QueryEngine` 状态。

## 11. 与其他机制的关系

### 11.1 与 Prompt Assembly

`Prompt Assembly` 只注入内置工具完整定义和扩展工具 `search_hint`。扩展工具完整 schema 必须通过 `ToolSearch` 获取。

### 11.2 与 Provider

模型能力作为 `tool_family=MODEL` 的工具注册和执行；Provider 路由作为模型工具调用前的预算门和候选选择机制。

### 11.3 与 Human Confirmation

人工确认是安全硬链路第 3 层。确认通过后仍必须进入 `Runtime Sandbox`，不能绕过执行隔离。

## 12. 本文结论

工具机制由 `Tool Registry / ToolSearch / Sandbox Executor / Output Offloader` 四组件承接，并保留 `ToolDefinition / ToolGrant / ToolInvocationEnvelope / ToolResultEnvelope` 四类契约对象。扩展工具通过 `search_hint` 发现、由 `ToolSearch` 按需载入 schema；所有动作经过 `Deterministic Guard -> LLM Judge -> Human Confirmation -> Runtime Sandbox` 安全硬链路，结果由 `Output Offloader` 控制进入上下文的大小和敏感边界。

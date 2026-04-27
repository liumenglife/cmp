# Agent OS 子模块专项设计：Human Confirmation Pause / Resume

## 1. 文档说明

本文档下沉 `Agent OS` 的人工确认机制，说明安全硬链路第 3 层如何把 `QueryEngine` 暂停到 `WAITING_HUMAN_CONFIRMATION`，并在人工决策后恢复、修改、终止、降级或转人工接管。

本文是 [`Agent OS Detailed Design`](../detailed-design.md) 的下游专项设计，直接承接 `Guard Chain -> Human Confirmation -> Runtime Sandbox` 与 `WAITING_HUMAN_CONFIRMATION` 恢复语义。

本文不展开以下内容：

- 不写控制台页面路由、组件树、按钮交互稿或通知通道配置。
- 不定义对外 API 路径、请求体、响应体或错误码。
- 不把确认处理流程写成运营排班、上线手册或业务审批流。
- 不定义数据库迁移脚本、消息主题或状态机代码。

## 2. 运行时定位

人工确认是安全硬链路第 3 层：

```text
Deterministic Guard -> LLM Judge -> Human Confirmation -> Runtime Sandbox
```

它不是高风险动作的默认路径。确定性规则能拒绝的动作必须拒绝；能安全放行的动作必须继续；只有前两层无法充分收口，且继续自动执行会放大业务、权限、合规或外部副作用风险时，才进入人工确认。

进入人工确认时，`QueryEngine` 必须暂停，写入检查点和确认单引用，不允许隐式继续。

## 3. 确认单对象模型

人工确认保留“确认单主对象 + 生命周期 + 决策动作 + 证据引用 + 恢复策略”结构。

### 3.1 确认单主对象

确认单以 `ao_human_confirmation` 为真相源，至少表达：

- `confirmation_id`、`task_id`、`run_id`、`result_id`。
- `confirmation_type`：结果放行、危险动作授权、委派批准、权限升级例外、冲突裁决等。
- `confirmation_source`：工具动作、结果回写、委派合并、记忆冲突、Provider 例外等。
- `target_ref`：本次确认真正作用的对象。
- `impact_scope`：对象、数据域、组织、外部动作、时间窗口和后续步骤。
- `trigger_reason`：归一化触发理由。
- `proposed_action_summary`：待放行或待拒绝动作摘要。
- `evidence_bundle_ref`：证据包引用。
- `decision_policy_snapshot`：确认创建时命中的策略快照。

合同管理平台中的确认单业务例子包括：高风险条款放行、审批例外、签署异常处置、履约风险处置和法务复核。确认单只裁决本次 Agent 运行中的动作或结果放行，不替代合同主流程中的正式业务审批。

### 3.2 生命周期

确认单生命周期由 `confirmation_status` 承接：

```text
CREATED -> PENDING -> IN_REVIEW -> DECIDED
                             -> EXPIRED
CREATED/PENDING/IN_REVIEW -> CANCELLED
DECIDED -> REVOKED
```

生命周期只表达确认单流转，不表达批准、拒绝或要求修改。

### 3.3 决策动作

裁决由 `decision_action` 承接：

- `APPROVE`
- `APPROVE_ONCE`
- `APPROVE_IN_SCOPE`
- `REQUEST_CHANGES`
- `REJECT`

决策动作必须带处理人、处理时间、处理意见、作用范围和恢复策略。

## 4. 暂停语义

创建确认单时，运行必须进入暂停态：

- `AgentRun.run_status = WAITING_HUMAN_CONFIRMATION`。
- `AgentRun.runtime_state` 保留进入确认前的阶段，如 `GUARD_CHECKING` 或 `TOOL_DECIDING`。
- 检查点记录候选动作、风险、证据、预算影响和恢复入口。
- `PromptSnapshot`、`ToolInvocationEnvelope`、`Guard Chain` 结论和证据包形成确认单上下文。

暂停态禁止默认放行、默认重试和绕过安全链路调用工具。

## 5. 确认恢复状态机

人工裁决后，`QueryEngine` 按固定恢复状态机处理：

```text
APPROVE -> GUARD_CHECKING/SANDBOX_EXECUTING/OBSERVATION_NORMALIZING
REQUEST_CHANGES -> INGRESS_PENDING/CONTEXT_GOVERNING
REJECT -> FAILED/CANCELLED
EXPIRED -> FAILED/CANCELLED/人工接管
```

| 输入 | 恢复目标 | 规则 |
| --- | --- | --- |
| `APPROVE` / `APPROVE_ONCE` / `APPROVE_IN_SCOPE` | `GUARD_CHECKING`、`SANDBOX_EXECUTING` 或 `OBSERVATION_NORMALIZING` | 只在批准范围内恢复；恢复后仍必须进入 `Runtime Sandbox`。 |
| `REQUEST_CHANGES` | `INGRESS_PENDING` 或 `CONTEXT_GOVERNING` | 人工意见作为新观察输入，重新进入任务解释、上下文治理和规划。 |
| `REJECT` | `FAILED` 或 `CANCELLED` | 拒绝当前动作或结果；按任务策略终止或取消。 |
| `EXPIRED` | `FAILED`、`CANCELLED` 或人工接管 | 不默认同意；按策略降级、转人工接管或终止。 |

## 6. 快照字段语义

确认单和恢复检查点必须补充以下字段语义：

| 字段 | 语义 |
| --- | --- |
| `guard_chain_snapshot_id` | 触发确认前的安全硬链路判断快照，覆盖确定性规则与 LLM Judge 结论。 |
| `llm_judge_summary_ref` | LLM Judge 的风险摘要引用；未触发 LLM Judge 时记录为空和原因。 |
| `sandbox_policy_ref` | 决策通过后将使用的沙箱策略引用，确保确认不绕过 `Runtime Sandbox`。 |
| `runtime_resume_state` | 决策后恢复到的 `QueryEngine` 状态，如 `GUARD_CHECKING`、`SANDBOX_EXECUTING`、`OBSERVATION_NORMALIZING`、`INGRESS_PENDING`。 |
| `budget_impact_summary` | 等待确认、重试、降级、沙箱执行对 `QueryEngine Round Budget` 的影响摘要。 |

这些字段用于运行回放、责任追溯和失败恢复，不作为业务 API 默认返回正文。

## 7. 证据包与控制台边界

确认单证据包以摘要和引用组织，至少包含：

- 触发确认的动作或结果摘要。
- `guard_chain_snapshot_id`。
- `llm_judge_summary_ref`。
- 工具定义快照、授权快照、资源范围和风险标签。
- `sandbox_policy_ref` 和可回滚 / 不可回滚说明。
- `budget_impact_summary`。
- 相关业务对象、结果对象、委派对象或记忆冲突引用。

控制台工作台只承接待确认、处理中、超时、已决策回查和人工接管入口；不维护业务对象主数据，不复制业务模块编辑界面，不默认展示 Prompt 原文、Provider 私参、Memory 原文或完整工具 schema。

## 8. 批量确认边界

批量确认只适用于确认类型、影响范围、风险等级、证据结构和建议动作足够同构的事项。

批量处理规则：

- 任一项存在不同资源范围、不可逆副作用或例外条件时不得混批。
- 批量放行优先使用 `APPROVE_ONCE` 或受限 `APPROVE_IN_SCOPE`。
- 每项必须保留独立决策动作、处理意见、恢复状态和审计记录。
- 高价值对象、跨组织数据和外部不可逆动作可强制单独确认。

## 9. 超时与人工接管

`EXPIRED` 表示未决，不表示同意。

超时策略只能选择：

- 终止当前动作或运行。
- 降级到低风险路径。
- 重新生成确认单并调整处理责任人。
- 转人工接管，并在检查点记录接管原因和责任边界。

人工接管回答“这条运行链路是否需要人接手后续处理”。当确认反复超时、证据长期冲突、自动路径无法收敛或副作用风险持续扩大时，运行可进入人工接管。控制台提供接管入口、上下文摘要和追溯链路，不在本文定义接管后的业务作业流程。

## 10. 撤销与追溯

`REVOKED` 作用于已形成的裁决，而不是删除确认单。撤销必须保留：

- 原裁决动作和作用范围。
- 撤销原因、撤销主体、撤销时间。
- 已产生副作用的补偿、追认或风险告警链路。
- 受影响运行、结果、工具调用和审计事件引用。

系统必须能回答：为什么转人工、谁基于哪些证据做了什么决策、恢复到了哪个运行态、是否经过沙箱、后续是否撤销或接管。

## 11. 与其他机制的关系

### 11.1 与 Tool

人工确认是安全硬链路第 3 层。批准后仍必须进入 `Runtime Sandbox`，并按工具契约生成 `ToolInvocationEnvelope` 与 `ToolResultEnvelope`。

### 11.2 与 Provider

确认等待和恢复会消耗或影响 `QueryEngine Round Budget`，必须写入 `budget_impact_summary`。预算不足本身不默认触发人工确认，除非需要例外授权或影响正式结果放行。

### 11.3 与 Prompt Assembly

确认单只以摘要进入 `Human Gate Brief`。未决确认不得被 Prompt 表达为已批准事实。

### 11.4 与 Memory

人工裁决可作为高优先级记忆来源进入 `Memory Intake`，但仍需经过准入硬规则、作用域、时效和撤销边界判断。

## 12. 本文结论

人工确认是安全硬链路第 3 层和 `QueryEngine` 的暂停 / 恢复机制，不是高风险动作默认路径。确认单保留生命周期和决策动作分层，决策后按 `APPROVE -> GUARD_CHECKING/SANDBOX_EXECUTING/OBSERVATION_NORMALIZING`、`REQUEST_CHANGES -> INGRESS_PENDING/CONTEXT_GOVERNING`、`REJECT -> FAILED/CANCELLED`、`EXPIRED -> FAILED/CANCELLED/人工接管` 恢复，并通过 `guard_chain_snapshot_id`、`llm_judge_summary_ref`、`sandbox_policy_ref`、`runtime_resume_state`、`budget_impact_summary` 保证可审计、可恢复、可追溯。

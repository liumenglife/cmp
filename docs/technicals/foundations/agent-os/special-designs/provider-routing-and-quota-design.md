# Agent OS 子模块专项设计：Provider Round Budget Gate

## 1. 文档说明

本文档下沉 `Agent OS` 的 Provider 路由与配额机制，说明模型类工具在 `QueryEngine` 每轮循环中如何经过预算门、配额、速率、熔断和降级策略后被选择。

本文是 [`Agent OS Detailed Design`](../detailed-design.md) 的下游专项设计，直接承接 `Prompt Assembly -> Provider Budget Check -> Model Call` 段。

本文不展开以下内容：

- 不写具体厂商 SDK、密钥注入、连接池、线程模型或部署脚本。
- 不定义对外 API 字段、接口路径或错误码。
- 不把单一 Provider 的私有参数、计费口径或错误码写成平台标准。
- 不定义模型评测基准和验证报告格式。

## 2. 运行时定位

Provider 路由不是“选择一家模型供应商”，而是 `QueryEngine` 每轮模型工具调用前的预算门。

核心输入为：

- `PromptSnapshot`：由 `Prompt Assembly` 输出，包含上下文估算、静态 / 动态 digest、输出契约和结构化要求。
- `QueryEngine Round Budget`：本轮可消耗的成本、token、延迟、速率、风险和降级边界。
- `CapabilityProfile`：本轮需要的模型能力语义。
- Provider / Model 健康、配额、速率和熔断状态。

核心输出为可调用的 `Provider + Model`、降级路径、等待策略或受控失败结论。

## 3. Provider / Model / Capability Profile

保留三层画像抽象。

### 3.1 ProviderProfile

表达供应商接入主体、区域、可用性、计费归属、合规标签、熔断状态和治理状态。

### 3.2 ModelProfile

表达模型实例的上下文窗口、能力类型、质量档位、结构化可靠性、成本档位、延迟档位、吞吐限制和当前健康度。

### 3.3 CapabilityProfile

表达平台真正需要的能力：文本生成、结构化提取、长上下文总结、嵌入、重排、低成本草稿、高可靠结构化输出等。

运行时优先面向 `CapabilityProfile` 路由，再映射到候选 `Provider + Model`。

## 4. QueryEngine Round Budget

`QueryEngine Round Budget` 是每轮预算门的正式输入，不等同于任务总预算。

| 字段 | 语义 |
| --- | --- |
| `round_token_in_budget` | 本轮允许输入 token 或等效上下文成本。 |
| `round_token_out_budget` | 本轮允许输出 token 或等效生成成本。 |
| `round_cost_budget` | 本轮可消耗费用上限。 |
| `round_latency_budget` | 本轮同步等待时间上限。 |
| `round_retry_budget` | 本轮允许重试次数。 |
| `round_degrade_level` | 当前允许降级层级。 |
| `context_window_required` | `PromptSnapshot` 估算出的上下文窗口需求。 |
| `structure_reliability_required` | 结构化输出最低可靠性要求。 |
| `risk_level` | 本轮动作或结果风险等级。 |
| `tenant_quota_ref` | 租户预算与配额桶引用。 |
| `run_budget_ref` | 任务 / 运行总预算引用。 |

每轮预算必须写入审计和成本归集，重试、主备切换和降级也消耗预算。

## 5. 预算门状态机

Provider 选择采用固定状态机：

```text
BUDGET_ESTIMATE -> ROUTE_CANDIDATE -> QUOTA_CHECK -> RATE_CHECK -> CIRCUIT_CHECK -> SELECT_OR_DEGRADE -> ACCOUNT_USAGE
```

| 状态 | 输入 | 输出 | 约束 |
| --- | --- | --- | --- |
| `BUDGET_ESTIMATE` | `PromptSnapshot`、上下文估算、输出契约 | `QueryEngine Round Budget` 估算 | 估算失败不得调用模型。 |
| `ROUTE_CANDIDATE` | 能力需求、人格边界、合规标签 | 候选 `Provider + Model` 集合 | 候选必须满足能力和合规底线。 |
| `QUOTA_CHECK` | 候选集合、租户 / 任务 / 人格配额 | 通过或拒绝原因 | 配额不足进入降级或等待，不得旁路。 |
| `RATE_CHECK` | 速率桶、并发限制、Provider 限流 | 通过、退避或切换建议 | 短时限流优先退避或备路由。 |
| `CIRCUIT_CHECK` | 熔断、黑名单、健康检查 | 可用候选集合 | 熔断候选不得被选中。 |
| `SELECT_OR_DEGRADE` | 可用候选、降级策略 | 最终路由、降级路径或失败 | 降级不得突破安全、合规和结果契约。 |
| `ACCOUNT_USAGE` | 实际调用和消耗 | 成本归集与审计记录 | 必须记录实际消耗、归属和策略版本。 |

## 6. 降级策略表

降级必须显式记录触发条件、动作、结果影响和审计要求。

| 触发条件 | 降级动作 | 结果边界 | 审计要求 |
| --- | --- | --- | --- |
| 预算不足 | 切换同能力低成本模型；缩短输出预算；转异步批处理 | 不得降低安全要求；结果可能标记为部分成功或低置信。 | 记录预算缺口、候选成本和选中路径。 |
| 长上下文过大 | 请求 `Context Governor` 进一步压缩；转摘要后推理；拆分委派 | 不得裁掉静态底座和工具 / 结果配对。 | 记录 `context_governor_snapshot_id` 与裁剪摘要。 |
| 结构化可靠性不足 | 切换高结构化可靠性模型；转规则 + 检索；增加验证步骤 | 不得把低可靠结构化结果当正式结果。 | 记录可靠性要求、模型画像和验证要求。 |
| Provider 熔断 | 切换备 Provider；切换同 Provider 其他模型；回退非模型路径 | 熔断对象不得继续被调用。 | 记录熔断粒度、触发信号和备路由结果。 |
| 速率受限 | 退避等待；排队；切换高吞吐备路由；转异步 | 同步场景超时需返回受控等待或降级状态。 | 记录速率桶、等待时长和切换原因。 |

无安全降级路径时，预算门返回受控失败，`QueryEngine` 进入终止、人工接管或委派路径。

业务风险也会影响路由口径：高金额合同、法律风险条款、审批例外、签署失败和履约逾期等场景应提高结构化可靠性、证据引用完整性和验证要求；低风险摘要或常规状态解释可选择低成本草稿模型，但不得降低安全、合规和审计要求。

## 7. 与 Context Governor 的接口

Provider 路由与 `Context Governor` 双向协作。

### 7.1 Provider 路由消费 Context Governor 输出

消费内容包括：

- `context_governor_snapshot_id`。
- `context_token_estimate`。
- 静态前缀、动态后缀、工具定义、记忆摘要和输出契约的分桶估算。
- 压缩结果、裁剪原因、未注入引用。
- `pre_compaction_flush_ref` 状态。

### 7.2 Provider 路由回传治理需求

当预算门发现上下文过大、成本不足或模型窗口不匹配时，必须回传明确动作：

- `REQUEST_MICRO_COMPRESSION`
- `REQUEST_FACT_COMPRESSION`
- `REQUEST_FULL_COMPRESSION`
- `REQUEST_MEMORY_RECALL_REDUCE`
- `REQUEST_TOOL_OUTPUT_OFFLOAD`
- `REQUEST_DELEGATION_SPLIT`

`Context Governor` 返回新的治理快照后，预算门才能重新估算；不得在路由层自行裁剪 Prompt 正文。

## 8. 配额、速率与熔断

### 8.1 配额桶

配额至少按租户、任务、运行、人格、Provider、模型、能力类型切桶。配额命中后必须返回拒绝、等待、备路由或降级结论。

### 8.2 速率限制

速率限制独立于预算存在，覆盖每租户、每任务、每人格、每 Provider / model 的请求数、并发数和 token 吞吐。

### 8.3 熔断粒度

熔断至少支持：

- Provider 级熔断。
- Model 级熔断。
- Capability 级熔断。

黑名单用于表达合规禁用、质量事故、账单异常或手工封禁。解除黑名单必须保留审计原因。

## 9. 计费归集与审计

预算门必须记录：

- `QueryEngine Round Budget` 输入。
- 候选 Provider / Model 集合。
- 配额、速率、熔断检查结果。
- 选中、降级、等待、拒绝或回退原因。
- 实际请求次数、token、按次费用和估算成本。
- 成本归属：平台、租户、任务、运行、人格、Provider、模型、能力类型。

一次调用只能有一个正式归属主体集合，但可以保留多维分析标签。

## 10. 与其他机制的关系

### 10.1 与 Tool

模型调用作为 `tool_family=MODEL` 的工具调用进入 `ToolInvocationEnvelope`。预算门通过后，模型工具仍需按工具契约审计和结果标准化。

### 10.2 与 Prompt Assembly

`PromptSnapshot` 提供路由估算输入。Provider 路由不得要求业务 API 直接传入模型私参，也不得反向修改 Prompt 快照正文。

### 10.3 与 Human Confirmation

预算不足、速率限制或熔断不直接进入人工确认；只有降级会影响正式业务结果、高风险结果放行或需要例外授权时，才可能进入确认链路。

## 11. 本文结论

Provider 机制以 `QueryEngine Round Budget` 为每轮预算门输入，保留 `ProviderProfile / ModelProfile / CapabilityProfile` 三层画像，通过 `BUDGET_ESTIMATE -> ROUTE_CANDIDATE -> QUOTA_CHECK -> RATE_CHECK -> CIRCUIT_CHECK -> SELECT_OR_DEGRADE -> ACCOUNT_USAGE` 状态机完成选择或降级，并与 `Context Governor` 通过治理快照和压缩请求闭环协作。

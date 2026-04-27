# Agent OS 子模块专项设计：Prompt Assembly

## 1. 文档说明

本文档下沉 `Agent OS` 的 `Prompt Assembly` 机制，说明 `QueryEngine` 每轮循环中如何把静态底座、动态片段、工具定义、记忆召回和 `Context Governor` 输出装配成可审计的 `PromptSnapshot`。

本文是 [`Agent OS Detailed Design`](../detailed-design.md) 的下游专项设计，直接承接 `QueryEngine Runtime Loop` 中的 `Context Governor -> Prompt Assembly -> Provider Budget Check` 段。

本文不展开以下内容：

- 不写具体 Prompt 正文、模板文案或模型私有提示技巧。
- 不定义对外 API 字段、接口路径或错误码。
- 不实现压缩算法；压缩由 `Context Governor` 负责，本文只定义装配侧如何消费压缩结果。
- 不定义具体模板引擎、配置文件格式、源码目录或迁移脚本。

## 2. 运行时定位

`Prompt Assembly` 是 `Context Plane` 的装配器，不是上下文治理器。它只接收已经被 `Context Governor` 预算、裁剪、压缩和记忆冲洗后的输入，并输出本轮模型调用可用的 `PromptSnapshot`。

运行时职责如下：

- 固定静态底座四层的装配顺序。
- 保持静态前缀与动态后缀的物理缓存边界稳定。
- 按 `Tool Definition Ordering` 注入工具定义或工具发现线索。
- 消费 `Context Governor` 的治理快照、压缩摘要、记忆冲洗引用和裁剪结果。
- 形成可回放的快照摘要、正文引用和 digest 字段。

`Prompt Assembly` 不负责判断哪些既有上下文应被压缩、哪些记忆应被召回、哪些工具结果应卸载；这些判断必须在上游完成。

## 3. 静态底座四层

静态底座保留四层稳定结构，位于 `PromptSnapshot` 的静态前缀区域。

### 3.1 平台根约束层

平台根约束层定义全部 Agent 共享的不可突破边界：身份定位、安全底线、越权禁止、审计要求、人工确认底线和结果责任边界。

### 3.2 运行时框架层

运行时框架层定义 `Agent OS` 的通用执行语义：`QueryEngine` 主循环、工具调用、观察归一、状态检查点、终止检查、失败恢复和证据优先原则。

### 3.3 通用人格层

通用人格层定义全部 Agent 默认行为方式，包括质量要求、协作习惯、工具使用偏好、记忆使用边界和确认敏感度。

### 3.4 专用人格补丁层

专用人格补丁层只在允许位点追加领域目标、输出重心、工具范围、记忆域和风险偏好。它不得改写平台根约束、运行时框架层或通用人格层中的硬规则。

## 4. 静态前缀与动态后缀缓存边界

`Prompt Assembly` 必须把最终 Prompt 物理拆成静态前缀和动态后缀，避免单轮任务变化破坏缓存命中。

### 4.1 静态前缀

静态前缀只包含低频变化内容：

- 平台根约束层。
- 运行时框架层。
- 通用人格层。
- 当前专用人格补丁层。
- 内置工具完整定义，按固定排序注入。

静态前缀不得包含任务目标、当前观察、记忆召回摘要、人工确认意见、委派回收摘要或工具运行结果。

### 4.2 动态后缀

动态后缀包含每轮变化内容：

- `Task Brief`：任务目标、完成定义、业务对象引用和本轮成功条件。
- `Environment Brief`：环境事件、用户追问、工具失败和最新观察。
- `Memory Brief`：`Active Recall` 产出的有限摘要。
- `Tool Hint Brief`：扩展工具的 `search_hint`、风险级别和发现入口。
- `Governance Brief`：确认要求、预算约束、降级策略、禁止项和本轮风险摘要。
- `Context Governor Brief`：压缩结果、裁剪原因、未注入内容引用和记忆冲洗状态。

动态后缀可以每轮变化，但必须保留片段类型、来源对象、摘要 digest 和裁剪原因。

在合同管理平台场景中，`Task Brief` 承载合同、审批、文档、签署或履约对象引用与本轮成功条件；`Governance Brief` 承载高金额合同、审批例外、签署异常、履约逾期和风控命中等治理上下文；`Memory Brief` 只注入用户风险分类偏好、法务审查口径、签署异常处理经验和履约风险模式等受控摘要。

### 4.3 缓存策略

`cache_prefix_policy_version` 定义静态前缀边界、工具定义排序策略和允许进入缓存前缀的内容类别。任何会改变静态前缀字节序的策略变化都必须提升该版本。

## 5. Tool Definition Ordering

`Tool Definition Ordering` 用于控制工具定义进入 Prompt 的顺序和颗粒度，避免扩展工具变化污染静态前缀。

### 5.1 内置工具

内置工具完整定义进入静态前缀，排序必须由 `tool_definition_order_version` 固化。排序键建议为：

1. `cache_order_group`
2. `tool_family`
3. `risk_level`
4. `tool_name`

内置工具定义必须包含完整名称、用途、输入边界、输出边界、风险等级、沙箱策略引用和结果卸载策略引用。

### 5.2 扩展工具

扩展工具不把完整 schema 注入 Prompt。动态后缀只注入：

- `tool_name`
- `tool_family`
- `risk_level`
- `search_hint`
- `schema_ref`

模型或规则判断需要使用扩展工具时，必须先进入 `ToolSearch`：`DISCOVER_HINT -> REQUEST_SCHEMA -> SNAPSHOT_DEFINITION -> GRANT_CHECK -> READY_TO_INVOKE`。完整 schema 只由 `ToolSearch` 按需载入，并形成工具定义快照。

### 5.3 排序快照

每次装配必须记录 `tool_definition_order_version`。若同一轮使用了按需载入的扩展工具 schema，还必须把该工具定义快照引用写入 `PromptSnapshot` 或相邻审计事件，保证工具契约可回放。

## 6. Context Governor 输入输出消费

`Context Governor` 是压缩、预算、召回和冲洗的责任主体。`Prompt Assembly` 只消费以下输出：

| 输出 | 装配用途 | 快照要求 |
| --- | --- | --- |
| `context_governor_snapshot_id` | 绑定本轮上下文治理决策 | 必填，串联压缩、裁剪、召回和预算判断。 |
| `governed_context_summary` | 进入 `Context Governor Brief` | 只注入摘要，不注入治理过程长文本。 |
| `memory_brief_list` | 进入 `Memory Brief` | 保留记忆可信度、冲突标签和来源引用。 |
| `trimmed_item_ref_list` | 说明未注入内容 | 只写引用与原因摘要。 |
| `pre_compaction_flush_ref` | 表达完整压缩前记忆冲洗结果 | 冲洗失败时不得伪装成完整成功快照。 |
| `tool_pair_integrity_status` | 校验工具调用 / 结果配对 | 断对时阻断 Prompt 装配。 |
| `context_token_estimate` | 提供 Provider 预算门输入 | 写入估算值和分桶摘要。 |

`Prompt Assembly` 不允许绕过 `Context Governor` 自行重取完整运行上下文、完整记忆池或完整工具结果。

## 7. 装配顺序

最终 Prompt 采用固定顺序：

1. 平台根约束层。
2. 运行时框架层。
3. 通用人格层。
4. 专用人格补丁层。
5. 内置工具完整定义。
6. `Task Brief`。
7. `Environment Brief`。
8. `Memory Brief`。
9. `Tool Hint Brief`。
10. `Governance Brief`。
11. `Context Governor Brief`。
12. 本轮输出契约与终止检查提示。

前 5 项构成静态前缀；第 6 项之后构成动态后缀。动态后缀不能反向覆盖静态前缀中的安全、权限、输出和确认规则。

## 8. 裁剪与拒绝装配规则

以下情况必须拒绝生成可调用模型的 Prompt：

| 情况 | 处理 |
| --- | --- |
| 缺失静态底座任一必需层 | 阻断装配，生成配置错误审计。 |
| `Context Governor` 未输出治理快照 | 阻断装配，返回 `CONTEXT_GOVERNOR_SNAPSHOT_MISSING`。 |
| 工具调用 / 结果断对 | 阻断装配，返回 `TOOL_PAIR_BROKEN`。 |
| 完整压缩需要记忆冲洗但 `pre_compaction_flush_ref` 失败 | 阻断完整压缩结果消费，按策略降级或人工接管。 |
| 动态后缀超预算且上游未给出裁剪结果 | 阻断装配，回到 `Context Governor`。 |

## 9. PromptSnapshot 快照语义

`PromptSnapshot` 至少记录以下字段语义：

| 字段 | 语义 |
| --- | --- |
| `static_prefix_digest` | 静态前缀字节序摘要，覆盖四层底座与内置工具完整定义。 |
| `dynamic_suffix_digest` | 动态后缀摘要，覆盖任务、环境、记忆、工具线索、治理和上下文摘要。 |
| `tool_definition_order_version` | 内置工具排序与扩展工具线索排序策略版本。 |
| `cache_prefix_policy_version` | 静态前缀物理缓存边界策略版本。 |
| `context_governor_snapshot_id` | 本轮上下文治理快照引用。 |
| `pre_compaction_flush_ref` | 完整压缩前记忆冲洗结果或补偿作业引用。 |
| `assembly_policy_version` | 装配顺序、冲突覆盖和拒绝装配规则版本。 |
| `trimming_policy_version` | 上游裁剪策略版本。 |
| `budget_policy_version` | 上下文预算分桶策略版本。 |
| `prompt_body_ref` | 最终正文受控引用，不默认对外暴露。 |

快照用于审计、运行回放、失败定位和 Provider 预算核算，不作为业务 API 直接返回内容。

## 10. 与其他运行时机制的关系

### 10.1 与 Memory

`Prompt Assembly` 只消费 `Active Recall` 的 `Memory Brief`，不得访问完整记忆池。记忆被采用、裁剪或降级应通过 `context_governor_snapshot_id` 和检索快照回溯。

### 10.2 与 Tool

`Prompt Assembly` 负责工具定义展示边界；工具注册、schema 发现、授权检查和沙箱执行由 `Tool Registry / ToolSearch / Sandbox Executor / Output Offloader` 承接。

### 10.3 与 Provider

`Provider Budget Check` 使用 `PromptSnapshot` 中的 token 估算、静态 / 动态 digest、上下文窗口需求和结构化输出要求做路由判断。

### 10.4 与 Human Confirmation

确认单只以 `Human Gate Brief` 摘要进入动态后缀。确认决策的暂停和恢复由 `human-gate` 管理，Prompt 不得把未决确认伪装成已放行事实。

## 11. 本文结论

`Prompt Assembly` 的核心是把静态底座、工具定义、动态片段和 `Context Governor` 输出装配成稳定、可缓存、可审计的每轮快照。静态前缀保证平台约束和内置工具定义稳定复用；动态后缀承接当前任务与治理状态；扩展工具通过 `ToolSearch` 按需载入完整 schema；压缩和记忆冲洗由 `Context Governor` 负责，装配侧只消费其快照结果。

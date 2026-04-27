# Agent OS 子模块专项设计：Memory Intake / Active Recall / Expiration

## 1. 文档说明

本文档下沉 `Agent OS` 的记忆运行时机制，围绕 `Memory Intake`、`Active Recall`、`Expiration / Forgetting` 三段式展开，说明记忆如何进入、如何被召回、如何退出在线影响面。

本文是 [`Agent OS Detailed Design`](../detailed-design.md) 的下游专项设计，直接承接 `QueryEngine Runtime Loop` 中的 `Observation Normalize -> Memory Intake -> State Checkpoint`，并为 `Context Governor` 和 `Prompt Assembly` 提供受控记忆摘要。

本文不展开以下内容：

- 不写向量库、嵌入模型、索引构建作业或消息队列实现。
- 不定义对外 API 字段、接口路径或错误码。
- 不把记忆正文、Prompt 片段或模型私有参数写成固定实现。
- 不定义 `Auto Dream` 资产发布管线，候选资产治理由对应专项承接。

## 2. 运行时定位

记忆不是上下文缓存，也不是事实仓库。它是带来源、可信度、时效、冲突和生命周期状态的辅助材料。

运行时三段式职责如下：

- `Memory Intake`：从观察、结果、失败、人工确认、委派回收和压缩前冲洗中接收候选记忆。
- `Active Recall`：按预算从元数据扫描开始，生成有限、可信、带标签的 `Memory Brief`。
- `Expiration / Forgetting`：控制记忆退出主注入层、退出在线召回面或冻结待复核。

## 3. 不应存硬规则

记忆准入先执行“不应存”硬规则。命中后直接拒绝或转审计引用，不进入候选记忆。

| 类别 | 不应存内容 | 处理 |
| --- | --- | --- |
| 临时执行流水 | 单次处理步骤、工具尝试顺序、平台资源结构快照、工具执行流水 | 写审计，不写记忆。 |
| 业务对象临时结构 | 合同附件清单、审批节点快照、文档目录快照、签署页临时索引 | 需要时重新检索，不长期存。 |
| 业务过程临时差异 | 业务对象临时版本差异、流程配置临时状态、导入批次流水、工具执行流水 | 保留在业务对象版本、流程审计或运行审计中，不写记忆。 |
| 配置正文 | 密钥、环境变量值、配置文件全文、凭据引用可展开内容 | 禁止入记忆，必要时只存受控引用。 |
| 敏感原文 | 合同全文、个人信息、业务秘密、供应商原始响应全文 | 默认只存摘要和证据引用。 |
| 低复用事实 | 仅服务当前一次任务的细枝末节 | 留在检查点或运行摘要。 |
| 相对日期 | “明天”“上周”“近期”等无绝对时间锚点内容 | 转绝对日期后再评估准入。 |
| 未验证推断 | 模型单次猜测、失败工具链导出结论 | 可入候选但不得晋升；高风险时拒绝。 |

应存内容只包括高价值事实、用户明确偏好、稳定约束、失败教训、工具偏好、项目决策原因和外部引用索引，并且必须带来源和可信度标签。合同管理平台中的典型可存业务记忆包括：用户偏好的风险分类、法务审查口径、审批例外规则、签署异常处理经验和履约风险模式；这些内容也只能以摘要、作用域和证据引用形式进入记忆。

## 4. Memory Intake

`Memory Intake` 负责把运行观察转成候选记忆或拒绝记录。它不把所有输出自动写入长期记忆。

### 4.1 Intake 来源

正式来源包括：

- `Observation Normalize` 产出的工具成功、失败、拒绝、超时和沙箱结果。
- `AgentResult` 的结果摘要和结果放行状态。
- `HumanConfirmation` 的裁决意见、作用范围和拒绝原因。
- `DelegationReturn` 的回收摘要、冲突证据和合并建议。
- `Context Governor` 的 `Pre-compression Memory Flush` 输出。
- `Auto Dream` 候选资产入口输出的候选摘要。

### 4.2 Intake 状态机

`Memory Intake` 采用固定状态机：

```text
OBSERVED -> CANDIDATE -> VALIDATED -> PROMOTED
                      -> REJECTED
                      -> FROZEN
```

| 状态 | 语义 | 允许动作 |
| --- | --- | --- |
| `OBSERVED` | 运行中出现了可能有记忆价值的观察 | 执行硬规则、来源归一和摘要化。 |
| `CANDIDATE` | 通过最低准入，尚未验证 | 去重、冲突扫描、可信度初评。 |
| `VALIDATED` | 已有足够证据作为辅助材料 | 进入可召回目录，但仍保留作用域与时效。 |
| `PROMOTED` | 晋升为正式记忆项 | 写入 `ao_memory_item`，可参与 `Active Recall`。 |
| `REJECTED` | 不应存或证据不足 | 写拒绝原因和审计引用。 |
| `FROZEN` | 存在冲突、污染或需人工复核 | 禁止主注入，可供复核链路处理。 |

### 4.3 Intake 字段语义

正式记忆项至少表达：

- `memory_scope`：`RUN`、`TASK`、`SESSION`、`AGENT`、`GLOBAL`。
- `memory_type`：`FACT`、`PATTERN`、`FAILURE_LESSON`、`TOOL_HINT`、`CONSTRAINT`。
- `source_ref`：来源任务、运行、工具调用、确认单、委派或冲洗引用。
- `confidence_score`：当前证据下的可采信程度。
- `verification_status`：`UNVERIFIED`、`PROBATION`、`VERIFIED`、`REJECTED`。
- `freshness_state`：`FRESH`、`STALE`、`NEEDS_REVIEW`。
- `conflict_state`：`NONE`、`SUSPECTED`、`CONFLICTING`、`SUPERSEDED`。
- `expiration_state`：`ACTIVE`、`FROZEN`、`PENDING_EXPIRATION`、`EXPIRED`、`REVOKED`、`FORGOTTEN`。

### 4.4 与 Context Governor 的压缩前冲洗交界

完整压缩前，`Context Governor` 必须调用 `Memory Intake` 执行 `Pre-compression Memory Flush`，把高价值事实、失败教训、任务进度、用户反馈、未完成边界和候选技能线索写入候选入口。

交界规则如下：

- `Context Governor` 提供待冲洗摘要、来源引用、压缩原因和预算压力。
- `Memory Intake` 返回 `pre_compaction_flush_ref`，标明成功、部分成功、拒绝或失败。
- 冲洗失败必须生成补偿作业，记录失败原因、待冲洗摘要 digest、可重试边界和降级建议。
- 高价值事实未冲洗成功时，`Context Governor` 不得继续执行会丢失原始上下文的完整压缩。

## 5. Active Recall

`Active Recall` 的目标不是找出所有相似内容，而是在预算内产出最少、最有价值、可解释的 `Memory Brief`。

### 5.1 四步召回流程

```text
metadata scan -> compact catalog -> cheap selector -> bounded injection
```

| 步骤 | 输入 | 输出 | 约束 |
| --- | --- | --- | --- |
| `metadata scan` | 任务、运行、人格、对象引用、风险标签 | 候选记忆元数据集合 | 不加载正文，先用作用域、类型、状态和时间过滤。 |
| `compact catalog` | 候选元数据与短摘要 | 紧凑候选目录 | 合并重复主题，标记冲突和过期提示。 |
| `cheap selector` | 紧凑目录、预算、当前目标 | 小集合候选 | 优先用规则或低成本模型选择，拒绝弱相关填充。 |
| `bounded injection` | 小集合候选 | `Memory Brief` | 只注入摘要、标签、来源引用和过期提示。 |

### 5.2 召回排序维度

排序至少综合：

- 语义相关度。
- 作用域贴近度。
- `verification_status` 与 `confidence_score`。
- `freshness_state` 与最近使用时间。
- `conflict_state`。
- 注入后是否减少重复工具调用、降低风险或提升结果稳定性。

### 5.3 注入上限

默认策略：

- 主注入层最多 5 条记忆摘要。
- 单条摘要建议不超过 4KB。
- 会话累计记忆注入建议不超过 60KB。
- 超过策略阈值或时效窗口的记忆必须附过期提示或只保留引用。

召回宁缺毋滥。不得为了填满上下文注入低可信、弱相关或冲突未解记忆。

### 5.4 召回快照

每次 `Active Recall` 必须形成召回快照，至少记录：

- 触发入口和当前 `QueryEngine` 状态。
- 元数据扫描范围。
- 紧凑目录摘要。
- 选择器策略版本。
- 注入、备用、排除三层结果。
- 被裁剪或拒绝注入原因。

## 6. 冲突与可信度治理

冲突记忆不能被抹平成确定事实。

### 6.1 冲突类型

- 同一对象在不同时间形成的事实冲突。
- 人工裁决与自动归纳冲突。
- 工具输出之间冲突。
- 长期经验与当前局部观察冲突。

### 6.2 收口规则

- 正式人工裁决高于自动归纳。
- 新近且已验证的对象事实高于泛化经验。
- 同级候选冲突时并存标记，不自动选边。
- 影响正式结果或高风险动作时转人工确认或验证链路。

### 6.3 注入规则

冲突未解时，只允许以“存在冲突，需要验证”的摘要进入 `Memory Brief`，不得包装成确定约束。

## 7. Expiration / Forgetting

`Expiration / Forgetting` 控制记忆的在线影响范围，不把记忆无限留在主注入面。

### 7.1 失效

失效表示记忆当前不应作为有效辅助依据。触发包括对象状态变更、来源证据失效、规则版本切换或冲突已被裁决。失效记忆默认退出主注入层。

### 7.2 过期

过期表示生命周期窗口结束，不必然表示内容错误。`RUN`、`TASK`、`SESSION` 级记忆应有更短过期策略，`AGENT`、`GLOBAL` 级记忆也必须支持复核窗口。

### 7.3 撤销

撤销表示记忆曾被正式使用，但后续被更高优先级证据或治理动作收回。撤销必须记录原因、主体、时间和影响范围。

### 7.4 遗忘

遗忘表示记忆正文退出高成本在线召回面。遗忘后仍保留最小审计锚点和摘要引用，保证可解释来源和退出原因。

### 7.5 冻结

冻结用于暂停有污染、冲突或复核需求的记忆。冻结记忆不得进入主注入层，只能作为复核对象或审计引用。

## 8. 审计与指标

必须留痕：

- 观察如何进入候选、如何晋升、如何拒绝或冻结。
- 每次 `Active Recall` 为什么命中、为什么排除、为什么注入。
- 记忆何时失效、过期、撤销、遗忘或冻结。
- `pre_compaction_flush_ref` 的执行结果、失败原因和补偿作业。

核心指标包括：召回命中率、有效率、误召回率、冲突率、冲洗失败率、补偿作业成功率、长期记忆晋升率和冻结解除率。

## 9. 与 Prompt Assembly 的边界

`Prompt Assembly` 只消费 `Active Recall` 产出的 `Memory Brief`。它不得访问完整记忆池，不得绕过召回快照直接拼接记忆正文。记忆是否被采用、裁剪或拒绝注入，由召回快照和 `PromptSnapshot` 共同形成证据链。

## 10. 本文结论

记忆运行时机制以 `Memory Intake` 控制准入，以 `Active Recall` 控制有限召回，以 `Expiration / Forgetting` 控制退出在线影响面。压缩前记忆冲洗通过 `pre_compaction_flush_ref` 与 `Context Governor` 交界，失败必须生成补偿作业，不能静默丢失关键事实。

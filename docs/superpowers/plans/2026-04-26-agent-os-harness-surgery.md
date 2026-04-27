# Agent-OS Harness 设计手术实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将面向企业业务系统的 `Agent OS` 正式设计从“能力清单式聚合”重构为“QueryEngine 驱动的薄 Harness 内核 + 外围治理能力”。

**Architecture:** 先立主脊柱，再挂专项能力。`QueryEngine` 是支撑合同管理、审批、文档、签署、履约、风控、运维等企业业务任务的运行时中心，Prompt、Context、Memory、Tool、Provider、安全、验证、委派与自演进都必须能回挂到主循环的输入、决策、行动、观察、检查点或终止条件。

**Tech Stack:** Markdown 技术文档；目标目录为 `docs/technicals/foundations/agent-os/`；验证使用 `git diff --check`、结构搜索和能力覆盖矩阵。

---

## 执行原则

- 主 Agent 只做派发、整合、规划真相维护和结果汇总。
- 目标设计文档的修改、质量审查与复审全部由专职 SubAgent 执行。
- 不做表面术语修补；每次修改必须改变文档结构、章节职责、状态机、策略表、数据对象或验收门禁之一。
- `Agent OS` 的目标客户是企业业务系统、业务用户、管理端和运维审计方，不是开发者自动化工具；所有示例、工具、验证、Persona、记忆和自演进资产必须围绕合同管理平台这类业务场景表达。
- 允许吸收 Claude Code / Harness Engineering 的工程美学，但不得继承其开发者工具产品定位；内部工程类表达只能作为验证类比，不能成为主业务场景。
- 不新增历史/兼容口径，不写“此前/本轮/之前版本”这类历史叙事。
- 未经用户明确要求，不提交 git commit。

## 文件结构与职责

### 主脊柱文档

- `docs/technicals/foundations/agent-os/architecture-design.md`：定义 `QueryEngine` 驱动的薄 Harness 架构分层。
- `docs/technicals/foundations/agent-os/detailed-design.md`：把 `QueryEngine Runtime Loop` 前移为运行时中心，承接对象、状态机、失败处理与审计链。
- `docs/technicals/foundations/agent-os/api-design.md`：拆分外部业务 API、内部控制面 API、运维审计 API，防止内部复杂度外泄。
- `docs/technicals/foundations/agent-os/implementation-plan.md`：改为最小 Harness 内核优先的实施顺序，并加入独立验证门禁。

### 运行时机制专项

- `docs/technicals/foundations/agent-os/special-designs/prompt-layering-and-versioning-design.md`：收敛为 Prompt Assembly 与缓存友好策略，压缩主体回挂 `Context Governor`。
- `docs/technicals/foundations/agent-os/special-designs/memory-retrieval-and-expiration-design.md`：重构为 `Memory Intake / Active Recall / Expiration` 三段式。
- `docs/technicals/foundations/agent-os/special-designs/tool-contract-and-sandbox-design.md`：重构为 `Tool Registry / ToolSearch / Sandbox Executor / Output Offloader` 四段式。
- `docs/technicals/foundations/agent-os/special-designs/provider-routing-and-quota-design.md`：接入 `QueryEngine Round Budget`，把路由改成每轮预算门。
- `docs/technicals/foundations/agent-os/special-designs/human-confirmation-and-console-design.md`：把人工确认收敛为安全硬链路的第 3 层和 `QueryEngine` 暂停/恢复态。

### 验证、自演进与协作专项

- `docs/technicals/foundations/agent-os/special-designs/verification-and-performance-design.md`：把 `Verification Agent` 制度化为正式运行时放行角色。
- `docs/technicals/foundations/agent-os/special-designs/auto-dream-daemon-candidate-quality-design.md`：升级为 `Memory / Skill / Pattern` 三资产发布管线。
- `docs/technicals/foundations/agent-os/special-designs/specialized-agent-persona-catalog-design.md`：增加 `Explore / Plan / Implement / Verify / Guide / Ops` 职责阶段轴。
- `docs/technicals/foundations/agent-os/special-designs/delegation-scheduler-design.md`：强化 `Leader-Teammate`、权限隔离、团队记忆同步与冲突回收。
- `docs/technicals/foundations/agent-os/special-designs/drill-and-operations-runbook-design.md`：补验证制度失效、压缩失败、工具安全拦截失败、自演进误发布演练。

---

### Task 1: 主脊柱设计手术

**Files:**
- Modify: `docs/technicals/foundations/agent-os/architecture-design.md`
- Modify: `docs/technicals/foundations/agent-os/detailed-design.md`
- Modify: `docs/technicals/foundations/agent-os/api-design.md`
- Modify: `docs/technicals/foundations/agent-os/implementation-plan.md`

- [ ] **Step 1: 重读主脊柱文档与基准**

读取：
- `PRINCIPLE.md`
- `AGENTS.md`
- `docs/technicals/best-practice/Agen-OS开发的最佳实践.md`
- 四份目标主脊柱文档

期望：明确当前文档是否仍以能力清单为主线，以及哪些章节必须改为 `QueryEngine` 主线。

- [ ] **Step 2: 重构 Architecture Design 主线**

在 `architecture-design.md` 中执行：
- 将 `## 3. 子模块定位与设计目标` 的第一主语义改为“围绕 `QueryEngine` 的薄 Harness 运行时”。
- 将 `## 5. 关键组件划分` 从并列能力清单改为分层架构：`Harness Kernel`、`Tool & Execution Plane`、`Context Plane`、`Governance Plane`、`Evolution Plane`。
- 将 `## 10. 运行时主链路` 重命名或重写为 `QueryEngine 主链路`，明确 `while loop + tools + observations + termination checks`。
- 保留平台边界、业务模块关系和企业审计优势，不删除已有企业治理能力。

- [ ] **Step 3: 重构 Detailed Design 运行时中心**

在 `detailed-design.md` 中执行：
- 将 `P-A-O runtime loop` 升级为 `QueryEngine Runtime Loop` 主章节，并使其成为运行时中心。
- 明确每轮循环：`Task Ingress -> Context Governor -> Prompt Assembly -> Provider Budget Check -> Model Call -> Tool Decision -> Guard Chain -> Sandbox Executor -> Observation Normalize -> Memory Intake -> State Checkpoint -> Termination Check`。
- 新增或明确 `Context Governor` 内部模块，覆盖微压缩、事实压缩、完整压缩、自动熔断、工具调用/结果成对保留、压缩前记忆冲洗。
- 新增运行时失败处理表，至少覆盖：`CONTEXT_BUDGET_EXCEEDED`、`TOOL_PAIR_BROKEN`、`MEMORY_FLUSH_FAILED`、`PROVIDER_BUDGET_EXCEEDED`、`SANDBOX_REJECTED`、`HUMAN_CONFIRMATION_TIMEOUT`。
- 明确 `LoopStep` 由哪些现有对象承接；如不新增表，必须说明 `PromptSnapshot + ToolInvocation + AuditEvent + Checkpoint` 如何组合成一轮 step。

- [ ] **Step 4: 重构 API Design 平面边界**

在 `api-design.md` 中执行：
- 新增或重写 `API 平面划分`：外部业务 API、内部控制面 API、运维审计 API。
- 明确业务 API 禁止直接传入或返回 Prompt 正文、Provider 私参、完整工具 schema、Memory 原文、子 Session 完整上下文。
- 将工具、Prompt、Memory、Provider 管理能力归入内部控制面。
- 将审计、指标、运行回放、失败诊断、成本配额、验证报告归入运维审计面。
- 保留现有 API 示例，除非示例已经违反新边界。

- [ ] **Step 5: 重构 Implementation Plan 排期**

在 `implementation-plan.md` 中执行：
- 将阶段顺序改为：最小 Harness Kernel -> 工具契约与沙箱 -> 上下文与记忆治理 -> 验证/安全/治理 -> 多 Agent 与自演进。
- 阶段一必须交付最小 `QueryEngine` 闭环，而不是只交付任务 CRUD 或 Prompt 拼接。
- 每阶段加入独立验证门禁，明确实现 Agent 不能自证最终通过。

- [ ] **Step 6: 运行主脊柱自检**

Run:

```bash
git diff --check -- docs/technicals/foundations/agent-os/architecture-design.md docs/technicals/foundations/agent-os/detailed-design.md docs/technicals/foundations/agent-os/api-design.md docs/technicals/foundations/agent-os/implementation-plan.md
```

Expected: 无输出。

搜索验证：
- `QueryEngine`
- `薄 Harness`
- `API 平面`
- `Context Governor`
- `Harness Kernel`
- `实现 Agent 不得自证`

Expected: 关键概念在对应主文档中命中，且不是孤立一句话。

---

### Task 2: 运行时机制专项手术

**Files:**
- Modify: `docs/technicals/foundations/agent-os/special-designs/prompt-layering-and-versioning-design.md`
- Modify: `docs/technicals/foundations/agent-os/special-designs/memory-retrieval-and-expiration-design.md`
- Modify: `docs/technicals/foundations/agent-os/special-designs/tool-contract-and-sandbox-design.md`
- Modify: `docs/technicals/foundations/agent-os/special-designs/provider-routing-and-quota-design.md`
- Modify: `docs/technicals/foundations/agent-os/special-designs/human-confirmation-and-console-design.md`

- [ ] **Step 1: 重读 Task 1 修改结果**

读取 Task 1 修改后的四份主文档，确认 `QueryEngine`、`Context Governor`、API 平面和阶段顺序的最新口径。

- [ ] **Step 2: 重构 Prompt 专项为 Prompt Assembly**

在 `prompt-layering-and-versioning-design.md` 中执行：
- 保留静态底座四层。
- 强化静态前缀 / 动态后缀的物理缓存边界。
- 新增 `Tool Definition Ordering`：内置工具完整定义固定排序；扩展工具只注入 `search_hint`；完整 schema 由 `ToolSearch` 按需载入。
- 将压缩主体收敛为 `Context Governor` 的输入/输出，本文只描述 Prompt Assembly 如何消费压缩结果。
- 补充 `static_prefix_digest`、`dynamic_suffix_digest`、`tool_definition_order_version`、`cache_prefix_policy_version`、`context_governor_snapshot_id`、`pre_compaction_flush_ref` 等快照语义。

- [ ] **Step 3: 重构 Memory 专项为三段式**

在 `memory-retrieval-and-expiration-design.md` 中执行：
- 章节主线改为 `Memory Intake`、`Active Recall`、`Expiration / Forgetting`。
- `Memory Intake` 状态机：`OBSERVED -> CANDIDATE -> VALIDATED -> PROMOTED -> REJECTED -> FROZEN`。
- `Active Recall` 四步：`metadata scan -> compact catalog -> cheap selector -> bounded injection`。
- 提升“不应存”硬规则为独立准入表。
- 将压缩前记忆冲洗写成与 `Context Governor` 的交界机制，失败生成补偿作业。

- [ ] **Step 4: 重构 Tool 专项为四组件**

在 `tool-contract-and-sandbox-design.md` 中执行：
- 保留 `ToolDefinition / ToolGrant / ToolInvocationEnvelope / ToolResultEnvelope`。
- 重构为 `Tool Registry / ToolSearch / Sandbox Executor / Output Offloader`。
- 新增工具注册字段语义：`tool_name`、`tool_family`、`risk_level`、`search_hint`、`schema_ref`、`definition_stability`、`cache_order_group`、`sandbox_policy_ref`、`offload_policy_ref`。
- 新增 `ToolSearch` 状态机：`DISCOVER_HINT -> REQUEST_SCHEMA -> SNAPSHOT_DEFINITION -> GRANT_CHECK -> READY_TO_INVOKE`。
- 固化安全硬链路：`Deterministic Guard -> LLM Judge -> Human Confirmation -> Runtime Sandbox`。

- [ ] **Step 5: 重构 Provider 专项为每轮预算门**

在 `provider-routing-and-quota-design.md` 中执行：
- 保留 Provider / Model / Capability Profile。
- 新增 `QueryEngine Round Budget` 作为路由输入。
- 新增预算门状态机：`BUDGET_ESTIMATE -> ROUTE_CANDIDATE -> QUOTA_CHECK -> RATE_CHECK -> CIRCUIT_CHECK -> SELECT_OR_DEGRADE -> ACCOUNT_USAGE`。
- 将降级策略改成策略表，覆盖预算不足、长上下文过大、结构化可靠性不足、Provider 熔断、速率受限。
- 明确 Provider 路由与 `Context Governor` 的接口。

- [ ] **Step 6: 重构 Human Confirmation 专项为暂停/恢复态**

在 `human-confirmation-and-console-design.md` 中执行：
- 保留确认单对象、生命周期和决策动作。
- 明确人工确认是安全硬链路第 3 层，不是高风险默认路径。
- 新增确认恢复状态机：`APPROVE -> ACTING/OBSERVING`、`REQUEST_CHANGES -> PERCEIVE_PENDING`、`REJECT -> FAILED/CANCELLED`、`EXPIRED -> DEGRADED/HUMAN_TAKEOVER`。
- 补充 `guard_chain_snapshot_id`、`llm_judge_summary_ref`、`sandbox_policy_ref`、`runtime_resume_state`、`budget_impact_summary` 语义。

- [ ] **Step 7: 运行运行时专项自检**

Run:

```bash
git diff --check -- docs/technicals/foundations/agent-os/special-designs/prompt-layering-and-versioning-design.md docs/technicals/foundations/agent-os/special-designs/memory-retrieval-and-expiration-design.md docs/technicals/foundations/agent-os/special-designs/tool-contract-and-sandbox-design.md docs/technicals/foundations/agent-os/special-designs/provider-routing-and-quota-design.md docs/technicals/foundations/agent-os/special-designs/human-confirmation-and-console-design.md
```

Expected: 无输出。

搜索验证：
- `Context Governor`
- `Tool Definition Ordering`
- `Memory Intake`
- `Active Recall`
- `Tool Registry`
- `ToolSearch`
- `Output Offloader`
- `QueryEngine Round Budget`
- `Runtime Sandbox`

Expected: 每个机制都有章节级承接。

---

### Task 3: 验证、自演进与协作专项手术

**Files:**
- Modify: `docs/technicals/foundations/agent-os/special-designs/verification-and-performance-design.md`
- Modify: `docs/technicals/foundations/agent-os/special-designs/auto-dream-daemon-candidate-quality-design.md`
- Modify: `docs/technicals/foundations/agent-os/special-designs/specialized-agent-persona-catalog-design.md`
- Modify: `docs/technicals/foundations/agent-os/special-designs/delegation-scheduler-design.md`
- Modify: `docs/technicals/foundations/agent-os/special-designs/drill-and-operations-runbook-design.md`

- [ ] **Step 1: 重读 Task 1 修改结果**

读取 Task 1 修改后的主文档，确认验证、自演进、persona、委派和运维在主脊柱中的挂接点。

- [ ] **Step 2: 制度化 Verification Agent**

在 `verification-and-performance-design.md` 中执行：
- 新增“运行时验证角色模型”。
- 明确 `Verification Agent` 只读、对抗、独立上下文、独立工具集、独立审计、独立回收格式。
- 明确实现 Agent 不得自证，验证 Agent 不消费实现者主观结论，只消费证据包、业务对象变更差异、合同 / 文档版本差异、审批 / 签署 / 履约状态、运行输出和对象状态。
- 新增 `verification_report` 字段语义：验证目标、独立上下文来源、检查项、失败证据、未覆盖风险、结论、回归基线入口。

- [ ] **Step 3: 升级 Auto Dream 为三资产管线**

在 `auto-dream-daemon-candidate-quality-design.md` 中执行：
- 将定位升级为自演进资产管线设计。
- 明确候选只是资产前态，正式出口为 `Memory Asset / Skill Asset / Pattern Asset`。
- 新增状态：`CANDIDATE -> VERIFIED -> CANARY -> PUBLISHED -> ROLLED_BACK / RETIRED`。
- 新增 `Skill Asset` 结构：元数据、适用条件、步骤、坑点、验证方法、版本号、配套资源、回滚目标。
- 新增 `Pattern Asset` 结构：可复用策略、路由偏好、委派切法、验证策略。
- 新增复用率、命中后成功率、误命中率、回滚次数、基准测试结果、人工否决率指标。

- [ ] **Step 4: 增加职责阶段 persona**

在 `specialized-agent-persona-catalog-design.md` 中执行：
- 增加“双轴人格目录”：职责阶段轴 + 领域能力轴，职责阶段优先。
- 新增 `persona_phase`：`EXPLORE`、`PLAN`、`IMPLEMENT`、`VERIFY`、`GUIDE`、`OPS`。
- 明确 `EXPLORE / VERIFY` 默认只读，`IMPLEMENT` 可写但不得自证，`OPS` 可触发恢复但需审计/确认。
- 明确领域 persona 只能收窄能力，不能放宽阶段边界。

- [ ] **Step 5: 强化 Delegation Scheduler**

在 `delegation-scheduler-design.md` 中执行：
- 明确 `Leader-Teammate` 模式：父运行是 Leader，子运行是 Teammate。
- 增加 `delegation_role`、`tool_grant_snapshot`、`permission_scope_snapshot`、`memory_sync_policy` 语义。
- 强化子运行独立上下文、独立工具集、独立权限、独立审计。
- 定义团队记忆同步边界：只同步高价值事实、证据、冲突，不同步无关观察。
- 结果冲突可转 `Verification Agent`。

- [ ] **Step 6: 补齐 Drill/Ops 高风险演练**

在 `drill-and-operations-runbook-design.md` 中执行：
- 新增验证制度失效演练：实现者误自证、验证 Agent 未独立、验证证据丢失、失败用例未入回归基线。
- 新增工具安全拦截失败演练：危险命令绕过、只读 Agent 获得写工具、沙箱拒绝未生效、权限分类器误放行。
- 新增压缩失败 / 记忆冲洗失败演练：Pre-compression memory flush 失败、摘要丢失关键状态、工具调用/结果对被压坏。
- 新增自演进误发布演练：错误 Skill 发布、错误 Pattern 污染调度、Memory 误晋升、回滚失败、误命中率升高。

- [ ] **Step 7: 运行验证与自演进专项自检**

Run:

```bash
git diff --check -- docs/technicals/foundations/agent-os/special-designs/verification-and-performance-design.md docs/technicals/foundations/agent-os/special-designs/auto-dream-daemon-candidate-quality-design.md docs/technicals/foundations/agent-os/special-designs/specialized-agent-persona-catalog-design.md docs/technicals/foundations/agent-os/special-designs/delegation-scheduler-design.md docs/technicals/foundations/agent-os/special-designs/drill-and-operations-runbook-design.md
```

Expected: 无输出。

搜索验证：
- `Verification Agent`
- `实现 Agent 不得自证`
- `Memory Asset`
- `Skill Asset`
- `Pattern Asset`
- `persona_phase`
- `Leader-Teammate`
- `团队记忆同步`
- `自演进误发布`

Expected: 每个机制都有章节级承接。

---

### Task 4: 父子文档一致性整合

**Files:**
- Modify only if needed: all files touched by Tasks 1-3

- [ ] **Step 1: 构建术语对齐表**

检查并统一术语：
- `QueryEngine`
- `Harness Kernel`
- `Context Governor`
- `Prompt Assembly`
- `Tool Registry`
- `ToolSearch`
- `Output Offloader`
- `QueryEngine Round Budget`
- `Verification Agent`
- `Memory Asset / Skill Asset / Pattern Asset`
- `Leader-Teammate`

- [ ] **Step 2: 检查父文档挂接**

确认主文档能直接指向专项：
- Prompt / Context -> Prompt 专项、Memory 专项
- Tool / Sandbox -> Tool 专项
- Provider Budget -> Provider 专项
- Human Gate -> Human Confirmation 专项
- Verification Runtime -> Verification 专项
- Evolution Plane -> Auto Dream 专项
- Persona Phase -> Persona 专项
- Delegation -> Delegation 专项
- Drill/Ops -> Ops 专项

- [ ] **Step 3: 删除重复或冲突口径**

只删除本轮改造产生的冲突表述；不得清理无关历史内容。

- [ ] **Step 4: 运行全目标文件自检**

Run:

```bash
git diff --check -- docs/technicals/foundations/agent-os/architecture-design.md docs/technicals/foundations/agent-os/detailed-design.md docs/technicals/foundations/agent-os/api-design.md docs/technicals/foundations/agent-os/implementation-plan.md docs/technicals/foundations/agent-os/special-designs/prompt-layering-and-versioning-design.md docs/technicals/foundations/agent-os/special-designs/memory-retrieval-and-expiration-design.md docs/technicals/foundations/agent-os/special-designs/tool-contract-and-sandbox-design.md docs/technicals/foundations/agent-os/special-designs/provider-routing-and-quota-design.md docs/technicals/foundations/agent-os/special-designs/human-confirmation-and-console-design.md docs/technicals/foundations/agent-os/special-designs/verification-and-performance-design.md docs/technicals/foundations/agent-os/special-designs/auto-dream-daemon-candidate-quality-design.md docs/technicals/foundations/agent-os/special-designs/specialized-agent-persona-catalog-design.md docs/technicals/foundations/agent-os/special-designs/delegation-scheduler-design.md docs/technicals/foundations/agent-os/special-designs/drill-and-operations-runbook-design.md
```

Expected: 无输出。

---

### Task 5: 独立 QA 复审

**Files:**
- Read-only review: all files touched by Tasks 1-4

- [ ] **Step 1: 执行覆盖矩阵复审**

QA 必须逐项检查：
- 主架构是否以 `QueryEngine` 为中心，而不是能力清单堆叠。
- `Detailed Design` 是否有可实现的每轮状态机、预算门、失败处理和审计链。
- `API Design` 是否清晰分离三类 API 平面，且业务 API 不外泄内部复杂度。
- Prompt / Memory / Tool / Provider / Human Gate 是否都能接入 `QueryEngine` 每轮循环。
- `Verification Agent` 是否成为运行时放行制度。
- Auto Dream 是否形成 `Memory / Skill / Pattern` 三资产发布管线。
- Persona 是否按职责阶段隔离。
- Delegation 是否体现 `Leader-Teammate`、权限隔离和团队记忆同步。
- Drill/Ops 是否覆盖验证失败、压缩失败、安全拦截失败、自演进误发布。

- [ ] **Step 2: 运行结构与空白检查**

Run:

```bash
git diff --check -- docs/technicals/foundations/agent-os/architecture-design.md docs/technicals/foundations/agent-os/detailed-design.md docs/technicals/foundations/agent-os/api-design.md docs/technicals/foundations/agent-os/implementation-plan.md docs/technicals/foundations/agent-os/special-designs/prompt-layering-and-versioning-design.md docs/technicals/foundations/agent-os/special-designs/memory-retrieval-and-expiration-design.md docs/technicals/foundations/agent-os/special-designs/tool-contract-and-sandbox-design.md docs/technicals/foundations/agent-os/special-designs/provider-routing-and-quota-design.md docs/technicals/foundations/agent-os/special-designs/human-confirmation-and-console-design.md docs/technicals/foundations/agent-os/special-designs/verification-and-performance-design.md docs/technicals/foundations/agent-os/special-designs/auto-dream-daemon-candidate-quality-design.md docs/technicals/foundations/agent-os/special-designs/specialized-agent-persona-catalog-design.md docs/technicals/foundations/agent-os/special-designs/delegation-scheduler-design.md docs/technicals/foundations/agent-os/special-designs/drill-and-operations-runbook-design.md
```

Expected: 无输出。

结构搜索：
- 无列表项内标题：`^-\s+#{1,6}\s`
- 无断裂标题：`^#{1,6} .*\/$`
- 无未收口占位标记

- [ ] **Step 3: 输出 QA 结论**

状态只能是：
- `PASS`
- `PASS_WITH_NOTES`
- `FAIL`

如 `FAIL`，必须给出可直接派给修复 SubAgent 的最小修复清单。

---

## 最终验收标准

- `Agent OS` 的心脏被清晰定义为 `QueryEngine`，而不是能力清单。
- `Harness Kernel` 与外围治理平面边界清晰。
- 运行时机制具备状态机、策略表、失败处理和审计字段语义。
- 验证与自演进不再是附属章节，而是运行时门禁和资产发布制度。
- 所有目标文件 `git diff --check` 通过。
- 独立 QA 结论为 `PASS` 或 `PASS_WITH_NOTES`，且无阻塞问题。

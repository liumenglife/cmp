# Batch 1 Foundations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付第一批底座主线的可运行实现基线，覆盖统一身份、组织、权限、任务、审计、集成边界，并完成 `agent-os` 最小 `QueryEngine / Harness Kernel` 闭环。

**Architecture:** 第一批按 `identity-access`、`agent-os`、`integration-hub` 三条底座主线并行推进，但共享身份、组织、权限、任务、审计与集成边界。`identity-access` 持有平台主体、组织、角色、权限、数据权限与授权判定真相；`agent-os` 持有围绕 `QueryEngine` 的薄 Harness 运行真相；`integration-hub` 持有外部交换过程、绑定、回调、任务、补偿和对账真相。

**Tech Stack:** `React SPA`、`Tailwind CSS`、`shadcn/ui`、既定后端技术栈、`MySQL`、`Redis`、`Docker Compose / 企业内网`、统一审计、统一任务中心、平台内部服务 API、模型 / Provider 抽象层。

---

## 1. 输入文档

### 1.1 总体输入

- [`PRINCIPLE.md`](../../../PRINCIPLE.md)
- [`docs/planning/current.md`](../../planning/current.md)
- [`docs/planning/decisions.md`](../../planning/decisions.md)
- [`docs/technicals/implementation-batch-plan.md`](../../technicals/implementation-batch-plan.md)
- [`docs/superpowers/specs/102-cmp-implementation-execution-spec.md`](../specs/102-cmp-implementation-execution-spec.md)

### 1.2 `identity-access` 输入

- [`Architecture Design`](../../technicals/foundations/identity-access/architecture-design.md)
- [`API Design`](../../technicals/foundations/identity-access/api-design.md)
- [`Detailed Design`](../../technicals/foundations/identity-access/detailed-design.md)
- [`Implementation Plan`](../../technicals/foundations/identity-access/implementation-plan.md)
- [`org-rule-resolution-design.md`](../../technicals/foundations/identity-access/special-designs/org-rule-resolution-design.md)
- [`external-identity-protocol-design.md`](../../technicals/foundations/identity-access/special-designs/external-identity-protocol-design.md)
- [`data-scope-sql-pushdown-design.md`](../../technicals/foundations/identity-access/special-designs/data-scope-sql-pushdown-design.md)
- [`decrypt-download-authorization-design.md`](../../technicals/foundations/identity-access/special-designs/decrypt-download-authorization-design.md)
- [`identity-migration-and-bootstrap-design.md`](../../technicals/foundations/identity-access/special-designs/identity-migration-and-bootstrap-design.md)

### 1.3 `agent-os` 输入

- [`Architecture Design`](../../technicals/foundations/agent-os/architecture-design.md)
- [`API Design`](../../technicals/foundations/agent-os/api-design.md)
- [`Detailed Design`](../../technicals/foundations/agent-os/detailed-design.md)
- [`Implementation Plan`](../../technicals/foundations/agent-os/implementation-plan.md)
- [`prompt-layering-and-versioning-design.md`](../../technicals/foundations/agent-os/special-designs/prompt-layering-and-versioning-design.md)
- [`tool-contract-and-sandbox-design.md`](../../technicals/foundations/agent-os/special-designs/tool-contract-and-sandbox-design.md)
- [`provider-routing-and-quota-design.md`](../../technicals/foundations/agent-os/special-designs/provider-routing-and-quota-design.md)
- [`memory-retrieval-and-expiration-design.md`](../../technicals/foundations/agent-os/special-designs/memory-retrieval-and-expiration-design.md)
- [`human-confirmation-and-console-design.md`](../../technicals/foundations/agent-os/special-designs/human-confirmation-and-console-design.md)
- [`delegation-scheduler-design.md`](../../technicals/foundations/agent-os/special-designs/delegation-scheduler-design.md)
- [`verification-and-performance-design.md`](../../technicals/foundations/agent-os/special-designs/verification-and-performance-design.md)
- [`auto-dream-daemon-candidate-quality-design.md`](../../technicals/foundations/agent-os/special-designs/auto-dream-daemon-candidate-quality-design.md)
- [`specialized-agent-persona-catalog-design.md`](../../technicals/foundations/agent-os/special-designs/specialized-agent-persona-catalog-design.md)
- [`drill-and-operations-runbook-design.md`](../../technicals/foundations/agent-os/special-designs/drill-and-operations-runbook-design.md)

### 1.4 `integration-hub` 输入

- [`Architecture Design`](../../technicals/foundations/integration-hub/architecture-design.md)
- [`API Design`](../../technicals/foundations/integration-hub/api-design.md)
- [`Detailed Design`](../../technicals/foundations/integration-hub/detailed-design.md)
- [`Implementation Plan`](../../technicals/foundations/integration-hub/implementation-plan.md)
- [`external-field-mapping-design.md`](../../technicals/foundations/integration-hub/special-designs/external-field-mapping-design.md)
- [`signing-and-ticket-security-design.md`](../../technicals/foundations/integration-hub/special-designs/signing-and-ticket-security-design.md)
- [`adapter-runtime-design.md`](../../technicals/foundations/integration-hub/special-designs/adapter-runtime-design.md)
- [`retry-timeout-and-alerting-design.md`](../../technicals/foundations/integration-hub/special-designs/retry-timeout-and-alerting-design.md)
- [`reconciliation-and-raw-message-governance-design.md`](../../technicals/foundations/integration-hub/special-designs/reconciliation-and-raw-message-governance-design.md)

## 2. 完成功能范围

第一批完成以下底座能力：

- 统一身份：平台统一用户主体、外部身份绑定、协议交换、绑定预检查、冲突冻结、会话上下文与身份审计。
- 统一组织：多组织、部门、成员挂接、组织规则版本、组织规则解析记录与审批节点组织绑定基础。
- 统一权限：角色、菜单权限、功能权限、数据权限、授权判定、显式拒绝、解密下载授权命中与审计追踪。
- 统一任务：`AgentTask`、`AgentRun`、`IntegrationJob` 与平台统一任务中心的最小映射，不重复建设业务私有任务体系。
- 统一审计：身份、授权、Agent 运行、工具调用、外部交换、回调、补偿、人工确认和恢复动作均可按 `trace_id` 回查。
- 集成边界：企业微信、`OA`、`CRM`、`SF`、`SRM`、`SAP` 统一经 `integration-hub` 进入，不允许业务模块私接外部入口。
- `agent-os` 最小闭环：完成 `Task Ingress -> Context Governor -> Prompt Assembly -> Provider Budget Check -> Model Call -> Tool Decision -> Guard Chain -> Observation Normalize -> State Checkpoint -> Termination Check` 的最小可运行链路。

## 3. 文件职责

- `identity-access` 实现文件：负责用户、组织、角色、权限、数据权限、组织规则、授权判定、会话、身份协议治理与身份审计。
- `agent-os` 实现文件：负责 Agent 任务、运行、结果、Prompt 快照、检查点、模型工具调用、观察归一、审计、人工确认与委派基础对象。
- `integration-hub` 实现文件：负责入站、出站、回调、绑定、适配器运行时、集成任务、恢复工单、对账与原始报文治理。
- 测试文件：按主线分别覆盖成功路径、权限拒绝、幂等冲突、失败补偿、审计留痕和最小端到端闭环。
- 配置与迁移文件：只承接第一批必须的表结构、枚举、索引、任务配置、审计事件和本地环境配置。

## 4. 可执行任务清单

### Task 1: 第一批启动门禁核对

**Files:**
- Read: `docs/superpowers/specs/102-cmp-implementation-execution-spec.md`
- Read: `docs/technicals/implementation-batch-plan.md`
- Read: 三条主线正式 4 件套与专项设计
- Modify: 不修改代码，仅产出批次启动检查记录到主 Agent 指定报告位置

- [ ] **Step 1: 核对正式输入完整性**
  确认 `identity-access`、`agent-os`、`integration-hub` 均已具备 `Architecture Design`、`API Design`、`Detailed Design`、`Implementation Plan` 与专项设计输入。

- [ ] **Step 2: 核对范围边界**
  确认第一批只覆盖底座主线，不提前实现 `contract-core`、`document-center`、`workflow-engine` 的业务主链路。

- [ ] **Step 3: 核对环境条件**
  确认 `Docker Compose / 企业内网`、`MySQL`、`Redis`、统一审计、统一任务中心、日志和配置注入具备最小可运行入口。

- [ ] **Step 4: 核对验收样例**
  准备三类样例：统一身份登录与授权判定、`agent-os` 合同风险提示最小任务、`integration-hub` 外部入站与回调闭环。

**完成标志:** 启动检查结论为可开工，且未发现批次范围、真相源归属或环境门禁阻塞。

**验证方式:** 独立质量审查 SubAgent 复核检查记录，并确认未跳过正式输入。

### Task 2: `identity-access` 统一主体与身份协议治理

**Files:**
- Read: `docs/technicals/foundations/identity-access/api-design.md`
- Read: `docs/technicals/foundations/identity-access/detailed-design.md`
- Read: `docs/technicals/foundations/identity-access/special-designs/external-identity-protocol-design.md`
- Read: `docs/technicals/foundations/identity-access/special-designs/identity-migration-and-bootstrap-design.md`
- Modify: `identity-access` 相关实现、迁移、测试与配置文件

- [ ] **Step 1: 建立统一用户主体与身份绑定对象**
  实现 `User`、`IdentityBinding`、`IdentitySession`、`protocol_exchange`、`binding_precheck`、人工处置与身份审计的最小持久化和服务边界。

- [ ] **Step 2: 实现外部身份进入平台的协议治理链路**
  支持本地账号、`SSO`、`LDAP`、企业微信标准化协议交换引用进入平台主体映射，不在 `integration-hub` 签发平台访问令牌。

- [ ] **Step 3: 实现冲突冻结与人工处置**
  对重复身份、候选主体冲突、撤销、解绑、重绑和重试建立正式状态、审计事件与恢复入口。

- [ ] **Step 4: 实现会话上下文查询**
  提供当前主体、组织上下文、角色摘要与权限摘要的受控查询能力。

**完成标志:** 同一自然人来自多个认证源时归并到平台统一主体；冲突可冻结、可审计、可人工处理；会话上下文可被后续权限、任务、审计链路消费。

**验证方式:** 覆盖登录成功、外部换票、重复回调、身份冲突、人工解冻、会话查询、审计追踪和幂等冲突测试。

### Task 3: `identity-access` 组织、角色、权限与数据权限

**Files:**
- Read: `docs/technicals/foundations/identity-access/architecture-design.md`
- Read: `docs/technicals/foundations/identity-access/detailed-design.md`
- Read: `docs/technicals/foundations/identity-access/special-designs/org-rule-resolution-design.md`
- Read: `docs/technicals/foundations/identity-access/special-designs/data-scope-sql-pushdown-design.md`
- Modify: `identity-access` 相关组织、角色、权限、数据权限、授权判定实现与测试

- [ ] **Step 1: 建立组织与成员关系**
  实现多组织、部门、成员挂接、主部门、兼职部门、负责人和组织路径基础能力。

- [ ] **Step 2: 建立角色与权限授权**
  实现角色、角色授予、菜单权限、功能权限、特殊授权和显式拒绝的统一资源模型。

- [ ] **Step 3: 建立数据权限与下推能力**
  实现 `SELF`、`ORG`、`ORG_UNIT`、`ORG_SUBTREE`、`USER_LIST`、`RULE` 等范围语义，并支持下游仓储消费受控条件。

- [ ] **Step 4: 建立组织规则版本与解析记录**
  对审批节点、通知、数据权限、授权命中的组织规则引用冻结版本并记录解析证据。

**完成标志:** 组织、角色、菜单权限、功能权限、数据权限都以 `identity-access` 为正式真相；审批节点可引用部门、人员或组织规则；业务模块不维护第二套用户组织权限。

**验证方式:** 覆盖组织树、角色授予、菜单显隐、功能拒绝、数据范围下推、组织规则解析、规则版本冻结和审计回放测试。

### Task 4: `identity-access` 统一授权判定与解密下载授权

**Files:**
- Read: `docs/technicals/foundations/identity-access/special-designs/decrypt-download-authorization-design.md`
- Read: `docs/technicals/foundations/identity-access/special-designs/data-scope-sql-pushdown-design.md`
- Modify: `identity-access` 授权判定、解密下载授权、审计与测试文件

- [ ] **Step 1: 实现统一授权判定对象**
  建立 `AuthorizationDecision`、命中依据、拒绝原因、数据范围命中和短期缓存失效机制。

- [ ] **Step 2: 实现解密下载授权配置**
  支持按部门、人员配置受控解密下载授权，并受统一功能权限、数据权限和组织上下文约束。

- [ ] **Step 3: 实现授权命中凭据传递**
  对加密文档或文档中心执行侧输出 `decision_id` 或等价命中凭据，不让执行侧成为第二套授权真相。

- [ ] **Step 4: 实现高敏授权审计**
  对授权命中、拒绝、撤销、显式拒绝和恢复动作写入审计。

**完成标志:** 合同、流程、文档、解密下载、管理授权可消费同一授权判定；高敏动作可追踪到主体、组织、授权项、数据范围和组织规则证据。

**验证方式:** 覆盖允许、拒绝、显式拒绝、数据范围不命中、部门授权、人员授权、撤销后重判和审计追踪测试。

### Task 5: `agent-os` 最小 `QueryEngine / Harness Kernel`

**Files:**
- Read: `docs/technicals/foundations/agent-os/architecture-design.md`
- Read: `docs/technicals/foundations/agent-os/api-design.md`
- Read: `docs/technicals/foundations/agent-os/detailed-design.md`
- Read: `docs/technicals/foundations/agent-os/implementation-plan.md`
- Modify: `agent-os` 任务、运行、结果、Prompt 快照、检查点、审计、模型工具调用与测试文件

- [ ] **Step 1: 建立任务与运行根对象**
  实现 `AgentTask`、`AgentRun`、`AgentResult`、`EnvironmentEvent` 的创建、查询、取消、状态推进与幂等入口。

- [ ] **Step 2: 建立最小 Prompt 与上下文装配**
  支持静态底座版本、专用人格补丁引用、动态注入摘要、上下文预算估算和 Prompt 快照引用。

- [ ] **Step 3: 实现最小 `QueryEngine` 循环**
  按 `Task Ingress -> Context Governor -> Prompt Assembly -> Provider Budget Check -> Model Call -> Tool Decision -> Guard Chain -> Observation Normalize -> State Checkpoint -> Termination Check` 推进至少一轮可恢复循环。

- [ ] **Step 4: 建立检查点与审计**
  每轮记录任务、运行、Prompt 快照、模型调用摘要、观察、风险、成本、终止判断和 `trace_id`。

- [ ] **Step 5: 完成业务样例**
  以合同风险提示或合同摘要任务作为最小端到端样例，业务端可提交任务、查询运行、读取结果，运维端可回放摘要。

**完成标志:** `agent-os` 不只是任务 CRUD 或 Prompt 拼接，而是形成可运行、可恢复、可审计的最小 Harness 内核；模型作为工具能力接入，不暴露 Provider 私参给业务模块。

**验证方式:** 覆盖成功、模型失败、预算拒绝、外部取消、最大循环、结果满足契约、审计缺失阻断和状态一致性测试。

### Task 6: `agent-os` 工具契约、沙箱与治理挂点

**Files:**
- Read: `docs/technicals/foundations/agent-os/special-designs/tool-contract-and-sandbox-design.md`
- Read: `docs/technicals/foundations/agent-os/special-designs/provider-routing-and-quota-design.md`
- Read: `docs/technicals/foundations/agent-os/special-designs/verification-and-performance-design.md`
- Modify: `agent-os` 工具注册、工具调用、沙箱、Provider 抽象、验证报告与测试文件

- [ ] **Step 1: 建立工具注册与工具发现**
  支持工具元数据、风险等级、完整 schema 快照、工具授权和按需发现。

- [ ] **Step 2: 建立沙箱执行边界**
  对只读、受控写入、高风险副作用执行权限分级、超时、拒绝、输出卸载与审计。

- [ ] **Step 3: 建立 Provider 抽象与预算门**
  把文本生成、结构化提取、摘要等能力作为统一模型工具，支持配额、速率、熔断、降级和成本记录。

- [ ] **Step 4: 建立验证报告入口**
  输出独立验证角色可消费的证据包、检查项、失败证据、性能基线和回归入口。

**完成标志:** `QueryEngine` 可以通过工具契约调用模型和平台只读工具；沙箱拒绝不可被绕过；工具调用与工具结果在审计链中成对存在。

**验证方式:** 覆盖工具成功、工具失败、工具超时、沙箱拒绝、输出卸载、Provider 降级、工具结果断对和验证报告生成测试。

### Task 7: `integration-hub` 统一接入与适配基础

**Files:**
- Read: `docs/technicals/foundations/integration-hub/architecture-design.md`
- Read: `docs/technicals/foundations/integration-hub/api-design.md`
- Read: `docs/technicals/foundations/integration-hub/detailed-design.md`
- Read: `docs/technicals/foundations/integration-hub/special-designs/adapter-runtime-design.md`
- Read: `docs/technicals/foundations/integration-hub/special-designs/signing-and-ticket-security-design.md`
- Modify: `integration-hub` 入站、出站、回调、适配器、验签、端点配置、审计与测试文件

- [ ] **Step 1: 建立统一交换主对象**
  实现 `InboundMessage`、`OutboundDispatch`、`CallbackReceipt`、`IntegrationBinding`、`IntegrationJob`、`IntegrationAuditView` 的最小模型和服务入口。

- [ ] **Step 2: 建立适配器运行时**
  实现适配器注册、端点解析、凭证引用、签名校验、字段归一、错误转译和运行上下文。

- [ ] **Step 3: 建立企业微信协议交换边界**
  支持企业微信外部换票、验签、原始协议适配和标准化输出，并把主体准入交给 `identity-access`。

- [ ] **Step 4: 建立统一审计和幂等**
  对入站、出站、回调、重复请求、签名失败、凭证失败和受理结果写入集成审计。

**完成标志:** 外围系统接入统一进入 `integration-hub`；业务模块不私接外部入口；企业微信身份接入不会在集成主线签发平台访问令牌。

**验证方式:** 覆盖签名成功、签名失败、重复入站、重复回调、端点不可用、企业微信换票移交和审计查询测试。

### Task 8: `integration-hub` 入站、出站、回调、补偿与对账

**Files:**
- Read: `docs/technicals/foundations/integration-hub/special-designs/external-field-mapping-design.md`
- Read: `docs/technicals/foundations/integration-hub/special-designs/retry-timeout-and-alerting-design.md`
- Read: `docs/technicals/foundations/integration-hub/special-designs/reconciliation-and-raw-message-governance-design.md`
- Modify: `integration-hub` 字段映射、任务重试、恢复工单、对账、原始报文证据、告警与测试文件

- [ ] **Step 1: 实现入站承接**
  支持外部主数据、合同事实、文档附件、`OA` 审批摘要和企业微信同步数据进入受理、归一、绑定或待处理状态。

- [ ] **Step 2: 实现出站派发**
  支持平台受治理的合同、文档、流程或通知事实投影出站，并记录目标系统请求引用、重试状态和审计证据。

- [ ] **Step 3: 实现回调闭环**
  统一承接企业微信、`OA`、业务系统回调，完成去重、顺序检查、绑定关联、状态转译和下游真相源回写请求。

- [ ] **Step 4: 实现补偿与恢复工单**
  对绑定缺失、状态冲突、下游承接失败、外部超时和重复回调生成恢复工单或重试任务。

- [ ] **Step 5: 实现对账与原始报文证据治理**
  建立对账任务、差异、证据组、原始报文引用、访问控制、保留和检索入口。

**完成标志:** 入站、出站、回调、补偿、对账与原始报文证据形成可追踪链路；`OA`、企业微信和业务系统可以各完成至少一条最小闭环样例。

**验证方式:** 覆盖字段映射、码表转换、外部超时、回调倒序、绑定缺失、恢复工单、多轮重放、对账差异、证据访问控制和审计回查测试。

### Task 9: 第一批跨主线联调

**Files:**
- Read: 本计划全部输入文档
- Modify: 第一批联调测试、端到端测试、测试数据、运行脚本、联调报告

- [ ] **Step 1: 联调身份与集成边界**
  企业微信协议交换由 `integration-hub` 标准化，再交给 `identity-access` 建立主体准入、绑定预检查、会话上下文和审计。

- [ ] **Step 2: 联调授权与 Agent 任务边界**
  `agent-os` 提交任务时消费统一主体、组织上下文和授权判定，不私建用户、角色或权限。

- [ ] **Step 3: 联调集成摘要与 Agent 输入**
  `agent-os` 只消费 `integration-hub` 治理后的集成摘要、异常语义、绑定关系和审计引用，不直接消费第三方原始协议。

- [ ] **Step 4: 联调审计链路**
  通过 `trace_id` 串起身份进入、授权判定、Agent 运行、工具调用、外部交换、回调和补偿事件。

**完成标志:** 三条主线共享同一身份、组织、权限、任务、审计与集成边界；任意一条链路失败时可定位到责任主线和正式对象。

**验证方式:** 执行至少三条端到端样例：企业微信登录与授权判定、合同风险提示 Agent 任务、`OA` 桥接回调与审计回放。

### Task 10: 第一批质量审查与发布前门禁

**Files:**
- Read: 实现代码、测试、迁移、配置、审计样例、联调报告
- Modify: 质量审查报告、风险清单、回归基线、发布前检查清单

- [ ] **Step 1: 代码与文档一致性审查**
  对照正式技术文档检查命名、状态、表、接口、事件、审计字段和责任边界。

- [ ] **Step 2: 真相源审查**
  检查是否出现第二套用户、组织、权限、任务、审计、外部交换、合同、文档或流程真相。

- [ ] **Step 3: 验证证据审查**
  复核测试输出、端到端样例、失败用例、回归基线、审计样例和环境复现方式。

- [ ] **Step 4: 风险与发布门禁审查**
  对外部联调窗口、测试账号、模型能力配置、回调白名单、凭证引用、数据迁移和回退方式给出放行或阻断结论。

**完成标志:** 独立质量审查给出 `PASS` 或带明确风险范围的放行结论；所有阻断级问题关闭后才能进入下一批。

**验证方式:** 审查报告必须引用测试证据、失败证据、修复记录、未覆盖风险和回归入口。

## 5. 批次级验证矩阵

| 验证面 | 必须覆盖 |
| --- | --- |
| 身份 | 本地登录、外部换票、绑定冲突、人工处置、会话上下文、身份审计 |
| 组织 | 多组织、部门、成员、主部门、组织规则解析、规则版本冻结 |
| 权限 | 角色、菜单、功能、数据权限、显式拒绝、授权判定、解密下载授权 |
| Agent 内核 | 任务受理、运行推进、Prompt 快照、模型调用、观察归一、检查点、终止条件 |
| Agent 治理 | 工具契约、沙箱拒绝、Provider 降级、验证报告、审计回放 |
| 集成 | 入站、出站、回调、绑定、验签、幂等、任务重试、恢复工单、对账证据 |
| 跨主线 | 统一主体进入、授权判定消费、Agent 任务消费、集成摘要消费、统一审计链 |
| 异常 | 重复请求、状态冲突、超时、外部不可用、权限拒绝、人工确认、补偿失败 |

## 6. 质量审查要求

- 实现完成后必须由独立质量审查 SubAgent 复核，不接受实现 SubAgent 自证完成。
- 审查必须覆盖正式文档一致性、真相源归属、接口契约、数据模型、幂等、审计、异常路径、测试覆盖和发布风险。
- 对阻断级问题，主 Agent 应重新派发专项修复并复审；不把阻断问题留到下一批。
- 若项目缺少某类自动化验证入口，质量审查报告必须明确缺失项、替代验证方式和剩余风险。

## 7. 第一批完成判定

第一批完成必须同时满足：

- `identity-access` 已成为统一身份、组织、权限、数据权限和授权判定真相源。
- `agent-os` 已完成最小 `QueryEngine / Harness Kernel` 可运行闭环，且模型只是工具能力。
- `integration-hub` 已成为唯一外部接入、入站、出站、回调、绑定、补偿与对账治理入口。
- 三条主线之间无重复底座、无绕行入口、无审计断链。
- 端到端验证、异常验证、审计回放和独立质量审查均已通过。

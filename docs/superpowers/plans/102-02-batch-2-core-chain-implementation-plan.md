# 第二批合同核心主链路成型 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成合同创建、编辑、文档挂接、审批承接、状态回写的核心闭环，让 `contract-core`、`document-center`、`workflow-engine` 共同形成合同管理平台的一期主链路。

**Architecture:** 本批次以合同主档为业务真相源、文档中心为文件真相源、流程引擎为审批运行时真相源，三条主线围绕同一 `contract_id`、同一文档版本链、同一审批摘要与状态回写口径推进。批内允许并行，但接口冻结、状态枚举、文档版本引用、审批回写契约必须由主链路集成检查统一收口。

**Tech Stack:** React SPA、Tailwind CSS、shadcn/ui、后端既定技术栈、MySQL、Redis、Docker Compose / 企业内网、平台统一认证权限、审计中心、任务中心、通知中心、DB abstraction layer。

---

## 1. 执行定位

本计划是 [`docs/technicals/implementation-batch-plan.md`](../../technicals/implementation-batch-plan.md) 中“第二批：合同核心主链路成型”的 Superpowers 执行基准。

本批次覆盖：

- `contract-core`
- `document-center`
- `workflow-engine`

本批次必须完成的功能闭环：

- 合同主档创建、编辑、查询与基础台账。
- 文档中心正式写入、文档主档、版本链、当前主版本引用与合同绑定。
- 审批流定义、平台审批承接、`OA` 主路径桥接、审批摘要与合同状态回写。
- 合同正文、附件、主版本、审批输入稿、审批摘要、合同时间线之间的稳定挂接。
- 文档版本链、审批回写、合同状态基础流转共同形成“合同创建 / 编辑 / 文档挂接 / 审批 / 状态回写”核心闭环。

本计划不重复展开正式技术文档中的表结构、接口字段、状态机细节和专项设计正文；执行时以正式技术文档为详细基线，以本文作为任务顺序、完成标志、验证和质量审查基准。

## 2. 输入文档

### 2.1 全局输入

- [`PRINCIPLE.md`](../../../PRINCIPLE.md)
- [`docs/planning/current.md`](../../planning/current.md)
- [`docs/planning/decisions.md`](../../planning/decisions.md)
- [`docs/technicals/implementation-batch-plan.md`](../../technicals/implementation-batch-plan.md)

### 2.2 `contract-core` 输入

- [`Architecture Design`](../../technicals/modules/contract-core/architecture-design.md)
- [`API Design`](../../technicals/modules/contract-core/api-design.md)
- [`Detailed Design`](../../technicals/modules/contract-core/detailed-design.md)
- [`Implementation Plan`](../../technicals/modules/contract-core/implementation-plan.md)
- [`合同文档版本绑定专项设计`](../../technicals/modules/contract-core/special-designs/contract-document-version-binding-design.md)
- [`合同审批桥接专项设计`](../../technicals/modules/contract-core/special-designs/contract-approval-bridge-design.md)
- [`合同搜索索引专项设计`](../../technicals/modules/contract-core/special-designs/contract-search-index-design.md)
- [`合同详情聚合专项设计`](../../technicals/modules/contract-core/special-designs/contract-detail-aggregation-design.md)
- [`条款语义标注与推荐治理设计`](../../technicals/modules/contract-core/special-designs/clause-semantic-tagging-and-recommendation-design.md)
- [`多语言治理设计`](../../technicals/modules/contract-core/special-designs/multilingual-governance-design.md)
- [`迁移、压测与切换治理设计`](../../technicals/modules/contract-core/special-designs/migration-loadtest-and-cutover-design.md)

### 2.3 `document-center` 输入

- [`Architecture Design`](../../technicals/modules/document-center/architecture-design.md)
- [`API Design`](../../technicals/modules/document-center/api-design.md)
- [`Detailed Design`](../../technicals/modules/document-center/detailed-design.md)
- [`Implementation Plan`](../../technicals/modules/document-center/implementation-plan.md)
- [`对象存储与生命周期设计`](../../technicals/modules/document-center/special-designs/object-storage-and-lifecycle-design.md)
- [`预览渲染与文本层设计`](../../technicals/modules/document-center/special-designs/preview-rendering-and-text-layer-design.md)
- [`批注锚点与跨版本重定位设计`](../../technicals/modules/document-center/special-designs/annotation-anchor-and-relocation-design.md)
- [`修订差异产物与红线展示设计`](../../technicals/modules/document-center/special-designs/redline-diff-artifact-design.md)
- [`加密句柄与安全导出设计`](../../technicals/modules/document-center/special-designs/crypto-handle-and-secure-export-design.md)
- [`OCR / 搜索 / 签章 / 归档挂接绑定`](../../technicals/modules/document-center/special-designs/ocr-search-signature-archive-binding-design.md)
- [`表级 DDL、分库分表与容量规划`](../../technicals/modules/document-center/special-designs/ddl-sharding-and-capacity-design.md)

### 2.4 `workflow-engine` 输入

- [`Architecture Design`](../../technicals/modules/workflow-engine/architecture-design.md)
- [`API Design`](../../technicals/modules/workflow-engine/api-design.md)
- [`Detailed Design`](../../technicals/modules/workflow-engine/detailed-design.md)
- [`Implementation Plan`](../../technicals/modules/workflow-engine/implementation-plan.md)
- [`流程 DSL 与校验器设计`](../../technicals/modules/workflow-engine/special-designs/workflow-dsl-and-validator-design.md)
- [`画布协议设计`](../../technicals/modules/workflow-engine/special-designs/workflow-canvas-protocol-design.md)
- [`组织规则求值器与参与人快照生成`](../../technicals/modules/workflow-engine/special-designs/org-rule-evaluator-design.md)
- [`并行与会签聚合设计`](../../technicals/modules/workflow-engine/special-designs/parallel-and-countersign-aggregation-design.md)
- [`OA 桥接映射与补偿专项设计`](../../technicals/modules/workflow-engine/special-designs/oa-bridge-mapping-and-compensation-design.md)
- [`任务执行器与通知运行时设计`](../../technicals/modules/workflow-engine/special-designs/task-executor-and-notification-runtime-design.md)
- [`流程实例迁移与管理员人工干预专项设计`](../../technicals/modules/workflow-engine/special-designs/instance-migration-and-admin-intervention-design.md)

## 3. 执行边界

### 3.1 本批次必须做

- 建立合同主档、合同草稿、合同编辑、合同详情与台账基础能力。
- 建立文档主档、文档版本链、主版本切换、文档与合同绑定关系。
- 建立审批定义、版本发布、审批实例、任务推进、组织绑定、审批摘要能力。
- 建立默认 `OA` 主路径与平台流程引擎承接路径的统一审批入口。
- 打通合同正文 / 附件进入文档中心后的合同侧引用和摘要回写。
- 打通审批发起、审批完成、驳回、终止、异常补偿到合同状态和时间线的回写。

### 3.2 本批次不做

- 不实现第三批的电子签章、加密软件、履约、变更、终止、归档完整业务能力。
- 不把搜索、AI、多语言增强、归档借阅等能力作为本批次完成前置。
- 不让文档中心复制合同主状态，不让流程引擎拥有合同主档，不让合同主档复制文件版本链。

## 4. 可执行任务清单

### Task 1: 批次启动门禁与接口冻结

**Files:**

- Modify: 实现仓库中的 `contract-core`、`document-center`、`workflow-engine` 代码与测试文件。
- Reference: 本文第 `2` 节列出的正式输入文档。

- [ ] **Step 1: 建立批次共享契约清单**

  输出一份实现侧契约清单，至少包含 `contract_id`、`document_asset_id`、`document_version_id`、`process_id`、`approval_summary`、`contract_status`、`timeline_event` 的字段命名、所有者和回写方向。

- [ ] **Step 2: 冻结第二批最小状态集合**

  明确合同主状态至少覆盖 `DRAFT`、`UNDER_APPROVAL`、`APPROVED`、`REJECTED`、`APPROVAL_TERMINATED`，流程实例状态至少覆盖发起、进行中、完成、驳回、终止、异常补偿，文档状态至少覆盖首版写入、激活主版本、失效版本、回写失败。

- [ ] **Step 3: 写跨模块契约测试**

  编写契约测试覆盖合同创建后绑定文档、审批发起消费合同与文档引用、审批结果回写合同状态三个场景。

- [ ] **Step 4: 运行契约测试并确认失败原因明确**

  在实现前运行契约测试，预期失败原因应指向缺少实现或缺少接口，而不是测试环境不可启动。

### Task 2: `contract-core` 合同主档与编辑基础能力

**Files:**

- Modify: `contract-core` 相关领域模型、接口、持久化、权限、审计、测试文件。
- Reference: [`contract-core Implementation Plan`](../../technicals/modules/contract-core/implementation-plan.md) 阶段一与阶段三。

- [ ] **Step 1: 写合同主档创建测试**

  覆盖创建合同后生成唯一 `contract_id`、合同编号、初始状态、责任组织、责任人和审计记录。

- [ ] **Step 2: 实现合同主档创建与基础编辑**

  实现 `POST /api/contracts`、`GET /api/contracts/{contract_id}/master`、`PATCH /api/contracts/{contract_id}` 对应能力，编辑范围受合同状态和权限约束。

- [ ] **Step 3: 写合同台账与详情聚合测试**

  覆盖台账查询、详情聚合读取主档、当前文档摘要、审批摘要和时间线摘要。

- [ ] **Step 4: 实现合同台账与详情基础读模型**

  实现合同台账列表、合同详情聚合骨架和摘要刷新入口，不把读模型做成第二份合同主档。

- [ ] **Step 5: 运行模块测试**

  运行 `contract-core` 单元测试、接口测试和持久化测试，确认合同主档创建、编辑、查询、审计均通过。

### Task 3: `document-center` 文档主档与版本链基础能力

**Files:**

- Modify: `document-center` 文档主档、版本链、绑定视图、预览入口、审计、测试文件。
- Reference: [`document-center Implementation Plan`](../../technicals/modules/document-center/implementation-plan.md) 阶段一与阶段二。

- [ ] **Step 1: 写文档主档写入测试**

  覆盖合同正文和附件写入文档中心后形成 `document_asset_id`、`current_version_id`、业务绑定和审计记录。

- [ ] **Step 2: 实现文档主档创建与合同绑定**

  实现 `POST /api/document-center/assets`、`GET /api/document-center/assets/{document_asset_id}`、按 `owner_type=CONTRACT` / `owner_id=contract_id` 查询文档列表。

- [ ] **Step 3: 写版本链测试**

  覆盖追加版本、查询版本、切换主版本、同一文档同一时刻只有一个当前主版本。

- [ ] **Step 4: 实现文档版本链与主版本切换**

  实现 `POST /api/document-center/assets/{document_asset_id}/versions`、版本查询、版本激活和绑定视图刷新。

- [ ] **Step 5: 运行模块测试**

  运行 `document-center` 单元测试、接口测试和存储边界测试，确认文件真相源与版本链不被业务模块复制。

### Task 4: `workflow-engine` 审批定义与平台运行时基础能力

**Files:**

- Modify: `workflow-engine` 定义、版本、节点绑定、实例、任务、审批动作、测试文件。
- Reference: [`workflow-engine Implementation Plan`](../../technicals/modules/workflow-engine/implementation-plan.md) 阶段一至阶段三。

- [ ] **Step 1: 写流程定义发布测试**

  覆盖流程定义草稿、版本发布、节点组织绑定必填校验、未绑定组织节点发布失败。

- [ ] **Step 2: 实现流程定义、版本和发布校验**

  实现流程定义、版本快照、发布校验、启停能力，并记录发布审计。

- [ ] **Step 3: 写平台审批运行时测试**

  覆盖基于已发布版本发起流程、生成任务、审批通过、驳回、终止、串行 / 并行 / 会签基础推进。

- [ ] **Step 4: 实现平台审批运行时与任务推进**

  实现流程实例、审批任务、审批动作、组织参与人快照、任务中心挂接和审批摘要生成。

- [ ] **Step 5: 运行模块测试**

  运行 `workflow-engine` 单元测试、接口测试和组织规则解析测试，确认平台审批引擎不是空壳。

### Task 5: 合同文档挂接闭环

**Files:**

- Modify: `contract-core` 与 `document-center` 集成接口、回写处理、摘要刷新、测试文件。
- Reference: [`合同文档版本绑定专项设计`](../../technicals/modules/contract-core/special-designs/contract-document-version-binding-design.md) 与 [`文档中心 Detailed Design`](../../technicals/modules/document-center/detailed-design.md)。

- [ ] **Step 1: 写合同正文入库集成测试**

  覆盖合同创建后上传主正文，文档中心建立文件对象和首版版本，合同主档保存当前正文引用和文档摘要。

- [ ] **Step 2: 写附件挂接集成测试**

  覆盖同一合同挂接多个附件，合同详情可读取附件摘要，文档中心仍持有文件版本真相。

- [ ] **Step 3: 实现文档摘要回写合同侧**

  实现文档写入、版本切换、主版本变化后的合同摘要刷新和时间线事件。

- [ ] **Step 4: 验证版本链不被复制**

  检查合同侧只保存稳定引用、当前摘要和业务有效版本，不保存完整文件版本链。

### Task 6: 审批发起、审批承接与合同状态回写闭环

**Files:**

- Modify: `contract-core` 审批入口、`workflow-engine` 实例发起 / 摘要 / 回调、状态映射、补偿任务、测试文件。
- Reference: [`合同审批桥接专项设计`](../../technicals/modules/contract-core/special-designs/contract-approval-bridge-design.md) 与 [`OA 桥接映射与补偿专项设计`](../../technicals/modules/workflow-engine/special-designs/oa-bridge-mapping-and-compensation-design.md)。

- [ ] **Step 1: 写平台审批路径端到端测试**

  覆盖合同从 `DRAFT` 发起平台审批、进入 `UNDER_APPROVAL`、审批通过后回写 `APPROVED` 和时间线。

- [ ] **Step 2: 写 `OA` 主路径桥接测试**

  覆盖默认走 `OA` 的审批发起、`OA` 回调、重复回调、乱序回调和统一审批摘要查询。

- [ ] **Step 3: 实现统一审批入口**

  合同侧只发起统一审批请求，由流程引擎根据承接策略走 `OA` 主路径或平台承接路径。

- [ ] **Step 4: 实现审批结果回写和补偿**

  审批完成、驳回、终止、异常失败均写入审批摘要、合同状态、合同时间线和审计；状态回写失败进入补偿任务。

- [ ] **Step 5: 验证双路径同一摘要口径**

  合同详情和台账只能读取统一审批摘要，不根据 `OA` 或平台路径分别写两套业务逻辑。

### Task 7: 第二批最小端到端闭环验证

**Files:**

- Modify: 跨模块端到端测试、测试数据、验收脚本、运行说明。
- Reference: [`implementation-batch-plan.md`](../../technicals/implementation-batch-plan.md) 第 `3`、`6`、`7` 节。

- [ ] **Step 1: 准备最小业务样例**

  准备合同样例、正文文件样例、附件样例、组织人员样例、审批流程样例、`OA` 回调样例。

- [ ] **Step 2: 跑通核心闭环**

  验证“创建合同 -> 编辑合同 -> 上传正文 -> 挂接附件 -> 发起审批 -> 审批通过 -> 合同状态回写 -> 合同详情查看”。

- [ ] **Step 3: 跑通关键异常闭环**

  验证文档写入失败、文档版本失效、审批驳回、审批回调重复、审批状态回写失败、组织节点解析失败。

- [ ] **Step 4: 输出验证证据**

  保存测试命令、测试结果、关键日志、数据库关键记录或接口响应证据，作为质量审查输入。

## 5. 完成标志

本批次只有同时满足以下条件才算完成：

- 合同可以稳定创建、编辑、查询，并生成唯一 `contract_id`。
- 合同正文和附件必须进入文档中心，形成文档主档、版本链和当前主版本引用。
- 合同侧只保存文档引用和摘要，不复制文档中心的完整版本链。
- 平台流程引擎具备流程定义、发布、组织绑定、实例推进、任务处理和审批摘要能力。
- 默认 `OA` 主路径与平台审批承接路径都能通过统一审批入口和统一审批摘要被合同侧消费。
- 审批通过、驳回、终止、异常补偿都能回写合同状态、合同摘要、时间线和审计。
- 至少一条端到端主链路通过：“合同创建 / 编辑 / 文档挂接 / 审批 / 状态回写”。
- 第三批所需的稳定挂载点已具备：合同状态、审批状态、文档版本链、签章输入稿候选引用、生命周期入口准入判断。

## 6. 验证方式

- 运行 `contract-core` 模块单元测试、接口测试、持久化测试。
- 运行 `document-center` 模块单元测试、接口测试、版本链测试。
- 运行 `workflow-engine` 模块单元测试、接口测试、组织绑定与审批运行时测试。
- 运行跨模块契约测试，覆盖合同、文档、审批三方共享字段与回写方向。
- 运行端到端测试，覆盖合同创建、文档挂接、审批发起、审批回写、详情查询。
- 运行异常测试，覆盖文档写入失败、版本切换失败、审批回调重复、审批回写失败、组织解析失败。
- 运行类型检查、lint、构建或项目等价验证命令；如仓库尚未配置对应命令，执行者必须在交付报告中明确说明缺失项。

## 7. 质量审查要求

- 必须由独立质量审查子代理复核本批次产物，不由实现子代理自审即通过。
- 审查重点一：合同主档、文档中心、流程引擎三类真相源是否互不越界。
- 审查重点二：`OA` 主路径与平台流程引擎承接路径是否形成统一审批摘要和统一合同回写。
- 审查重点三：文档版本链是否只归文档中心治理，合同侧是否只持有受控引用和摘要。
- 审查重点四：审批回写是否具备幂等、补偿、审计和时间线证据。
- 审查重点五：第三批所需的挂载点是否真实可消费，而不是只在文档中描述。
- 审查输出必须包含发现问题、证据位置、影响范围、修复建议和复审结论。

## 8. 风险与控制

- 风险：三条主线并行推进时字段命名和状态枚举漂移。控制：Task 1 先冻结共享契约，变更必须评审。
- 风险：`OA` 桥接先行导致平台流程引擎空心化。控制：平台审批运行时必须在 Task 4 独立可运行。
- 风险：文档中心被绕开形成业务附件表。控制：所有正式正文和附件均必须通过文档中心写入。
- 风险：审批回写直接硬改合同状态导致乱序覆盖。控制：统一状态映射、幂等键、补偿任务和审计事件。

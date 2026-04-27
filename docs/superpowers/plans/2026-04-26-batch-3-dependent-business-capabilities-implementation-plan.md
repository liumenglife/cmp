# 第三批依赖主链路真相源的业务能力 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在第二批合同主链路稳定后，完成电子签章、加密软件、履约 / 变更 / 终止 / 归档等依赖型业务能力，让签章结果、受控解密、生命周期后续业务围绕同一合同主档、审批摘要和文档版本链运行。

**Architecture:** 本批次严格消费第二批形成的合同状态、审批状态和文档版本真相源，不反向重写合同主档、文档中心或审批引擎的基础边界。`e-signature`、`encrypted-document`、`contract-lifecycle` 可并行推进，但必须通过统一准入、受控回写、摘要投影、审计和补偿机制挂接到主链路。

**Tech Stack:** React SPA、Tailwind CSS、shadcn/ui、后端既定技术栈、MySQL、Redis、Docker Compose / 企业内网、平台统一认证权限、审计中心、任务中心、通知中心、文档中心版本链、流程引擎审批摘要、DB abstraction layer。

---

## 1. 执行定位

本计划是 [`docs/technicals/implementation-batch-plan.md`](../../technicals/implementation-batch-plan.md) 中“第三批：依赖主链路真相源的业务能力”的 Superpowers 执行基准。

本批次覆盖：

- `e-signature`
- `encrypted-document`
- `contract-lifecycle`

本批次必须完成的功能能力：

- 电子签章：签章申请、准入校验、签章会话、参与方编排、结果回写、验签摘要、纸质备案辅助能力。
- 加密软件：文件入库自动加密、平台内受控解密访问、管理端按部门 / 人员授权解密下载、明文导出作业、审计闭环。
- 合同生命周期：履约、变更、终止、归档能力，以及对应的摘要回写、时间线、里程碑、审计与周边消费入口。

本批次不是第二批主链路的返工批次。若发现合同状态、审批摘要、文档版本链或挂载点不可消费，应先登记阻塞并回到第二批责任主线修复，不在第三批内部临时复制主链真相。

## 2. 输入文档

### 2.1 全局输入

- [`PRINCIPLE.md`](../../../PRINCIPLE.md)
- [`docs/planning/current.md`](../../planning/current.md)
- [`docs/planning/decisions.md`](../../planning/decisions.md)
- [`docs/technicals/implementation-batch-plan.md`](../../technicals/implementation-batch-plan.md)
- [`第二批合同核心主链路成型计划`](./2026-04-26-batch-2-core-chain-implementation-plan.md)

### 2.2 `e-signature` 输入

- [`Architecture Design`](../../technicals/modules/e-signature/architecture-design.md)
- [`API Design`](../../technicals/modules/e-signature/api-design.md)
- [`Detailed Design`](../../technicals/modules/e-signature/detailed-design.md)
- [`Implementation Plan`](../../technicals/modules/e-signature/implementation-plan.md)
- [`印章资源与证书介质专项设计`](../../technicals/modules/e-signature/special-designs/seal-resource-and-certificate-design.md)
- [`签章坐标与渲染专项设计`](../../technicals/modules/e-signature/special-designs/signature-coordinate-and-rendering-design.md)
- [`签署参与方编排专项设计`](../../technicals/modules/e-signature/special-designs/signing-party-orchestration-design.md)
- [`签章引擎适配层专项设计`](../../technicals/modules/e-signature/special-designs/signature-engine-adapter-design.md)
- [`批量签章与结果优化专项设计`](../../technicals/modules/e-signature/special-designs/batch-resign-and-result-optimization-design.md)
- [`签章运行时参数专项设计`](../../technicals/modules/e-signature/special-designs/signature-runtime-parameters-design.md)

### 2.3 `encrypted-document` 输入

- [`Architecture Design`](../../technicals/modules/encrypted-document/architecture-design.md)
- [`API Design`](../../technicals/modules/encrypted-document/api-design.md)
- [`Detailed Design`](../../technicals/modules/encrypted-document/detailed-design.md)
- [`Implementation Plan`](../../technicals/modules/encrypted-document/implementation-plan.md)
- [`加密算法与密钥层级专项设计`](../../technicals/modules/encrypted-document/special-designs/crypto-algorithm-and-key-hierarchy-design.md)
- [`受控读取句柄专项设计`](../../technicals/modules/encrypted-document/special-designs/controlled-read-handle-design.md)
- [`明文导出包专项设计`](../../technicals/modules/encrypted-document/special-designs/plaintext-export-package-design.md)
- [`授权范围表达式专项设计`](../../technicals/modules/encrypted-document/special-designs/authorization-scope-expression-design.md)
- [`脱敏策略与二次存储专项设计`](../../technicals/modules/encrypted-document/special-designs/desensitization-and-secondary-storage-design.md)
- [`消费方适配与压力测试专项设计`](../../technicals/modules/encrypted-document/special-designs/consumer-adaptation-and-pressure-test-design.md)
- [`DDL 事件与重试参数专项设计`](../../technicals/modules/encrypted-document/special-designs/ddl-event-and-retry-parameter-design.md)

### 2.4 `contract-lifecycle` 输入

- [`Architecture Design`](../../technicals/modules/contract-lifecycle/architecture-design.md)
- [`API Design`](../../technicals/modules/contract-lifecycle/api-design.md)
- [`Detailed Design`](../../technicals/modules/contract-lifecycle/detailed-design.md)
- [`Implementation Plan`](../../technicals/modules/contract-lifecycle/implementation-plan.md)
- [`履约规则与风险评分专项设计`](../../technicals/modules/contract-lifecycle/special-designs/fulfillment-rules-and-risk-scoring-design.md)
- [`变更影响与回写专项设计`](../../technicals/modules/contract-lifecycle/special-designs/change-impact-and-write-back-design.md)
- [`终止善后与访问控制专项设计`](../../technicals/modules/contract-lifecycle/special-designs/termination-settlement-and-access-control-design.md)
- [`归档封包与借阅归还专项设计`](../../technicals/modules/contract-lifecycle/special-designs/archive-package-and-borrow-return-design.md)
- [`生命周期字典与通知专项设计`](../../technicals/modules/contract-lifecycle/special-designs/lifecycle-dictionary-and-notification-design.md)
- [`摘要重建与回填专项设计`](../../technicals/modules/contract-lifecycle/special-designs/summary-rebuild-and-backfill-design.md)

## 3. 启动门禁

第三批启动前必须确认第二批至少具备以下可消费事实：

- 合同主档已可创建、编辑、查询，并持有稳定 `contract_id` 与基础 `contract_status`。
- 文档中心已可提供 `document_asset_id`、`document_version_id`、当前主版本、版本链、受控读取入口和文档绑定视图。
- 流程引擎已可提供统一审批摘要、审批通过事实、审批驳回事实、审批状态回写和异常补偿。
- 合同详情已可读取当前正文、附件、审批摘要、文档摘要和时间线。
- 审计中心、任务中心、权限体系、组织主数据已可被本批次复用。

如任一门禁未满足，执行者应暂停对应下游任务，只登记阻塞和证据，不在第三批中复制临时合同、临时文件或临时审批真相。

## 4. 执行边界

### 4.1 本批次必须做

- 电子签章必须建立在审批通过后的正式合同主链上，结果稿必须回收到文档中心版本链，再回写合同摘要与时间线。
- 加密软件必须作为平台内正式子模块挂在文档中心读写路径上，支持自动加密、受控读取、授权解密下载和审计。
- 合同生命周期必须围绕同一 `contract_id` 完成履约、变更、终止、归档，不创建新的合同主档。
- 三条主线都必须输出稳定摘要、里程碑、时间线和审计证据，供合同详情、台账、搜索、AI、通知后续消费。

### 4.2 本批次不做

- 不重写第二批合同主档、文档版本链、审批运行时的基础真相。
- 不把电子签章、加密、生命周期过程表升级为合同主档、文档主档或审批主档。
- 不把搜索、AI 的完整能力作为本批次完成条件，只保留稳定消费面和受控入口。
- 不把自研电子签章或加密软件写成依赖外部测试账号才能成立的外部系统。

## 5. 可执行任务清单

### Task 1: 第三批挂载点验收与共享契约冻结

**Files:**

- Modify: 第三批实现仓库中的跨模块契约测试、集成测试、测试样例。
- Reference: 本文第 `2` 节输入文档与第二批交付产物。

- [ ] **Step 1: 写第三批启动门禁测试**

  覆盖审批通过合同可作为签章输入、文档版本可作为加密 / 签章 / 归档输入、已签合同可进入履约入口。

- [ ] **Step 2: 冻结第三批共享契约**

  明确 `signature_status`、`encryption_status`、`performance_status`、`change_status`、`termination_status`、`archive_status` 与合同主状态之间的映射和摘要回写方向。

- [ ] **Step 3: 验证不得复制主链真相**

  检查第三批各模块只引用 `contract_id`、文档版本引用和审批摘要，不新建合同主档、文件版本链或审批实例主档。

### Task 2: `e-signature` 签章申请与准入能力

**Files:**

- Modify: `e-signature` 申请、准入、文档绑定、权限、审计、测试文件。
- Reference: [`e-signature Implementation Plan`](../../technicals/modules/e-signature/implementation-plan.md) 阶段一。

- [ ] **Step 1: 写签章准入测试**

  覆盖审批通过合同可以发起签章，审批未通过、审批撤回、合同状态不合法、输入稿版本失效、权限不足均被拒绝。

- [ ] **Step 2: 实现签章申请受理**

  实现 `POST /api/contracts/{contract_id}/signatures/apply`，生成 `signature_request_id`、申请快照、输入稿绑定、幂等指纹和审计记录。

- [ ] **Step 3: 实现申请查询**

  实现 `GET /api/signature-requests/{signature_request_id}`，仅暴露申请摘要和当前状态，不暴露内部引擎细节。

- [ ] **Step 4: 运行准入验证**

  运行签章准入单元测试、接口测试和权限测试，确认签章动作只建立在审批通过后的正式合同主链上。

### Task 3: `e-signature` 签章会话、结果回写与纸质备案

**Files:**

- Modify: `e-signature` 会话、参与方、结果、文档回写、合同回写、纸质备案、补偿任务、测试文件。
- Reference: [`e-signature Detailed Design`](../../technicals/modules/e-signature/detailed-design.md) 与相关专项设计。

- [ ] **Step 1: 写签章会话测试**

  覆盖签章会话创建、参与方展开、顺序控制、会话超时、重复回调、乱序回调和人工介入。

- [ ] **Step 2: 实现签章会话与参与方编排**

  实现 `SignatureSession`、参与方快照、签署顺序、会话状态推进和通知 / 任务挂接。

- [ ] **Step 3: 写结果回写测试**

  覆盖签章完成后先回写文档中心结果稿和验签产物，再回写合同主档签章摘要、状态和时间线。

- [ ] **Step 4: 实现签章结果、文档回写与合同回写**

  实现 `SignatureResult`、文档中心结果稿回收、合同摘要回写、失败补偿和重建摘要。

- [ ] **Step 5: 实现纸质备案辅助能力**

  支持纸质备案登记、纸质扫描件文档中心引用、备案摘要和电子签章结果的互斥 / 并存判断。

- [ ] **Step 6: 运行签章端到端测试**

  验证“审批通过 -> 发起签章 -> 会话推进 -> 结果回写文档中心 -> 合同签章摘要回写”。

### Task 4: `encrypted-document` 自动加密与平台内受控访问

**Files:**

- Modify: `encrypted-document` 安全绑定、自动加密、受控读取、审计、文档中心挂接、测试文件。
- Reference: [`encrypted-document Implementation Plan`](../../technicals/modules/encrypted-document/implementation-plan.md) 阶段一与阶段二。

- [ ] **Step 1: 写自动加密测试**

  覆盖文档进入文档中心后自动创建安全绑定、受理加密、幂等处理、失败不破坏文档真相。

- [ ] **Step 2: 实现自动加密主链**

  实现文档写入路径加密受理、安全绑定、任务接入、加密状态摘要和审计事件。

- [ ] **Step 3: 写平台内受控访问测试**

  覆盖合同详情预览、签章输入、归档输入、搜索 / AI 内部消费的受控读取句柄或短期票据。

- [ ] **Step 4: 实现受控解密访问**

  实现 `DecryptAccess`、访问场景判定、短期访问票据、拒绝 / 过期 / 撤销和审计。

- [ ] **Step 5: 运行安全主链测试**

  验证默认路径是平台内受控使用，不形成平台外明文外放通道。

### Task 5: `encrypted-document` 授权解密下载与高敏审计闭环

**Files:**

- Modify: `encrypted-document` 管理端授权、授权命中、解密下载作业、导出包、过期回收、审计、测试文件。
- Reference: [`encrypted-document Detailed Design`](../../technicals/modules/encrypted-document/detailed-design.md) 与相关专项设计。

- [ ] **Step 1: 写授权规则测试**

  覆盖管理端按部门、人员授予解密下载权限，授权生效、撤销、过期、范围命中和解释结果。

- [ ] **Step 2: 实现解密下载授权模型**

  实现部门 / 人员授权、范围表达、优先级、生效期、撤销和授权快照。

- [ ] **Step 3: 写解密下载作业测试**

  覆盖命中授权后发起下载、生成导出结果、明文文件可脱离 `CMP` 使用、过期回收、失败补偿。

- [ ] **Step 4: 实现解密下载作业与导出控制**

  实现 `DecryptDownloadJob`、授权冻结、导出物登记、下载地址有效期、回收任务和失败审计。

- [ ] **Step 5: 运行高敏审计测试**

  验证授权发放、授权命中、导出成功、导出失败、导出过期、越权拒绝均可审计回查。

### Task 6: `contract-lifecycle` 履约基础能力

**Files:**

- Modify: `contract-lifecycle` 履约记录、履约节点、风险摘要、文档引用、合同回写、测试文件。
- Reference: [`contract-lifecycle Implementation Plan`](../../technicals/modules/contract-lifecycle/implementation-plan.md) 阶段一。

- [ ] **Step 1: 写履约准入测试**

  覆盖已生效合同可进入履约，未签章 / 未生效合同不可创建正式履约主记录。

- [ ] **Step 2: 实现履约聚合与履约节点**

  实现履约总览、履约节点新增 / 更新、履约凭证文档中心引用、履约风险摘要。

- [ ] **Step 3: 写履约回写测试**

  覆盖履约进展、逾期、完成、风险变化回写合同摘要、时间线和审计。

- [ ] **Step 4: 运行履约模块测试**

  验证履约围绕同一 `contract_id` 运行，不创建第二合同主档。

### Task 7: `contract-lifecycle` 变更、终止与归档能力

**Files:**

- Modify: `contract-lifecycle` 变更、终止、归档、流程引用、文档引用、摘要、时间线、测试文件。
- Reference: [`contract-lifecycle Detailed Design`](../../technicals/modules/contract-lifecycle/detailed-design.md) 与相关专项设计。

- [ ] **Step 1: 写变更主链测试**

  覆盖原合同上发起变更、流程审批、补充协议文档引用、变更结果回写合同当前有效摘要。

- [ ] **Step 2: 实现变更能力**

  实现变更申请、影响摘要、流程挂接、结果应用、历史记录和合同摘要回写。

- [ ] **Step 3: 写终止主链测试**

  覆盖终止申请、终止审批、终止材料引用、善后摘要、终止状态回写和后续限制。

- [ ] **Step 4: 实现终止能力**

  实现终止申请、审批引用、终止结果、善后摘要、访问控制和合同状态回写。

- [ ] **Step 5: 写归档主链测试**

  覆盖归档准入、归档输入集校验、归档记录、归档封包文档引用、归档摘要回写。

- [ ] **Step 6: 实现归档能力**

  实现归档记录、输入集、封包引用、借阅归还基础入口、归档状态和合同摘要回写。

- [ ] **Step 7: 运行生命周期端到端测试**

  验证“生效 -> 履约 -> 变更 / 终止 -> 归档 -> 查询 / 审计回查 / 通知”。

### Task 8: 第三批跨模块综合验证

**Files:**

- Modify: 跨模块端到端测试、验收脚本、测试数据、联调说明。
- Reference: [`implementation-batch-plan.md`](../../technicals/implementation-batch-plan.md) 第 `3`、`6`、`7` 节。

- [ ] **Step 1: 准备综合测试样例**

  准备审批通过合同、正式文档版本、签章参与方、授权部门 / 人员、履约节点、变更材料、终止材料、归档输入集。

- [ ] **Step 2: 验证签章与文档版本闭环**

  跑通审批通过合同的签章申请、结果稿回写文档中心、合同签章摘要回写。

- [ ] **Step 3: 验证加密与授权解密下载闭环**

  跑通文件入库自动加密、平台内受控读取、授权命中下载、明文导出、审计回查。

- [ ] **Step 4: 验证生命周期闭环**

  跑通生效合同进入履约、发起变更、发起终止、完成归档、摘要与时间线查询。

- [ ] **Step 5: 验证异常链路**

  覆盖签章准入拒绝、文档回写失败、合同回写失败、解密下载越权、授权撤销、变更审批驳回、归档输入缺失。

- [ ] **Step 6: 输出验证证据**

  保存测试命令、测试结果、关键日志、接口响应、审计记录和补偿任务记录，作为质量审查输入。

## 6. 完成标志

本批次只有同时满足以下条件才算完成：

- 电子签章可基于审批通过后的正式合同和文档中心版本发起申请，并完成会话、结果回写、验签摘要、合同摘要和时间线回写。
- 签章结果稿、纸质备案扫描件和验签产物均回收到文档中心，不形成签章私有文件真相。
- 加密软件可在文件入库后自动加密，平台内读取统一走受控解密访问。
- 管理端可按部门、人员授权解密下载，命中授权后导出的明文文件可脱离 `CMP` 使用，且全过程可审计。
- 履约、变更、终止、归档均围绕同一 `contract_id` 运行，不创建新的合同主档。
- 变更、终止需要审批时通过流程引擎绑定合同，审批结果回写生命周期摘要和合同主档。
- 归档记录作为归档模块过程真相成立，但合同主档仍保留业务真相地位，归档稿 / 封包引用回到文档中心。
- 三条主线均输出稳定摘要、里程碑、时间线和审计证据，供后续搜索、AI、通知、验收联调消费。

## 7. 验证方式

- 运行 `e-signature` 单元测试、接口测试、会话推进测试、结果回写测试、补偿恢复测试。
- 运行 `encrypted-document` 单元测试、接口测试、授权规则测试、高敏审计测试、导出回收测试。
- 运行 `contract-lifecycle` 单元测试、接口测试、履约 / 变更 / 终止 / 归档流程测试。
- 运行跨模块契约测试，确认第三批只消费第二批真相源，不复制合同、文档、审批主档。
- 运行综合端到端测试，覆盖审批通过 -> 签章 -> 加密受控访问 -> 履约 -> 变更 / 终止 -> 归档。
- 运行异常与补偿测试，覆盖签章回写失败、文档回写失败、授权撤销、下载过期、审批驳回、归档输入缺失。
- 运行类型检查、lint、构建或项目等价验证命令；如仓库尚未配置对应命令，执行者必须在交付报告中明确说明缺失项。

## 8. 质量审查要求

- 必须由独立质量审查子代理复核本批次产物，不由实现子代理自审即通过。
- 审查重点一：第三批是否只消费第二批主链路真相源，是否存在私建合同、文档或审批真相。
- 审查重点二：电子签章是否严格建立在审批通过事实和文档中心正式版本之上。
- 审查重点三：加密软件是否保持平台内受控使用为默认规则，解密下载是否仅作为授权例外。
- 审查重点四：履约、变更、终止、归档是否围绕同一 `contract_id`，变更和终止是否没有创建新合同主档。
- 审查重点五：所有高敏动作、状态回写、补偿任务是否具备审计和回查证据。
- 审查输出必须包含发现问题、证据位置、影响范围、修复建议和复审结论。

## 9. 风险与控制

- 风险：第三批提前启动时主链路挂载点不可消费。控制：Task 1 先执行启动门禁测试，不满足即登记阻塞。
- 风险：签章模块把结果文件长期保存在自身私域。控制：签章结果必须先回写文档中心版本链，再回写合同摘要。
- 风险：解密下载被误做成默认下载能力。控制：平台内访问与授权解密下载分离建模，下载必须命中部门 / 人员授权。
- 风险：履约、变更、终止、归档各自生成合同解释口径。控制：统一 `contract_id`、统一摘要、统一时间线，合同主档保留业务真相。
- 风险：高敏审计缺失导致验收不可证明。控制：授权、访问、导出、签章、归档、状态回写均必须写审计并纳入端到端验证证据。

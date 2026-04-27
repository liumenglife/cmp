# 湖南星邦智能装备股份有限公司合同管理平台 Special Design Plan

## 1. 文档说明

本文档是当前 10 条正式主线的专项设计索引、已完成基线索引与后续维护入口，
用于统一定位各主线 `special-designs/` 目录中的正式专项设计成果。

本文档只回答以下问题：

- 哪些专项设计已纳入正式基线
- 各主线专项设计之间的阅读依赖参考
- 每条主线的专项设计入口清单
- 后续维护时应从哪些入口同步更新

本文档不替代：

- 总平台 [`Implementation Plan`](./implementation-plan.md)
- 总平台 [`Implementation Batch Plan`](./implementation-batch-plan.md)
- 各主线既有 `Architecture Design` / `API Design` / `Detailed Design`
- 各专项设计正文

## 2. 来源文档范围

当前 10 条正式主线均已具备专项设计基线，来源文档如下：

| 顺序 | 主线 | 来源文档 | 专项设计入口依据 |
| --- | --- | --- | --- |
| 1 | `identity-access` | [`docs/technicals/foundations/identity-access/detailed-design.md`](./foundations/identity-access/detailed-design.md) | 第 11 节 |
| 2 | `agent-os` | [`docs/technicals/foundations/agent-os/detailed-design.md`](./foundations/agent-os/detailed-design.md) | 第 14 节 |
| 3 | `integration-hub` | [`docs/technicals/foundations/integration-hub/detailed-design.md`](./foundations/integration-hub/detailed-design.md) | 第 11 节 |
| 4 | `contract-core` | [`docs/technicals/modules/contract-core/detailed-design.md`](./modules/contract-core/detailed-design.md) | 第 13 节 |
| 5 | `document-center` | [`docs/technicals/modules/document-center/detailed-design.md`](./modules/document-center/detailed-design.md) | 第 12 节 |
| 6 | `workflow-engine` | [`docs/technicals/modules/workflow-engine/detailed-design.md`](./modules/workflow-engine/detailed-design.md) | 第 13 节 |
| 7 | `e-signature` | [`docs/technicals/modules/e-signature/detailed-design.md`](./modules/e-signature/detailed-design.md) | 第 11 节 |
| 8 | `encrypted-document` | [`docs/technicals/modules/encrypted-document/detailed-design.md`](./modules/encrypted-document/detailed-design.md) | 第 12 节 |
| 9 | `contract-lifecycle` | [`docs/technicals/modules/contract-lifecycle/detailed-design.md`](./modules/contract-lifecycle/detailed-design.md) | 第 11 节 |
| 10 | `intelligent-applications` | [`docs/technicals/modules/intelligent-applications/detailed-design.md`](./modules/intelligent-applications/detailed-design.md) | 第 13 节 |

## 3. 专项基线阅读依赖参考

阅读专项设计基线时，可参考“底座先行、主链路收口、挂接能力后置、智能能力最后”的依赖关系理解上下游边界。

该顺序仅作为阅读依赖参考，不作为实现排期依据；正式实现排期以总平台 [`Implementation Batch Plan`](./implementation-batch-plan.md) 为准。

阅读依赖参考顺序如下：

1. `identity-access`
2. `integration-hub`
3. `agent-os`
4. `document-center`
5. `workflow-engine`
6. `contract-core`
7. `e-signature`
8. `encrypted-document`
9. `contract-lifecycle`
10. `intelligent-applications`

依赖原因如下：

- `identity-access` 用于先收口组织、角色、权限、数据权限和外部身份接入真相，避免审批、解密授权和多端访问各自定义主体边界
- `integration-hub` 用于先收口外围字段映射、签名校验、适配器和重试补偿策略，减少后续 `OA`、企业微信及其他外围联调返工
- `agent-os` 作为 AI 运行时底座，应先收口 `QueryEngine`、薄 `Harness` 内核、工具协议、模型路由、记忆、人工确认和委派调度边界，为后续智能能力提供稳定底层契约
- `document-center` 是文件真相源，签章、加密、搜索、`OCR`、归档都围绕它挂接，因此阅读下游专项设计前应先确认其专项基线
- `workflow-engine` 是审批编排和 `OA` 桥接核心，合同主链路与生命周期管理都依赖其节点规则、组织绑定和运行时控制边界
- `contract-core` 作为业务真相主档，阅读时应结合文档中心与流程引擎已经稳定的下层边界
- `e-signature` 与 `encrypted-document` 都强依赖文档中心读写链路，并受合同状态与审批状态稳定性影响，适合在主链路专项基线可读后再理解
- `contract-lifecycle` 依赖合同状态、文档版本链、审批结果和归档输入边界，适合在主链路与挂接能力稳定后阅读
- `intelligent-applications` 依赖 `Agent OS`、文档中心、合同主档、搜索 / `OCR` 结果与多语言治理边界，适合作为最后一组阅读专项基线

## 4. 已完成主线索引

专项设计基线按四组维护：

### 第一组：底座专项设计

- `identity-access`
- `integration-hub`
- `agent-os`

### 第二组：主链路专项设计

- `document-center`
- `workflow-engine`
- `contract-core`

### 第三组：挂接型业务专项设计

- `e-signature`
- `encrypted-document`
- `contract-lifecycle`

### 第四组：智能能力专项设计

- `intelligent-applications`

## 5. 专项设计总表

本表用于冻结、验收、排期引用和质量检查。验收口径统一为：边界已冻结；关键异常路径已覆盖；不与对应 `API Design` 和 `Detailed Design` 冲突；可被 [`Implementation Batch Plan`](./implementation-batch-plan.md) 或对应主线 `Implementation Plan` 引用。

| 主线 | 专项文件 | 范围说明 | 上游依赖 | 下游影响 | 优先级 | 验收口径 |
| --- | --- | --- | --- | --- | --- | --- |
| `identity-access` | `org-rule-resolution-design.md` | 组织规则解析、人员命中与组织快照 | 身份主档、组织主数据 | 审批、签章、数据权限 | 高 | 按统一四项验收 |
| `identity-access` | `external-identity-protocol-design.md` | 外部身份协议交换、绑定预检、会话准入 | `integration-hub` 标准化票据 | 登录、企业微信入口、审计 | 高 | 按统一四项验收 |
| `identity-access` | `data-scope-sql-pushdown-design.md` | 数据权限表达、查询下推与审计 | 组织、角色、岗位、合同归属 | 合同台账、搜索、报表 | 高 | 按统一四项验收 |
| `identity-access` | `decrypt-download-authorization-design.md` | 解密下载授权配置、命中判定与审计 | 文档权限、组织权限 | 加密文档、文档中心下载 | 高 | 按统一四项验收 |
| `identity-access` | `identity-migration-and-bootstrap-design.md` | 身份初始化、迁移、冲突处置 | 历史人员与组织数据 | 全平台登录和权限 | 高 | 按统一四项验收 |
| `integration-hub` | `external-field-mapping-design.md` | 外围字段映射、码表转换、冲突处理 | 外围系统字段来源 | 合同入站、审批、主数据 | 高 | 按统一四项验收 |
| `integration-hub` | `signing-and-ticket-security-design.md` | 外围签名、票据、安全校验 | 外围系统协议 | 入站接口、回调接口 | 高 | 按统一四项验收 |
| `integration-hub` | `adapter-runtime-design.md` | 适配器运行时、连接器生命周期 | 集成端点配置 | 外围系统联调与监控 | 高 | 按统一四项验收 |
| `integration-hub` | `retry-timeout-and-alerting-design.md` | 重试、超时、告警、降级 | 平台任务与告警能力 | 外围调用稳定性 | 高 | 按统一四项验收 |
| `integration-hub` | `reconciliation-and-raw-message-governance-design.md` | 对账、原始报文治理、差异闭环 | 外围交换日志 | 审计、工单、数据修复 | 中 | 按统一四项验收 |
| `agent-os` | `prompt-layering-and-versioning-design.md` | 提示词分层、版本、发布治理 | `QueryEngine` 运行时 | AI 任务输出稳定性 | 高 | 按统一四项验收 |
| `agent-os` | `tool-contract-and-sandbox-design.md` | 工具契约、授权、沙箱执行 | 权限与审计底座 | AI 工具调用安全 | 高 | 按统一四项验收 |
| `agent-os` | `provider-routing-and-quota-design.md` | Provider 路由、配额、熔断 | 模型抽象层 | 智能应用成本与稳定性 | 高 | 按统一四项验收 |
| `agent-os` | `memory-retrieval-and-expiration-design.md` | 记忆召回、过期、证据约束 | 文档中心、合同主档 | AI 上下文质量 | 中 | 按统一四项验收 |
| `agent-os` | `human-confirmation-and-console-design.md` | 人工确认、控制台、裁决回写 | 权限、审计、任务中心 | 高风险 AI 动作闭环 | 高 | 按统一四项验收 |
| `agent-os` | `delegation-scheduler-design.md` | 委派调度、子运行、回收合并 | `QueryEngine` 运行态 | 多 Agent 协作 | 高 | 按统一四项验收 |
| `agent-os` | `verification-and-performance-design.md` | 验证门禁、性能指标、回归检查 | AI 运行时与任务结果 | 智能能力验收 | 中 | 按统一四项验收 |
| `agent-os` | `auto-dream-daemon-candidate-quality-design.md` | 候选生成、自演进、质量筛选 | 记忆与验证结果 | 智能建议质量 | 中 | 按统一四项验收 |
| `agent-os` | `specialized-agent-persona-catalog-design.md` | 专用 Agent 人格目录与适用边界 | 角色权限、任务类型 | 智能应用编排 | 中 | 按统一四项验收 |
| `agent-os` | `drill-and-operations-runbook-design.md` | 演练、运维、恢复手册 | 运行时监控与任务中心 | 运维验收与应急 | 中 | 按统一四项验收 |
| `document-center` | `object-storage-and-lifecycle-design.md` | 对象存储、版本生命周期 | 文件上传、存储层 | 合同附件、签章、加密 | 高 | 按统一四项验收 |
| `document-center` | `preview-rendering-and-text-layer-design.md` | 预览、页级切片、文本层 | 文档版本链、OCR | 批注、搜索、AI | 高 | 按统一四项验收 |
| `document-center` | `annotation-anchor-and-relocation-design.md` | 批注锚点、重定位 | 预览代次与文本层 | 合同审阅与协作 | 中 | 按统一四项验收 |
| `document-center` | `redline-diff-artifact-design.md` | 修订对比、差异产物 | 文档版本链 | 合同审阅、归档证据 | 中 | 按统一四项验收 |
| `document-center` | `crypto-handle-and-secure-export-design.md` | 加密句柄、安全导出 | 加密文档、权限授权 | 文件下载和外发 | 高 | 按统一四项验收 |
| `document-center` | `ocr-search-signature-archive-binding-design.md` | OCR、搜索、签章、归档绑定 | 文档版本链 | 下游挂接能力 | 高 | 按统一四项验收 |
| `document-center` | `ddl-sharding-and-capacity-design.md` | 表结构分片、容量规划 | 文档对象模型 | 性能和扩容 | 中 | 按统一四项验收 |
| `workflow-engine` | `workflow-dsl-and-validator-design.md` | 流程 DSL 与校验器 | 审批需求、组织主数据 | 流程定义发布 | 高 | 按统一四项验收 |
| `workflow-engine` | `workflow-canvas-protocol-design.md` | 流程画布协议 | 前端可视化配置 | 审批配置界面 | 中 | 按统一四项验收 |
| `workflow-engine` | `org-rule-evaluator-design.md` | 组织规则求值 | 身份组织主数据 | 审批节点选人 | 高 | 按统一四项验收 |
| `workflow-engine` | `parallel-and-countersign-aggregation-design.md` | 并行、会签、聚合判定 | 流程实例状态 | 审批推进与回写 | 高 | 按统一四项验收 |
| `workflow-engine` | `oa-bridge-mapping-and-compensation-design.md` | OA 桥接、字段映射、补偿 | `integration-hub`、OA 回调 | 审批主链路 | 高 | 按统一四项验收 |
| `workflow-engine` | `task-executor-and-notification-runtime-design.md` | 任务执行器、通知运行时 | 平台任务中心 | 待办、消息、告警 | 高 | 按统一四项验收 |
| `workflow-engine` | `instance-migration-and-admin-intervention-design.md` | 实例迁移、人工干预 | 流程实例与审计 | 运维与异常修复 | 中 | 按统一四项验收 |
| `contract-core` | `contract-document-version-binding-design.md` | 合同与文档版本绑定 | 文档中心版本链 | 合同主档、签章、归档 | 高 | 按统一四项验收 |
| `contract-core` | `contract-approval-bridge-design.md` | 合同审批桥接 | 工作流与 OA | 合同状态推进 | 高 | 按统一四项验收 |
| `contract-core` | `clause-semantic-tagging-and-recommendation-design.md` | 条款语义、推荐、人工决策 | 条款库、AI 底座 | 起草与审核 | 中 | 按统一四项验收 |
| `contract-core` | `contract-search-index-design.md` | 合同检索索引 | 合同主档、文档文本层 | 搜索、报表、AI | 中 | 按统一四项验收 |
| `contract-core` | `contract-detail-aggregation-design.md` | 合同详情聚合 | 合同、审批、文档、签章摘要 | 前端详情页 | 中 | 按统一四项验收 |
| `contract-core` | `multilingual-governance-design.md` | 多语言字段与字典治理 | 字典、模板、接口 | 国际化展示 | 中 | 按统一四项验收 |
| `contract-core` | `migration-loadtest-and-cutover-design.md` | 迁移、压测、切换 | 历史合同数据 | 上线准备 | 高 | 按统一四项验收 |
| `e-signature` | `seal-resource-and-certificate-design.md` | 印章资源、证书介质、授权快照 | 身份权限、文档中心 | 签章准入和验签 | 高 | 按统一四项验收 |
| `e-signature` | `signature-coordinate-and-rendering-design.md` | 坐标、渲染、验签策略 | 文档中心、印章资源 | 签章结果稿 | 高 | 按统一四项验收 |
| `e-signature` | `signing-party-orchestration-design.md` | 参与方、顺序、完成判定 | 合同主档、审批摘要 | 签署会话推进 | 高 | 按统一四项验收 |
| `e-signature` | `signature-engine-adapter-design.md` | 引擎适配、参数映射、能力声明 | 印章、坐标、编排 | 引擎调用和结果归一 | 高 | 按统一四项验收 |
| `e-signature` | `batch-resign-and-result-optimization-design.md` | 批量补签、结果优化、历史迁移 | 签章结果、文档中心 | 批处理与迁移 | 中 | 按统一四项验收 |
| `e-signature` | `signature-runtime-parameters-design.md` | 审计、重试、锁、摘要窗口 | 平台任务与控制面 | 签章运行稳定性 | 高 | 按统一四项验收 |
| `encrypted-document` | `crypto-algorithm-and-key-hierarchy-design.md` | 加密算法、密钥层级 | 文档中心、权限底座 | 文档加密与解密 | 高 | 按统一四项验收 |
| `encrypted-document` | `controlled-read-handle-design.md` | 受控读取句柄 | 权限、文档中心 | 在线预览和访问 | 高 | 按统一四项验收 |
| `encrypted-document` | `plaintext-export-package-design.md` | 明文导出包 | 解密下载授权 | 外发和审计 | 高 | 按统一四项验收 |
| `encrypted-document` | `authorization-scope-expression-design.md` | 授权范围表达式 | 身份权限 | 解密下载命中 | 高 | 按统一四项验收 |
| `encrypted-document` | `desensitization-and-secondary-storage-design.md` | 脱敏与二级存储 | 文档密级、权限 | 检索、AI 消费 | 中 | 按统一四项验收 |
| `encrypted-document` | `consumer-adaptation-and-pressure-test-design.md` | 消费方适配、压测 | 文档中心和下游模块 | 性能验收 | 中 | 按统一四项验收 |
| `encrypted-document` | `ddl-event-and-retry-parameter-design.md` | 表结构治理、事件、重试参数 | 加密对象模型 | 实现与运维 | 高 | 按统一四项验收 |
| `contract-lifecycle` | `fulfillment-rules-and-risk-scoring-design.md` | 履约规则、风险评分 | 合同主档、AI 底座 | 履约预警 | 中 | 按统一四项验收 |
| `contract-lifecycle` | `change-impact-and-write-back-design.md` | 变更影响、回写 | 合同状态和审批 | 合同变更闭环 | 高 | 按统一四项验收 |
| `contract-lifecycle` | `termination-settlement-and-access-control-design.md` | 终止、结算、访问控制 | 合同履约、权限 | 终止闭环 | 高 | 按统一四项验收 |
| `contract-lifecycle` | `archive-package-and-borrow-return-design.md` | 归档封包、借阅归还 | 文档中心、合同状态 | 档案管理 | 高 | 按统一四项验收 |
| `contract-lifecycle` | `lifecycle-dictionary-and-notification-design.md` | 生命周期字典、通知 | 字典与消息中心 | 履约提醒 | 中 | 按统一四项验收 |
| `contract-lifecycle` | `summary-rebuild-and-backfill-design.md` | 摘要重建、历史回填 | 合同主档、结果事件 | 台账和搜索 | 中 | 按统一四项验收 |
| `intelligent-applications` | `ocr-engine-and-layout-analysis-design.md` | OCR 引擎与版面解析 | 文档中心 | 搜索、AI 抽取 | 高 | 按统一四项验收 |
| `intelligent-applications` | `search-index-and-rebuild-design.md` | 搜索索引与重建 | 合同、文档、OCR | 检索和智能问答 | 高 | 按统一四项验收 |
| `intelligent-applications` | `ai-context-assembly-and-output-guardrails-design.md` | AI 上下文组装与输出护栏 | `agent-os`、合同、文档 | AI 审核和草稿 | 高 | 按统一四项验收 |
| `intelligent-applications` | `candidate-ranking-and-quality-evaluation-design.md` | 候选排序、质量评估 | AI 输出与人工反馈 | 推荐质量 | 中 | 按统一四项验收 |
| `intelligent-applications` | `multilingual-knowledge-governance-design.md` | 多语言知识治理 | 多语言字典、文档文本 | 国际化智能能力 | 中 | 按统一四项验收 |
| `intelligent-applications` | `result-writeback-and-conflict-resolution-design.md` | 结果回写、冲突处理 | 合同主档、人工确认 | AI 结果闭环 | 高 | 按统一四项验收 |
| `intelligent-applications` | `ops-monitoring-alert-and-recovery-design.md` | 运维监控、告警、恢复 | 任务中心、监控 | AI 运维验收 | 中 | 按统一四项验收 |

## 6. 后续维护入口

专项设计统一维护在各主线自己的 `special-designs/` 目录下：

- `docs/technicals/foundations/<module>/special-designs/*.md`
- `docs/technicals/modules/<module>/special-designs/*.md`

采用该目录后：

- 不需要继续把更细内容堆回模块 `detailed-design.md`
- 每一类专项边界可独立维护、评审和排期
- 后续实现、测试、联调与运维说明可按专题直接引用
- 维护专项设计时，应优先更新对应主线目录下的正式文件，再同步本索引

## 7. 各主线专项设计文档清单

### 7.1 `identity-access`

目录：`docs/technicals/foundations/identity-access/special-designs/`

- `org-rule-resolution-design.md`
- `external-identity-protocol-design.md`
- `data-scope-sql-pushdown-design.md`
- `decrypt-download-authorization-design.md`
- `identity-migration-and-bootstrap-design.md`

### 7.2 `integration-hub`

目录：`docs/technicals/foundations/integration-hub/special-designs/`

- `external-field-mapping-design.md`
- `signing-and-ticket-security-design.md`
- `adapter-runtime-design.md`
- `retry-timeout-and-alerting-design.md`
- `reconciliation-and-raw-message-governance-design.md`

### 7.3 `agent-os`

目录：`docs/technicals/foundations/agent-os/special-designs/`

`agent-os` 基线为：`QueryEngine` 驱动薄 `Harness` 内核，面向合同管理、审批、文档、签署、履约、风控、运维审计等企业业务场景。

- `prompt-layering-and-versioning-design.md`
- `tool-contract-and-sandbox-design.md`
- `provider-routing-and-quota-design.md`
- `memory-retrieval-and-expiration-design.md`
- `human-confirmation-and-console-design.md`
- `delegation-scheduler-design.md`
- `verification-and-performance-design.md`
- `auto-dream-daemon-candidate-quality-design.md`
- `specialized-agent-persona-catalog-design.md`
- `drill-and-operations-runbook-design.md`

### 7.4 `document-center`

目录：`docs/technicals/modules/document-center/special-designs/`

- `object-storage-and-lifecycle-design.md`
- `preview-rendering-and-text-layer-design.md`
- `annotation-anchor-and-relocation-design.md`
- `redline-diff-artifact-design.md`
- `crypto-handle-and-secure-export-design.md`
- `ocr-search-signature-archive-binding-design.md`
- `ddl-sharding-and-capacity-design.md`

### 7.5 `workflow-engine`

目录：`docs/technicals/modules/workflow-engine/special-designs/`

- `workflow-dsl-and-validator-design.md`
- `workflow-canvas-protocol-design.md`
- `org-rule-evaluator-design.md`
- `parallel-and-countersign-aggregation-design.md`
- `oa-bridge-mapping-and-compensation-design.md`
- `task-executor-and-notification-runtime-design.md`
- `instance-migration-and-admin-intervention-design.md`

### 7.6 `contract-core`

目录：`docs/technicals/modules/contract-core/special-designs/`

- `contract-document-version-binding-design.md`
- `contract-approval-bridge-design.md`
- `clause-semantic-tagging-and-recommendation-design.md`
- `contract-search-index-design.md`
- `contract-detail-aggregation-design.md`
- `multilingual-governance-design.md`
- `migration-loadtest-and-cutover-design.md`

### 7.7 `e-signature`

目录：`docs/technicals/modules/e-signature/special-designs/`

- `seal-resource-and-certificate-design.md`
- `signature-coordinate-and-rendering-design.md`
- `signing-party-orchestration-design.md`
- `signature-engine-adapter-design.md`
- `batch-resign-and-result-optimization-design.md`
- `signature-runtime-parameters-design.md`

### 7.8 `encrypted-document`

目录：`docs/technicals/modules/encrypted-document/special-designs/`

- `crypto-algorithm-and-key-hierarchy-design.md`
- `controlled-read-handle-design.md`
- `plaintext-export-package-design.md`
- `authorization-scope-expression-design.md`
- `desensitization-and-secondary-storage-design.md`
- `consumer-adaptation-and-pressure-test-design.md`
- `ddl-event-and-retry-parameter-design.md`

### 7.9 `contract-lifecycle`

目录：`docs/technicals/modules/contract-lifecycle/special-designs/`

- `fulfillment-rules-and-risk-scoring-design.md`
- `change-impact-and-writeback-design.md`
- `termination-settlement-and-access-control-design.md`
- `archive-package-and-borrow-return-design.md`
- `lifecycle-dictionary-and-notification-design.md`
- `summary-rebuild-and-backfill-design.md`

### 7.10 `intelligent-applications`

目录：`docs/technicals/modules/intelligent-applications/special-designs/`

- `ocr-engine-and-layout-analysis-design.md`
- `search-index-and-rebuild-design.md`
- `ai-context-assembly-and-output-guardrails-design.md`
- `candidate-ranking-and-quality-evaluation-design.md`
- `multilingual-knowledge-governance-design.md`
- `result-writeback-and-conflict-resolution-design.md`
- `ops-monitoring-alert-and-recovery-design.md`

## 8. 索引维护规则

后续维护时，先更新对应专项设计正文，再同步本索引。

如专项设计新增、合并、删除或改名，应同步父级 `Detailed Design`、本索引和相关 `Implementation Plan` 引用。

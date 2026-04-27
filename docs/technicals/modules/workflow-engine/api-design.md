# 流程引擎子模块 API Design

## 1. 文档说明

本文档是 `CMP` 流程引擎子模块的第一份正式 `API Design`。

本文在以下输入文档约束下，定义流程引擎子模块的资源边界、
请求/响应契约、鉴权约束、错误码复用策略，以及 `OA` 桥接、
平台流程引擎配置端、运行时、审批摘要、异步回调等 API 边界。

### 1.1 输入

- 上游需求基线：[`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- 总平台架构：[`Architecture Design`](../../architecture-design.md)
- 总平台接口规范：[`API Design`](../../api-design.md)
- 总平台共享内部边界：[`Detailed Design`](../../detailed-design.md)
- 流程引擎子模块架构：[`Architecture Design`](./architecture-design.md)

### 1.2 输出

- 流程引擎子模块的正式 API 资源与接口分组
- `OA` 主审批桥接与平台流程引擎承接双路径的接口边界
- 面向配置端、运行时、审批摘要、异步回调的契约约束
- 需要继续下沉到本模块 `Detailed Design` 的内部设计边界

### 1.3 阅读边界

本文只描述 API 可见契约，不展开以下内容：

- 不复述需求范围、业务背景、技术选型理由
- 不写物理表结构、索引、事务与锁策略
- 不展开节点 DSL、规则表达式、内部快照结构
- 不展开流程内部状态机、并行聚合算法、会签内部判定细节
- 不写实施排期、任务拆分与联调日程

## 2. API 边界

### 2.1 子模块对外接口分组

- `OA` 桥接接口：承接平台向 `OA` 发起审批、接收 `OA` 回调、
  查询平台侧审批摘要。
- 平台流程引擎接口：承接平台审批引擎的定义、版本、发布、实例、
  任务、动作、查询。
- 配置端接口：面向管理端配置页面，承接流程定义草稿保存、版本比较、
  发布校验、启停等能力。
- 运行时接口：面向合同主链路与审批执行链路，承接实例发起、待办查询、
  任务处理、催办、终止、进度查询。
- 审批摘要接口：面向合同详情、台账、消息中心等模块提供统一审批摘要，
  屏蔽 `OA` 与平台引擎的执行差异。

### 2.2 边界判断

- 默认主审批路径仍优先走 `OA`。
- 平台流程引擎是一期开箱可用的正式能力，不是仅保留接口壳子。
- API 必须同时支持 `OA` 主路径与平台引擎承接路径，但对上层业务保持
  统一审批摘要与统一业务回写入口。
- 流程引擎不拥有合同主档，只通过 `contract_id` 等业务关联键与合同主链路
  绑定。
- 流程引擎不拥有组织主数据，只暴露“组织绑定对象”和“绑定类型”等
  API 可见契约。
- 本模块只定义 `CMP` 平台侧接口，不定义 `OA` 系统内部原生接口和页面。

## 3. 核心资源划分

### 3.1 `ProcessDefinition`

用途：定义一个可被发布和运行的流程模板。

API 可见核心字段：

- `definition_id`
- `process_code`
- `process_name`
- `business_type`
- `approval_mode`：`OA` / `CMP`
- `definition_status`
- `current_draft_version`
- `latest_published_version`
- `organization_binding_required`

说明：

- `definition_payload` 作为高阶配置载荷对外暴露，但本文不展开其内部 DSL。
- 定义层只表达可发布流程的 API 壳层，不在本文写节点内部模型明细。

### 3.2 `ProcessVersion`

用途：承接流程定义的版本化、发布态与历史可追溯能力。

API 可见核心字段：

- `version_id`
- `definition_id`
- `version_no`
- `version_status`：如 `DRAFT`、`PUBLISHED`、`DISABLED`
- `published_at`
- `published_by`
- `version_note`

说明：

- 版本接口只暴露版本生命周期与引用关系。
- 版本差异计算、兼容策略、旧实例与新版本切换规则下沉到
  [`Detailed Design`](./detailed-design.md)。

### 3.3 `Process`

用途：表示一次实际运行中的审批流程实例。

API 可见核心字段：

- `process_id`
- `definition_id`
- `version_id`
- `contract_id`
- `approval_mode`
- `instance_status`
- `current_node_id`
- `started_by`
- `started_at`
- `finished_at`
- `source_system_instance_id`

说明：

- 当主路径为 `OA` 时，`source_system_instance_id` 可映射 `oa_instance_id`。
- 当主路径为平台引擎时，实例由 `CMP` 内部直接驱动。

### 3.4 `ApprovalTask`

用途：表示运行时分发给审批参与人的待办或已办任务。

API 可见核心字段：

- `task_id`
- `process_id`
- `task_status`
- `task_type`
- `node_id`
- `assignee_user_id`
- `assignee_org_unit_id`
- `candidate_list`
- `due_at`
- `completed_at`
- `available_action_list`

说明：

- 任务接口只表达当前任务、候选人和可执行动作。
- 候选人解析算法、组织规则求值过程不在本文展开。

### 3.5 `ApprovalAction`

用途：表示针对任务或实例执行的一次审批动作。

API 可见核心字段：

- `approval_action_id`
- `process_id`
- `task_id`
- `action_type`
- `operator_user_id`
- `target_user_id`
- `comment`
- `acted_at`
- `action_result`

说明：

- 动作资源对外只定义动作类型、操作者、目标对象和结果摘要。
- 会签内部票数、聚合策略、分支收敛规则不在本文展开。

### 3.6 `ApprovalSummary`

用途：向合同详情、台账、消息中心提供统一审批摘要。

API 可见核心字段：

- `contract_id`
- `approval_mode`
- `summary_status`
- `current_node_name`
- `current_approver_list`
- `latest_action`
- `started_at`
- `updated_at`
- `finished_at`
- `source_system`
- `source_system_instance_id`

说明：

- 审批摘要是统一查询视图，不等于完整审批原始记录。
- 当主路径为 `OA` 时，一期只保证平台侧摘要同步，不承诺完整替代 `OA`
  原始审批明细展示。

## 4. 统一约定

### 4.1 协议

- 协议采用 `HTTPS + JSON`。
- 编码采用 `UTF-8`。
- 时间字段采用 `ISO 8601`。
- 成功、失败、分页响应结构继承总平台
  [`API Design`](../../api-design.md) 的统一响应约定。
- 异步受理型接口返回 `202`，同步创建返回 `201`，同步查询和更新返回 `200`。

### 4.2 鉴权

- 管理端配置接口要求平台登录态和模块管理权限。
- 运行时任务查询与审批动作接口要求平台登录态和任务访问权限。
- `OA` 回调接口使用系统级签名校验、时间戳校验和重放校验，不依赖用户登录态。
- 平台内部模块调用本子模块时，应以平台统一鉴权上下文传递调用人或系统身份。

### 4.3 幂等

- 创建实例、审批动作、回调接收、催办触发等写接口均支持 `Idempotency-Key`。
- 对同一业务主键和同一动作语义，重复提交应返回首次处理结果或稳定冲突错误。
- `OA` 回调必须以 `oa_instance_id + callback_event_id` 或等效外部唯一键保证幂等。

### 4.4 命名规范

- 路径、查询参数、请求字段、响应字段统一继承总平台
  [`API Design`](../../api-design.md) 的 `snake_case` 规范。
- 资源主键统一使用 `<resource>_id`。
- 外部系统主键统一使用 `<system>_<resource>_id`，例如 `oa_instance_id`。
- 状态枚举统一使用 `UPPER_SNAKE_CASE`。

### 4.5 错误码复用策略

本模块优先复用总平台 [`API Design`](../../api-design.md)
中的通用错误码，不新增平行体系。

优先复用的错误码包括：

- `40001 INVALID_PAYLOAD`
- `40002 INVALID_FIELD_VALUE`
- `40003 INVALID_QUERY_PARAMS`
- `40101 AUTH_REQUIRED`
- `40103 CALLBACK_SIGNATURE_INVALID`
- `40301 PERMISSION_DENIED`
- `40303 APPROVAL_ACTION_FORBIDDEN`
- `40403 EXTERNAL_INSTANCE_NOT_FOUND`
- `40905 IDEMPOTENCY_CONFLICT`
- `42203 OA_SYNC_FAILED`
- `42207 EXTERNAL_CALLBACK_PROCESSING_FAILED`
- `50001 INTERNAL_SERVER_ERROR`
- `50201 EXTERNAL_SYSTEM_UNAVAILABLE`

模块级补充原则：

- 仅当总平台错误码无法准确表达流程定义、实例或任务语义时，才在后续正式
  `OpenAPI` 中补充流程域错误码。
- 新增流程域错误码时，仍需遵守总平台错误域分段规则，不单独定义新的返回结构。

## 5. 与 `OA` 的桥接接口边界

### 5.1 边界说明

- 本组接口只处理 `CMP` 与 `OA` 之间的审批发起、状态回传、摘要同步。
- 本组接口不承诺替代 `OA` 原生审批页面、完整意见流和原始历史查询接口。
- `OA` 仍是默认主审批路径，但平台保留发起参数治理、业务关联、状态承接、
  审计留痕和摘要查询责任。

### 5.2 接口分组

#### `POST /api/integrations/oa/approval-requests`

用途：由 `CMP` 向 `OA` 发起审批实例。

请求重点字段：

- `contract_id`
- `definition_id` 或 `process_code`
- `oa_flow_code`
- `business_title`
- `form_fields`
- `approver_list`
- `attachment_list`
- `callback_url`

响应重点字段：

- `process_id`
- `oa_instance_id`
- `approval_mode`
- `instance_status`

#### `POST /api/integrations/oa/approval-callback`

用途：接收 `OA` 回调的审批状态、节点结果和完成结果。

请求重点字段：

- `oa_instance_id`
- `callback_event_id`
- `contract_id`
- `approval_status`
- `current_node_name`
- `current_approver_list`
- `comment_list`
- `approved_at`
- `finished_at`

响应重点字段：

- `accepted`
- `processed_at`
- `trace_id`

#### `GET /api/contracts/{contract_id}/approval-summary`

用途：查询平台侧维护的 `OA` / `CMP` 统一审批摘要。

### 5.3 双路径约束

- 业务侧发起审批时，可见的是统一审批入口和统一摘要接口。
- 当审批实际走 `OA` 时，平台负责桥接、摘要同步和合同侧状态回写。
- 当 `OA` 无法承接时，平台改由本地流程引擎正式执行，但仍通过同一摘要口径
  向上层暴露结果。

## 6. 平台流程引擎接口边界

### 6.1 配置端接口

本组接口面向管理端与配置端页面，负责流程定义、版本和发布管理。

#### `POST /api/approval-engine/process-definitions`

用途：创建流程定义草稿。

请求重点字段：

- `process_code`
- `process_name`
- `business_type`
- `approval_mode`
- `definition_payload`

#### `PUT /api/approval-engine/process-definitions/{definition_id}`

用途：更新流程定义草稿。

#### `GET /api/approval-engine/process-definitions/{definition_id}`

用途：查询流程定义详情、当前草稿和发布摘要。

#### `GET /api/approval-engine/process-definitions`

用途：按业务类型、状态、承接模式查询流程定义列表。

#### `POST /api/approval-engine/process-definitions/{definition_id}/publish`

用途：发布指定流程定义，生成新的可运行版本。

请求重点字段：

- `version_note`
- `publish_comment`

#### `POST /api/approval-engine/process-definitions/{definition_id}/disable`

用途：停用流程定义的当前发布版本。

#### `GET /api/approval-engine/process-definitions/{definition_id}/versions`

用途：查询流程版本列表。

#### `GET /api/approval-engine/process-versions/{version_id}`

用途：查询指定版本摘要。

### 6.2 运行时接口

本组接口面向合同业务链路和审批执行链路，负责实例、任务和进度查询。

#### `POST /api/approval-engine/processes`

用途：创建审批实例；当命中平台承接条件时，由 `CMP` 流程引擎正式承接。

请求重点字段：

- `contract_id`
- `definition_id` 或 `version_id`
- `approval_mode`
- `starter_user_id`
- `business_context`

#### `GET /api/approval-engine/processes/{process_id}`

用途：查询实例详情、当前节点摘要和历史动作摘要。

#### `GET /api/approval-engine/processes/{process_id}/diagram`

用途：查询实例当前流程图状态，用于前端展示。

#### `GET /api/approval-engine/tasks`

用途：查询待办、已办、抄送、可处理任务列表。

查询重点字段：

- `task_status`
- `assignee_user_id`
- `contract_id`
- `process_id`
- `page`
- `page_size`

#### `GET /api/approval-engine/tasks/{task_id}`

用途：查询单个任务详情、可执行动作和审批上下文摘要。

### 6.3 审批摘要接口

#### `GET /api/contracts/{contract_id}/approval-summary`

用途：返回合同维度统一审批摘要。

返回重点字段：

- `contract_id`
- `approval_mode`
- `summary_status`
- `current_node_name`
- `current_approver_list`
- `latest_action`
- `source_system`
- `source_system_instance_id`

#### `GET /api/approval-engine/processes/{process_id}/summary`

用途：返回实例维度审批摘要，供消息、审计、合同时间线等模块复用。

## 7. 组织架构绑定在 API 层的体现

### 7.1 API 可见契约

流程定义与运行时接口仅保留以下高阶组织绑定字段：

- `binding_type`：如 `USER`、`ORG_UNIT`、`ORG_RULE`
- `binding_object_id`
- `binding_object_name`
- `binding_scope`
- `candidate_resolution_mode`

### 7.2 约束说明

- 每个审批节点都必须至少声明一个组织绑定对象，不允许只保留抽象节点类型。
- `binding_type=USER` 表示直接绑定人员。
- `binding_type=ORG_UNIT` 表示直接绑定部门或组织单元。
- `binding_type=ORG_RULE` 表示基于组织规则选人，但 API 只暴露规则引用，
  不展开规则表达式和运行时快照。
- API 可返回候选人解析结果摘要，如 `candidate_list`、`resolved_assignee_list`，
  但不展开内部求值路径。

### 7.3 不在 API 层展开的内容

- 不展开组织规则 DSL
- 不展开快照持久化结构
- 不展开组织变更后的实例补偿策略
- 不展开权限、在岗状态、代理关系的内部求值步骤

## 8. 审批动作接口

### 8.1 统一动作入口

#### `POST /api/approval-engine/processes/{process_id}/actions`

用途：统一提交审批动作。

请求重点字段：

- `task_id`
- `action_type`
- `operator_user_id`
- `target_user_id`
- `comment`
- `attachment_list`

响应重点字段：

- `approval_action_id`
- `process_id`
- `task_id`
- `action_type`
- `action_result`
- `instance_status`

### 8.2 动作类型边界

- `APPROVE`：同意当前任务。
- `REJECT`：驳回当前任务；驳回理由为 API 可见字段，必填规则由正式
  `OpenAPI` 细化。
- `TRANSFER`：转办给指定目标人，要求 `target_user_id`。
- `COUNTERSIGN_ADD`：追加会签人。
- `COUNTERSIGN_SUBMIT`：提交当前会签意见。
- `COUNTERSIGN_PASS`：会签通过动作。
- `COUNTERSIGN_REJECT`：会签驳回动作。
- `REMIND`：催办当前实例或任务。
- `WITHDRAW`：撤回已发起但尚满足撤回条件的实例。
- `TERMINATE`：终止流程实例。

### 8.3 动作边界说明

- 动作接口只定义“调用方可以请求什么动作”和“平台返回什么结果摘要”。
- 会签票数规则、会签收敛条件、回退范围、终止权限判定属于模块
  [`Detailed Design`](./detailed-design.md) 范围。
- `OA` 主路径下的动作不通过本接口直接驱动 `OA` 原生页面动作；平台侧仅保留
  摘要同步和必要的业务补充动作边界。

## 9. 异步与回调边界

### 9.1 异步场景

以下场景允许设计为异步：

- 发起 `OA` 审批后的状态同步与重试
- `OA` 回调后的摘要更新、合同状态回写、通知分发
- 平台引擎催办、超时扫描、异常补偿
- 批量审批摘要刷新或历史数据同步

### 9.2 异步任务契约

#### `POST /api/approval-engine/jobs`

用途：创建流程相关异步任务。

请求重点字段：

- `job_type`
- `owner_type`
- `owner_id`
- `payload`

#### `GET /api/approval-engine/jobs/{job_id}`

用途：查询异步任务状态与结果摘要。

返回重点字段：

- `job_id`
- `job_status`
- `result_code`
- `result_message`
- `started_at`
- `finished_at`

### 9.3 回调边界

- 外部回调当前主要指 `OA` 审批状态回调。
- 回调接口必须支持签名校验、时间戳校验、重放校验和幂等处理。
- 回调成功仅表示平台已受理，不等于全部后续补偿、通知、状态回写都已完成。
- 回调后的内部补偿、重试编排和失败恢复流程不在本文展开。

## 10. 需要下沉到该模块 Detailed Design 的内容边界

以下内容不在本文继续展开，应下沉到
[`Detailed Design`](./detailed-design.md)：

- `definition_payload` 的内部结构、节点 DSL 与规则表达式
- 组织绑定的内部模型、快照结构、解析与补偿机制
- 实例状态机、任务状态机、会签聚合与转办内部规则
- `OA` 桥接的字段映射明细、重试策略、回调补偿流程
- 审批动作的权限判定、可执行性校验与冲突消解细节
- 任务中心协作、超时扫描、催办调度、异常恢复的内部实现
- 主表、索引、审计事件明细与缓存键设计

## 11. 本文结论

流程引擎子模块 `API Design` 在一期明确采用“双路径、统一契约”口径：

- 默认主审批路径优先走 `OA`
- 平台流程引擎是一期正式能力并具备独立承接接口
- 组织架构绑定在 API 层以“绑定类型 + 绑定对象”高阶契约体现
- 对上层业务统一暴露审批实例、审批任务、审批动作和审批摘要接口

本文到此为止只收口模块级接口契约，不继续下沉到内部建模与实现细节。

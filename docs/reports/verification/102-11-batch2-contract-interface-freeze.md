# 第二批合同核心主链路接口冻结清单

## 一、冻结范围

本清单冻结第二批最小实现所需的跨模块共享契约，覆盖 `contract-core`、`document-center`、`workflow-engine` 三条主线之间的字段命名、真相归属、回写方向与最小状态集合。

## 二、共享字段契约

| 字段 | 所有者 | 消费方 | 回写方向 | 最小约束 |
| --- | --- | --- | --- | --- |
| `contract_id` | `contract-core` | `document-center`、`workflow-engine` | 由合同主档生成后向文档绑定与审批发起请求传递；其他模块不得改写 | 合同唯一标识，作为业务对象引用主键 |
| `document_asset_id` | `document-center` | `contract-core`、`workflow-engine` | 文档中心写入后回写合同侧文档摘要，并随审批输入传给流程引擎 | 文档资产唯一标识，表示同一文档主档 |
| `document_version_id` | `document-center` | `contract-core`、`workflow-engine` | 文档中心首版写入或主版本切换后回写合同侧当前版本引用；审批发起消费该版本 | 文档版本唯一标识，合同侧只保存当前业务引用，不复制版本链 |
| `process_id` | `workflow-engine` | `contract-core` | 审批发起后由流程引擎回写合同审批摘要 | 流程实例唯一标识，表示一次审批运行 |
| `approval_summary` | `workflow-engine` | `contract-core` | 审批发起、完成、驳回、终止或异常补偿后回写合同侧统一摘要 | 合同侧只保存统一摘要，不区分 `OA` 或平台路径形成两套摘要 |
| `contract_status` | `contract-core` | `workflow-engine`、台账与详情读模型 | 审批结果按冻结映射回写合同主状态；合同主档是最终状态真相源 | 仅允许第二批冻结状态集合内的值 |
| `timeline_event` | `contract-core` | 台账、详情、审计与后续业务 | 文档绑定、审批发起、审批结果回写时由合同侧记录业务时间线 | 记录事件类型、对象引用、摘要、`trace_id` 与发生时间 |

`timeline_event` 响应字段固定为数组字段，不使用 `timeline_event_list`。事件对象最小载荷固定为：`event_type`、`object_id`、`summary`、`trace_id`、`occurred_at`。

## 三、最小状态集合

### 3.1 合同主状态

| 状态 | 语义 | 归属 |
| --- | --- | --- |
| `DRAFT` | 合同草稿，允许补充正文和附件 | `contract-core` |
| `UNDER_APPROVAL` | 已发起审批，等待审批运行结果 | `contract-core` |
| `APPROVED` | 审批通过，合同主状态完成通过回写 | `contract-core` |
| `REJECTED` | 审批驳回，合同回到业务修订或终止判断入口 | `contract-core` |
| `APPROVAL_TERMINATED` | 审批被终止，合同侧记录终止结果 | `contract-core` |

### 3.2 流程实例状态

| 状态 | 语义 | 归属 |
| --- | --- | --- |
| `STARTED` | 审批实例已创建并接收合同与文档引用 | `workflow-engine` |
| `IN_PROGRESS` | 审批任务推进中 | `workflow-engine` |
| `COMPLETED` | 审批通过完成 | `workflow-engine` |
| `REJECTED` | 审批驳回完成 | `workflow-engine` |
| `TERMINATED` | 审批被终止 | `workflow-engine` |
| `COMPENSATING` | 审批回写或外部桥接存在异常，进入补偿 | `workflow-engine` |

### 3.3 文档状态

| 状态 | 语义 | 归属 |
| --- | --- | --- |
| `FIRST_VERSION_WRITTEN` | 文档资产与首版已写入 | `document-center` |
| `ACTIVE_MAIN_VERSION` | 当前主版本已激活，可被合同和审批消费 | `document-center` |
| `VERSION_INVALIDATED` | 指定版本失效，不再作为当前审批输入 | `document-center` |
| `WRITEBACK_FAILED` | 文档摘要回写合同侧失败，等待补偿 | `document-center` |

## 四、审批结果到合同状态映射

| 流程实例状态 | 合同主状态 | 回写事件 |
| --- | --- | --- |
| `STARTED` | `UNDER_APPROVAL` | `APPROVAL_STARTED` |
| `IN_PROGRESS` | `UNDER_APPROVAL` | `APPROVAL_IN_PROGRESS` |
| `COMPLETED` | `APPROVED` | `APPROVAL_APPROVED` |
| `REJECTED` | `REJECTED` | `APPROVAL_REJECTED` |
| `TERMINATED` | `APPROVAL_TERMINATED` | `APPROVAL_TERMINATED` |
| `COMPENSATING` | 保持最近一次合同主状态 | `APPROVAL_WRITEBACK_COMPENSATING` |

## 五、最小接口冻结

| 接口 | 所有者 | 用途 |
| --- | --- | --- |
| `POST /api/contracts` | `contract-core` | 创建合同主档并返回 `contract_id`、`contract_status` |
| `GET /api/contracts/{contract_id}/master` | `contract-core` | 查询合同主档、文档引用、审批摘要和时间线 |
| `POST /api/document-center/assets` | `document-center` | 写入文档资产首版并绑定 `contract_id` |
| `POST /api/workflow-engine/processes` | `workflow-engine` | 基于合同与文档版本引用发起审批 |
| `POST /api/workflow-engine/processes/{process_id}/results` | `workflow-engine` | 接收审批结果并回写合同状态与时间线 |

## 六、测试驱动证据

### 6.1 先失败证据

- 命令：`mvn -Dtest=Batch2CoreChainContractTests test`
- 失败断言：`jsonPath("$.timeline_event[?(@.event_type == 'DOCUMENT_BOUND')]").isNotEmpty()`。
- 失败原因：接口响应仍返回 `timeline_event_list`，没有冻结字段 `timeline_event`；事件对象也未携带 `summary`。
- 关键失败输出：`PathNotFoundException: Missing property in path $['timeline_event']`。
- 失败位置：`backend/src/test/java/com/cmp/platform/corechain/Batch2CoreChainContractTests.java:55`。

### 6.2 实现后通过证据

- 命令：`mvn -Dtest=Batch2CoreChainContractTests test`
- 结果：`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
- 覆盖断言：合同主档详情返回 `timeline_event`，且 `DOCUMENT_BOUND` 事件摘要为 `文档已绑定合同当前主版本`，`APPROVAL_APPROVED` 事件摘要为 `审批通过并回写合同状态`。

## 七、门禁结论

第二批 Task 1 冻结以上字段、状态与接口作为后续实现的最小契约。后续 Task 2 及之后如需变更字段、状态或回写方向，必须先更新本清单并补充跨模块契约测试。

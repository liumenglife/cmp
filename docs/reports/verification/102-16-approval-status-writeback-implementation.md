# 第二批审批发起、审批承接与合同状态回写闭环验证报告

## 一、验证范围

- 覆盖合同从 `DRAFT` 通过统一审批入口发起平台承接审批，进入 `UNDER_APPROVAL`，审批通过后回写 `APPROVED`、统一审批摘要、合同时间线和审计记录。
- 覆盖默认 `OA` 主路径发起、`OA` 回调、重复回调、乱序回调、统一审批摘要查询、合同详情和台账读取统一审批摘要。
- 覆盖审批回写失败进入补偿任务，补偿状态进入统一审批摘要的桥接健康块。
- 本报告仅覆盖第二批 Task 6；未推进 Task 7 最小端到端闭环验证。

## 二、RED 失败证据

### 2.1 平台承接路径 RED

命令：

```bash
mvn -Dtest=Batch2CoreChainContractTests#unifiedApprovalEntryCanUsePlatformTakeoverAndWriteBackApprovedStatusAndTimeline test
```

失败证据：

```text
Status expected:<202> but was:<404>
No static resource api/contracts/{contract_id}/approvals.
```

结论：失败原因明确指向合同侧统一审批入口 `POST /api/contracts/{contract_id}/approvals` 缺失，不是测试环境或数据准备问题。

### 2.2 OA 主路径桥接 RED

命令：

```bash
mvn -Dtest=Batch2CoreChainContractTests#unifiedApprovalEntryDefaultsToOaBridgeAndHandlesCallbacksIdempotencyOrderingSummaryAndCompensation test
```

失败证据：

```text
Status expected:<202> but was:<404>
No static resource api/contracts/{contract_id}/approvals.
```

结论：失败原因同样明确指向统一审批入口缺失，证明 `OA` 主路径也尚未通过合同侧统一入口承接。

## 三、GREEN 验证结果

### 3.1 新增审批回写测试

命令：

```bash
mvn -Dtest=Batch2CoreChainContractTests#unifiedApprovalEntryCanUsePlatformTakeoverAndWriteBackApprovedStatusAndTimeline test
mvn -Dtest=Batch2CoreChainContractTests#unifiedApprovalEntryDefaultsToOaBridgeAndHandlesCallbacksIdempotencyOrderingSummaryAndCompensation test
```

结果：

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 3.2 第二批核心链路回归

命令：

```bash
mvn -Dtest=Batch2CoreChainContractTests test
```

结果：

```text
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 3.3 后端完整测试

命令：

```bash
mvn test
```

结果：

```text
Tests run: 93, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 四、实现结论

- 已新增合同侧统一审批入口 `POST /api/contracts/{contract_id}/approvals`，合同侧只发起统一审批请求。
- 已实现流程侧按承接策略区分 `OA` 默认主路径与 `CMP_WORKFLOW` 平台承接路径。
- 已实现平台承接结果回写入口 `POST /api/workflow-engine/approvals/{process_id}/results`。
- 已实现 `OA` 回调入口 `POST /api/workflow-engine/oa/callbacks`，覆盖重复回调幂等忽略、乱序回调忽略和终态回写。
- 已实现统一审批摘要查询 `GET /api/workflow-engine/approvals/{process_id}/summary`。
- 已实现回写失败时生成补偿任务，并通过 `GET /api/workflow-engine/compensation-tasks` 查询。
- 合同详情与台账继续读取合同主档上的统一 `approval_summary`，未按 `OA` 或平台路径拆出两套业务读逻辑。

## 五、范围边界

- 当前实现使用第二批既有内存态核心链路控制器完成最小闭环，未引入数据库表结构或外部 `OA` SDK。
- `OA` 回调可信性、签名、映射模板版本化和真实外部调用仍属于后续生产化接入深水区，不在 Task 6 最小代码范围内扩展。
- 旧 `/api/workflow-engine/processes` 入口保留既有 `STARTED` 语义，新的合同统一审批入口默认走 `OA` 主路径。
- 未执行 Task 7 的最小端到端闭环验证报告。

## 六、遗留问题

没有问题。

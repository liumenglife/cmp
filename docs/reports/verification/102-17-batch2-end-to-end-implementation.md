# 第二批最小端到端闭环实现验证报告

## 一、验证结论

第二批最小端到端闭环验证已完成，结论为通过。

本次验证覆盖合同样例、正文文件样例、附件样例、组织人员样例、审批流程样例与 `OA` 回调样例，并跑通“创建合同 -> 编辑合同 -> 上传正文 -> 挂接附件 -> 发起审批 -> 审批通过 -> 合同状态回写 -> 合同详情查看”。

关键异常闭环已覆盖：文档写入失败、文档版本失效、审批驳回、审批回调重复、审批状态回写失败、组织节点解析失败。

## 二、RED 失败证据

### 2.1 主链路 RED

命令：

```bash
mvn -Dtest=Batch2CoreChainEndToEndTests test
```

结果：失败。

关键失败证据：

```text
Failures: 
  Batch2CoreChainEndToEndTests.runsMinimumContractDocumentApprovalDetailChainWithBusinessSamples:72 No value at JSON path "$.approval_summary.process_version_id"
```

失败含义：审批发起已经创建 `OA` 审批实例，但统一审批摘要未保留本次端到端样例实际消费的流程版本证据，无法作为第二批最小闭环验证证据。

### 2.2 异常闭环 RED

命令：

```bash
mvn -Dtest=Batch2CoreChainEndToEndTests test
```

结果：失败。

关键失败证据：

```text
Failures: 
  Batch2CoreChainEndToEndTests.closesKeyExceptionLoopsForDocumentApprovalCallbackWritebackAndOrganizationResolution:115 Status expected:<503> but was:<201>
  Batch2CoreChainEndToEndTests.runsMinimumContractDocumentApprovalDetailChainWithBusinessSamples:72 No value at JSON path "$.approval_summary.process_version_id"
```

失败含义：文档写入失败样例尚未形成失败响应闭环，仍被当作成功写入；主链路审批流程版本证据仍缺失。

## 三、GREEN 验证结果

### 3.1 新增端到端与异常闭环测试

命令：

```bash
mvn -Dtest=Batch2CoreChainEndToEndTests test
```

结果：通过。

关键结果：

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 3.2 第二批既有核心链路回归

命令：

```bash
mvn -Dtest=Batch2CoreChainContractTests test
```

结果：通过。

关键结果：

```text
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 3.3 后端完整测试回归

命令：

```bash
mvn test
```

结果：通过。

关键结果：

```text
Tests run: 95, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 四、关键接口响应与记录证据

本次测试通过 `MockMvc` 使用内存 `H2` 测试数据库，接口响应和数据库迁移日志均在测试输出中体现。

关键响应证据：

1. 合同创建响应包含 `contract_id`、`contract_no`、`contract_status=DRAFT`、责任组织、责任人与 `CONTRACT_CREATED` 审计事件。
2. 合同编辑响应将合同名称更新为“第二批端到端样例合同-已编辑”。
3. 正文文件写入响应包含 `document_asset_id`、`document_version_id`、`document_role=MAIN_BODY`。
4. 附件写入响应包含 `document_role=ATTACHMENT`，合同详情返回附件摘要。
5. 审批发起响应包含 `approval_mode=OA`、`oa_instance_id`、`approval_summary.process_version_id`。
6. `OA` 审批通过回调响应包含 `callback_result=ACCEPTED`、`final_result=APPROVED`。
7. 合同详情响应包含 `contract_status=APPROVED`、正文摘要、附件摘要、审批摘要和 `APPROVAL_APPROVED` 时间线事件。
8. 文档写入失败响应返回 `503`、`error_code=DOCUMENT_WRITE_FAILED`、`recovery_action=RETRY_DOCUMENT_WRITE`。
9. 文档版本失效响应返回 `409`、`error_code=DOCUMENT_VERSION_STALE`、`current_document_version_id`。
10. 审批驳回回调后合同详情返回 `contract_status=REJECTED` 与 `APPROVAL_REJECTED` 时间线事件。
11. 重复 `OA` 回调返回 `callback_result=DUPLICATE_IGNORED`。
12. 审批状态回写失败返回 `callback_result=WRITEBACK_COMPENSATING`，补偿任务列表包含 `CONTRACT_APPROVAL_WRITEBACK`。
13. 组织节点解析失败返回 `422`、`error_code=ORG_NODE_RESOLUTION_FAILED` 与失败节点 `broken-org-rule`。

数据库关键记录证据：

1. 测试启动时 `Flyway` 成功应用 `7` 个迁移，日志显示 `Successfully applied 7 migrations to schema "PUBLIC", now at version v7`。
2. 后端完整测试中所有测试均基于自动创建的 `H2` 测试数据库执行，未依赖外部数据库状态。

## 五、实现覆盖

新增测试文件：`backend/src/test/java/com/cmp/platform/corechain/Batch2CoreChainEndToEndTests.java`。

最小生产补充：`backend/src/main/java/com/cmp/platform/corechain/CoreChainController.java`。

覆盖能力：

1. 端到端审批摘要保留 `process_version_id` 证据。
2. 文档写入失败返回明确失败响应，不污染合同当前正文引用。
3. 审批发起使用过期文档版本时返回版本失效冲突。
4. `OA` 回调后继续保留审批流程版本证据。
5. 组织规则绑定解析失败时阻止流程发布并返回明确错误。

## 六、范围边界

本次只执行第二批计划中的 Task 7，不扩展第三批能力。

本次未实现真实文件存储、真实 `OA` 外部系统、真实组织规则引擎、真实补偿任务重试执行器；这些仍属于后续生产化或后续批次范围。

## 七、遗留问题

没有问题。

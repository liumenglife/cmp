# 合同主档与编辑基础能力实现验证报告

## 一、验证范围

- 范围：第二批计划 `Task 2: contract-core 合同主档与编辑基础能力`。
- 覆盖接口：`POST /api/contracts`、`GET /api/contracts/{contract_id}/master`、`PATCH /api/contracts/{contract_id}`、`GET /api/contracts`、`GET /api/contracts/{contract_id}`。
- 覆盖能力：合同主档创建、唯一 `contract_id`、合同编号、初始状态、责任组织、责任人、审计记录、草稿编辑、权限拒绝、非草稿状态拒绝、合同台账、详情聚合读取主档、当前文档摘要、审批摘要和时间线摘要。

## 二、RED 失败证据

1. 合同主档创建测试先失败。
   - 命令：`mvn -Dtest=Batch2CoreChainContractTests#createsContractMasterWithBusinessIdentityOwnershipAndAuditRecord test`
   - 结果：失败。
   - 失败原因：响应缺少 `$.contract_no`。
   - 关键证据：`No value at JSON path "$.contract_no"`。

2. 合同基础编辑测试先失败。
   - 命令：`mvn -Dtest=Batch2CoreChainContractTests#editsOnlyDraftContractWhenRequesterHasEditPermission test`
   - 结果：失败。
   - 失败原因：`PATCH /api/contracts/{contract_id}` 未实现，返回 `404`，不满足缺少编辑权限时返回 `403` 的期望。
   - 关键证据：`Status expected:<403> but was:<404>`。

3. 合同台账与详情聚合测试先失败。
   - 命令：`mvn -Dtest=Batch2CoreChainContractTests#ledgerAndDetailAggregateMasterCurrentDocumentApprovalAndTimelineSummaries test`
   - 结果：失败。
   - 失败原因：`GET /api/contracts` 未实现，返回 `405`，不满足台账查询期望。
   - 关键证据：`Status expected:<200> but was:<405>`。

说明：首次尝试使用 `./mvnw` 运行测试失败，原因为仓库未提供 `mvnw`，该次未作为 RED 行为证据；后续均改用项目实际可用的 `mvn`。

## 三、GREEN 验证结果

1. 合同核心测试类通过。
   - 命令：`mvn -Dtest=Batch2CoreChainContractTests test`
   - 结果：`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`。

2. 后端完整回归通过。
   - 命令：`mvn test`
   - 结果：`Tests run: 84, Failures: 0, Errors: 0, Skipped: 0`。

## 四、实现边界

- 合同主档仍是当前合同业务真相源。
- 台账和详情只组合合同主档、当前文档摘要、审批摘要和时间线摘要，没有落成第二份合同主档。
- 文档能力仅沿用当前已有的文档引用摘要，不实现完整 `document-center` 版本链。
- 审批能力仅沿用当前已有的流程启动与结果回写摘要，不实现完整 `workflow-engine` 运行时或后续审批闭环。
- 编辑约束已覆盖最小必要边界：缺少 `CONTRACT_EDIT` 权限拒绝，非 `DRAFT` 状态拒绝。

## 五、遗留问题

- 当前实现仍为内存态最小闭环，尚未落到 `cc_contract_master`、`cc_contract_summary`、`cc_contract_ledger_projection` 等正式持久化表。
- 合同编号当前由 UUID 片段生成，尚未接入企业正式编号规则或号段治理。
- 权限判断当前通过 `X-CMP-Permissions` 最小模拟，尚未接入统一身份与权限网关的真实授权判定。
- 审计记录当前随合同内存状态返回，尚未接入统一审计中心持久化。

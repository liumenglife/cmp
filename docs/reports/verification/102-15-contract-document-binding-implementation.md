# 第二批合同文档挂接闭环实现验证报告

## 一、验证结论

- 状态：通过。
- 范围：仅覆盖第二批 Task 5 合同文档挂接闭环。
- 结论：合同创建后可上传主正文，文档中心建立文件对象与首版版本；合同侧保存稳定文档引用、当前摘要与业务有效版本；同一合同可挂接多个附件，合同详情可读取附件摘要；文档版本链仍由文档中心持有，合同侧不复制完整版本链。

## 二、RED 失败证据

1. 主正文入库测试先失败。
   - 命令：`mvn -Dtest=Batch2CoreChainContractTests#uploadingMainBodyCreatesDocumentFirstVersionAndRefreshesContractDocumentSummary test`
   - 失败原因：`No value at JSON path "$.current_document.effective_document_version_id"`
   - 判断：测试环境启动正常，失败指向合同侧尚未回写业务有效版本摘要。

2. 附件挂接测试先失败。
   - 命令：`mvn -Dtest=Batch2CoreChainContractTests#attachingMultipleDocumentsKeepsVersionTruthInDocumentCenterAndExposesAttachmentSummariesInContractDetail test`
   - 失败原因：`$.document_summary.document_asset_id` 期望主正文文档对象，实际被最后一个附件文档对象覆盖。
   - 判断：失败指向正文与附件角色未分离，附件上传错误覆盖合同当前主正文摘要。

3. 主版本切换测试先失败。
   - 命令：`mvn -Dtest=Batch2CoreChainContractTests#switchingMainBodyVersionRefreshesContractSummaryAndTimelineWithoutCopyingVersionChain test`
   - 失败原因：`No matching value at JSON path "$.timeline_event[?(@.event_type == 'DOCUMENT_MAIN_VERSION_SWITCHED')].object_id"`
   - 判断：合同当前文档摘要已可刷新，但缺少合同侧主正文业务有效版本切换事件。

## 三、GREEN 验证结果

1. 第二批合同核心测试类通过。
   - 命令：`mvn -Dtest=Batch2CoreChainContractTests test`
   - 结果：`Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`。

2. 后端完整测试通过。
   - 命令：`mvn test`
   - 结果：`Tests run: 91, Failures: 0, Errors: 0, Skipped: 0`。

## 四、实现覆盖

- 主正文上传后，文档中心返回 `document_asset_id`、`document_version_id`、`current_version_id`、`latest_version_no` 与审计记录。
- 合同主档 `current_document` 保存稳定引用、`effective_document_version_id`、`document_role`、`document_title`、`latest_version_no` 与当前状态摘要。
- 附件挂接写入合同详情 `attachment_summaries`，不覆盖 `document_summary` / `current_document` 的主正文引用。
- 文档版本追加与主版本激活后，合同侧刷新当前业务有效版本摘要，并记录 `DOCUMENT_MAIN_VERSION_SWITCHED` 时间线事件。
- 合同侧响应不输出 `version_chain`，完整版本链继续通过文档中心版本查询接口读取。

## 五、范围边界

- 未推进 Task 6 审批统一入口、`OA` 主路径桥接、审批结果状态回写增强与补偿能力。
- 未引入真实对象存储、加密、预览、搜索、签章或归档能力。
- 当前实现仍沿用本批次既有内存态最小实现，用于验证第二批核心主链路契约。

## 六、遗留问题

没有问题。

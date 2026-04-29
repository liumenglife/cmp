# 第二批文档中心文档主档与版本链基础能力实现验证报告

## 1. 验证范围

本报告覆盖第二批计划中的 `Task 3: document-center 文档主档与版本链基础能力`。

已验证能力：

- 合同正文与附件写入文档中心后生成 `document_asset_id`、`current_version_id`、业务绑定摘要和审计记录。
- `POST /api/document-center/assets`、`GET /api/document-center/assets/{document_asset_id}`、按 `owner_type=CONTRACT` / `owner_id=contract_id` 查询文档列表。
- 追加版本、版本列表、版本详情、激活指定版本。
- 同一文档同一时刻只有一个当前主版本，版本激活后文档主档 `current_version_id` 与合同绑定引用同步刷新。

未纳入本次范围：

- 未实现预览、红线、批注、加密、`OCR`、签章、归档完整业务。
- 未推进第二批 `Task 4` 及后续审批流能力。
- 未把文件版本链复制到合同主档；合同侧仅保留当前文档引用。

## 2. RED 失败证据

### 2.1 文档主档写入测试

先新增测试：`documentCenterCreatesContractOwnedMainBodyAndAttachmentAssetsWithBindingAndAudit`。

运行命令：

```bash
mvn -Dtest=Batch2CoreChainContractTests#documentCenterCreatesContractOwnedMainBodyAndAttachmentAssetsWithBindingAndAudit test
```

失败证据：

```text
java.lang.AssertionError: No value at JSON path "$.current_version_id"
Caused by: com.jayway.jsonpath.PathNotFoundException: No results for path: $['current_version_id']
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

失败原因明确：现有文档创建响应只有 `document_version_id`，没有 `current_version_id`，也没有文档资产详情和按合同查询能力。

### 2.2 版本链测试

先新增测试：`documentCenterAppendsQueriesAndActivatesVersionsWithSingleCurrentVersion`。

运行命令：

```bash
mvn -Dtest=Batch2CoreChainContractTests#documentCenterAppendsQueriesAndActivatesVersionsWithSingleCurrentVersion test
```

失败证据：

```text
Status expected:<201> but was:<404>
No static resource api/document-center/assets/{document_asset_id}/versions.
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

失败原因明确：版本追加接口尚不存在，测试命中缺少生产接口实现。

## 3. GREEN 验证结果

文档主档最小实现后运行：

```bash
mvn -Dtest=Batch2CoreChainContractTests#documentCenterCreatesContractOwnedMainBodyAndAttachmentAssetsWithBindingAndAudit test
```

结果：

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

版本链最小实现后运行：

```bash
mvn -Dtest=Batch2CoreChainContractTests#documentCenterAppendsQueriesAndActivatesVersionsWithSingleCurrentVersion test
```

结果：

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

第二批核心链路测试回归：

```bash
mvn -Dtest=Batch2CoreChainContractTests test
```

结果：

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

完整后端测试回归：

```bash
mvn test
```

结果：

```text
Tests run: 86, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 4. 范围边界

- 本次实现采用当前核心链路控制器内的内存状态，服务于第二批主链路契约测试，不提前扩展为完整数据库持久化设计。
- 文档中心作为文件真相源持有资产与版本链；合同主档只读取当前文档引用，不保存完整版本列表。
- 版本状态通过文档主档 `current_version_id` 计算，保证对外列表中同一文档只有一个 `is_current_version=true`。
- 版本激活后刷新合同当前文档引用，以满足后续合同详情、审批输入稿读取当前主版本的基础挂点。

## 5. 遗留问题

没有问题。

# 文档中心与文档协作子模块 API Design

## 1. 文档说明

本文档是 `CMP` 文档中心与文档协作子模块的第一份正式
`API Design`，用于在
[`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)、
[`总平台 Architecture Design`](../../architecture-design.md)、
[`总平台 API Design`](../../api-design.md)、
[`总平台 Detailed Design`](../../detailed-design.md)、
[`文档中心与文档协作子模块 Architecture Design`](./architecture-design.md)
的约束下，固化本子模块对外可见的资源边界、请求/响应契约、鉴权约束、
错误码复用策略、异步任务与回调边界。

本文档输出为：

- 本文：[`API Design`](./api-design.md)
- 下游实现约束：后续文档中心与文档协作子模块 `Detailed Design`
- 下游接口定稿：正式 `OpenAPI` / 接口清单

本文只描述接口契约，不展开以下内容：

- 不复述需求范围、建设目标、技术选型理由
- 不写物理表结构、对象存储目录规划、索引结构
- 不写预览转换协议、`OCR` 参数、签章坐标协议、加密算法细节
- 不写任务拆解、实施排期、负责人安排

## 2. API 边界

### 2.1 模块边界总览

- 文档中心是平台唯一文件对象真相源，对外暴露文档主档、版本、预览、
  绑定视图、下载授权与审计查询等接口。
- 文档协作建立在文档中心之上，对外暴露批注、修订、协作视图与协作动作接口。
- 合同主档、审批实例、签章申请、归档记录的一级业务真相不属于本模块；
  本模块只提供这些业务对象所依赖的文件对象与协作能力契约。
- `OCR`、搜索、签章、加密、归档与本模块的关系是“能力挂接”，
  不是把这些能力的内部实现写成本模块 API。

### 2.2 文档主档接口

资源定位：`DocumentAsset` 是文件对象一级资源，承载业务绑定、文档类型、
当前主版本引用、受控访问摘要。

#### `POST /api/document-center/assets`

用途：创建文档主档并接收首个文件版本，作为正文、附件、签章稿、归档稿等
正式文件对象的统一写入入口。

请求重点字段：

- `owner_type`：绑定对象类型，首批至少支持 `CONTRACT`
- `owner_id`：绑定对象主键，如 `contract_id`
- `document_role`：`MAIN_BODY` / `ATTACHMENT` / `SIGNATURE_COPY` /
  `ARCHIVE_COPY` / `SUPPLEMENT`
- `document_title`
- `source_channel`：`MANUAL_UPLOAD` / `SYSTEM_GENERATED` /
  `EXTERNAL_IMPORT`
- `file_upload_token` 或等价文件引用句柄
- `encryption_required`：是否按平台规则进入受控加密链路

响应重点字段：

- `document_asset_id`
- `current_version_id`
- `document_status`
- `encryption_status`
- `binding_summary`

#### `GET /api/document-center/assets/{document_asset_id}`

用途：查询文档主档详情。

响应重点字段：

- `document_asset_id`
- `owner_type`、`owner_id`
- `document_role`
- `document_title`
- `current_version_id`
- `latest_version_no`
- `preview_status`
- `encryption_status`
- `collaboration_summary`

#### `GET /api/document-center/assets`

用途：按业务对象、文档角色、状态、关键字查询文档主档列表。

查询参数重点：

- `owner_type`、`owner_id`
- `document_role`
- `document_status`
- `keyword`
- `page`、`page_size`

#### `PATCH /api/document-center/assets/{document_asset_id}`

用途：更新文档主档元数据，不直接替代版本内容。

允许变更范围：

- `document_title`
- `document_role`
- `document_status`
- `business_tags`

### 2.3 版本接口

资源定位：`DocumentVersion` 是 `DocumentAsset` 下的版本资源，承载每次文件内容
追加后的稳定版本视图。

#### `POST /api/document-center/assets/{document_asset_id}/versions`

用途：向既有文档主档追加新版本。

请求重点字段：

- `base_version_id`：修订或替换时引用的基线版本
- `change_reason`
- `version_label`
- `file_upload_token`
- `source_channel`

响应重点字段：

- `document_version_id`
- `version_no`
- `version_status`
- `is_current_version`
- `preview_generation_status`

#### `GET /api/document-center/assets/{document_asset_id}/versions`

用途：查询某文档主档的版本链摘要。

#### `GET /api/document-center/versions/{document_version_id}`

用途：查询单个版本详情。

#### `POST /api/document-center/versions/{document_version_id}/activate`

用途：将某版本切换为当前主版本。

边界说明：

- 是否允许切主版本由上游业务状态与权限共同约束
- 本接口只表达版本切换动作，不在本层展开切换后的搜索重建、审批引用刷新、
  协作锚点迁移细节

### 2.4 预览接口

资源定位：`PreviewArtifact` 是针对某一 `DocumentVersion` 生成的预览产物资源，
只代表受控视图，不代表新的文件真相。

#### `POST /api/document-center/versions/{document_version_id}/preview-artifacts`

用途：为指定版本创建预览产物生成任务。

响应重点字段：

- `preview_artifact_id`
- `job_id`
- `preview_status`

#### `GET /api/document-center/versions/{document_version_id}/preview-artifacts`

用途：查询某版本可用的预览产物列表。

#### `GET /api/document-center/preview-artifacts/{preview_artifact_id}`

用途：查询单个预览产物详情与受控访问地址。

响应重点字段：

- `preview_artifact_id`
- `document_version_id`
- `preview_type`
- `preview_status`
- `preview_url`
- `expires_at`

### 2.5 批注 / 修订接口

资源定位：

- `Annotation`：锚定在特定版本视图上的批注资源
- `Revision`：围绕版本替换、修订建议、差异采纳形成的协作资源

#### `POST /api/document-center/versions/{document_version_id}/annotations`

用途：在指定版本上创建批注。

请求重点字段：

- `anchor_ref`：锚点引用句柄
- `annotation_type`：`COMMENT` / `QUESTION` / `RISK` / `TODO`
- `content`
- `mentioned_user_id_list`

#### `GET /api/document-center/versions/{document_version_id}/annotations`

用途：查询某版本的批注列表。

查询参数重点：

- `annotation_status`
- `annotation_type`
- `created_by`

#### `PATCH /api/document-center/annotations/{annotation_id}`

用途：更新批注状态、内容或处理结果。

#### `POST /api/document-center/assets/{document_asset_id}/revisions`

用途：创建修订记录，表达“基于哪个版本、产生了哪个候选版本、当前修订状态如何”。

请求重点字段：

- `base_version_id`
- `candidate_version_id`
- `revision_type`：`REPLACE` / `REDLINE` / `COMPARE_ONLY`
- `revision_note`

#### `GET /api/document-center/revisions/{revision_id}`

用途：查询修订详情。

#### `POST /api/document-center/revisions/{revision_id}/accept`

用途：采纳修订结果并推进版本链。

#### `POST /api/document-center/revisions/{revision_id}/reject`

用途：拒绝修订结果。

### 2.6 文档协作接口

文档协作接口只建立在文档主档和版本链之上，不创建第二套文件资源。

#### `GET /api/document-center/assets/{document_asset_id}/summary-view`

用途：返回文档摘要视图，供合同详情、审批摘要、归档准备页复用。

响应重点字段：

- `document_asset_id`
- `document_title`
- `document_role`
- `current_version_id`
- `annotation_open_count`
- `revision_open_count`
- `preview_status`
- `encryption_status`

#### `GET /api/document-center/assets/{document_asset_id}/binding-view`

用途：返回文档绑定视图，说明文档当前绑定到哪个业务对象、哪一业务阶段。

响应重点字段：

- `owner_type`、`owner_id`
- `binding_status`
- `linked_contract_status`
- `linked_signature_status`
- `linked_archive_status`

#### `GET /api/document-center/assets/{document_asset_id}/collaboration-view`

用途：返回协作工作区聚合视图。

聚合范围：

- 当前版本摘要
- 批注统计
- 打开中的修订
- 最近协作活动

### 2.7 `OCR` / 搜索 / 签章 / 加密 / 归档挂接接口边界

这些接口用于表达能力挂接边界，不展开各能力内部参数或算法细节。

#### `POST /api/internal/document-center/ocr-jobs`

用途：基于受控文档版本创建 `OCR` 作业。

请求重点字段：

- `document_version_id`
- `job_purpose`：`TEXT_EXTRACTION` / `FIELD_ASSIST`

#### `POST /api/internal/document-center/search-index-jobs`

用途：基于文档版本创建索引构建或刷新作业。

#### `POST /api/internal/document-center/signature-inputs`

用途：向签章模块声明某版本可作为签章输入稿。

#### `POST /api/internal/document-center/archive-inputs`

用途：向归档模块声明某版本可作为归档输入稿或归档封包来源。

#### `POST /api/internal/document-center/encryption/check-in`

用途：文档写入时触发入库加密校验与自动加密。

#### `POST /api/internal/document-center/encryption/decrypt-access`

用途：平台内受控读取时申请自动解密访问。

边界说明：

- `OCR`、搜索、签章、归档、加密都消费 `DocumentVersion`，不直接消费业务模块私有附件
- 这些挂接接口只承载“哪个文档版本可被消费、返回什么作业句柄或接收结果摘要”
- `OCR` 参数、索引字段、签章定位、加密算法、归档封包字段不在本文展开

### 2.8 解密下载授权接口与审计接口

资源定位：

- `DownloadAuthorization`：管理端授予的“解密下载”授权规则
- `DecryptDownloadJob`：执行一次受控解密下载形成的任务资源

#### `POST /api/internal/document-center/encryption/download-authorizations`

用途：管理端配置某合同或合同范围的“解密下载”授权规则，支持按部门、人员授权。

请求重点字段：

- `scope_type`：`CONTRACT` / `CONTRACT_RANGE`
- `contract_id`：当 `scope_type=CONTRACT` 时必填
- `scope_filter`：当 `scope_type=CONTRACT_RANGE` 时用于表达合同范围条件，如分类、归属部门、密级或业务域
- `department_id_list`：获授权部门列表
- `user_id_list`：获授权人员列表
- `authorization_status`：`ENABLED` / `DISABLED`
- `effective_start`
- `effective_end`
- `reason`

约束：

- 仅管理端可调用
- 授权对象只在 API 可见层表达为部门、人员，不展开内部权限模型
- 授权生效后，仅放开“解密下载”这一受控例外，不放开其他业务越权动作

#### `GET /api/internal/document-center/encryption/download-authorizations`

用途：管理端查询某合同或合同范围的“解密下载”授权配置。

#### `POST /api/internal/document-center/encryption/decrypt-downloads`

用途：获授权部门人员触发解密下载，生成受控下载任务或下载结果；导出的明文文件可脱离 `CMP` 使用。

请求重点字段：

- `contract_id`
- `document_id` / `attachment_id`
- `reason`

响应重点字段：

- `job_id`
- `job_status`
- `audit_event_id`

#### `GET /api/internal/document-center/encryption/decrypt-downloads/{job_id}`

用途：查询解密下载任务状态、失败原因、下载结果。

#### `GET /api/internal/document-center/encryption/jobs/{job_id}`

用途：查询内部加密作业状态、失败原因与审计结果；仅用于平台内异步任务查询，不作为外部回调接口。

#### `GET /api/internal/document-center/encryption/audit-logs`

用途：按合同、文档、部门、人员、动作类型查询加密访问、授权变更、解密下载等审计日志。

## 3. 核心资源划分

### 3.1 `DocumentAsset`

- 资源职责：文件对象主档、业务绑定、角色归属、当前主版本引用
- 典型上游引用：合同主档、审批详情、签章输入、归档准备
- 不承载内容：版本内部差异、预览转换实现、协作状态机细节

### 3.2 `DocumentVersion`

- 资源职责：文件内容版本、版本号、来源、当前状态、预览与挂接能力的输入基线
- 与 `DocumentAsset` 关系：一个 `DocumentAsset` 对应多个 `DocumentVersion`

### 3.3 `PreviewArtifact`

- 资源职责：指定版本对应的受控预览产物
- 边界约束：预览产物是派生视图，不是新的文件真相源

### 3.4 `Annotation`

- 资源职责：版本上的批注、评论、风险提示、处理结论
- 边界约束：必须锚定到受控版本视图，不能脱离版本链存在

### 3.5 `Revision`

- 资源职责：表达基线版本、候选版本、修订关系与采纳结果
- 边界约束：修订资源推进版本链，但不替代版本资源本身

### 3.6 `DecryptDownloadAuthorization`

- 资源职责：管理端授予的解密下载授权规则
- 作用范围：合同或合同范围
- 授权对象：部门、人员

### 3.7 `DecryptDownloadJob`

- 资源职责：一次解密下载动作的受理、执行、结果与失败原因
- 边界约束：导出文件可脱离 `CMP` 使用，但任务过程必须纳入审计留痕

### 3.8 文档摘要 / 绑定视图

- `DocumentSummaryView`：面向合同详情、审批、归档准备等页面的聚合只读视图
- `DocumentBindingView`：面向业务对象绑定关系核对的聚合只读视图
- 这两类视图属于 API 聚合资源，不单独代表新的主数据真相

## 4. 统一约定

### 4.1 协议

- 继承 [`总平台 API Design`](../../api-design.md) 的统一约定
- 协议：`HTTPS + JSON`
- 编码：`UTF-8`
- 时间格式：`ISO 8601`
- 分页：`page`、`page_size`
- 成功响应、失败响应、分页响应结构沿用总平台统一包裹格式

### 4.2 鉴权

- 用户侧接口基于平台统一登录态与权限校验
- 内部挂接接口使用平台内部服务鉴权，不对外暴露为外围系统开放接口
- 预览、版本读取、协作、解密下载均需同时校验：
  `登录身份 + 业务对象访问权限 + 文档访问权限 + 敏感动作权限`
- 组织、部门、人员只保留 API 可见约束，不在本文展开权限模型内部实现

### 4.3 幂等

- 创建型和动作型写接口支持 `Idempotency-Key`
- 同一 `Idempotency-Key` 对应不同请求体时，返回总平台定义的
  `40905 IDEMPOTENCY_CONFLICT`
- 以下接口应强制幂等：
  - 创建文档主档
  - 追加版本
  - 创建预览生成任务
  - 创建 `OCR` / 搜索索引 / 解密下载作业
  - 创建或更新解密下载授权

### 4.4 命名规范

- 路径资源段统一使用 `kebab-case`；路径参数、请求字段、响应字段、查询参数统一使用 `snake_case`
- 主键命名统一使用 `<resource>_id`
- 状态字段统一使用 `<domain>_status`
- 模块级命名规范完全继承 [`总平台 API Design`](../../api-design.md)

### 4.5 错误码复用策略

- 参数校验、鉴权失败、权限不足、幂等冲突、异步拥塞等通用场景，优先复用总平台
  已定义错误码，如 `40001`、`40002`、`40101`、`40301`、`40905`、`50301`
- 文档中心内部加密校验失败，复用总平台 `42202 ENCRYPTION_CHECK_FAILED`
- `OCR` 任务失败，复用总平台 `42206 OCR_TASK_FAILED`
- 模块特有的资源不存在、版本切换冲突、批注锚点失效、解密授权不足等错误，
  由本模块在正式 `OpenAPI` 中补齐明细码，但不得重定义总平台已有通用码语义

## 5. 与合同主档的绑定接口边界

- 合同主档是业务真相，文档中心是文件真相；两者通过绑定接口而不是互相接管
- 文档创建时，`owner_type=CONTRACT`、`owner_id=contract_id` 用于建立稳定绑定
- 合同详情、审批、签章、归档读取的是文档中心提供的摘要视图、绑定视图、
  当前版本引用与受控访问入口
- 本模块不负责创建或修改合同一级业务字段，不提供“直接改合同状态”的接口
- 当文档版本切换影响合同详情呈现时，只回写引用关系或触发异步刷新，
  不把合同主档迁移到本模块管理

## 6. 与 `OCR` / 搜索 / 签章 / 加密 / 归档的接口边界

- `OCR`：消费指定 `DocumentVersion`，返回识别结果摘要或作业状态；
  结果不覆盖原文件真相
- 搜索：消费指定 `DocumentVersion` 创建或刷新索引；索引是读模型，不是文件真相
- 签章：消费指定版本作为签章输入稿；签章完成后形成新的版本或版本引用，
  但签章业务真相仍归签章模块
- 加密：挂在文档写入 / 读取链路上，负责入库校验、自动加密、平台内受控解密、
  解密下载授权与审计
- 归档：消费指定版本作为归档输入稿；归档记录真相仍归归档模块，
  文档中心只承载归档输入与归档产物引用

## 7. 加密授权下载边界

- 默认情况下，密文文档不可脱离平台使用
- 管理端可以按部门、人员授予“解密下载”权限
- “解密下载”是受控例外，不等于关闭文档平台内受控访问约束
- 获授权对象发起解密下载后，导出的明文文件可脱离 `CMP` 使用
- 每次授权变更、下载申请、下载完成、下载失败、结果领取都必须有审计留痕
- 本文只定义授权与下载任务的 API 契约，不展开授权审批流、明文保存策略、
  水印策略、密钥管理和算法实现

## 8. 异步与回调边界

### 8.1 异步任务边界

以下场景统一按异步任务受理，创建成功返回 `202` 与任务主键：

- 预览产物生成
- `OCR` 作业
- 搜索索引构建 / 刷新
- 解密下载
- 大文件版本导入或批量文档写入

异步结果查询统一返回：

- `job_id`
- `job_type`
- `job_status`
- `result_code`
- `result_message`
- `started_at`
- `completed_at`

### 8.2 回调边界

对需要能力提供方异步回传结果的场景，模块保留统一回调受理边界：

#### `POST /api/internal/document-center/capability-jobs/{job_id}/callbacks`

用途：受理预览、`OCR`、搜索索引、签章回收、归档回收等能力作业的结果回调。

请求重点字段：

- `capability_type`：`PREVIEW` / `OCR` / `SEARCH_INDEX` /
  `SIGNATURE_RECOVERY` / `ARCHIVE_RECOVERY` / `ENCRYPTION`
- `job_status`
- `result_code`
- `result_message`
- `output_resource_list`
- `completed_at`

统一约束：

- 回调接口只定义结果交付契约，不定义能力侧内部处理过程
- 回调必须支持签名、时间戳与重放校验，复用总平台回调约束
- 回调成功不代表业务主链全部完成，只代表本模块已接收并可继续推进后续状态更新

## 9. 需要下沉到该模块 Detailed Design 的内容边界

以下内容不继续留在本 `API Design`，应下沉到后续模块级 `Detailed Design`：

- `DocumentAsset`、`DocumentVersion`、`PreviewArtifact`、`Annotation`、`Revision`
  的内部模型与状态机
- 版本切换后的锚点迁移、引用刷新、补偿策略
- 预览生成编排、失败重试、产物清理规则
- `OCR`、搜索、签章、归档挂接的内部任务编排与恢复细节
- 解密下载授权存储、任务编排、结果介质管理、审计落库细节
- 文件上传句柄、对象存储映射、下载流控、水印与明文缓存策略
- 权限模型内部实现、组织树求值、部门人员命中规则

## 10. 本文结论

本文将文档中心收口为 `CMP` 的文件对象真相源，将文档协作收口为建立在其上的
协作能力层，并以模块级接口契约形式明确了文档主档、版本、预览、批注、修订、
协作聚合视图、能力挂接、解密下载授权与审计查询的稳定边界。

本文与 [`总平台 API Design`](../../api-design.md) 的统一命名、响应、错误码、
幂等和异步规则保持一致；与
[`文档中心与文档协作子模块 Architecture Design`](./architecture-design.md)
保持一致，不把对象存储、预览转换、签章坐标、`OCR` 参数、加密算法等内部实现
提前写入接口契约层。

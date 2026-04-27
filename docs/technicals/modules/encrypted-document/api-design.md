# 加密文档子模块 API Design

## 1. 文档说明

本文档是 `CMP` 加密文档子模块的第一份正式 `API Design`。
本文基于以下上游文档，定义该子模块在平台内的接口边界、
资源契约、鉴权约束、错误码复用策略，以及与相关模块的交互边界。

### 1.1 输入

- 需求基线：[`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- 总平台架构：[`Architecture Design`](../../architecture-design.md)
- 总平台接口规范：[`API Design`](../../api-design.md)
- 总平台详细设计边界：[`Detailed Design`](../../detailed-design.md)
- 子模块架构：[`Architecture Design`](./architecture-design.md)

### 1.2 输出

- 本文：[`API Design`](./api-design.md)
- 为后续子模块 [`Detailed Design`](./detailed-design.md) 预留下沉边界

### 1.3 阅读边界

本文只描述子模块级 API 可见契约，不展开以下内容：

- 不复述需求范围、技术选型理由或项目排期
- 不展开物理表结构、索引、缓存键、任务主题与内部状态机
- 不展开加密算法、密钥存储、介质形态与明文导出文件封装实现
- 不展开页面交互、审批编排细节、审计报表实现与运维部署细节

## 2. API 边界

### 2.1 子模块接口范围

本子模块只对以下资源边界负责：

- 文档写入自动加密接口边界
- 平台内受控解密访问接口边界
- 解密下载任务接口边界
- 内部加密作业查询接口边界
- 审计查询接口边界

本子模块不负责以下接口边界：

- 不定义文档中心文件主记录、版本链、预览转换的完整接口
- 不定义合同主档创建、审批、签章、归档、搜索、AI 的主业务接口
- 不定义“解密下载”授权配置、授权查询与命中判定接口
- 不定义外部系统本体改造接口
- 不定义明文导出文件脱离 `CMP` 后的使用接口

### 2.2 平台默认规则与受控例外

- 默认规则：密文文档不可脱离 `CMP` 使用。
- 平台内阅读、预览、签章、归档、搜索、AI 消费，只能通过受控解密访问接口成立。
- 受控例外：`identity-access` 主线完成“解密下载”授权配置并命中后，相关部门、人员可发起解密下载。
- 例外结果：解密下载后导出的明文文件可脱离 `CMP` 使用。
- 例外边界：该例外只改变一次受控导出结果，不改变平台内正式文件仍由文档中心持有的事实。

### 2.3 与总平台 API Design 的关系

- 路径命名、字段命名、统一响应结构、分页约定、幂等约定、错误码风格，继承总平台 [`API Design`](../../api-design.md)。
- 本文只在总平台“文档中心内部加密服务接口”基础上，进一步下沉到加密文档子模块资源级契约。
- 若本文与总平台 `API Design` 存在冲突，以总平台规范为先；若与 `identity-access` 主线的授权归属定义冲突，以授权主线为先回收本模块中的授权配置表述；若与子模块 [`Architecture Design`](./architecture-design.md) 存在冲突，以架构边界为先并回收接口表述。

## 3. 核心资源划分

### 3.1 `EncryptionCheckIn`

用途：承接文档进入文档中心时的自动加密校验与受理结果。

核心字段：

- `check_in_id`
- `document_id`
- `document_version_id`
- `contract_id`
- `owner_type`
- `owner_id`
- `encryption_status`
- `check_in_status`
- `idempotency_key`
- `accepted_at`
- `completed_at`
- `result_code`
- `result_message`

### 3.2 `DecryptAccess`

用途：承接平台内受控解密访问申请与结果摘要，服务对象包括用户会话与平台内模块。

核心字段：

- `decrypt_access_id`
- `document_id`
- `document_version_id`
- `contract_id`
- `access_scene`
- `access_subject_type`
- `access_subject_id`
- `access_result`
- `access_token`
- `token_expires_at`
- `preview_mode`
- `result_code`
- `result_message`
- `occurred_at`

### 3.3 `DecryptDownloadJob`

用途：表达一次解密下载申请、受理、处理、完成或失败结果。

核心字段：

- `job_id`
- `authorization_decision_ref`
- `contract_id`
- `document_id`
- `document_version_id`
- `requested_by`
- `job_status`
- `download_url`
- `download_expires_at`
- `result_code`
- `result_message`
- `requested_at`
- `completed_at`

### 3.4 `EncryptionAuditLog`

用途：表达加密、受控解密访问、解密下载、失败拒绝等安全审计事实。

核心字段：

- `audit_log_id`
- `action_type`
- `action_result`
- `contract_id`
- `document_id`
- `document_version_id`
- `actor_user_id`
- `actor_department_id`
- `related_resource_type`
- `related_resource_id`
- `trace_id`
- `occurred_at`

## 4. 统一约定

### 4.1 协议

- 协议：`HTTPS + JSON`
- 编码：`UTF-8`
- 时间：`ISO 8601`
- 同步写接口成功：`200` 或 `201`
- 异步任务受理成功：`202`
- 分页查询：`page`、`page_size`、`total`、`item_list`
- 统一响应结构继承总平台 [`API Design`](../../api-design.md)

### 4.2 鉴权

- 平台内受控解密访问接口要求平台登录态，或受信任内部服务身份。
- 解密下载任务接口要求平台登录态，且必须同时通过文件访问权限与 `identity-access` 主线的解密下载授权命中校验。
- 内部加密作业查询接口仅开放给平台内部服务、运维查询角色或具备对应管理权限的用户。
- 审计查询接口要求平台登录态 + 对应审计查看权限。
- 本子模块不单独发明第二套账号体系，统一复用平台认证与权限体系。

### 4.3 幂等

- `POST` 型写接口支持 `Idempotency-Key`。
- `EncryptionCheckIn` 必须支持幂等，避免同一文档版本重复触发入库加密。
- `DecryptDownloadJob` 创建接口必须支持幂等，避免重复提交生成多个导出任务。
- 相同幂等键但请求载荷不一致时，复用总平台 `40905 IDEMPOTENCY_CONFLICT`。

### 4.4 命名规范

- 路径资源段使用 `kebab-case`。
- 路径参数、请求字段、响应字段、查询参数使用 `snake_case`。
- 资源主键统一使用 `<resource>_id`。
- 状态字段统一使用 `*_status`。
- 枚举值统一使用 `UPPER_SNAKE_CASE`。

### 4.5 错误码复用策略

本子模块不单独发明一套独立错误码体系，优先复用总平台错误码。

优先复用：

- `40101 AUTH_REQUIRED`
- `40102 AUTH_TOKEN_INVALID`
- `40301 PERMISSION_DENIED`
- `40401 CONTRACT_NOT_FOUND`
- `40905 IDEMPOTENCY_CONFLICT`
- `42201 FILE_VALIDATION_FAILED`
- `42202 ENCRYPTION_CHECK_FAILED`
- `50001 INTERNAL_SERVER_ERROR`
- `50301 ASYNC_JOB_BACKLOG`

子模块专属失败语义，如授权未命中、访问场景不允许、下载任务已过期，
在总平台正式扩展错误码前，先通过上述通用错误域 + `details` 表达；
避免在本阶段 API 文档中提前固化过细错误字典。

## 5. 资源接口契约

### 5.1 `EncryptionCheckIn`

#### `POST /api/internal/document-center/encryption/check-in`

用途：文档进入文档中心时执行入库校验并受理自动加密。

请求重点字段：

- `document_id`
- `document_version_id`
- `contract_id`
- `owner_type`
- `owner_id`
- `source_module`
- `content_type`
- `encryption_required`

响应重点字段：

- `check_in_id`
- `encryption_status`
- `check_in_status`
- `accepted_at`
- `job_id`

边界约束：

- 该接口只受理文档中心已建立主记录的文件对象，不接收脱离文档中心的裸文件上传。
- 该接口只定义“是否受理”和“是否进入自动加密链路”，不定义具体加密算法或介质实现。
- 当处理需要异步执行时，返回 `202` 与 `job_id`。

### 5.2 `DecryptAccess`

#### `POST /api/internal/document-center/encryption/decrypt-access`

用途：平台内用户或内部模块申请受控自动解密访问。

请求重点字段：

- `document_id`
- `document_version_id`
- `contract_id`
- `access_scene`
- `access_subject_type`
- `access_subject_id`
- `client_context`

响应重点字段：

- `decrypt_access_id`
- `access_result`
- `preview_mode`
- `access_token`
- `token_expires_at`
- `result_code`
- `result_message`

边界约束：

- `access_scene` 仅用于平台内受控场景，如 `PREVIEW`、`SIGNATURE`、`ARCHIVE`、`SEARCH`、`AI`。
- 成功结果只代表当前受控场景下的临时访问，不等于对外导出明文。
- 消费方不得绕过该接口长期持久化明文结果为新的文件真相源。

### 5.3 `DecryptDownloadJob`

#### `POST /api/internal/document-center/encryption/decrypt-downloads`

用途：授权命中后发起解密下载任务。

请求重点字段：

- `contract_id`
- `document_id`
- `document_version_id`
- `reason`

响应重点字段：

- `job_id`
- `job_status`
- `accepted_at`
- `authorization_decision_ref`

边界约束：

- 调用方必须已具备平台内文件访问权限。
- 调用方还必须命中 `identity-access` 主线返回的有效解密下载授权。
- 该接口只受理任务，不承诺同步返回下载结果。
- 导出的明文文件可脱离 `CMP` 使用，但该结果不回写替代文档中心正式文件。

#### `GET /api/internal/document-center/encryption/decrypt-downloads/{job_id}`

用途：查询解密下载任务状态与结果。

响应重点字段：

- `job_id`
- `job_status`
- `download_url`
- `download_expires_at`
- `result_code`
- `result_message`
- `completed_at`

边界约束：

- 下载地址只代表受控导出结果，不代表新的平台正式文件资源。
- 过期、失效、撤销、失败等结果统一通过任务状态与错误信息表达。

### 5.4 内部加密作业查询

#### `GET /api/internal/document-center/encryption/jobs/{job_id}`

用途：查询内部加密作业状态、失败原因与处理结果摘要。

响应重点字段：

- `job_id`
- `job_type`
- `job_status`
- `owner_type`
- `owner_id`
- `result_code`
- `result_message`
- `started_at`
- `completed_at`

边界约束：

- 该接口面向平台内部任务跟踪，不作为外部系统回调接口。
- 该接口只暴露作业结果摘要，不暴露内部执行步骤、调度器实现或重试算法细节。

### 5.5 `EncryptionAuditLog`

#### `GET /api/internal/document-center/encryption/audit-logs`

用途：查询加密、受控解密访问、解密下载等审计日志。

查询参数重点字段：

- `contract_id`
- `document_id`
- `action_type`
- `actor_user_id`
- `actor_department_id`
- `action_result`
- `occurred_start`
- `occurred_end`
- `page`
- `page_size`

响应重点字段：

- `item_list`
- `page`
- `page_size`
- `total`

边界约束：

- 本接口负责审计查询边界，不负责统计报表、风险评分或告警规则接口。
- 审计明细应能关联合同、文档、授权判定结果、任务等资源，但不替代这些资源本身的主记录接口。

## 6. 与文档中心的接口边界

- 文档中心是文件真相源；加密子模块只接受带 `document_id` 与版本上下文的请求。
- 文档写入自动加密通过 `EncryptionCheckIn` 挂接文档中心写入链路，不单独接收文件上传。
- 平台内受控解密访问通过 `DecryptAccess` 挂接文档中心读取链路。
- 解密下载结果不回写成文档中心新的正式主文件，除非由文档中心主流程显式接纳新的受控产物。
- 文档中心负责文件对象、版本链、主引用；加密子模块负责加密治理结果、受控解密与下载执行结果、审计事实。

## 7. 与合同主档的接口边界

- 所有受控访问与解密下载请求都应携带 `contract_id` 或可追溯到合同上下文。
- 加密子模块依据合同上下文判断业务归属与访问约束，但不维护合同一级业务状态。
- 加密、解密、下载结果可作为摘要事件回写合同时间线；合同主状态仍由合同主档控制。
- 当合同不存在、合同上下文不匹配或不允许当前动作时，复用总平台资源 / 权限错误码表达。

## 8. 与管理端授权、权限、审计的接口边界

### 8.1 与管理端授权

- 管理端“解密下载”授权配置、授权查询与命中判定由 `identity-access` 主线提供。
- 本子模块只消费授权结论，不复制授权规则资源，也不扩展更复杂策略 DSL。
- 本子模块只定义命中授权后的解密执行与结果查询契约，不定义授权录入、审批单或二次确认页面实现。

### 8.2 与权限体系

- 平台内受控解密访问依赖已有文件访问权限。
- 解密下载需要“文件访问权限 + 解密下载授权”双重命中。
- 子模块不复制角色、菜单、组织主数据，只消费平台权限与组织结果。

### 8.3 与审计体系

- `EncryptionAuditLog` 复用平台统一审计口径。
- 子模块负责定义哪些安全动作必须形成审计事件。
- 审计查询接口只暴露查询边界，不定义审计落库模型、索引设计或留存策略。

## 9. 与签章、归档、搜索、AI 的接口边界

### 9.1 与签章

- 签章模块如需消费正式文件，应通过 `DecryptAccess` 以 `SIGNATURE` 场景申请受控访问。
- 签章过程中的平台内解密使用不等于获得对外明文下载资格。

### 9.2 与归档

- 归档模块如需读取归档输入集，应通过 `DecryptAccess` 以 `ARCHIVE` 场景申请受控访问。
- 归档记录与归档结果主档不由加密子模块持有。

### 9.3 与搜索

- 搜索模块如需读取可索引内容，应通过 `DecryptAccess` 以 `SEARCH` 场景申请受控访问。
- 搜索索引属于读模型，不作为加密子模块的受控访问结果真相源。

### 9.4 与 AI

- AI 模块如需消费正式文件内容，应通过 `DecryptAccess` 以 `AI` 场景申请受控访问。
- AI 输出是辅助结果，不改变授权真相、文件真相与合同真相归属。

## 10. 异步与回调边界

- `EncryptionCheckIn` 与 `DecryptDownloadJob` 在耗时场景下应走异步任务受理模式。
- 异步任务统一通过 `job_id` 查询结果，不在本子模块定义额外回调协议作为主路径。
- `GET /api/internal/document-center/encryption/jobs/{job_id}` 负责加密类异步作业查询。
- `GET /api/internal/document-center/encryption/decrypt-downloads/{job_id}` 负责解密下载结果查询。
- 若后续确需增加内部事件或内部回调，也应保持“平台内协作边界”定位，不扩写为外部系统对接协议。

## 11. 需要下沉到 Detailed Design 的内容边界

以下内容不在本文展开，应下沉到该子模块 `Detailed Design`：

- `EncryptionCheckIn`、`DecryptAccess`、`DecryptDownloadJob`、`EncryptionAuditLog` 的内部状态机
- 加密处理、受控解密、导出封装、过期清理、失败补偿的内部执行流程
- 与 `identity-access` 主线之间的授权校验调用时序
- 审计事件持久化模型、检索索引与留存策略
- 与文档中心、权限中心、审计中心之间的内部事件编排方式
- 导出文件访问介质、下载地址生成方式与失效清理机制
- 任何算法、密钥、介质、存储、表结构与调度实现细节

## 12. 一致性说明

- 本文保持“默认不可脱离平台使用，授权解密下载是受控例外”的正式口径。
- 本文保持“授权配置、授权查询与命中判定归 `identity-access`，解密执行与下载任务归 `encrypted-document`”的正式口径。
- 本文保持“文档中心是真相源，加密模块不拥有文件真相源”的正式口径。
- 本文保持“加密软件是平台内正式子模块，不是外部对接系统”的正式口径。
- 本文保持“只写 API 边界，不下沉到 Detailed Design 实现层”的文档层级边界。

# 加密文档子模块 Detailed Design

## 1. 文档说明

本文档是 `CMP` 加密文档子模块的第一份正式 `Detailed Design`。
本文在以下文档基础上，收口该子模块的内部实现层设计：

### 1.1 输入

- 需求基线：[`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- 总平台架构基线：[`Architecture Design`](../../architecture-design.md)
- 总平台接口基线：[`API Design`](../../api-design.md)
- 总平台共享内部基线：[`Detailed Design`](../../detailed-design.md)
- 子模块架构基线：[`Architecture Design`](./architecture-design.md)
- 子模块接口基线：[`API Design`](./api-design.md)

### 1.2 输出

- 本文：[`Detailed Design`](./detailed-design.md)
- 供后续表级 `DDL`、服务拆分、任务编排、恢复演练定稿使用的内部设计基线

### 1.3 阅读边界

本文只写加密文档子模块的内部实现层设计，重点回答：

- 子模块内部如何拆分，分别持有哪些内部职责
- 自动加密、平台内受控解密访问、授权解密下载如何内部建模
- 与文档中心、合同主档、权限、管理端、签章、归档、搜索、AI 如何挂接
- 核心物理表、状态、幂等、锁、缓存、异步任务、补偿与恢复如何落地

本文不承担以下内容：

- 不复述需求范围、项目阶段目标、验收口径
- 不重写总平台架构总览、模块拓扑或对外资源清单
- 不展开对外 `API` 路径、请求报文、错误码全集
- 不下沉到加密算法、密钥存储介质、导出包格式的实现细节
- 不写实施排期、联调顺序、工时估算或负责人分配

## 2. 设计目标与约束落点

### 2.1 设计目标

- 让文件进入文档中心后自动进入加密治理，而不是依赖人工补做
- 让默认读取路径保持“平台内受控解密使用”，而不是默认形成平台外明文
- 让管理端可按部门、人员授予“解密下载”这一受控例外能力
- 让解密下载导出的明文文件可脱离 `CMP` 使用，但只作为一次受控导出结果存在
- 让签章、归档、搜索、AI 共享同一受控读取边界，不各自长出私有明文通道
- 让全部关键安全动作可审计、可恢复、可追踪，不把恢复责任交给缓存或临时文件

### 2.2 约束落点

- 加密软件是平台内正式子模块，不是外部对接系统
- 文档中心是文件真相源：正式文件主记录、版本链、介质定位仍由文档中心治理
- 合同主档是业务真相源：合同归属、业务阶段、上下文权限仍由合同主档与权限体系治理
- 加密子模块不拥有文件真相源，也不拥有合同主档，只持有安全治理状态与审计事实
- 默认密文文档不可脱离 `CMP` 使用，平台内消费一律走受控解密访问
- 管理端可按部门、人员授权解密下载，导出的明文文件可脱离 `CMP` 使用
- 解密下载全过程必须受权限控制与审计留痕，且不能把导出明文反写成文档中心正式文件

## 3. 模块内部拆分

内部实现按七个组件拆分，统一围绕 `document_asset_id`、`document_version_id`、
`contract_id` 和平台任务中心协作。

### 3.1 `security-binding`

- 持有文档与安全治理的绑定主记录
- 维护当前加密状态、受控读取状态、下载策略摘要、消费能力摘要
- 负责把文档中心真相映射为加密子模块的内部安全视图

### 3.2 `encryption-check-in`

- 挂接文档中心写入路径
- 承接文件入库后的自动加密受理、执行、重试与结果回写
- 负责阻止同一文档版本重复进入多次正式加密处理

### 3.3 `controlled-read-runtime`

- 承接平台内受控解密访问
- 统一解释用户访问、内部服务访问、消费场景差异
- 负责生成短期访问票据、受控流式读取句柄和访问审计

### 3.4 `download-authorization`

- 承接管理端按部门、人员下发的解密下载授权规则
- 负责规则生效期、撤销、范围匹配、授权快照与解释结果
- 不替代平台权限底座，只在“是否允许解密下载”这一例外能力上补充判定

### 3.5 `download-orchestrator`

- 承接解密下载申请、作业编排、导出产物登记、过期失效与结果回写
- 负责把授权命中结果冻结到作业快照，避免长作业期间策略漂移
- 负责失败重试、下载地址过期和导出产物清理

### 3.6 `capability-consumer-adapter`

- 统一挂接签章、归档、搜索、AI 等内部消费方
- 为不同消费场景提供稳定的受控读取模式，而不是把原始明文直接交给消费方长期持有
- 保存消费登记、场景策略和结果摘要，不接管各消费方自己的业务真相

### 3.7 `audit-observability`

- 统一记录加密、解密访问、授权变更、下载申请、下载完成、拒绝、失败与恢复动作
- 输出安全审计事件、业务日志关联字段和指标
- 负责恢复边界所需的诊断数据组织，不替代平台统一日志中心

## 4. 核心物理表设计

本节只列子模块核心物理表与必要策略表，不展开完整 `DDL`。
全部表默认包含基础审计字段：`created_at`、`created_by`、`updated_at`、
`updated_by`、`is_deleted`。

### 4.1 `ed_document_security_binding`

用途：
为每个受加密治理的文档资产建立安全主绑定，是子模块内部读写的入口根表。

- 关键主键：`security_binding_id`
- 关键字段：
  - `document_asset_id`
  - `current_version_id`
  - `contract_id`
  - `owner_type`、`owner_id`
  - `document_role`：`MAIN_BODY`、`ATTACHMENT`、`SIGNATURE_COPY`、`ARCHIVE_COPY`
  - `encryption_profile_code`
  - `encryption_status`：`PENDING`、`ENCRYPTING`、`ENCRYPTED`、`FAILED`、`SUSPENDED`
  - `internal_access_mode`：`PLATFORM_ONLY`、`PLATFORM_CONTROLLED`
  - `download_control_mode`：`FORBIDDEN`、`AUTHORIZED_ONLY`
  - `latest_check_in_id`
  - `last_successful_encrypted_version_id`
  - `last_security_event_at`
  - `security_version_no`：安全治理侧乐观并发版本
- 关键索引 / 唯一约束：
  - `uk_document_asset(document_asset_id)`
  - `idx_contract_role(contract_id, document_role, encryption_status)`
  - `idx_current_version(current_version_id)`
  - `idx_security_event(last_security_event_at)`
- 关联对象：
  - 文档中心 `dc_document_asset`、`dc_document_version`
  - 合同主档 `contract_master`
  - `ed_encryption_check_in`
  - `ed_decrypt_access`
  - `ed_decrypt_download_job`

设计说明：

- 一份文档资产只允许存在一条激活绑定记录
- 该表不复制文件真相，只保存安全治理必要摘要与当前指针
- 文档版本切换时由文档中心驱动更新 `current_version_id`，加密模块只补安全状态

### 4.2 `ed_encryption_check_in`

用途：
记录文档进入加密治理链路时的受理与执行结果，是自动加密主流水表。

- 关键主键：`check_in_id`
- 关键字段：
  - `security_binding_id`
  - `document_asset_id`
  - `document_version_id`
  - `contract_id`
  - `source_module`：`DOCUMENT_CENTER`、`E_SIGNATURE`、`ARCHIVE`、`IMPORT`
  - `trigger_type`：`NEW_VERSION`、`VERSION_REPLACE`、`RECHECK`
  - `check_in_status`：`ACCEPTED`、`READY`、`RUNNING`、`SUCCEEDED`、`FAILED_RETRYABLE`、`FAILED_TERMINAL`
  - `encryption_result_status`：`NOT_STARTED`、`ENCRYPTED`、`REJECTED`
  - `idempotency_key`
  - `content_fingerprint`
  - `payload_ref`
  - `result_code`、`result_message`
  - `accepted_at`、`started_at`、`completed_at`
  - `platform_job_id`
- 关键索引 / 唯一约束：
  - `uk_version_trigger(document_version_id, trigger_type)`
  - `uk_idempotency(idempotency_key)`
  - `idx_binding_status(security_binding_id, check_in_status)`
  - `idx_job(platform_job_id)`
  - `idx_contract_time(contract_id, accepted_at)`
- 关联对象：
  - `ed_document_security_binding`
  - 平台任务中心 `platform_job`
  - 文档中心版本表

设计说明：

- 同一 `document_version_id` 默认只允许一条正式自动加密受理记录
- `RECHECK` 用于人工补偿或策略调整后的重跑，不覆盖原始流水
- 结果摘要写在本表，详细诊断进入审计事件与作业尝试记录

### 4.3 `ed_decrypt_access`

用途：
记录平台内一次受控解密访问申请、判定和票据摘要，服务对象包括用户与内部模块。

- 关键主键：`decrypt_access_id`
- 关键字段：
  - `security_binding_id`
  - `document_asset_id`
  - `document_version_id`
  - `contract_id`
  - `access_scene`：`PREVIEW`、`SIGNATURE`、`ARCHIVE`、`SEARCH`、`AI`
  - `access_subject_type`：`USER`、`INTERNAL_SERVICE`
  - `access_subject_id`
  - `actor_department_id`
  - `access_result`：`APPROVED`、`DENIED`、`EXPIRED`、`REVOKED`
  - `decision_reason_code`
  - `decision_snapshot_ref`
  - `access_ticket`
  - `ticket_expires_at`
  - `consumption_mode`：`STREAM`、`PREVIEW_TOKEN`、`TEMP_TEXT`、`INTERNAL_HANDLE`
  - `consumed_at`
  - `trace_id`
- 关键索引 / 唯一约束：
  - `uk_access_ticket(access_ticket)`
  - `idx_subject_scene(access_subject_type, access_subject_id, access_scene, created_at)`
  - `idx_document_scene(document_asset_id, document_version_id, access_scene)`
  - `idx_contract_result(contract_id, access_result, created_at)`
- 关联对象：
  - `ed_document_security_binding`
  - 权限体系用户 / 内部服务身份
  - 签章、归档、搜索、AI 等消费方

设计说明：

- 该表承接“平台内受控使用”，不是平台外下载授权表
- 成功访问只发放短期票据或内部句柄，不发放可长期复用的明文地址
- 平台内受控访问与平台外导出下载分离建模，避免一次访问票据被误用为下载授权

### 4.4 `ed_decrypt_download_authorization`

用途：
记录管理端授予的“解密下载”例外授权规则。

- 关键主键：`authorization_id`
- 关键字段：
  - `authorization_name`
  - `authorization_status`：`DRAFT`、`ACTIVE`、`EXPIRED`、`REVOKED`
  - `subject_type`：`DEPARTMENT`、`USER`
  - `subject_id`
  - `scope_type`：`GLOBAL`、`ORG_SCOPE`、`CONTRACT_CATEGORY`、`DOCUMENT_ROLE`、`CONTRACT`
  - `scope_value`
  - `download_reason_required`
  - `effective_start_at`、`effective_end_at`
  - `priority_no`
  - `granted_by`
  - `revoked_by`
  - `revoked_at`
  - `policy_snapshot`
- 关键索引 / 唯一约束：
  - `uk_subject_scope(subject_type, subject_id, scope_type, scope_value, effective_start_at)`
  - `idx_status_time(authorization_status, effective_start_at, effective_end_at)`
  - `idx_subject(subject_type, subject_id, authorization_status)`
  - `idx_scope(scope_type, scope_value, authorization_status)`
- 关联对象：
  - 平台组织架构 `org_unit`
  - 用户主表 `user_account`
  - 合同分类 / 文档角色字典
  - `ed_decrypt_download_job`

设计说明：

- 授权最小粒度必须支持部门、人员，两者都可直接建模
- 规则允许按范围收口，但不能退化为单一角色开关
- 策略变更不直接覆盖历史作业判断，历史作业靠授权快照冻结

### 4.5 `ed_decrypt_download_job`

用途：
记录一次解密下载申请、授权冻结、导出生成、交付和过期结果。

- 关键主键：`decrypt_download_job_id`
- 关键字段：
  - `security_binding_id`
  - `authorization_id`
  - `document_asset_id`
  - `document_version_id`
  - `contract_id`
  - `requested_by`
  - `requested_department_id`
  - `download_reason`
  - `request_idempotency_key`
  - `job_status`：`REQUESTED`、`AUTHORIZED`、`GENERATING`、`READY`、`DELIVERED`、`EXPIRED`、`FAILED`、`CANCELLED`
  - `authorization_snapshot_ref`
  - `export_artifact_ref`
  - `export_file_name`
  - `download_url_token`
  - `download_expires_at`
  - `attempt_count`
  - `platform_job_id`
  - `result_code`、`result_message`
  - `requested_at`、`completed_at`
- 关键索引 / 唯一约束：
  - `uk_job_idempotency(requested_by, document_version_id, request_idempotency_key)`
  - `idx_requester_status(requested_by, job_status, requested_at)`
  - `idx_document_status(document_asset_id, document_version_id, job_status)`
  - `idx_platform_job(platform_job_id)`
  - `idx_download_expire(job_status, download_expires_at)`
- 关联对象：
  - `ed_document_security_binding`
  - `ed_decrypt_download_authorization`
  - 平台任务中心 `platform_job`
  - 审计事件表

设计说明：

- 作业记录保存导出事实与授权冻结结果，不保存明文作为平台长期真相
- 明文导出产物只保留受控引用，失效后不影响文档中心正式密文对象
- 同一文档可存在多次历史导出作业，但每次都必须重新经过授权与审计

### 4.6 `ed_encryption_audit_event`

用途：
记录加密子模块的追加式安全审计事件，是本模块最核心的留痕事实表。

- 关键主键：`audit_event_id`
- 关键字段：
  - `event_type`：`CHECK_IN_ACCEPTED`、`ENCRYPT_SUCCEEDED`、`ENCRYPT_FAILED`、`DECRYPT_ACCESS_APPROVED`、`DECRYPT_ACCESS_DENIED`、`DOWNLOAD_AUTH_GRANTED`、`DOWNLOAD_AUTH_REVOKED`、`DOWNLOAD_REQUESTED`、`DOWNLOAD_READY`、`DOWNLOAD_DELIVERED`、`DOWNLOAD_EXPIRED`、`RECOVERY_REPLAYED`
  - `event_result`：`SUCCESS`、`REJECTED`、`FAILED`
  - `security_binding_id`
  - `document_asset_id`
  - `document_version_id`
  - `contract_id`
  - `actor_type`：`USER`、`SYSTEM`、`INTERNAL_SERVICE`
  - `actor_id`
  - `actor_department_id`
  - `related_resource_type`
  - `related_resource_id`
  - `trace_id`
  - `event_payload_ref`
  - `occurred_at`
- 关键索引 / 唯一约束：
  - `idx_document_time(document_asset_id, occurred_at)`
  - `idx_contract_event(contract_id, event_type, occurred_at)`
  - `idx_actor_time(actor_type, actor_id, occurred_at)`
  - `idx_related_resource(related_resource_type, related_resource_id)`
  - `idx_trace(trace_id)`
- 关联对象：
  - `ed_encryption_check_in`
  - `ed_decrypt_access`
  - `ed_decrypt_download_authorization`
  - `ed_decrypt_download_job`

设计说明：

- 本表追加写，不做业务覆盖更新
- 平台审计中心可从本表汇总，但本表保留子模块级安全事实颗粒度
- 恢复动作本身也必须记一条审计事件，避免“恢复导致留痕缺口”

### 4.7 `ed_capability_consume_policy`

用途：
定义签章、归档、搜索、AI 等内部消费场景的受控读取策略，是必要的内部策略表。

- 关键主键：`consume_policy_id`
- 关键字段：
  - `consumer_code`：`E_SIGNATURE`、`ARCHIVE`、`SEARCH`、`AI`
  - `access_scene`
  - `allowed_document_role`
  - `allowed_encryption_status`
  - `allowed_consumption_mode`
  - `policy_status`：`ACTIVE`、`DISABLED`
  - `ttl_seconds`
  - `max_concurrency`
  - `policy_version_no`
- 关键索引 / 唯一约束：
  - `uk_consumer_scene(consumer_code, access_scene)`
  - `idx_policy_status(policy_status, consumer_code)`
- 关联对象：
  - `ed_decrypt_access`
  - 签章、归档、搜索、AI 内部服务

设计说明：

- 该表不表达用户下载授权，只表达平台内部消费策略
- 通过策略版本号把消费行为和策略快照建立可追踪关系

## 5. 自动加密内部模型

### 5.1 触发入口

自动加密只接受文档中心已经建立正式 `DocumentAsset` 与 `DocumentVersion`
后的内部事件或内部调用，不接受脱离文档中心的裸文件输入。

触发来源首批包含：

- 合同正文或附件首次入库
- 文档版本替换
- 签章结果稿回收
- 归档稿回收
- 人工补偿触发的安全重检

### 5.2 内部处理链

1. 文档中心完成主记录与版本记录事务提交。
2. `security-binding` 以 `document_asset_id` 查找或创建绑定主记录。
3. `encryption-check-in` 生成 `ed_encryption_check_in` 记录，并写入幂等键。
4. 若命中轻量同步条件，可直接完成受理校验并投递平台任务。
5. 异步执行器读取原始版本内容，完成加密处理并回写受控介质引用。
6. 成功后更新 `ed_document_security_binding.encryption_status=ENCRYPTED`。
7. 写入 `ed_encryption_audit_event`，并向文档中心回写“当前版本已纳入加密治理”摘要。

### 5.3 状态机

`ed_encryption_check_in.check_in_status` 状态机建议如下：

```text
ACCEPTED -> READY -> RUNNING -> SUCCEEDED
                     -> FAILED_RETRYABLE -> READY
                     -> FAILED_TERMINAL
```

`ed_document_security_binding.encryption_status` 状态机建议如下：

```text
PENDING -> ENCRYPTING -> ENCRYPTED
                     -> FAILED
FAILED -> ENCRYPTING
ENCRYPTED -> SUSPENDED
```

说明：

- `FAILED` 表示当前版本未完成正式加密，不代表文档资产失效
- `SUSPENDED` 用于策略冲突、介质异常或人工冻结，阻止继续消费
- 版本切换时只推进当前版本状态，不回写历史版本流水

### 5.4 结果回写原则

- 成功加密后，只回写文档中心可消费的安全摘要与受控读取入口引用
- 不把加密模块中的中间结果写成新的文档正式版本
- 合同主档最多写入时间线摘要，例如“附件已进入受控加密治理”，不改写合同主状态

## 6. 平台内受控解密访问内部模型

### 6.1 适用场景

平台内受控解密访问适用于：

- 合同详情预览
- 审批过程阅读
- 签章处理输入
- 归档封包生成输入
- 搜索索引提取
- AI 文本提取与问答输入

这些场景都属于平台内受控消费，不等于平台外明文下载。

### 6.2 判定输入

受控读取判定至少依赖以下输入：

- 当前用户或内部服务身份
- 文件访问权限与合同上下文权限
- 文档角色与当前版本加密状态
- 场景策略 `ed_capability_consume_policy`
- 当前绑定是否处于 `SUSPENDED` 或下载冻结等安全限制状态

### 6.3 处理模型

1. 消费方发起内部读取申请。
2. `controlled-read-runtime` 加载 `ed_document_security_binding`。
3. 读取权限中心与合同主档上下文，确认是否具备查看该合同 / 文档权限。
4. 根据 `access_scene` 匹配消费策略，决定可用 `consumption_mode`。
5. 生成 `ed_decrypt_access` 记录与短期访问票据。
6. 消费方在票据有效期内通过内部受控句柄读取，不获得长期明文介质引用。
7. 访问结束或超时后标记 `consumed_at` 或 `EXPIRED`。

### 6.4 内部边界

- 平台内受控访问允许短时明文驻留在进程内存或临时处理缓冲区
- 不允许把明文持久化成新的文件真相源、长期缓存或跨模块共享目录
- 搜索和 AI 只能保存必要派生数据，如索引文本、摘要、向量或结构化提取结果
- 这些派生数据都不是文档中心正式文件真相

## 7. 管理端授权与解密下载内部模型

### 7.1 授权模型

管理端授权只解决“谁可以发起平台外明文导出”这一高敏例外问题。
其内部模型分为三层：

- 主体层：按部门、人员建模授权主体
- 范围层：按合同、合同分类、文档角色或组织范围定义授权边界
- 时效层：通过生效期、撤销状态控制授权有效窗口

授权判断规则：

- 必须先具备原始文件访问权限，下载授权不能替代普通查看权限
- 命中部门授权时，仍需校验申请人的当前部门归属
- 命中人员授权时，以用户主键为准，不通过角色继承隐式放大
- 授权撤销后，只影响新的下载申请；已生成的历史作业按冻结快照执行或过期失效

### 7.2 下载申请与冻结快照

下载申请采用“申请时判定 + 作业时冻结”模型：

1. 用户提交下载申请与业务理由。
2. 系统校验普通访问权限、授权规则、文档状态和合同上下文。
3. 命中后创建 `ed_decrypt_download_job`，并冻结：
   - 命中的 `authorization_id`
   - 申请人身份与部门快照
   - 文档版本快照
   - 当时的策略快照
4. 后续导出执行只消费冻结快照，不重新解释漂移中的管理配置。

这样可以避免“申请通过后，执行期间授权已被修改，导致作业结果不可解释”。

### 7.3 导出作业模型

导出作业统一走平台任务中心，`ed_decrypt_download_job` 是业务主表，
`platform_job` 是平台执行表。

状态机建议如下：

```text
REQUESTED -> AUTHORIZED -> GENERATING -> READY -> DELIVERED
                                     -> FAILED
READY -> EXPIRED
REQUESTED -> CANCELLED
AUTHORIZED -> CANCELLED
```

说明：

- `AUTHORIZED` 表示权限与快照冻结完成，尚未生成导出产物
- `READY` 表示导出明文已生成且下载入口已发放
- `DELIVERED` 表示下载动作已实际发生
- `EXPIRED` 表示下载入口超时失效，不代表历史授权无效

### 7.4 默认规则与例外边界

- 默认规则始终不变：正式密文文档不可脱离 `CMP` 使用
- 管理端授权只开通“受控导出一次结果”的例外，不把平台默认规则改写为“可自由下载”
- 导出的明文文件可脱离 `CMP` 使用，但该文件不是文档中心真相，不回流替代正式密文对象
- 若需要再次导出，必须重新发起申请并重新留痕

## 8. 与相关模块的内部挂接设计

### 8.1 与文档中心的挂接

- 文档中心是文件真相源，加密模块只通过 `document_asset_id` / `document_version_id` 挂接
- 写入路径：文档中心完成入库后触发加密受理
- 读取路径：文档中心在读取前调用受控解密访问边界
- 版本切换：文档中心负责变更主版本，加密模块同步刷新绑定摘要与检查状态
- 删除 / 归档 / 失效：文档中心发出状态变化后，加密模块更新安全绑定和下载可用性

### 8.2 与合同主档的挂接

- 加密模块读取 `contract_id`、合同归属组织、业务分类、生命周期摘要
- 是否允许平台内读取或下载，必须结合合同上下文与权限判定
- 安全事件可写入合同时间线摘要，但不修改合同主状态
- 合同主档变更归属部门时，历史下载授权不自动迁移，需按实时组织关系重新判定

### 8.3 与权限 / 审计 / 管理端的挂接

- 普通访问权限由平台权限底座判定，加密模块只追加高敏动作控制
- 管理端授权配置结果写入 `ed_decrypt_download_authorization`
- 审计中心可汇总查询 `ed_encryption_audit_event`，但不替代其原始记录
- 管理端撤销授权时，应同步使未来下载申请失效，并写入撤销审计事件

### 8.4 与签章的挂接

- 签章只能通过 `SIGNATURE` 场景受控读取正式文件
- 签章过程中的临时明文只允许存在于签章处理窗口内
- 签章结果稿回收文档中心后重新走自动加密链路

### 8.5 与归档的挂接

- 归档封包生成通过 `ARCHIVE` 场景读取受控内容
- 归档模块持有归档记录真相，加密模块只持有其读取审计与安全摘要
- 归档稿进入文档中心后继续纳入加密治理，不因归档而脱离默认规则

### 8.6 与搜索的挂接

- 搜索只能通过 `SEARCH` 场景获取受控文本输入
- 索引中保存的是派生文本、分词结果、向量等读模型，不是正式明文文件
- 加密模块负责限制索引构建入口与记录审计，不接管索引内部实现

### 8.7 与 AI 的挂接

- AI 只能通过 `AI` 场景读取受控内容
- AI 输出仅作为摘要、问答、提取、风险提示等辅助结果
- AI 不得将明文重新落成新的正式文件，也不得绕过权限直接拿到平台外下载能力

## 9. 缓存、锁、幂等与并发控制

### 9.1 缓存边界

`Redis` 只作为增强层，允许缓存：

- 加密受理幂等键
- 受控访问短期票据
- 下载地址短期令牌
- 授权规则只读快照
- 作业执行心跳与短态

必须落库的内容：

- 安全绑定主记录
- 自动加密流水
- 受控解密访问记录
- 解密下载授权与作业记录
- 审计事件

### 9.2 锁粒度

短锁粒度建议如下：

- `ed:binding:{document_asset_id}`：保护安全绑定更新与当前版本切换摘要
- `ed:checkin:{document_version_id}`：保护同一版本自动加密受理
- `ed:download:{document_version_id}:{user_id}`：保护同一用户重复申请下载
- `ed:job:{decrypt_download_job_id}`：保护导出作业单执行上下文

锁只做互斥，不做最终成功判断；最终一致性仍由数据库唯一约束和条件更新保证。

### 9.3 幂等规则

- 自动加密：以 `document_version_id + trigger_type` 做正式唯一受理
- 平台内受控访问：不要求全局幂等，但相同短期重试可复用未过期票据
- 解密下载申请：以申请人、文档版本、申请批次键或请求幂等键防止重复建单
- 授权写入：相同主体、范围、生效起点采用唯一约束防止重复规则堆叠

### 9.4 并发控制

- `ed_document_security_binding` 使用 `security_version_no` 做乐观并发
- 关键状态迁移统一使用条件更新，例如只允许从 `GENERATING` 更新到 `READY`
- 授权撤销与下载生成并发时，以下载作业冻结快照为准，避免一半成功一半失败的漂移解释
- 文档版本切换与自动加密并发时，以文档中心版本主键为真相，旧版本流水允许继续完成，但不得覆盖新版本当前指针

## 10. 异步任务、补偿与恢复

### 10.1 任务类型

本子模块首批任务类型建议包括：

- `ED_ENCRYPTION_CHECK_IN`
- `ED_DECRYPT_DOWNLOAD_EXPORT`
- `ED_DOWNLOAD_EXPIRE_CLEANUP`
- `ED_SECURITY_RECHECK`

全部复用平台任务中心的统一状态机、重试和人工介入机制。

### 10.2 自动补偿边界

- 加密受理已落库但平台任务未创建：通过补扫 `ed_encryption_check_in` 自动补投任务
- 导出作业 `AUTHORIZED` 后执行器崩溃：由调度器按 `platform_job` 心跳回收重试
- 下载入口已过期但清理任务未执行：允许延迟清理，不影响平台真相
- 审计事件写入失败：业务主动作不判定为完成，必须重试或转人工处理

### 10.3 不自动补偿的边界

- 权限不足、授权未命中、合同状态不允许下载：直接终态失败，不重试
- 文档中心当前版本已切换而申请基于旧版本：保留历史失败，不自动改绑新版本执行
- 导出明文已被用户下载：不做“回收导出文件”式伪恢复，只做过期失效和后续审计

### 10.4 恢复流程

恢复优先级如下：

1. 以文档中心当前版本和 `ed_document_security_binding` 对账
2. 重放 `FAILED_RETRYABLE` 的自动加密流水
3. 回收悬挂下载任务并按快照重试
4. 对缺失审计事件的关键业务记录做审计补记
5. 对仍无法自动恢复的任务转 `WAITING_MANUAL`

## 11. 审计、日志、指标与恢复边界

### 11.1 审计要求

以下动作必须形成原子级审计事实：

- 自动加密受理
- 自动加密成功 / 失败
- 平台内解密访问批准 / 拒绝
- 管理端授权新增 / 修改 / 撤销
- 解密下载申请、生成完成、实际交付、过期失效
- 恢复重放、人工介入、策略冻结

审计最少字段包括：主体、对象、合同上下文、文档版本、场景、结果、时间、追踪号。

### 11.2 日志要求

- 业务日志记录判定路径、状态迁移、下游调用摘要
- 安全日志记录授权命中、拒绝原因、异常下载、策略变更
- 执行日志记录作业尝试、耗时、重试原因、恢复动作
- 全部日志统一带 `trace_id`、`contract_id`、`document_asset_id`、`document_version_id`

### 11.3 指标要求

至少输出以下指标：

- 自动加密受理量、成功率、平均耗时、失败率
- 平台内受控访问批准率、拒绝率、票据过期率
- 解密下载申请量、授权命中率、导出成功率、下载完成率、过期率
- 恢复重放次数、人工介入次数、悬挂任务数

### 11.4 恢复边界

- 可以恢复的：绑定摘要、自动加密流水状态、下载作业状态、审计缺口、过期令牌状态
- 不作为恢复目标的：平台外已交付的明文文件实体本身
- 恢复后仍以文档中心密文对象与合同主档上下文为正式真相，不用导出结果反推平台真相

## 12. 继续下沉到后续专项设计或实现的内容

以下内容应继续下沉，不在本文写死：

- 加密算法选择、密钥层级、密钥轮换、介质保护和托管实现
  加密算法与密钥层级专项设计： [crypto-algorithm-and-key-hierarchy-design.md](special-designs/crypto-algorithm-and-key-hierarchy-design.md)
- 受控读取句柄的具体协议、流式分段策略和临时缓存介质实现
  受控读取句柄专项设计： [controlled-read-handle-design.md](special-designs/controlled-read-handle-design.md)
- 明文导出包格式、水印、脱敏选项、文件名规范
  明文导出包专项设计： [plaintext-export-package-design.md](special-designs/plaintext-export-package-design.md)
- 授权范围表达式是否扩展到更细粒度字段、标签或业务规则
  授权范围表达式专项设计： [authorization-scope-expression-design.md](special-designs/authorization-scope-expression-design.md)
- 搜索 / AI 消费文本的脱敏策略与二次存储治理
  脱敏策略与二次存储专项设计： [desensitization-and-secondary-storage-design.md](special-designs/desensitization-and-secondary-storage-design.md)
- 签章、归档、搜索、AI 各消费方的专项适配细节与压力测试参数
  消费方适配与压力测试专项设计： [consumer-adaptation-and-pressure-test-design.md](special-designs/consumer-adaptation-and-pressure-test-design.md)
- 表级 `DDL` 变更治理、核心表与索引实现基线、仓储接口签名、内部事件载荷结构、任务重试参数默认值和告警规则样例。治理设计已完成，表、索引、接口、事件、重试和告警实现基线由该专项承接。
  DDL 事件与重试参数专项设计： [ddl-event-and-retry-parameter-design.md](special-designs/ddl-event-and-retry-parameter-design.md)

本文到此为止，保持在子模块内部实现层，回答“如何落地内部模型、状态、
表、任务、恢复与挂接”，不越界到需求、架构总览、对外 `API` 或实施计划层。

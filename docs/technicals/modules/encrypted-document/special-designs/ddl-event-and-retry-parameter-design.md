# DDL 事件与重试参数专项设计

## 1. 文档定位

本文档是 `encrypted-document` 子模块的**表结构变更治理与实现基线设计**，承接 [`detailed-design.md`](../detailed-design.md) 第 12 节中"表级 DDL、仓储接口、内部事件载荷、任务重试参数和告警阈值"的专项下沉。

本文档只回答以下问题：
- DDL 事件如何治理：对象是谁、稳定锚点在哪里、版本如何归属、最小治理单元是什么
- 核心表结构草案和索引清单如何作为实现基线落地
- 仓储接口的治理边界在哪里、责任如何划分
- 仓储接口签名如何覆盖核心读写路径
- 内部事件载荷的治理边界在哪里、责任如何划分
- 内部事件载荷结构如何支撑审计、恢复和消费方联动
- 任务重试参数的治理对象是什么、与任务类型的对应关系如何
- 任务重试默认值如何作为首批实现参数
- 告警阈值的治理对象是什么、与监控维度的对应关系如何
- 告警规则样例如何覆盖首批核心风险
- 与前六份专项设计的引用关系
- 与 `contract-core`、`document-center` 的边界如何划分

本文档**不承担**以下内容：
- 不展开 API 路径、请求/响应结构、错误码
- 不输出可直接执行的 DDL 脚本、索引创建语句
- 不编写仓储接口实现代码、事件发布/订阅代码、重试逻辑代码
- 不包含实施排期、工时估算、负责人分配
- 不写成运维手册、部署方案、故障排查指南
- 不描述具体参数代码、告警规则配置代码、监控采集代码

核心表草案、索引方向、仓储接口签名、内部事件载荷结构、重试默认值和告警规则样例属于本文承接范围，不再下沉到其他专项。

---

## 2. DDL 事件治理

### 2.1 治理对象

DDL 事件治理的**一级对象**是 `ddl-event`（DDL 事件），每个事件封装一次表结构变更的治理动作、影响范围与回溯能力。

治理对象层级：

| 层级 | 对象 | 说明 |
| --- | --- | --- |
| 一级 | `ddl-event` | DDL 事件，是 DDL 治理的最小单元 |
| 二级 | `ddl-spec` | DDL 规格，描述变更类型、目标表、变更内容摘要 |
| 三级 | `ddl-impact` | DDL 影响范围，记录变更影响的字段、索引、约束、依赖对象 |

`ddl-event` 不持有 DDL 执行代码，不持有表结构详情，只持有"谁在何时对哪个表做了什么类型的变更、影响范围如何"的治理描述。

### 2.2 稳定锚点

DDL 事件治理的**稳定锚点**是 `ddl-event-id`（DDL 事件标识），其稳定性约束如下：

- `ddl-event-id` 是跨模块、跨版本的稳定引用键，审计链路、任务中心、恢复流程均通过 `ddl-event-id` 追溯 DDL 变更。
- `ddl-event-id` 一旦生成，不允许修改其核心属性（目标表、变更类型、变更摘要）；若需变更表结构，只能创建新的 `ddl-event-id`。
- `ddl-event-id` 与 `document-center` 的松耦合：文档中心不感知加密模块的 DDL 事件，只通过表结构变更间接影响数据访问。
- `ddl-event-id` 与 `contract-core` 的松耦合：合同主档不感知加密模块的 DDL 事件，只通过 `document_asset_id` 间接关联到加密模块的表结构版本。

### 2.3 版本归属

DDL 事件版本归属规则：

| 版本维度 | 归属主体 | 说明 |
| --- | --- | --- |
| `ddl-event-version` | `encrypted-document` 子模块 | DDL 事件自身的版本演进，由加密模块独立治理 |
| `ddl-spec-version` | `encrypted-document` 子模块 | DDL 规格版本，随表结构演进或合规要求演进 |
| `table-version` | `encrypted-document` 子模块 | 表结构版本，记录每张表的结构变更历史 |
| `document-version` | `document-center` | 文档版本，加密模块只引用，不拥有 |
| `contract-version` | `contract-core` | 合同版本，加密模块不直接访问 |

版本归属的核心原则：**DDL 事件版本由加密模块自治，文档版本和合同版本由各自归属模块治理，通过 `ddl-event-id` 建立变更追溯链**。

### 2.4 最小治理单元

DDL 事件的最小治理单元是 **`ddl-event` + `ddl-spec` 的组合**，具备以下治理属性：

- 可独立审批（DDL 变更前必须经由治理流程审批）
- 可独立审计（谁在何时对哪个表做了什么变更）
- 可独立回溯（通过 `ddl-event-id` 追溯变更前后状态）
- 可独立关联（关联到具体的任务执行、恢复动作、回滚记录）

最小治理单元**不包含**具体 DDL 执行语句、具体表结构定义、具体索引创建语句——这些属于执行层，不在治理设计范围内。

### 2.5 DDL 事件类型

DDL 事件必须覆盖以下类型：

| 事件类型 | 说明 | 治理要求 |
| --- | --- | --- |
| `TABLE_CREATE` | 新建表 | 必须关联 `ddl-spec`，记录表用途、关键字段、索引策略 |
| `TABLE_ALTER` | 修改表结构 | 必须记录变更前后差异、影响范围、回滚方案 |
| `INDEX_CREATE` | 新建索引 | 必须记录索引类型、字段组合、性能影响评估 |
| `INDEX_DROP` | 删除索引 | 必须记录删除原因、替代方案、影响范围 |
| `CONSTRAINT_ADD` | 新增约束 | 必须记录约束类型、字段、验证规则 |
| `CONSTRAINT_DROP` | 删除约束 | 必须记录删除原因、兼容性影响 |
| `TABLE_DROP` | 删除表 | 必须关联归档策略、数据迁移方案、回滚方案 |

---

## 3. 核心表结构与索引实现基线

本节不提供可直接执行的建表语句，而是给出开发实现必须遵循的核心表草案、关键字段、状态枚举、唯一约束和索引方向。详细字段说明与状态语义继续以 [`detailed-design.md`](../detailed-design.md) 第 4 节为主，本节用于补齐专项下沉的表级基线和索引清单。

### 3.1 核心表草案

| 表名 | 主键 | 核心字段 | 状态字段 | 唯一约束 | 主要索引方向 |
| --- | --- | --- | --- | --- | --- |
| `ed_document_security_binding` | `security_binding_id` | `document_asset_id`、`current_version_id`、`contract_id`、`owner_type`、`owner_id`、`document_role`、`encryption_profile_code`、`latest_check_in_id`、`last_successful_encrypted_version_id`、`security_version_no` | `encryption_status`、`internal_access_mode`、`download_control_mode` | `uk_document_asset(document_asset_id)` | 合同与文档角色查询、当前版本查询、安全事件时间查询 |
| `ed_encryption_check_in` | `check_in_id` | `security_binding_id`、`document_asset_id`、`document_version_id`、`contract_id`、`source_module`、`trigger_type`、`idempotency_key`、`content_fingerprint`、`payload_ref`、`platform_job_id` | `check_in_status`、`encryption_result_status` | `uk_version_trigger(document_version_id, trigger_type)`、`uk_idempotency(idempotency_key)` | 绑定状态查询、作业查询、合同时间查询 |
| `ed_decrypt_access` | `decrypt_access_id` | `security_binding_id`、`document_asset_id`、`document_version_id`、`contract_id`、`access_scene`、`access_subject_type`、`access_subject_id`、`decision_snapshot_ref`、`access_ticket`、`ticket_expires_at`、`consumption_mode`、`trace_id` | `access_result` | `uk_access_ticket(access_ticket)` | 主体场景查询、文档场景查询、合同访问结果查询 |
| `ed_decrypt_download_authorization` | `authorization_id` | `authorization_name`、`subject_type`、`subject_id`、`scope_type`、`scope_value`、`download_reason_required`、`effective_start_at`、`effective_end_at`、`priority_no`、`policy_snapshot` | `authorization_status` | `uk_subject_scope(subject_type, subject_id, scope_type, scope_value, effective_start_at)` | 授权状态时效查询、授权主体查询、授权范围查询 |
| `ed_decrypt_download_job` | `decrypt_download_job_id` | `security_binding_id`、`authorization_id`、`document_asset_id`、`document_version_id`、`contract_id`、`requested_by`、`download_reason`、`request_idempotency_key`、`authorization_snapshot_ref`、`export_artifact_ref`、`download_url_token`、`download_expires_at`、`attempt_count`、`platform_job_id` | `job_status` | `uk_job_idempotency(requested_by, document_version_id, request_idempotency_key)` | 申请人状态查询、文档作业查询、平台作业查询、下载过期扫描 |
| `ed_encryption_audit_event` | `audit_event_id` | `event_type`、`event_result`、`security_binding_id`、`document_asset_id`、`document_version_id`、`contract_id`、`actor_type`、`actor_id`、`related_resource_type`、`related_resource_id`、`trace_id`、`event_payload_ref`、`occurred_at` | `event_result` | 无业务唯一约束，追加写 | 文档时间查询、合同事件查询、主体时间查询、关联资源查询、追踪号查询 |
| `ed_capability_consume_policy` | `consume_policy_id` | `consumer_code`、`access_scene`、`allowed_document_role`、`allowed_encryption_status`、`allowed_consumption_mode`、`ttl_seconds`、`max_concurrency`、`policy_version_no` | `policy_status` | `uk_consumer_scene(consumer_code, access_scene)` | 策略状态查询、消费方查询 |

### 3.2 索引清单

| 表名 | 索引或约束 | 字段 | 用途 |
| --- | --- | --- | --- |
| `ed_document_security_binding` | `uk_document_asset` | `document_asset_id` | 保证一份文档资产只有一条激活安全绑定 |
| `ed_document_security_binding` | `idx_contract_role` | `contract_id, document_role, encryption_status` | 合同详情、归档、签章按角色读取安全状态 |
| `ed_document_security_binding` | `idx_current_version` | `current_version_id` | 文档版本切换后快速定位安全绑定 |
| `ed_document_security_binding` | `idx_security_event` | `last_security_event_at` | 安全事件补偿与巡检 |
| `ed_encryption_check_in` | `uk_version_trigger` | `document_version_id, trigger_type` | 防止同一版本同一触发类型重复受理 |
| `ed_encryption_check_in` | `uk_idempotency` | `idempotency_key` | 自动加密受理幂等 |
| `ed_encryption_check_in` | `idx_binding_status` | `security_binding_id, check_in_status` | 查询绑定下的处理流水 |
| `ed_encryption_check_in` | `idx_job` | `platform_job_id` | 平台任务回写定位 |
| `ed_encryption_check_in` | `idx_contract_time` | `contract_id, accepted_at` | 合同维度审计与排障 |
| `ed_decrypt_access` | `uk_access_ticket` | `access_ticket` | 短期访问票据唯一 |
| `ed_decrypt_access` | `idx_subject_scene` | `access_subject_type, access_subject_id, access_scene, created_at` | 用户或内部服务访问追踪 |
| `ed_decrypt_access` | `idx_document_scene` | `document_asset_id, document_version_id, access_scene` | 文档场景访问审计 |
| `ed_decrypt_access` | `idx_contract_result` | `contract_id, access_result, created_at` | 合同维度批准、拒绝统计 |
| `ed_decrypt_download_authorization` | `uk_subject_scope` | `subject_type, subject_id, scope_type, scope_value, effective_start_at` | 防止同一主体范围同一生效起点重复授权 |
| `ed_decrypt_download_authorization` | `idx_status_time` | `authorization_status, effective_start_at, effective_end_at` | 生效授权查询与过期扫描 |
| `ed_decrypt_download_authorization` | `idx_subject` | `subject_type, subject_id, authorization_status` | 主体授权匹配 |
| `ed_decrypt_download_authorization` | `idx_scope` | `scope_type, scope_value, authorization_status` | 范围授权匹配 |
| `ed_decrypt_download_job` | `uk_job_idempotency` | `requested_by, document_version_id, request_idempotency_key` | 下载申请幂等 |
| `ed_decrypt_download_job` | `idx_requester_status` | `requested_by, job_status, requested_at` | 用户下载记录查询 |
| `ed_decrypt_download_job` | `idx_document_status` | `document_asset_id, document_version_id, job_status` | 文档下载作业查询 |
| `ed_decrypt_download_job` | `idx_platform_job` | `platform_job_id` | 平台任务回写定位 |
| `ed_decrypt_download_job` | `idx_download_expire` | `job_status, download_expires_at` | 下载地址过期清理 |
| `ed_encryption_audit_event` | `idx_document_time` | `document_asset_id, occurred_at` | 文档安全审计查询 |
| `ed_encryption_audit_event` | `idx_contract_event` | `contract_id, event_type, occurred_at` | 合同安全事件时间线 |
| `ed_encryption_audit_event` | `idx_actor_time` | `actor_type, actor_id, occurred_at` | 主体操作追踪 |
| `ed_encryption_audit_event` | `idx_related_resource` | `related_resource_type, related_resource_id` | 关联业务资源审计串联 |
| `ed_encryption_audit_event` | `idx_trace` | `trace_id` | 调用链排障 |
| `ed_capability_consume_policy` | `uk_consumer_scene` | `consumer_code, access_scene` | 一个消费方场景只有一个当前策略 |
| `ed_capability_consume_policy` | `idx_policy_status` | `policy_status, consumer_code` | 启用策略匹配 |

### 3.3 表结构变更治理与实现基线关系

表结构变更仍通过第 2 节的 `ddl-event` 治理；上表是当前实现基线。新增字段、索引、约束或表时必须先形成 `ddl-event`，并同步更新本节对应表草案和索引清单，避免治理记录与实现基线漂移。

---

## 4. 仓储接口治理与签名基线

### 4.1 治理边界

仓储接口治理的**核心对象**是 `repository-interface`（仓储接口），定义数据访问的抽象边界、方法契约、返回值规范。

仓储接口治理边界：

**`encrypted-document` 负责的边界：**
- 仓储接口的方法定义（查询、写入、更新、删除的抽象契约）
- 仓储接口与表结构的映射关系（哪个接口对应哪些表）
- 仓储接口的返回值规范（成功、失败、幂等、并发冲突的治理语义）
- 仓储接口的审计要求（哪些操作必须形成审计事件）

**`encrypted-document` 不负责的边界：**
- 仓储接口的具体实现代码（由执行层负责）
- ORM 框架选择、SQL 拼接、连接池管理
- 数据库事务的具体编排、隔离级别选择

**`document-center` 的边界：**
- 持有文档对象、版本链、存储定位
- 不感知加密模块的仓储接口内部细节
- 只通过 `document_asset_id` 间接感知"该文档在加密模块中是否有对应仓储记录"

**`contract-core` 的边界：**
- 持有合同主档、业务状态、生命周期
- 不直接参与仓储接口治理
- 通过 `contract_id` 间接关联到加密模块的仓储查询

### 4.2 责任划分

仓储接口的责任划分：

| 责任维度 | `encrypted-document` 负责 | `document-center` 负责 | `contract-core` 负责 |
| --- | --- | --- | --- |
| 接口定义 | ✅ 方法契约、返回值规范 | ❌ 不感知 | ❌ 不感知 |
| 表映射关系 | ✅ 接口与表的对应关系 | ❌ 不持有映射状态 | ❌ 不持有映射状态 |
| 审计要求 | ✅ 哪些操作必须审计、审计要素 | ❌ 不记录 | ❌ 不记录 |
| 实现代码 | ❌ 不负责（执行层负责） | ❌ 不负责 | ❌ 不负责 |
| 事务编排 | ❌ 不负责（执行层负责） | ❌ 不负责 | ❌ 不负责 |

### 4.3 仓储接口治理约束

仓储接口必须满足以下治理约束：

| 约束维度 | 治理要求 |
| --- | --- |
| 抽象隔离 | 仓储接口不暴露数据库实现细节，不暴露 ORM 特定类型 |
| 幂等语义 | 写入、更新操作必须定义幂等契约，明确重复调用的治理结果 |
| 并发语义 | 更新操作必须定义并发控制契约（乐观锁、条件更新） |
| 审计穿透 | 关键操作必须定义审计穿透要求，不允许静默执行无审计 |
| 返回值治理 | 返回值必须区分"成功"、"失败"、"幂等重复"、"并发冲突"等治理语义 |

### 4.4 仓储接口签名基线

仓储接口签名只定义方法契约，不绑定具体语言、框架或 ORM。返回值中的 `RepoResult` 必须能表达 `SUCCESS`、`NOT_FOUND`、`IDEMPOTENT_HIT`、`CONFLICT`、`FAILED` 五类结果。

#### 4.4.1 `DocumentSecurityBindingRepository`

```text
findByDocumentAssetId(document_asset_id) -> DocumentSecurityBinding?
findByCurrentVersionId(current_version_id) -> DocumentSecurityBinding?
createIfAbsent(command, idempotency_key) -> RepoResult<DocumentSecurityBinding>
updateEncryptionStatus(security_binding_id, expected_version_no, target_status, latest_check_in_id) -> RepoResult<DocumentSecurityBinding>
attachSuccessfulVersion(security_binding_id, expected_version_no, document_version_id, check_in_id) -> RepoResult<DocumentSecurityBinding>
markSuspended(security_binding_id, expected_version_no, reason_code) -> RepoResult<DocumentSecurityBinding>
```

#### 4.4.2 `EncryptionCheckInRepository`

```text
createAccepted(command, idempotency_key) -> RepoResult<EncryptionCheckIn>
findByDocumentVersionAndTrigger(document_version_id, trigger_type) -> EncryptionCheckIn?
findRetryableBefore(limit_time, page_size) -> Page<EncryptionCheckIn>
markReady(check_in_id, expected_status) -> RepoResult<EncryptionCheckIn>
markRunning(check_in_id, platform_job_id, expected_status) -> RepoResult<EncryptionCheckIn>
markSucceeded(check_in_id, result_summary, expected_status) -> RepoResult<EncryptionCheckIn>
markFailedRetryable(check_in_id, result_code, result_message, next_retry_at) -> RepoResult<EncryptionCheckIn>
markFailedTerminal(check_in_id, result_code, result_message) -> RepoResult<EncryptionCheckIn>
```

#### 4.4.3 `DecryptAccessRepository`

```text
createDecision(command, access_ticket, decision_snapshot_ref) -> RepoResult<DecryptAccess>
findByAccessTicket(access_ticket) -> DecryptAccess?
markConsumed(decrypt_access_id, consumed_at) -> RepoResult<DecryptAccess>
markExpiredBefore(ticket_expires_at, page_size) -> Page<DecryptAccess>
markRevoked(decrypt_access_id, reason_code) -> RepoResult<DecryptAccess>
```

#### 4.4.4 `DecryptDownloadAuthorizationRepository`

```text
createAuthorization(command, policy_snapshot) -> RepoResult<DecryptDownloadAuthorization>
findActiveBySubjectAndScope(subject_type, subject_id, scope_candidates, now) -> List<DecryptDownloadAuthorization>
revokeAuthorization(authorization_id, revoked_by, revoked_at, expected_status) -> RepoResult<DecryptDownloadAuthorization>
expireBefore(now, page_size) -> Page<DecryptDownloadAuthorization>
```

#### 4.4.5 `DecryptDownloadJobRepository`

```text
createRequested(command, request_idempotency_key) -> RepoResult<DecryptDownloadJob>
findByPlatformJobId(platform_job_id) -> DecryptDownloadJob?
findByRequesterAndIdempotency(requested_by, document_version_id, request_idempotency_key) -> DecryptDownloadJob?
markAuthorized(job_id, authorization_snapshot_ref, expected_status) -> RepoResult<DecryptDownloadJob>
markGenerating(job_id, platform_job_id, expected_status) -> RepoResult<DecryptDownloadJob>
markReady(job_id, export_artifact_ref, download_url_token, download_expires_at, expected_status) -> RepoResult<DecryptDownloadJob>
markDelivered(job_id, delivered_at, expected_status) -> RepoResult<DecryptDownloadJob>
markExpired(job_id, expected_status) -> RepoResult<DecryptDownloadJob>
markFailed(job_id, result_code, result_message, retryable_flag) -> RepoResult<DecryptDownloadJob>
findReadyExpiredBefore(now, page_size) -> Page<DecryptDownloadJob>
```

#### 4.4.6 `EncryptionAuditEventRepository`

```text
append(event) -> RepoResult<EncryptionAuditEvent>
findByDocument(document_asset_id, occurred_range, page_request) -> Page<EncryptionAuditEvent>
findByContract(contract_id, event_type, occurred_range, page_request) -> Page<EncryptionAuditEvent>
findByTraceId(trace_id) -> List<EncryptionAuditEvent>
```

#### 4.4.7 `CapabilityConsumePolicyRepository`

```text
findActiveByConsumerScene(consumer_code, access_scene) -> CapabilityConsumePolicy?
upsertPolicy(command, policy_version_no) -> RepoResult<CapabilityConsumePolicy>
disablePolicy(consume_policy_id, expected_version_no) -> RepoResult<CapabilityConsumePolicy>
```

---

## 5. 内部事件载荷治理与结构基线

### 5.1 治理边界

内部事件载荷治理的**核心对象**是 `event-payload`（事件载荷），定义模块内部事件的总线契约、载荷结构、版本管理。

内部事件载荷治理边界：

**`encrypted-document` 负责的边界：**
- 事件类型的定义（哪些动作必须产生内部事件）
- 事件载荷的结构规范（必须包含哪些字段、字段类型、可选性）
- 事件版本管理（载荷结构变更时的版本演进策略）
- 事件与审计的关联关系（事件如何映射到 `ed_encryption_audit_event`）

**`encrypted-document` 不负责的边界：**
- 事件发布/订阅的具体实现代码（由执行层负责）
- 消息队列选择、序列化协议、重试机制
- 事件消费方的内部处理逻辑

**`document-center` 的边界：**
- 持有文档对象、版本链、存储定位
- 可向加密模块发送"文档入库"、"版本切换"等事件
- 不感知加密模块内部事件载荷的具体结构

**`contract-core` 的边界：**
- 持有合同主档、业务状态、生命周期
- 不直接参与加密模块内部事件治理
- 可通过合同状态变更间接触发加密模块的事件响应

### 5.2 责任划分

内部事件载荷的责任划分：

| 责任维度 | `encrypted-document` 负责 | `document-center` 负责 | `contract-core` 负责 |
| --- | --- | --- | --- |
| 事件类型定义 | ✅ 加密模块内部事件类型 | ✅ 文档入库、版本切换事件 | ✅ 合同状态变更事件 |
| 载荷结构规范 | ✅ 必须字段、类型、版本 | ❌ 不感知 | ❌ 不感知 |
| 事件版本管理 | ✅ 载荷结构演进策略 | ❌ 不感知 | ❌ 不感知 |
| 事件发布实现 | ❌ 不负责（执行层负责） | ❌ 不负责 | ❌ 不负责 |
| 事件消费实现 | ❌ 不负责（执行层负责） | ❌ 不负责 | ❌ 不负责 |

### 5.3 事件载荷治理约束

内部事件载荷必须满足以下治理约束：

| 约束维度 | 治理要求 |
| --- | --- |
| 最小字段集 | 每个事件必须包含 `event_type`、`trace_id`、`actor_type`、`actor_id`、`occurred_at` |
| 关联对象 | 事件必须关联到 `security_binding_id`、`document_asset_id`、`contract_id`（如适用） |
| 版本兼容 | 载荷结构变更时必须保持向后兼容，或通过版本号明确不兼容边界 |
| 审计映射 | 每个内部事件必须能映射到 `ed_encryption_audit_event` 的具体记录 |
| 不泄漏密钥 | 事件载荷不得包含密钥材料、解密明文、临时凭证等敏感内容 |

### 5.4 核心事件类型

内部事件必须覆盖以下类型（与 `ed_encryption_audit_event.event_type` 对应）：

| 事件类型 | 触发源 | 载荷关键字段 |
| --- | --- | --- |
| `CHECK_IN_ACCEPTED` | 文档中心入库 | `check_in_id`、`document_version_id`、`trigger_type` |
| `ENCRYPT_SUCCEEDED` | 加密执行器 | `check_in_id`、`encryption_result_status` |
| `ENCRYPT_FAILED` | 加密执行器 | `check_in_id`、`result_code`、`result_message` |
| `DECRYPT_ACCESS_APPROVED` | 受控读取 | `decrypt_access_id`、`access_scene`、`consumption_mode` |
| `DECRYPT_ACCESS_DENIED` | 受控读取 | `decrypt_access_id`、`decision_reason_code` |
| `DOWNLOAD_AUTH_GRANTED` | 授权管理 | `authorization_id`、`subject_type`、`subject_id` |
| `DOWNLOAD_AUTH_REVOKED` | 授权管理 | `authorization_id`、`revoked_by` |
| `DOWNLOAD_REQUESTED` | 下载申请 | `decrypt_download_job_id`、`requested_by` |
| `DOWNLOAD_READY` | 导出作业 | `decrypt_download_job_id`、`export_file_name` |
| `DOWNLOAD_DELIVERED` | 下载交付 | `decrypt_download_job_id`、`download_url_token` |
| `DOWNLOAD_EXPIRED` | 过期清理 | `decrypt_download_job_id`、`download_expires_at` |
| `RECOVERY_REPLAYED` | 恢复流程 | `security_binding_id`、`recovery_type` |

### 5.5 内部事件载荷结构基线

所有内部事件载荷统一采用包络结构，敏感内容只允许保存为引用，不允许直接进入载荷。

```json
{
  "event_id": "{event_id}",
  "event_type": "ENCRYPT_SUCCEEDED",
  "payload_version": "1.0",
  "occurred_at": "{occurred_at}",
  "trace_id": "{trace_id}",
  "actor": {
    "actor_type": "SYSTEM",
    "actor_id": "encrypted-document-worker",
    "actor_department_id": null
  },
  "resource": {
    "security_binding_id": "{security_binding_id}",
    "document_asset_id": "{document_asset_id}",
    "document_version_id": "{document_version_id}",
    "contract_id": "{contract_id}",
    "related_resource_type": "ed_encryption_check_in",
    "related_resource_id": "{check_in_id}"
  },
  "payload": {
    "check_in_id": "{check_in_id}",
    "encryption_result_status": "ENCRYPTED",
    "result_code": "OK",
    "result_message": "加密成功"
  },
  "audit": {
    "audit_required": true,
    "audit_event_id": "{audit_event_id}",
    "event_payload_ref": "{event_payload_ref}"
  }
}
```

核心事件载荷差异字段如下：

| 事件类型 | `related_resource_type` | `payload` 必填字段 | 禁止字段 |
| --- | --- | --- | --- |
| `CHECK_IN_ACCEPTED` | `ed_encryption_check_in` | `check_in_id`、`document_version_id`、`trigger_type`、`idempotency_key` | 文件内容、密钥材料 |
| `ENCRYPT_SUCCEEDED` | `ed_encryption_check_in` | `check_in_id`、`encryption_result_status`、`content_fingerprint` | 密文内容、密钥材料 |
| `ENCRYPT_FAILED` | `ed_encryption_check_in` | `check_in_id`、`result_code`、`result_message`、`retryable_flag` | 异常堆栈全文、文件内容 |
| `DECRYPT_ACCESS_APPROVED` | `ed_decrypt_access` | `decrypt_access_id`、`access_scene`、`consumption_mode`、`ticket_expires_at` | `access_ticket` 明文值、解密内容 |
| `DECRYPT_ACCESS_DENIED` | `ed_decrypt_access` | `decrypt_access_id`、`decision_reason_code` | 权限内部规则全文 |
| `DOWNLOAD_AUTH_GRANTED` | `ed_decrypt_download_authorization` | `authorization_id`、`subject_type`、`subject_id`、`scope_type`、`effective_end_at` | 个人联系方式 |
| `DOWNLOAD_AUTH_REVOKED` | `ed_decrypt_download_authorization` | `authorization_id`、`revoked_by`、`revoked_at`、`revoke_reason_code` | 无关授权历史全文 |
| `DOWNLOAD_REQUESTED` | `ed_decrypt_download_job` | `decrypt_download_job_id`、`requested_by`、`document_version_id`、`request_idempotency_key` | 下载理由中的敏感附件 |
| `DOWNLOAD_READY` | `ed_decrypt_download_job` | `decrypt_download_job_id`、`export_artifact_ref`、`download_expires_at` | `download_url_token` 明文值、导出明文 |
| `DOWNLOAD_DELIVERED` | `ed_decrypt_download_job` | `decrypt_download_job_id`、`delivered_at`、`delivery_channel` | 客户端本地路径 |
| `DOWNLOAD_EXPIRED` | `ed_decrypt_download_job` | `decrypt_download_job_id`、`download_expires_at` | 过期令牌明文值 |
| `RECOVERY_REPLAYED` | `platform_job` 或本模块主表 | `recovery_type`、`source_status`、`target_status`、`replay_result` | 敏感诊断明细 |

---

## 6. 任务重试参数治理

### 6.1 治理对象

任务重试参数治理的**核心对象**是 `retry-policy`（重试策略），定义任务失败后的重试行为、退避策略、终止条件。

| 治理对象 | 归属主体 | 说明 |
| --- | --- | --- |
| `retry-policy` | `encrypted-document` | 重试策略，定义重试次数、间隔、退避算法 |
| `retry-spec` | `encrypted-document` | 重试规格，描述具体参数的取值与约束 |
| `retry-history` | `encrypted-document` | 重试历史，记录每次重试的时间、结果、原因 |

任务重试参数**不包含**具体重试代码实现、具体调度器配置、具体线程池参数——这些属于执行层，不在治理设计范围内。

### 6.2 任务类型与重试策略对应关系

任务重试策略必须按任务类型分别定义：

| 任务类型 | 重试策略编码 | 最大重试次数 | 退避算法 | 终止条件 |
| --- | --- | --- | --- | --- |
| `ED_ENCRYPTION_CHECK_IN` | `retry-encrypt` | 5 | 指数退避 | 终态失败、人工介入 |
| `ED_DECRYPT_DOWNLOAD_EXPORT` | `retry-export` | 3 | 固定间隔 | 终态失败、人工介入 |
| `ED_DOWNLOAD_EXPIRE_CLEANUP` | `retry-cleanup` | 8 | 固定间隔 | 达到最大重试次数 |
| `ED_SECURITY_RECHECK` | `retry-recheck` | 4 | 指数退避 | 终态失败、人工介入 |

### 6.3 重试参数治理约束

任务重试参数必须满足以下治理约束：

| 约束维度 | 治理要求 |
| --- | --- |
| 任务类型隔离 | 不同任务类型必须配置独立的重试策略，不共享重试计数器 |
| 退避可配置 | 退避算法、初始间隔、最大间隔必须可配置，不允许硬编码 |
| 终止条件明确 | 必须定义重试终止条件（最大次数、终态判定、人工介入标记） |
| 重试可审计 | 每次重试必须形成审计事件，记录重试次数、结果、原因 |
| 不覆盖业务判定 | 重试只解决临时性故障（网络、超时、资源），不重试业务终态失败 |

### 6.4 重试参数默认值

| 重试策略编码 | 初始间隔 | 最大间隔 | 抖动 | 单次执行超时 | 最大重试次数 | 转人工条件 |
| --- | --- | --- | --- | --- | --- | --- |
| `retry-encrypt` | 30 秒 | 15 分钟 | 20% | 10 分钟 | 5 | 连续失败 5 次或 `FAILED_TERMINAL` |
| `retry-export` | 60 秒 | 5 分钟 | 10% | 15 分钟 | 3 | 连续失败 3 次、授权快照失效或导出介质异常 |
| `retry-cleanup` | 5 分钟 | 30 分钟 | 10% | 3 分钟 | 8 | 连续失败 8 次 |
| `retry-recheck` | 2 分钟 | 30 分钟 | 20% | 10 分钟 | 4 | 连续失败 4 次或绑定进入 `SUSPENDED` |

默认值使用原则：

- 业务终态失败不重试，包括权限不足、授权未命中、合同状态不允许下载、文档版本已失效。
- 可重试失败只覆盖网络超时、任务执行器临时不可用、对象存储短暂不可达、平台任务中心心跳丢失。
- 每次重试必须写入 `ed_encryption_audit_event` 或平台任务中心尝试记录，二者至少有一个可被 `trace_id` 串联。

### 6.5 与平台任务中心的边界

任务重试参数与平台任务中心的边界：

- **平台任务中心**负责：统一任务状态机、调度器、心跳机制、全局重试配置
- **`encrypted-document`** 负责：加密模块专属任务类型的重试策略定义、业务重试判定、重试审计
- **治理原则**：平台任务中心提供基础重试能力，`encrypted-document` 在此基础上定义业务重试策略，不依赖平台默认配置

---

## 7. 告警阈值治理

### 7.1 治理对象

告警阈值治理的**核心对象**是 `alert-threshold`（告警阈值），定义监控指标的告警触发条件、严重级别、通知路由。

| 治理对象 | 归属主体 | 说明 |
| --- | --- | --- |
| `alert-threshold` | `encrypted-document` | 告警阈值，定义指标、阈值、严重级别 |
| `alert-spec` | `encrypted-document` | 告警规格，描述阈值类型、比较算子、持续时间 |
| `alert-route` | `encrypted-document` | 告警路由，定义告警通知的接收者、通道、抑制策略 |

告警阈值**不包含**具体监控采集代码、具体告警通知代码、具体仪表盘配置——这些属于执行层或运维层，不在治理设计范围内。

### 7.2 监控维度与告警阈值对应关系

告警阈值必须按监控维度分别定义：

| 监控维度 | 告警阈值编码 | 指标示例 | 严重级别 | 通知路由 |
| --- | --- | --- | --- | --- |
| 自动加密 | `alert-encrypt` | 成功率、平均耗时、失败率 | `WARN`、`ERROR`、`CRITICAL` | 安全运维、模块负责人 |
| 受控访问 | `alert-access` | 批准率、拒绝率、过期率 | `INFO`、`WARN`、`ERROR` | 安全运维、权限管理员 |
| 解密下载 | `alert-download` | 申请量、授权命中率、导出成功率 | `WARN`、`ERROR`、`CRITICAL` | 安全运维、合规负责人 |
| 恢复重放 | `alert-recovery` | 重放次数、人工介入次数 | `WARN`、`ERROR` | 模块负责人、值班人员 |
| 悬挂任务 | `alert-hanging` | 悬挂任务数、超时任务数 | `WARN`、`ERROR`、`CRITICAL` | 值班人员、平台任务中心负责人 |

### 7.3 告警阈值治理约束

告警阈值必须满足以下治理约束：

| 约束维度 | 治理要求 |
| --- | --- |
| 指标可量化 | 告警阈值必须基于可量化指标，不允许基于主观判断 |
| 级别可区分 | 必须定义不同严重级别（如 INFO、WARN、ERROR、CRITICAL） |
| 路由可配置 | 不同严重级别的告警必须可配置通知路由，不允许硬编码接收者 |
| 抑制可定义 | 必须定义告警抑制策略（如同一指标短时间内不重复告警） |
| 阈值可审计 | 告警阈值的变更（新增、修改、禁用）必须形成治理事件 |

### 7.4 告警规则样例

| 告警编码 | 指标 | 触发条件 | 持续时间 | 严重级别 | 抑制策略 | 建议动作 |
| --- | --- | --- | --- | --- | --- | --- |
| `alert-encrypt-success-rate-low` | `ed.encryption.success_rate` | 最近 15 分钟成功率低于 98% | 15 分钟 | `ERROR` | 30 分钟同指标抑制 | 检查加密执行器、对象存储、平台任务中心 |
| `alert-encrypt-failed-terminal-spike` | `ed.encryption.failed_terminal_count` | 最近 10 分钟终态失败大于 10 | 10 分钟 | `CRITICAL` | 15 分钟同指标抑制 | 暂停批量受理并检查输入文件类型、策略配置 |
| `alert-access-denied-rate-high` | `ed.decrypt_access.denied_rate` | 最近 30 分钟拒绝率高于历史同周期 3 倍 | 30 分钟 | `WARN` | 60 分钟同指标抑制 | 检查权限策略、合同归属同步、消费方场景配置 |
| `alert-download-export-failed` | `ed.download.export_failed_count` | 最近 15 分钟导出失败大于 5 | 15 分钟 | `ERROR` | 30 分钟同指标抑制 | 检查导出作业、授权快照、临时介质 |
| `alert-download-ready-expired-backlog` | `ed.download.ready_expired_backlog` | 待过期清理作业大于 100 | 20 分钟 | `WARN` | 60 分钟同指标抑制 | 检查过期清理任务与平台任务调度 |
| `alert-recovery-manual-required` | `ed.recovery.waiting_manual_count` | 待人工恢复数大于 0 | 5 分钟 | `ERROR` | 30 分钟同指标抑制 | 安排安全运维处理恢复队列 |
| `alert-job-hanging-critical` | `ed.platform_job.hanging_count` | 悬挂任务大于 20 或最老任务超过 30 分钟 | 10 分钟 | `CRITICAL` | 15 分钟同指标抑制 | 检查任务租约、执行器心跳、数据库锁等待 |

### 7.5 与平台监控中心的边界

告警阈值与平台监控中心的边界：

- **平台监控中心**负责：统一指标采集、存储、查询、仪表盘展示、告警引擎
- **`encrypted-document`** 负责：加密模块专属指标的告警阈值定义、严重级别划分、通知路由配置
- **治理原则**：平台监控中心提供基础告警能力，`encrypted-document` 在此基础上定义业务告警阈值，不依赖平台默认配置

---

## 8. 与前六份专项设计的引用关系

本文档与 [`crypto-algorithm-and-key-hierarchy-design.md`](crypto-algorithm-and-key-hierarchy-design.md)、[`controlled-read-handle-design.md`](controlled-read-handle-design.md)、[`plaintext-export-package-design.md`](plaintext-export-package-design.md)、[`authorization-scope-expression-design.md`](authorization-scope-expression-design.md)、[`desensitization-and-secondary-storage-design.md`](desensitization-and-secondary-storage-design.md)、[`consumer-adaptation-and-pressure-test-design.md`](consumer-adaptation-and-pressure-test-design.md) 的引用关系：

### 8.1 架构层级关系

```
加密算法与密钥层级专项设计（第一份）
  └── encryption-profile（加密方案）
        └── controlled-read-handle-design（第二份）
              └── read-handle（读取句柄）
                    └── plaintext-export-package-design（第三份）
                          └── export-package（导出包）
                                └── authorization-scope-expression-design（第四份）
                                      └── authorization-scope（授权范围）
                                            └── desensitization-and-secondary-storage-design（第五份）
                                                  └── desensitization-policy（脱敏策略）
                                                  └── secondary-storage-policy（二次存储策略）
                                                        └── consumer-adaptation-and-pressure-test-design（第六份）
                                                              └── consumer-adapter（消费方适配器）
                                                              └── pressure-test-policy（压力测试策略）
                                                                    └── ddl-event-and-retry-parameter-design（本文档，第七份）
                                                                          └── ddl-event（DDL 事件）
                                                                          └── repository-interface（仓储接口）
                                                                          └── event-payload（内部事件载荷）
                                                                          └── retry-policy（任务重试参数）
                                                                          └── alert-threshold（告警阈值）
```

### 8.2 治理依赖关系

| 本文档依赖前六份的内容 | 说明 |
| --- | --- |
| `encryption-profile` | DDL 事件必须覆盖加密方案相关表的变更；告警阈值必须监控加密模块整体健康度 |
| `read-handle` | 仓储接口必须支持读取句柄的查询、更新；内部事件载荷必须覆盖受控访问事件 |
| `export-package` | DDL 事件必须覆盖导出包相关表的变更；任务重试参数必须覆盖导出作业的重试策略 |
| `authorization-scope` | 仓储接口必须支持授权规则的查询、更新；内部事件载荷必须覆盖授权变更事件 |
| `desensitization-policy` | DDL 事件必须覆盖脱敏策略相关表的变更；告警阈值必须监控脱敏执行健康度 |
| `consumer-adapter` | 任务重试参数必须覆盖消费方适配器的重试策略；告警阈值必须监控消费方健康度 |

### 8.3 治理边界划分

- **第一份专项设计**负责：加密算法选择、密钥层级划分、密钥轮换策略、介质保护和密钥托管
- **第二份专项设计**负责：读取句柄治理、流式分段策略治理、临时缓存介质治理
- **第三份专项设计**负责：导出包治理、水印选项治理、脱敏选项治理、文件名规范治理
- **第四份专项设计**负责：授权范围表达式治理、规则引擎治理、范围匹配策略治理
- **第五份专项设计**负责：脱敏策略治理、二次存储治理、索引文本治理
- **第六份专项设计**负责：消费方适配器治理、压力测试策略治理、性能指标治理
- **本文档（第七份）**负责：表结构变更治理、核心表与索引实现基线、仓储接口签名、内部事件载荷结构、任务重试参数、告警阈值治理
- **七份文档的共同约束**：都不持有文档明文内容、都不编写实现代码、都保持与 `document-center` 和 `contract-core` 的松耦合

---

## 9. 与 `contract-core`、`document-center` 的边界说明

### 9.1 与 `document-center` 的边界

| 边界维度 | `document-center` 负责 | `encrypted-document` 负责 |
| --- | --- | --- |
| 文件真相 | 持有文档资产、版本链、存储定位 | 不持有文件真相，只引用 `document_asset_id` |
| 版本管理 | 管理文档版本的新增、切换、失效 | 只感知当前版本，不管理版本生命周期 |
| 表结构变更 | 独立治理自身表结构的 DDL 事件 | 独立治理加密模块表结构的 DDL 事件，互不影响 |
| 仓储接口 | 独立定义自身仓储接口 | 独立定义加密模块仓储接口，不共享实现 |
| 内部事件 | 可向加密模块发送"文档入库"、"版本切换"事件 | 定义内部事件载荷规范，接收并响应文档中心事件 |
| 告警监控 | 独立监控文档中心健康度 | 独立监控加密模块健康度，不交叉告警 |

引用关系：`document-center` ← `document_asset_id` ← `encrypted-document`（加密模块）
DDL 事件关系：两个模块的 DDL 事件独立治理，不共享 DDL 事件标识

### 9.2 与 `contract-core` 的边界

| 边界维度 | `contract-core` 负责 | `encrypted-document` 负责 |
| --- | --- | --- |
| 合同主档 | 持有合同业务状态、生命周期 | 不持有合同主档，只引用 `contract_id` |
| 业务权限 | 管理合同的业务级权限 | 只读取权限结果，不管理业务权限 |
| 表结构变更 | 独立治理自身表结构的 DDL 事件 | 独立治理加密模块表结构的 DDL 事件，互不影响 |
| 仓储接口 | 独立定义自身仓储接口 | 独立定义加密模块仓储接口，不共享实现 |
| 内部事件 | 可通过合同状态变更间接触发加密模块事件响应 | 定义内部事件载荷规范，不直接依赖合同主档事件 |
| 告警监控 | 独立监控合同核心健康度 | 独立监控加密模块健康度，不交叉告警 |

引用关系：`contract-core` ← `contract_id` ← `encrypted-document`（加密模块）
DDL 事件关系：两个模块的 DDL 事件独立治理，不共享 DDL 事件标识

### 9.3 跨模块引用原则

- **自上而下引用**：合同 → 文档 → 加密方案 → 读取句柄 → 导出包 → 授权范围 → 脱敏策略 → 消费方适配器 → DDL 事件/重试参数
- **禁止反向依赖**：DDL 事件、仓储接口、内部事件载荷、重试参数不持有合同主档引用，不持有文档内容引用
- **松耦合**：`ddl-event-id`、`repository-interface-id`、`event-payload-id` 是模块内稳定键，各模块不直接依赖对方内部模型
- **治理隔离**：`contract-core` 不感知加密模块的 DDL 事件，`document-center` 不感知加密模块的仓储接口细节

---

## 10. 审计最小单元

### 10.1 DDL 事件审计最小单元

DDL 事件审计最小单元是 **`ddl-event` + `ddl-spec` + `ddl-impact`**，审计必须覆盖：

| 审计要素 | 说明 |
| --- | --- |
| DDL 事件标识 | `ddl-event-id` |
| DDL 规格标识 | `ddl-spec-id` |
| 事件类型 | `TABLE_CREATE` / `TABLE_ALTER` / `INDEX_CREATE` / 等 |
| 目标表 | 受影响的表名（不记录表结构详情） |
| 变更摘要 | 变更内容的治理摘要（不记录 DDL 语句） |
| 操作主体 | 操作人 / 系统 / 迁移脚本 |
| 操作时间 | 操作时间戳 |
| 影响范围 | 受影响的字段、索引、约束（不展开到具体数据） |

### 10.2 仓储接口审计最小单元

仓储接口审计最小单元是 **`repository-interface` + 方法调用记录**，审计必须覆盖：

| 审计要素 | 说明 |
| --- | --- |
| 接口标识 | `repository-interface-id` |
| 方法名称 | 调用的仓储方法名 |
| 操作类型 | 查询 / 写入 / 更新 / 删除 |
| 关联对象 | `security_binding_id` / `document_asset_id`（如适用） |
| 操作结果 | 成功 / 幂等重复 / 并发冲突 / 失败 |
| 操作主体 | 操作人 / 系统 / 内部服务 |
| 操作时间 | 操作时间戳 |

### 10.3 内部事件载荷审计最小单元

内部事件载荷审计最小单元是 **`event-payload` + 事件发布记录**，审计必须覆盖：

| 审计要素 | 说明 |
| --- | --- |
| 事件类型 | `event_type`（与 `ed_encryption_audit_event` 对应） |
| 事件载荷版本 | `payload-version` |
| 追踪标识 | `trace_id` |
| 关联对象 | `security_binding_id`、`document_asset_id`、`contract_id` |
| 发布主体 | 操作人 / 系统 / 内部服务 |
| 发布时间 | 事件时间戳 |
| 映射审计事件 | 对应的 `audit_event_id` |

### 10.4 任务重试参数审计最小单元

任务重试参数审计最小单元是 **`retry-policy` + 重试执行记录**，审计必须覆盖：

| 审计要素 | 说明 |
| --- | --- |
| 重试策略编码 | `retry-policy-code` |
| 任务类型 | `ED_ENCRYPTION_CHECK_IN` / `ED_DECRYPT_DOWNLOAD_EXPORT` / 等 |
| 重试次数 | 当前重试次数 |
| 重试结果 | 成功 / 失败 / 终止 |
| 重试原因 | 超时 / 网络错误 / 资源不足 / 等 |
| 重试时间 | 每次重试的时间戳 |
| 关联作业 | `platform_job_id` / `decrypt_download_job_id` |

### 10.5 告警阈值审计最小单元

告警阈值审计最小单元是 **`alert-threshold` + 告警触发记录**，审计必须覆盖：

| 审计要素 | 说明 |
| --- | --- |
| 告警阈值编码 | `alert-threshold-code` |
| 监控维度 | 自动加密 / 受控访问 / 解密下载 / 恢复重放 / 悬挂任务 |
| 告警级别 | INFO / WARN / ERROR / CRITICAL |
| 触发指标 | 指标名称（不记录具体指标值） |
| 触发时间 | 告警时间戳 |
| 通知路由 | 告警接收者 / 通道（不记录接收者联系方式） |
| 阈值变更 | 新增 / 修改 / 禁用 / 删除 |

### 10.6 审计关联约束

- 审计事件只记录治理动作，不记录 DDL 执行细节、仓储实现细节、事件序列化细节、重试逻辑细节、告警通知细节
- 审计事件中的标识不包含敏感内容（密钥材料、明文内容、凭证等），只保留 ID 和状态
- 审计事件与 `contract-core`、`document-center` 的关联通过 `document_asset_id` 或 `contract_id` 间接建立，不直接引用合同或文档内部状态
- 审计事件保留期限按合规要求设定，与 DDL 事件、任务、告警的生命周期解耦

---

## 11. 与总平台架构的对应关系

本文档的治理设计基于以下总平台架构约束：

| 架构约束来源 | 对应章节 | 本文档遵循方式 |
| --- | --- | --- |
| `architecture-design.md` 5.4 总平台与子模块边界 | §5.4 | 加密模块不拥有文件真相源和合同主档，只持有安全治理状态 |
| `architecture-design.md` 8.2 自研子模块边界 | §8.2 | 加密模块挂在文档中心读写路径上，是平台内正式子模块 |
| `detailed-design.md` 2.2 约束落点 | §2.2 | 加密子模块不拥有文件真相源和合同主档，只持有安全治理状态与审计事实 |
| `detailed-design.md` 10. 异步任务、补偿与恢复 | §10. | 任务重试参数治理承接异步任务与恢复模型的治理要求 |
| `detailed-design.md` 11. 审计、日志、指标与恢复边界 | §11. | 告警阈值治理承接指标与恢复的监控要求 |
| `detailed-design.md` 4. 核心物理表设计 | §3. | 核心表结构与索引实现基线承接表级下沉要求 |
| `detailed-design.md` 9. 缓存、锁、幂等与并发控制 | §4. | 仓储接口签名基线承接数据访问的治理边界 |
| `crypto-algorithm-and-key-hierarchy-design.md` | 第一份专项 | DDL 事件必须覆盖加密方案相关表的变更 |
| `controlled-read-handle-design.md` | 第二份专项 | 仓储接口必须支持读取句柄的查询、更新 |
| `plaintext-export-package-design.md` | 第三份专项 | 任务重试参数必须覆盖导出作业的重试策略 |
| `authorization-scope-expression-design.md` | 第四份专项 | 仓储接口必须支持授权规则的查询、更新 |
| `desensitization-and-secondary-storage-design.md` | 第五份专项 | DDL 事件必须覆盖脱敏策略相关表的变更 |
| `consumer-adaptation-and-pressure-test-design.md` | 第六份专项 | 任务重试参数必须覆盖消费方适配器的重试策略 |

---

## 12. 本文档边界

### 12.1 本文保留的内容

作为表结构变更治理与实现基线设计，本文保留以下内容：
- DDL 事件的治理对象、稳定锚点、版本归属、最小治理单元
- 核心表结构草案、索引清单、表结构变更治理与实现基线关系
- 仓储接口的治理边界、责任划分、接口签名基线
- 内部事件载荷的治理边界、责任划分、核心事件类型、载荷结构基线
- 任务重试参数的治理对象、与任务类型的对应关系、默认值
- 告警阈值的治理对象、与监控维度的对应关系、告警规则样例
- 与前六份专项设计的引用关系
- 与 `contract-core`、`document-center` 的边界说明
- DDL 事件/仓储接口/内部事件载荷/任务重试参数/告警阈值的审计最小单元

### 12.2 下沉到实现层的内容

以下内容在后续实现层展开，不在本文设计：
- 具体 DDL 语句（CREATE TABLE、ALTER TABLE 等）与数据库方言脚本
- 仓储接口的具体实现代码（DAO、Repository、ORM 映射）
- 内部事件的具体发布/订阅代码、序列化协议实现
- 任务重试的具体逻辑代码、调度器配置代码
- 告警阈值的具体监控采集代码、告警通知代码、仪表盘配置代码
- 与 `document-center`、`contract-core` 的具体挂接接口与调用时序

### 12.3 排除内容

本文不包含以下内容：
- API 路径设计与请求/响应结构（已在 `api-design.md` 定义）
- DDL 代码与表结构创建脚本（本文只保留表草案、索引清单和约束方向）
- 代码实现与算法细节
- 实施排期与工时估算
- 运维手册与故障排查指南
- 具体参数代码、告警规则配置代码、监控采集代码

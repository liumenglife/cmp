# 文档中心与文档协作子模块 Detailed Design

## 1. 文档说明

本文档是 `CMP` 文档中心与文档协作子模块的第一份正式
`Detailed Design`。

### 1.1 输入

- 上游需求基线：[`Requirement Spec`](../../../specifications/cmp-phase1-requirement-spec.md)
- 总平台架构基线：[`Architecture Design`](../../architecture-design.md)
- 总平台接口基线：[`API Design`](../../api-design.md)
- 总平台共享内部基线：[`Detailed Design`](../../detailed-design.md)
- 子模块架构基线：[`文档中心与文档协作子模块 Architecture Design`](./architecture-design.md)
- 子模块接口基线：[`文档中心与文档协作子模块 API Design`](./api-design.md)

### 1.2 输出

- 本文：[`Detailed Design`](./detailed-design.md)
- 供后续实现、专项设计与表级 DDL 定稿使用的内部设计基线

### 1.3 阅读边界

本文只写文档中心 / 文档协作子模块的内部实现层设计，重点回答：

- 子模块在代码与数据层如何拆分
- 文件对象真相、版本链、预览、批注、修订如何内部建模
- `OCR` / 搜索 / 签章 / 加密 / 归档如何挂接到同一文档对象
- 解密下载授权、作业、审计、恢复如何落地
- 缓存、锁、幂等、并发、补偿、恢复如何控制

本文不承担以下内容：

- 不复述需求范围、功能清单、验收口径
- 不重写总平台架构总览、模块拓扑或对外资源清单
- 不写 `OpenAPI` 级字段样例、错误码全集或外部回调报文
- 不写实施排期、工时、里程碑、负责人拆分

## 2. 设计目标与约束落点

### 2.1 设计目标

- 让文档中心成为 `CMP` 内唯一文件对象真相源
- 让文档协作建立在文档中心之上，不生成第二文件真相源
- 让合同正文、附件、签章稿、归档稿、修订候选稿都纳入同一版本治理体系
- 让 `OCR`、搜索、签章、加密、归档消费同一 `DocumentVersion`，而不是各自消费私有附件
- 让默认读取路径保持“平台内受控使用”，管理端授权的解密下载作为受控例外
- 让文档状态变更可追踪、可恢复、可审计，而不把恢复责任交给缓存或外部索引

### 2.2 约束落点

- 文档中心是文件对象真相源：正式文件必须先进入文档中心，其他模块只能引用
- 文档协作建立在其上：批注、修订、评论、采纳都依附文档中心版本链
- 其他模块不能各自长出文件真相源：签章、归档、搜索、`OCR` 只允许保留派生数据或读模型
- 加密模块挂在文档中心写入 / 读取路径上：写入即校验与加密，读取按权限受控自动解密
- 默认不可脱离平台使用，但管理端可按部门、人员授权解密下载
- 导出的明文文件可脱离 `CMP` 使用，但该能力只通过受控授权与受控作业完成
- 不生成第二文件真相源：预览产物、索引文本、`OCR` 结果、签章结果摘要都不是主文件真相

## 3. 模块内部拆分

内部实现按八个组件拆分，所有组件共享统一的 `document_asset_id` /
`document_version_id` 主键体系。

### 3.1 `asset-registry`

- 持有 `DocumentAsset` 主记录
- 管理业务绑定、文档角色、当前主版本、当前可读版本摘要
- 对外只暴露稳定内部服务，不允许其他模块直接写表

### 3.2 `version-chain`

- 持有版本链追加、版本切换、父子版本关系、来源关系
- 负责“当前主版本”切换时的事务一致性
- 负责阻止同一文档对象出现两个激活主版本

### 3.3 `preview-runtime`

- 持有预览派生域总入口、页级摘要、渲染状态
- 负责原文件到预览代次、页级切片、文本层快照、校正对象等正式派生产物的生命周期
- 负责旧预览失效与新预览激活

### 3.4 `annotation-anchor`

- 持有批注主记录、锚点正式对象、锚点重定位、处理状态
- 负责批注与版本视图的附着关系
- 负责版本切换后锚点失效 / 重定位判断

### 3.5 `revision-workspace`

- 持有修订记录、候选版本与采纳 / 驳回状态
- 负责协作过程中的版本替换意图与基线版本关系
- 负责修订采纳后触发版本链推进

### 3.6 `capability-adapter`

- 统一挂接 `OCR`、搜索、签章、加密、归档
- 对每种能力只保存“挂接记录 + 输入版本 + 结果摘要 + 任务引用”
- 不接管各能力域内部算法或业务规则

### 3.7 `decrypt-control`

- 持有解密下载授权、授权匹配、下载作业、导出介质引用
- 负责“默认不可脱离平台使用”的主路径约束
- 负责管理端按部门、人员授予“解密下载”例外能力

### 3.8 `document-read-model`

- 负责聚合只读视图，如文档绑定视图、协作摘要视图
- 采用聚合缓存表而不是运行时跨多表大查询
- 聚合视图失效可重建，但不承担真相源职责

## 4. 核心物理表设计

本节只列子模块核心物理表与必要挂接表，不展开完整 DDL。
全部表默认包含基础审计字段：`created_at`、`created_by`、`updated_at`、
`updated_by`、`is_deleted`。

### 4.1 `dc_document_asset`

用途：文件对象主档，是文档中心文件真相的入口根表。

- 关键主键：`document_asset_id`
- 关键字段：
  - `owner_type`、`owner_id`：业务归属，首批以 `CONTRACT` 为主
  - `document_role`：`MAIN_BODY`、`ATTACHMENT`、`SIGNATURE_COPY`、`ARCHIVE_COPY`、`SUPPLEMENT`
  - `document_title`
  - `current_version_id`
  - `latest_version_no`
  - `document_status`
  - `encryption_status`
  - `preview_status`
  - `source_channel`
  - `content_fingerprint`：当前主版本内容摘要，便于幂等判重与对比
  - `access_scope_code`：文档访问域 / 密级摘要
- 关键索引 / 唯一约束：
  - `idx_owner_role(owner_type, owner_id, document_role)`
  - `idx_owner_status(owner_type, owner_id, document_status)`
  - `idx_current_version(current_version_id)`
  - `idx_encryption_status(encryption_status, document_status)`
- 关联对象：`Contract`、`dc_document_version`、`dc_binding_view_cache`

设计说明：

- 一份正式文件对象只允许有一个 `DocumentAsset` 根记录
- 当前主版本指针只保存在本表，不复制到其他业务模块
- 业务模块禁止自建附件主表承接正式文件真相

### 4.2 `dc_document_version`

用途：承载文件内容版本链，是文件内容真相的版本级节点表。

- 关键主键：`document_version_id`
- 关键字段：
  - `document_asset_id`
  - `version_no`
  - `parent_version_id`
  - `base_version_id`：修订时的基线版本
  - `version_label`
  - `change_reason`
  - `version_status`：`DRAFT`、`ACTIVE`、`SUPERSEDED`、`REJECTED`、`ARCHIVED`
  - `storage_provider`、`storage_bucket`、`storage_object_key`
  - `storage_content_hash`
  - `file_name`、`mime_type`、`file_size_bytes`
  - `encryption_profile_code`
  - `preview_generation_status`
  - `capability_state_mask`：用于汇总 `OCR`、索引、签章输入、归档输入是否已就绪
  - `origin_type`：`MANUAL_UPLOAD`、`SYSTEM_GENERATED`、`SIGNATURE_OUTPUT`、`ARCHIVE_OUTPUT`
- 关键索引 / 唯一约束：
  - `uk_asset_version(document_asset_id, version_no)`
  - `uk_storage_object(storage_provider, storage_bucket, storage_object_key)`
  - `idx_parent_version(parent_version_id)`
  - `idx_base_version(base_version_id)`
  - `idx_asset_status(document_asset_id, version_status)`
  - `idx_content_hash(storage_content_hash)`
- 关联对象：`dc_document_asset`、`dc_preview_artifact`、预览代次 / 页级切片 / 文本层快照 / 校正对象、`dc_annotation`、`dc_annotation_anchor`、`dc_revision`

设计说明：

- 版本号在同一 `document_asset_id` 内单调递增，不回收、不重排
- 内容哈希用于去重、断点恢复与派生任务判重
- 签章稿、归档稿、系统生成稿仍然回收到本表，而不是落到子模块私有文件表
- `storage_provider`、`storage_bucket`、`storage_object_key` 等字段只承接当前有效正式对象摘要，不承担历史物理对象链路的展开
- 历史物理对象引用、冷热迁移记录、恢复源登记、删除评估与清理证明，统一由对象生命周期正式记录与审计事件承接；`dc_document_version` 不允许以覆盖式更新吞掉这些留痕

### 4.3 `dc_preview_artifact`

用途：登记某个版本在预览派生域中的总入口或兼容总览摘要，仅代表受控视图，不代表新文件真相，也不再单独代表完整正式预览模型。

- 关键主键：`preview_artifact_id`
- 关键字段：
  - `document_version_id`
  - `preview_type`：`PDF_VIEW`、`IMAGE_TILESET`、`TEXT_LAYER`、`PAGE_SNAPSHOT`
  - `artifact_status`：`PENDING`、`GENERATING`、`READY`、`FAILED`、`STALE`
  - `artifact_storage_key`
  - `page_count`
  - `render_engine`
  - `render_checksum`
  - `generated_job_id`
  - `expires_at`：仅用于临时访问令牌，不用于控制产物生命周期
- 关键索引 / 唯一约束：
  - `uk_version_preview(document_version_id, preview_type, render_checksum)`
  - `idx_version_status(document_version_id, artifact_status)`
  - `idx_generated_job(generated_job_id)`
- 关联对象：`dc_document_version`、`platform_job`

设计说明：

- `dc_preview_artifact` 只承担总入口、兼容引用和聚合摘要职责，避免父文档继续把单表误读为完整正式模型
- 预览派生域的正式模型以下游专项设计为准，由预览代次、页级切片、文本层快照、文本层校正对象共同组成；`dc_preview_artifact` 仅用于汇总当前默认暴露代次及其基础状态摘要
- 旧主版本被替换后，其关联预览总览对象可保留，但状态改为 `STALE`

### 4.3.1 `dc_preview_generation`

用途：承接预览代次正式对象，表达某个文档版本在某个阅读场景下的一次受控预览派生。

- 关键主键：`preview_generation_id`
- 关键字段：
  - `document_version_id`
  - `preview_artifact_id`：总入口 / 兼容总览引用，可为空但就绪后必须回填
  - `preview_profile_code`：`STANDARD_READING`、`SIGNATURE_PRECHECK`、`MOBILE_READING`
  - `preview_generation_no`
  - `render_checksum`
  - `coordinate_space_version`
  - `preview_status`：`PENDING`、`GENERATING`、`READY`、`FAILED`、`STALE`
  - `text_layer_status`：`ABSENT`、`READY`、`CORRECTED`、`STALE`
  - `page_count`
  - `is_current_effective`：同一版本同一场景当前有效代次标识
  - `fallback_generation_id`
  - `generated_job_id`
- 关键索引 / 唯一约束：
  - `uk_version_profile_generation(document_version_id, preview_profile_code, preview_generation_no)`
  - `uk_version_profile_current(document_version_id, preview_profile_code, is_current_effective)`，其中 `is_current_effective=1` 时同一 `document_version_id + preview_profile_code` 只允许一条
  - `idx_generation_status(document_version_id, preview_profile_code, preview_status)`
  - `idx_render_checksum(render_checksum)`
- 关联对象：`dc_document_version`、`dc_preview_artifact`、`dc_preview_page_slice`、`dc_text_layer_snapshot`、`platform_job`

设计说明：

- 预览代次是预览派生域的版本级正式对象，不替代 `DocumentVersion`
- 当前有效代次必须通过唯一约束保证，不能只依赖应用层先查后改
- 新代次接管默认暴露资格时，旧代次状态改为 `STALE`，同时 `is_current_effective` 置为 `0`

### 4.3.2 `dc_preview_page_slice`

用途：承接页级切片正式对象，表达某个预览代次下的稳定页坐标系、页内容摘要与页级渲染引用。

- 关键主键：`page_slice_id`
- 关键字段：
  - `preview_generation_id`
  - `document_version_id`
  - `preview_profile_code`
  - `preview_generation_no`
  - `page_no`
  - `page_render_ref`
  - `page_width`、`page_height`
  - `page_rotation`
  - `page_checksum`
  - `page_status`：`READY`、`FAILED`、`STALE`
- 关键索引 / 唯一约束：
  - `uk_generation_page(preview_generation_id, page_no)`
  - `uk_version_generation_page(document_version_id, preview_profile_code, preview_generation_no, page_no)`
  - `idx_page_status(preview_generation_id, page_status)`
  - `idx_page_checksum(page_checksum)`
- 关联对象：`dc_preview_generation`、`dc_text_layer_snapshot`、`dc_annotation_anchor`

设计说明：

- 页号只在同一 `preview_generation_id` 内保证唯一且单调，不跨代次复用语义
- 批注、签章坐标和文本高亮必须引用页级切片的坐标系摘要，不能各自计算私有页坐标

### 4.3.3 `dc_text_layer_snapshot`

用途：承接文本层快照正式对象，表达某个预览代次某一页上可定位、可选区、可高亮的文本层基础快照。

- 关键主键：`text_layer_snapshot_id`
- 关键字段：
  - `page_slice_id`
  - `preview_generation_id`
  - `document_version_id`
  - `page_no`
  - `text_layer_checksum`
  - `text_source_type`：`EMBEDDED_TEXT`、`LAYOUT_EXTRACTION`、`OCR_ASSISTED`
  - `token_span_count`
  - `coordinate_space_version`
  - `correction_state`：`NONE`、`HAS_ACTIVE_CORRECTION`、`STALE`
  - `snapshot_storage_ref`
- 关键索引 / 唯一约束：
  - `uk_page_text_snapshot(page_slice_id)`
  - `uk_generation_page_text(preview_generation_id, page_no)`
  - `idx_text_checksum(text_layer_checksum)`
  - `idx_text_source(document_version_id, text_source_type)`
- 关联对象：`dc_preview_page_slice`、`dc_text_layer_correction`、`dc_annotation_anchor`

设计说明：

- 文本层快照只解释预览视图中的文本定位，不替代 `OCR` 域正式识别结果
- 当前页的基础文本层只能有一份，校正通过覆盖层追加，不覆盖原始快照

### 4.3.4 `dc_text_layer_correction`

用途：承接文本层校正正式对象，表达针对某个文本层快照的可审计校正覆盖层与版本链。

- 关键主键：`text_layer_correction_id`
- 关键字段：
  - `text_layer_snapshot_id`
  - `preview_generation_id`
  - `document_version_id`
  - `page_no`
  - `correction_no`
  - `previous_correction_id`
  - `correction_reason`
  - `source_span_ref`
  - `corrected_span_payload`
  - `evidence_ref`
  - `correction_status`：`ACTIVE`、`REPLACED`、`REVOKED`
  - `is_current_effective`
  - `corrected_by`、`corrected_at`
- 关键索引 / 唯一约束：
  - `uk_snapshot_correction_no(text_layer_snapshot_id, correction_no)`
  - `uk_snapshot_current_correction(text_layer_snapshot_id, is_current_effective)`，其中 `is_current_effective=1` 时同一快照只允许一条当前有效校正
  - `idx_previous_correction(previous_correction_id)`
  - `idx_correction_status(text_layer_snapshot_id, correction_status)`
- 关联对象：`dc_text_layer_snapshot`、`dc_preview_generation`、审计事件

设计说明：

- 校正版本链通过 `previous_correction_id + correction_no` 表达，不允许覆盖式更新旧校正正文
- 新校正生效时，上一条当前有效校正改为 `REPLACED` 且 `is_current_effective=0`
- 撤销校正只影响覆盖层，不改写 `dc_text_layer_snapshot` 和原始 `DocumentVersion`

### 4.4 `dc_annotation`

用途：版本级批注主记录与处理状态表。

- 关键主键：`annotation_id`
- 关键字段：
  - `document_asset_id`
  - `document_version_id`
  - `current_anchor_id`：当前默认锚点引用，指向 `dc_annotation_anchor`
  - `preview_artifact_id`
  - `annotation_type`：`COMMENT`、`QUESTION`、`RISK`、`TODO`
  - `annotation_status`：`OPEN`、`IN_PROGRESS`、`RESOLVED`、`REJECTED`
  - `content`
  - `mentioned_user_ids_json`
  - `resolved_at`、`resolved_by`
- 关键索引 / 唯一约束：
  - `idx_version_status(document_version_id, annotation_status)`
  - `idx_asset_type(document_asset_id, annotation_type, annotation_status)`
  - `idx_current_anchor(current_anchor_id)`
  - `idx_resolver(resolved_by, resolved_at)`
- 关联对象：`dc_document_version`、`dc_preview_artifact`、`dc_annotation_anchor`、`dc_revision`

设计说明：

- 批注锚定的是“版本视图”而不是业务对象，因此必须保留版本主键
- `dc_annotation` 只承接批注业务语义与当前默认锚点引用，锚点载荷、哈希、状态与重定位链路统一下沉到独立正式对象 `dc_annotation_anchor`
- 锚点失效时不删除原记录，而是通过 `dc_annotation_anchor` 的状态与重定位链路留痕

### 4.5 `dc_annotation_anchor`

用途：承载批注锚点、锚点状态与跨版本重定位链路，是批注定位语义的正式对象表。

- 关键主键：`anchor_id`
- 关键字段：
  - `annotation_id`
  - `document_version_id`
  - `preview_generation_id`
  - `page_slice_id`
  - `text_layer_snapshot_id`：仅 `TEXT_RANGE` 锚点必填，页级锚点为空
  - `preview_generation_no`
  - `anchor_type`：`TEXT_RANGE`、`PAGE_RECT`、`PAGE_POINT`、`WHOLE_PAGE`
  - `anchor_payload`：锚点序列化内容
  - `anchor_hash`
  - `anchor_status`：`ACTIVE`、`RELOCATED`、`STALE`、`RELOCATION_FAILED`、`EXPIRED`
  - `degraded_reason_code`：降级定位原因；未降级时为空
  - `degraded_granularity`：降级后的定位粒度；如 `PAGE`、`REGION`、`POINT`
  - `reanchored_from_anchor_id`：重定位来源锚点
  - `relocation_reason`
  - `relocation_job_id`
- 关键索引 / 唯一约束：
  - `idx_annotation_status(annotation_id, anchor_status)`
  - `idx_version_anchor_hash(document_version_id, anchor_hash)`
  - `idx_preview_generation(preview_generation_id, anchor_status)`
  - `idx_page_slice(page_slice_id, anchor_status)`
  - `idx_text_layer_snapshot(text_layer_snapshot_id, anchor_status)`
  - `idx_reanchor_source(reanchored_from_anchor_id)`
- 关联对象：`dc_annotation`、`dc_document_version`、`dc_preview_generation`、`dc_preview_page_slice`、`dc_text_layer_snapshot`、`platform_job`

设计说明：

- 锚点已经提升为独立正式对象，父文档与锚点专项统一以“批注主记录 + 锚点正式对象”作为持久化边界
- 锚点必须通过 `preview_generation_id` 固定预览代次；页级锚点必须通过 `page_slice_id` 固定页坐标系；文本锚点必须通过 `text_layer_snapshot_id` 固定文本层快照
- `dc_annotation.current_anchor_id` 只表达当前默认展示锚点，不覆盖历史锚点链路
- 跨版本重定位通过新增锚点记录承接，历史锚点只改状态与链路，不回写覆盖原始载荷
- 降级定位语义也必须落在锚点正式对象上，统一由 `degraded_reason_code` 与 `degraded_granularity` 承接，禁止只写审计事件或运行时态

### 4.6 `dc_revision`

用途：表达基线版本与候选版本之间的修订关系，是协作与版本推进的桥。

- 关键主键：`revision_id`
- 关键字段：
  - `document_asset_id`
  - `base_version_id`
  - `candidate_version_id`
  - `revision_type`：`REPLACE`、`REDLINE`、`COMPARE_ONLY`
  - `revision_status`：`OPEN`、`UNDER_REVIEW`、`ACCEPTED`、`REJECTED`、`WITHDRAWN`
  - `revision_note`
  - `accepted_at`、`accepted_by`
  - `rejected_at`、`rejected_by`
  - `current_diff_artifact_id`：当前默认展示的差异产物引用，指向 `dc_diff_artifact`
  - `origin_annotation_count`
- 关键索引 / 唯一约束：
  - `uk_candidate_version(candidate_version_id)`
  - `idx_asset_status(document_asset_id, revision_status)`
  - `idx_base_candidate(base_version_id, candidate_version_id)`
  - `idx_acceptor(accepted_by, accepted_at)`
- 关联对象：`dc_document_version`、`dc_annotation`、`dc_diff_artifact`

设计说明：

- 一个候选版本只能属于一个激活中的修订记录，防止同一稿件被多次并行采纳
- 修订采纳是推动版本切换的业务动作，但不直接替代版本链表

### 4.7 `dc_diff_artifact`

用途：承载修订关系下的正式差异产物对象，是红线展示、差异审查与审计回放的派生证据主表。

- 关键主键：`diff_artifact_id`
- 关键字段：
  - `revision_id`
  - `base_document_version_id`
  - `candidate_document_version_id`
  - `diff_profile_code`：`TEXT_REDMARK`、`PAGE_VISUAL`、`STRUCTURAL`
  - `diff_generation_no`
  - `diff_algorithm_code`
  - `base_content_hash`
  - `candidate_content_hash`
  - `render_checksum`
  - `artifact_status`：`PENDING`、`GENERATING`、`READY`、`FAILED`、`STALE`
  - `hunk_count`
  - `page_coverage_json`
  - `artifact_storage_key`
  - `generated_job_id`
- 关键索引 / 唯一约束：
  - `uk_revision_generation(revision_id, diff_profile_code, diff_generation_no)`
  - `idx_revision_status(revision_id, artifact_status)`
  - `idx_version_pair(base_document_version_id, candidate_document_version_id)`
  - `idx_generated_job(generated_job_id)`
- 关联对象：`dc_revision`、`dc_document_version`、`platform_job`

设计说明：

- `dc_diff_artifact` 已提升为模块内正式对象，父文档与修订差异专项统一以“修订关系 + 差异产物对象”作为持久化边界
- `dc_revision.current_diff_artifact_id` 只表达当前默认展示引用，不替代历史差异产物链路
- 差异产物是正式派生证据，不是页面缓存，也不回写覆盖任一 `DocumentVersion`

### 4.8 `dc_binding_view_cache`

用途：面向合同详情、审批摘要、归档准备页的文档聚合只读缓存表。

- 关键主键：`binding_cache_id`
- 关键字段：
  - `owner_type`、`owner_id`
  - `document_asset_id`
  - `current_version_id`
  - `binding_status`
  - `linked_contract_status`
  - `linked_signature_status`
  - `linked_archive_status`
  - `annotation_open_count`
  - `revision_open_count`
  - `preview_ready_flag`
  - `encryption_status`
  - `snapshot_version`
  - `rebuilt_at`
- 关键索引 / 唯一约束：
  - `uk_owner_document(owner_type, owner_id, document_asset_id)`
  - `idx_owner_lookup(owner_type, owner_id)`
  - `idx_rebuilt_at(rebuilt_at)`
- 关联对象：`dc_document_asset`、合同主档、签章摘要、归档摘要

设计说明：

- 该表是聚合缓存，不是业务真相源
- 缓存错了可以重建，文档真相仍以 `dc_document_asset` /
  `dc_document_version` 为准
- 选择表而非数据库视图，是因为聚合项同时依赖文档表与其他模块状态摘要，且需要控制重建时机

### 4.9 `dc_decrypt_download_authorization`

用途：管理端维护的解密下载授权规则表，表达“谁在什么范围内可以触发受控明文导出”。

- 关键主键：`decrypt_download_authorization_id`
- 关键字段：
  - `scope_type`：`CONTRACT`、`CONTRACT_RANGE`
  - `contract_id`
  - `scope_filter_json`：分类、归属部门、密级、业务域等范围条件
  - `authorization_status`：`ENABLED`、`DISABLED`、`EXPIRED`
  - `effective_start`、`effective_end`
  - `granted_department_ids_json`
  - `granted_user_ids_json`
  - `reason`
  - `approval_ref`：管理端审批或审批单号引用
  - `policy_snapshot_json`：授权生效时的加密策略快照
- 关键索引 / 唯一约束：
  - `idx_scope_contract(scope_type, contract_id, authorization_status)`
  - `idx_effective_window(authorization_status, effective_start, effective_end)`
  - `idx_updated_by(updated_by, updated_at)`
- 关联对象：合同主档、组织 / 人员主数据、`dc_decrypt_download_job`

设计说明：

- 授权是规则，不是一次下载动作
- 授权对象口径固定为部门、人员，不扩展为任意表达式，避免越权模型失控
- 规则命中后也只放开“解密下载”，不放开平台内其他业务越权动作

### 4.10 `dc_decrypt_download_job`

用途：一次受控解密下载动作的正式任务表。

- 关键主键：`decrypt_download_job_id`
- 关键字段：
  - `authorization_id`
  - `contract_id`
  - `document_asset_id`
  - `document_version_id`
  - `request_user_id`
  - `request_department_id`
  - `job_status`：`PENDING`、`AUTHORIZED`、`GENERATING`、`READY`、`DELIVERED`、`FAILED`、`EXPIRED`、`CANCELLED`
  - `failure_code`、`failure_message`
  - `reason`
  - `request_dedup_fingerprint`：基于正式去重语义计算的请求指纹
  - `generated_plain_storage_key`
  - `download_token_hash`
  - `token_expires_at`
  - `audit_event_id`
  - `platform_job_id`
  - `delivered_at`
- 关键索引 / 唯一约束：
  - `uk_request_dedup(document_version_id, request_user_id, request_dedup_fingerprint)`
  - `idx_contract_status(contract_id, job_status, created_at)`
  - `idx_request_user(request_user_id, created_at)`
  - `idx_platform_job(platform_job_id)`
  - `idx_token_expire(token_expires_at, job_status)`
- 关联对象：`dc_decrypt_download_authorization`、`dc_document_version`、`platform_job`、`audit_event`

设计说明：

- 明文导出文件可脱离 `CMP` 使用，但其生成过程、发放过程、过期过程都受控
- 任务与授权分表，避免把策略和一次性执行状态混在一起
- 解密下载正式去重语义统一为“同一 `document_version_id + request_user_id + reason + 授权命中时间窗` 只创建一个活跃作业”；`request_dedup_fingerprint` 承接该业务语义，`download_token_hash` 仅承接交付令牌本身，二者不得混用

### 4.11 `dc_capability_binding`

用途：统一登记文档版本与 `OCR` / 搜索 / 签章 / 加密 / 归档能力挂接关系。

- 关键主键：`capability_binding_id`
- 关键字段：
  - `document_version_id`
  - `capability_type`：`OCR`、`SEARCH`、`SIGNATURE`、`ENCRYPTION`、`ARCHIVE`
  - `binding_status`：`PENDING`、`READY`、`FAILED`、`STALE`
  - `platform_job_id`
  - `result_ref`
  - `result_checksum`
  - `last_synced_at`
- 关键索引 / 唯一约束：
  - `uk_version_capability(document_version_id, capability_type)`
  - `idx_capability_status(capability_type, binding_status)`
  - `idx_platform_job(platform_job_id)`
- 关联对象：`dc_document_version`、`platform_job`

设计说明：

- 子模块只保存挂接状态与结果引用，不复制能力域内部明细
- 同一版本对同一能力只有一个当前挂接记录，重跑时覆盖状态并追加任务历史
- 所有“旧结果失效 / 历史降级 / 需重挂”场景统一收口为 `STALE`，不再额外引入 `INVALIDATED` 等平行状态

## 5. 文件对象真相与版本链内部模型

### 5.1 文件对象真相模型

- `DocumentAsset` 是“这份文件对象是谁”的根
- `DocumentVersion` 是“这份文件对象当前有哪些内容版本”的链
- `current_version_id` 是“平台默认读取哪一个版本”的唯一入口
- 对象存储中的文件只是内容载体，不自行构成业务真相
- 预览、索引、`OCR` 文本、签章结果摘要、归档封包摘要都属于派生产物
- 明文导出介质只属于受控导出作业下的临时受控介质，不进入派生对象层

其中预览派生域在父文档层只做总述：`dc_preview_artifact` 是总入口 / 兼容总览对象，正式派生模型由预览代次、页级切片、文本层快照、文本层校正对象组成，细节以下游专项设计为准。

因此，文件对象真相的唯一判断规则是：

1. 是否存在 `dc_document_asset`
2. 是否存在其受控 `dc_document_version`
3. 当前主版本是否由 `current_version_id` 明确指向

### 5.2 版本链规则

- 首个版本写入时创建 `version_no=1`
- 后续版本只允许追加，不允许原地覆盖既有版本内容
- `parent_version_id` 表达时间上的上一版本
- `base_version_id` 表达修订所基于的基线版本
- 激活新主版本时，旧主版本状态改为 `SUPERSEDED`
- 任一时刻一个 `DocumentAsset` 只允许一个 `ACTIVE` 版本

### 5.3 写入与切换事务原则

- 文件上传成功不等于版本成立，必须在元数据落库成功后才视为版本创建成功
- 切主版本采用单事务更新：
  - 锁定 `dc_document_asset`
  - 校验候选版本状态
  - 更新旧 `ACTIVE` 版本为 `SUPERSEDED`
  - 更新候选版本为 `ACTIVE`
  - 回写 `current_version_id` 与 `latest_version_no`
- 事务提交后再异步触发预览重建、索引刷新、绑定视图重算

### 5.4 版本链恢复原则

- 对象存储写成功但数据库事务失败时，标记为孤儿对象，进入清理任务
- 数据库事务成功但派生任务失败时，版本仍然成立，只是能力状态为失败或待重试
- 恢复时永远以 `dc_document_version` 为准，结合对象生命周期正式记录与审计事件定位历史对象引用、恢复源和当前有效落点，再补建预览代次、页级切片、文本层快照、校正覆盖层，以及其对外总览对象、索引和挂接记录

## 6. 批注 / 修订 / 协作内部模型

### 6.1 批注模型

- 批注必须绑定 `document_version_id`
- 批注主记录与锚点正式对象拆表持久化：`dc_annotation` 承接业务语义，`dc_annotation_anchor` 承接定位载荷、状态与重定位链路
- 批注展示可经由 `preview_artifact_id` 命中当前默认预览总览入口，但锚定语义仍应落在对应预览代次 / 页级 / 文本层快照上，真相仍归属版本
- 一个批注从 `OPEN` 到 `RESOLVED` 的过程只改变处理状态，不改变锚点原始留痕
- `mentioned_user_ids_json` 只保留轻量协作上下文，不承担通知真相

### 6.2 锚点模型

- 文本类文档优先使用 `TEXT_RANGE`
- 版面文档优先使用 `PAGE_RECT` / `PAGE_POINT`
- 锚点序列化内容中至少包含页号、片段定位信息、预览校验摘要，且统一持久化在 `dc_annotation_anchor.anchor_payload`
- 锚点重定位基于“文本片段 + 页码 + 局部哈希”三元组
- 降级定位必须显式落库到 `dc_annotation_anchor.degraded_reason_code` 与 `dc_annotation_anchor.degraded_granularity`
- 重定位失败时保留失效批注，不静默删除

### 6.3 修订模型

- 修订不是文件内容本身，而是对“基线版本与候选版本关系”的业务表达
- `dc_diff_artifact` 是修订域内正式差异产物对象，承接版本对差异证据；`dc_revision.current_diff_artifact_id` 只保存当前默认展示引用
- `REPLACE` 表示采纳后会切主版本
- `REDLINE` 表示存在候选稿和差异展示，但不一定立即切主版本
- `COMPARE_ONLY` 只保留对比结果，不推进版本链

### 6.4 协作状态模型

协作状态不单独新建大而全状态机，而由三组状态叠加得到：

- 文档层：`document_status`
- 批注层：`annotation_status`
- 修订层：`revision_status`

协作工作区展示时采用以下聚合规则：

- 存在 `OPEN` / `UNDER_REVIEW` 修订，则文档协作摘要为“修订处理中”
- 无打开修订但存在 `OPEN` / `IN_PROGRESS` 批注，则为“批注处理中”
- 批注与修订均清零，则为“协作已收敛”

### 6.5 与业务状态回写的边界

- 协作采纳触发版本切换，但不直接改合同一级业务状态
- 若上游审批 / 签章 / 归档要求引用最新主版本，由业务模块依据 `current_version_id` 自行感知
- 文档协作只负责文件协作真相，不负责审批结论真相

## 7. 与 OCR / 搜索 / 签章 / 加密 / 归档的内部挂接设计

### 7.1 挂接总原则

- 所有能力都消费 `DocumentVersion`
- 所有能力都通过 `dc_capability_binding` 登记挂接状态
- 能力失败不破坏文件真相，只影响派生能力可用性
- 派生能力需要重建时，以版本主键重放，不依赖页面操作补偿

### 7.2 `OCR` 挂接

- 输入：`document_version_id`
- 输出：`result_ref` 指向识别文本或结构化结果存储
- 文本结果可进入搜索索引或 AI 能力，但不回写覆盖原文件
- 失败后将 `binding_status` 标记为 `FAILED`，允许重试

### 7.3 搜索挂接

- 索引主键建议采用 `document_version_id`
- 当前主版本变化时，默认将旧主版本索引绑定置为 `STALE` 并退出默认检索曝光，保留历史索引引用供审计 / 回放；仅在审计调查、争议回放、合规核查或显式版本检索任务触发时，才临时重新暴露或补建历史索引，新主版本索引负责接管默认曝光
- 搜索索引只保留检索所需字段，不保留独立文件真相

### 7.4 签章挂接

- 签章模块读取指定 `ACTIVE` 版本作为输入稿
- 签章结果文件回写为新的 `DocumentVersion`
- 原输入稿保留，签章结果稿通过 `origin_type=SIGNATURE_OUTPUT` 标识来源
- 签章业务状态仍由签章模块 / 合同主档负责，文档中心只负责文件版本回收

### 7.5 加密挂接

- 写入路径：白名单校验 -> 加密校验 -> 生成受控存储对象 -> 版本落库
- 平台内读取路径：权限校验 -> 生成短期解密访问句柄 -> 内存流式解密 -> 返回预览 / 下载流
- 管理端授权下载路径：授权匹配 -> 创建解密下载作业 -> 生成明文导出文件 -> 审计 -> 有效期后清理导出介质
- 加密模块只挂在文档中心读写路径上，不另建文件主档

### 7.6 归档挂接

- 归档模块读取指定主版本或归档封包来源版本
- 归档稿 / 归档封包若形成实体文件，仍回收到 `dc_document_version`
- 归档模块只持有档案真相与借阅状态，不持有第二文件真相源

## 8. 解密下载授权与任务内部设计

### 8.1 主语义

- 默认不可脱离平台使用
- 管理端可按部门、人员授权解密下载
- 下载后的明文文件可脱离 `CMP` 使用
- 整个授权、生成、发放、过期、追溯链路必须全程审计与受控

请求标识映射说明：

- `API Design` 对外允许继续使用 `contract_id` 与 `document_id` / `attachment_id` 这类业务可见标识发起解密下载请求
- 请求进入文档中心内部后，必须先基于 `contract_id + 业务标识` 解析到唯一的 `document_asset_id`；其中 `document_id` / `attachment_id` 只是外部契约字段，不作为子模块内部真相主键继续流转
- 若请求语义是“下载该业务文档当前平台可读版本”，则在 `document_asset_id` 解析完成后，统一通过 `dc_document_asset.current_version_id` 定位 `document_version_id`
- 若后续某个内部流程需要落到特定历史版本，也必须先锚定 `document_asset_id`，再在该资产的版本链内定位目标 `document_version_id`，而不是在内部直接沿用 `document_id` / `attachment_id`

### 8.2 授权匹配规则

- 先校验请求用户对合同 / 文档的基础访问权限
- 再校验是否命中启用中的 `dc_decrypt_download_authorization`
- 命中规则时同时检查：
  - 时间窗口是否有效
  - 请求用户是否属于授权部门或授权人员
  - 文档密级与规则范围是否匹配
- 任一条件不满足则拒绝创建下载作业

### 8.3 下载作业状态流转

`PENDING -> AUTHORIZED -> GENERATING -> READY -> DELIVERED`

失败或终止分支：

- `PENDING -> FAILED`
- `AUTHORIZED -> FAILED`
- `READY -> EXPIRED`
- 任意未完成状态 -> `CANCELLED`

### 8.4 导出文件控制

- 明文导出文件不写回 `dc_document_version`，避免把平台外使用副本误当成平台真相版本
- 明文导出介质只作为任务结果文件存在，绑定到 `dc_decrypt_download_job`
- 导出文件必须设置短有效期与回收任务
- 即使回收失败，也不影响审计链；恢复任务会继续清理残留明文介质

### 8.5 审计要求

- 记录授权创建、授权停用、授权命中、作业创建、明文生成、下载发放、过期清理
- 审计对象至少包含：`contract_id`、`document_asset_id`、`document_version_id`、
  `authorization_id`、`request_user_id`、`request_department_id`
- 对失败动作同样落审计，不能只记成功事件

## 9. 缓存、锁、幂等与并发控制

### 9.1 缓存边界

- `Redis` 只存放热点摘要、幂等键、短锁、短期解密访问句柄、作业短态
- `dc_binding_view_cache` 是数据库内聚合缓存，不是 `Redis` 替代品
- 缓存失效后必须可由数据库真相重建，不能影响版本链正确性

### 9.2 幂等策略

- 创建文档主档：按 `Idempotency-Key + owner_type + owner_id + content_hash` 去重
- 追加版本：按 `document_asset_id + content_hash + change_reason + base_version_id` 去重
- 预览生成：按 `document_version_id + preview_type + render_checksum` 去重
- 解密下载：按 `document_version_id + request_user_id + reason + 授权命中时间窗` 计算 `request_dedup_fingerprint` 去重；下载令牌不参与作业去重
- 能力挂接任务：按 `document_version_id + capability_type + result_checksum` 去重

### 9.3 锁策略

- `asset:{document_asset_id}:activate`：切主版本锁
- `asset:{document_asset_id}:append`：版本追加锁
- `preview:{document_version_id}:{preview_type}`：预览生成锁
- `decrypt:{document_version_id}:{request_user_id}`：解密下载锁
- `binding-cache:{owner_type}:{owner_id}`：聚合缓存重建锁

规则：

- 锁只保护临界区，正式状态仍以数据库事务提交为准
- 锁超时后由重试逻辑接管，不能把锁本身当恢复依据

### 9.4 并发控制

- `dc_document_asset`、`dc_document_version` 采用乐观版本号字段辅助并发更新
- 版本切换与修订采纳必须串行化到同一 `document_asset_id`
- 解密下载对同一用户 / 同一版本短时间内只允许一个活跃任务
- 聚合缓存允许最终一致，不阻塞主业务事务

## 10. 异步任务、补偿与恢复

### 10.1 异步任务清单

- 预览产物生成
- `OCR` 识别
- 搜索索引构建 / 刷新
- 签章结果回收后的派生处理
- 解密下载明文生成与过期清理
- 聚合缓存重建
- 孤儿对象清理

### 10.2 与平台任务中心的关系

- 正式任务状态落总平台 `platform_job`
- 文档中心私有表只保存业务引用和结果摘要
- 平台任务失败后，子模块根据 `platform_job_id` 回写自身状态

### 10.3 补偿策略

- 预览失败：标记 `artifact_status=FAILED`，允许重试，不回滚版本
- `OCR` / 搜索失败：标记 `dc_capability_binding` 为 `FAILED`，允许重试
- 签章结果回收失败：签章模块保留业务状态，文档中心创建补偿任务重拉文件
- 解密导出失败：`dc_decrypt_download_job` 标记失败并清理半成品明文介质
- 聚合缓存失败：删除旧缓存并重建，不影响主链路读取兜底查询

### 10.4 恢复边界

- 可自动恢复：预览、索引、`OCR`、聚合缓存、导出介质清理
- 需人工介入：
  - 对象存储内容损坏
  - 加密模块校验规则变更导致历史文件无法重解
  - 明文导出已发放且外部传播后的平台外后果
- 平台恢复目标是恢复平台内可控状态，不承诺回收已经脱离 `CMP` 的明文副本

## 11. 审计、日志、指标与恢复边界

### 11.1 审计

- 关键动作全部写入 `audit_event`
- 审计动作至少包括：上传、追加版本、切主版本、预览生成、批注创建 / 处理、修订采纳 / 驳回、能力挂接失败、授权变更、解密下载、导出清理
- 审计要能按合同、文档、版本、人员、部门、动作类型回溯

### 11.2 运行日志

- 应用日志记录任务链路、对象主键、耗时、异常摘要
- 敏感日志不输出明文文件路径、下载令牌、解密后的内容片段
- 预览、加密、导出流程统一带 `trace_id`

### 11.3 指标

- 文档写入成功率 / 失败率
- 版本切换耗时
- 预览生成耗时与失败率
- `OCR` 成功率、索引刷新延迟
- 批注打开数、修订积压数
- 解密下载授权命中数、下载成功率、导出介质清理成功率
- 孤儿对象数量、缓存重建耗时

### 11.4 恢复边界

- 平台内正式恢复边界：恢复到“文件真相、版本链、授权与审计可重建”
- 不把 `Redis`、搜索索引、预览产物视为必须先恢复的真相层
- 对象存储与数据库恢复时，优先恢复 `dc_document_asset` /
  `dc_document_version` 与授权 / 审计主表，再重放派生任务

## 12. 继续下沉到后续专项设计或实现的内容

- 对象存储与生命周期专项设计： [object-storage-and-lifecycle-design.md](./special-designs/object-storage-and-lifecycle-design.md)
- 预览渲染与文本层专项设计： [preview-rendering-and-text-layer-design.md](./special-designs/preview-rendering-and-text-layer-design.md)
- 批注锚点与跨版本重定位专项设计： [annotation-anchor-and-relocation-design.md](./special-designs/annotation-anchor-and-relocation-design.md)
- 修订差异产物与红线展示专项设计： [redline-diff-artifact-design.md](./special-designs/redline-diff-artifact-design.md)
- 加密句柄、解密访问句柄、受控导出与介质清理专项设计： [crypto-handle-and-secure-export-design.md](./special-designs/crypto-handle-and-secure-export-design.md)
- OCR / 搜索 / 签章 / 归档挂接绑定专项设计： [ocr-search-signature-archive-binding-design.md](./special-designs/ocr-search-signature-archive-binding-design.md)
- 表级 DDL、分库分表与容量规划： [ddl-sharding-and-capacity-design.md](./special-designs/ddl-sharding-and-capacity-design.md)

## 13. 本文结论

文档中心是 `CMP` 的文件对象真相源，文档协作建立在其上。文件对象主档、
版本链、预览、批注、修订、解密下载授权与受控下载任务都在本子模块内部闭环。

其中预览域在父文档层只保留总述口径：`dc_preview_artifact` 是总入口 / 兼容总览对象，不应再被理解为完整正式模型；正式派生模型以下游专项设计定义的预览代次、页级切片、文本层快照与校正对象为准。

`OCR`、搜索、签章、加密、归档都只能围绕文档中心挂接，不能各自长出文件真相源。
加密模块挂在文档中心写入 / 读取路径上，默认保证文档不可脱离平台使用；管理端按
部门、人员授权的解密下载是受控例外，导出的明文文件可脱离 `CMP` 使用，但不进入
第二文件真相源，并必须全程审计、可追溯、可恢复到平台内受控边界。

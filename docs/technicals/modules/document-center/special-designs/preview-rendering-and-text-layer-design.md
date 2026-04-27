# 文档中心专项设计：预览渲染与文本层设计

## 1. 文档说明

本文档用于继续下沉 `document-center` 主线中“预览渲染与文本层”这一能力，重点回答以下问题：

- 预览与文本层如何作为文件真相源上的正式派生能力成立，而不是临时缓存或页面私有实现
- 预览对象模型如何围绕 `DocumentVersion`、页级切片、文本层快照和校正结果稳定建模
- 预览版本与文档版本是什么关系，何时允许重建，何时只能失效回退，何时必须人工介入
- 预览缓存、失败回退、审计留痕和重建边界如何收口，保证不把派生产物误写成新的文件真相源
- 文本层如何同时服务预览阅读、批注锚定、搜索辅助和后续校正，而不覆盖原文件内容真相

本文是以下文档的下游专项设计：

- [`document-center Detailed Design`](../detailed-design.md)

父文档在预览域只保留总述口径：`dc_preview_artifact` 视为预览派生域的总入口或兼容总览对象，不能被理解为完整正式模型；正式对象模型仍以下文定义的预览代次、页级切片、文本层快照和校正对象为准。

`contract-core`、`e-signature`、`encrypted-document`、`intelligent-applications` 与 `OCR` / 搜索能力是本文的强依赖或关键消费方：它们分别消费预览页视图、文本层锚点、签章前页级视图、受控阅读链路或辅助文本结果，但这些关系都属于跨模块依赖或消费关系，不属于本文档的父文档关系。

本文不展开以下内容：

- 不重写渲染引擎选型、厂商 `SDK`、字体兼容策略、图片编码参数或前端播放组件实现
- 不写对外 `API` 路径、字段、错误码、回调协议或鉴权流程
- 不写批处理任务编排脚本、类图、函数签名、数据库 `DDL` 细节或部署拓扑
- 不写实施排期、压测计划、迁移步骤、值守手册或上线方案

## 2. 设计目标

- 让预览与文本层成为围绕 `DocumentVersion` 的正式派生能力，而不是页面缓存、浏览器临时产物或外部引擎私有状态
- 让页级切片、页级坐标系和文本层快照形成稳定对象模型，为批注、修订、搜索辅助和受控阅读提供统一基础
- 让同一文件版本可多次重建预览，但始终只有一个当前有效预览代次对外默认暴露
- 让文本层校正有正式留痕、有适用边界、有回退路径，但永远不改写文件真相和原始内容哈希
- 让预览失败、文本层缺失、校正冲突、缓存失效都能回退到平台内可控状态，而不是把恢复责任交给前端缓存或第三方服务

## 3. 正式派生能力成立原则

### 3.1 真相源与派生层分离

预览与文本层的成立前提，是继续坚持“文件真相只在 `DocumentAsset` / `DocumentVersion`，预览与文本层只在派生层”的原则。

因此，正式判断规则如下：

1. 只要对象可被当作正式文件继续签署、归档、下载、替换主版本，它就必须回到 `DocumentVersion`。
2. 只要对象只用于阅读视图、页码定位、文本选区、命中高亮、批注锚定或辅助检索，它就属于预览 / 文本层派生对象。
3. 派生对象可以重建、失效、切换当前代次，但无权覆盖 `document_version_id` 所代表的正式内容真相。

### 3.2 版本锚点统一为 `document_version_id`

预览与文本层都必须先锚定具体 `document_version_id`，不能锚定“当前最新文件”“合同详情当前展示稿”这类漂浮语义。这样才能同时成立两件事：

- 历史版本的预览与文本层可以被审计、回放和重建
- 当前主档只默认暴露当前主版本对应的当前有效预览代次

### 3.3 预览是正式派生能力，不是纯缓存

预览虽然可重建，但不能因此被降格为“随便丢、随便算”的页面缓存。正式派生能力至少要满足：

- 有明确来源版本和派生规则
- 有稳定对象身份与状态机
- 有当前有效代次与失效代次的区分
- 有失败、回退、重建、审计和恢复边界

浏览器本地缓存、`CDN` 缓存或网关短缓存都只是传输优化层，不承担上述正式语义。

## 4. 统一对象模型

### 4.1 预览代次对象

预览不能只建模到“某个 `preview_artifact_id` 是否生成成功”，还需要有“同一版本下第几次正式预览代次”的概念。本文将其统一称为“预览代次”。

一个预览代次至少要表达以下语义：

- `source_document_version_id`
- `preview_profile_code`：表达阅读场景语义，如标准阅读、签章前视图、移动端简化视图；它是产品语义，不是具体引擎参数透传
- `preview_generation_no`
- `render_checksum`
- `preview_status`：`PENDING`、`GENERATING`、`READY`、`FAILED`、`STALE`
- `page_count`
- `text_layer_status`：`ABSENT`、`READY`、`CORRECTED`、`STALE`
- `fallback_generation_ref`
- `generated_job_id`

统一约束如下：

- 同一 `document_version_id + preview_profile_code` 允许多代并存，但只能有一个当前有效代次
- 旧代次可以保留用于回退、审计和批注解释，但默认状态必须可区分为 `STALE`
- 代次切换不改变 `DocumentVersion` 身份，只改变默认暴露的派生视图

### 4.2 页级切片对象

页是预览与文本层的共同最小对齐单元。无论底层原始文件来自 `PDF`、图片扫描件还是办公文档，进入正式预览能力后，都必须先收口为页级对象清单。

每个页级对象至少要表达：

- `source_document_version_id`
- `preview_generation_no`
- `page_no`
- `page_render_ref`
- `page_width`、`page_height`
- `page_rotation`
- `page_checksum`
- `page_status`：`READY`、`FAILED`、`STALE`

页级切片的正式意义不是“图片怎么切”，而是：

- 给批注、修订、签章坐标和高亮提供稳定页坐标系
- 让局部页失败与整份文档失败可分离
- 让重建与缓存失效可以页级判断，而不是一律整份回炉

### 4.3 文本层快照对象

文本层不是“识别结果总表”，而是“某个预览代次在页级阅读视图上可定位、可选区、可高亮的文本快照”。

其最小语义至少包括：

- `source_document_version_id`
- `preview_generation_no`
- `page_no`
- `text_layer_checksum`
- `text_source_type`：`EMBEDDED_TEXT`、`LAYOUT_EXTRACTION`、`OCR_ASSISTED`
- `token_span_count`
- `coordinate_space_version`
- `correction_state`

文本层与 `OCR` 的关系必须明确拆开：

- `OCR` 结果回答“识别到了什么内容，能否进入搜索或结构化提取”
- 文本层回答“在当前预览视图上，这些文本如何被定位、选区和高亮”

两者可以互相借力，但不能互相替代。

### 4.4 文本层校正对象

文本层校正不是修改原文件，也不是覆盖 `OCR` 真相，而是给某个文本层快照追加一层正式校正语义。

校正对象至少要表达：

- `source_document_version_id`
- `preview_generation_no`
- `page_no`
- `correction_no`
- `correction_reason`
- `source_span_ref`
- `corrected_span_payload`
- `evidence_ref`
- `corrected_by` / `corrected_at`
- `correction_status`：`ACTIVE`、`REPLACED`、`REVOKED`

因此，文本层对外默认暴露的是“基础文本层 + 当前有效校正覆盖层”的组合结果，而不是把基础文本层原文直接改写掉。

### 4.5 表级对象草案

预览与文本层正式对象以 `dc_preview_generation`、`dc_preview_page_slice`、`dc_text_layer_snapshot`、`dc_text_layer_correction` 四张表承接。`dc_preview_artifact` 只保留总入口和兼容总览摘要，不再承担完整正式模型。

#### 4.5.1 `dc_preview_generation`

用途：预览代次主表，表达同一 `DocumentVersion` 在指定阅读场景下的一次正式派生。

- 主键：`preview_generation_id`
- 外键：`document_version_id` 指向 `dc_document_version`，`preview_artifact_id` 指向 `dc_preview_artifact`，`fallback_generation_id` 指向本表历史代次，`generated_job_id` 指向 `platform_job`
- 关键字段：`preview_profile_code`、`preview_generation_no`、`render_checksum`、`coordinate_space_version`、`preview_status`、`text_layer_status`、`page_count`、`is_current_effective`
- 状态枚举：`preview_status` 为 `PENDING`、`GENERATING`、`READY`、`FAILED`、`STALE`；`text_layer_status` 为 `ABSENT`、`READY`、`CORRECTED`、`STALE`
- 唯一约束：`uk_version_profile_generation(document_version_id, preview_profile_code, preview_generation_no)`
- 当前有效代次约束：`uk_version_profile_current(document_version_id, preview_profile_code, is_current_effective)`，其中 `is_current_effective=1` 时同一版本同一场景只能存在一条当前有效代次

#### 4.5.2 `dc_preview_page_slice`

用途：页级切片表，表达预览代次内每一页的渲染引用、页坐标系与页内容校验摘要。

- 主键：`page_slice_id`
- 外键：`preview_generation_id` 指向 `dc_preview_generation`
- 关键字段：`document_version_id`、`preview_profile_code`、`preview_generation_no`、`page_no`、`page_render_ref`、`page_width`、`page_height`、`page_rotation`、`page_checksum`、`page_status`
- 状态枚举：`page_status` 为 `READY`、`FAILED`、`STALE`
- 页级唯一约束：`uk_generation_page(preview_generation_id, page_no)`；为便于审计回放，同时保留 `uk_version_generation_page(document_version_id, preview_profile_code, preview_generation_no, page_no)`
- 引用约束：批注、签章坐标、页内高亮必须通过 `preview_generation_id + page_no` 或 `page_slice_id` 回到本表，不允许仅保存页面临时序号

#### 4.5.3 `dc_text_layer_snapshot`

用途：文本层快照表，表达某一页在指定预览代次下的基础文本定位快照。

- 主键：`text_layer_snapshot_id`
- 外键：`page_slice_id` 指向 `dc_preview_page_slice`，`preview_generation_id` 指向 `dc_preview_generation`
- 关键字段：`document_version_id`、`page_no`、`text_layer_checksum`、`text_source_type`、`token_span_count`、`coordinate_space_version`、`correction_state`、`snapshot_storage_ref`
- 状态枚举：`correction_state` 为 `NONE`、`HAS_ACTIVE_CORRECTION`、`STALE`
- 唯一约束：`uk_page_text_snapshot(page_slice_id)`、`uk_generation_page_text(preview_generation_id, page_no)`
- 关系边界：该表只承接阅读视图文本定位，不替代 `OCR` 识别结果和搜索索引正文

#### 4.5.4 `dc_text_layer_correction`

用途：文本层校正表，表达针对某个文本层快照追加的正式校正覆盖层。

- 主键：`text_layer_correction_id`
- 外键：`text_layer_snapshot_id` 指向 `dc_text_layer_snapshot`，`preview_generation_id` 指向 `dc_preview_generation`，`previous_correction_id` 指向本表上一条校正
- 关键字段：`document_version_id`、`page_no`、`correction_no`、`correction_reason`、`source_span_ref`、`corrected_span_payload`、`evidence_ref`、`correction_status`、`is_current_effective`、`corrected_by`、`corrected_at`
- 状态枚举：`correction_status` 为 `ACTIVE`、`REPLACED`、`REVOKED`
- 唯一约束：`uk_snapshot_correction_no(text_layer_snapshot_id, correction_no)`
- 校正版本链约束：`previous_correction_id` 形成链式追溯；`uk_snapshot_current_correction(text_layer_snapshot_id, is_current_effective)` 保证同一文本层快照最多一条当前有效校正
- 覆盖规则：新增校正生效时只追加新记录并替换当前有效指针，不覆盖旧校正和基础文本层快照

## 5. 预览版本与文档版本关系

### 5.1 文档版本是源，预览代次是派生

`DocumentVersion` 表示正式内容版本；预览代次表示该正式内容在特定阅读语义下的一次受控派生。两者关系统一如下：

- 一个 `DocumentVersion` 可对应多个 `preview_profile_code`
- 每个 `preview_profile_code` 下可存在多个 `preview_generation_no`
- 只有当前有效预览代次拥有默认曝光资格
- 任何预览代次都不能独立升级为新的 `DocumentVersion`

### 5.2 何时创建新预览代次

以下情况应创建新预览代次，而不是覆盖旧代次：

- 源 `document_version_id` 首次进入预览体系
- 预览规则语义变化，导致页数、页坐标系或文本层校验摘要发生变化
- 文本层重抽取或重排版后，已影响批注锚点、高亮或签章坐标可信度
- 旧代次失败后重试，需要保留失败证据与成功代次边界

### 5.3 何时只允许状态失效

以下情况不应新建文件版本，也不应静默覆盖预览结果，而应把旧代次置为 `STALE` 或 `FAILED`：

- 当前主版本切换导致旧版本不再默认暴露
- 校正证据失效，导致原校正不再可信
- 消费方依赖的页坐标系已失配，但源文件内容本身未改变

## 6. 页级切片与文本层抽取原则

### 6.1 页级切片是正式对齐层

页级切片的第一职责不是优化图片加载，而是建立平台统一页语义。后续批注、修订、签章坐标、文本高亮、阅读位置恢复，都必须站在这套页级语义上，而不是各消费方各自算一套页码和缩放逻辑。

因此，页级切片必须满足：

- 页号在同一预览代次内稳定且单调
- 页宽高与旋转方向属于正式快照的一部分
- 任一页重建后如影响坐标语义，必须触发相关锚点与消费面的失效判断

### 6.2 文本层抽取优先级

文本层抽取应遵循“先忠于源文件可读文本，再补足受控识别结果”的优先级：

1. 优先使用源文件内已有可定位文本
2. 源文件缺乏稳定文本层时，生成版面阅读所需的布局文本层
3. 仍无法形成可用文本层时，才引入受控 `OCR` 结果作为辅助来源

这样做的目的，是避免把 `OCR` 默认升级成一切文档的唯一文本真相。

### 6.3 文本层输出边界

文本层只承担以下职责：

- 文本选区
- 页内命中高亮
- 批注文本锚点
- 阅读复制与辅助检索

文本层不承担以下职责：

- 作为合同正文的正式可编辑内容
- 直接替代搜索域自己的索引文档
- 直接替代 `OCR` 域的结构化识别结果

## 7. 预览缓存与默认暴露策略

### 7.1 缓存分层

预览缓存必须区分三层：

1. 正式派生对象缓存：数据库与对象存储中登记的预览代次、页级切片、文本层快照
2. 分发缓存：网关、短期访问令牌、边缘缓存等加速层
3. 终端缓存：浏览器或客户端本地缓存

只有第一层属于本文正式治理范围；后两层都必须可由第一层重建或失效。

### 7.2 默认暴露规则

对同一 `document_version_id + preview_profile_code`，默认只暴露一个当前有效预览代次。选择规则应优先看：

- 预览代次状态是否 `READY`
- 页级清单是否完整
- 文本层是否达到该场景最低可用要求
- 是否存在仍有效的校正覆盖层

主档级或聚合视图层只记录“当前是否有可用预览”和“当前默认代次摘要”，不保存页级明细或文本层真相。

### 7.3 缓存失效触发

以下事件必须触发正式失效判断：

- `DocumentVersion` 被新版本替代为非默认主版本
- 新预览代次接管默认暴露资格
- 页级坐标系变化
- 文本层校验摘要变化
- 校正覆盖层被撤销、替换或证据失效

## 8. 失败回退与降级边界

### 8.1 回退优先级

预览失败时，平台内正式回退顺序应为：

1. 同一版本下最近一个仍可信的当前有效或可回退代次
2. 同一版本下仅文本层缺失但页视图仍可读的阅读降级视图
3. 保留文档可访问但提示“当前仅支持受控下载或原件查看”的平台内降级入口

回退的核心原则是“保持平台内受控可读”，而不是“保证永远存在完整预览能力”。

### 8.2 不允许的回退方式

- 不允许把上一版 `DocumentVersion` 的预览静默当作当前版本预览
- 不允许把第三方引擎的临时输出在未登记状态下直接作为正式页面返回
- 不允许因为文本层失败就把任意 `OCR` 文本直接覆盖到阅读视图上

### 8.3 需人工介入的场景

以下情况超出自动回退边界：

- 源文件损坏，导致所有预览代次均无法重建
- 文本层与页坐标持续失配，影响批注、签章或法律意义定位
- 校正证据相互冲突，无法自动判定哪一层应默认生效

## 9. 文本层校正策略

### 9.1 校正的正式定位

文本层校正的目标不是“把识别得更像原文”，而是“让当前预览视图上的文本定位结果在平台正式语义内可解释、可追溯、可回退”。

因此，校正必须满足：

- 必须基于具体页和具体文本层快照
- 必须带证据来源和责任主体
- 必须可以撤销和替换
- 必须能解释其是否已影响批注锚点、高亮和辅助检索结果

### 9.2 校正传播规则

校正生效后，只允许影响以下消费面：

- 当前预览文本选区结果
- 批注文本锚点重算候选
- 页内命中高亮
- 辅助检索摘要

校正默认不直接改写：

- 原始 `DocumentVersion`
- `OCR` 域正式识别结果
- 搜索域既有正式索引

若其他能力域要消费校正结果，必须通过重新挂接或显式重建进入各自闭环。

## 10. 审计与重建边界

### 10.1 最小审计单元

预览与文本层至少要按以下键留痕：

- `document_asset_id`
- `document_version_id`
- `preview_profile_code`
- `preview_generation_no`
- `page_no`（如是页级事件）
- `render_checksum` / `text_layer_checksum`
- `correction_no`（如是校正事件）
- `job_id` / `operator_id`
- `result_status`

### 10.2 必须记录的关键事件

- 预览代次创建
- 页级切片生成成功 / 失败
- 文本层抽取成功 / 失败
- 当前有效预览代次切换
- 文本层校正生效、替换、撤销
- 预览失效、回退、重建、人工驳回

### 10.3 可重建边界

在源 `DocumentVersion` 仍可访问、对象生命周期仍允许、必要校验摘要仍在的前提下，以下对象允许重建：

- 预览代次
- 页级切片
- 文本层基础快照
- 主档级预览摘要与聚合缓存

### 10.4 不承诺自动重建的边界

以下内容超出本文自动重建承诺：

- 已被人工撤销且缺乏有效证据的校正结果
- 已影响外部签章、外部归档或外部取证结论的历史定位快照
- 源文件已不可恢复时的完整预览能力恢复

文档中心在这些场景下承诺的是：保留正式审计链、标记失效边界、恢复可解释性，而不是伪造一个看似成功的预览结果。

## 11. 与其他专项设计的边界

### 11.1 与对象存储与生命周期设计的边界

- 本文只定义预览与文本层派生对象如何成立、如何失效、如何回退、如何重建
- 派生对象具体落在哪个存储层、如何冷热迁移、如何清理，由对象存储与生命周期专项设计承接

### 11.2 与 `OCR` / 搜索 / 签章 / 归档挂接设计的边界

- 本文定义文本层与页坐标如何成为这些能力的输入基础之一
- 这些能力如何登记绑定、如何回写各自结果、何时重挂，由挂接专项设计承接

### 11.3 与批注锚点设计的边界

- 本文定义页级坐标与文本层快照的正式来源
- 批注锚点如何序列化、如何跨版本重定位，由后续批注锚点专项设计承接

## 12. 本文结论

预览与文本层要作为文档中心文件真相源上的正式派生能力成立，关键不在于选择哪一个渲染引擎，而在于建立一套围绕 `DocumentVersion` 的正式派生模型：预览代次可重建但不可越权、页级切片统一页语义、文本层服务阅读与锚定但不改写原文、校正有覆盖层但不覆盖真相、失败可回退但边界清晰、审计与重建都能回到同一版本锚点。

只有这样，预览与文本层才不会沦为“看起来能打开文件”的临时功能，而能成为 `document-center` 上稳定、可治理、可追溯、可被其他能力安全消费的正式派生能力。

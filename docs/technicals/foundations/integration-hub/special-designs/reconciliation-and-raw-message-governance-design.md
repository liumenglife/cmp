# 外围系统集成主线专项设计：对账与原始报文治理

## 1. 文档说明

本文档下沉 `integration-hub` 主线中的对账治理与原始报文治理，回答以下边界内问题：

- 对账主对象如何定义，任务、记录、差异如何稳定标识
- 原始报文如何作为正式证据被引用、脱敏、审计和长期追溯
- 差异如何从发现、分流、恢复到关闭形成闭环
- 人工台账、恢复工单、审计留痕之间如何互相引用和回写

本文是以下文档的下游专项设计：

- [`integration-hub Detailed Design`](../detailed-design.md)

本文不展开以下内容：

- 不重写外围系统对外 `API` 路径、协议字段、错误码和回调格式
- 不写对象存储脚本、生命周期策略配置步骤或实施级运维手册
- 不写页面原型、按钮交互、排期、角色分工和上线计划
- 不写类名、表索引、SDK 封装和批处理代码细节

## 2. 设计目标

- 为 `integration-hub` 提供统一的对账主对象模型，避免每个外部系统各自定义“差异对象”
- 为入站、出站、回调、附件建立统一证据模型，保证证据可追溯、可脱敏、可审计
- 为差异处理定义明确状态流转和升级条件，避免长期停留在“待人工确认”
- 把恢复工单、人工台账和审计事件收敛到同一引用闭环内，避免三套记录失联

## 3. 适用范围与治理对象

本文覆盖 `integration-hub` 内以下治理对象：

- `ih_inbound_message`、`ih_outbound_dispatch`、`ih_callback_receipt` 的交换摘要与证据引用
- 周期性或触发式对账任务、对账记录、差异记录及其关闭结果
- 原始报文证据对象、脱敏视图、附件引用、访问审计引用
- 人工台账项、恢复工单、与其相关的审计留痕

统一边界如下：

- 原始报文治理归属始终在 `integration-hub`
- 文档中心可消费正式附件对象，但不接管交换报文证据
- 对账负责判断“平台交换认知”和“外部认知”是否一致，不替代业务域对最终业务状态的主判定

## 4. 对账与原始报文的关系

### 4.1 对账主对象模型

`integration-hub` 的对账不是围绕“某一条消息”展开，而是围绕“同一个被集成对象在平台侧与外部侧是否一致”展开。统一主对象如下：

1. `ReconciliationTask`
   一次实际执行的对账任务，表示在某个时间窗内对一批主对象进行扫描。
2. `ReconciliationRecord`
   某次任务中，对某一个对账主对象得到的一次对账结果。
3. `ReconciliationDiff`
   某条对账记录下识别出的一个具体差异点；一个记录可拆出多条差异。

最小稳定标识必须满足以下约束：

- `reconciliation_task_id`：任务内部唯一主键；同一任务还必须具备可去重自然键 `system_name + scope_type + scope_key + window_start + window_end + task_kind`
- `reconciliation_subject_key`：对账主对象稳定键；用于跨任务归并同一对象的差异历史
- `reconciliation_record_id`：某次任务对某个 `reconciliation_subject_key` 的一次结果主键
- `diff_id`：单条差异主键；同一主对象在同一基线版本下重复发现同类差异时，必须复用同一 `diff_identity_key`

`reconciliation_subject_key` 的生成优先级必须固定，禁止不同系统各自定义：

1. 优先使用有效 `binding_id`
2. 无有效绑定时，使用 `system_name + object_type + normalized_external_object_key`
3. 外部对象键也缺失时，退化为 `system_name + object_type + platform_object_id`

一旦某个主对象已经形成 `reconciliation_subject_key`，后续任务不得因单次缺字段而更换主键，只能补证据或补绑定。

### 4.2 比较基线与对象关系

对账比较不是直接拿“平台业务对象”和“外部原始报文”逐字段硬比，而是先落到统一基线：

- 交换记录：入站、出站、回调等一次次交换事件，是事实证据
- 业务对象：合同、审批实例、文档等平台域对象，是平台侧业务归属
- 绑定关系：连接平台对象与外部对象的正式映射，是对账归位主锚点
- 外部对象键：外部系统给出的对象标识，是外部侧归位主锚点

统一比较顺序如下：

1. 先用 `binding_id` 或 `normalized_external_object_key` 把交换记录归并到同一 `reconciliation_subject_key`
2. 在该主对象下分别抽取“平台当前基线快照”和“外部当前基线快照”
3. 以快照比较结果生成差异，不直接把历史尝试当作当前状态

平台当前基线快照由以下对象组合形成：

- 当前有效绑定关系
- 当前平台业务对象摘要
- 该主对象下最新有效交换结论
- 与该交换结论关联的证据组摘要

外部当前基线快照由以下对象组合形成：

- 当前可确认的外部对象键
- 最新有效外部回执、回调或外部侧状态摘要
- 与外部结论对应的证据组摘要

### 4.3 多次出站、多次回调、多次重试的基线规则

同一主对象存在多次出站、重试、补发或重复回调时，必须区分“当前基线”和“历史证据”：

- 同一 `dispatch` 的重试链中，最后一个进入有效终态且未被后续人工重放替代的尝试，是当前传输基线
- 之前失败、超时、被替代的尝试，全部保留为历史证据，不得覆盖
- 回调或入站消息若通过签名校验、关联到同一主对象且事件序号更高，则覆盖此前回调成为当前外部基线
- 重复回调、乱序回调、无效回调只作为历史证据或差异来源，不能直接替换当前基线
- 人工重放成功后，只能将本次重放形成的新交换尝试提升为当前基线；原始失败尝试仍保留为证据

当前基线的选择优先级必须固定为：

1. 最新有效回调或入站确认事件
2. 若无确认事件，则取最新有效出站终态
3. 若无有效终态，则主对象进入“证据不足 / 状态未定”差异，而不是强行认定成功或失败

## 5. 对账治理模型

### 5.1 对账任务、记录、差异的最小结构

对账任务层必须至少包含：

- `reconciliation_task_id`
- `task_kind`：如周期扫描、触发扫描、人工复核
- `system_name`
- `scope_type` 与 `scope_key`
- `window_start`、`window_end`
- `task_status`
- `baseline_version`

对账记录层必须至少包含：

- `reconciliation_record_id`
- `reconciliation_task_id`
- `reconciliation_subject_key`
- `binding_id`、`platform_object_id`、`normalized_external_object_key` 中至少一个有效锚点
- `platform_snapshot_ref`
- `external_snapshot_ref`
- `record_result`：一致、不一致、证据不足、无法归位

差异记录层必须至少包含：

- `diff_id`
- `reconciliation_record_id`
- `diff_identity_key`
- `diff_type`
- `severity`
- `baseline_fingerprint`
- `primary_evidence_group_id`
- `current_state`
- `resolution_channel`

### 5.2 差异分类

正式差异类型收口为以下几类：

- `MISSING_ON_PLATFORM`：外部已有有效事实，但平台侧没有对应交换或受理痕迹
- `MISSING_ON_EXTERNAL`：平台侧已发起有效交换，但外部侧没有对应受理、结果或回执
- `STATUS_MISMATCH`：平台当前状态与外部当前状态不一致
- `SEQUENCE_MISMATCH`：事件顺序、版本顺序、回调顺序不一致
- `BINDING_MISMATCH`：绑定缺失、错误或漂移，导致对象无法正确归位
- `PAYLOAD_MISMATCH`：关键业务摘要字段不一致
- `EVIDENCE_GAP`：主表有交换结论，但证据链缺失、不可读或引用失效

差异类型必须面向治理动作，而不是面向页面展示文案。页面文案可派生，底层类型不可随系统自定义漂移。

### 5.3 基线版本与历史证据分离

一次对账只比较一个 `baseline_version`。`baseline_version` 必须由以下输入组合确定：

- 主对象稳定键
- 平台当前基线快照版本
- 外部当前基线快照版本
- 绑定关系版本

只要上述任一输入变化，就应形成新的 `baseline_version`。历史对账记录不得被覆盖，只能新增新版本并保留旧版本作为证据。

## 6. 原始报文治理模型

### 6.1 证据对象与稳定引用关系

原始报文治理统一拆为四层引用，四层必须能稳定反查：

1. 主表摘要层
   交换主表中的轻量摘要和证据引用入口。
2. `EvidenceGroup`
   围绕一次交换尝试或一次外部事件归并的一组证据容器。
3. `EvidenceObject`
   组内单个证据对象，如请求正文、响应正文、回调正文、附件、失败快照。
4. 审计引用层
   记录谁在什么场景下访问、导出、比对、重放了哪一个证据对象或脱敏视图。

稳定引用关系必须如下：

- 主表摘要行持有 `evidence_group_id` 作为正式入口，不直接持有对象存储路径作为唯一入口
- 父文档中的 `ih_inbound_message`、`ih_outbound_dispatch`、`ih_callback_receipt`、`ih_integration_audit_event` 必须把 `evidence_group_id` 作为正式摘要层字段承接，避免证据入口只停留在专项设计层
- 每个 `EvidenceObject` 持有 `evidence_object_id` 与 `evidence_group_id`
- 每个脱敏视图持有 `masked_view_id`，并且必须回指唯一 `evidence_object_id`
- 每个审计引用持有 `audit_ref_id`，并且必须回指 `evidence_group_id` 或 `evidence_object_id`
- 对账记录、差异记录、人工台账、恢复工单统一只引用 `evidence_group_id`、`evidence_object_id`、`masked_view_id`、`audit_ref_id`，不得直接写对象存储物理地址

### 6.2 `EvidenceGroup` 归并规则

请求、响应、回调、附件引用是否归入同一证据组，必须按“是否属于同一交换尝试 / 同一外部事件闭环”判断：

- 同一次出站尝试产生的请求、同步响应、传输附件、失败快照，必须归入同一 `evidence_group_id`
- 能明确关联到该次出站尝试的回调，必须并入该 `evidence_group_id`
- 无法可靠关联到既有出站尝试的回调，必须单独形成新的 `evidence_group_id`，并通过 `reconciliation_subject_key` 与原链路关联
- 一次人工重放形成新的交换尝试时，必须生成新的 `evidence_group_id`，不得把重放证据塞回原失败证据组

因此，`EvidenceGroup` 不是“一个对象的一生”，而是“一个可说明单次交换结论的证据闭包”。一个对账主对象可关联多个证据组。

### 6.3 主表摘要层与证据层字段边界

以下字段必须保留在主表摘要层：

- 主键、`trace_id`、方向、系统名、发生时间、受理时间、终态时间
- `binding_id`、`platform_object_id`、`normalized_external_object_key`
- `evidence_group_id`
- 原始报文大小、介质类型、敏感等级、哈希摘要
- 传输结论、签名校验结论、脱敏视图可用标记

以下内容只能保留在证据层，不得长期放入主表摘要层：

- 请求正文、响应正文、回调正文的完整文本或二进制
- 附件原件、压缩包、解包后的正文副本
- 密钥材料、签名串原文、票据原文、完整身份信息
- 为恢复而生成的补丁输入正文

主表摘要层的职责是“定位、归位、筛选、跳转”；证据层的职责是“留存、比对、恢复、复盘”。两层不得混写。

## 7. 命名、分桶、保留与检索

### 7.1 命名与分桶约束

原始证据命名只服务于稳定治理，不服务于人工拼接业务语义。对象命名必须至少体现：

- 环境
- 系统归属
- 方向或证据类别
- 日期分区
- `evidence_group_id` 或 `evidence_object_id`
- 版本号或尝试号

统一约束如下：

- 对象名中不得出现合同名称、供应商名称、手机号、身份证号等明文识别信息
- 原始证据区、脱敏视图区、归档区必须分开治理，不得共用默认访问策略
- 同一 `evidence_object_id` 跨冷热层迁移时，逻辑引用不变；变化的只能是底层存储位置

### 7.2 保留边界

保留策略必须服从治理闭环，而不是只服从存储成本。统一边界如下：

- 被未关闭差异引用的证据，不得删除
- 被未关闭人工台账项引用的证据，不得删除
- 被未关闭恢复工单引用的证据，不得删除
- 被审计事件引用且仍在审计复核窗口内的证据，不得删除
- 可删除对象仅限临时缓存、重复副本、推导性中间文件，不包括正式证据对象

### 7.3 检索入口

正式检索入口至少支持：

- 按 `trace_id` 串联交换、对账、恢复、审计
- 按 `reconciliation_subject_key` 查看同一主对象的全部对账历史和证据组
- 按 `binding_id`、`platform_object_id`、`normalized_external_object_key` 精确定位
- 按 `evidence_group_id`、`evidence_object_id`、`masked_view_id`、`audit_ref_id` 反查
- 按外部请求号、回执号、时间窗做受控检索

检索默认只返回摘要层和脱敏视图入口，不默认直出原始正文。

## 8. 差异状态流转规则

### 8.1 正式状态集合

差异状态至少收口为以下状态：

1. `DETECTED`
   已发现差异，尚未完成证据完整性判断。
2. `EVIDENCE_PENDING`
   主对象已归位，但关键证据、绑定或外部键不足，无法做下一步决策。
3. `PENDING_MANUAL_LEDGER`
   已确认需要人工跟进，已进入人工台账。
4. `PENDING_RECOVERY_TICKET`
   已确认需要恢复动作，等待恢复工单创建或审批完成。
5. `RECOVERING`
   恢复工单已启动，正在执行修复、补发、重放或外部协同。
6. `OBSERVING`
   恢复动作已完成，等待下一次对账窗口确认差异是否真正消失。
7. `CLOSED_RESOLVED`
   后续对账已确认差异消失，正式闭环。
8. `CLOSED_ACCEPTED`
   业务或治理方确认差异可接受，不再继续恢复，但保留审计依据。
9. `CLOSED_AUDIT_ONLY`
   差异无需进入人工或恢复，仅做审计归档。

禁止出现长期停留的模糊状态，如“处理中”“待观察”“人工确认中”而没有明确分流去向。

### 8.2 分流判定条件

差异进入人工台账的条件至少满足其一：

- 根因未明，需要人工判断真实归属或真实状态
- 需要业务确认是否接受差异、是否允许补录或跳过
- 需要外部系统协同，但尚不能直接发起恢复动作
- 证据不全但仍有补证据可能

差异升级为恢复工单的条件必须同时满足：

- 已明确恢复目标对象和恢复动作边界
- 已有足够证据支持执行恢复，而不是凭截图或口头结论
- 恢复动作可被记录、回滚或至少可被完整审计
- 严重级别、影响面或闭环要求表明不能只停留在人工备注

差异仅审计归档的条件必须同时满足：

- 不影响当前主对象正式状态
- 不要求人工后续动作或恢复动作
- 后续再次出现时仍可通过相同规则重新发现

### 8.3 恢复工单回写规则

恢复工单关闭后，对账差异必须按以下规则回写：

- 工单成功且后续对账已消除差异：`RECOVERING -> OBSERVING -> CLOSED_RESOLVED`
- 工单成功但尚未经过下一窗口验证：`RECOVERING -> OBSERVING`
- 工单失败：回写为 `PENDING_MANUAL_LEDGER`，并附失败原因与本次证据组引用
- 工单被驳回或取消：回写为 `PENDING_MANUAL_LEDGER` 或 `CLOSED_ACCEPTED`，不得直接写成已解决

`CLOSED_RESOLVED` 必须由新一轮对账结果确认，不能仅凭“工单执行成功”直接写死。

## 9. 脱敏与访问控制

原始报文至少提供三种可见层级：

- `RAW`：完整原始证据
- `MASKED`：可排查但已脱敏的视图
- `SUMMARY`：仅含摘要、哈希、差异点、引用键

统一约束如下：

- 默认查看层级必须是 `SUMMARY` 或 `MASKED`
- 查看 `RAW`、导出 `RAW`、用证据执行重放必须分开授权
- 任何访问、导出、比对、重放都必须形成 `audit_ref_id`
- 脱敏视图必须可重复生成，并标记脱敏规则版本

## 10. 回放 / 重放输入治理

回放用于重建证据视图，重放用于再次驱动交换动作。两者都只能使用正式登记的证据引用：

- 输入只能来自 `evidence_object_id`、`masked_view_id` 或登记后的补丁证据对象
- 个人下载件、聊天转发件、手工编辑文本不能作为正式输入
- 补丁输入必须形成新的证据对象，与原始证据并存，不得覆盖原件
- 任意重放都必须绑定恢复工单，且产出新的 `evidence_group_id`

## 11. 人工台账、恢复工单与审计留痕关系

### 11.1 互相引用键

三类对象的互相引用必须固定：

- 人工台账项必须持有 `ledger_entry_id` 和 `diff_id`
- 恢复工单必须持有 `recovery_ticket_id`、`diff_id`、`result_evidence_group_id`、`last_audit_ref_id`，若由台账升级而来还必须持有 `ledger_entry_id`；存在单对象恢复输入或结果锚点时还必须持有 `result_evidence_object_id`
- 恢复工单若关闭后再次进入新处置轮次，必须新开 `ticket_round_no`；`root_ticket_id` 只用于串联历史，不得复用旧工单充当新轮次主记录
- 审计事件必须持有 `audit_event_id`，并引用 `diff_id`、`ledger_entry_id`、`recovery_ticket_id`、`evidence_group_id` 或 `evidence_object_id` 中的至少一个

父文档中的 `ih_recovery_ticket` 必须把 `diff_id`、`ledger_entry_id`、`result_evidence_group_id`、`result_evidence_object_id`、`last_audit_ref_id` 作为正式结构化字段承接，并固定语义如下：

- `diff_id`：恢复工单当前处置轮次所针对的正式对账差异主键；直接从差异建单时为必填
- `ledger_entry_id`：恢复工单所承接的人工台账项主键；仅在由人工台账升级为恢复工单时填写，不允许伪造“必有台账”关系
- `result_evidence_group_id`：恢复工单当前处置轮次最终回写的正式证据组引用；凡发生补发、重放、补证据或失败固化，关闭前必须可反查到该字段
- `result_evidence_object_id`：恢复工单当前轮次直接作为恢复输入、补丁输入或最终结果锚点的证据对象引用；没有单对象锚点时可为空，但不得替代 `result_evidence_group_id`
- `last_audit_ref_id`：恢复工单最近一次执行、访问、导出、比对、重放或关闭动作形成的正式审计引用；用于与证据访问链路统一反查

禁止只靠备注文本写“关联某工单/某台账”。正式关联必须可结构化查询。

### 11.2 升级条件与关闭条件

人工台账的升级条件：

- 已形成明确恢复动作
- 已指定执行责任和审批边界
- 继续停留在人工备注会导致闭环失控或遗漏

人工台账的关闭条件：

- 关联差异进入 `CLOSED_RESOLVED`、`CLOSED_ACCEPTED` 或 `CLOSED_AUDIT_ONLY`
- 关闭时必须回写最终结论、最后引用的 `audit_event_id` 和是否产生恢复工单

恢复工单的关闭条件：

- 已记录执行动作、执行人、执行时间、执行结果
- 已回写 `result_evidence_group_id`，且需要单对象锚点时已同步回写 `result_evidence_object_id`
- 已回写 `last_audit_ref_id`
- 已回写差异状态为 `OBSERVING`、`PENDING_MANUAL_LEDGER` 或 `CLOSED_ACCEPTED`

审计留痕的关闭规则：

- 审计事件本身不承担“待办关闭”职责
- 但每个差异分流、台账升级、工单关闭、原始报文访问或重放，都必须形成可反查的审计事件

### 11.3 闭环规则

完整闭环必须满足以下顺序：

1. 差异发现后生成或复用 `diff_id`
2. 需要人工跟进时创建人工台账项，并回写 `ledger_entry_id`
3. 需要恢复动作时从差异或台账升级生成恢复工单，并回写 `recovery_ticket_id`
4. 恢复动作执行后写回新证据组、执行审计和差异状态
5. 下一轮对账确认结果后，正式关闭差异，并同步关闭台账项

因此：

- 人工台账是待办载体，不是最终结论载体
- 恢复工单是执行载体，不是对账真相源
- 对账差异状态才是闭环主状态
- 审计事件负责证明每一次访问、升级、执行、关闭确实发生过

## 12. 一致性检查结论

- 本文已补齐对账主对象模型、证据对象模型、差异状态流转规则以及人工台账 / 恢复工单 / 审计留痕闭环关系
- 本文已删除或压缩主要空泛表述，保留的是可直接约束后续详细设计与实现的规则
- 本文仍保持在 `integration-hub` 专项设计边界内，且父文档关系只列出 [`integration-hub Detailed Design`](../detailed-design.md)

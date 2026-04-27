# 外围系统集成主线专项设计：外部字段映射治理

## 1. 文档说明

本文档用于继续下沉 `integration-hub` 主线中“外部字段映射治理”能力，重点回答以下问题：

- 各外围系统的字段映射表、码表字典与值转换规则应如何统一建模
- 映射规则如何做版本治理、灰度发布、审计追溯、回放与失败补偿
- `OA`、企业微信、`CRM`、`SF`、`SRM`、`SAP` 的差异应如何在统一治理框架中落地

本文是以下文档的下游专项设计：

- [`integration-hub Detailed Design`](../detailed-design.md)

`workflow-engine`、`identity-access`、`contract-core`、`document-center` 是本专项设计的强依赖或关键消费方：前者依赖审批与回调字段归一，后者分别消费组织身份映射、合同投影映射与附件引用映射。但这些关系属于跨模块依赖关系，不属于本文档的父文档关系，也不构成本文档的上游来源。

本文不展开以下内容：

- 不重写外围系统对外 `API` 路径、请求体、响应体与签名协议
- 不写外围系统完整字段全集、联调脚本或厂商接口手册；首批实施字段基线在本文第 6 节固定，后续扩展按相同矩阵维护
- 不写管理端页面交互稿、实施排期、责任分工与上线步骤
- 不写具体类图、数据库索引细节、脚本实现或最终代码方案

## 2. 设计目标

- 为 `OA`、企业微信、`CRM`、`SF`、`SRM`、`SAP` 建立统一的字段映射治理模型，避免映射规则散落在各适配器代码中
- 保证字段映射、码表映射、值转换、空值处理、冲突判断都可以被版本化、审计、回放与重算
- 让业务模块只消费平台统一字段语义，不直接理解外围系统私有字段命名与枚举口径
- 在不牺牲统一治理的前提下，允许不同系统保留必要的字段差异与发布节奏
- 将失败补偿边界限制在“交换链路和映射结果修复”，不越界承诺外部系统本体修复

## 3. 适用范围与系统分组

本专项设计覆盖 `integration-hub` 内所有需要“外部字段 -> 平台统一字段”或“平台统一字段 -> 外部字段”转换的场景，包括入站、出站、回调三类链路。

各系统治理重点如下：

- `OA`：重点处理审批表单字段、审批结果字段、实例标识、节点状态与摘要字段映射
- 企业微信：重点处理组织 / 人员字段、消息模板变量、轻量动作回执字段与渠道状态映射
- `CRM` / `SF`：重点处理客户、商机、销售订单、合同来源信息及其状态字段映射
- `SRM`：重点处理供应商主数据、采购协同字段、供应侧合同关联字段映射
- `SAP`：重点处理财务编码、主数据编码、单据摘要字段、业务状态码与金额时间格式映射

统一原则如下：

- 上述系统都属于映射规则的来源或消费对象，但都不是本文档父文档
- 统一治理只定义建模方式、版本方式、发布方式与审计方式，不把每个系统写成实施级配置手册
- 映射规则的归属始终在 `integration-hub`，其他模块只消费归一后的结果

## 4. 统一映射模型

### 4.1 分层模型

字段映射治理统一拆为四层：

1. `CanonicalFieldModel`：平台统一字段模型，定义平台认可的标准字段名、标准类型、标准枚举和值域约束
2. `SystemFieldProfile`：外围系统字段画像，定义某系统某对象可被映射的字段、字段语义、是否必填、是否可回写
3. `MappingRuleSet`：字段映射规则集，描述源字段与目标字段之间的映射、转换、默认值、空值策略、优先级与生效范围
4. `DictionaryRuleSet`：码表与枚举规则集，描述外部值、平台标准值、展示值、别名、停用值与兼容值关系

四层职责必须分离：

- 平台统一字段模型负责定义“平台认什么”
- 系统字段画像负责定义“外部系统有什么”
- 映射规则集负责定义“怎么映过去”
- 码表规则集负责定义“值怎么归一”

### 4.2 核心对象

统一治理中至少存在以下概念对象：

- `mapping_domain`：映射领域，如 `CONTRACT_HEADER`、`APPROVAL_SUMMARY`、`ORG_USER`、`MASTER_DATA`
- `object_type`：对象类型，如合同、审批实例、用户、部门、供应商、客户
- `direction`：`INBOUND`、`OUTBOUND`、`CALLBACK`
- `system_name`：外部系统标识
- `field_rule`：单字段映射规则
- `dictionary_rule`：单码表规则
- `rule_version`：规则版本标识
- `release_window`：规则发布窗口与灰度范围

统一约束如下：

- 字段映射与码表映射都必须绑定 `system_name + object_type + direction + rule_version`
- 单字段规则只描述字段归一，不直接承载业务状态机
- 业务是否承接、是否入库、是否推进状态，仍由下游真相源判断，不由字段映射规则替代

## 5. 字段映射规则结构

### 5.1 单字段规则要素

每条 `field_rule` 至少需要描述：

- `source_field_path`：源字段路径，支持结构化层级路径
- `target_field_code`：目标统一字段编码
- `value_type`：目标值类型，如 `STRING`、`ENUM`、`BOOLEAN`、`DECIMAL`、`DATETIME`、`ARRAY`
- `required_level`：`REQUIRED`、`OPTIONAL`、`DERIVED`
- `transform_chain`：值转换链，例如裁剪、格式归一、时间转换、金额标准化、布尔归一
- `dictionary_ref`：如涉及码表或枚举，引用对应 `dictionary_rule`
- `null_policy`：空值处理策略
- `conflict_policy`：冲突处理策略
- `default_value_policy`：默认值策略
- `effective_scope`：适用租户、组织、接口版本或灰度范围

### 5.2 组合字段与派生字段

并非所有字段都是一对一映射，统一支持以下三类：

- 直接映射：一个源字段直接落到一个统一字段
- 组合映射：多个源字段组合生成一个统一字段，例如名称 + 编码、日期 + 时区
- 派生映射：源字段先经过归一和字典映射，再得到平台派生字段，例如审批结论、合同来源分类、组织层级类型

治理要求如下：

- 组合与派生规则必须显式声明参与字段，不能在适配器中隐式拼接
- 派生规则只做值转换，不写业务流程判断
- 任意映射结果都应能回溯到源字段证据链

## 6. 码表字典与枚举归一

### 6.1 首批落地字段矩阵

首批字段矩阵作为实现基线，不替代外围系统完整接口文档。后续新增字段必须沿用同一列定义，确保每条规则都能落到 `field_rule`、`dictionary_rule`、映射版本和证据链。

| 外部系统 | 方向 | 对象类型 | 源字段路径 | 目标字段编码 | 值类型 | 必填级别 | 码表引用 | 空值策略 | 冲突策略 | 映射版本 | 证据引用 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `OA` 审批系统 | `OUTBOUND` | 审批申请 | `contract.header.contract_no` | `approval.request.contract_no` | `STRING` | `REQUIRED` | 无 | `REJECT` | `BLOCK_AND_AUDIT` | `oa-approval-v1` | `ih_outbound_dispatch.evidence_group_id` |
| `OA` 审批系统 | `OUTBOUND` | 审批申请 | `workflow.summary.starter_user_id` | `approval.request.applicant_ref` | `STRING` | `REQUIRED` | `DICT_IDENTITY_CONFIRMED_REF` | `WAIT_ENRICH` | `USE_IDENTITY_CONFIRMED_REF` | `oa-approval-v1` | `ih_integration_binding.binding_id` |
| `OA` 审批系统 | `CALLBACK` | 审批结果 | `payload.approvalStatus` | `workflow.approval_status` | `ENUM` | `REQUIRED` | `DICT_OA_APPROVAL_STATUS` | `REJECT` | `MARK_CONFLICT_WAIT_MANUAL` | `oa-callback-v1` | `ih_callback_receipt.evidence_group_id` |
| 企业微信 | `INBOUND` | 已确认主体引用 | `user.userid` | `identity.external_identity_key` | `STRING` | `REQUIRED` | 无 | `REJECT` | `HANDOFF_IDENTITY_PRECHECK` | `wecom-identity-v1` | `ia_protocol_exchange.protocol_exchange_id` |
| 企业微信 | `INBOUND` | 已确认组织引用 | `department.id` | `identity.org_unit_confirmed_ref` | `STRING` | `REQUIRED` | `DICT_ORG_UNIT_CONFIRMED_REF` | `WAIT_ENRICH` | `HANDOFF_IDENTITY_ORG_RESOLUTION` | `wecom-org-v1` | `ia_org_unit.org_unit_id` |
| 企业微信 | `CALLBACK` | 消息回执 | `payload.MsgID` | `notification.external_message_ref` | `STRING` | `REQUIRED` | 无 | `REJECT` | `KEEP_FIRST_AND_AUDIT` | `wecom-message-v1` | `ih_callback_receipt.callback_receipt_id` |
| `CRM` 客户系统 | `INBOUND` | 客户主数据 | `customer.accountId` | `customer.external_customer_id` | `STRING` | `REQUIRED` | 无 | `REJECT` | `KEEP_EXTERNAL_FACT_WAIT_DOWNSTREAM` | `crm-customer-v1` | `ih_inbound_message.evidence_group_id` |
| `CRM` 客户系统 | `INBOUND` | 客户主数据 | `customer.accountName` | `customer.customer_name` | `STRING` | `REQUIRED` | 无 | `WAIT_ENRICH` | `KEEP_LATEST_WITH_EVIDENCE` | `crm-customer-v1` | `ih_inbound_message.inbound_message_id` |
| `CRM` 客户系统 | `INBOUND` | 客户主数据 | `customer.levelCode` | `customer.customer_level` | `ENUM` | `OPTIONAL` | `DICT_CRM_CUSTOMER_LEVEL` | `SET_NULL` | `MARK_CONFLICT_WAIT_MANUAL` | `crm-customer-v1` | `ih_object_mapping.mapping_id` |
| `SRM` 供应商系统 | `INBOUND` | 供应商主数据 | `supplier.supplierCode` | `supplier.external_supplier_id` | `STRING` | `REQUIRED` | 无 | `REJECT` | `KEEP_EXTERNAL_FACT_WAIT_DOWNSTREAM` | `srm-supplier-v1` | `ih_inbound_message.evidence_group_id` |
| `SRM` 供应商系统 | `INBOUND` | 供应商主数据 | `supplier.supplierName` | `supplier.supplier_name` | `STRING` | `REQUIRED` | 无 | `WAIT_ENRICH` | `KEEP_LATEST_WITH_EVIDENCE` | `srm-supplier-v1` | `ih_inbound_message.inbound_message_id` |
| `SRM` 供应商系统 | `INBOUND` | 供应商主数据 | `supplier.status` | `supplier.supplier_status` | `ENUM` | `REQUIRED` | `DICT_SRM_SUPPLIER_STATUS` | `REJECT` | `MARK_CONFLICT_WAIT_MANUAL` | `srm-supplier-v1` | `ih_object_mapping.mapping_id` |
| `SAP` 财务系统 | `INBOUND` | 财务主数据 | `finance.companyCode` | `finance.company_code` | `STRING` | `REQUIRED` | `DICT_SAP_COMPANY_CODE` | `REJECT` | `KEEP_EXTERNAL_FACT_WAIT_DOWNSTREAM` | `sap-finance-v1` | `ih_inbound_message.evidence_group_id` |
| `SAP` 财务系统 | `INBOUND` | 财务主数据 | `finance.costCenter` | `finance.cost_center_code` | `STRING` | `REQUIRED` | `DICT_SAP_COST_CENTER` | `WAIT_ENRICH` | `MARK_CONFLICT_WAIT_MANUAL` | `sap-finance-v1` | `ih_object_mapping.mapping_id` |
| `SAP` 财务系统 | `INBOUND` | 财务单据摘要 | `document.currency` | `finance.currency_code` | `ENUM` | `REQUIRED` | `DICT_SAP_CURRENCY` | `REJECT` | `KEEP_FIRST_AND_AUDIT` | `sap-finance-v1` | `ih_inbound_message.inbound_message_id` |

矩阵执行约束：

- `OA` 审批字段只形成审批桥接投影，流程实例和节点状态仍由流程引擎承接。
- 企业微信身份和组织字段只生成协议交换引用、已确认主体引用或已确认组织引用；主体准入、绑定预检查、冲突冻结和会话签发由 `identity-access` 承接。
- 客户、供应商、财务字段只形成外部事实投影和映射证据，下游真相源决定是否承接为正式业务对象。
- `证据引用` 必须能回到入站、出站、回调、绑定或身份主线正式对象，不能只写日志文本。

### 6.2 字典模型

`DictionaryRuleSet` 统一用于治理码表、枚举、状态值、布尔别名、地域编码、组织类型编码等值域映射。

每条字典规则至少包括：

- `dictionary_code`：字典编码
- `platform_value`：平台标准值
- `external_value`：外部系统原始值
- `external_aliases`：兼容别名集合
- `display_name`：中文展示名
- `value_status`：`ACTIVE`、`DEPRECATED`、`DISABLED`
- `effective_from` / `effective_to`：生效期
- `fallback_value`：无法命中时的兜底值，可为空

### 6.3 枚举归一原则

- 平台只维护一份标准枚举语义，外部系统值必须向平台枚举归一
- 同一业务含义的别名，如“已审批”“通过”“APPROVED”，必须收口到统一平台值
- 外部系统出现新增枚举时，若未完成映射，不允许静默写入未知平台值
- 已停用外部值可以在兼容窗口内保留别名，但必须标记停用状态与退出时间

### 6.4 各系统字典侧重点

- `OA`：审批动作、审批状态、节点类型、表单控件值
- 企业微信：消息送达状态、用户启停状态、部门状态、动作回执类型
- `CRM` / `SF`：客户等级、商机阶段、合同来源、销售组织口径
- `SRM`：供应商状态、采购类别、协同单据状态
- `SAP`：公司代码、成本中心、凭证类型、币种、税码、会计期间相关枚举

## 7. 空值策略、默认值与格式转换

### 7.1 空值策略

统一支持以下 `null_policy`：

- `REJECT`：关键字段为空时直接拒绝映射结果
- `DROP_FIELD`：丢弃该字段，不影响其他字段映射
- `SET_NULL`：显式写入空值，交由下游判断是否接受
- `USE_DEFAULT`：使用默认值
- `WAIT_ENRICH`：先保留待补全状态，进入后续补齐或人工确认链路

使用原则如下：

- 主键类、绑定类、关键业务状态字段默认优先 `REJECT` 或 `WAIT_ENRICH`
- 可选展示字段、扩展备注字段可采用 `DROP_FIELD` 或 `SET_NULL`
- 不允许在未声明策略时由代码自行猜测空值含义

### 7.2 值转换规则

统一允许但不限于以下转换类型：

- 字符串裁剪、大小写归一、全角半角归一
- 时间格式、时区与日期精度归一
- 金额、小数精度、币种补齐与千分位清洗
- 布尔值归一，如 `Y/N`、`1/0`、`true/false`
- 附件引用、组织路径、层级编码的结构标准化

转换原则如下：

- 转换必须可声明、可追溯、可回放，不能依赖隐藏逻辑
- 无损转换与有损转换必须区分记录
- 一旦发生截断、格式降级或兼容值替换，必须留下转换标记

## 8. 映射版本治理

### 8.1 双版本模型

必须同时维护两类版本：

- `model_version`：平台统一字段模型版本，表示平台标准字段语义是否发生变化
- `mapping_version`：具体系统映射规则版本，表示某系统某对象的字段映射是否发生变化

二者不能混用。

### 8.2 版本规则

- 平台字段语义发生破坏性变化时，提升 `model_version`
- 仅某个系统字段对照或字典值变化时，只提升对应 `mapping_version`
- 历史已生效的入站、出站、回调主记录与审计记录必须保存其命中的 `mapping_version`
- 父文档主表至少在 `ih_inbound_message`、`ih_outbound_dispatch`、`ih_callback_receipt`、`ih_integration_audit_event` 正式承接 `mapping_version` 与 `model_version`，保证历史回放与重放都能解释命中的字段模型和规则集
- 新规则上线前，旧版本规则必须保留一段并行可读期，确保历史回放可解释

### 8.3 兼容与迁移原则

- 同主版本内只允许追加可选字段和别名，不允许原地改变既有字段含义
- 需要破坏性调整时，应新开版本并通过灰度发布切换
- 不允许直接覆盖历史版本后再解释旧消息

## 9. 冲突处理与优先级

### 9.1 冲突来源

字段映射冲突主要来自以下场景：

- 同一外部字段在不同接口口径下语义不同
- 多个外部字段映射到同一统一字段且值不一致
- 同一枚举值在不同系统或不同版本中含义漂移
- 回调值与此前入站 / 出站建立的绑定值不一致

### 9.2 冲突处理原则

- 结构冲突优先阻断映射，不直接落到业务承接层
- 值冲突优先保留证据并进入 `CONFLICT` 或待人工确认状态
- 若同一统一字段存在多条候选规则，按“显式版本范围 > 系统专属规则 > 领域默认规则 > 平台兜底规则”的优先级决策
- 不允许用“最后一次写入覆盖一切”作为默认冲突策略

### 9.3 系统级差异判断

- `OA` 与流程摘要相关字段，优先以审批桥接上下文和实例绑定关系判定冲突
- 企业微信组织 / 人员字段冲突，优先以 `identity-access` 已确认的主体绑定结果为准
- `CRM` / `SF` / `SRM` / `SAP` 主数据冲突，优先保留原始事实与映射证据，交由下游真相源决定是否承接或拒绝

## 10. 灰度发布与生效控制

映射规则发布不能采用“一次性全量切换”。统一要求支持灰度治理。

灰度维度至少包括：

- 按 `system_name`
- 按 `object_type`
- 按接口方向 `INBOUND / OUTBOUND / CALLBACK`
- 按租户、组织或特定外部端点
- 按时间窗口

发布原则如下：

- 新 `mapping_version` 先以只读校验或影子比对方式验证结果差异
- 验证通过后再逐步放量到真实承接链路
- 灰度期内必须可快速回退到上一稳定版本
- 回退只切换规则版本，不修改历史交换记录事实

## 11. 审计追溯与回放 / 重放

### 11.1 审计最小留痕单元

每次映射执行都应留下最小可追溯证据，至少包括：

- `trace_id`
- `system_name`
- `object_type`
- `direction`
- `mapping_version`
- `model_version`
- `evidence_group_id`
- `source_payload_ref` 或摘要
- `mapped_result_snapshot_ref` 或摘要
- `dictionary_hit_list`
- `transform_step_summary`
- `conflict_flag` 与结论

### 11.2 回放与重放边界

- 回放是使用历史源报文和历史规则版本重建当时映射结果，用于解释审计事实
- 重放是使用历史源报文和指定新规则版本重新计算映射结果，用于验证修复效果或恢复链路
- 回放不改变历史业务结论，只解释历史
- 重放是否触发下游承接，必须由恢复策略显式决定，不能默认重新推进业务状态

## 12. 失败补偿边界

字段映射失败后的补偿只覆盖 `integration-hub` 自身边界内的动作：

- 重跑映射规则
- 补齐字典值或绑定值后重新归一
- 切换到修复后的 `mapping_version` 执行重放
- 将无法自动修复的记录转入人工确认或恢复工单

明确不在本专项设计补偿边界内的事项：

- 不承诺直接修改外部系统原始数据
- 不承诺自动修复下游业务模块已经拒绝的业务语义冲突
- 不承诺把字段映射补偿升级为跨系统事务回滚

## 13. 治理落地原则

为避免映射治理再次散落，正式落地时必须满足以下原则：

- 统一字段模型、字段规则、字典规则由 `integration-hub` 统一维护入口
- 适配器只负责调用规则，不负责私自新增隐式映射口径
- 下游模块只消费归一结果和映射证据，不倒逼 `integration-hub` 暴露外部私有字段语义
- 新增系统、新增对象、新增枚举时，优先复用既有字段模型与字典模型，只有确有必要时才扩展标准模型
- 任何映射差异修复都应先修规则，再决定是否触发重放，不允许长期依赖代码特判

## 14. 一致性检查结论

- 本文父文档关系只列出 [`integration-hub Detailed Design`](../detailed-design.md)，未把外部系统或跨模块依赖误写成父文档
- 本文聚焦字段映射治理原则、统一模型、版本、生效、审计与恢复边界，未展开成外部 `API` 文档
- 本文未写实施排期、联调步骤、责任人拆分或代码实现细节，未越界成实施计划或具体代码方案

# 组织架构 / 权限 / 统一认证主线专项设计：解密下载授权

## 1. 文档说明

本文档用于继续下沉 `identity-access` 主线中“解密下载授权”的设计，重点回答以下问题：

- 管理端如何按部门、人员授予解密下载能力
- 解密下载授权是否需要与审批单据联动，以及联动边界在哪里
- 授权生效、撤销、显式拒绝和目标资源数据权限如何叠加
- `decision_id` 如何传给 `encrypted-document` 主线执行导出
- 导出作业、明文包生成与交付为何不属于本主线边界
- 幂等、审计和恢复如何闭环

本文是以下文档的下游专项设计：

- [`identity-access Detailed Design`](../detailed-design.md)

本文不展开以下内容：

- 不设计明文导出包格式、密钥解包、文件水印、过期清理或下载链接生成
- 不写管理端页面交互稿、审批表单字段、前端按钮显隐细节
- 不定义完整审批流程 DSL 或审批节点编排
- 不写实现排期、负责人安排或联调脚本

## 2. 设计目标

- 将解密下载收口为 `SPECIAL:DECRYPT_DOWNLOAD` 授权，不新建第二套高敏授权体系
- 支持管理端按 `ORG_UNIT` 与 `USER` 授权，并支持显式 `DENY`
- 在执行解密下载前同时校验主体、会话、特殊授权、目标资源数据权限和业务上下文
- 将一次命中冻结为 `AuthorizationDecision`，并把 `decision_id` 传给 `encrypted-document`
- 确保授权配置、审批单据、判定命中、导出请求之间可追踪、可审计、可恢复

## 3. 适用范围与边界

解密下载授权适用于平台内受控明文导出例外场景：默认文件进入文档中心后按权限在平台内解密使用，只有命中解密下载授权后，导出的明文文件才可脱离 `CMP` 使用。

统一边界如下：

- `identity-access` 负责“谁是否被授权对哪个目标资源发起解密下载”
- `encrypted-document` 负责“如何解密、如何生成明文导出包、如何交付、如何过期与清理”
- 解密下载授权属于 `ia_permission_grant.permission_type=SPECIAL`，不走菜单权限体系
- 目标资源是否可接触必须叠加 `DOCUMENT` 或 `CONTRACT` 数据权限
- 审批单据可作为授权生效依据，但审批流状态真相仍归 `workflow-engine`

## 4. 核心对象

### 4.1 特殊授权配置

管理端授予解密下载能力时，统一写入 `ia_permission_grant`：

- `permission_type=SPECIAL`
- `permission_code=DECRYPT_DOWNLOAD`
- `grant_target_type=ORG_UNIT` 或 `USER`
- `grant_target_id` 指向 `ia_org_unit.org_unit_id` 或 `ia_user.user_id`
- `resource_type` 使用 `DOCUMENT`、`CONTRACT` 或受控组合口径
- `resource_scope_ref` 指向授权范围摘要或审批依据引用
- `grant_status` 使用 `ACTIVE`、`DISABLED`、`REVOKED`
- `effect_mode` 使用 `ALLOW` 或 `DENY`
- `priority_no`、`effective_start_at`、`effective_end_at` 控制解释顺序和时效

### 4.2 数据权限对象

解密下载不是只看特殊授权，还必须叠加 `ia_data_scope`：

- 对文档明文导出，校验 `resource_type=DOCUMENT`
- 对合同关联文档或合同包导出，校验 `resource_type=CONTRACT`，必要时再校验关联文档可见性
- 若授权范围依赖组织规则，命中结果必须冻结 `ia_org_rule_version` 与 `ia_org_rule_resolution_record`

### 4.3 判定冻结对象

每次解密下载请求都必须生成或复用请求级 `ia_authorization_decision`：

- `action_code=SPECIAL:DECRYPT_DOWNLOAD`
- `resource_type=DOCUMENT` 或 `CONTRACT`
- `resource_id` 指向目标文档、合同或导出集合的受控资源标识
- `decision_result` 为 `ALLOW` 或 `DENY`
- 命中依据落到 `ia_authorization_hit_result`

`decision_id` 是传给 `encrypted-document` 的正式授权凭据，不把 `ia_permission_grant` 原始配置直接暴露给导出执行侧。

## 5. 授权配置规则

### 5.1 按部门授权

按部门授权时：

- `grant_target_type=ORG_UNIT`
- `grant_target_id=ia_org_unit.org_unit_id`
- 授权对象包括该部门当前有效成员
- 是否包含下级部门不由 `grant_target_type` 暗含，必须由 `resource_scope_ref` 或配套数据范围明确表达

部门授权命中时，需读取 `ia_org_membership` 判断用户是否仍为该部门有效成员。人员离开部门后，新请求不得继续命中该部门授权；已冻结的导出作业按作业侧快照边界处理。

### 5.2 按人员授权

按人员授权时：

- `grant_target_type=USER`
- `grant_target_id=ia_user.user_id`
- 只对该平台主体生效，不因外部身份绑定变化自动转移

若用户主体发生归并，原 `user_status=MERGED` 的主体不得继续发起新请求；后续是否补授到保留主体，应形成新的授权记录或人工处置记录，而不是静默改写旧授权。

### 5.3 显式拒绝

显式 `DENY` 用于高敏能力收紧：

- 用户级 `DENY` 可封禁某个人，即使其所在部门存在 `ALLOW`
- 部门级 `DENY` 可封禁某部门范围，即使角色或其他范围存在 `ALLOW`
- `DENY` 命中必须写入 `ia_authorization_hit_result.hit_result=DENY`
- 最终解释遵循显式 `DENY` 高于 `ALLOW`

显式拒绝不得只通过禁用按钮实现，必须落到 `ia_permission_grant.effect_mode=DENY` 或等价正式授权对象。

## 6. 审批单据联动边界

解密下载授权可以要求审批单据作为生效依据，但审批单据不替代授权真相。

联动规则如下：

- 审批流负责审批过程、节点状态、审批意见和最终审批结果
- 审批通过后，由授权管理动作写入或激活 `ia_permission_grant`
- `resource_scope_ref` 可保存审批单据引用、授权原因摘要或受控范围引用
- 审批撤回、驳回或作废时，授权不得自动保持 `ACTIVE`，必须进入 `DISABLED` 或 `REVOKED`
- 临时授权必须有 `effective_end_at`

不要求每次解密下载动作都重新走审批；是否需要“一次性审批、一段时间授权、单资源授权”由管理制度配置决定，但最终执行前都必须回到 `authorization-engine` 判定。

## 7. 授权命中主链路

解密下载授权命中固定为以下 9 步：

1. 用户对文档、合同或导出集合发起解密下载请求
2. 校验 `ia_user.user_status`、会话状态和 `active_org_id`、`active_org_unit_id`
3. 读取 `ia_permission_grant` 中 `SPECIAL:DECRYPT_DOWNLOAD` 的候选授权
4. 展开用户直授、部门授予、角色关联授权，并过滤有效期与 `grant_status`
5. 先计算显式 `DENY`，命中则冻结拒绝结果
6. 对目标资源叠加 `DOCUMENT` 或 `CONTRACT` 数据权限判定
7. 校验业务上下文，例如文档状态、合同状态、密级、审批依据、授权有效窗口
8. 写入 `ia_authorization_decision` 与 `ia_authorization_hit_result`
9. 若 `decision_result=ALLOW`，把 `decision_id` 传给 `encrypted-document` 执行导出

若第 5、6、7 步任一步拒绝，必须返回拒绝原因并写审计事件，不得把请求透传给导出执行侧再失败。

## 8. 目标资源数据权限叠加

特殊授权只表示“具备发起解密下载这种高敏动作的资格”，不表示“对所有文档或合同都有访问范围”。

叠加规则如下：

- `SPECIAL:DECRYPT_DOWNLOAD` 必须先命中有效 `ALLOW`
- 目标 `DOCUMENT` 或 `CONTRACT` 必须在数据权限范围内
- 对合同包导出，应逐项校验合同和关联文档范围，不能只校验合同头
- 若部分资源可导出、部分资源不可导出，应由请求策略决定整体拒绝或只导出允许部分；该策略必须写入判定摘要
- 任何 `ia_data_scope.effect_mode=DENY` 命中都必须阻断对应目标资源

数据权限命中依据应写入 `ia_authorization_hit_result.hit_type=DATA_SCOPE`；若依赖组织规则，还必须记录 `hit_type=ORG_RULE`、`frozen_ref_id` 与 `resolution_record_id`。

## 9. `decision_id` 传递与执行边界

`identity-access` 输出给 `encrypted-document` 的最小凭据为：

- `decision_id`
- `subject_user_id`
- `resource_type`
- `resource_id` 或受控资源集合摘要
- `decision_result=ALLOW`
- `expires_at`
- `request_trace_id`

`encrypted-document` 受理导出前必须回查或校验该 `decision_id`：

- `decision_id` 存在且未过期
- `decision_result=ALLOW`
- `action_code=SPECIAL:DECRYPT_DOWNLOAD`
- 请求主体与冻结主体一致
- 目标资源与冻结资源一致或包含在冻结集合摘要中

导出作业边界如下：

- 明文文件生成、压缩、加水印、加审计标识、存储、下载链接、过期清理归 `encrypted-document`
- 本主线不保存明文文件、不生成下载地址、不管理导出作业状态机
- 导出作业可保存授权冻结快照，但该快照只是执行投影，不成为第二套授权真相

## 10. 生效、撤销与恢复

### 10.1 生效

授权生效必须同时满足：

- `grant_status=ACTIVE`
- 当前时间在 `effective_start_at` 与 `effective_end_at` 范围内
- 授权对象仍有效，用户未 `DISABLED`、`LOCKED`、`MERGED`
- 部门授权对应的 `ia_org_unit.org_status=ACTIVE`
- 如绑定审批单据，审批结果仍为可生效状态

### 10.2 撤销

撤销授权时：

- 更新 `ia_permission_grant.grant_status=REVOKED` 或写入等价撤销事实
- 触发主体、部门、权限摘要和解密下载授权缓存失效
- 新请求必须重新判定并拒绝已撤销授权
- 已冻结且已被 `encrypted-document` 受理的导出作业按执行侧快照继续或终止，由导出作业策略决定，但不得反向改写历史 `AuthorizationDecision`

### 10.3 恢复

恢复时以 `MySQL` 中 `ia_permission_grant`、`ia_data_scope`、`ia_authorization_decision`、`ia_authorization_hit_result` 为准。缓存和作业侧投影可以重建，审计事实不得伪造历史，只能补写恢复动作。

## 11. 幂等与并发

### 11.1 授权配置幂等

授权配置幂等键使用：

`grant_target_type + grant_target_id + permission_type + permission_code + resource_type`

相同幂等键下的重复提交只能更新受控字段或返回既有结果，不得创建多条语义重复的活跃授权。显式 `DENY` 与 `ALLOW` 不应被同一条记录无审计地来回覆盖，应形成可追踪状态变更。

### 11.2 下载请求幂等

解密下载动作判定幂等键使用：

`Idempotency-Key + subject_user_id + resource_type + resource fingerprint`

重复请求命中同一幂等键时，可复用未过期的 `decision_id`；若资源集合、主体、会话或授权摘要发生变化，必须重新判定。

### 11.3 并发控制

- 同一高敏授权项并发修改时，使用 `row_version` 与必要短锁控制
- 授权判定只追加写，不覆盖旧判定
- 撤销与下载请求并发时，以判定冻结时间为界；冻结前撤销生效则拒绝，冻结后进入导出执行侧快照策略

## 12. 审计与观测

必须写入 `ia_identity_audit_event` 的事件包括：

- 解密下载授权授予、撤销、禁用、显式拒绝
- 解密下载判定命中
- 解密下载判定拒绝
- 审批单据联动导致的授权生效或失效
- `decision_id` 被导出执行侧校验失败

最小审计字段包括：

- `actor_type`、`actor_id`
- `target_user_id`
- `target_resource_type`、`target_resource_id`
- `decision_id`
- `permission_grant_id`
- `data_scope_id`
- `trace_id`
- `event_result`
- `event_payload_ref`

事件类型使用 `DECRYPT_DOWNLOAD_HIT`、`DECRYPT_DOWNLOAD_DENIED`、`PERMISSION_GRANTED`、`PERMISSION_REVOKED` 或当前枚举中的等价稳定类型。

## 13. 与其他主线的挂接

### 13.1 与 `encrypted-document`

`encrypted-document` 只消费 `decision_id` 和授权冻结摘要，不解释 `ia_permission_grant`。若导出执行失败，应回写导出作业结果与审计关联，但不改变授权判定事实。

### 13.2 与文档中心

文档中心提供目标文档状态、归属、关联合同、密级等业务上下文。本主线使用这些上下文判定是否允许解密下载，不接管文档真相。

### 13.3 与合同主档

合同主档提供合同归属部门、负责人、合同状态与合同关联文档集合。本主线只使用这些上下文进行数据权限与业务上下文判定。

### 13.4 与流程引擎

审批单据联动时，流程引擎只提供审批结果与单据状态。本主线在授权配置生效前读取结果，不把审批流状态机复制到授权表中。

## 14. 本文结论

解密下载授权是 `identity-access` 内的 `SPECIAL:DECRYPT_DOWNLOAD` 高敏授权判定能力，而不是加密文档主线内部的私有权限开关。

通过 `ia_permission_grant` 承接部门 / 人员授权与显式 `DENY`，通过 `ia_data_scope` 叠加目标资源可接触范围，通过 `ia_authorization_decision` 冻结一次命中并把 `decision_id` 传给 `encrypted-document`，可以在不复制授权真相的前提下完成受控明文导出例外机制。

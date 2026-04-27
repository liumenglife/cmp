# 湖南星邦智能装备股份有限公司合同管理平台文档索引

## 一、索引边界

本索引只同步当前已经正式存在的文档链路，并明确区分以下六类：

1. 总平台正式链路
2. 统一底座 / 应用级模块链路
3. planning 真相文件
4. Superpowers 正式执行基准
5. 过程报告
6. 上游来源材料

本页不把未创建的一级能力写成已存在，也不混入已删除、已归档或仅用于历史留痕的旧影子文档。

## 二、总平台正式链路

总平台正式链路是当前所有底座主线与应用级模块链路的上游入口，阅读和执行时默认先看总平台，再进入对应主线。

正式链路顺序：`Requirement Spec -> Architecture Design -> API Design -> Detailed Design -> Implementation Plan`

1. [docs/specifications/cmp-phase1-requirement-spec.md](./specifications/cmp-phase1-requirement-spec.md)
2. [docs/technicals/architecture-design.md](./technicals/architecture-design.md)
3. [docs/technicals/api-design.md](./technicals/api-design.md)
4. [docs/technicals/detailed-design.md](./technicals/detailed-design.md)
5. [docs/technicals/implementation-plan.md](./technicals/implementation-plan.md)

## 三、统一底座 / 应用级模块链路

`docs/technicals/foundations/` 用于统一底座 / 横切主线；`docs/technicals/modules/` 用于应用级业务模块 / 业务组。两类链路都承接总平台正式链路，不替代总平台主链路入口。

### 3.1 统一底座 / 横切主线

#### 3.1.1 Agent OS

1. [docs/technicals/foundations/agent-os/architecture-design.md](./technicals/foundations/agent-os/architecture-design.md)
2. [docs/technicals/foundations/agent-os/api-design.md](./technicals/foundations/agent-os/api-design.md)
3. [docs/technicals/foundations/agent-os/detailed-design.md](./technicals/foundations/agent-os/detailed-design.md)
4. [docs/technicals/foundations/agent-os/implementation-plan.md](./technicals/foundations/agent-os/implementation-plan.md)

#### 3.1.2 外围系统集成主线

1. [docs/technicals/foundations/integration-hub/architecture-design.md](./technicals/foundations/integration-hub/architecture-design.md)
2. [docs/technicals/foundations/integration-hub/api-design.md](./technicals/foundations/integration-hub/api-design.md)
3. [docs/technicals/foundations/integration-hub/detailed-design.md](./technicals/foundations/integration-hub/detailed-design.md)
4. [docs/technicals/foundations/integration-hub/implementation-plan.md](./technicals/foundations/integration-hub/implementation-plan.md)

#### 3.1.3 身份与权限

1. [docs/technicals/foundations/identity-access/architecture-design.md](./technicals/foundations/identity-access/architecture-design.md)
2. [docs/technicals/foundations/identity-access/api-design.md](./technicals/foundations/identity-access/api-design.md)
3. [docs/technicals/foundations/identity-access/detailed-design.md](./technicals/foundations/identity-access/detailed-design.md)
4. [docs/technicals/foundations/identity-access/implementation-plan.md](./technicals/foundations/identity-access/implementation-plan.md)

### 3.2 应用级业务模块 / 业务组

### 3.2.1 流程引擎

1. [docs/technicals/modules/workflow-engine/architecture-design.md](./technicals/modules/workflow-engine/architecture-design.md)
2. [docs/technicals/modules/workflow-engine/api-design.md](./technicals/modules/workflow-engine/api-design.md)
3. [docs/technicals/modules/workflow-engine/detailed-design.md](./technicals/modules/workflow-engine/detailed-design.md)
4. [docs/technicals/modules/workflow-engine/implementation-plan.md](./technicals/modules/workflow-engine/implementation-plan.md)

### 3.2.2 文档中心 / 文档协作

1. [docs/technicals/modules/document-center/architecture-design.md](./technicals/modules/document-center/architecture-design.md)
2. [docs/technicals/modules/document-center/api-design.md](./technicals/modules/document-center/api-design.md)
3. [docs/technicals/modules/document-center/detailed-design.md](./technicals/modules/document-center/detailed-design.md)
4. [docs/technicals/modules/document-center/implementation-plan.md](./technicals/modules/document-center/implementation-plan.md)

### 3.2.3 合同管理本体

1. [docs/technicals/modules/contract-core/architecture-design.md](./technicals/modules/contract-core/architecture-design.md)
2. [docs/technicals/modules/contract-core/api-design.md](./technicals/modules/contract-core/api-design.md)
3. [docs/technicals/modules/contract-core/detailed-design.md](./technicals/modules/contract-core/detailed-design.md)
4. [docs/technicals/modules/contract-core/implementation-plan.md](./technicals/modules/contract-core/implementation-plan.md)

### 3.2.4 电子签章

1. [docs/technicals/modules/e-signature/architecture-design.md](./technicals/modules/e-signature/architecture-design.md)
2. [docs/technicals/modules/e-signature/api-design.md](./technicals/modules/e-signature/api-design.md)
3. [docs/technicals/modules/e-signature/detailed-design.md](./technicals/modules/e-signature/detailed-design.md)
4. [docs/technicals/modules/e-signature/implementation-plan.md](./technicals/modules/e-signature/implementation-plan.md)

### 3.2.5 加密软件

1. [docs/technicals/modules/encrypted-document/architecture-design.md](./technicals/modules/encrypted-document/architecture-design.md)
2. [docs/technicals/modules/encrypted-document/api-design.md](./technicals/modules/encrypted-document/api-design.md)
3. [docs/technicals/modules/encrypted-document/detailed-design.md](./technicals/modules/encrypted-document/detailed-design.md)
4. [docs/technicals/modules/encrypted-document/implementation-plan.md](./technicals/modules/encrypted-document/implementation-plan.md)

### 3.2.6 合同后续业务组

1. [docs/technicals/modules/contract-lifecycle/architecture-design.md](./technicals/modules/contract-lifecycle/architecture-design.md)
2. [docs/technicals/modules/contract-lifecycle/api-design.md](./technicals/modules/contract-lifecycle/api-design.md)
3. [docs/technicals/modules/contract-lifecycle/detailed-design.md](./technicals/modules/contract-lifecycle/detailed-design.md)
4. [docs/technicals/modules/contract-lifecycle/implementation-plan.md](./technicals/modules/contract-lifecycle/implementation-plan.md)

### 3.2.7 检索 / OCR / AI 业务应用主线

1. [docs/technicals/modules/intelligent-applications/architecture-design.md](./technicals/modules/intelligent-applications/architecture-design.md)
2. [docs/technicals/modules/intelligent-applications/api-design.md](./technicals/modules/intelligent-applications/api-design.md)
3. [docs/technicals/modules/intelligent-applications/detailed-design.md](./technicals/modules/intelligent-applications/detailed-design.md)
4. [docs/technicals/modules/intelligent-applications/implementation-plan.md](./technicals/modules/intelligent-applications/implementation-plan.md)

说明：

1. `foundations` 下收口统一底座 / 横切主线，`modules` 下收口应用级业务模块 / 业务组。
2. 以上链路均为当前已正式存在并已纳入索引的链路。
3. 未在本页列出的能力，不视为当前已经形成正式链路。

## 四、planning 真相文件

以下文件用于同步当前批次真相、关键决策与历史留痕，不属于正式交付链路节点：

1. [docs/planning/current.md](./planning/current.md)
2. [docs/planning/decisions.md](./planning/decisions.md)
3. [docs/planning/history.md](./planning/history.md)

## 五、Superpowers 正式执行基准

以下文件用于开发执行阶段，承接正式技术文档并转化为 Agent 可执行的规格、批次计划、门禁与验证闭环：

1. [docs/superpowers/specs/102-cmp-implementation-execution-spec.md](./superpowers/specs/102-cmp-implementation-execution-spec.md)
2. [docs/superpowers/plans/102-01-batch-1-foundations-implementation-plan.md](./superpowers/plans/102-01-batch-1-foundations-implementation-plan.md)
3. [docs/superpowers/plans/2026-04-26-batch-2-core-chain-implementation-plan.md](./superpowers/plans/2026-04-26-batch-2-core-chain-implementation-plan.md)
4. [docs/superpowers/plans/2026-04-26-batch-3-dependent-business-capabilities-implementation-plan.md](./superpowers/plans/2026-04-26-batch-3-dependent-business-capabilities-implementation-plan.md)
5. [docs/superpowers/plans/2026-04-26-batch-4-intelligent-applications-implementation-plan.md](./superpowers/plans/2026-04-26-batch-4-intelligent-applications-implementation-plan.md)
6. [docs/superpowers/plans/2026-04-26-batch-5-integration-acceptance-release-plan.md](./superpowers/plans/2026-04-26-batch-5-integration-acceptance-release-plan.md)

## 六、过程报告

以下文件用于记录阶段性分析、复核、验收或专题判断，不替代正式技术链路：

1. [查询引擎驱动薄运行内核与外围治理能力分析报告](./reports/Harness/%E6%9F%A5%E8%AF%A2%E5%BC%95%E6%93%8E%E9%A9%B1%E5%8A%A8%E8%96%84%E8%BF%90%E8%A1%8C%E5%86%85%E6%A0%B8%E4%B8%8E%E5%A4%96%E5%9B%B4%E6%B2%BB%E7%90%86%E8%83%BD%E5%8A%9B%E5%88%86%E6%9E%90%E6%8A%A5%E5%91%8A.md)

## 七、上游来源材料

当前正式上游来源材料为：

1. [docs/specifications/湖南星邦智能装备股份有限公司合同管理平台招标技术要求.pdf](./specifications/%E6%B9%96%E5%8D%97%E6%98%9F%E9%82%A6%E6%99%BA%E8%83%BD%E8%A3%85%E5%A4%87%E8%82%A1%E4%BB%BD%E6%9C%89%E9%99%90%E5%85%AC%E5%8F%B8%E5%90%88%E5%90%8C%E7%AE%A1%E7%90%86%E5%B9%B3%E5%8F%B0%E6%8B%9B%E6%A0%87%E6%8A%80%E6%9C%AF%E8%A6%81%E6%B1%82.pdf)

## 八、推荐阅读顺序

### 8.1 新 session / 新成员 / 快速恢复上下文

推荐顺序：先总平台正式链路，再按需进入底座主线 / 应用级模块链路，最后补读 planning 与来源材料。

1. [docs/docs-overview.md](./docs-overview.md)
2. [docs/specifications/cmp-phase1-requirement-spec.md](./specifications/cmp-phase1-requirement-spec.md)
3. [docs/technicals/architecture-design.md](./technicals/architecture-design.md)
4. [docs/technicals/api-design.md](./technicals/api-design.md)
5. [docs/technicals/detailed-design.md](./technicals/detailed-design.md)
6. [docs/technicals/implementation-plan.md](./technicals/implementation-plan.md)
7. 按负责范围补读对应底座主线 / 应用级模块链路：
8. [docs/technicals/modules/workflow-engine/architecture-design.md](./technicals/modules/workflow-engine/architecture-design.md)
9. [docs/technicals/modules/document-center/architecture-design.md](./technicals/modules/document-center/architecture-design.md)
10. [docs/technicals/foundations/agent-os/architecture-design.md](./technicals/foundations/agent-os/architecture-design.md)
11. [docs/technicals/modules/contract-core/architecture-design.md](./technicals/modules/contract-core/architecture-design.md)
12. [docs/technicals/modules/e-signature/architecture-design.md](./technicals/modules/e-signature/architecture-design.md)
13. [docs/technicals/modules/encrypted-document/architecture-design.md](./technicals/modules/encrypted-document/architecture-design.md)
14. [docs/technicals/modules/contract-lifecycle/architecture-design.md](./technicals/modules/contract-lifecycle/architecture-design.md)
15. [docs/technicals/modules/intelligent-applications/architecture-design.md](./technicals/modules/intelligent-applications/architecture-design.md)
16. [docs/technicals/foundations/integration-hub/architecture-design.md](./technicals/foundations/integration-hub/architecture-design.md)
17. [docs/technicals/foundations/identity-access/architecture-design.md](./technicals/foundations/identity-access/architecture-design.md)
18. [docs/planning/current.md](./planning/current.md)
19. [docs/planning/decisions.md](./planning/decisions.md)
20. [docs/planning/history.md](./planning/history.md)
21. [docs/specifications/湖南星邦智能装备股份有限公司合同管理平台招标技术要求.pdf](./specifications/%E6%B9%96%E5%8D%97%E6%98%9F%E9%82%A6%E6%99%BA%E8%83%BD%E8%A3%85%E5%A4%87%E8%82%A1%E4%BB%BD%E6%9C%89%E9%99%90%E5%85%AC%E5%8F%B8%E5%90%88%E5%90%8C%E7%AE%A1%E7%90%86%E5%B9%B3%E5%8F%B0%E6%8B%9B%E6%A0%87%E6%8A%80%E6%9C%AF%E8%A6%81%E6%B1%82.pdf)

### 8.2 开发执行

1. [docs/specifications/cmp-phase1-requirement-spec.md](./specifications/cmp-phase1-requirement-spec.md)
2. [docs/technicals/architecture-design.md](./technicals/architecture-design.md)
3. [docs/technicals/api-design.md](./technicals/api-design.md)
4. [docs/technicals/detailed-design.md](./technicals/detailed-design.md)
5. [docs/technicals/implementation-plan.md](./technicals/implementation-plan.md)
6. 再进入所属底座主线 / 应用级模块的完整链路
7. [docs/superpowers/specs/102-cmp-implementation-execution-spec.md](./superpowers/specs/102-cmp-implementation-execution-spec.md)
8. 按当前批次读取对应 Superpowers 执行计划；第一批入口为 [docs/superpowers/plans/102-01-batch-1-foundations-implementation-plan.md](./superpowers/plans/102-01-batch-1-foundations-implementation-plan.md)
9. [docs/planning/current.md](./planning/current.md)
10. [docs/planning/decisions.md](./planning/decisions.md)

### 8.3 评审 / 管理 / 对外沟通

1. [docs/specifications/cmp-phase1-requirement-spec.md](./specifications/cmp-phase1-requirement-spec.md)
2. [docs/technicals/architecture-design.md](./technicals/architecture-design.md)
3. [docs/technicals/api-design.md](./technicals/api-design.md)
4. [docs/technicals/detailed-design.md](./technicals/detailed-design.md)
5. [docs/technicals/implementation-plan.md](./technicals/implementation-plan.md)
6. 按主题补读对应底座主线 / 应用级模块链路
7. [docs/planning/current.md](./planning/current.md)
8. [docs/planning/decisions.md](./planning/decisions.md)
9. [docs/planning/history.md](./planning/history.md)

## 九、文档清单总览

| 分组 | 文档 | 角色 | 当前状态 |
| --- | --- | --- | --- |
| 总平台正式链路 | [docs/specifications/cmp-phase1-requirement-spec.md](./specifications/cmp-phase1-requirement-spec.md) | 总平台 Requirement Spec，正式需求入口 | 已存在 |
| 总平台正式链路 | [docs/technicals/architecture-design.md](./technicals/architecture-design.md) | 总平台 Architecture Design | 已存在 |
| 总平台正式链路 | [docs/technicals/api-design.md](./technicals/api-design.md) | 总平台 API Design | 已存在 |
| 总平台正式链路 | [docs/technicals/detailed-design.md](./technicals/detailed-design.md) | 总平台 Detailed Design | 已存在 |
| 总平台正式链路 | [docs/technicals/implementation-plan.md](./technicals/implementation-plan.md) | 总平台 Implementation Plan | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/workflow-engine/architecture-design.md](./technicals/modules/workflow-engine/architecture-design.md) | 流程引擎 Architecture Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/workflow-engine/api-design.md](./technicals/modules/workflow-engine/api-design.md) | 流程引擎 API Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/workflow-engine/detailed-design.md](./technicals/modules/workflow-engine/detailed-design.md) | 流程引擎 Detailed Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/workflow-engine/implementation-plan.md](./technicals/modules/workflow-engine/implementation-plan.md) | 流程引擎 Implementation Plan | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/document-center/architecture-design.md](./technicals/modules/document-center/architecture-design.md) | 文档中心 / 文档协作 Architecture Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/document-center/api-design.md](./technicals/modules/document-center/api-design.md) | 文档中心 / 文档协作 API Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/document-center/detailed-design.md](./technicals/modules/document-center/detailed-design.md) | 文档中心 / 文档协作 Detailed Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/document-center/implementation-plan.md](./technicals/modules/document-center/implementation-plan.md) | 文档中心 / 文档协作 Implementation Plan | 已存在 |
| 统一底座 / 横切主线链路 | [docs/technicals/foundations/agent-os/architecture-design.md](./technicals/foundations/agent-os/architecture-design.md) | Agent OS Architecture Design | 已存在 |
| 统一底座 / 横切主线链路 | [docs/technicals/foundations/agent-os/api-design.md](./technicals/foundations/agent-os/api-design.md) | Agent OS API Design | 已存在 |
| 统一底座 / 横切主线链路 | [docs/technicals/foundations/agent-os/detailed-design.md](./technicals/foundations/agent-os/detailed-design.md) | Agent OS Detailed Design | 已存在 |
| 统一底座 / 横切主线链路 | [docs/technicals/foundations/agent-os/implementation-plan.md](./technicals/foundations/agent-os/implementation-plan.md) | Agent OS Implementation Plan | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/contract-core/architecture-design.md](./technicals/modules/contract-core/architecture-design.md) | 合同管理本体 Architecture Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/contract-core/api-design.md](./technicals/modules/contract-core/api-design.md) | 合同管理本体 API Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/contract-core/detailed-design.md](./technicals/modules/contract-core/detailed-design.md) | 合同管理本体 Detailed Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/contract-core/implementation-plan.md](./technicals/modules/contract-core/implementation-plan.md) | 合同管理本体 Implementation Plan | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/e-signature/architecture-design.md](./technicals/modules/e-signature/architecture-design.md) | 电子签章 Architecture Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/e-signature/api-design.md](./technicals/modules/e-signature/api-design.md) | 电子签章 API Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/e-signature/detailed-design.md](./technicals/modules/e-signature/detailed-design.md) | 电子签章 Detailed Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/e-signature/implementation-plan.md](./technicals/modules/e-signature/implementation-plan.md) | 电子签章 Implementation Plan | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/encrypted-document/architecture-design.md](./technicals/modules/encrypted-document/architecture-design.md) | 加密软件 Architecture Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/encrypted-document/api-design.md](./technicals/modules/encrypted-document/api-design.md) | 加密软件 API Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/encrypted-document/detailed-design.md](./technicals/modules/encrypted-document/detailed-design.md) | 加密软件 Detailed Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/encrypted-document/implementation-plan.md](./technicals/modules/encrypted-document/implementation-plan.md) | 加密软件 Implementation Plan | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/contract-lifecycle/architecture-design.md](./technicals/modules/contract-lifecycle/architecture-design.md) | 合同后续业务组 Architecture Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/contract-lifecycle/api-design.md](./technicals/modules/contract-lifecycle/api-design.md) | 合同后续业务组 API Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/contract-lifecycle/detailed-design.md](./technicals/modules/contract-lifecycle/detailed-design.md) | 合同后续业务组 Detailed Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/contract-lifecycle/implementation-plan.md](./technicals/modules/contract-lifecycle/implementation-plan.md) | 合同后续业务组 Implementation Plan | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/intelligent-applications/architecture-design.md](./technicals/modules/intelligent-applications/architecture-design.md) | 检索 / OCR / AI 业务应用主线 Architecture Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/intelligent-applications/api-design.md](./technicals/modules/intelligent-applications/api-design.md) | 检索 / OCR / AI 业务应用主线 API Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/intelligent-applications/detailed-design.md](./technicals/modules/intelligent-applications/detailed-design.md) | 检索 / OCR / AI 业务应用主线 Detailed Design | 已存在 |
| 应用级业务模块 / 业务组链路 | [docs/technicals/modules/intelligent-applications/implementation-plan.md](./technicals/modules/intelligent-applications/implementation-plan.md) | 检索 / OCR / AI 业务应用主线 Implementation Plan | 已存在 |
| 统一底座 / 横切主线链路 | [docs/technicals/foundations/integration-hub/architecture-design.md](./technicals/foundations/integration-hub/architecture-design.md) | 外围系统集成主线 Architecture Design | 已存在 |
| 统一底座 / 横切主线链路 | [docs/technicals/foundations/integration-hub/api-design.md](./technicals/foundations/integration-hub/api-design.md) | 外围系统集成主线 API Design | 已存在 |
| 统一底座 / 横切主线链路 | [docs/technicals/foundations/integration-hub/detailed-design.md](./technicals/foundations/integration-hub/detailed-design.md) | 外围系统集成主线 Detailed Design | 已存在 |
| 统一底座 / 横切主线链路 | [docs/technicals/foundations/integration-hub/implementation-plan.md](./technicals/foundations/integration-hub/implementation-plan.md) | 外围系统集成主线 Implementation Plan | 已存在 |
| 统一底座 / 横切主线链路 | [docs/technicals/foundations/identity-access/architecture-design.md](./technicals/foundations/identity-access/architecture-design.md) | 身份与权限 Architecture Design | 已存在 |
| 统一底座 / 横切主线链路 | [docs/technicals/foundations/identity-access/api-design.md](./technicals/foundations/identity-access/api-design.md) | 身份与权限 API Design | 已存在 |
| 统一底座 / 横切主线链路 | [docs/technicals/foundations/identity-access/detailed-design.md](./technicals/foundations/identity-access/detailed-design.md) | 身份与权限 Detailed Design | 已存在 |
| 统一底座 / 横切主线链路 | [docs/technicals/foundations/identity-access/implementation-plan.md](./technicals/foundations/identity-access/implementation-plan.md) | 身份与权限 Implementation Plan | 已存在 |
| planning 真相文件 | [docs/planning/current.md](./planning/current.md) | 当前批次真相 | 已存在 |
| planning 真相文件 | [docs/planning/decisions.md](./planning/decisions.md) | 关键决策记录 | 已存在 |
| planning 真相文件 | [docs/planning/history.md](./planning/history.md) | 历史批次归档 | 已存在 |
| Superpowers 正式执行基准 | [docs/superpowers/specs/102-cmp-implementation-execution-spec.md](./superpowers/specs/102-cmp-implementation-execution-spec.md) | 实现阶段统一执行规格 | 已存在 |
| Superpowers 正式执行基准 | [docs/superpowers/plans/102-01-batch-1-foundations-implementation-plan.md](./superpowers/plans/102-01-batch-1-foundations-implementation-plan.md) | 第一批底座主线执行计划 | 已存在 |
| Superpowers 正式执行基准 | [docs/superpowers/plans/2026-04-26-batch-2-core-chain-implementation-plan.md](./superpowers/plans/2026-04-26-batch-2-core-chain-implementation-plan.md) | 第二批合同核心主链路执行计划 | 已存在 |
| Superpowers 正式执行基准 | [docs/superpowers/plans/2026-04-26-batch-3-dependent-business-capabilities-implementation-plan.md](./superpowers/plans/2026-04-26-batch-3-dependent-business-capabilities-implementation-plan.md) | 第三批依赖型业务能力执行计划 | 已存在 |
| Superpowers 正式执行基准 | [docs/superpowers/plans/2026-04-26-batch-4-intelligent-applications-implementation-plan.md](./superpowers/plans/2026-04-26-batch-4-intelligent-applications-implementation-plan.md) | 第四批智能与增强能力执行计划 | 已存在 |
| Superpowers 正式执行基准 | [docs/superpowers/plans/2026-04-26-batch-5-integration-acceptance-release-plan.md](./superpowers/plans/2026-04-26-batch-5-integration-acceptance-release-plan.md) | 第五批联调验收与上线准备执行计划 | 已存在 |
| 过程报告 | [查询引擎驱动薄运行内核与外围治理能力分析报告](./reports/Harness/%E6%9F%A5%E8%AF%A2%E5%BC%95%E6%93%8E%E9%A9%B1%E5%8A%A8%E8%96%84%E8%BF%90%E8%A1%8C%E5%86%85%E6%A0%B8%E4%B8%8E%E5%A4%96%E5%9B%B4%E6%B2%BB%E7%90%86%E8%83%BD%E5%8A%9B%E5%88%86%E6%9E%90%E6%8A%A5%E5%91%8A.md) | 智能体操作系统查询引擎驱动薄运行内核与外围治理能力分析报告 | 已存在 |
| 上游来源材料 | [docs/specifications/湖南星邦智能装备股份有限公司合同管理平台招标技术要求.pdf](./specifications/%E6%B9%96%E5%8D%97%E6%98%9F%E9%82%A6%E6%99%BA%E8%83%BD%E8%A3%85%E5%A4%87%E8%82%A1%E4%BB%BD%E6%9C%89%E9%99%90%E5%85%AC%E5%8F%B8%E5%90%88%E5%90%8C%E7%AE%A1%E7%90%86%E5%B9%B3%E5%8F%B0%E6%8B%9B%E6%A0%87%E6%8A%80%E6%9C%AF%E8%A6%81%E6%B1%82.pdf) | 正式上游来源材料 | 已存在 |

## 十、维护规则

1. 推荐阅读与执行顺序始终遵循“先总平台，再底座主线 / 应用级模块”。
2. `foundations` 用于统一底座 / 横切主线，`modules` 用于应用级业务模块 / 业务组。
3. `docs/planning/current.md`、`docs/planning/decisions.md`、`docs/planning/history.md` 只作为 planning 真相文件，不写成正式交付链路节点。
4. `docs/superpowers/specs/` 与 `docs/superpowers/plans/` 是开发执行基准，只承接正式技术文档，不替代 `docs/technicals/` 的正式设计职责。
5. 上游来源材料用于回溯与核对，不替代总平台正式链路入口。
6. 所有引用统一使用 Markdown 链接。
7. 正式文档新增、迁移、重命名或删除后，必须同步更新本索引。

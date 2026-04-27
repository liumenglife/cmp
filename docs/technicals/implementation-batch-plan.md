# 湖南星邦智能装备股份有限公司合同管理平台 Implementation Batch Plan

## 0. 先看这里

- 本文只负责跨模块五批分组，不替代总平台 `Implementation Plan`，也不替代各模块 `Implementation Plan`。
- 正式推荐顺序固定为：第一批 -> 第二批 -> 第三批 -> 第四批 -> 第五批。
- 当前各主线均具备正式设计输入，按批次满足启动门禁后进入实现。
- 五批之间按关键依赖推进；批次内部可并行，但必须围绕共享真相受控推进。
- 是否正式进入实现阶段，以第 `7` 节“进入实现阶段的判定标准”为准。

## 1. 文档定位

本文档用于承接总平台 [`Implementation Plan`](./implementation-plan.md) 之下的“实现阶段排期分组清单”，把总平台已经确认的推进顺序进一步落为正式的五批实施分组。

本文档的角色是：

- 作为总平台实施顺序的正式补充文档
- 作为跨模块排期分组、并行关系和关键依赖的统一口径
- 作为判断各批次是否满足启动门禁、是否可进入实现的正式依据

本文不替代：

- 总平台 `Implementation Plan`
- 各模块 `Implementation Plan`

职责边界如下：

- 总平台文档继续负责阶段与全局顺序。
- `foundations` 文档继续负责统一底座 / 横切主线的分阶段实施安排，`modules` 文档继续负责应用级业务模块 / 业务组的实施批次与联调安排。
- 本文只负责跨模块五批分组。

## 2. 正式文档入口

评审跨模块顺序时，先看总平台入口；下钻某条主线时，再回到对应模块入口。

| 类别 | 正式入口 |
| --- | --- |
| 总平台正式实施计划 | [docs/technicals/implementation-plan.md](./implementation-plan.md) |
| `identity-access` | [docs/technicals/foundations/identity-access/implementation-plan.md](./foundations/identity-access/implementation-plan.md) |
| `agent-os` | [docs/technicals/foundations/agent-os/implementation-plan.md](./foundations/agent-os/implementation-plan.md) |
| `integration-hub` | [docs/technicals/foundations/integration-hub/implementation-plan.md](./foundations/integration-hub/implementation-plan.md) |
| `contract-core` | [docs/technicals/modules/contract-core/implementation-plan.md](./modules/contract-core/implementation-plan.md) |
| `document-center` | [docs/technicals/modules/document-center/implementation-plan.md](./modules/document-center/implementation-plan.md) |
| `workflow-engine` | [docs/technicals/modules/workflow-engine/implementation-plan.md](./modules/workflow-engine/implementation-plan.md) |
| `e-signature` | [docs/technicals/modules/e-signature/implementation-plan.md](./modules/e-signature/implementation-plan.md) |
| `encrypted-document` | [docs/technicals/modules/encrypted-document/implementation-plan.md](./modules/encrypted-document/implementation-plan.md) |
| `contract-lifecycle` | [docs/technicals/modules/contract-lifecycle/implementation-plan.md](./modules/contract-lifecycle/implementation-plan.md) |
| `intelligent-applications` | [docs/technicals/modules/intelligent-applications/implementation-plan.md](./modules/intelligent-applications/implementation-plan.md) |

## 3. 五批分组总览

先看批次顺序，再看每一批的并行关系和关键依赖。

### 第一批：底座主线先行

- 判断：先建立统一底座，再承接后续业务主线。
- 分组：`identity-access`、`agent-os`、`integration-hub`
- 并行关系：三条主线可并行推进，但应先统一身份、组织、权限、任务、审计与集成边界，不应各自形成重复底座
- 关键依赖：依赖正式需求与正式技术链路已收口；不依赖其他业务主线先完成
- `agent-os` 实现准备：先落最小 `QueryEngine` / `Harness Kernel` 闭环，并保持面向合同管理、审批、文档、签署、履约、风控、运维审计的企业业务系统定位

### 第二批：合同核心主链路成型

- 判断：先把合同主链路、文件真相源和审批承接主线做成闭环。
- 分组：`contract-core`、`document-center`、`workflow-engine`
- 并行关系：三条主线可并行推进，但需围绕同一合同主档、文档主档和审批回写链路持续对齐
- 关键依赖：依赖第一批已提供统一身份、组织权限、任务审计与外部承接边界

### 第三批：依赖主链路真相源的业务能力

- 判断：在主链路真相稳定后，再展开依赖型业务能力。
- 分组：`e-signature`、`encrypted-document`、`contract-lifecycle`
- 并行关系：可以并行展开，但必须建立在合同状态、审批状态和文档版本真相已稳定的前提上
- 关键依赖：依赖第二批完成一轮主链路收口，至少明确文档版本链、审批结果回写、合同状态流转与挂载点边界

### 第四批：智能与增强能力

- 判断：以稳定输入源和异步能力为前提，后置铺开智能能力。
- 分组：`intelligent-applications`
- 并行关系：可与第三批后段适度交错预集成，但不宜早于主链路和增强业务组稳定前全面铺开
- 关键依赖：依赖统一任务体系、稳定文档资产、审批摘要、合同状态、搜索与异步任务挂接入口

### 第五批：综合联调、验收与上线准备

- 判断：在前四批达到可联调状态后，再统一推进外部联调和上线准备。
- 分组：`OA`、企业微信及其他一期范围内外围联调事项，以及综合测试、验收、上线准备工作
- 并行关系：可按外部系统窗口并行推进，但应统一受控于平台功能冻结、环境就绪和测试数据准备情况
- 关键依赖：依赖前四批达到可联调、可回归、可验收状态

## 4. 推荐顺序与原因

推荐顺序为：第一批 -> 第二批 -> 第三批 -> 第四批 -> 第五批。

推荐原因如下：

- 第一批先行，是为了先建立唯一可复用的身份、权限、任务、审计与集成底座，避免后续模块各自补基础设施
- 第二批紧随其后，是因为 `contract-core`、`document-center`、`workflow-engine` 共同构成一期最关键的合同主链路、文件真相源与审批承接主线，越早形成闭环，越能减少后续主线返工
- 第三批放在主链路之后，是因为签章、加密与生命周期都直接消费合同状态、审批状态和文档版本真相；若主链路未稳定，这一批会持续反复调整挂载点
- 第四批后置，是因为智能能力天然依赖稳定输入源、异步任务体系和已经成形的业务事件；过早进入全面实现，容易倒逼前面主线反复修改输出接口
- 第五批最后统一推进，是因为联调、验收与上线准备必须建立在功能边界基本冻结、环境就绪和测试资产可复用的前提上，否则只能形成低效并行和重复验证

## 5. 并行关系与关键依赖说明

五批分组并不意味着每批内部必须完全串行。

核心约束只有一条：批次之间按依赖推进，批次内部按共享真相受控并行。

- 第一批内部可并行，但 `identity-access` 提供的组织与权限真相应优先收口，避免后续审批节点、数据权限和多端访问重复定义主体
- 第二批内部可并行，但 `contract-core`、`document-center`、`workflow-engine` 需要围绕同一合同主档、文档主档、审批回写和任务入口持续联动校正
- 第三批内部可并行，但只能消费第二批已经稳定的挂载点，不应一边依赖未定状态、一边反向推动主链路重写基础边界
- 第四批应以前三批沉淀出的稳定输入源为前提，允许预接入与预验证，但不建议在输入结构未稳前进入大规模实现
- 第五批可以按外部系统窗口和验收专题并行展开，但不应早于平台主线进入“可冻结、可联调、可回归”的状态

## 6. 批次启动条件与验收门禁

当前各主线均具备正式需求、架构、接口、详细设计、专项设计与实施计划输入。是否进入实现，不再按“可直接进入 / 先补设计”二分判断，而按批次启动门禁执行。

第三批、第四批启动前，必须把以下接口、状态与挂载点检查为可消费状态：

- 合同状态流转与生命周期关键节点的稳定边界
- 文档版本链、签章稿、归档稿与受控解密下载的挂接边界
- `OA` 主路径与平台审批承接路径的回写边界
- 智能能力可消费的文档资产、审批摘要、任务事件与异步入口

满足上述检查后，对应批次按第 `7` 节门禁进入实现；未满足时，仅冻结问题清单和责任主线，不把该状态表述为缺少专项设计。

## 7. 进入实现阶段的判定标准

只有同时满足下表判定项，才应认定该批次可以正式开工。

| 判定项 | 进入实现阶段的标准 |
| --- | --- |
| 正式文档齐备 | 对应主线至少已具备正式 `Architecture Design`、`API Design`、`Detailed Design`、专项设计基线、`Implementation Plan`，并与总平台正式链路不冲突。 |
| 范围已收口 | 当前批次目标、边界、不做内容、外部依赖、关键异常路径都已明确，不再存在边做边定范围的核心分歧。 |
| 前置主线可复用 | 该批次依赖的上游主线已经形成可消费的稳定接口、状态字段、事件或挂载点，而不是口头约定。 |
| 接口可冻结 | 当前批次对内服务、对外契约、事件载荷、状态枚举和跨模块挂载点已具备冻结条件，变更必须显式评审。 |
| 环境可启动 | 开发、测试、联调环境已满足 `Docker Compose / 企业内网` 基线，最小依赖服务可启动并可重复部署。 |
| 主数据可获得 | 组织架构、权限、合同样例、文档样例、流程样例、外部接入配置等最小样本已准备完成。 |
| 独立验证可执行 | 当前批次具备可独立运行的最小闭环验证、关键异常验证和回归验证入口。 |
| 跨模块挂载点可消费 | 上游主档、文档、审批、任务、审计、集成事件等挂载点已具备稳定消费方式和样例数据。 |
| 验收口径可定义 | 该批次至少能写清楚端到端最小闭环、关键异常场景、权限场景和完成标志，而不是只有开发任务没有验收判断。 |
| 风险已显式登记 | 关键串行依赖、外部联调窗口、账号条件、状态回写边界、真相源边界等风险已被明确记录并被团队接受。 |

只有当某一批次同时满足以上标准，才应认定为可以正式开工；如果仍缺少上游真相源、关键接口边界或验收判断，则应继续停留在设计收口阶段，而不是提前进入实现。

## 8. 使用方式

使用本文时，按下面的引用边界执行。

- 总平台层如需说明整体实施顺序，应引用本文与 [`Implementation Plan`](./implementation-plan.md)
- 模块层如需展开模块内批次，应回到各自模块 `Implementation Plan`
- 后续若五批分组、批间依赖或启动门禁发生正式变化，应优先更新本文，再同步相关总平台文档

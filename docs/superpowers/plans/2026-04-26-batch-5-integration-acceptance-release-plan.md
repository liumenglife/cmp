# 第五批综合联调验收与上线准备 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按第五批“综合联调、验收与上线准备”完成 OA、企业微信及其他一期外围联调事项、综合测试、验收和 Docker Compose 企业内网部署准备。

**Architecture:** 本批不新增业务主线能力，而是在前四批功能可冻结、接口可联调、环境可复现的基础上完成外部联调、端到端回归、权限 / 审计 / 性能 / 安全验收和上线准备。所有联调、验收和上线动作统一围绕正式文档链路、批次门禁、Docker Compose 企业内网部署基线和审计留痕执行。

**Tech Stack:** `Docker Compose / 企业内网` 部署基线；`MySQL` 正式数据库口径并保留数据库抽象层；`React (SPA) + Tailwind CSS + shadcn/ui` 前端交付口径；平台后端、任务中心、审计中心、文档中心、`Agent OS`、集成中心、企业微信和 OA 接口均按正式技术文档联调。

---

## 1. 批次定位

本计划是 [`Implementation Batch Plan`](../../technicals/implementation-batch-plan.md) 中“第五批：综合联调、验收与上线准备”的 Superpowers 执行基准。第五批覆盖 OA、企业微信及其他一期范围内外围联调事项，以及综合测试、验收、上线准备工作。

本批完成以下功能：

- 外部联调：完成 OA 主审批路径、平台审批承接路径、企业微信登录 / 组织同步 / 消息触达，以及其他一期外围接口的连通、字段映射、回调、安全签名、重试和对账。
- 端到端回归：覆盖合同创建、文档入库、审批发起、审批回写、签章、加密、履约、搜索、OCR、AI 辅助、归档、统计、多语言和权限审计的端到端路径。
- 权限 / 审计 / 性能 / 安全验收：完成功能权限、数据权限、解密下载授权、审计追溯、性能压测、接口安全、回调安全、日志脱敏和异常恢复验收。
- Docker Compose 企业内网部署准备：完成镜像构建、配置注入、初始化脚本、环境变量、内网依赖、备份恢复、健康检查、上线包、回退预案和运维交接。

本批只有在前四批达到可联调、可回归、可验收状态后启动。若前置功能未冻结，应登记阻塞项和责任主线，不通过修改本计划扩大范围。

## 2. 输入文档

- 项目原则：[`PRINCIPLE.md`](../../../PRINCIPLE.md)
- 当前真相：[`docs/planning/current.md`](../../planning/current.md)
- 决策记录：[`docs/planning/decisions.md`](../../planning/decisions.md)
- 总平台实施计划：[`docs/technicals/implementation-plan.md`](../../technicals/implementation-plan.md)
- 跨模块批次计划：[`docs/technicals/implementation-batch-plan.md`](../../technicals/implementation-batch-plan.md)
- 总平台架构设计：[`architecture-design.md`](../../technicals/architecture-design.md)
- 总平台接口设计：[`api-design.md`](../../technicals/api-design.md)
- 总平台详细设计：[`detailed-design.md`](../../technicals/detailed-design.md)
- `identity-access` 实施基线：[`implementation-plan.md`](../../technicals/foundations/identity-access/implementation-plan.md)
- `integration-hub` 实施基线：[`implementation-plan.md`](../../technicals/foundations/integration-hub/implementation-plan.md)
- `agent-os` 实施基线：[`implementation-plan.md`](../../technicals/foundations/agent-os/implementation-plan.md)
- `workflow-engine` 实施基线：[`implementation-plan.md`](../../technicals/modules/workflow-engine/implementation-plan.md)
- `document-center` 实施基线：[`implementation-plan.md`](../../technicals/modules/document-center/implementation-plan.md)
- `contract-core` 实施基线：[`implementation-plan.md`](../../technicals/modules/contract-core/implementation-plan.md)
- `e-signature` 实施基线：[`implementation-plan.md`](../../technicals/modules/e-signature/implementation-plan.md)
- `encrypted-document` 实施基线：[`implementation-plan.md`](../../technicals/modules/encrypted-document/implementation-plan.md)
- `contract-lifecycle` 实施基线：[`implementation-plan.md`](../../technicals/modules/contract-lifecycle/implementation-plan.md)
- `intelligent-applications` 实施基线：[`implementation-plan.md`](../../technicals/modules/intelligent-applications/implementation-plan.md)

## 3. 文件职责映射

本计划不指定代码文件路径，由执行 Agent 按现有工程结构落位。职责边界必须保持如下：

- `integration-hub`：承接 OA、企业微信和外围系统接口联调、签名验签、字段映射、回调、重试、超时、对账和原始报文治理。
- `workflow-engine`：承接 OA 主审批、平台审批引擎承接、审批节点组织绑定、任务推进和审批结果回写。
- `identity-access`：承接统一登录、组织人员同步、角色权限、数据权限、解密下载授权和企业微信身份映射。
- `contract-core`：承接合同主档、合同台账、合同详情、模板条款、受控回写和主链路验收对象。
- `document-center`：承接文件真相源、版本链、预览、归档稿、受控解密下载、OCR / 搜索 / 签章挂接入口。
- `agent-os` 与 `intelligent-applications`：承接 AI 任务、人工确认、智能结果、搜索、OCR、多语言和回写审计的联调验收。
- `deployment` / `ops`：承接 Docker Compose 企业内网环境、初始化、备份恢复、健康检查、日志、监控、运维手册和上线回退。

## 4. 可执行任务清单

### Task 1: 第五批启动门禁核验

**Files:**
- Read: `docs/technicals/implementation-batch-plan.md`
- Read: `docs/technicals/implementation-plan.md`
- Inspect deployment, integration, test and ops entrypoints selected by the executing Agent

- [ ] 确认前四批功能边界已冻结，核心接口、事件、状态字段和跨模块挂载点具备联调条件。
- [ ] 确认联调环境、测试数据、组织架构、测试角色、样例合同、样例文档、样例流程、样例条款和企业微信测试账号可用。
- [ ] 确认 OA 接口地址、字段映射、回调路径、签名方式、失败回执、重试策略和责任边界已固化。
- [ ] 确认 Docker Compose 企业内网环境能启动最小依赖服务，并能重复部署。
- [ ] 输出第五批启动门禁记录，所有未满足项绑定责任主线和阻塞级别。

**Completion Flag:** 第五批联调、回归、验收和上线准备所需环境、数据、账号、配置、接口窗口均具备可执行入口。

**Verification:** 在联调环境完成一次最小启动、登录、合同样例读取、文档样例读取、审计写入和外部接口健康检查。

### Task 2: OA 外部联调

**Files:**
- Baseline: [`workflow-engine implementation-plan.md`](../../technicals/modules/workflow-engine/implementation-plan.md)
- Baseline: [`integration-hub implementation-plan.md`](../../technicals/foundations/integration-hub/implementation-plan.md)

- [ ] 联调 OA 主审批发起接口，验证合同审批请求、流程实例引用、业务单号、回调地址和签名字段。
- [ ] 联调 OA 审批结果回调，验证同意、拒绝、退回、撤回、异常、重复回调和乱序回调。
- [ ] 验证平台审批引擎承接路径，覆盖 OA 无法满足业务或技术要求时由平台审批正式承接。
- [ ] 验证审批节点绑定部门、人员或组织规则，不允许纯抽象节点绕过组织主数据。
- [ ] 验证审批摘要、审批附件、审批状态、任务状态、审计事件回写到合同主链。
- [ ] 验证 OA 联调失败后的重试、补偿、对账、告警和人工处理入口。

**Completion Flag:** OA 主审批路径和平台审批承接路径均可在联调环境完成业务闭环，审批结果能受控回写并可审计。

**Verification:** 执行至少一条“合同创建 -> 发起 OA 审批 -> OA 回调 -> 平台状态回写 -> 审计查询”的端到端用例，并执行异常回调、重复回调、签名失败和超时重试用例。

### Task 3: 企业微信外部联调

**Files:**
- Baseline: [`identity-access implementation-plan.md`](../../technicals/foundations/identity-access/implementation-plan.md)
- Baseline: [`integration-hub implementation-plan.md`](../../technicals/foundations/integration-hub/implementation-plan.md)

- [ ] 联调企业微信测试企业、测试应用、回调配置、密钥信息和测试通讯录。
- [ ] 验证企业微信登录、身份绑定、部门同步、人员同步、离职 / 禁用处理和增量同步。
- [ ] 验证组织架构同步到平台后可被审批节点、数据权限、通知触达和管理端授权使用。
- [ ] 验证企业微信消息触达，覆盖审批提醒、任务提醒、风险提醒、回写异常提醒和上线通知。
- [ ] 验证回调签名、时间戳、重放防护、字段映射、失败重试和对账记录。
- [ ] 验证企业微信不可用时的平台降级策略和补偿通知策略。

**Completion Flag:** 企业微信登录、组织人员同步和消息触达均通过联调，且组织身份数据能被平台权限、审批和通知链路消费。

**Verification:** 使用普通员工、部门负责人、管理端操作人员三个测试角色完成登录、组织同步、权限裁剪、审批提醒和消息触达验证。

### Task 4: 其他一期外围联调事项

**Files:**
- Baseline: [`implementation-plan.md`](../../technicals/implementation-plan.md)
- Baseline: module implementation plans listed in section 2

- [ ] 验证电子签章作为平台内自研子模块完成签署方、签章坐标、签章稿、批量重签、签署结果回写和审计联调。
- [ ] 验证加密软件作为平台内自研子模块完成文件自动加密、平台内解密读取、授权解密下载、明文导出审计和权限拦截。
- [ ] 验证文档中心与 OCR、搜索、签章、归档、受控解密下载的挂接状态和版本链一致性。
- [ ] 验证履约、变更、终止、归档、借阅归还、统计报表、多语言、电视端等一期外围能力与合同主链联动。
- [ ] 验证所有外围能力的失败重试、对账、审计和告警入口。
- [ ] 输出外围联调矩阵，标注每项能力的主对象、输入、输出、责任模块和验收证据。

**Completion Flag:** 一期外围能力与合同主链、文档中心、权限审计和任务体系联调完成，不存在未归属的外围接口或手工补数据路径。

**Verification:** 执行电子签章、授权解密下载、归档借阅、履约提醒、统计导出、多语言展示和电视端展示的联调用例。

### Task 5: 端到端回归测试

**Files:**
- Create or update tests according to existing test structure selected by the executing Agent
- Evidence should be stored in the repository’s established reports or test artifact locations by the main execution workflow

- [ ] 建立核心业务闭环回归：合同创建 / 起草、文档入库、审批发起、审批回写、合同台账、详情查询。
- [ ] 建立文档链路回归：版本链、预览、批注、修订、签章稿、归档稿、受控解密下载。
- [ ] 建立签章与加密回归：签署方、签章坐标、签章结果、自动加密、授权解密下载、明文导出审计。
- [ ] 建立生命周期回归：履约、变更、终止、结算、归档、借阅归还、通知和统计。
- [ ] 建立智能能力回归：OCR、全文检索、摘要、问答、风险识别、比对提取、多语言和受控回写。
- [ ] 建立异常回归：外部接口超时、重复回调、签名失败、权限拒绝、任务失败、死信恢复、版本切换、回滚演练。

**Completion Flag:** 平台一期核心场景、外围场景、智能场景和异常场景均有可重复执行的端到端回归入口和结果证据。

**Verification:** 在联调环境执行完整回归套件，记录通过率、缺陷清单、阻塞项、重跑结果和最终放行结论。

### Task 6: 权限、审计、安全验收

**Files:**
- Baseline: [`identity-access implementation-plan.md`](../../technicals/foundations/identity-access/implementation-plan.md)
- Baseline: [`implementation-plan.md`](../../technicals/implementation-plan.md)

- [ ] 验证功能权限、菜单权限、数据权限、组织范围、跨部门访问、管理端操作权限和运维权限。
- [ ] 验证审批节点组织绑定、企业微信身份映射、OA 回调身份、服务账号和系统间凭证。
- [ ] 验证受控解密下载授权，覆盖按部门、人员授权、明文导出、越权拦截和审计留痕。
- [ ] 验证审计链路覆盖登录、合同操作、文档操作、审批回写、签章、加密、OCR、搜索、AI、回写、外部回调和运维恢复。
- [ ] 验证接口安全，覆盖鉴权、签名、时间戳、重放防护、幂等冲突、字段校验、文件校验和错误码。
- [ ] 验证日志脱敏，确保普通日志不输出完整合同正文、OCR 全文、AI 原始长文、密钥、令牌和高敏字段。

**Completion Flag:** 权限、审计和安全验收通过，未发现可绕过权限访问合同、文档、AI 结果、解密下载或外部回调的路径。

**Verification:** 使用最小角色矩阵执行正向授权、越权访问、跨组织查询、回调伪造、重复提交、解密下载和审计追踪测试。

### Task 7: 性能、容量与恢复验收

**Files:**
- Baseline: [`implementation-plan.md`](../../technicals/implementation-plan.md)
- Baseline: module-specific operations and recovery designs

- [ ] 执行核心接口性能测试，覆盖登录、合同列表、合同详情、文档预览、审批发起、审批回写、搜索查询、AI 任务受理。
- [ ] 执行异步任务容量测试，覆盖 OCR 队列、搜索索引刷新、AI 作业队列、回写队列、通知队列和死信队列。
- [ ] 执行文件与文档容量测试，覆盖大文件、扫描件、多页文档、多版本文档、归档包和受控解密下载。
- [ ] 执行外部接口稳定性测试，覆盖 OA、企业微信和外围接口的超时、限流、失败重试和对账。
- [ ] 执行备份恢复测试，覆盖 MySQL 数据、对象存储、配置、索引重建、缓存失效和任务重放。
- [ ] 执行回滚演练，覆盖搜索双代回退、OCR 引擎路由回滚、AI 结果失效、回写死信重推和部署版本回退。

**Completion Flag:** 性能、容量和恢复验收达到一期上线或试运行要求，核心链路无不可接受的阻塞性性能风险。

**Verification:** 输出性能指标、容量指标、恢复耗时、回滚耗时、失败率、资源占用和问题关闭记录。

### Task 8: Docker Compose 企业内网部署准备

**Files:**
- Modify deployment files selected by the executing Agent, respecting existing repository structure
- Include deployment evidence and operations instructions in established project documentation locations when execution is authorized

- [ ] 固化 Docker Compose 服务清单，覆盖前端、后端、MySQL、Redis、对象存储、任务执行器、搜索引擎、日志 / 监控组件和必要适配服务。
- [ ] 固化企业内网配置注入方式，覆盖环境变量、密钥引用、接口地址、回调地址、域名、时间同步、文件存储路径和日志目录。
- [ ] 固化初始化脚本，覆盖数据库初始化、组织角色初始化、权限初始化、字典初始化、流程样例、模板条款样例和测试账号初始化。
- [ ] 固化镜像构建、版本标识、制品校验、离线包或内网制品仓库发布方式。
- [ ] 固化健康检查、启动顺序、依赖等待、日志收集、备份恢复、配置差异和故障排查入口。
- [ ] 固化上线包、回退包、上线窗口操作清单、回退条件、回退步骤和运维交接材料。

**Completion Flag:** 平台可在企业内网以 Docker Compose 可重复部署，具备初始化、升级、备份、恢复、健康检查和回退能力。

**Verification:** 在干净企业内网等价环境执行一次完整部署、初始化、冒烟测试、备份恢复和版本回退演练。

### Task 9: 验收材料与上线放行

**Files:**
- Evidence and release materials should follow the repository’s established reports, guides and release artifact conventions when execution is authorized

- [ ] 汇总外部联调报告，覆盖 OA、企业微信和一期外围联调事项。
- [ ] 汇总端到端回归报告，覆盖通过率、缺陷状态、阻塞项、重跑结论和剩余风险。
- [ ] 汇总权限、审计、性能、安全、部署和恢复验收报告。
- [ ] 准备上线操作手册、运维手册、培训材料、测试账号说明、初始化数据说明和回退预案。
- [ ] 组织上线放行评审，确认功能冻结、缺陷收敛、环境就绪、外部窗口、运维值守和回退条件。
- [ ] 形成上线或试运行放行结论，明确放行范围、观察指标、值守安排和问题升级路径。

**Completion Flag:** 验收材料、上线材料、运维交接和放行结论齐备，平台具备一期上线或试运行条件。

**Verification:** 上线评审逐项核对外部联调、端到端回归、权限审计、安全性能、部署恢复和风险清单，全部通过后形成放行记录。

## 5. 综合验证方式

- 外部联调验证：OA、企业微信及外围接口按字段映射、签名验签、回调、重试、对账和审计逐项验证。
- 端到端回归验证：覆盖合同主链、审批、文档、签章、加密、生命周期、智能能力、多语言、统计和通知。
- 权限审计验证：覆盖角色权限、数据权限、组织范围、解密下载、运维权限和全链路审计追踪。
- 安全验证：覆盖接口鉴权、签名、重放防护、幂等冲突、日志脱敏、文件校验、回调伪造和敏感字段保护。
- 性能验证：覆盖核心接口、搜索、文档预览、OCR、AI 任务、回写队列、外部接口和部署资源占用。
- 恢复验证：覆盖备份恢复、任务重放、死信重推、索引重建、缓存失效、服务重启和版本回退。
- 部署验证：在 Docker Compose 企业内网基线环境完成全量部署、初始化、冒烟、监控、日志、备份、恢复和回退演练。

## 6. 完成标志

- OA 主审批、平台审批承接、企业微信登录 / 组织同步 / 消息触达和其他一期外围联调事项通过。
- 端到端回归覆盖一期核心业务、增强业务、智能能力、权限审计和异常恢复场景。
- 权限 / 审计 / 性能 / 安全验收达到上线或试运行要求，阻塞缺陷清零。
- Docker Compose 企业内网部署包、初始化脚本、配置清单、备份恢复、健康检查、运维手册和回退预案齐备。
- 验收材料、培训材料、上线操作清单、值守安排和放行结论完成。

## 7. 质量审查要求

- 审查本计划是否严格对应第五批“综合联调、验收与上线准备”，不得新增第四批智能实现功能或重写前四批模块范围。
- 审查 OA 主审批和平台审批引擎一期必建能力是否同时被验证，不能把平台审批引擎降级为可不实现的接口壳。
- 审查企业微信对接是否独立于微信支付口径，且只把企业微信作为一期外部测试账号依赖。
- 审查加密软件和电子签章是否作为平台内自研子模块联调验收，不误写成外部测试账号依赖。
- 审查 Docker Compose / 企业内网部署准备是否覆盖配置、初始化、健康检查、备份恢复、日志、监控、回退和运维交接。
- 审查全部验收证据是否可复现、可追溯、可审计，并覆盖权限、审计、性能、安全、外部联调和端到端回归。

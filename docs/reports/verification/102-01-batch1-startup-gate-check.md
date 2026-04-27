# 第一批启动门禁核对报告

## 结论

- 门禁结论：通过
- 核对范围：`identity-access`、`agent-os`、`integration-hub` 第一批底座主线启动门禁。
- 核对时间：2026-04-27
- 工作区：`/Users/agent/cmp/.worktrees/feature/batch1-foundations`

## 输入完整性核对

| 主线 | Architecture Design | API Design | Detailed Design | Implementation Plan | 专项设计输入 | 结论 |
| --- | --- | --- | --- | --- | --- | --- |
| `identity-access` | 已存在 | 已存在 | 已存在 | 已存在 | 5 份 | 通过 |
| `agent-os` | 已存在 | 已存在 | 已存在 | 已存在 | 10 份 | 通过 |
| `integration-hub` | 已存在 | 已存在 | 已存在 | 已存在 | 5 份 | 通过 |

专项设计输入清单：

- `identity-access`：`org-rule-resolution-design.md`、`identity-migration-and-bootstrap-design.md`、`external-identity-protocol-design.md`、`decrypt-download-authorization-design.md`、`data-scope-sql-pushdown-design.md`。
- `agent-os`：`verification-and-performance-design.md`、`tool-contract-and-sandbox-design.md`、`specialized-agent-persona-catalog-design.md`、`provider-routing-and-quota-design.md`、`memory-retrieval-and-expiration-design.md`、`prompt-layering-and-versioning-design.md`、`human-confirmation-and-console-design.md`、`delegation-scheduler-design.md`、`drill-and-operations-runbook-design.md`、`auto-dream-daemon-candidate-quality-design.md`。
- `integration-hub`：`signing-and-ticket-security-design.md`、`retry-timeout-and-alerting-design.md`、`reconciliation-and-raw-message-governance-design.md`、`external-field-mapping-design.md`、`adapter-runtime-design.md`。

证据：路径检查命令返回 `required-files-present`，并返回 `identity-access-special=5`、`agent-os-special=10`、`integration-hub-special=5`。

## 范围边界核对

第一批范围只覆盖底座主线：

- `identity-access`：统一身份、组织、角色、菜单权限、功能权限、数据权限、授权判定、身份审计与解密下载授权边界。
- `agent-os`：围绕 `QueryEngine` 的薄 `Harness Kernel`，覆盖任务、运行、结果、Prompt 快照、检查点、工具、上下文、记忆、确认、委派与验证治理边界。
- `integration-hub`：统一外部接入、入站、出站、回调、绑定、集成任务、审计、补偿、恢复、对账与原始报文治理边界。

未提前进入的业务主链路：

- 未把 `contract-core` 合同主档实现作为第一批目标。
- 未把 `document-center` 文档版本链、文件真相源实现作为第一批目标。
- 未把 `workflow-engine` 流程定义、流程实例、审批运行时主链路实现作为第一批目标。

证据：`docs/technicals/implementation-batch-plan.md` 第 3 节将第一批固定为 `identity-access`、`agent-os`、`integration-hub`，第二批才是 `contract-core`、`document-center`、`workflow-engine`；`docs/superpowers/specs/102-cmp-implementation-execution-spec.md` 明确禁止绕过第一批范围提前实现第二批业务主链路。

## 环境条件核对

| 条件 | 证据 | 结论 |
| --- | --- | --- |
| `Docker Compose / 企业内网` 基线 | `scripts/verify-all.sh` 完成 Docker Compose 镜像构建、容器启动、健康检查与清理；`mysql`、`redis`、`backend`、`frontend` 均进入 `Healthy` | 通过 |
| `MySQL` | `scripts/verify-all.sh` 输出 `batch1-foundations-mysql-1 Healthy` | 通过 |
| `Redis` | `scripts/verify-all.sh` 输出 `batch1-foundations-redis-1 Healthy` | 通过 |
| 统一审计最小入口 | 三条主线正式设计均把身份、授权、Agent 运行、工具调用、外部交换、回调、补偿、恢复动作纳入 `trace_id` 审计链；当前批次执行规格将审计闭环列为门禁 | 通过 |
| 统一任务中心最小入口 | 第一批计划把 `AgentTask`、`AgentRun`、`IntegrationJob` 与平台统一任务中心的最小映射列为底座能力；`agent-os` 与 `integration-hub` 实施计划均要求复用统一任务中心 | 通过 |
| 日志和配置注入最小入口 | `scripts/verify-all.sh` 完成后端、前端、Docker 镜像与 Compose 健康验证；`agent-os`、`integration-hub` 实施计划均将日志采集、配置注入、凭据引用和可观测性列为前置或上线准备项 | 通过 |

## 验收样例准备

### 样例一：统一身份登录与授权判定

- 输入：本地账号或企业微信标准化协议交换引用、平台统一主体、组织上下文、角色授权、数据权限样例。
- 链路：登录或外部换票进入 `identity-access`，完成 `IdentityBinding` 绑定预检查，生成 `IdentitySession`，调用 `POST /api/authorization/decisions` 做功能权限和数据权限判定。
- 期望：同一自然人归并到统一 `User`；授权返回 `AuthorizationDecision`；允许、拒绝、显式拒绝、数据范围不命中均有 `trace_id` 审计。

### 样例二：`agent-os` 合同风险提示最小任务

- 输入：业务用户提交 `DOCUMENT_REVIEW` 或 `RISK_ANALYSIS` 类型 `AgentTask`，对象引用为合同和合同文档。
- 链路：`Task Ingress -> Context Governor -> Prompt Assembly -> Provider Budget Check -> Model Call -> Tool Decision -> Guard Chain -> Observation Normalize -> State Checkpoint -> Termination Check`。
- 期望：生成合同风险提示 `AgentResult`；运行具备 `AgentRun`、`PromptSnapshot`、检查点、模型调用摘要和审计事件；模型失败、预算拒绝、取消、最大循环等异常路径可验证。

### 样例三：`integration-hub` 外部入站与回调闭环

- 输入：外部系统发送合同事实或主数据入站消息，并在平台出站后回调处理结果。
- 链路：`InboundMessage` 受理、验签、幂等、字段映射、绑定建立；`OutboundDispatch` 出站；`CallbackReceipt` 回调去重、顺序检查、状态转译；异常进入 `IntegrationJob` 或恢复工单。
- 期望：入站、出站、回调都由 `integration-hub` 统一承接；业务模块不私接外部入口；链路可按 `trace_id` 回查审计、补偿和对账证据。

## 验证命令与结果

| 命令 | 结果 |
| --- | --- |
| `git status --short` | 运行前无输出；报告写入前工作树无其他变更 |
| `scripts/verify-all.sh` | 第一次 120 秒工具超时截断在 Docker Compose 网络创建阶段；随后以 300 秒超时重跑通过，后端 Maven 测试 `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`，前端 lint / test / build 通过，Docker Compose 启动并健康检查 `mysql`、`redis`、`backend`、`frontend` 后完成清理 |
| 必读文件路径检查命令 | 通过，输出 `required-files-present`、`identity-access-special=5`、`agent-os-special=10`、`integration-hub-special=5` |

## 阻断问题列表

无。

## 未覆盖风险

无。

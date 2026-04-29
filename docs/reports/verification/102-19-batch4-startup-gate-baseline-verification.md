# 第四批启动门禁核验与基线验证报告

## 1. 验证结论

第四批启动门禁核验结论：不通过。

基线可运行验证结论：通过，没问题。

本次未进行第四批功能编码，未修改生产代码，未修改 planning 真相文件。

## 2. 已读取依据

- `PRINCIPLE.md`
- `AGENTS.md`
- `docs/planning/current.md`
- `docs/planning/history.md`
- `docs/planning/decisions.md`
- `docs/superpowers/specs/102-cmp-implementation-execution-spec.md`
- `docs/superpowers/plans/102-04-batch-4-intelligent-applications-implementation-plan.md`
- `docs/technicals/implementation-batch-plan.md`
- `docs/technicals/modules/intelligent-applications/implementation-plan.md`

## 3. 启动门禁核验事实

### 3.1 合同主档可消费性

- 已具备：`CoreChainController` 提供 `/api/contracts`、`/api/contracts/{contractId}`、`/api/contracts/{contractId}/master`，实现侧已有 `contract_id`、合同详情、合同主档读取和审批 / 签章 / 履约 / 变更 / 终止 / 归档摘要挂接。
- 已具备：`/api/batch3/shared-contract` 输出第三批共享主合同挂载约定和摘要回写方向。
- 已具备：合同侧存在审批、签章、履约、变更、终止、归档等受控写入入口。
- 缺口：实现侧未发现可消费的分类主链接口或字段闭环。
- 缺口：实现侧未发现条款库或模板库读取入口，第四批搜索和 AI grounding 不能直接消费条款 / 模板稳定版本。

### 3.2 文档中心可消费性

- 已具备：`CoreChainController` 提供 `/api/document-center/assets`、`/api/document-center/assets/{documentAssetId}`、`/api/document-center/assets/{documentAssetId}/versions`、`/api/document-center/versions/{documentVersionId}`、`/api/document-center/versions/{documentVersionId}/activate`。
- 已具备：实现侧返回 `document_asset_id`、`document_version_id`、`current_version_id`、`version_status`、`is_current_version` 等版本链字段。
- 已具备：文档版本切换时记录 `DOCUMENT_VERSION_ACTIVATED` 审计事件，旧版本读取时按当前版本关系返回 `SUPERSEDED`。
- 已具备：加密文档访问返回 `controlled_read_handle`，可作为受控读取事实依据。
- 缺口：实现侧未发现文档中心面向 `OCR` / 搜索 / AI 的 `dc_capability_binding` 或等价能力挂接摘要可消费入口。
- 缺口：版本切换已有审计事实，但未发现面向第四批异步消费者的稳定事件投递 / 订阅入口。

### 3.3 `Agent OS` 可消费性

- 已具备：`AgentOsController` 提供 `/api/agent-os/tasks` 统一任务创建入口。
- 已具备：`AgentOsController` 提供 `/api/agent-os/tasks/{taskId}/result`、`/api/agent-os/results/{resultId}`、`/api/agent-os/runs/{runId}` 结果与运行查询入口。
- 已具备：`AgentOsController` 提供 `/api/agent-os/runs/{runId}/audit-view` 审计视图入口。
- 已具备：`ao_agent_task`、`ao_agent_run`、`ao_agent_result`、`ao_agent_audit_event` 等迁移表已存在。
- 缺口：实现侧未发现人工确认单创建、确认处理或确认结果查询的正式接口；仅发现 `human_confirmation_required` 结果字段。

### 3.4 权限、审计、任务中心与本地编排

- 已具备：`IdentityAccessAuthorizationGateway` 提供统一授权判定并写入 `ia_authorization_decision`、`ia_authorization_hit_result`、`ia_identity_audit`。
- 已具备：`Agent OS`、`integration-hub`、`contract-lifecycle` 均有审计表或审计事件记录。
- 已具备：`docker-compose.yml` 包含 `mysql`、`redis`、`backend`、`frontend`、`local-ready`，健康检查能够支撑本地编排验证。
- 已具备：`Agent OS` 的任务表和 `integration-hub` 的 `ih_integration_job` 可支撑部分异步任务。
- 缺口：实现侧未发现统一 `platform_job` 或跨模块任务中心正式表；`ih_integration_job.platform_job_id` 仅是引用字段，不能证明统一任务中心已经可被第四批统一复用。

## 4. 运行命令与结果

运行命令：

```bash
./scripts/verify-all.sh
```

运行结果：通过，没问题。

关键日志事实：

- 后端构建成功。
- 后端测试 `Tests run: 133, Failures: 0, Errors: 0, Skipped: 0`。
- 前端 `eslint` 通过。
- 前端 `vitest` 通过，`1` 个测试通过。
- 前端生产构建通过。
- `Docker Compose` 本地栈构建、启动、健康检查通过，随后完成容器与网络清理。

## 5. 问题点

1. 合同主档缺少可消费的分类主链接口或字段闭环。
2. 条款库 / 模板库读取入口未在实现侧出现，第四批搜索与 AI grounding 的上游资源不可直接消费。
3. 文档中心缺少面向 `OCR` / 搜索 / AI 的能力挂接摘要入口，例如 `dc_capability_binding` 或等价实现。
4. 文档版本切换缺少面向第四批异步消费者的稳定事件投递 / 订阅入口。
5. `Agent OS` 缺少人工确认单创建、确认处理或确认结果查询的正式接口。
6. 统一任务中心尚未形成可复用的跨模块正式入口；当前只看到 `Agent OS` 任务表和 `integration-hub` 局部任务表。

## 6. 新增或修改文件

- 新增：`docs/reports/verification/102-19-batch4-startup-gate-baseline-verification.md`

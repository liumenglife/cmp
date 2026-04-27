# 检索 / OCR / AI 业务应用主线专项设计：监控面板、告警阈值、恢复脚本与运维手册

## 1. 文档说明

本文档用于继续下沉 `intelligent-applications` 主线中"监控面板、告警阈值、恢复脚本和运维手册"能力，回答以下问题：

- 主线 7 个子系统（OCR、搜索、AI 任务与护栏、候选排序、多语言治理、回写冲突、运维治理）的监控数据如何汇集到统一面板
- 各子系统的核心指标、采集口径、聚合粒度和暴露方式
- 告警如何按紧急度分级、按渠道分发、按静默策略收敛
- 恢复脚本覆盖哪几类故障场景，回滚手册如何按影响等级执行
- 运维操作的权限矩阵如何设计，全局健康检查的探测面和阻断策略如何定义
- 监控、告警、恢复脚本、运维手册与验收口径如何形成闭环

本文是以下文档的下游专项设计：

- [`intelligent-applications Detailed Design`](../detailed-design.md)

本文不展开以下内容：

- 不写 OCR 引擎识别算法、搜索排序算法、AI Prompt 模板、候选打分规则、多语言术语审核流程和回写字段级映射的正文
- 不写具体监控产品（Prometheus / Grafana / ELK）的安装配置步骤或 Dashboard JSON
- 不写具体告警渠道的 SDK 调用代码或 Webhook 字段定义
- 不写实施排期、资源预估或部署拓扑

## 2. 目标与边界

### 2.1 设计目标

- 让 `ops-governor`（主线 §3.7）的运维治理职责落实到可运行的监控面板、告警规则、恢复脚本和运维手册四件套
- 让 7 个子系统的运行健康状态可观测、异常可感知、故障可恢复、操作可审计
- 让告警从"谁看到谁处理"升级为"按紧急度分级、按角色定向、按窗口收敛"的结构化通道
- 让恢复脚本覆盖各子系统已定义的典型故障场景，且不破坏合同主档、文档中心和条款库的真相源
- 让运维操作权限与主线内部的正式权限体系一致，不发生越权运维

### 2.2 边界原则

- 监控面板只展示本主线内部运行指标，不接管合同主档、文档中心、`Agent OS` 和条款库的自身运维面板。
- 告警只针对本主线内部可观测异常触发，不重复上游模块已有的基础架构告警（如 MySQL 连接池耗尽、Redis 不可用等）。
- 恢复脚本只执行本主线内部的状态修复、任务重推、数据重建和缓存失效，不直接操作合同主表、文档版本链和条款正文。
- 运维操作权限只在本主线运维角色范围内定义，不新增超越平台权限体系的超级运维角色。
- 全局健康检查是"本主线是否可正常服务"的对外探测面，不替代各上游模块的自身健康检查。

## 3. 统一监控面板设计

### 3.1 面板架构

统一监控面板按三层组织：

- **总览层（OpsOverview）**：面向值班运维和 TL，展示全局健康信号、异常计数、当前活跃告警、各子系统红绿灯状态和关键吞吐趋势。
- **子系统层（SubsystemDashboard）**：面向子系统 on-call，按 OCR / 搜索 / AI 任务与护栏 / 候选排序 / 多语言治理 / 回写冲突六个子系统独立切面。
- **明细层（DrillDownView）**：面向故障排查，按时间段、合同 ID、文档版本 ID、任务 ID、追踪号下钻到单次作业的执行细节、失败原因和关联审计事件。

### 3.2 面板数据来源

监控数据统一从以下来源采集，不直读业务主表：

| 数据来源 | 用途 | 采集方式 |
| --- | --- | --- |
| `ia_ocr_job` / `ia_ocr_result` 正式表 | OCR 作业与结果指标 | 定时聚合查询，写入指标存储 |
| `ia_search_query` / `ia_search_result_set` 正式表 | 搜索查询与结果指标 | 同上 |
| `ia_ai_application_job` / `ia_ai_application_result` 正式表 | AI 任务与结果指标 | 同上 |
| `ia_writeback_record` 正式表 | 回写指标 | 同上 |
| `ia_i18n_context` / 术语库管理表 | 多语言健康指标 | 同上 |
| 审计事件存储 | 关键动作计数、异常告警源 | 事件流消费 |
| 任务中心状态 | 队列深度、重试堆积、死信量 | 任务中心指标接口 |
| 搜索引擎适配层 | 索引文档量、别名状态、重建进度 | 适配层健康接口 |
| `Redis` 缓存状态 | 缓存命中率、过期率、锁竞争 | `Redis INFO` 指标 |
| `Agent OS` 回调摘要 | AI 运行时耗时、超时率、模型不可用 | `Agent OS` 运维接口 |

### 3.3 面板刷新与保留策略

- 总览层刷新间隔 ≤ 60 秒，子系统层 ≤ 30 秒，明细层按需查询。
- 聚合指标保留 90 天；单次任务明细保留 30 天；审计事件保留周期与审计中心一致。
- 超过保留周期的聚合数据降采样后归档，明细数据按审计策略清理。
- 面板展示不得暴露调用方的完整查询正文、合同内容片段或 AI 结果详情，只保留必要的摘要和统计计数。

## 4. 子系统核心指标

### 4.1 OCR 引擎子系统

#### 4.1.1 作业指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `ocr_job_accepted_count` | `ia_ocr_job` 按 `job_status=ACCEPTED` 计数 | 异常突降 |
| `ocr_job_queued_depth` | `job_status=QUEUED` 当前积压量 | 积压阈值告警 |
| `ocr_job_running_count` | `job_status=RUNNING` 当前运行量 | 执行器异常 |
| `ocr_job_succeeded_count` | `job_status=SUCCEEDED` 成功计数 | 成功率告警 |
| `ocr_job_failed_count` | `job_status=FAILED` 失败计数 | 失败率告警 |
| `ocr_job_cancelled_count` | `job_status=CANCELLED` 取消计数 | 异常取消 |
| `ocr_end_to_end_duration_p95` | `ACCEPTED → SUCCEEDED` 端到端 P95 耗时 | 时延告警 |
| `ocr_queue_wait_duration_p95` | `ACCEPTED → RUNNING` 队列等待 P95 耗时 | 积压告警 |
| `ocr_retry_count_distribution` | 按 `attempt_no` 分布 | 重试堆积 |
| `ocr_dead_letter_depth` | 任务中心 OCR 死信队列深度 | 死信告警 |
| `ocr_human_review_backlog` | 待人工复核 OCR 任务积压量 | 积压告警 |

#### 4.1.2 引擎指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `ocr_engine_call_success_rate` | 引擎调用成功 / 总调用 | 成功率告警 |
| `ocr_engine_p95_duration` | 引擎调用 P95 耗时 | 时延告警 |
| `ocr_engine_p99_duration` | 引擎调用 P99 耗时 | 时延告警 |
| `ocr_engine_timeout_rate` | 超时次数 / 总调用 | 超时告警 |
| `ocr_engine_rate_limit_count` | 限流命中次数 | 限流告警 |
| `ocr_engine_circuit_breaker_count` | 熔断触发次数 | 熔断告警 |
| `ocr_engine_degradation_route_count` | 降级路由次数 | 降级告警 |
| `ocr_engine_failure_by_mime_type` | 按文件类型分失败率 | 文件类型异常 |

#### 4.1.3 质量指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `ocr_page_coverage_ratio` | 已识别页数 / 总页数 | 质量告警 |
| `ocr_avg_text_confidence` | 文本层平均置信度 | 质量告警 |
| `ocr_table_success_rate` | 表格识别成功率 | 质量告警 |
| `ocr_seal_success_rate` | 印章区域识别成功率 | 质量告警 |
| `ocr_field_low_confidence_ratio` | `LOW_CONFIDENCE` 候选占比 | 质量告警 |
| `ocr_coordinate_error_rate` | 坐标校验失败率 | 质量告警 |
| `ocr_rebuild_completion_rate` | 版本切换后重建完成率 | 重建告警 |

### 4.2 搜索子系统

#### 4.2.1 索引指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `search_index_total_doc_count` | 索引文档总量 | 异常陡降 |
| `search_index_doc_count_by_type` | 按 `doc_type` 分文档量 | 来源异常 |
| `search_index_incremental_refresh_lag` | 增量刷新延迟 | 延迟告警 |
| `search_index_full_rebuild_duration` | 全量重建耗时 | 时延告警 |
| `search_index_alias_switch_duration` | 别名切换耗时 | 切换异常 |

#### 4.2.2 查询指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `search_query_qps` | 每秒查询量 | 突增 / 突降 |
| `search_query_p95_duration` | 查询 P95 时延 | 时延告警 |
| `search_query_p99_duration` | 查询 P99 时延 | 时延告警 |
| `search_result_snapshot_hit_rate` | 结果快照命中率 | 缓存异常 |
| `search_cache_hit_rate` | 缓存命中率 | 缓存异常 |
| `search_export_success_rate` | 导出成功率 | 导出异常 |

#### 4.2.3 质量指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `search_zero_result_rate` | 零结果查询占比 | 召回异常 |
| `search_anchorless_hit_intercept_count` | 无锚点结果拦截数 | 数据质量 |
| `search_permission_filter_rate` | 权限剔除率 | 权限异常 |
| `search_stale_version_hit_rate` | 旧版本命中率 | 版本治理 |
| `search_ai_result_unauthorized_intercept_count` | AI 结果越权拦截数 | 安全告警 |
| `search_org_scope_filter_rate` | 组织范围过滤率 | 权限异常 |

#### 4.2.4 恢复指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `search_failed_task_backlog` | 失败索引任务堆积量 | 积压告警 |
| `search_backfill_backlog` | 补数任务积压量 | 积压告警 |
| `search_rebuild_failure_rate` | 重建失败率 | 重建告警 |
| `search_rollback_count` | 别名回退次数 | 回退告警 |

### 4.3 AI 任务执行与护栏子系统

#### 4.3.1 吞吐指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `ai_context_assembly_accepted_count` | 上下文装配受理数 | 异常突降 |
| `ai_context_assembly_completed_count` | 上下文装配完成数 | 成功率 |
| `ai_context_assembly_failed_count` | 上下文装配失败数 | 失败率告警 |
| `ai_guardrail_blocked_count` | 护栏拦截数 | 拦截率告警 |
| `ai_guardrail_escalated_to_human_count` | 转人工确认数 | 积压告警 |
| `ai_job_accepted_count` | AI 作业受理数 | 异常突降 |
| `ai_job_succeeded_count` | AI 作业成功数 | 成功率 |
| `ai_job_failed_count` | AI 作业失败数 | 失败率告警 |

#### 4.3.2 时延指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `ai_context_assembly_duration_p95` | 装配 P95 耗时 | 时延告警 |
| `ai_evidence_clipping_duration_p95` | 证据裁剪 P95 耗时 | 时延告警 |
| `ai_guardrail_validation_duration_p95` | 护栏校验 P95 耗时 | 时延告警 |
| `ai_human_confirmation_wait_duration_p95` | 人工确认等待 P95 时长 | 积压告警 |
| `ai_agent_os_call_duration_p95` | `Agent OS` 调用 P95 耗时 | 时延告警 |
| `ai_agent_os_timeout_rate` | `Agent OS` 超时率 | 超时告警 |

#### 4.3.3 质量指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `ai_evidence_coverage_ratio` | 输出证据覆盖率 | 质量告警 |
| `ai_sourceless_conclusion_intercept_rate` | 无来源结论拦截率 | 质量告警 |
| `ai_conflict_degradation_rate` | 冲突结果降级率 | 质量告警 |
| `ai_human_rejection_rate` | 人工驳回率 | 质量告警 |
| `ai_writeback_release_rate` | 回写放行率 | 放行异常 |

#### 4.3.4 健康指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `ai_budget_exceed_rate` | Token 预算超限率 | 资源告警 |
| `ai_assembly_cache_hit_rate` | 上下文装配缓存命中率 | 缓存异常 |
| `ai_rerun_rate` | 任务重跑率 | 稳定性告警 |
| `ai_source_superseded_ratio` | 来源失效导致 `SUPERSEDED` 比例 | 来源异常 |
| `ai_protected_result_backlog` | `ProtectedResultSnapshot` 堆积量 | 积压告警 |

### 4.4 候选排序与质量评估子系统

#### 4.4.1 吞吐指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `candidate_generation_count` | 候选生成数 | 异常突降 |
| `ranking_completion_count` | 排序完成数 | 成功率 |
| `quality_evaluation_completion_count` | 质量评估完成数 | 成功率 |
| `release_decision_count` | 放行决策数 | 按类型分布 |
| `ranking_failed_count` | 排序失败数 | 失败率告警 |
| `quality_evaluation_failed_count` | 质量评估失败数 | 失败率告警 |

#### 4.4.2 时延指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `candidate_normalization_duration_p95` | 候选标准化 P95 耗时 | 时延告警 |
| `ranking_duration_p95` | 排序 P95 耗时 | 时延告警 |
| `quality_evaluation_duration_p95` | 质量评估 P95 耗时 | 时延告警 |
| `escalation_to_human_duration_p95` | 转人工 P95 耗时 | 积压告警 |

#### 4.4.3 质量指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `candidate_elimination_rate` | 候选淘汰率 | 质量异常 |
| `candidate_conflict_rate` | 候选冲突率 | 质量告警 |
| `sourceless_intercept_rate` | 无来源结论拦截率 | 质量告警 |
| `partial_publish_rate` | 部分发布率（`release_decision=PARTIAL_PUBLISH`） | 质量趋势 |
| `human_rejection_rate` | 人工驳回率 | 质量告警 |

#### 4.4.4 稳定性指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `idempotency_hit_rate` | 幂等命中率 | 异常重复 |
| `ranking_retry_rate` | 排序重试率 | 稳定性告警 |
| `snapshot_rebuild_rate` | 快照重建率 | 来源异常 |
| `candidate_cache_hit_rate` | 候选缓存命中率 | 缓存异常 |

### 4.5 多语言术语治理子系统

#### 4.5.1 覆盖率指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `terminology_published_term_count` | 已发布 `TermEntry` 数 | 趋势监控 |
| `terminology_approved_coverage_by_language` | 各语言 `APPROVED` 覆盖率 | 覆盖告警 |
| `terminology_uncovered_clause_count` | 未覆盖语言的条款数量 | 覆盖告警 |

#### 4.5.2 时延指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `terminology_draft_to_publish_duration_avg` | 术语从草稿到发布平均周期 | 流程积压 |
| `terminology_review_wait_duration_avg` | 审核等待平均时长 | 流程积压 |

#### 4.5.3 质量指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `i18n_context_failed_rate` | `i18n_status=FAILED` 比率 | 语言识别异常 |
| `language_degradation_rate` | 语言降级发生率 | 术语缺失告警 |
| `terminology_empty_hit_rate` | 术语空命中率 | 术语覆盖告警 |

#### 4.5.4 健康指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `terminology_profile_publish_frequency` | `TerminologyProfile` 发布频率 | 变更频率 |
| `terminology_cache_hit_rate` | 术语快照缓存命中率 | 缓存异常 |
| `terminology_deprecated_still_referenced_count` | 已废弃但仍被引用的术语数量 | 数据一致性 |

### 4.6 结果回写与冲突消解子系统

#### 4.6.1 吞吐指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `writeback_created_count` | 回写记录创建数 | 异常突降 |
| `writeback_written_count` | 成功写入数 | 成功率 |
| `writeback_skipped_count` | 跳过数 | 冲突趋势 |
| `writeback_failed_count` | 失败数 | 失败率告警 |

#### 4.6.2 时延指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `writeback_pending_to_written_duration_p95` | `PENDING → WRITTEN` 端到端 P95 时长 | 时延告警 |
| `writeback_gateway_call_duration_p95` | 受控写接口调用 P95 耗时 | 时延告警 |
| `writeback_lock_wait_duration_p95` | 锁等待 P95 时长 | 锁竞争告警 |

#### 4.6.3 冲突指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `writeback_version_conflict_rate` | 版本冲突率 | 并发异常 |
| `writeback_slot_conflict_rate` | 槽位冲突率 | 冲突告警 |
| `writeback_confidence_conflict_rate` | 置信度冲突率 | 质量告警 |
| `writeback_human_rejection_trigger_rate` | 人工否决触发率 | 质量告警 |
| `writeback_unresolved_equal_rate` | `UNRESOLVED_EQUAL` 率 | 升级告警 |

#### 4.6.4 健康指标

| 指标 | 采集口径 | 告警关联 |
| --- | --- | --- |
| `writeback_retry_backlog` | 重试堆积量 | 积压告警 |
| `writeback_dead_letter_depth` | 死信队列积压量 | 死信告警 |
| `writeback_pending_timeout_count` | `PENDING` 超时量 | 超时告警 |
| `writeback_lock_contention_count` | 锁竞争次数 | 并发告警 |
| `writeback_partial_publish_gap_annotation_coverage` | `PARTIAL_PUBLISH` 回写缺口标注覆盖率 | 质量告警 |
| `writeback_post_write_consistency_anomaly_rate` | 回写后数据一致性校验异常率 | 一致性告警 |

## 5. 告警阈值与分级

### 5.1 告警等级定义

告警统一分三级：

| 等级 | 标识 | 含义 | 响应要求 |
| --- | --- | --- | --- |
| `P1` / 紧急 | Critical | 主线核心功能不可用、数据完整性受威胁、真相源有被污染风险 | 5 分钟内响应，15 分钟内启动处理 |
| `P2` / 严重 | Warning | 子系统部分能力降级、性能显著恶化、积压趋势持续扩大 | 15 分钟内响应，1 小时内启动处理 |
| `P3` / 一般 | Info | 单一指标波动、非关键异常、需关注但不影响主链路的信号 | 下一工作周期内确认 |

### 5.2 告警阈值矩阵

#### 5.2.1 OCR 引擎子系统告警

| 告警名称 | 触发条件 | 等级 |
| --- | --- | --- |
| OCR 队列严重积压 | `ocr_job_queued_depth > 100` 持续 5 分钟 | P2 |
| OCR 队列紧急积压 | `ocr_job_queued_depth > 500` 持续 3 分钟 | P1 |
| OCR 失败率升高 | `ocr_job_failed_count / (succeeded_count + failed_count) > 10%` 持续 5 分钟 | P2 |
| OCR 失败率严重 | 同上比率 > 25% 持续 3 分钟 | P1 |
| OCR 引擎熔断 | `ocr_engine_circuit_breaker_count > 0` 且最近 5 分钟无恢复 | P1 |
| OCR 引擎成功率下降 | `ocr_engine_call_success_rate < 95%` 持续 5 分钟 | P2 |
| OCR 引擎严重不可用 | `ocr_engine_call_success_rate < 80%` 持续 3 分钟 | P1 |
| OCR 结果质量分低于告警阈值 | `ocr_avg_quality_score < OcrQualityProfile.alert_threshold_profile_code` 对应阈值，持续 10 分钟 | P2 |
| OCR 版本切换后重建积压 | `ocr_rebuild_completion_rate < 80%` 且积压任务 > 20 | P2 |
| OCR 死信堆积 | `ocr_dead_letter_depth > 50` 持续 5 分钟 | P2 |
| OCR 死信紧急堆积 | `ocr_dead_letter_depth > 200` 持续 3 分钟 | P1 |
| OCR 人工复核积压 | `ocr_human_review_backlog > 30` 持续 15 分钟 | P2 |
| OCR 结果写入失败 | `ocr_job_status=SUCCEEDED` 但 `ia_ocr_result` 写入未完成数量 > 10 持续 10 分钟 | P2 |
| OCR 文档中心挂接失败 | `OcrResultAggregate` 已存在但 `dc_capability_binding` 缺失数量 > 15 持续 15 分钟 | P2 |
| OCR 历史结果仍被默认消费 | `SUPERSEDED` 状态结果被搜索或 AI 消费命中次数 > 5 持续 10 分钟 | P2 |

#### 5.2.2 搜索子系统告警

| 告警名称 | 触发条件 | 等级 |
| --- | --- | --- |
| 搜索索引文档异常陡降 | `search_index_total_doc_count` 较前一周期下降 > 20% | P1 |
| 搜索增量刷新严重延迟 | `search_index_incremental_refresh_lag > 10 分钟` | P2 |
| 搜索查询时延恶化 | `search_query_p95_duration > 2 秒` 持续 5 分钟 | P2 |
| 搜索查询时延严重恶化 | `search_query_p95_duration > 5 秒` 持续 3 分钟 | P1 |
| 搜索零结果率异常 | `search_zero_result_rate > 40%` 持续 5 分钟 | P2 |
| 搜索别名切换失败 | `search_index_alias_switch_duration` 任务超时或失败 | P1 |
| 搜索重建失败率升高 | `search_rebuild_failure_rate > 10%` 持续 10 分钟 | P2 |
| 搜索失败任务堆积 | `search_failed_task_backlog > 100` 持续 5 分钟 | P2 |
| 搜索补数积压 | `search_backfill_backlog > 200` 持续 10 分钟 | P2 |
| 搜索越权拦截突增 | `search_ai_result_unauthorized_intercept_count` 较前一周期突增 > 50 | P1 |
| 搜索引擎完全不可用 | 搜索引擎适配层 `health` 接口持续不可达 > 3 分钟 | P1 |

#### 5.2.3 AI 任务执行与护栏子系统告警

| 告警名称 | 触发条件 | 等级 |
| --- | --- | --- |
| AI 上下文装配失败率升高 | `ai_context_assembly_failed_count / accepted_count > 10%` 持续 5 分钟 | P2 |
| AI 护栏拦截率异常升高 | `ai_guardrail_blocked_count / total_checked > 30%` 持续 10 分钟 | P2 |
| AI 人工确认积压 | `ai_guardrail_escalated_to_human_count` 未处理量 > 50 且持续 15 分钟 | P2 |
| AI 人工确认紧急积压 | 同上 > 200 且持续 10 分钟 | P1 |
| `Agent OS` 调用成功率下降 | `ai_agent_os_call_success_rate < 95%` 持续 5 分钟 | P2 |
| `Agent OS` 严重不可用 | `ai_agent_os_call_success_rate < 80%` 持续 3 分钟 | P1 |
| `ProtectedResultSnapshot` 堆积 | `ai_protected_result_backlog > 100` 持续 10 分钟 | P2 |
| AI 证据覆盖率下降 | `ai_evidence_coverage_ratio < 0.6` 持续 15 分钟 | P2 |
| AI 无来源结论拦截率突增 | `ai_sourceless_conclusion_intercept_rate > 20%` 持续 10 分钟 | P2 |
| AI 来源失效比例升高 | `ai_source_superseded_ratio > 15%` 持续 15 分钟 | P2 |

#### 5.2.4 候选排序与质量评估子系统告警

| 告警名称 | 触发条件 | 等级 |
| --- | --- | --- |
| 排序失败率升高 | `ranking_failed_count / total_ranking > 10%` 持续 5 分钟 | P2 |
| 质量评估失败率升高 | `quality_evaluation_failed_count / total_evaluation > 10%` 持续 5 分钟 | P2 |
| 候选冲突率升高 | `candidate_conflict_rate > 20%` 持续 10 分钟 | P2 |
| 候选淘汰率异常 | `candidate_elimination_rate > 60%` 持续 10 分钟 | P2 |
| 人工驳回率升高 | `human_rejection_rate > 15%` 持续 15 分钟 | P2 |
| 快照重建率升高 | `snapshot_rebuild_rate > 20%` 持续 10 分钟 | P2 |
| 排序重试率升高 | `ranking_retry_rate > 10%` 持续 10 分钟 | P2 |

#### 5.2.5 多语言术语治理子系统告警

| 告警名称 | 触发条件 | 等级 |
| --- | --- | --- |
| 术语空命中率升高 | `terminology_empty_hit_rate > 10%` 持续 30 分钟 | P3 |
| `i18n_context` 失败率升高 | `i18n_context_failed_rate > 10%` 持续 15 分钟 | P2 |
| 语言降级率升高 | `language_degradation_rate > 15%` 持续 15 分钟 | P2 |
| 术语审核积压 | `terminology_review_wait_duration_avg > 48 小时` | P3 |
| 已废弃术语仍被引用 | `terminology_deprecated_still_referenced_count > 10` 持续 1 小时 | P3 |

#### 5.2.6 结果回写与冲突消解子系统告警

| 告警名称 | 触发条件 | 等级 |
| --- | --- | --- |
| 回写失败率升高 | `writeback_failed_count / (written + skipped + failed) > 10%` 持续 5 分钟 | P2 |
| 回写死信堆积 | `writeback_dead_letter_depth > 30` 持续 10 分钟 | P2 |
| 回写死信紧急堆积 | `writeback_dead_letter_depth > 100` 持续 5 分钟 | P1 |
| 回写端到端时延恶化 | `writeback_pending_to_written_duration_p95 > 5 分钟` 持续 10 分钟 | P2 |
| 回写版本冲突率升高 | `writeback_version_conflict_rate > 10%` 持续 10 分钟 | P2 |
| `UNRESOLVED_EQUAL` 升级率升高 | `writeback_unresolved_equal_rate > 5%` 持续 15 分钟 | P2 |
| 回写 PENDING 超时 | `writeback_pending_timeout_count > 50` 持续 10 分钟 | P2 |
| 回写后一致性校验异常 | `writeback_post_write_consistency_anomaly_rate > 5%` 持续 5 分钟 | P1 |

### 5.3 告警静默与收敛

- 同一告警规则在 5 分钟内最多发送一次通知，避免告警风暴。
- 同一子系统在 30 分钟内累计触发 5 次以上通知时，自动升级为摘要通知并附带 `suppressed_count`。
- 计划内维护窗口（通过 `maintenance_window` 标记）期间，可将指定子系统的告警降级或静默。
  - `maintenance_window` 由运维管理员通过运维工作台配置，按 `subsystem + start_time + end_time + level` 四元组定义；当前活跃窗口通过 `Redis` 缓存读取，TTL 与窗口结束时间一致，缓存失效则回退到数据库读取。
- 引擎熔断期间，同一引擎的超时、限流和降级告警合并为熔断告警，不重复发送。
- 已被人工确认（`acknowledged`）的告警在恢复前不再重复推送；恢复后自动清除确认标记。

## 6. 告警通知渠道

### 6.1 渠道定义

| 渠道 | 适用等级 | 说明 |
| --- | --- | --- |
| 运维工作台（WebSocket 推送） | P1 / P2 / P3 | 实时推送至运维面板告警中心，支持确认、静默、升级 |
| 即时通讯（企业微信 / 钉钉 / Slack） | P1 / P2 | 推送到指定 on-call 群组或个人 |
| 邮件 | P2 / P3 | 每日摘要邮件 + 实时 P2 告警邮件 |
| 短信 | P1 | 紧急告警短信通知 on-call 轮值人员 |
| 电话 | P1（可选） | 持续未确认的 P1 告警自动升级为电话通知 |

### 6.2 渠道路由规则

- P1 告警：同时推送运维工作台、即时通讯群组、短信和 on-call 个人即时通讯。
- P2 告警：推送运维工作台、即时通讯群组、邮件（实时）。
- P3 告警：仅推送运维工作台，并在每日摘要邮件中汇总。
- 同一告警在升级为更高等级后，按目标等级渠道重新分发，不沿用旧等级渠道。
- 运维工作台内的告警超过 15 分钟未确认时，自动升级通知渠道（P2→追加即时通讯群组，P1→追加电话）。

### 6.3 通知内容

每条告警通知至少包含：

- 告警名称、等级、触发时间
- 当前指标值与阈值
- 受影响子系统与范围（合同范围、文档版本范围、任务类型等摘要，不暴露正文）
- 关联的追踪号（`trace_id`）
- 快速操作入口：确认、静默、跳转明细面板
- 建议的恢复脚本清单（按适用性排序）

## 7. 恢复脚本与回滚手册

### 7.1 恢复脚本总则

恢复脚本的统一约束：

- 所有脚本在执行前必须校验当前操作人拥有对应运维权限。
- 所有脚本在执行前必须记录操作审计事件，包含操作人、脚本名、参数、时间戳、追踪号。
- 合同主档、文档中心、条款库的真相表只允许读取，禁止写入和更新。
- 只允许回填本主线内部的状态、索引、缓存和补偿任务；如需跨模块操作，必须通过对应模块的受控运维接口。
- 脚本执行结果写入恢复操作日志表 `ia_recovery_operation_log`。

### 7.2 恢复场景与脚本清单

#### 7.2.1 OCR 引擎恢复

| 恢复场景 | 脚本名称 | 操作说明 | 影响等级 |
| --- | --- | --- | --- |
| OCR 作业死信重推 | `recover_ocr_dead_letter` | 读取任务中心 OCR 死信队列，筛选可重试作业并重新提交到执行队列 | 中 |
| OCR 结果写入失败补偿 | `recover_ocr_result_write` | 针对 `OcrJob` 状态为 `SUCCEEDED` 但 `OcrResultAggregate` 缺失的作业，重放结果归一化与写入 | 中 |
| OCR 文档中心挂接补偿 | `recover_ocr_dc_binding` | 针对 `OcrResultAggregate` 已存在但 `dc_capability_binding` 缺失的结果，重新挂接 | 低 |
| OCR 版本切换后批量重建 | `recover_ocr_version_rebuild` | 按文档资产范围或时间范围，批量触发 `SUPERSEDED` 结果对应新版本的 OCR 重建任务 | 高 |
| OCR 引擎熔断后手动恢复 | `recover_ocr_engine_circuit_breaker` | 人工确认引擎恢复后，重置熔断状态并重新开放该引擎的路由 | 中 |
| OCR 搜索索引回补 | `recover_ocr_to_search_index` | 针对 OCR 成功但搜索索引缺失的文档，触发搜索补偿补数任务 | 低 |

#### 7.2.2 搜索恢复

| 恢复场景 | 脚本名称 | 操作说明 | 影响等级 |
| --- | --- | --- | --- |
| 搜索索引构建失败重试 | `recover_search_index_build` | 筛选失败索引任务，按来源对象范围重新提交构建 | 中 |
| 搜索全量重建 | `recover_search_full_rebuild` | 触发新一代全量索引重建，采用双代运行策略，旧代继续服务 | 高 |
| 搜索别名回退 | `recover_search_alias_rollback` | 将搜索别名切回上一代索引，用于新代校验失败或严重数据异常 | 高 |
| 搜索补数批量执行 | `recover_search_backfill` | 按来源类型和时间范围，批量提交缺失索引的补数任务 | 中 |
| 搜索结果快照清理 | `recover_search_snapshot_cleanup` | 批量清理过期或越权的 `ia_search_result_set` 和缓存快照 | 低 |
| 搜索缓存全量失效 | `recover_search_cache_invalidate` | 清除 `Redis` 中全部搜索相关缓存，触发缓存全量重建 | 低 |

#### 7.2.3 AI 任务恢复

| 恢复场景 | 脚本名称 | 操作说明 | 影响等级 |
| --- | --- | --- | --- |
| AI 任务死信重推 | `recover_ai_job_dead_letter` | 读取 AI 任务死信队列，筛选可重试任务重新提交 | 中 |
| AI 上下文装配失败重试 | `recover_ai_context_assembly` | 针对上下文装配失败的 `AiContextAssemblyJob`，复用已落库来源快照重新装配 | 中 |
| AI 护栏失败重放 | `recover_ai_guardrail_replay` | 针对护栏判定 `BLOCK` / `REJECT` 的结果，按修正后的护栏策略重放校验 | 中 |
| AI 人工确认超时处理 | `recover_ai_human_confirmation_timeout` | 对 `WAITING_HUMAN_CONFIRMATION` 超时任务执行通知升级或按策略超时处理 | 低 |
| `ProtectedResultSnapshot` 清理 | `recover_protected_result_cleanup` | 批量清理过期 `ProtectedResultSnapshot`，释放存储 | 低 |
| AI 来源失效结果批量标记 | `recover_ai_source_superseded` | 按来源对象范围，批量将引用了已失效来源的 AI 结果标记为 `SUPERSEDED` | 中 |

#### 7.2.4 候选排序与质量评估恢复

| 恢复场景 | 脚本名称 | 操作说明 | 影响等级 |
| --- | --- | --- | --- |
| 排序失败重跑 | `recover_ranking_retry` | 针对排序失败的 `CandidateRankingSnapshot`，复用已存在来源快照重新执行排序 | 中 |
| 质量评估失败重跑 | `recover_quality_evaluation_retry` | 针对质量评估失败的 `QualityEvaluationReport`，复用候选快照重新执行评估 | 中 |
| 候选快照清理 | `recover_candidate_snapshot_cleanup` | 清理过期或已废弃的候选快照和缓存 | 低 |
| 来源失效快照标记 | `recover_candidate_source_superseded` | 当上游关键来源失效时，批量标记受影响候选快照为 `SUPERSEDED` | 中 |

#### 7.2.5 多语言术语治理恢复

| 恢复场景 | 脚本名称 | 操作说明 | 影响等级 |
| --- | --- | --- | --- |
| 术语快照加载失败重试 | `recover_terminology_profile_load` | 针对 `terminology_profile_code` 加载失败的 `ia_i18n_context`，重新加载已发布快照 | 低 |
| `i18n_context` 失败重处理 | `recover_i18n_context_failed` | 批量重新处理 `i18n_status=FAILED` 的上下文，使用当前已发布术语快照重新归一 | 中 |
| 术语缓存全量刷新 | `recover_terminology_cache_refresh` | 清除全部术语快照缓存并重新预热 | 低 |

#### 7.2.6 结果回写恢复

| 恢复场景 | 脚本名称 | 操作说明 | 影响等级 |
| --- | --- | --- | --- |
| 回写死信重推 | `recover_writeback_dead_letter` | 读取回写死信队列，筛选可重试的回写记录重新提交 | 中 |
| 回写冲突人工裁决辅助 | `recover_writeback_conflict_resolve` | 对有 `UNRESOLVED_EQUAL` 冲突的回写记录，生成完整冲突证据包供人工裁决 | 中 |
| 回写 PENDING 超时清理 | `recover_writeback_pending_timeout` | 将超时 `PENDING` 回写记录标记为 `FAILED`（超时原因），并补建新记录 | 中 |
| 回写后一致性修复 | `recover_writeback_consistency` | 对回写后一致性校验异常的记录，输出差异报告供人工确认后手动修复 | 高 |
| 批量标记旧回写失效 | `recover_writeback_mark_superseded` | 按合同范围批量标记旧回写引用为 `SUPERSEDED` | 低 |

### 7.3 回滚手册

#### 7.3.1 回滚分级

| 回滚等级 | 触发条件 | 回滚范围 | 审批要求 |
| --- | --- | --- | --- |
| L1 紧急回滚 | 生产事故、数据完整性受威胁 | 全模块或子系统级回滚 | CTO 或 on-call 负责人即时审批 |
| L2 计划回滚 | 功能缺陷、性能严重退化 | 单子系统或单能力回滚 | 技术负责人审批 |
| L3 维护回滚 | 配置错误、小范围数据修复 | 单配置或单表级回滚 | 运维负责人审批 |

#### 7.3.2 搜索双代回滚手册

| 步骤 | 操作 | 验证 | 回滚 |
| --- | --- | --- | --- |
| 1 | 确认当前活跃索引代次 `current_generation` 和备用代次 `standby_generation` | `search_index_alias_status` 接口 | — |
| 2 | 切换别名到备用代次 | `recover_search_alias_rollback` 脚本 | 别名切换后的查询结果一致性 |
| 3 | 观察新代查询指标（QPS、P95、零结果率）持续 5 分钟 | 搜索子系统面板 | 若指标恶化，再次执行步骤 2 回切 |
| 4 | 确认稳定后，将旧代标记为可清理 | 更新 `rebuild_generation` 元数据 | 保留旧代 24 小时再清理 |

#### 7.3.3 OCR 引擎路由回滚手册

| 步骤 | 操作 | 验证 | 回滚 |
| --- | --- | --- | --- |
| 1 | 通过 `recover_ocr_engine_circuit_breaker` 重置熔断状态 | 引擎健康接口 | — |
| 2 | 手动放行一批测试作业（≤5 个），验证引擎恢复 | `ocr_engine_call_success_rate` | 若测试作业全部失败，重新触发熔断 |
| 3 | 逐步放量：先打开 10% 路由权重，观察 10 分钟 | OCR 引擎面板成功率 | 若成功率 < 90%，立即切回备用引擎 |
| 4 | 观察稳定后恢复到 100% 权重 | OCR 引擎面板全指标 | — |

#### 7.3.4 回写批量标记失效回滚手册

| 步骤 | 操作 | 验证 | 回滚 |
| --- | --- | --- | --- |
| 1 | 确认需要回滚的合同范围 | 数据库查询 | — |
| 2 | 暂停对应合同的新回写任务（通过 `target_type + target_id` 锁） | `writeback_pending_timeout_count` | — |
| 3 | 执行 `recover_writeback_mark_superseded` 脚本 | 回写冲突指标面板 | 若误标记，通过回写死信重推恢复 |
| 4 | 恢复新回写任务 | `writeback_written_count` 恢复 | — |

#### 7.3.5 AI 结果批量标记失效回滚手册

| 步骤 | 操作 | 验证 | 回滚 |
| --- | --- | --- | --- |
| 1 | 确认来源失效范围（合同、文档版本或条款版本） | AI 来源失效指标 | — |
| 2 | 暂停该范围内的新 AI 任务受理 | `ai_job_accepted_count` | — |
| 3 | 执行 `recover_ai_source_superseded` 脚本 | `ai_source_superseded_ratio` | 历史结果仅标记不删除，无需数据回滚 |
| 4 | 恢复新任务受理 | `ai_job_accepted_count` 恢复 | — |

#### 7.3.6 候选排序与多语言治理子系统的回滚

候选排序子系统和多语言术语治理子系统暂不要求独立回滚手册，原因如下：

- 候选排序的 `CandidateRankingSnapshot` 和 `QualityEvaluationReport` 均为运行时对象，不持有正式持久化主表，其恢复路径已由 §7.2.4 的排序重跑和质量评估重跑覆盖。
- 多语言术语治理的 `TermEntry` / `TranslationUnit` 是管理型资源，其版本状态机和审核流程（DRAFT → REVIEW → PUBLISHED → DEPRECATED）本身即回退机制。术语快照（`TerminologyProfile`）发布后不可变，回退场景只需发布新快照或标记旧快照废弃，不需要额外回滚手册。
- 若上述两个子系统的关联数据（如缓存中的候选快照、`ia_i18n_context` 中的术语引用）出现异常，可通过 §7.2.4 和 §7.2.5 的恢复脚本修复，不涉及跨系统数据回滚。

## 8. 运维操作权限

### 8.1 权限矩阵

| 操作 | 运维观察者 | 运维操作者 | 运维管理员 | 超级管理员 |
| --- | --- | --- | --- | --- |
| 查看监控面板（总览层） | ✓ | ✓ | ✓ | ✓ |
| 查看监控面板（子系统层） | ✓ | ✓ | ✓ | ✓ |
| 查看监控面板（明细层） | 受限（去敏） | ✓ | ✓ | ✓ |
| 确认告警 | — | ✓ | ✓ | ✓ |
| 静默告警 | — | ✓ | ✓ | ✓ |
| 执行恢复脚本（影响等级：低） | — | ✓ | ✓ | ✓ |
| 执行恢复脚本（影响等级：中） | — | — | ✓ | ✓ |
| 执行恢复脚本（影响等级：高） | — | — | ✓（需双人复核） | ✓ |
| 执行 L1 紧急回滚 | — | — | ✓（需 CTO / on-call 负责人审批） | ✓（需 CTO / on-call 负责人审批） |
| 执行 L2 计划回滚 | — | — | ✓ | ✓ |
| 执行 L3 维护回滚 | — | — | ✓ | ✓ |
| 配置告警规则 | — | — | ✓ | ✓ |
| 配置监控面板 | — | — | ✓ | ✓ |
| 管理运维权限 | — | — | — | ✓ |
| 查看审计日志 | ✓ | ✓ | ✓ | ✓ |
| 导出运维数据 | — | ✓ | ✓ | ✓ |

### 8.2 权限与主线已有权限体系对齐

- 运维角色不与合同数据权限、文档访问权限、条款管理权限混淆。一个用户可同时拥有业务角色和运维角色，但运维操作不受业务数据权限裁剪。
- `运维观察者`、`运维操作者`、`运维管理员` 三个角色由平台权限体系统一管理，不在此专项中重复定义角色表。
- 执行恢复脚本和回滚操作产生的审计事件，必须携带操作人 (`operator_id`)、角色 (`operator_role`)、操作类型 (`action_type`) 和追踪号 (`trace_id`)。
- 回滚操作如涉及合同视图数据修改，必须通过合同管理本体受控写入口，不得绕过业务权限直接写表。

### 8.3 敏感操作管控

以下操作列为敏感操作，必须经过双人复核（运维管理员 + 任一其他运维管理员或超级管理员）方可执行：

- 搜索全量重建（`recover_search_full_rebuild`）
- 搜索别名回退（`recover_search_alias_rollback`）
- OCR 版本切换后批量重建（`recover_ocr_version_rebuild`）
- 回写后一致性修复（`recover_writeback_consistency`）
- 任何 L1 紧急回滚操作
- 任何涉及批量标记正式结果为 `SUPERSEDED` 的操作

## 9. 全局健康检查

### 9.1 健康检查接口

`ops-governor` 暴露统一健康检查接口，供负载均衡、上游模块和运维探活使用。

健康检查分为两级：

- `/health/liveness`：浅度探活，检查本主线进程是否存活。
- `/health/readiness`：深度就绪检查，检查本主线是否可正常服务。

### 9.2 `readiness` 检查项

| 检查项 | 检查内容 | 失败阻断策略 |
| --- | --- | --- |
| MySQL 连接可用 | 主库连接池可用，能执行 `SELECT 1` | 阻断（不可服务） |
| Redis 连接可用 | Redis 连接可用，能执行 `PING` | 降级（缓存不可用但主库服务继续） |
| 搜索引擎可用 | 搜索引擎适配层 `health` 接口返回正常 | 降级（搜索降级为有限结构化查询） |
| OCR 引擎可用 | 至少一个 OCR 引擎路由可用 | 降级（OCR 新作业排队等待，已落库结果继续可查） |
| `Agent OS` 可用 | `Agent OS` 健康检查接口正常 | 降级（暂停新增 AI 任务，已落库结果继续可查） |
| 任务执行器存活 | 至少一个执行器 Pod 存活且心跳正常 | 阻断（任务无法推进） |
| 死信队列未溢出 | OCR / AI / 回写死信队列均未达到容量上限 | 告警（不影响健康状态，仅触发 P2 告警） |
| 数据库无死锁 | 最近 5 分钟无数据库死锁事件 | 告警（不影响健康状态，仅触发 P2 告警） |
| 缓存命中率正常 | 搜索和术语缓存命中率不低于最低阈值 | 告警（不影响健康状态，仅触发 P2 告警） |

### 9.3 健康状态聚合

| 聚合状态 | 含义 | 对上游影响 |
| --- | --- | --- |
| `READY` | 全部检查通过 | 正常服务 |
| `DEGRADED` | 存在可降级检查项失败，主链路仍可用 | 上游可继续调用，但部分能力受限 |
| `UNAVAILABLE` | 存在阻断性检查项失败 | 上游应暂停向本主线发送新请求，已落库结果仍可查询 |

### 9.4 健康检查缓存与超时

- `liveness` 检查无缓存，每次实时执行，超时 2 秒。
- `readiness` 检查结果缓存 15 秒，超时 10 秒；超时返回最后一次已知结果并标记 `stale=true`。
- `readiness` 检查中单个外部依赖项（搜索引擎、`Agent OS`）的超时独立设置为 5 秒，不拖慢整体检查。

## 10. 验收口径

### 10.1 面板验收

- 统一监控面板能够实时展示总览层（红绿灯状态、关键吞吐、活跃告警数）。
- 六张子系统面板均能按 §4 所列指标口径正确展示各子系统运行数据。
- 明细层能够按合同 ID、文档版本 ID、任务 ID、追踪号下钻到单次作业细节。

### 10.2 指标验收

- §4 所列 6 个子系统的全部指标均有对应的采集口径和聚合逻辑，不存在空指标或未定义采集路径。
- 指标采集不对业务主表造成明显查询压力（聚合查询使用只读副本或异步汇总表）。
- 指标展示面板的刷新延迟不超过 60 秒（总览层）和 30 秒（子系统层）。

### 10.3 告警验收

- §5.2 所列全部告警规则配置到位，告警测试信号能正确触发并在对应渠道收到通知。
- P1 告警在 1 分钟内送达运维工作台和即时通讯群组；P2 告警在 2 分钟内送达。
- 告警静默、收敛和升级逻辑正确：同一告警 5 分钟内不重复；P2 告警 15 分钟未确认触发渠道升级。
- 计划内维护窗口期间，指定子系统的告警被正确静默或降级。

### 10.4 恢复脚本验收

- §7.2 所列全部恢复脚本均可执行，不产生副作用破坏真相源。
- 每项恢复脚本执行后，在 `ia_recovery_operation_log` 中留有完整审计记录。
- `ia_recovery_operation_log` 表结构（见父文档 `detailed-design.md §4.15`）包含 `script_name`、`operator_id`、`operator_role`、`execution_status`、`target_subsystem`、`trace_id` 和 `review_operator_id` 字段，验收时确认该表存在且字段完整。
- OCR 死信重推、搜索补数、AI 任务重推、回写死信重推四类核心脚本通过故障演练验证。

### 10.5 回滚验收

- 搜索双代回滚在别名切换后 5 分钟内完成验证和回退判断。
- OCR 引擎路由回滚在逐步放量过程中，指标异常时能自动或手动切回备用引擎。
- 回写批量失效回滚不产生数据丢失，误标记可通过死信重推恢复。
- L1 回滚操作强制要求审批，未经审批无法执行。

### 10.6 权限验收

- 运维观察者不能执行恢复脚本、不能确认或静默告警、不能查看明细层完整数据。
- 运维操作者不能执行 L1 / L2 回滚操作和高影响恢复脚本。
- 运维管理员执行高影响操作时，需要双人复核；操作记录中留有复核人信息。
- 所有运维操作均有审计事件，能找到操作人、操作时间、追踪号和影响范围。

### 10.7 健康检查验收

- `/health/liveness` 在进程存活时返回 `200 OK`。
- `/health/readiness` 在全部检查通过时返回 `{"status": "READY"}`。
- 单个外部依赖不可用时，`readiness` 正确返回 `DEGRADED` 且具体失败项可见。
- 阻断性依赖不可用时，`readiness` 正确返回 `UNAVAILABLE`。

### 10.8 对齐验收

- 本文列出的全部子系统核心指标与对应专项设计（OCR 引擎 §13、搜索 §10.1、AI 护栏 §10.1、候选排序 §11.1、多语言治理 §11、回写冲突 §12）的指标定义和采集口径保持一致，OCR 专项 §13.4 的 6 条告警规则已在本公告警阈值矩阵全部覆盖。
- 本文列出的全部恢复场景与对应专项设计的失败恢复章节（OCR §10、搜索 §9.5、AI 护栏 §9.4、候选排序 §10.4、多语言治理 §10、回写冲突 §10）的恢复策略保持一致，无冲突。
- 本文运维权限矩阵不与主线内部权限体系冲突，不新增超级运维角色。

## 11. 小结

本文把 `intelligent-applications` 主线的运维治理能力收口为"监控面板 + 告警阈值 + 恢复脚本 + 回滚手册 + 运维权限 + 健康检查 + 验收口径"七件套：

- 统一监控面板按总览层、子系统层、明细层三层组织，覆盖 OCR / 搜索 / AI 任务 / 候选排序 / 多语言治理 / 回写冲突六个子系统的完整指标矩阵。
- 告警按 P1 / P2 / P3 三级分级，68 条告警规则覆盖六个子系统的关键异常信号，并定义静默收敛和渠道升级策略。
- 29 个恢复脚本覆盖六个子系统的典型故障场景，所有脚本受权限管控、有审计留痕、不破坏真相源。
- 回滚手册按 L1 / L2 / L3 三级分级，覆盖搜索双代回退、OCR 引擎路由回滚、回写批量失效和 AI 结果批量标记四类关键回滚场景。
- 运维权限按观察者、操作者、管理员三级矩阵定义，敏感操作强制双人复核。
- 全局健康检查提供 `liveness` 和 `readiness` 两级探测面，聚合状态能正确反映主线可用性。

该口径与 `intelligent-applications` 主线既有 `Detailed Design` 和前六份专项设计保持一致，为本模块全部子系统提供统一运维入口。

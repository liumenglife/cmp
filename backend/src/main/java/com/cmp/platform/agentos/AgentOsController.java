package com.cmp.platform.agentos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AgentOsController {

    private final AgentOsService service;

    AgentOsController(AgentOsService service) {
        this.service = service;
    }

    @PostMapping("/api/agent-os/tasks")
    ResponseEntity<Map<String, Object>> createTask(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> request) {
        AgentOsService.Outcome outcome = service.createTask(idempotencyKey, request);
        return ResponseEntity.status(outcome.status()).body(outcome.body());
    }

    @GetMapping("/api/agent-os/runs/{runId}")
    Map<String, Object> run(@PathVariable String runId) {
        return service.run(runId);
    }

    @PostMapping("/api/agent-os/runs/{runId}/cancel")
    Map<String, Object> cancelRun(@PathVariable String runId, @RequestBody Map<String, Object> request) {
        return service.cancelRun(runId, request);
    }

    @GetMapping("/api/agent-os/tasks/{taskId}/result")
    Map<String, Object> taskResult(@PathVariable String taskId) {
        return service.taskResult(taskId);
    }

    @GetMapping("/api/agent-os/results/{resultId}")
    Map<String, Object> result(@PathVariable String resultId) {
        return service.result(resultId);
    }

    @GetMapping("/api/agent-os/runs/{runId}/audit-view")
    Map<String, Object> auditView(@PathVariable String runId) {
        return service.auditView(runId);
    }

    @PostMapping("/api/agent-os/environment-events")
    ResponseEntity<Map<String, Object>> createEnvironmentEvent(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> request) {
        AgentOsService.Outcome outcome = service.createEnvironmentEvent(idempotencyKey, request);
        return ResponseEntity.status(outcome.status()).body(outcome.body());
    }

    @GetMapping("/api/agent-os/environment-events/{eventId}")
    Map<String, Object> environmentEvent(@PathVariable String eventId) {
        return service.environmentEvent(eventId);
    }

    @GetMapping("/api/agent-os/internal/tools")
    Map<String, Object> tools(@RequestParam(value = "capability_code", required = false) String capabilityCode) {
        return service.tools(capabilityCode);
    }

    @GetMapping("/api/agent-os/internal/tools/{toolName}/schema")
    Map<String, Object> toolSchema(@PathVariable String toolName, @RequestParam("run_id") String runId) {
        return service.toolSchema(runId, toolName);
    }

    @PostMapping("/api/agent-os/runs/{runId}/tools/{toolName}/invoke")
    Map<String, Object> invokeTool(@PathVariable String runId, @PathVariable String toolName, @RequestBody Map<String, Object> request) {
        return service.invokeTool(runId, toolName, request);
    }

    @GetMapping("/api/agent-os/runs/{runId}/tool-pair-check")
    Map<String, Object> toolPairCheck(@PathVariable String runId) {
        return service.toolPairCheck(runId);
    }

    @PostMapping("/api/agent-os/runs/{runId}/verification-reports")
    Map<String, Object> verificationReport(@PathVariable String runId, @RequestBody Map<String, Object> request) {
        return service.verificationReport(runId, request);
    }
}

@org.springframework.stereotype.Service
class AgentOsService {

    private static final String GENERAL_PERSONA = "GENERAL_BUSINESS_AGENT";
    private static final String PLATFORM_ROOT_VERSION = "platform-root-v1";

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    AgentOsService(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    Outcome createTask(String idempotencyKey, Map<String, Object> request) {
        String requesterId = text(request, "requester_id", "SYSTEM");
        String taskSource = text(request, "task_source", "BUSINESS_MODULE");
        String payloadFingerprint = fingerprint(request);
        Optional<Map<String, Object>> existing = findTaskByIdempotency(taskSource, requesterId, idempotencyKey);
        if (existing.isPresent() && !payloadFingerprint.equals(existing.get().get("payload_fingerprint"))) {
            return new Outcome(HttpStatus.CONFLICT, Map.of("code", "40905", "error", "IDEMPOTENCY_CONFLICT"));
        }
        if (existing.isPresent()) {
            Map<String, Object> body = taskResponse(existing.get().get("task_id").toString());
            body.put("duplicate", true);
            return new Outcome(HttpStatus.OK, body);
        }

        String taskId = "agt-task-" + UUID.randomUUID();
        String runId = "agt-run-" + UUID.randomUUID();
        String traceId = trace(request);
        String personaCode = text(request, "specialized_agent_code", "CONTRACT_REVIEW_AGENT");
        Map<String, Object> inputContext = objectMap(request.get("input_context"));
        Instant now = now();
        jdbcTemplate.update("""
                insert into ao_agent_task
                (task_id, task_type, task_source, requester_type, requester_id, persona_code, general_persona_code, task_status,
                 business_module, object_type, object_id, current_run_id, idempotency_key, payload_fingerprint, trace_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, taskId, text(request, "task_type", "RISK_ANALYSIS"), taskSource, text(request, "requester_type", "USER"), requesterId,
                personaCode, GENERAL_PERSONA, "ACCEPTED", text(inputContext, "business_module", "CONTRACT"), text(inputContext, "object_type", "CONTRACT"),
                text(inputContext, "object_id", null), runId, idempotencyKey, payloadFingerprint, traceId, ts(now), ts(now));
        jdbcTemplate.update("""
                insert into ao_agent_run
                (run_id, task_id, session_id, agent_code, general_persona_code, persona_code, run_status, runtime_state, loop_count,
                 trace_id, started_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, runId, taskId, "sess-" + UUID.randomUUID(), personaCode, GENERAL_PERSONA, personaCode, "RUNNING", "INGRESS_PENDING", 0,
                traceId, ts(now), ts(now), ts(now));
        audit(runId, taskId, "RUN_STARTED", "QueryEngine 运行已创建", "SUCCESS", requesterId, traceId, "LOW");

        String scenario = scenario(request);
        if ("MANUAL_START".equals(scenario)) {
            return new Outcome(HttpStatus.ACCEPTED, taskResponse(taskId));
        }

        String resultId = executeMinimalLoop(taskId, runId, request, scenario);
        if (resultId != null) {
            jdbcTemplate.update("update ao_agent_task set final_result_id = ?, updated_at = ? where task_id = ?", resultId, ts(now()), taskId);
        }
        return new Outcome(HttpStatus.ACCEPTED, taskResponse(taskId));
    }

    @Transactional
    Outcome createEnvironmentEvent(String idempotencyKey, Map<String, Object> request) {
        String eventSource = text(request, "event_source", "platform");
        String payloadFingerprint = fingerprint(request);
        Optional<Map<String, Object>> existing = findEventByIdempotency(eventSource, idempotencyKey);
        if (existing.isPresent() && !payloadFingerprint.equals(existing.get().get("payload_fingerprint"))) {
            return new Outcome(HttpStatus.CONFLICT, Map.of("code", "40905", "error", "IDEMPOTENCY_CONFLICT"));
        }
        if (existing.isPresent()) {
            Map<String, Object> body = eventBody(existing.get().get("event_id").toString());
            body.put("duplicate", true);
            return new Outcome(HttpStatus.OK, body);
        }

        String eventId = "env-" + UUID.randomUUID();
        String traceId = trace(request);
        String derivedTaskId = null;
        String processingStatus = bool(request, "agent_processing_required", false) ? "TURNED_INTO_TASK" : "NEW";
        if ("TURNED_INTO_TASK".equals(processingStatus)) {
            derivedTaskId = createDerivedTaskForEvent(request, traceId);
        }
        Instant now = now();
        jdbcTemplate.update("""
                insert into ao_environment_event
                (event_id, event_type, event_source, severity, related_task_id, related_run_id, event_payload_json, event_fingerprint,
                 processing_status, derived_task_id, idempotency_key, payload_fingerprint, trace_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, eventId, text(request, "event_type", null), eventSource, text(request, "severity", "LOW"), text(request, "related_task_id", null),
                text(request, "related_run_id", null), json(request.getOrDefault("event_payload", Map.of())), "envfp-" + payloadFingerprint,
                processingStatus, derivedTaskId, idempotencyKey, payloadFingerprint, traceId, ts(now), ts(now));
        audit(eventId, derivedTaskId, "ENVIRONMENT_EVENT_RECEIVED", "环境事件已进入 Agent OS 输入域", "SUCCESS", eventSource, traceId, text(request, "severity", "LOW"));
        return new Outcome(HttpStatus.ACCEPTED, eventBody(eventId));
    }

    Map<String, Object> run(String runId) {
        return queryRun(runId);
    }

    @Transactional
    Map<String, Object> cancelRun(String runId, Map<String, Object> request) {
        Map<String, Object> run = queryRun(runId);
        String taskId = run.get("task_id").toString();
        String traceId = text(request, "trace_id", run.get("trace_id").toString());
        Instant now = now();
        Map<String, Object> checkpoint = checkpoint("CANCELLED", "EXTERNAL_CANCELLED", traceId, ((Number) run.get("loop_count")).intValue());
        jdbcTemplate.update("""
                update ao_agent_run
                set run_status = 'CANCELLED', runtime_state = 'CANCELLED', failure_code = 'EXTERNAL_CANCELLED',
                    failure_reason = '外部取消', latest_checkpoint_summary = ?, finished_at = ?, updated_at = ?
                where run_id = ?
                """, json(checkpoint), ts(now), ts(now), runId);
        jdbcTemplate.update("update ao_agent_task set task_status = 'CANCELLED', updated_at = ? where task_id = ?", ts(now), taskId);
        audit(runId, taskId, "RUN_CANCELLED", "运行被外部取消", "CANCELLED", text(request, "operator_id", "SYSTEM"), traceId, "LOW");
        return queryRun(runId);
    }

    Map<String, Object> taskResult(String taskId) {
        return queryOptional("""
                select result_id from ao_agent_result where task_id = ? order by created_at desc limit 1
                """, (rs, rowNum) -> resultBody(rs.getString("result_id")), taskId)
                .orElseThrow(() -> new IllegalArgumentException("result 不存在: " + taskId));
    }

    Map<String, Object> result(String resultId) {
        return resultBody(resultId);
    }

    Map<String, Object> auditView(String runId) {
        Map<String, Object> run = queryRun(runId);
        List<Map<String, Object>> events = jdbcTemplate.query("""
                select audit_event_id, object_type, object_id, action_type, action_summary, actor_type, actor_id, result_status, trace_id, occurred_at
                from ao_agent_audit_event
                where object_id = ? or parent_object_id = ?
                order by occurred_at, audit_event_id
                """, (rs, rowNum) -> auditBody(rs), runId, runId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("run_id", runId);
        body.put("trace_id", run.get("trace_id"));
        body.put("checkpoint_summary", run.get("latest_checkpoint"));
        body.put("event_list", events);
        return body;
    }

    Map<String, Object> environmentEvent(String eventId) {
        return eventBody(eventId);
    }

    @Transactional
    Map<String, Object> tools(String capabilityCode) {
        seedToolDefinitions();
        List<Map<String, Object>> tools = jdbcTemplate.query("""
                select tool_name, tool_family, capability_code, risk_level, search_hint
                from ao_tool_definition
                where ? is null or capability_code = ?
                order by cache_order_group, tool_name
                """, (rs, rowNum) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tool_name", rs.getString("tool_name"));
            body.put("tool_family", rs.getString("tool_family"));
            body.put("capability_code", rs.getString("capability_code"));
            body.put("risk_level", rs.getString("risk_level"));
            body.put("search_hint", rs.getString("search_hint"));
            return body;
        }, capabilityCode, capabilityCode);
        return Map.of("tool_list", tools);
    }

    @Transactional
    Map<String, Object> toolSchema(String runId, String toolName) {
        seedToolDefinitions();
        Map<String, Object> run = queryRun(runId);
        Map<String, Object> definition = toolDefinition(toolName);
        String snapshotRef = "tool-snap-" + UUID.randomUUID();
        Instant now = now();
        jdbcTemplate.update("""
                insert into ao_tool_definition_snapshot
                (snapshot_ref, run_id, tool_name, schema_snapshot_json, sandbox_policy_ref, offload_policy_ref, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, snapshotRef, runId, toolName, definition.get("schema_json"), definition.get("sandbox_policy_ref"), definition.get("offload_policy_ref"), ts(now));
        jdbcTemplate.update("""
                insert into ao_tool_grant
                (grant_id, run_id, tool_name, snapshot_ref, grant_status, grant_scope_json, denied_reason, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, "tool-grant-" + UUID.randomUUID(), runId, toolName, snapshotRef, "GRANTED",
                json(Map.of("resource_scope", "CURRENT_BUSINESS_OBJECT", "risk_level", definition.get("risk_level"))), null, ts(now));
        audit(snapshotRef, runId, "TOOL_SCHEMA_SNAPSHOT_CREATED", "工具 schema 快照已固化", "SUCCESS", "SYSTEM", run.get("trace_id").toString(), definition.get("risk_level").toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tool_name", toolName);
        body.put("snapshot_ref", snapshotRef);
        body.put("grant_status", "GRANTED");
        body.put("schema_snapshot", parseMap(definition.get("schema_json").toString()));
        return body;
    }

    @Transactional
    Map<String, Object> invokeTool(String runId, String toolName, Map<String, Object> request) {
        seedToolDefinitions();
        Map<String, Object> run = queryRun(runId);
        String promptId = ensurePromptSnapshot(runId);
        toolSchema(runId, toolName);
        Map<String, Object> definition = toolDefinition(toolName);
        String simulate = text(request, "simulate", "NORMAL");
        String traceId = run.get("trace_id").toString();
        String providerCode = "MODEL".equals(definition.get("tool_family")) && "PRIMARY_CIRCUIT_OPEN".equals(simulate) ? "MOCK_FALLBACK_PROVIDER" : "MOCK_PROVIDER";
        String status = toolStatus(definition, simulate, request);
        String failureCode = failureCode(status);
        String invocationId = insertToolInvocation(runId, promptId, definition, providerCode, status, failureCode, simulate);
        String resultId = insertToolResult(runId, invocationId, definition, status, failureCode, simulate);
        if ("MODEL".equals(definition.get("tool_family"))) {
            recordProviderUsage(runId, invocationId, providerCode, "PRIMARY_CIRCUIT_OPEN".equals(simulate) ? "DEGRADED" : "SELECTED", simulate);
        }
        audit(invocationId, runId, "TOOL_INVOKED", "工具调用已进入沙箱执行", status.equals("SUCCEEDED") ? "SUCCESS" : status, "SYSTEM", traceId, definition.get("risk_level").toString());
        audit(resultId, runId, "TOOL_RESULT_RECORDED", "工具结果已归一化并落库", status.equals("SUCCEEDED") ? "SUCCESS" : status, "SYSTEM", traceId, definition.get("risk_level").toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tool_invocation_id", invocationId);
        body.put("tool_result_id", resultId);
        body.put("tool_name", toolName);
        body.put("result_status", status);
        body.put("failure_code", failureCode);
        body.put("provider_code", providerCode);
        body.put("offloaded", "LARGE_OUTPUT".equals(simulate));
        body.put("artifact_ref", "LARGE_OUTPUT".equals(simulate) ? "artifact://tool-output/" + resultId : null);
        return body;
    }

    @Transactional
    Map<String, Object> toolPairCheck(String runId) {
        Map<String, Object> run = queryRun(runId);
        Integer invocationCount = jdbcTemplate.queryForObject("select count(*) from ao_tool_invocation where run_id = ?", Integer.class, runId);
        Integer resultCount = jdbcTemplate.queryForObject("""
                select count(*) from ao_tool_result r
                where r.run_id = ? and exists (
                    select 1 from ao_tool_invocation i where i.tool_invocation_id = r.tool_invocation_id and i.run_id = r.run_id
                )
                """, Integer.class, runId);
        Integer missingResultCount = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select i.tool_invocation_id
                    from ao_tool_invocation i
                    left join ao_tool_result r on r.run_id = i.run_id and r.tool_invocation_id = i.tool_invocation_id
                    where i.run_id = ?
                    group by i.tool_invocation_id
                    having count(r.tool_result_id) = 0
                ) broken
                """, Integer.class, runId);
        Integer duplicateResultCount = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select i.tool_invocation_id
                    from ao_tool_invocation i
                    left join ao_tool_result r on r.run_id = i.run_id and r.tool_invocation_id = i.tool_invocation_id
                    where i.run_id = ?
                    group by i.tool_invocation_id
                    having count(r.tool_result_id) > 1
                ) broken
                """, Integer.class, runId);
        Integer orphanResultCount = jdbcTemplate.queryForObject("""
                select count(*) from ao_tool_result r
                where r.run_id = ? and not exists (
                    select 1 from ao_tool_invocation i where i.run_id = r.run_id and i.tool_invocation_id = r.tool_invocation_id
                )
                """, Integer.class, runId);
        int brokenInvocationCount = value(missingResultCount) + value(duplicateResultCount);
        boolean paired = brokenInvocationCount == 0 && value(orphanResultCount) == 0;
        if (!paired) {
            audit(runId, run.get("task_id").toString(), "TOOL_PAIR_BROKEN", "工具调用与工具结果断对", "FAILED", "SYSTEM", run.get("trace_id").toString(), "HIGH");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("run_id", runId);
        body.put("pair_status", paired ? "PAIRED" : "BROKEN");
        body.put("failure_code", paired ? null : "TOOL_PAIR_BROKEN");
        body.put("invocation_count", invocationCount == null ? 0 : invocationCount);
        body.put("result_count", resultCount == null ? 0 : resultCount);
        body.put("missing_result_count", value(missingResultCount));
        body.put("duplicate_result_count", value(duplicateResultCount));
        body.put("orphan_result_count", value(orphanResultCount));
        body.put("broken_invocation_count", brokenInvocationCount + value(orphanResultCount));
        return body;
    }

    @Transactional
    Map<String, Object> verificationReport(String runId, Map<String, Object> request) {
        Map<String, Object> run = queryRun(runId);
        Map<String, Object> pairCheck = toolPairCheck(runId);
        Map<String, Object> auditCheck = toolAuditPairCheck(runId, run);
        String target = text(request, "target", "agent-os tool sandbox governance");
        String reportId = "verify-" + UUID.randomUUID();
        boolean resultPaired = pairCheck.get("pair_status").equals("PAIRED");
        boolean auditPaired = auditCheck.get("pair_status").equals("PAIRED");
        List<Map<String, Object>> checkItems = List.of(
                Map.of("check_code", "TOOL_RESULT_PAIRING", "status", resultPaired ? "PASSED" : "FAILED", "evidence_ref", "run://" + runId),
                Map.of("check_code", "TOOL_AUDIT_PAIRING", "status", auditPaired ? "PASSED" : "FAILED", "evidence_ref", "run://" + runId));
        List<Map<String, Object>> failureEvidence = resultPaired && auditPaired
                ? List.of(Map.of("evidence_type", "NONE", "summary", "未发现失败证据"))
                : List.of(Map.of("evidence_type", resultPaired ? "TOOL_AUDIT_PAIR_BROKEN" : "TOOL_PAIR_BROKEN",
                        "summary", resultPaired ? "工具调用与结果缺少成对审计" : "工具调用与结果断对"));
        Map<String, Object> baseline = Map.of("p95_latency_ms", 500, "max_tool_timeout_ms", 1000, "regression_scope", "agent-os-task6");
        String conclusion = resultPaired && auditPaired ? "PASSED" : "FAILED";
        String regressionEntry = "regression://agent-os/tool-sandbox/" + reportId;
        jdbcTemplate.update("""
                insert into ao_verification_report
                (verification_report_id, run_id, verification_target, independent_context_sources_json, check_items_json,
                 failure_evidence_json, performance_baseline_json, uncovered_risks_json, conclusion, regression_baseline_entry, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, reportId, runId, target,
                json(List.of("ao_tool_invocation", "ao_tool_result", "ao_agent_audit_event", "ao_provider_usage")),
                json(checkItems), json(failureEvidence), json(baseline), json(List.of()), conclusion, regressionEntry, ts(now()));
        audit(reportId, runId, "VERIFICATION_REPORT_CREATED", "验证报告已生成", conclusion, "VERIFICATION_AGENT", run.get("trace_id").toString(), "MEDIUM");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("verification_report_id", reportId);
        body.put("verification_target", target);
        body.put("independent_context_sources", List.of("ao_tool_invocation", "ao_tool_result", "ao_agent_audit_event", "ao_provider_usage"));
        body.put("check_items", checkItems);
        body.put("failure_evidence", failureEvidence);
        body.put("performance_baseline", baseline);
        body.put("uncovered_risks", List.of());
        body.put("conclusion", conclusion);
        body.put("regression_baseline_entry", regressionEntry);
        return body;
    }

    private String executeMinimalLoop(String taskId, String runId, Map<String, Object> request, String scenario) {
        String traceId = trace(request);
        String promptId = createPromptSnapshot(runId, request);
        jdbcTemplate.update("update ao_agent_run set runtime_state = 'BUDGET_CHECKING', latest_prompt_snapshot_id = ?, updated_at = ? where run_id = ?",
                promptId, ts(now()), runId);

        if ("BUDGET_REJECT".equals(scenario)) {
            return fail(taskId, runId, "PROVIDER_BUDGET_EXCEEDED", "Provider 预算门拒绝模型调用", "PROVIDER_BUDGET_REJECTED", traceId, null);
        }

        if (!"AUDIT_MISSING".equals(scenario)) {
            audit(promptId, runId, "PROMPT_SNAPSHOT_CREATED", "Prompt 快照已生成", "SUCCESS", "SYSTEM", traceId, "LOW");
        }
        createModelInvocation(runId, promptId, scenario);
        if ("MODEL_FAIL".equals(scenario)) {
            return fail(taskId, runId, "MODEL_CALL_FAILED", "模型工具调用失败", "MODEL_CALL_FAILED", traceId, "模型调用失败");
        }

        Map<String, Object> checkpoint = checkpoint("SUCCEEDED", "RESULT_CONTRACT_SATISFIED", traceId, 1);
        if (!"AUDIT_MISSING".equals(scenario)) {
            audit(runId, taskId, "STATE_CHECKPOINT", "第 1 轮状态检查点已写入", "SUCCESS", "SYSTEM", traceId, "MEDIUM");
        }
        if ("NEED_MORE".equals(scenario)) {
            jdbcTemplate.update("""
                    update ao_agent_run
                    set run_status = 'FAILED', runtime_state = 'FAILED', loop_count = 1, latest_checkpoint_summary = ?,
                        failure_code = 'MAX_LOOP_EXCEEDED', failure_reason = '已达到最大循环次数', finished_at = ?, updated_at = ?
                    where run_id = ?
                    """, json(checkpoint("FAILED", "MAX_LOOP_EXCEEDED", traceId, 1)), ts(now()), ts(now()), runId);
            jdbcTemplate.update("update ao_agent_task set task_status = 'FAILED', updated_at = ? where task_id = ?", ts(now()), taskId);
            return createResult(taskId, runId, "FAILED", "SUMMARY", "达到最大循环次数，未形成可发布结果", Map.of("reason", "MAX_LOOP_EXCEEDED"), List.of(), null);
        }
        if ("AUDIT_MISSING".equals(scenario)) {
            return fail(taskId, runId, "AUDIT_REQUIRED", "审计缺失，阻断结果释放", "RESULT_RELEASE_BLOCKED", traceId, "审计缺失，结果不可释放");
        }
        if ("TOOL_SUCCESS".equals(scenario)) {
            invokeTool(runId, "platform.contract.readonly.lookup", Map.of("object_id", "ctr-agent-001"));
        }

        Map<String, Object> inputPayload = objectMap(request.get("input_payload"));
        boolean environmentTriage = "ENVIRONMENT_TRIAGE".equals(text(request, "task_type", null));
        String resultId = createResult(taskId, runId, "SUCCEEDED", "SUMMARY",
                environmentTriage ? "环境事件已完成最小 Agent triage，并形成处理摘要。" : "合同存在中等风险，建议补充违约责任和履约节点说明。",
                environmentTriage
                        ? Map.of("source_event_type", text(inputPayload, "source_event_type", "UNKNOWN"), "triage_status", "OBSERVED_AND_SUMMARIZED")
                        : Map.of("risk_level", "MEDIUM", "summary", "合同风险提示已生成"),
                environmentTriage
                        ? List.of(Map.of("object_type", "ENVIRONMENT_EVENT_TYPE", "object_id", text(inputPayload, "source_event_type", "UNKNOWN")))
                        : List.of(Map.of("object_type", "CONTRACT", "object_id", "ctr-agent-001")),
                "provider-digest-ok");
        jdbcTemplate.update("""
                update ao_agent_run
                set run_status = 'SUCCEEDED', runtime_state = 'SUCCEEDED', loop_count = 1, latest_checkpoint_summary = ?, finished_at = ?, updated_at = ?
                where run_id = ?
                """, json(checkpoint), ts(now()), ts(now()), runId);
        jdbcTemplate.update("update ao_agent_task set task_status = 'SUCCEEDED', final_result_id = ?, updated_at = ? where task_id = ?", resultId, ts(now()), taskId);
        audit(resultId, runId, "RESULT_RELEASED", "结果满足契约并释放给业务模块", "SUCCESS", "SYSTEM", traceId, "MEDIUM");
        return resultId;
    }

    private String fail(String taskId, String runId, String failureCode, String failureReason, String actionType, String traceId, String resultSummary) {
        String resultId = createResult(taskId, runId, "FAILED", "SUMMARY", resultSummary == null ? failureReason : resultSummary,
                Map.of("failure_code", failureCode), List.of(), null);
        jdbcTemplate.update("""
                update ao_agent_run
                set run_status = 'FAILED', runtime_state = 'FAILED', loop_count = 1, latest_checkpoint_summary = ?,
                    failure_code = ?, failure_reason = ?, finished_at = ?, updated_at = ?
                where run_id = ?
                """, json(checkpoint("FAILED", failureCode, traceId, 1)), failureCode, failureReason, ts(now()), ts(now()), runId);
        jdbcTemplate.update("update ao_agent_task set task_status = 'FAILED', final_result_id = ?, updated_at = ? where task_id = ?", resultId, ts(now()), taskId);
        audit(runId, taskId, actionType, failureReason, "FAILED", "SYSTEM", traceId, "MEDIUM");
        return resultId;
    }

    private String createPromptSnapshot(String runId, Map<String, Object> request) {
        String promptId = "prompt-" + UUID.randomUUID();
        Map<String, Object> inputContext = objectMap(request.get("input_context"));
        Map<String, Object> inputPayload = objectMap(request.get("input_payload"));
        Map<String, Object> dynamic = new LinkedHashMap<>();
        dynamic.put("task_brief", text(inputPayload, "question", "合同风险提示"));
        dynamic.put("business_context", inputContext);
        dynamic.put("governance_context", Map.of("max_loop_count", number(request, "max_loop_count", 1)));
        int tokenEstimate = Math.max(64, json(dynamic).length() / 2);
        jdbcTemplate.update("""
                insert into ao_prompt_snapshot
                (prompt_snapshot_id, run_id, snapshot_no, general_persona_code, persona_code, platform_root_version,
                 runtime_framework_version, general_persona_version, persona_patch_version, dynamic_injection_digest,
                 dynamic_injection_summary, trimmed_reason_summary, context_token_estimate, prompt_body_ref,
                 assembly_policy_version, trimming_policy_version, budget_policy_version, cache_prefix_policy_version, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, promptId, runId, 1, GENERAL_PERSONA, text(request, "specialized_agent_code", "CONTRACT_REVIEW_AGENT"), PLATFORM_ROOT_VERSION,
                "runtime-loop-v1", "general-persona-v1", "contract-review-agent-v1", "dyn-" + Math.abs(json(dynamic).hashCode()),
                json(dynamic), "未触发裁剪", tokenEstimate, "prompt-body-ref://" + promptId, "assembly-v1", "trim-v1", "budget-v1", "cache-v1", ts(now()));
        return promptId;
    }

    private void createModelInvocation(String runId, String promptId, String scenario) {
        seedToolDefinitions();
        Map<String, Object> run = queryRun(runId);
        Map<String, Object> definition = toolDefinition("model.generate_text");
        boolean failed = "MODEL_FAIL".equals(scenario);
        if ("TOOL_SUCCESS".equals(scenario)) {
            invokeTool(runId, "model.generate_text", Map.of("capability", "generate_text"));
            return;
        }
        String status = failed ? "FAILED" : "SUCCEEDED";
        String failureCode = failed ? "MODEL_CALL_FAILED" : null;
        String invocationId = insertToolInvocation(runId, promptId, definition, "MOCK_PROVIDER", status, failureCode, scenario);
        String resultId = insertToolResult(runId, invocationId, definition, status, failureCode, scenario);
        recordProviderUsage(runId, invocationId, "MOCK_PROVIDER", failed ? "FAILED" : "SELECTED", scenario);
        audit(invocationId, runId, "TOOL_INVOKED", "模型工具调用已进入沙箱执行", failed ? "FAILED" : "SUCCESS", "SYSTEM",
                run.get("trace_id").toString(), definition.get("risk_level").toString());
        audit(resultId, runId, "TOOL_RESULT_RECORDED", "模型工具结果已归一化并落库", failed ? "FAILED" : "SUCCESS", "SYSTEM",
                run.get("trace_id").toString(), definition.get("risk_level").toString());
    }

    private Map<String, Object> toolAuditPairCheck(String runId, Map<String, Object> run) {
        Integer invocationAuditBroken = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select i.tool_invocation_id
                    from ao_tool_invocation i
                    left join ao_agent_audit_event a on a.parent_object_id = i.run_id
                        and a.object_id = i.tool_invocation_id
                        and a.action_type = 'TOOL_INVOKED'
                    where i.run_id = ?
                    group by i.tool_invocation_id
                    having count(a.audit_event_id) <> 1
                ) broken
                """, Integer.class, runId);
        Integer resultAuditBroken = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select r.tool_result_id
                    from ao_tool_result r
                    left join ao_agent_audit_event a on a.parent_object_id = r.run_id
                        and a.object_id = r.tool_result_id
                        and a.action_type = 'TOOL_RESULT_RECORDED'
                    where r.run_id = ?
                    group by r.tool_result_id
                    having count(a.audit_event_id) <> 1
                ) broken
                """, Integer.class, runId);
        int brokenCount = value(invocationAuditBroken) + value(resultAuditBroken);
        if (brokenCount > 0) {
            audit(runId, run.get("task_id").toString(), "TOOL_AUDIT_PAIR_BROKEN", "工具调用与结果缺少成对审计", "FAILED", "SYSTEM", run.get("trace_id").toString(), "HIGH");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pair_status", brokenCount == 0 ? "PAIRED" : "BROKEN");
        body.put("broken_audit_count", brokenCount);
        return body;
    }

    private void seedToolDefinitions() {
        if (Boolean.TRUE.equals(jdbcTemplate.queryForObject("select count(*) > 0 from ao_tool_definition", Boolean.class))) {
            return;
        }
        insertToolDefinition("platform.contract.readonly.lookup", "INTERNAL_SERVICE", "CONTRACT_READ", "L1_READONLY", "READONLY",
                "读取当前授权合同摘要", Map.of("input_schema", Map.of("object_id", Map.of("type", "string"))), "sandbox-readonly-v1", "offload-large-v1");
        insertToolDefinition("platform.contract.guarded.writeback", "INTERNAL_SERVICE", "CONTRACT_WRITE", "L3_GUARDED_WRITE", "CONTROLLED_WRITE",
                "受控写入合同 AI 结果", Map.of("input_schema", Map.of("object_id", Map.of("type", "string"))), "sandbox-write-guarded-v1", "offload-large-v1");
        insertToolDefinition("model.generate_text", "MODEL", "generate_text", "L2_CONTROLLED_COMPUTE", "CONTROLLED_COMPUTE",
                "模型文本生成能力", Map.of("input_schema", Map.of("capability", Map.of("type", "string"))), "sandbox-model-v1", "offload-provider-v1");
    }

    private void insertToolDefinition(String name, String family, String capability, String risk, String sideEffect, String hint,
                                      Map<String, Object> schema, String sandboxPolicy, String offloadPolicy) {
        Instant now = now();
        jdbcTemplate.update("""
                insert into ao_tool_definition
                (tool_name, tool_family, capability_code, risk_level, side_effect_level, search_hint, schema_json,
                 definition_stability, cache_order_group, sandbox_policy_ref, offload_policy_ref, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, name, family, capability, risk, sideEffect, hint, json(schema), "BUILT_IN_STABLE", family, sandboxPolicy, offloadPolicy, ts(now), ts(now));
    }

    private Map<String, Object> toolDefinition(String toolName) {
        return queryOptional("""
                select tool_name, tool_family, capability_code, risk_level, side_effect_level, schema_json, sandbox_policy_ref, offload_policy_ref
                from ao_tool_definition where tool_name = ?
                """, (rs, rowNum) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tool_name", rs.getString("tool_name"));
            body.put("tool_family", rs.getString("tool_family"));
            body.put("capability_code", rs.getString("capability_code"));
            body.put("risk_level", rs.getString("risk_level"));
            body.put("side_effect_level", rs.getString("side_effect_level"));
            body.put("schema_json", rs.getString("schema_json"));
            body.put("sandbox_policy_ref", rs.getString("sandbox_policy_ref"));
            body.put("offload_policy_ref", rs.getString("offload_policy_ref"));
            return body;
        }, toolName).orElseThrow(() -> new IllegalArgumentException("tool 不存在: " + toolName));
    }

    private String ensurePromptSnapshot(String runId) {
        return queryOptional("select latest_prompt_snapshot_id from ao_agent_run where run_id = ?", (rs, rowNum) -> rs.getString("latest_prompt_snapshot_id"), runId)
                .filter(value -> value != null && !value.isBlank())
                .orElseGet(() -> {
                    String promptId = "prompt-" + UUID.randomUUID();
                    Instant now = now();
                    jdbcTemplate.update("""
                            insert into ao_prompt_snapshot
                            (prompt_snapshot_id, run_id, snapshot_no, general_persona_code, persona_code, platform_root_version,
                             runtime_framework_version, general_persona_version, persona_patch_version, dynamic_injection_digest,
                             dynamic_injection_summary, trimmed_reason_summary, context_token_estimate, prompt_body_ref,
                             assembly_policy_version, trimming_policy_version, budget_policy_version, cache_prefix_policy_version, created_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, promptId, runId, 1, GENERAL_PERSONA, "CONTRACT_REVIEW_AGENT", PLATFORM_ROOT_VERSION,
                            "runtime-loop-v1", "general-persona-v1", "contract-review-agent-v1", "dyn-manual",
                            json(Map.of("task_brief", "manual tool invocation")), "未触发裁剪", 64, "prompt-body-ref://" + promptId,
                            "assembly-v1", "trim-v1", "budget-v1", "cache-v1", ts(now));
                    jdbcTemplate.update("update ao_agent_run set latest_prompt_snapshot_id = ?, updated_at = ? where run_id = ?", promptId, ts(now), runId);
                    return promptId;
                });
    }

    private String toolStatus(Map<String, Object> definition, String simulate, Map<String, Object> request) {
        if ("TIMEOUT".equals(simulate)) {
            return "TIMED_OUT";
        }
        if ("FAILURE".equals(simulate)) {
            return "FAILED";
        }
        if (definition.get("risk_level").toString().startsWith("L3") || bool(request, "bypass_sandbox", false)) {
            return "REJECTED";
        }
        return "SUCCEEDED";
    }

    private String failureCode(String status) {
        return switch (status) {
            case "FAILED" -> "RUNTIME_FAILURE";
            case "TIMED_OUT" -> "TOOL_TIMEOUT";
            case "REJECTED" -> "SANDBOX_REJECTED";
            default -> null;
        };
    }

    private String insertToolInvocation(String runId, String promptId, Map<String, Object> definition, String providerCode, String status, String failureCode, String simulate) {
        String invocationId = "tool-" + UUID.randomUUID();
        Instant now = now();
        jdbcTemplate.update("""
                insert into ao_tool_invocation
                (tool_invocation_id, run_id, prompt_snapshot_id, tool_type, tool_name, provider_code, invocation_status,
                 input_digest, output_digest, output_artifact_ref, output_truncated_reason, latency_ms, token_in, token_out,
                 error_code, error_message_summary, retry_no, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, invocationId, runId, promptId, definition.get("tool_family"), definition.get("tool_name"), providerCode, status,
                "input-" + invocationId, status.equals("SUCCEEDED") ? "output-" + invocationId : null,
                "LARGE_OUTPUT".equals(simulate) ? "artifact://tool-output/" + invocationId : null,
                "LARGE_OUTPUT".equals(simulate) ? "OUTPUT_OFFLOADED" : null,
                "TIMED_OUT".equals(status) ? 1001 : 25, "MODEL".equals(definition.get("tool_family")) ? 180 : 0,
                "MODEL".equals(definition.get("tool_family")) && status.equals("SUCCEEDED") ? 80 : 0,
                failureCode, failureCode == null ? null : "工具执行未成功", 0, ts(now), ts(now));
        return invocationId;
    }

    private String insertToolResult(String runId, String invocationId, Map<String, Object> definition, String status, String failureCode, String simulate) {
        String resultId = "tool-result-" + UUID.randomUUID();
        jdbcTemplate.update("""
                insert into ao_tool_result
                (tool_result_id, tool_invocation_id, run_id, tool_name, result_status, result_class, output_summary,
                 output_artifact_ref, failure_code, failure_class, retryable, resource_usage_json, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, resultId, invocationId, runId, definition.get("tool_name"), status,
                "MODEL".equals(definition.get("tool_family")) ? "STRUCTURED_EXTRACTION" : "READ_OBSERVATION",
                "LARGE_OUTPUT".equals(simulate) ? "工具输出已卸载为摘要和引用" : status + " observation",
                "LARGE_OUTPUT".equals(simulate) ? "artifact://tool-output/" + resultId : null,
                failureCode, failureCode == null ? null : failureCode, !"REJECTED".equals(status),
                json(Map.of("latency_ms", "TIMED_OUT".equals(status) ? 1001 : 25)), ts(now()));
        return resultId;
    }

    private void recordProviderUsage(String runId, String invocationId, String providerCode, String routeStatus, String simulate) {
        jdbcTemplate.update("""
                insert into ao_provider_usage
                (provider_usage_id, run_id, tool_invocation_id, capability_code, provider_code, model_code, route_status,
                 quota_status, rate_status, circuit_status, degrade_reason, token_in, token_out, estimated_cost, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "provider-use-" + UUID.randomUUID(), runId, invocationId, "generate_text", providerCode,
                "MOCK_FALLBACK_PROVIDER".equals(providerCode) ? "mock-low-cost" : "mock-primary", routeStatus, "PASSED", "PASSED",
                "PRIMARY_CIRCUIT_OPEN".equals(simulate) ? "OPEN" : "CLOSED", "PRIMARY_CIRCUIT_OPEN".equals(simulate) ? "主 Provider 熔断，切换备路由" : null,
                180, 80, 0.010000, ts(now()));
    }

    private String createResult(String taskId, String runId, String status, String type, String summary, Map<String, Object> payload, List<Map<String, Object>> citations, String digest) {
        String resultId = "agt-result-" + UUID.randomUUID();
        Instant now = now();
        jdbcTemplate.update("""
                insert into ao_agent_result
                (result_id, task_id, run_id, result_status, result_type, output_text_summary, output_payload_json, citation_payload_json,
                 human_confirmation_required, writeback_status, provider_result_digest, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, resultId, taskId, runId, status, type, summary, json(payload), json(citations), false, "SKIPPED", digest, ts(now), ts(now));
        return resultId;
    }

    private String createDerivedTaskForEvent(Map<String, Object> eventRequest, String traceId) {
        String taskId = "agt-task-" + UUID.randomUUID();
        String runId = "agt-run-" + UUID.randomUUID();
        Instant now = now();
        jdbcTemplate.update("""
                insert into ao_agent_task
                (task_id, task_type, task_source, requester_type, requester_id, persona_code, general_persona_code, task_status,
                 business_module, object_type, object_id, current_run_id, trace_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, taskId, "ENVIRONMENT_TRIAGE", "PLATFORM_EVENT", "SYSTEM", text(eventRequest, "event_source", "platform"),
                "OPS_TRIAGE_AGENT", GENERAL_PERSONA, "ACCEPTED", "AGENT_OS", "ENVIRONMENT_EVENT", null, runId, traceId, ts(now), ts(now));
        jdbcTemplate.update("""
                insert into ao_agent_run
                (run_id, task_id, session_id, agent_code, general_persona_code, persona_code, run_status, runtime_state, loop_count,
                 trace_id, started_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, runId, taskId, "sess-" + UUID.randomUUID(), "OPS_TRIAGE_AGENT", GENERAL_PERSONA, "OPS_TRIAGE_AGENT", "PENDING",
                "INGRESS_PENDING", 0, traceId, ts(now), ts(now), ts(now));
        audit(runId, taskId, "RUN_STARTED", "环境事件派生 QueryEngine 运行已创建", "SUCCESS", text(eventRequest, "event_source", "platform"), traceId, text(eventRequest, "severity", "LOW"));

        Map<String, Object> derivedRequest = new LinkedHashMap<>();
        derivedRequest.put("task_type", "ENVIRONMENT_TRIAGE");
        derivedRequest.put("task_source", "PLATFORM_EVENT");
        derivedRequest.put("requester_type", "SYSTEM");
        derivedRequest.put("requester_id", text(eventRequest, "event_source", "platform"));
        derivedRequest.put("specialized_agent_code", "OPS_TRIAGE_AGENT");
        derivedRequest.put("input_context", Map.of("business_module", "AGENT_OS", "object_type", "ENVIRONMENT_EVENT"));
        derivedRequest.put("input_payload", Map.of(
                "question", "请归纳环境事件并给出最小处理摘要",
                "source_event_type", text(eventRequest, "event_type", "UNKNOWN"),
                "event_payload", objectMap(eventRequest.get("event_payload")),
                "scenario", "NORMAL"));
        derivedRequest.put("max_loop_count", 1);
        derivedRequest.put("trace_id", traceId);
        executeMinimalLoop(taskId, runId, derivedRequest, "NORMAL");
        return taskId;
    }

    private Map<String, Object> taskResponse(String taskId) {
        Map<String, Object> task = queryTask(taskId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("task_id", taskId);
        body.put("task_status", task.get("task_status"));
        body.put("run_id", task.get("current_run_id"));
        body.put("result_id", task.get("final_result_id"));
        body.put("trace_id", task.get("trace_id"));
        return body;
    }

    private Map<String, Object> queryTask(String taskId) {
        return queryOptional("""
                select task_id, task_status, current_run_id, final_result_id, trace_id, payload_fingerprint
                from ao_agent_task where task_id = ?
                """, (rs, rowNum) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("task_id", rs.getString("task_id"));
            body.put("task_status", rs.getString("task_status"));
            body.put("current_run_id", rs.getString("current_run_id"));
            body.put("final_result_id", rs.getString("final_result_id"));
            body.put("trace_id", rs.getString("trace_id"));
            body.put("payload_fingerprint", rs.getString("payload_fingerprint"));
            return body;
        }, taskId).orElseThrow(() -> new IllegalArgumentException("task 不存在: " + taskId));
    }

    private Map<String, Object> queryRun(String runId) {
        return queryOptional("""
                select run_id, task_id, run_status, runtime_state, loop_count, latest_checkpoint_summary, failure_code, trace_id
                from ao_agent_run where run_id = ?
                """, (rs, rowNum) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("run_id", rs.getString("run_id"));
            body.put("task_id", rs.getString("task_id"));
            body.put("run_status", rs.getString("run_status"));
            body.put("runtime_state", rs.getString("runtime_state"));
            body.put("loop_count", rs.getInt("loop_count"));
            body.put("failure_code", rs.getString("failure_code"));
            body.put("trace_id", rs.getString("trace_id"));
            body.put("latest_checkpoint", parseMap(rs.getString("latest_checkpoint_summary")));
            return body;
        }, runId).orElseThrow(() -> new IllegalArgumentException("run 不存在: " + runId));
    }

    private Map<String, Object> resultBody(String resultId) {
        return queryOptional("""
                select result_id, task_id, run_id, result_status, result_type, output_text_summary, output_payload_json, citation_payload_json
                from ao_agent_result where result_id = ?
                """, (rs, rowNum) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("result_id", rs.getString("result_id"));
            body.put("task_id", rs.getString("task_id"));
            body.put("run_id", rs.getString("run_id"));
            body.put("result_status", rs.getString("result_status"));
            body.put("result_type", rs.getString("result_type"));
            body.put("output_text", rs.getString("output_text_summary"));
            body.put("output_payload", parseMap(rs.getString("output_payload_json")));
            body.put("citation_list", parseList(rs.getString("citation_payload_json")));
            return body;
        }, resultId).orElseThrow(() -> new IllegalArgumentException("result 不存在: " + resultId));
    }

    private Map<String, Object> eventBody(String eventId) {
        return queryOptional("""
                select event_id, event_type, event_source, severity, event_payload_json, processing_status, derived_task_id, trace_id
                from ao_environment_event where event_id = ?
                """, (rs, rowNum) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("event_id", rs.getString("event_id"));
            body.put("event_type", rs.getString("event_type"));
            body.put("event_source", rs.getString("event_source"));
            body.put("severity", rs.getString("severity"));
            body.put("event_payload", parseMap(rs.getString("event_payload_json")));
            body.put("processing_status", rs.getString("processing_status"));
            body.put("derived_task_id", rs.getString("derived_task_id"));
            body.put("trace_id", rs.getString("trace_id"));
            return body;
        }, eventId).orElseThrow(() -> new IllegalArgumentException("event 不存在: " + eventId));
    }

    private Optional<Map<String, Object>> findTaskByIdempotency(String taskSource, String requesterId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return queryOptional("""
                select task_id, payload_fingerprint from ao_agent_task
                where task_source = ? and requester_id = ? and idempotency_key = ?
                """, (rs, rowNum) -> Map.of("task_id", rs.getString("task_id"), "payload_fingerprint", rs.getString("payload_fingerprint")),
                taskSource, requesterId, idempotencyKey);
    }

    private Optional<Map<String, Object>> findEventByIdempotency(String eventSource, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return queryOptional("""
                select event_id, payload_fingerprint from ao_environment_event
                where event_source = ? and idempotency_key = ?
                """, (rs, rowNum) -> Map.of("event_id", rs.getString("event_id"), "payload_fingerprint", rs.getString("payload_fingerprint")),
                eventSource, idempotencyKey);
    }

    private Map<String, Object> auditBody(ResultSet rs) throws SQLException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("audit_event_id", rs.getString("audit_event_id"));
        body.put("object_type", rs.getString("object_type"));
        body.put("object_id", rs.getString("object_id"));
        body.put("action_type", rs.getString("action_type"));
        body.put("action_summary", rs.getString("action_summary"));
        body.put("actor_type", rs.getString("actor_type"));
        body.put("actor_id", rs.getString("actor_id"));
        body.put("result_status", rs.getString("result_status"));
        body.put("trace_id", rs.getString("trace_id"));
        body.put("occurred_at", rs.getTimestamp("occurred_at").toInstant().toString());
        return body;
    }

    private void audit(String objectId, String parentObjectId, String actionType, String summary, String result, String actorId, String traceId, String riskLevel) {
        jdbcTemplate.update("""
                insert into ao_agent_audit_event
                (audit_event_id, object_type, object_id, parent_object_type, parent_object_id, action_type, action_summary,
                 actor_type, actor_id, result_status, trace_id, risk_level, payload_digest, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "ao-aud-" + UUID.randomUUID(), "AGENT_RUN", objectId, parentObjectId == null ? null : "AGENT_TASK", parentObjectId,
                actionType, summary, "SYSTEM", actorId == null ? "SYSTEM" : actorId, result, traceId, riskLevel, "aud-" + Math.abs(summary.hashCode()), ts(now()));
    }

    private Map<String, Object> checkpoint(String runtimeState, String terminationReason, String traceId, int loopCount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runtime_state", runtimeState);
        body.put("termination_reason", terminationReason);
        body.put("trace_id", traceId);
        body.put("loop_count", loopCount);
        body.put("action_summary", "MODEL -> FINAL_RESULT");
        body.put("observation_summary", "合同风险提示观察已归一化");
        body.put("risk_level", "MEDIUM");
        body.put("cost_summary", Map.of("token_in", 180, "token_out", 80));
        return body;
    }

    private String scenario(Map<String, Object> request) {
        return text(objectMap(request.get("input_payload")), "scenario", "NORMAL");
    }

    private String trace(Map<String, Object> request) {
        return text(request, "trace_id", "trace-" + UUID.randomUUID());
    }

    private String text(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : value.toString();
    }

    private int number(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String fingerprint(Map<String, Object> request) {
        return "fp-" + Math.abs(json(request).hashCode());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 序列化失败", e);
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 解析失败", e);
        }
    }

    private List<Map<String, Object>> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 解析失败", e);
        }
    }

    private <T> Optional<T> queryOptional(String sql, org.springframework.jdbc.core.RowMapper<T> rowMapper, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, args));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private Instant now() {
        return Instant.now();
    }

    private Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    record Outcome(HttpStatus status, Map<String, Object> body) {
    }
}

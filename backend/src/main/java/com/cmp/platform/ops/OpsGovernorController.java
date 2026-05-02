package com.cmp.platform.ops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OpsGovernorController {

    private static final List<String> SUBSYSTEMS = List.of("OCR", "SEARCH", "AI_GUARDRAIL", "CANDIDATE_RANKING", "LANGUAGE_GOVERNANCE", "RESULT_WRITEBACK");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    OpsGovernorController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @GetMapping("/api/intelligent-applications/ops/monitoring/overview")
    Map<String, Object> monitoringOverview() {
        List<Map<String, Object>> subsystems = new ArrayList<>();
        for (String subsystem : SUBSYSTEMS) {
            subsystems.add(Map.of("subsystem", subsystem, "status", subsystemStatus(subsystem)));
        }
        return orderedMap(
                "panel_layer", "OVERVIEW",
                "status", aggregateStatus(subsystems),
                "active_alert_count", activeAlertCount(),
                "subsystems", subsystems,
                "key_throughput", keyThroughput(),
                "metric_matrix", metricMatrix()
        );
    }

    @GetMapping("/api/intelligent-applications/ops/monitoring/subsystems/{subsystem}")
    Map<String, Object> subsystemDashboard(@PathVariable String subsystem) {
        String normalized = normalizeSubsystem(subsystem);
        return orderedMap(
                "panel_layer", "SUBSYSTEM",
                "subsystem", normalized,
                "status", subsystemStatus(normalized),
                "metrics", subsystemMetrics(normalized),
                "recovery_scripts", recoveryScriptsFor(normalized)
        );
    }

    @GetMapping("/api/intelligent-applications/ops/monitoring/drill-down")
    Map<String, Object> drillDown(@RequestParam(required = false) String trace_id,
                                  @RequestParam(required = false) String task_id,
                                  @RequestParam(required = false) String contract_id,
                                  @RequestParam(required = false) String document_version_id) {
        String traceId = firstNonBlank(trace_id, task_id, contract_id, document_version_id, "UNKNOWN");
        return orderedMap(
                "panel_layer", "DRILL_DOWN",
                "trace_id", traceId,
                "related_events", relatedEvents(trace_id, task_id, contract_id, document_version_id)
        );
    }

    @PostMapping("/api/intelligent-applications/ops/alerts/evaluate")
    Map<String, Object> evaluateAlert(@RequestBody Map<String, Object> request) {
        String subsystem = text(request, "subsystem", "UNKNOWN");
        String metric = text(request, "metric_name", "UNKNOWN");
        double value = number(request.get("current_value"));
        AlertRule rule = alertRule(subsystem, metric, value, request);
        String severity = rule == null ? "P3" : rule.severity();
        boolean maintenance = activeMaintenanceWindow(subsystem);
        int notificationCount = recentNotificationCount(subsystem, metric);
        boolean suppressed = notificationCount > 0;
        List<String> channels = maintenance ? List.of() : notificationChannels(severity);
        return orderedMap(
                "subsystem", subsystem,
                "metric_name", metric,
                "severity", severity,
                "current_value", value,
                "suppressed", suppressed,
                "suppressed_count", notificationCount,
                "maintenance_window_active", maintenance,
                "notification_channels", channels,
                "alert_rule", rule == null ? alertRulePayload(new AlertRule(subsystem, metric, "P3", value, "观测信号", "下一工作周期确认")) : alertRulePayload(rule),
                "trace_id", text(request, "trace_id", null)
        );
    }

    @GetMapping("/api/intelligent-applications/ops/recovery/scripts")
    Map<String, Object> recoveryScripts() {
        return Map.of("scripts", scripts());
    }

    @PostMapping("/api/intelligent-applications/ops/recovery/scripts/{scriptName}/execute")
    ResponseEntity<Map<String, Object>> executeRecoveryScript(@PathVariable String scriptName,
                                                              @RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        String operatorId = text(body, "operator_id", "UNKNOWN");
        String operatorRole = opsRoleFor(operatorId);
        ScriptDefinition script = script(scriptName);
        if (script == null) {
            insertRecoveryLog(scriptName, operatorId, roleOrUnknown(operatorRole), body, "FAILED", "UNKNOWN", "UNKNOWN", Map.of("result_status", "FAILED"), "OPS_RECOVERY_SCRIPT_NOT_FOUND");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("OPS_RECOVERY_SCRIPT_NOT_FOUND", "恢复脚本不存在"));
        }
        if (!canExecute(operatorRole, script)) {
            insertRecoveryLog(script, roleOrUnknown(operatorRole), body, "DENIED", Map.of("result_status", "DENIED"), "OPS_OPERATION_PERMISSION_DENIED");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("OPS_OPERATION_PERMISSION_DENIED", "当前运维角色无权执行该操作"));
        }
        String reviewOperatorId = text(body, "review_operator_id", null);
        if (script.sensitive() && isBlank(reviewOperatorId)) {
            insertRecoveryLog(script, operatorRole, body, "REVIEW_REQUIRED", Map.of("result_status", "REVIEW_REQUIRED"), null);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(orderedMap(
                    "script_name", scriptName,
                    "execution_status", "REVIEW_REQUIRED",
                    "required_review", "TWO_PERSON_REVIEW"
            ));
        }
        if (script.sensitive()) {
            if (operatorId.equals(reviewOperatorId)) {
                insertRecoveryLog(script, operatorRole, body, "DENIED", Map.of("result_status", "DENIED"), "OPS_TWO_PERSON_REVIEW_INVALID");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("OPS_TWO_PERSON_REVIEW_INVALID", "复核人不能与操作人相同"));
            }
            String reviewerRole = opsRoleFor(reviewOperatorId);
            if (!("OPS_ADMIN".equals(reviewerRole) || "SUPER_ADMIN".equals(reviewerRole))) {
                insertRecoveryLog(script, operatorRole, body, "DENIED", Map.of("result_status", "DENIED"), "OPS_REVIEWER_PERMISSION_DENIED");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("OPS_REVIEWER_PERMISSION_DENIED", "复核人角色无权复核敏感运维操作"));
            }
        }
        return transactionTemplate.execute(status -> {
            Map<String, Object> result;
            String executionStatus;
            String failureReason = null;
            try {
                result = recoveryResult(script);
                executionStatus = text(result, "result_status", "SUCCEEDED");
            } catch (RuntimeException e) {
                result = orderedMap("result_status", "FAILED", "error", e.getMessage());
                executionStatus = "FAILED";
                failureReason = e.getClass().getSimpleName();
            }
            insertRecoveryLog(script, operatorRole, body, executionStatus, result, failureReason);
            return ResponseEntity.ok(orderedMap(
                    "script_name", scriptName,
                    "execution_status", executionStatus,
                    "target_subsystem", script.subsystem(),
                    "rollback", result,
                    "trace_id", text(body, "trace_id", null)
            ));
        });
    }

    @GetMapping("/api/intelligent-applications/ops/rollback/runbooks")
    Map<String, Object> rollbackRunbooks() {
        return Map.of("runbooks", List.of(
                runbook("search_double_generation_rollback", "L1", "recover_search_alias_rollback"),
                runbook("ocr_engine_route_rollback", "L2", "recover_ocr_engine_circuit_breaker"),
                runbook("writeback_batch_supersede", "L2", "recover_writeback_mark_superseded"),
                runbook("ai_result_batch_supersede", "L2", "recover_ai_source_superseded")
        ));
    }

    @GetMapping("/api/intelligent-applications/ops/permissions/matrix")
    Map<String, Object> permissionMatrix() {
        return orderedMap(
                "roles", orderedMap(
                        "OPS_OBSERVER", orderedMap("can_view_overview", true, "can_execute_recovery", false),
                        "OPS_OPERATOR", orderedMap("can_execute_recovery", true, "max_recovery_impact", "LOW"),
                        "OPS_ADMIN", orderedMap("can_execute_recovery", true, "max_recovery_impact", "HIGH", "requires_two_person_review_for_high_impact", true),
                        "SUPER_ADMIN", orderedMap("can_execute_recovery", true, "max_recovery_impact", "HIGH", "requires_two_person_review_for_high_impact", true)
                ),
                "operations", orderedMap(
                        "can_view_overview", roleMatrix(true, true, true, true),
                        "can_view_subsystem", roleMatrix(true, true, true, true),
                        "can_view_drill_down", roleMatrix("MASKED", true, true, true),
                        "can_ack_alert", roleMatrix(false, true, true, true),
                        "can_silence_alert", roleMatrix(false, true, true, true),
                        "can_execute_low_recovery", roleMatrix(false, true, true, true),
                        "can_execute_medium_recovery", roleMatrix(false, false, true, true),
                        "can_execute_high_recovery", roleMatrix(false, false, "TWO_PERSON_REVIEW", true),
                        "can_execute_l1_rollback", roleMatrix(false, false, "APPROVAL_REQUIRED", "APPROVAL_REQUIRED"),
                        "can_execute_l2_rollback", roleMatrix(false, false, true, true),
                        "can_execute_l3_rollback", roleMatrix(false, false, true, true),
                        "can_configure_alert_rules", roleMatrix(false, false, true, true),
                        "can_configure_dashboard", roleMatrix(false, false, true, true),
                        "can_manage_ops_permissions", roleMatrix(false, false, false, true),
                        "can_view_audit_logs", roleMatrix(true, true, true, true),
                        "can_export_ops_data", roleMatrix(false, true, true, true)
                )
        );
    }

    @GetMapping("/health/liveness")
    Map<String, Object> liveness() {
        return orderedMap("status", "READY", "checks", List.of(Map.of("name", "PROCESS", "status", "READY")));
    }

    @GetMapping("/health/readiness")
    ResponseEntity<Map<String, Object>> readiness() {
        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(check("MYSQL", mysqlReady() ? "READY" : "UNAVAILABLE", true, false));
        checks.add(dependencyCheck("REDIS", false));
        checks.add(dependencyCheck("SEARCH_ENGINE", false));
        checks.add(dependencyCheck("OCR_ENGINE", false));
        checks.add(dependencyCheck("AGENT_OS", false));
        checks.add(dependencyCheck("TASK_EXECUTOR", true));
        checks.add(dependencyCheck("DEAD_LETTER_QUEUES", false));
        String status = aggregateReadiness(checks);
        HttpStatus httpStatus = "UNAVAILABLE".equals(status) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        boolean stale = checks.stream().anyMatch(check -> Boolean.TRUE.equals(check.get("stale")));
        return ResponseEntity.status(httpStatus).body(orderedMap("status", status, "checks", checks, "stale", stale));
    }

    private String subsystemStatus(String subsystem) {
        return switch (subsystem) {
            case "OCR" -> count("ia_ocr_job", "job_status = 'FAILED'") > 0 ? "DEGRADED" : "READY";
            case "SEARCH" -> count("ia_search_rebuild_job", "rebuild_status = 'FAILED'") > 0 ? "DEGRADED" : "READY";
            case "AI_GUARDRAIL" -> count("ia_ai_application_job", "job_status = 'FAILED'") > 0 ? "DEGRADED" : "READY";
            case "LANGUAGE_GOVERNANCE" -> count("ia_i18n_context", "i18n_status = 'FAILED'") > 0 ? "DEGRADED" : "READY";
            case "RESULT_WRITEBACK" -> count("ia_writeback_dead_letter", "dead_letter_status = 'OPEN'") > 0 ? "DEGRADED" : "READY";
            default -> "READY";
        };
    }

    private String aggregateStatus(List<Map<String, Object>> subsystems) {
        return subsystems.stream().anyMatch(item -> "DEGRADED".equals(item.get("status"))) ? "DEGRADED" : "READY";
    }

    private int activeAlertCount() {
        int count = 0;
        if (count("ia_ocr_job", "job_status = 'FAILED'") > 0) count++;
        if (count("ia_search_rebuild_job", "rebuild_status = 'FAILED'") > 0) count++;
        if (count("ia_ai_application_job", "job_status = 'FAILED'") > 0) count++;
        if (count("ia_i18n_context", "i18n_status = 'FAILED'") > 0) count++;
        if (count("ia_writeback_dead_letter", "dead_letter_status = 'OPEN'") > 0) count++;
        return count;
    }

    private Map<String, Object> subsystemMetrics(String subsystem) {
        return switch (subsystem) {
            case "OCR" -> flatten((Map<String, Map<String, Object>>) metricMatrix().get("OCR"));
            case "SEARCH" -> flatten((Map<String, Map<String, Object>>) metricMatrix().get("SEARCH"));
            case "AI_GUARDRAIL" -> flatten((Map<String, Map<String, Object>>) metricMatrix().get("AI_GUARDRAIL"));
            case "LANGUAGE_GOVERNANCE" -> flatten((Map<String, Map<String, Object>>) metricMatrix().get("LANGUAGE_GOVERNANCE"));
            case "RESULT_WRITEBACK" -> flatten((Map<String, Map<String, Object>>) metricMatrix().get("RESULT_WRITEBACK"));
            default -> flatten((Map<String, Map<String, Object>>) metricMatrix().get("CANDIDATE_RANKING"));
        };
    }

    private List<String> recoveryScriptsFor(String subsystem) {
        return scripts().stream().filter(script -> subsystem.equals(script.get("target_subsystem"))).map(script -> script.get("script_name").toString()).toList();
    }

    private List<Map<String, Object>> relatedEvents(String traceId, String taskId, String contractId, String documentVersionId) {
        List<Map<String, Object>> events = new ArrayList<>();
        addRelated(events, "OCR", "ia_ocr_job", "ocr_job_id", "job_status", traceId, taskId, contractId, documentVersionId);
        addRelated(events, "SEARCH", "ia_search_rebuild_job", "rebuild_job_id", "rebuild_status", traceId, taskId, null, null);
        addRelated(events, "AI_GUARDRAIL", "ia_ai_application_job", "ai_application_job_id", "job_status", traceId, taskId, contractId, documentVersionId);
        addRelated(events, "LANGUAGE_GOVERNANCE", "ia_i18n_context", "i18n_context_id", "i18n_status", traceId, taskId, null, null);
        addRelated(events, "RESULT_WRITEBACK", "ia_writeback_record", "writeback_record_id", "writeback_status", traceId, taskId, null, null);
        return events;
    }

    private List<String> notificationChannels(String severity) {
        return switch (severity) {
            case "P1" -> List.of("OPS_WORKBENCH", "IM_GROUP", "SMS", "ON_CALL_IM");
            case "P2" -> List.of("OPS_WORKBENCH", "IM_GROUP", "EMAIL");
            default -> List.of("OPS_WORKBENCH");
        };
    }

    private List<Map<String, Object>> scripts() {
        return scriptDefinitions().stream().map(script -> orderedMap(
                "script_name", script.name(),
                "target_subsystem", script.subsystem(),
                "impact_level", script.impact(),
                "sensitive", script.sensitive()
        )).toList();
    }

    private List<ScriptDefinition> scriptDefinitions() {
        return List.of(
                new ScriptDefinition("recover_ocr_dead_letter", "OCR", "MEDIUM", false),
                new ScriptDefinition("recover_ocr_result_write", "OCR", "MEDIUM", false),
                new ScriptDefinition("recover_ocr_dc_binding", "OCR", "LOW", false),
                new ScriptDefinition("recover_ocr_version_rebuild", "OCR", "HIGH", true),
                new ScriptDefinition("recover_ocr_engine_circuit_breaker", "OCR", "MEDIUM", false),
                new ScriptDefinition("recover_ocr_to_search_index", "OCR", "LOW", false),
                new ScriptDefinition("recover_search_backfill", "SEARCH", "MEDIUM", false),
                new ScriptDefinition("recover_search_index_build", "SEARCH", "MEDIUM", false),
                new ScriptDefinition("recover_search_full_rebuild", "SEARCH", "HIGH", true),
                new ScriptDefinition("recover_search_alias_rollback", "SEARCH", "HIGH", true),
                new ScriptDefinition("recover_search_snapshot_cleanup", "SEARCH", "LOW", false),
                new ScriptDefinition("recover_search_cache_invalidate", "SEARCH", "LOW", false),
                new ScriptDefinition("recover_ai_job_dead_letter", "AI_GUARDRAIL", "MEDIUM", false),
                new ScriptDefinition("recover_ai_context_assembly", "AI_GUARDRAIL", "MEDIUM", false),
                new ScriptDefinition("recover_ai_guardrail_replay", "AI_GUARDRAIL", "MEDIUM", false),
                new ScriptDefinition("recover_ai_human_confirmation_timeout", "AI_GUARDRAIL", "LOW", false),
                new ScriptDefinition("recover_protected_result_cleanup", "AI_GUARDRAIL", "LOW", false),
                new ScriptDefinition("recover_ai_source_superseded", "AI_GUARDRAIL", "MEDIUM", true),
                new ScriptDefinition("recover_ranking_retry", "CANDIDATE_RANKING", "MEDIUM", false),
                new ScriptDefinition("recover_quality_evaluation_retry", "CANDIDATE_RANKING", "MEDIUM", false),
                new ScriptDefinition("recover_candidate_snapshot_cleanup", "CANDIDATE_RANKING", "LOW", false),
                new ScriptDefinition("recover_candidate_source_superseded", "CANDIDATE_RANKING", "MEDIUM", true),
                new ScriptDefinition("recover_terminology_profile_load", "LANGUAGE_GOVERNANCE", "LOW", false),
                new ScriptDefinition("recover_i18n_context_failed", "LANGUAGE_GOVERNANCE", "MEDIUM", false),
                new ScriptDefinition("recover_terminology_cache_refresh", "LANGUAGE_GOVERNANCE", "LOW", false),
                new ScriptDefinition("recover_writeback_dead_letter", "RESULT_WRITEBACK", "MEDIUM", false),
                new ScriptDefinition("recover_writeback_conflict_resolve", "RESULT_WRITEBACK", "MEDIUM", false),
                new ScriptDefinition("recover_writeback_pending_timeout", "RESULT_WRITEBACK", "MEDIUM", false),
                new ScriptDefinition("recover_writeback_consistency", "RESULT_WRITEBACK", "HIGH", true),
                new ScriptDefinition("recover_writeback_mark_superseded", "RESULT_WRITEBACK", "LOW", true)
        );
    }

    private ScriptDefinition script(String scriptName) {
        return scriptDefinitions().stream().filter(script -> script.name().equals(scriptName)).findFirst().orElse(null);
    }

    private boolean canExecute(String role, ScriptDefinition script) {
        if (role == null || "OPS_OBSERVER".equals(role)) {
            return false;
        }
        if ("OPS_OPERATOR".equals(role)) {
            return "LOW".equals(script.impact());
        }
        return "OPS_ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }

    private Map<String, Object> recoveryResult(ScriptDefinition script) {
        int affected = 0;
        switch (script.name()) {
            case "recover_ocr_dead_letter" -> affected = jdbcTemplate.update("update ia_ocr_job set job_status = 'QUEUED', current_attempt_no = 0, failure_code = null, failure_reason = null, updated_at = current_timestamp where job_status = 'FAILED' and failure_code = 'DEAD_LETTER'");
            case "recover_search_index_build", "recover_search_backfill" -> affected = jdbcTemplate.update("update ia_search_rebuild_job set rebuild_status = 'QUEUED', alias_status = 'PENDING', completed_at = null where rebuild_status = 'FAILED'");
            case "recover_ai_job_dead_letter" -> affected = jdbcTemplate.update("update ia_ai_application_job set job_status = 'QUEUED', failure_code = null, failure_reason = null, updated_at = current_timestamp where job_status = 'FAILED'");
            case "recover_protected_result_cleanup" -> affected = jdbcTemplate.update("delete from ia_protected_result_snapshot where expires_at < current_timestamp");
            case "recover_terminology_profile_load", "recover_i18n_context_failed" -> affected = jdbcTemplate.update("update ia_i18n_context set i18n_status = 'READY', downstream_degradation_json = '{}' where i18n_status = 'FAILED'");
            case "recover_writeback_dead_letter" -> {
                affected += jdbcTemplate.update("update ia_writeback_dead_letter set dead_letter_status = 'REQUEUED' where dead_letter_status = 'OPEN'");
                affected += jdbcTemplate.update("update ia_writeback_record set writeback_status = 'PENDING', failure_reason = null, updated_at = current_timestamp where writeback_status = 'FAILED'");
            }
            case "recover_search_alias_rollback" -> {
                affected = jdbcTemplate.update("update ia_search_rebuild_job set alias_status = 'ROLLED_BACK', rebuild_status = 'ROLLED_BACK' where alias_status = 'FAILED' or rebuild_status = 'FAILED'");
                return orderedMap("alias_status", affected > 0 ? "ROLLED_BACK" : "NO_TARGET", "current_generation", 1, "standby_generation", 2, "affected_count", affected, "result_status", affected > 0 ? "SUCCEEDED" : "NO_TARGET");
            }
            default -> {
                return orderedMap("affected_count", 0, "result_status", "NOT_IMPLEMENTED");
            }
        }
        return orderedMap("affected_count", affected, "result_status", affected > 0 ? "SUCCEEDED" : "NO_TARGET");
    }

    private void insertRecoveryLog(ScriptDefinition script, String role, Map<String, Object> request, String status, Map<String, Object> result, String failureReason) {
        insertRecoveryLog(script.name(), text(request, "operator_id", "UNKNOWN"), role, request, status, script.subsystem(), script.impact(), result, failureReason);
    }

    private void insertRecoveryLog(String scriptName, String operatorId, String role, Map<String, Object> request, String status, String subsystem, String impact, Map<String, Object> result, String failureReason) {
        jdbcTemplate.update("""
                insert into ia_recovery_operation_log
                (operation_log_id, script_name, operator_id, operator_role, action_type, execution_status, target_subsystem, impact_level,
                 trace_id, review_operator_id, request_payload_json, result_payload_json, failure_reason, created_at, completed_at)
                values (?, ?, ?, ?, 'RECOVERY_SCRIPT_EXECUTED', ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                """, "recovery-" + UUID.randomUUID(), scriptName, operatorId, role, status, subsystem, impact,
                text(request, "trace_id", null), text(request, "review_operator_id", null), json(request), json(result), failureReason);
    }

    private String roleOrUnknown(String role) {
        return role == null ? "UNKNOWN" : role;
    }

    private Map<String, Object> runbook(String name, String level, String scriptName) {
        return orderedMap("runbook_name", name, "rollback_level", level, "script_name", scriptName, "steps", List.of("确认影响范围", "执行恢复脚本", "观察指标", "记录审计"));
    }

    private String normalizeSubsystem(String subsystem) {
        return "AI".equalsIgnoreCase(subsystem) ? "AI_GUARDRAIL" : subsystem.toUpperCase();
    }

    private int count(String table, String where) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + table + " where " + where, Integer.class);
        return count == null ? 0 : count;
    }

    private boolean activeMaintenanceWindow(String subsystem) {
        Integer active = jdbcTemplate.queryForObject("""
                select count(*) from ia_ops_maintenance_window
                where subsystem = ? and window_level = 'SILENCE' and starts_at <= current_timestamp and ends_at >= current_timestamp
                """, Integer.class, subsystem);
        return active != null && active > 0;
    }

    private int recentNotificationCount(String subsystem, String metric) {
        Timestamp windowStart = Timestamp.from(Instant.now().minus(5, ChronoUnit.MINUTES));
        Integer sent = jdbcTemplate.queryForObject("""
                select count(*) from ia_ops_alert_notification
                where subsystem = ? and metric_name = ? and notification_status = 'SENT' and sent_at >= ?
                """, Integer.class, subsystem, metric, windowStart);
        return sent == null ? 0 : sent;
    }

    private Map<String, Object> dependencyCheck(String dependencyName, boolean blocking) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select dependency_status, blocking_failure, checked_at from ia_ops_dependency_health where dependency_name = ?
                """, dependencyName);
        if (rows.isEmpty()) {
            return check(dependencyName, blocking ? "UNAVAILABLE" : "DEGRADED", blocking, true);
        }
        Map<String, Object> row = rows.getFirst();
        String status = row.get("DEPENDENCY_STATUS").toString();
        boolean stale = isStale((Timestamp) row.get("CHECKED_AT"));
        boolean rowBlocking = Boolean.TRUE.equals(row.get("BLOCKING_FAILURE"));
        if (stale) {
            status = blocking || rowBlocking ? "UNAVAILABLE" : "DEGRADED";
        }
        return check(dependencyName, status, blocking || rowBlocking, stale);
    }

    private String opsRoleFor(String operatorId) {
        List<String> roles = jdbcTemplate.queryForList("""
                select operator_role from ia_ops_operation_authorization
                where operator_id = ? and authorization_status = 'ACTIVE'
                """, String.class, operatorId);
        return roles.isEmpty() ? null : roles.getFirst();
    }

    private Map<String, Object> keyThroughput() {
        return orderedMap(
                "ocr_failed", count("ia_ocr_job", "job_status = 'FAILED'"),
                "search_failed", count("ia_search_rebuild_job", "rebuild_status = 'FAILED'"),
                "ai_failed", count("ia_ai_application_job", "job_status = 'FAILED'"),
                "i18n_failed", count("ia_i18n_context", "i18n_status = 'FAILED'"),
                "writeback_dead_letter", count("ia_writeback_dead_letter", "dead_letter_status = 'OPEN'")
        );
    }

    private Map<String, Object> metricMatrix() {
        Map<String, Object> matrix = orderedMap(
                "OCR", orderedMap(
                        "throughput", orderedMap("ocr_job_failed_count", count("ia_ocr_job", "job_status = 'FAILED'"), "ocr_job_succeeded_count", count("ia_ocr_job", "job_status = 'SUCCEEDED'")),
                        "latency", orderedMap("ocr_end_to_end_duration_p95", missingMetric(), "ocr_queue_wait_duration_p95", missingMetric()),
                        "quality", orderedMap("ocr_avg_text_confidence", missingMetric(), "ocr_rebuild_completion_rate", count("ia_ocr_result_aggregate", "result_status = 'READY'")),
                        "health", orderedMap("ocr_engine_timeout_count", count("ia_ocr_job", "failure_code = 'OCR_ENGINE_TIMEOUT'"), "ocr_human_review_backlog", count("ia_ocr_job", "job_status = 'WAITING_HUMAN_REVIEW'")),
                        "recovery", orderedMap("ocr_dead_letter_depth", count("ia_ocr_job", "failure_code = 'DEAD_LETTER'"))
                ),
                "SEARCH", orderedMap(
                        "throughput", orderedMap("search_index_total_doc_count", count("ia_search_document", "1 = 1"), "search_query_qps", count("ia_search_result_set", "1 = 1")),
                        "latency", orderedMap("search_query_p95_duration", missingMetric(), "search_index_full_rebuild_duration", missingMetric()),
                        "quality", orderedMap("search_zero_result_rate", missingMetric(), "search_permission_filter_rate", missingMetric()),
                        "health", orderedMap("search_rollback_count", count("ia_search_rebuild_job", "alias_status = 'ROLLED_BACK'")),
                        "recovery", orderedMap("search_failed_task_backlog", count("ia_search_rebuild_job", "rebuild_status = 'FAILED'"), "search_backfill_backlog", count("ia_search_rebuild_job", "rebuild_status = 'QUEUED'"))
                ),
                "AI_GUARDRAIL", orderedMap(
                        "throughput", orderedMap("ai_job_failed_count", count("ia_ai_application_job", "job_status = 'FAILED'"), "ai_job_succeeded_count", count("ia_ai_application_job", "job_status = 'SUCCEEDED'")),
                        "latency", orderedMap("ai_agent_os_call_duration_p95", missingMetric(), "ai_context_assembly_duration_p95", missingMetric()),
                        "quality", orderedMap("ai_evidence_coverage_ratio", count("ia_ai_application_result", "evidence_coverage_ratio >= 0.6"), "ai_sourceless_conclusion_intercept_rate", missingMetric()),
                        "health", orderedMap("ai_agent_os_timeout_count", count("ia_ai_application_job", "failure_code = 'AGENT_OS_TIMEOUT'"), "ai_protected_result_backlog", count("ia_protected_result_snapshot", "expires_at > current_timestamp")),
                        "recovery", orderedMap("ai_context_assembly_failed_count", count("ia_ai_application_job", "failure_code = 'CONTEXT_ASSEMBLY_FAILED'"))
                ),
                "CANDIDATE_RANKING", orderedMap(
                        "throughput", orderedMap("ranking_completion_count", count("ia_ai_application_result", "structured_payload_json like '%ranking_snapshot%'")),
                        "latency", orderedMap("ranking_duration_p95", missingMetric(), "quality_evaluation_duration_p95", missingMetric()),
                        "quality", orderedMap("quality_evaluation_failed_count", count("ia_ai_application_result", "structured_payload_json like '%quality_evaluation_failed%'"), "candidate_conflict_rate", missingMetric()),
                        "health", orderedMap("ranking_retry_rate", missingMetric(), "snapshot_rebuild_rate", missingMetric()),
                        "recovery", orderedMap("ranking_failed_count", count("ia_ai_application_job", "failure_code = 'RANKING_FAILED'"))
                ),
                "LANGUAGE_GOVERNANCE", orderedMap(
                        "throughput", orderedMap("terminology_published_term_count", missingMetric()),
                        "latency", orderedMap("terminology_review_wait_duration_avg", missingMetric()),
                        "quality", orderedMap("i18n_context_failed_rate", count("ia_i18n_context", "i18n_status = 'FAILED'"), "terminology_empty_hit_rate", missingMetric()),
                        "health", orderedMap("terminology_profile_load_failed_count", count("ia_i18n_context", "i18n_status = 'FAILED' and downstream_degradation_json like '%TERMINOLOGY_PROFILE_LOAD_FAILED%'"), "terminology_cache_hit_rate", missingMetric()),
                        "recovery", orderedMap("i18n_context_failed_count", count("ia_i18n_context", "i18n_status = 'FAILED'"))
                ),
                "RESULT_WRITEBACK", orderedMap(
                        "throughput", orderedMap("writeback_failed_count", count("ia_writeback_record", "writeback_status = 'FAILED'"), "writeback_written_count", count("ia_writeback_record", "writeback_status = 'WRITTEN'")),
                        "latency", orderedMap("writeback_pending_to_written_duration_p95", missingMetric()),
                        "quality", orderedMap("writeback_version_conflict_rate", count("ia_writeback_record", "conflict_code = 'VERSION_CONFLICT'"), "writeback_unresolved_equal_rate", count("ia_writeback_record", "conflict_code = 'UNRESOLVED_EQUAL'")),
                        "health", orderedMap("writeback_pending_timeout_count", count("ia_writeback_record", "writeback_status = 'PENDING'"), "writeback_lock_contention_count", count("ia_writeback_lock", "lock_status = 'LOCKED'")),
                        "recovery", orderedMap("writeback_dead_letter_depth", count("ia_writeback_dead_letter", "dead_letter_status = 'OPEN'"))
                )
        );
        applyMetricSnapshots(matrix);
        return matrix;
    }

    @SuppressWarnings("unchecked")
    private void applyMetricSnapshots(Map<String, Object> matrix) {
        for (Map<String, Object> row : jdbcTemplate.queryForList("""
                select subsystem, metric_group, metric_name, metric_value
                from ia_ops_metric_snapshot m
                where captured_at = (
                    select max(captured_at) from ia_ops_metric_snapshot latest
                    where latest.subsystem = m.subsystem and latest.metric_group = m.metric_group and latest.metric_name = m.metric_name
                )
                """)) {
            Map<String, Object> subsystem = (Map<String, Object>) matrix.get(row.get("SUBSYSTEM"));
            if (subsystem == null) {
                continue;
            }
            Map<String, Object> group = (Map<String, Object>) subsystem.get(row.get("METRIC_GROUP"));
            if (group != null) {
                group.put(row.get("METRIC_NAME").toString(), row.get("METRIC_VALUE"));
            }
        }
    }

    private Map<String, Object> flatten(Map<String, Map<String, Object>> grouped) {
        Map<String, Object> result = new LinkedHashMap<>();
        grouped.values().forEach(result::putAll);
        return result;
    }

    private void addRelated(List<Map<String, Object>> events, String subsystem, String table, String idColumn, String statusColumn,
                            String traceId, String taskId, String contractId, String documentVersionId) {
        StringBuilder sql = new StringBuilder("select * from " + table + " where 1 = 0");
        List<Object> args = new ArrayList<>();
        Consumer<String> addOr = condition -> sql.append(" or ").append(condition);
        boolean hasTraceId = hasColumn(table, "trace_id");
        if (!isBlank(traceId) && hasTraceId) {
            addOr.accept("trace_id = ?");
            args.add(traceId);
        }
        if (!isBlank(taskId)) {
            addOr.accept(idColumn + " = ?");
            args.add(taskId);
        }
        if (!isBlank(contractId) && hasColumn(table, "contract_id")) {
            addOr.accept("contract_id = ?");
            args.add(contractId);
        }
        if (!isBlank(documentVersionId) && hasColumn(table, "document_version_id")) {
            addOr.accept("document_version_id = ?");
            args.add(documentVersionId);
        }
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql.toString(), args.toArray())) {
            events.add(orderedMap(
                    "subsystem", subsystem,
                    "trace_id", hasTraceId ? row.get("TRACE_ID") : null,
                    "task_id", row.get(idColumn.toUpperCase()),
                    "contract_id", row.get("CONTRACT_ID"),
                    "document_version_id", row.get("DOCUMENT_VERSION_ID"),
                    "event_status", row.get(statusColumn.toUpperCase())
            ));
        }
    }

    private boolean hasColumn(String table, String column) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from information_schema.columns where table_name = upper(?) and column_name = upper(?)", Integer.class, table, column);
        return count != null && count > 0;
    }

    private AlertRule alertRule(String subsystem, String metric, double value, Map<String, Object> request) {
        AlertRule profileRule = thresholdProfileRule(subsystem, metric, request);
        if (profileRule != null) {
            return thresholdReached(profileRule, value) ? profileRule : null;
        }
        return alertRules().stream()
                .filter(rule -> rule.subsystem().equals(subsystem) && rule.metricName().equals(metric) && thresholdReached(rule, value))
                .findFirst()
                .orElse(null);
    }

    private AlertRule thresholdProfileRule(String subsystem, String metric, Map<String, Object> request) {
        String requestedProfile = text(request, "quality_profile_code", null);
        List<Map<String, Object>> rows;
        if (isBlank(requestedProfile)) {
            rows = jdbcTemplate.queryForList("""
                    select subsystem, metric_name, severity, threshold_value, threshold_direction
                    from ia_ops_alert_threshold_profile
                    where subsystem = ? and metric_name = ? and enabled_flag = ?
                    order by updated_at desc
                    """, subsystem, metric, true);
        } else {
            rows = jdbcTemplate.queryForList("""
                    select subsystem, metric_name, severity, threshold_value, threshold_direction
                    from ia_ops_alert_threshold_profile
                    where profile_code = ? and subsystem = ? and metric_name = ? and enabled_flag = ?
                    order by updated_at desc
                    """, requestedProfile, subsystem, metric, true);
        }
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.getFirst();
        return new AlertRule(row.get("SUBSYSTEM").toString(), row.get("METRIC_NAME").toString(), row.get("SEVERITY").toString(), number(row.get("THRESHOLD_VALUE")), profileCondition(metric), profileDuration(metric));
    }

    private String profileCondition(String metric) {
        if ("ocr_avg_quality_score".equals(metric)) {
            return "OCR 结果质量分低于告警阈值";
        }
        return "阈值画像告警";
    }

    private String profileDuration(String metric) {
        if ("ocr_avg_quality_score".equals(metric)) {
            return "10 分钟";
        }
        return "配置窗口";
    }

    private boolean thresholdReached(AlertRule rule, double value) {
        if (lowerValueIsWorse(rule.metricName())) {
            return value <= rule.threshold();
        }
        return value >= rule.threshold();
    }

    private boolean lowerValueIsWorse(String metricName) {
        return metricName.contains("success_rate") || metricName.contains("completion_rate") || metricName.contains("coverage_ratio") || metricName.contains("quality_score");
    }

    private List<AlertRule> alertRules() {
        return List.of(
                new AlertRule("OCR", "ocr_job_failed_rate", "P1", 0.25, "OCR 失败率严重", "3 分钟"),
                new AlertRule("OCR", "ocr_dead_letter_depth", "P1", 200, "OCR 死信紧急堆积", "3 分钟"),
                new AlertRule("OCR", "ocr_job_queued_depth", "P1", 500, "OCR 队列紧急积压", "3 分钟"),
                new AlertRule("OCR", "ocr_engine_circuit_breaker_count", "P1", 1, "OCR 引擎熔断", "5 分钟无恢复"),
                new AlertRule("OCR", "ocr_engine_call_success_rate", "P1", 0.80, "OCR 引擎严重不可用", "3 分钟"),
                new AlertRule("OCR", "ocr_job_failed_rate", "P2", 0.10, "OCR 失败率升高", "5 分钟"),
                new AlertRule("OCR", "ocr_job_queued_depth", "P2", 100, "OCR 队列严重积压", "5 分钟"),
                new AlertRule("OCR", "ocr_dead_letter_depth", "P2", 50, "OCR 死信堆积", "5 分钟"),
                new AlertRule("OCR", "ocr_engine_call_success_rate", "P2", 0.95, "OCR 引擎成功率下降", "5 分钟"),
                new AlertRule("OCR", "ocr_rebuild_completion_rate", "P2", 0.80, "OCR 版本切换后重建积压", "10 分钟"),
                new AlertRule("OCR", "ocr_human_review_backlog", "P2", 30, "OCR 人工复核积压", "15 分钟"),
                new AlertRule("OCR", "ocr_result_write_missing_count", "P2", 10, "OCR 结果写入失败", "10 分钟"),
                new AlertRule("OCR", "ocr_dc_binding_missing_count", "P2", 15, "OCR 文档中心挂接失败", "15 分钟"),
                new AlertRule("OCR", "ocr_superseded_consumed_count", "P2", 5, "OCR 历史结果仍被默认消费", "10 分钟"),
                new AlertRule("SEARCH", "search_index_total_doc_drop_rate", "P1", 0.20, "搜索索引文档异常陡降", "单周期"),
                new AlertRule("SEARCH", "search_query_p95_duration", "P1", 5, "搜索查询时延严重恶化", "3 分钟"),
                new AlertRule("SEARCH", "search_index_alias_switch_failed", "P1", 1, "搜索别名切换失败", "任务超时或失败"),
                new AlertRule("SEARCH", "search_ai_result_unauthorized_intercept_count", "P1", 50, "搜索越权拦截突增", "单周期"),
                new AlertRule("SEARCH", "search_engine_health_unreachable_minutes", "P1", 3, "搜索引擎完全不可用", "3 分钟"),
                new AlertRule("SEARCH", "search_query_p95_duration", "P2", 2, "搜索查询时延恶化", "5 分钟"),
                new AlertRule("SEARCH", "search_index_incremental_refresh_lag_minutes", "P2", 10, "搜索增量刷新严重延迟", "10 分钟"),
                new AlertRule("SEARCH", "search_zero_result_rate", "P2", 0.40, "搜索零结果率异常", "5 分钟"),
                new AlertRule("SEARCH", "search_rebuild_failure_rate", "P2", 0.10, "搜索重建失败率升高", "10 分钟"),
                new AlertRule("SEARCH", "search_failed_task_backlog", "P2", 100, "搜索失败任务堆积", "5 分钟"),
                new AlertRule("SEARCH", "search_backfill_backlog", "P2", 200, "搜索补数积压", "10 分钟"),
                new AlertRule("AI_GUARDRAIL", "ai_agent_os_call_success_rate", "P1", 0.80, "Agent OS 严重不可用", "3 分钟"),
                new AlertRule("AI_GUARDRAIL", "ai_human_confirmation_backlog", "P1", 200, "AI 人工确认紧急积压", "10 分钟"),
                new AlertRule("AI_GUARDRAIL", "ai_context_assembly_failed_rate", "P2", 0.10, "AI 上下文装配失败率升高", "5 分钟"),
                new AlertRule("AI_GUARDRAIL", "ai_guardrail_blocked_rate", "P2", 0.30, "AI 护栏拦截率异常升高", "10 分钟"),
                new AlertRule("AI_GUARDRAIL", "ai_human_confirmation_backlog", "P2", 50, "AI 人工确认积压", "15 分钟"),
                new AlertRule("AI_GUARDRAIL", "ai_agent_os_call_success_rate", "P2", 0.95, "Agent OS 调用成功率下降", "5 分钟"),
                new AlertRule("AI_GUARDRAIL", "ai_protected_result_backlog", "P2", 100, "ProtectedResultSnapshot 堆积", "10 分钟"),
                new AlertRule("AI_GUARDRAIL", "ai_evidence_coverage_ratio", "P2", 0.60, "AI 证据覆盖率下降", "15 分钟"),
                new AlertRule("AI_GUARDRAIL", "ai_sourceless_conclusion_intercept_rate", "P2", 0.20, "AI 无来源结论拦截率突增", "10 分钟"),
                new AlertRule("AI_GUARDRAIL", "ai_source_superseded_ratio", "P2", 0.15, "AI 来源失效比例升高", "15 分钟"),
                new AlertRule("CANDIDATE_RANKING", "candidate_conflict_rate", "P2", 0.20, "候选冲突率升高", "10 分钟"),
                new AlertRule("CANDIDATE_RANKING", "ranking_failed_rate", "P2", 0.10, "排序失败率升高", "5 分钟"),
                new AlertRule("CANDIDATE_RANKING", "quality_evaluation_failed_rate", "P2", 0.10, "质量评估失败率升高", "5 分钟"),
                new AlertRule("CANDIDATE_RANKING", "candidate_elimination_rate", "P2", 0.60, "候选淘汰率异常", "10 分钟"),
                new AlertRule("CANDIDATE_RANKING", "human_rejection_rate", "P2", 0.15, "人工驳回率升高", "15 分钟"),
                new AlertRule("CANDIDATE_RANKING", "snapshot_rebuild_rate", "P2", 0.20, "快照重建率升高", "10 分钟"),
                new AlertRule("CANDIDATE_RANKING", "ranking_retry_rate", "P2", 0.10, "排序重试率升高", "10 分钟"),
                new AlertRule("LANGUAGE_GOVERNANCE", "i18n_context_failed_rate", "P2", 0.10, "i18n_context 失败率升高", "15 分钟"),
                new AlertRule("LANGUAGE_GOVERNANCE", "language_degradation_rate", "P2", 0.15, "语言降级率升高", "15 分钟"),
                new AlertRule("LANGUAGE_GOVERNANCE", "terminology_empty_hit_rate", "P3", 0.10, "术语空命中率升高", "30 分钟"),
                new AlertRule("LANGUAGE_GOVERNANCE", "terminology_review_wait_hours", "P3", 48, "术语审核积压", "下一工作周期"),
                new AlertRule("LANGUAGE_GOVERNANCE", "terminology_deprecated_still_referenced_count", "P3", 10, "已废弃术语仍被引用", "1 小时"),
                new AlertRule("RESULT_WRITEBACK", "writeback_post_write_consistency_anomaly_rate", "P1", 0.05, "回写后一致性校验异常", "5 分钟"),
                new AlertRule("RESULT_WRITEBACK", "writeback_dead_letter_depth", "P1", 100, "回写死信紧急堆积", "5 分钟"),
                new AlertRule("RESULT_WRITEBACK", "writeback_dead_letter_depth", "P2", 30, "回写死信堆积", "10 分钟"),
                new AlertRule("RESULT_WRITEBACK", "writeback_failed_rate", "P2", 0.10, "回写失败率升高", "5 分钟"),
                new AlertRule("RESULT_WRITEBACK", "writeback_pending_to_written_duration_p95_minutes", "P2", 5, "回写端到端时延恶化", "10 分钟"),
                new AlertRule("RESULT_WRITEBACK", "writeback_version_conflict_rate", "P2", 0.10, "回写版本冲突率升高", "10 分钟"),
                new AlertRule("RESULT_WRITEBACK", "writeback_unresolved_equal_rate", "P2", 0.05, "UNRESOLVED_EQUAL 升级率升高", "15 分钟"),
                new AlertRule("RESULT_WRITEBACK", "writeback_pending_timeout_count", "P2", 50, "回写 PENDING 超时", "10 分钟")
        );
    }

    private Map<String, Object> alertRulePayload(AlertRule rule) {
        return orderedMap("subsystem", rule.subsystem(), "metric_name", rule.metricName(), "severity", rule.severity(), "threshold", rule.threshold(), "condition", rule.condition(), "duration", rule.duration(), "silence_window_minutes", 5, "maintenance_window_supported", true);
    }

    private Map<String, Object> missingMetric() {
        return orderedMap("status", "MISSING_SOURCE", "reason", "未接入 ia_ops_metric_snapshot 或事实聚合来源");
    }

    private Map<String, Object> roleMatrix(Object observer, Object operator, Object admin, Object superAdmin) {
        return orderedMap("OPS_OBSERVER", observer, "OPS_OPERATOR", operator, "OPS_ADMIN", admin, "SUPER_ADMIN", superAdmin);
    }

    private Map<String, Object> check(String name, String status, boolean blocking, boolean stale) {
        return orderedMap("name", name, "status", status, "blocking", blocking, "stale", stale);
    }

    private boolean isStale(Timestamp checkedAt) {
        return checkedAt == null || checkedAt.toInstant().isBefore(Instant.now().minus(5, ChronoUnit.MINUTES));
    }

    private boolean mysqlReady() {
        try {
            Integer result = jdbcTemplate.queryForObject("select 1", Integer.class);
            return result != null && result == 1;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private int deadLetterDepth() {
        return count("ia_ocr_job", "failure_code = 'DEAD_LETTER'") + count("ia_writeback_dead_letter", "dead_letter_status = 'OPEN'");
    }

    private String aggregateReadiness(List<Map<String, Object>> checks) {
        if (checks.stream().anyMatch(check -> Boolean.TRUE.equals(check.get("blocking")) && "UNAVAILABLE".equals(check.get("status")))) {
            return "UNAVAILABLE";
        }
        if (checks.stream().anyMatch(check -> !"READY".equals(check.get("status")))) {
            return "DEGRADED";
        }
        return "READY";
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of("error_code", code, "message", message);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 序列化失败", e);
        }
    }

    private String text(Map<String, Object> source, String key, String defaultValue) {
        Object value = source.get(key);
        if (value == null || value.toString().isBlank()) {
            return defaultValue;
        }
        return value.toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private Map<String, Object> orderedMap(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put(entries[i].toString(), entries[i + 1]);
        }
        return map;
    }

    private record ScriptDefinition(String name, String subsystem, String impact, boolean sensitive) {
    }

    private record AlertRule(String subsystem, String metricName, String severity, double threshold, String condition, String duration) {
    }
}

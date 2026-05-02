package com.cmp.platform.batch4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class Batch4OpsMonitoringRecoveryAcceptanceTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanOpsTables() {
        deleteIfExists("ia_ops_alert_threshold_profile");
        deleteIfExists("ia_ops_operation_authorization");
        deleteIfExists("ia_ops_dependency_health");
        deleteIfExists("ia_ops_metric_snapshot");
        deleteIfExists("ia_ops_alert_notification");
        deleteIfExists("ia_ops_maintenance_window");
        deleteIfExists("ia_recovery_operation_log");
        deleteIfExists("ia_writeback_dead_letter");
        deleteIfExists("ia_writeback_lock");
        deleteIfExists("ia_writeback_record");
        deleteIfExists("ia_i18n_context");
        deleteIfExists("ia_ai_audit_event");
        deleteIfExists("ia_protected_result_snapshot");
        deleteIfExists("ia_ai_application_result");
        deleteIfExists("ia_ai_context_envelope");
        deleteIfExists("ia_ai_application_job");
        deleteIfExists("ia_search_audit_event");
        deleteIfExists("ia_search_rebuild_job");
        deleteIfExists("ia_search_result_set");
        deleteIfExists("ia_search_document");
        deleteIfExists("ia_search_source_envelope");
        deleteIfExists("ia_ocr_result_aggregate");
        deleteIfExists("ia_ocr_retry_fact");
        deleteIfExists("ia_ocr_audit_event");
        deleteIfExists("ia_ocr_job");
        deleteIfExists("platform_job");
    }

    @Test
    void exposesOverviewSubsystemAndDrillDownMonitoringPanelsForSixSubsystems() throws Exception {
        seedOpsSignals();

        mockMvc.perform(get("/api/intelligent-applications/ops/monitoring/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.panel_layer").value("OVERVIEW"))
                .andExpect(jsonPath("$.subsystems.length()").value(6))
                .andExpect(jsonPath("$.subsystems[?(@.subsystem == 'OCR')].status").value("DEGRADED"))
                .andExpect(jsonPath("$.active_alert_count").value(5))
                .andExpect(jsonPath("$.metric_matrix.OCR.throughput.ocr_job_failed_count").value(1))
                .andExpect(jsonPath("$.metric_matrix.SEARCH.recovery.search_failed_task_backlog").value(1))
                .andExpect(jsonPath("$.metric_matrix.AI_GUARDRAIL.health.ai_agent_os_timeout_count").value(1))
                .andExpect(jsonPath("$.metric_matrix.CANDIDATE_RANKING.quality.quality_evaluation_failed_count").value(0))
                .andExpect(jsonPath("$.metric_matrix.LANGUAGE_GOVERNANCE.health.terminology_profile_load_failed_count").value(1))
                .andExpect(jsonPath("$.metric_matrix.RESULT_WRITEBACK.recovery.writeback_dead_letter_depth").value(1));

        mockMvc.perform(get("/api/intelligent-applications/ops/monitoring/subsystems/SEARCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.panel_layer").value("SUBSYSTEM"))
                .andExpect(jsonPath("$.metrics.search_failed_task_backlog").value(1))
                .andExpect(jsonPath("$.recovery_scripts[0]").value("recover_search_backfill"));

        mockMvc.perform(get("/api/intelligent-applications/ops/monitoring/drill-down")
                        .param("trace_id", "trace-ops-ocr-dead-letter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.panel_layer").value("DRILL_DOWN"))
                .andExpect(jsonPath("$.trace_id").value("trace-ops-ocr-dead-letter"))
                .andExpect(jsonPath("$.related_events[0].subsystem").value("OCR"))
                .andExpect(jsonPath("$.related_events[0].task_id").value("ocr-ops-1"))
                .andExpect(jsonPath("$.related_events[0].contract_id").value("ctr-ops"))
                .andExpect(jsonPath("$.related_events[0].document_version_id").value("doc-ver-ops"));

        mockMvc.perform(get("/api/intelligent-applications/ops/monitoring/drill-down")
                        .param("contract_id", "ctr-ops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.related_events[?(@.trace_id == 'trace-ops-agent-os-timeout')].subsystem").value("AI_GUARDRAIL"));
    }

    @Test
    void evaluatesAlertLevelsSilenceMaintenanceWindowsAndNotificationRoutes() throws Exception {
        mockMvc.perform(post("/api/intelligent-applications/ops/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subsystem":"OCR","metric_name":"ocr_dead_letter_depth","current_value":220,"trace_id":"trace-alert-p1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value("P1"))
                .andExpect(jsonPath("$.notification_channels[0]").value("OPS_WORKBENCH"))
                .andExpect(jsonPath("$.notification_channels[?(@ == 'SMS')]").value("SMS"));

        mockMvc.perform(post("/api/intelligent-applications/ops/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subsystem":"SEARCH","metric_name":"search_query_p95_duration","current_value":3,"same_rule_notifications_in_window":2,"trace_id":"trace-alert-suppress-untrusted"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value("P2"))
                .andExpect(jsonPath("$.suppressed").value(false));

        jdbcTemplate.update("insert into ia_ops_alert_notification (notification_id, subsystem, metric_name, severity, trace_id, notification_status, sent_at) values ('notification-search-p95', 'SEARCH', 'search_query_p95_duration', 'P2', 'trace-alert-suppress', 'SENT', current_timestamp)");

        mockMvc.perform(post("/api/intelligent-applications/ops/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subsystem":"SEARCH","metric_name":"search_query_p95_duration","current_value":3,"trace_id":"trace-alert-suppress"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suppressed").value(true))
                .andExpect(jsonPath("$.suppressed_count").value(1));

        mockMvc.perform(post("/api/intelligent-applications/ops/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subsystem":"LANGUAGE_GOVERNANCE","metric_name":"terminology_empty_hit_rate","current_value":0.20,"maintenance_window":{"level":"SILENCE"},"trace_id":"trace-alert-maintenance"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value("P3"))
                .andExpect(jsonPath("$.maintenance_window_active").value(false))
                .andExpect(jsonPath("$.notification_channels.length()").value(1));

        jdbcTemplate.update("insert into ia_ops_maintenance_window (maintenance_window_id, subsystem, window_level, starts_at, ends_at, configured_by, created_at) values ('mw-language-silence', 'LANGUAGE_GOVERNANCE', 'SILENCE', dateadd('minute', -5, current_timestamp), dateadd('minute', 5, current_timestamp), 'ops-admin', current_timestamp)");

        mockMvc.perform(post("/api/intelligent-applications/ops/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subsystem":"LANGUAGE_GOVERNANCE","metric_name":"terminology_empty_hit_rate","current_value":0.20,"trace_id":"trace-alert-maintenance"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenance_window_active").value(true))
                .andExpect(jsonPath("$.notification_channels.length()").value(0));

        assertAlert("OCR", "ocr_job_queued_depth", 600, "P1", "trace-alert-ocr-queue-p1");
        assertAlert("OCR", "ocr_job_queued_depth", 120, "P2", "trace-alert-ocr-queue-p2");
        assertAlert("OCR", "ocr_job_failed_rate", 0.30, "P1", "trace-alert-ocr-failed-p1");
        assertAlert("OCR", "ocr_job_failed_rate", 0.12, "P2", "trace-alert-ocr-failed-p2");
        assertAlert("OCR", "ocr_engine_circuit_breaker_count", 1, "P1", "trace-alert-ocr-circuit");
        assertAlert("OCR", "ocr_engine_call_success_rate", 0.70, "P1", "trace-alert-ocr-success-p1");
        assertAlert("OCR", "ocr_engine_call_success_rate", 0.90, "P2", "trace-alert-ocr-success-p2");
        assertAlertRule("OCR", "ocr_dead_letter_depth", 60, "P2", 50, "OCR 死信堆积", "5 分钟", "trace-alert-ocr-dead-letter-p2");
        seedAlertThresholdProfile("OCR_BASELINE", "OCR", "ocr_avg_quality_score", "P2", 0.70);
        assertAlert("OCR", "ocr_avg_quality_score", 0.65, "P2", "trace-alert-ocr-quality");
        assertNoAlert("OCR", "ocr_avg_quality_score", 0.75, "OCR_BASELINE", "trace-alert-ocr-quality-ok");
        assertAlert("OCR", "ocr_rebuild_completion_rate", 0.70, "P2", "trace-alert-ocr-rebuild");
        assertAlert("OCR", "ocr_human_review_backlog", 31, "P2", "trace-alert-ocr-review");
        assertAlert("OCR", "ocr_result_write_missing_count", 11, "P2", "trace-alert-ocr-result-write");
        assertAlert("OCR", "ocr_dc_binding_missing_count", 16, "P2", "trace-alert-ocr-binding");
        assertAlert("OCR", "ocr_superseded_consumed_count", 6, "P2", "trace-alert-ocr-superseded");
        assertAlert("SEARCH", "search_index_total_doc_drop_rate", 0.25, "P1", "trace-alert-search-p1");
        assertAlert("SEARCH", "search_query_p95_duration", 6, "P1", "trace-alert-search-p95-p1");
        assertAlert("SEARCH", "search_index_incremental_refresh_lag_minutes", 11, "P2", "trace-alert-search-lag");
        assertAlert("SEARCH", "search_zero_result_rate", 0.45, "P2", "trace-alert-search-zero");
        assertAlert("SEARCH", "search_index_alias_switch_failed", 1, "P1", "trace-alert-search-alias");
        assertAlert("SEARCH", "search_rebuild_failure_rate", 0.15, "P2", "trace-alert-search-rebuild");
        assertAlert("SEARCH", "search_failed_task_backlog", 120, "P2", "trace-alert-search-failed-backlog");
        assertAlert("SEARCH", "search_backfill_backlog", 220, "P2", "trace-alert-search-backfill");
        assertAlert("SEARCH", "search_ai_result_unauthorized_intercept_count", 51, "P1", "trace-alert-search-auth");
        assertAlert("SEARCH", "search_engine_health_unreachable_minutes", 4, "P1", "trace-alert-search-engine");
        assertAlert("AI_GUARDRAIL", "ai_agent_os_call_success_rate", 0.70, "P1", "trace-alert-ai-p1");
        assertAlertRule("AI_GUARDRAIL", "ai_agent_os_call_success_rate", 0.90, "P2", 0.95, "Agent OS 调用成功率下降", "5 分钟", "trace-alert-ai-agent-os-p2");
        assertAlert("AI_GUARDRAIL", "ai_context_assembly_failed_rate", 0.12, "P2", "trace-alert-ai-context");
        assertAlert("AI_GUARDRAIL", "ai_guardrail_blocked_rate", 0.35, "P2", "trace-alert-ai-guardrail");
        assertAlert("AI_GUARDRAIL", "ai_human_confirmation_backlog", 220, "P1", "trace-alert-ai-human-p1");
        assertAlert("AI_GUARDRAIL", "ai_human_confirmation_backlog", 60, "P2", "trace-alert-ai-human-p2");
        assertAlert("AI_GUARDRAIL", "ai_protected_result_backlog", 120, "P2", "trace-alert-ai-protected");
        assertAlert("AI_GUARDRAIL", "ai_evidence_coverage_ratio", 0.50, "P2", "trace-alert-ai-evidence");
        assertAlert("AI_GUARDRAIL", "ai_sourceless_conclusion_intercept_rate", 0.25, "P2", "trace-alert-ai-sourceless");
        assertAlert("AI_GUARDRAIL", "ai_source_superseded_ratio", 0.20, "P2", "trace-alert-ai-source");
        assertAlert("CANDIDATE_RANKING", "candidate_conflict_rate", 0.25, "P2", "trace-alert-ranking-p2");
        assertAlert("CANDIDATE_RANKING", "ranking_failed_rate", 0.12, "P2", "trace-alert-ranking-failed");
        assertAlert("CANDIDATE_RANKING", "quality_evaluation_failed_rate", 0.12, "P2", "trace-alert-quality-failed");
        assertAlert("CANDIDATE_RANKING", "candidate_elimination_rate", 0.65, "P2", "trace-alert-ranking-elimination");
        assertAlert("CANDIDATE_RANKING", "human_rejection_rate", 0.18, "P2", "trace-alert-ranking-rejection");
        assertAlert("CANDIDATE_RANKING", "snapshot_rebuild_rate", 0.25, "P2", "trace-alert-ranking-rebuild");
        assertAlert("CANDIDATE_RANKING", "ranking_retry_rate", 0.12, "P2", "trace-alert-ranking-retry");
        assertAlert("LANGUAGE_GOVERNANCE", "i18n_context_failed_rate", 0.15, "P2", "trace-alert-i18n-p2");
        assertAlert("LANGUAGE_GOVERNANCE", "language_degradation_rate", 0.16, "P2", "trace-alert-language-degrade");
        assertAlert("LANGUAGE_GOVERNANCE", "terminology_review_wait_hours", 49, "P3", "trace-alert-term-review");
        assertAlert("LANGUAGE_GOVERNANCE", "terminology_deprecated_still_referenced_count", 11, "P3", "trace-alert-i18n-deprecated");
        assertAlert("RESULT_WRITEBACK", "writeback_post_write_consistency_anomaly_rate", 0.08, "P1", "trace-alert-writeback-p1");
        assertAlert("RESULT_WRITEBACK", "writeback_dead_letter_depth", 120, "P1", "trace-alert-writeback-dl-p1");
        assertAlert("RESULT_WRITEBACK", "writeback_dead_letter_depth", 40, "P2", "trace-alert-writeback-dl-p2");
        assertAlert("RESULT_WRITEBACK", "writeback_failed_rate", 0.12, "P2", "trace-alert-writeback-failed");
        assertAlert("RESULT_WRITEBACK", "writeback_pending_to_written_duration_p95_minutes", 6, "P2", "trace-alert-writeback-duration");
        assertAlert("RESULT_WRITEBACK", "writeback_version_conflict_rate", 0.12, "P2", "trace-alert-writeback-version");
        assertAlert("RESULT_WRITEBACK", "writeback_unresolved_equal_rate", 0.06, "P2", "trace-alert-writeback-unresolved");
        assertAlert("RESULT_WRITEBACK", "writeback_pending_timeout_count", 51, "P2", "trace-alert-writeback-timeout");
    }

    @Test
    void listsRecoveryScriptsAndAuditsRecoveryFaultDrills() throws Exception {
        mockMvc.perform(get("/api/intelligent-applications/ops/recovery/scripts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_ocr_dead_letter')].target_subsystem").value("OCR"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_search_backfill')].target_subsystem").value("SEARCH"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_ai_job_dead_letter')].target_subsystem").value("AI_GUARDRAIL"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_ranking_retry')].target_subsystem").value("CANDIDATE_RANKING"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_i18n_context_failed')].target_subsystem").value("LANGUAGE_GOVERNANCE"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_writeback_dead_letter')].target_subsystem").value("RESULT_WRITEBACK"))
                .andExpect(jsonPath("$.scripts.length()").value(30))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_ocr_result_write')].target_subsystem").value("OCR"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_ocr_dc_binding')].target_subsystem").value("OCR"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_ocr_version_rebuild')].impact_level").value("HIGH"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_ocr_engine_circuit_breaker')].target_subsystem").value("OCR"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_ocr_to_search_index')].impact_level").value("LOW"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_search_index_build')].target_subsystem").value("SEARCH"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_search_full_rebuild')].impact_level").value("HIGH"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_search_alias_rollback')].impact_level").value("HIGH"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_search_snapshot_cleanup')].impact_level").value("LOW"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_search_cache_invalidate')].impact_level").value("LOW"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_ai_context_assembly')].target_subsystem").value("AI_GUARDRAIL"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_ai_guardrail_replay')].target_subsystem").value("AI_GUARDRAIL"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_ai_human_confirmation_timeout')].impact_level").value("LOW"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_protected_result_cleanup')].target_subsystem").value("AI_GUARDRAIL"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_ai_source_superseded')].impact_level").value("MEDIUM"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_quality_evaluation_retry')].target_subsystem").value("CANDIDATE_RANKING"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_candidate_snapshot_cleanup')].impact_level").value("LOW"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_candidate_source_superseded')].impact_level").value("MEDIUM"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_terminology_profile_load')].target_subsystem").value("LANGUAGE_GOVERNANCE"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_terminology_cache_refresh')].target_subsystem").value("LANGUAGE_GOVERNANCE"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_writeback_conflict_resolve')].impact_level").value("MEDIUM"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_writeback_pending_timeout')].impact_level").value("MEDIUM"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_writeback_consistency')].impact_level").value("HIGH"))
                .andExpect(jsonPath("$.scripts[?(@.script_name == 'recover_writeback_mark_superseded')].impact_level").value("LOW"));

        seedOpsSignals();
        seedOpsAuthorization("ops-user", "OPS_ADMIN");
        executeRecovery("recover_ocr_dead_letter", "OPS_ADMIN", null, "trace-drill-ocr");
        executeRecovery("recover_search_index_build", "OPS_ADMIN", null, "trace-drill-search");
        executeRecovery("recover_ai_job_dead_letter", "OPS_ADMIN", null, "trace-drill-agent-os-timeout");
        seedExpiredProtectedResultSnapshot();
        executeRecovery("recover_protected_result_cleanup", "OPS_ADMIN", null, "trace-drill-protected-cleanup")
                .andExpect(jsonPath("$.rollback.affected_count").value(1));
        executeRecovery("recover_terminology_profile_load", "OPS_ADMIN", null, "trace-drill-terminology");
        executeRecovery("recover_writeback_dead_letter", "OPS_ADMIN", null, "trace-drill-writeback");

        assertRowCount("ia_recovery_operation_log", "execution_status = 'SUCCEEDED'", 6);
        assertRowCount("ia_recovery_operation_log", "script_name = 'recover_ai_job_dead_letter' and trace_id = 'trace-drill-agent-os-timeout'", 1);
        assertRowCount("ia_protected_result_snapshot", "protected_result_snapshot_id = 'protected-expired-ops'", 0);
        assertRowCount("ia_ocr_job", "ocr_job_id = 'ocr-ops-1' and job_status = 'QUEUED' and current_attempt_no = 0", 1);
        assertRowCount("ia_search_rebuild_job", "rebuild_job_id = 'search-rebuild-ops' and rebuild_status = 'QUEUED'", 1);
        assertRowCount("ia_ai_application_job", "ai_application_job_id = 'ai-ops-timeout' and job_status = 'QUEUED' and failure_code is null", 1);
        assertRowCount("ia_i18n_context", "i18n_context_id = 'i18n-ops-failed' and i18n_status = 'READY'", 1);
        assertRowCount("ia_writeback_dead_letter", "dead_letter_id = 'dl-writeback-ops' and dead_letter_status = 'REQUEUED'", 1);
        assertRowCount("ia_writeback_record", "writeback_record_id = 'writeback-ops-failed' and writeback_status = 'PENDING'", 1);
    }

    @Test
    void exposesRollbackRunbooksAndExecutesAliasRollbackWithReviewAudit() throws Exception {
        mockMvc.perform(get("/api/intelligent-applications/ops/rollback/runbooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runbooks[?(@.runbook_name == 'search_double_generation_rollback')].rollback_level").value("L1"))
                .andExpect(jsonPath("$.runbooks[?(@.runbook_name == 'ocr_engine_route_rollback')].rollback_level").value("L2"))
                .andExpect(jsonPath("$.runbooks[?(@.runbook_name == 'writeback_batch_supersede')].rollback_level").value("L2"))
                .andExpect(jsonPath("$.runbooks[?(@.runbook_name == 'ai_result_batch_supersede')].rollback_level").value("L2"));

        seedOpsAuthorization("ops-user", "OPS_ADMIN");
        seedOpsAuthorization("ops-reviewer", "OPS_ADMIN");
        seedOpsSignals();

        executeRecovery("recover_search_alias_rollback", "OPS_ADMIN", "ops-reviewer", "trace-drill-search-alias-rollback")
                .andExpect(jsonPath("$.rollback.alias_status").value("ROLLED_BACK"));

        assertRowCount("ia_recovery_operation_log", "script_name = 'recover_search_alias_rollback' and review_operator_id = 'ops-reviewer'", 1);
    }

    @Test
    void enforcesOpsPermissionMatrixAndTwoPersonReviewForSensitiveOperations() throws Exception {
        mockMvc.perform(get("/api/intelligent-applications/ops/permissions/matrix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.OPS_OBSERVER.can_execute_recovery").value(false))
                .andExpect(jsonPath("$.roles.OPS_OPERATOR.max_recovery_impact").value("LOW"))
                .andExpect(jsonPath("$.roles.OPS_ADMIN.requires_two_person_review_for_high_impact").value(true))
                .andExpect(jsonPath("$.operations.can_view_overview.OPS_OBSERVER").value(true))
                .andExpect(jsonPath("$.operations.can_view_subsystem.OPS_OBSERVER").value(true))
                .andExpect(jsonPath("$.operations.can_view_drill_down.OPS_OBSERVER").value("MASKED"))
                .andExpect(jsonPath("$.operations.can_ack_alert.OPS_OPERATOR").value(true))
                .andExpect(jsonPath("$.operations.can_silence_alert.OPS_OPERATOR").value(true))
                .andExpect(jsonPath("$.operations.can_execute_medium_recovery.OPS_OPERATOR").value(false))
                .andExpect(jsonPath("$.operations.can_execute_high_recovery.OPS_ADMIN").value("TWO_PERSON_REVIEW"))
                .andExpect(jsonPath("$.operations.can_execute_l1_rollback.OPS_ADMIN").value("APPROVAL_REQUIRED"))
                .andExpect(jsonPath("$.operations.can_configure_alert_rules.OPS_ADMIN").value(true))
                .andExpect(jsonPath("$.operations.can_manage_ops_permissions.SUPER_ADMIN").value(true))
                .andExpect(jsonPath("$.operations.can_export_ops_data.OPS_OPERATOR").value(true));

        seedOpsAuthorization("ops-user", "OPS_OPERATOR");

        executeRecovery("recover_search_alias_rollback", "OPS_ADMIN", null, "trace-forged-admin-header")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("OPS_OPERATION_PERMISSION_DENIED"));

        executeRecovery("recover_search_alias_rollback", "OPS_OPERATOR", null, "trace-forbidden-operator")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("OPS_OPERATION_PERMISSION_DENIED"));

        seedOpsAuthorization("ops-user", "OPS_ADMIN");

        executeRecovery("recover_search_alias_rollback", "OPS_ADMIN", null, "trace-review-required")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.execution_status").value("REVIEW_REQUIRED"));

        executeRecovery("recover_search_alias_rollback", "OPS_ADMIN", "ops-user", "trace-review-same-operator")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("OPS_TWO_PERSON_REVIEW_INVALID"));

        executeRecovery("recover_search_alias_rollback", "OPS_ADMIN", "ops-reviewer", "OPS_OPERATOR", "trace-review-role-denied")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("OPS_REVIEWER_PERMISSION_DENIED"));

        seedOpsAuthorization("ops-reviewer", "OPS_OPERATOR");
        executeRecovery("recover_search_alias_rollback", "OPS_ADMIN", "ops-reviewer", "OPS_ADMIN", "trace-review-request-forged-role")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("OPS_REVIEWER_PERMISSION_DENIED"));

        seedOpsAuthorization("ops-admin-reviewer", "OPS_ADMIN");
        seedOpsSignals();
        executeRecovery("recover_search_alias_rollback", "OPS_ADMIN", "ops-admin-reviewer", null, "trace-review-trusted-role")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution_status").value("SUCCEEDED"));
    }

    @Test
    void auditsRecoveryRejectionsAndDoesNotReportUnimplementedScriptsAsSucceeded() throws Exception {
        executeRecovery("recover_ocr_dead_letter", "OPS_ADMIN", null, "trace-denied-no-server-auth")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("OPS_OPERATION_PERMISSION_DENIED"));
        assertRowCount("ia_recovery_operation_log", "trace_id = 'trace-denied-no-server-auth' and execution_status = 'DENIED'", 1);

        executeRecovery("missing_recovery_script", "OPS_ADMIN", null, "trace-missing-script")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("OPS_RECOVERY_SCRIPT_NOT_FOUND"));
        assertRowCount("ia_recovery_operation_log", "script_name = 'missing_recovery_script' and trace_id = 'trace-missing-script' and execution_status = 'FAILED'", 1);

        seedOpsAuthorization("ops-user", "OPS_ADMIN");
        executeRecovery("recover_search_alias_rollback", "OPS_ADMIN", null, "trace-sensitive-review-required")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.execution_status").value("REVIEW_REQUIRED"));
        assertRowCount("ia_recovery_operation_log", "trace_id = 'trace-sensitive-review-required' and execution_status = 'REVIEW_REQUIRED'", 1);

        executeRecovery("recover_search_alias_rollback", "OPS_ADMIN", "ops-user", "trace-sensitive-same-reviewer")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("OPS_TWO_PERSON_REVIEW_INVALID"));
        assertRowCount("ia_recovery_operation_log", "trace_id = 'trace-sensitive-same-reviewer' and execution_status = 'DENIED' and failure_reason = 'OPS_TWO_PERSON_REVIEW_INVALID'", 1);

        executeRecovery("recover_ocr_result_write", "OPS_ADMIN", null, "trace-not-implemented")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution_status").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.rollback.result_status").value("NOT_IMPLEMENTED"));
        assertRowCount("ia_recovery_operation_log", "trace_id = 'trace-not-implemented' and execution_status = 'NOT_IMPLEMENTED'", 1);
    }

    @Test
    void healthLivenessAndReadinessAggregateReadyDegradedAndUnavailable() throws Exception {
        mockMvc.perform(get("/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        mockMvc.perform(get("/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.stale").value(true))
                .andExpect(jsonPath("$.checks[?(@.name == 'TASK_EXECUTOR')].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.checks[?(@.name == 'SEARCH_ENGINE')].stale").value(true));

        seedReadyDependencies();

        mockMvc.perform(get("/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.stale").value(false));

        seedOpsSignals();
        jdbcTemplate.update("insert into platform_job (platform_job_id, job_type, job_status, source_module, consumer_module, resource_type, resource_id, business_object_type, business_object_id, priority, attempt_no, max_attempts, runner_code, trace_id, created_at, updated_at) values ('executor-dead-ops', 'OPS_EXECUTOR_HEARTBEAT', 'FAILED', 'INTELLIGENT_APPLICATIONS', 'OPS_GOVERNOR', 'EXECUTOR', 'ops-executor-1', 'OPS_EXECUTOR', 'ops-executor-1', 1, 3, 3, 'OPS_EXECUTOR', 'trace-ops-executor-dead', current_timestamp, current_timestamp)");

        mockMvc.perform(get("/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.checks[?(@.name == 'SEARCH_ENGINE')].status").value("READY"))
                .andExpect(jsonPath("$.checks[?(@.name == 'AGENT_OS')].status").value("READY"))
                .andExpect(jsonPath("$.checks[?(@.name == 'TASK_EXECUTOR')].status").value("READY"))
                .andExpect(jsonPath("$.checks[?(@.name == 'DEAD_LETTER_QUEUES')].status").value("READY"));

        seedOpsDependencyHealth("SEARCH_ENGINE", "READY", false, -20);

        mockMvc.perform(get("/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.stale").value(true))
                .andExpect(jsonPath("$.checks[?(@.name == 'SEARCH_ENGINE')].status").value("DEGRADED"))
                .andExpect(jsonPath("$.checks[?(@.name == 'SEARCH_ENGINE')].stale").value(true));

        seedOpsDependencyHealth("SEARCH_ENGINE", "DEGRADED", false);
        seedOpsDependencyHealth("AGENT_OS", "DEGRADED", false);
        seedOpsDependencyHealth("DEAD_LETTER_QUEUES", "WARNING", false);

        mockMvc.perform(get("/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.checks[?(@.name == 'SEARCH_ENGINE')].status").value("DEGRADED"))
                .andExpect(jsonPath("$.checks[?(@.name == 'AGENT_OS')].status").value("DEGRADED"))
                .andExpect(jsonPath("$.checks[?(@.name == 'DEAD_LETTER_QUEUES')].status").value("WARNING"));

        seedOpsDependencyHealth("TASK_EXECUTOR", "UNAVAILABLE", true);

        mockMvc.perform(get("/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.checks[?(@.name == 'TASK_EXECUTOR')].status").value("UNAVAILABLE"));
    }

    private void assertAlert(String subsystem, String metricName, double value, String severity, String traceId) throws Exception {
        mockMvc.perform(post("/api/intelligent-applications/ops/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subsystem":"%s","metric_name":"%s","current_value":%s,"trace_id":"%s"}
                                """.formatted(subsystem, metricName, value, traceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value(severity))
                .andExpect(jsonPath("$.alert_rule.metric_name").value(metricName))
                .andExpect(jsonPath("$.alert_rule.subsystem").value(subsystem))
                .andExpect(jsonPath("$.notification_channels.length()").isNotEmpty());
    }

    private void assertAlertRule(String subsystem, String metricName, double value, String severity, double threshold, String condition, String duration, String traceId) throws Exception {
        mockMvc.perform(post("/api/intelligent-applications/ops/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subsystem":"%s","metric_name":"%s","current_value":%s,"trace_id":"%s"}
                                """.formatted(subsystem, metricName, value, traceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value(severity))
                .andExpect(jsonPath("$.alert_rule.metric_name").value(metricName))
                .andExpect(jsonPath("$.alert_rule.subsystem").value(subsystem))
                .andExpect(jsonPath("$.alert_rule.threshold").value(threshold))
                .andExpect(jsonPath("$.alert_rule.condition").value(condition))
                .andExpect(jsonPath("$.alert_rule.duration").value(duration))
                .andExpect(jsonPath("$.notification_channels.length()").isNotEmpty());
    }

    private void assertNoAlert(String subsystem, String metricName, double value, String qualityProfileCode, String traceId) throws Exception {
        mockMvc.perform(post("/api/intelligent-applications/ops/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subsystem":"%s","metric_name":"%s","current_value":%s,"quality_profile_code":"%s","trace_id":"%s"}
                                """.formatted(subsystem, metricName, value, qualityProfileCode, traceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value("P3"))
                .andExpect(jsonPath("$.alert_rule.condition").value("观测信号"));
    }

    @Test
    void monitoringPanelsReadPersistedOpsMetricFactsInsteadOfHardcodedZeros() throws Exception {
        jdbcTemplate.update("insert into ia_ops_metric_snapshot (metric_snapshot_id, subsystem, metric_group, metric_name, metric_value, trace_id, captured_at) values ('metric-ocr-latency', 'OCR', 'latency', 'ocr_end_to_end_duration_p95', 17.5, 'trace-metric-ocr', current_timestamp)");
        jdbcTemplate.update("insert into ia_ops_metric_snapshot (metric_snapshot_id, subsystem, metric_group, metric_name, metric_value, trace_id, captured_at) values ('metric-search-zero', 'SEARCH', 'quality', 'search_zero_result_rate', 0.42, 'trace-metric-search', current_timestamp)");
        jdbcTemplate.update("insert into ia_ops_metric_snapshot (metric_snapshot_id, subsystem, metric_group, metric_name, metric_value, trace_id, captured_at) values ('metric-ai-source', 'AI_GUARDRAIL', 'quality', 'ai_source_superseded_ratio', 0.18, 'trace-metric-ai', current_timestamp)");
        jdbcTemplate.update("insert into ia_ops_metric_snapshot (metric_snapshot_id, subsystem, metric_group, metric_name, metric_value, trace_id, captured_at) values ('metric-ranking-retry', 'CANDIDATE_RANKING', 'health', 'ranking_retry_rate', 0.13, 'trace-metric-ranking', current_timestamp)");
        jdbcTemplate.update("insert into ia_ops_metric_snapshot (metric_snapshot_id, subsystem, metric_group, metric_name, metric_value, trace_id, captured_at) values ('metric-language-empty', 'LANGUAGE_GOVERNANCE', 'quality', 'terminology_empty_hit_rate', 0.11, 'trace-metric-language', current_timestamp)");
        jdbcTemplate.update("insert into ia_ops_metric_snapshot (metric_snapshot_id, subsystem, metric_group, metric_name, metric_value, trace_id, captured_at) values ('metric-writeback-duration', 'RESULT_WRITEBACK', 'latency', 'writeback_pending_to_written_duration_p95_minutes', 6.5, 'trace-metric-writeback', current_timestamp)");

        mockMvc.perform(get("/api/intelligent-applications/ops/monitoring/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric_matrix.OCR.latency.ocr_end_to_end_duration_p95").value(17.5))
                .andExpect(jsonPath("$.metric_matrix.SEARCH.quality.search_zero_result_rate").value(0.42))
                .andExpect(jsonPath("$.metric_matrix.AI_GUARDRAIL.quality.ai_source_superseded_ratio").value(0.18))
                .andExpect(jsonPath("$.metric_matrix.CANDIDATE_RANKING.health.ranking_retry_rate").value(0.13))
                .andExpect(jsonPath("$.metric_matrix.LANGUAGE_GOVERNANCE.quality.terminology_empty_hit_rate").value(0.11))
                .andExpect(jsonPath("$.metric_matrix.RESULT_WRITEBACK.latency.writeback_pending_to_written_duration_p95_minutes").value(6.5));

        mockMvc.perform(get("/api/intelligent-applications/ops/monitoring/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric_matrix.OCR.latency.ocr_queue_wait_duration_p95.status").value("MISSING_SOURCE"))
                .andExpect(jsonPath("$.metric_matrix.SEARCH.latency.search_index_full_rebuild_duration.status").value("MISSING_SOURCE"))
                .andExpect(jsonPath("$.metric_matrix.SEARCH.quality.search_permission_filter_rate.status").value("MISSING_SOURCE"))
                .andExpect(jsonPath("$.metric_matrix.AI_GUARDRAIL.latency.ai_context_assembly_duration_p95.status").value("MISSING_SOURCE"))
                .andExpect(jsonPath("$.metric_matrix.CANDIDATE_RANKING.latency.ranking_duration_p95.status").value("MISSING_SOURCE"))
                .andExpect(jsonPath("$.metric_matrix.LANGUAGE_GOVERNANCE.latency.terminology_review_wait_duration_avg.status").value("MISSING_SOURCE"))
                .andExpect(jsonPath("$.metric_matrix.RESULT_WRITEBACK.latency.writeback_pending_to_written_duration_p95.status").value("MISSING_SOURCE"));
    }

    private void seedAlertThresholdProfile(String profileCode, String subsystem, String metricName, String severity, double thresholdValue) {
        jdbcTemplate.update("insert into ia_ops_alert_threshold_profile (profile_code, subsystem, metric_name, severity, threshold_value, threshold_direction, enabled_flag, updated_at) values (?, ?, ?, ?, ?, 'LOWER_OR_EQUAL', true, current_timestamp)", profileCode, subsystem, metricName, severity, thresholdValue);
    }

    private org.springframework.test.web.servlet.ResultActions executeRecovery(String scriptName, String role, String reviewOperatorId, String traceId) throws Exception {
        return executeRecovery(scriptName, role, reviewOperatorId, null, traceId);
    }

    private org.springframework.test.web.servlet.ResultActions executeRecovery(String scriptName, String role, String reviewOperatorId, String reviewOperatorRole, String traceId) throws Exception {
        String reviewField = reviewOperatorId == null ? "" : ",\"review_operator_id\":\"" + reviewOperatorId + "\"";
        String reviewRoleField = reviewOperatorRole == null ? "" : ",\"review_operator_role\":\"" + reviewOperatorRole + "\"";
        return mockMvc.perform(post("/api/intelligent-applications/ops/recovery/scripts/{scriptName}/execute", scriptName)
                .header("X-CMP-Ops-Role", role)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"operator_id":"ops-user","trace_id":"%s"%s%s}
                        """.formatted(traceId, reviewField, reviewRoleField)));
    }

    private void seedOpsSignals() {
        jdbcTemplate.update("insert into ia_ocr_job (ocr_job_id, contract_id, document_asset_id, document_version_id, input_content_fingerprint, job_purpose, job_status, language_hint_json, quality_profile_code, engine_route_code, current_attempt_no, max_attempt_no, failure_code, failure_reason, idempotency_key, trace_id, created_at, updated_at) values ('ocr-ops-1', 'ctr-ops', 'doc-ops', 'doc-ver-ops', 'sha256:ops', 'OPS_DRILL', 'FAILED', '[]', 'OCR_BASELINE', 'CMP_OCR_PRIMARY', 5, 5, 'DEAD_LETTER', 'dead letter drill', 'idem-ops', 'trace-ops-ocr-dead-letter', current_timestamp, current_timestamp)");
        jdbcTemplate.update("insert into ia_search_rebuild_job (rebuild_job_id, rebuild_type, rebuild_status, scope_json, old_generation, new_generation, backfilled_count, alias_status, trace_id, created_at, completed_at) values ('search-rebuild-ops', 'RANGE', 'FAILED', '{}', 1, 2, 0, 'FAILED', 'trace-ops-search-failed', current_timestamp, current_timestamp)");
        jdbcTemplate.update("insert into ia_ai_application_job (ai_application_job_id, application_type, contract_id, document_version_id, job_status, idempotency_key, scope_digest, failure_code, failure_reason, trace_id, created_at, updated_at) values ('ai-ops-timeout', 'SUMMARY', 'ctr-ops', 'doc-ver-ops', 'FAILED', 'idem-ai-ops', 'scope-ops', 'AGENT_OS_TIMEOUT', 'Agent OS timeout drill', 'trace-ops-agent-os-timeout', current_timestamp, current_timestamp)");
        jdbcTemplate.update("insert into ia_i18n_context (i18n_context_id, owner_type, owner_id, terminology_profile_code, profile_version, input_language, normalized_language, output_language, display_label_language, i18n_status, segment_language_payload_json, terminology_snapshot_json, downstream_degradation_json, created_at) values ('i18n-ops-failed', 'AI_JOB', 'ai-ops-timeout', 'BASE', 1, 'zh-CN', 'zh-CN', 'zh-CN', 'zh-CN', 'FAILED', '[]', '{}', '{\"reason\":\"TERMINOLOGY_PROFILE_LOAD_FAILED\",\"trace_id\":\"trace-ops-terminology-failed\"}', current_timestamp)");
        jdbcTemplate.update("insert into ia_writeback_record (writeback_record_id, result_id, target_type, target_id, writeback_action, writeback_status, target_snapshot_version, conflict_code, failure_reason, retry_count, operator_type, operator_id, payload_json, trace_id, created_at, updated_at) values ('writeback-ops-failed', 'result-ops', 'CONTRACT_SUMMARY', 'ctr-ops', 'UPSERT_REFERENCE', 'FAILED', 0, 'NO_CONFLICT', 'dead letter drill', 5, 'USER', 'ops-user', '{}', 'trace-ops-writeback-dead-letter', current_timestamp, current_timestamp)");
        jdbcTemplate.update("insert into ia_writeback_dead_letter (dead_letter_id, writeback_record_id, result_id, target_type, target_id, failure_reason, retry_count, dead_letter_status, trace_id, created_at) values ('dl-writeback-ops', 'writeback-ops-failed', 'result-ops', 'CONTRACT_SUMMARY', 'ctr-ops', 'dead letter drill', 5, 'OPEN', 'trace-ops-writeback-dead-letter', current_timestamp)");
    }

    private void seedExpiredProtectedResultSnapshot() {
        jdbcTemplate.update("insert into ia_ai_application_job (ai_application_job_id, application_type, contract_id, document_version_id, job_status, idempotency_key, scope_digest, trace_id, created_at, updated_at) values ('ai-protected-cleanup', 'SUMMARY', 'ctr-ops', 'doc-ver-ops', 'SUCCEEDED', 'idem-protected-cleanup', 'scope-protected', 'trace-drill-protected-cleanup', current_timestamp, current_timestamp)");
        jdbcTemplate.update("insert into ia_protected_result_snapshot (protected_result_snapshot_id, ai_application_job_id, result_id, agent_task_id, agent_result_id, guardrail_decision, guardrail_failure_code, confirmation_required_flag, protected_payload_ref, protected_payload_json, expires_at, created_at) values ('protected-expired-ops', 'ai-protected-cleanup', null, null, null, 'ALLOW', null, false, 'vault://protected-expired-ops', '{}', dateadd('minute', -1, current_timestamp), current_timestamp)");
    }

    private void seedOpsDependencyHealth(String dependencyName, String status, boolean blocking) {
        jdbcTemplate.update("merge into ia_ops_dependency_health (dependency_name, dependency_status, blocking_failure, checked_at, detail_json) key(dependency_name) values (?, ?, ?, current_timestamp, '{}')", dependencyName, status, blocking);
    }

    private void seedOpsDependencyHealth(String dependencyName, String status, boolean blocking, int checkedAtOffsetMinutes) {
        jdbcTemplate.update("merge into ia_ops_dependency_health (dependency_name, dependency_status, blocking_failure, checked_at, detail_json) key(dependency_name) values (?, ?, ?, dateadd('minute', ?, current_timestamp), '{}')", dependencyName, status, blocking, checkedAtOffsetMinutes);
    }

    private void seedReadyDependencies() {
        seedOpsDependencyHealth("REDIS", "READY", false);
        seedOpsDependencyHealth("SEARCH_ENGINE", "READY", false);
        seedOpsDependencyHealth("OCR_ENGINE", "READY", false);
        seedOpsDependencyHealth("AGENT_OS", "READY", false);
        seedOpsDependencyHealth("TASK_EXECUTOR", "READY", true);
        seedOpsDependencyHealth("DEAD_LETTER_QUEUES", "READY", false);
    }

    private void seedOpsAuthorization(String operatorId, String operatorRole) {
        jdbcTemplate.update("merge into ia_ops_operation_authorization (operator_id, operator_role, authorization_status, updated_at) key(operator_id) values (?, ?, 'ACTIVE', current_timestamp)", operatorId, operatorRole);
    }

    private void deleteIfExists(String tableName) {
        Integer exists = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_name = upper(?)", Integer.class, tableName);
        if (exists != null && exists > 0) {
            jdbcTemplate.update("delete from " + tableName);
        }
    }

    private void assertRowCount(String tableName, String whereClause, int expectedCount) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + whereClause, Integer.class);
        assertThat(count).isEqualTo(expectedCount);
    }
}

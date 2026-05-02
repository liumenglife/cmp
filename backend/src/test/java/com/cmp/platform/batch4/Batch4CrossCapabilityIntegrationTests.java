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
class Batch4CrossCapabilityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanBatch4CrossCapabilityFacts() {
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
        deleteIfExists("contract_ai_extraction_view");
        deleteIfExists("contract_ai_risk_view");
        deleteIfExists("contract_ai_summary_view");
        deleteIfExists("ia_i18n_audit_event");
        deleteIfExists("ia_i18n_context");
        deleteIfExists("ia_terminology_profile_term_snapshot");
        deleteIfExists("ia_terminology_profile");
        deleteIfExists("ia_translation_unit");
        deleteIfExists("ia_term_entry");
        deleteIfExists("ia_ai_audit_event");
        deleteIfExists("ia_protected_result_snapshot");
        deleteIfExists("ia_ai_application_result");
        deleteIfExists("ia_ai_context_envelope");
        deleteIfExists("ia_ai_application_job");
        deleteIfExists("ia_search_audit_event");
        deleteIfExists("ia_search_export_record");
        deleteIfExists("ia_search_rebuild_job");
        deleteIfExists("ia_search_result_set");
        deleteIfExists("ia_search_document");
        deleteIfExists("ia_search_source_envelope");
        deleteIfExists("ia_ocr_language_segment");
        deleteIfExists("ia_ocr_field_candidate");
        deleteIfExists("ia_ocr_seal_region");
        deleteIfExists("ia_ocr_table_region");
        deleteIfExists("ia_ocr_layout_block");
        deleteIfExists("ia_ocr_text_layer");
        deleteIfExists("ia_ocr_result_aggregate");
        deleteIfExists("ia_ocr_retry_fact");
        deleteIfExists("ia_ocr_audit_event");
        deleteIfExists("ia_ocr_job");
        deleteIfExists("ao_human_confirmation");
        deleteIfExists("ao_agent_result");
        deleteIfExists("ao_agent_run");
        deleteIfExists("ao_agent_task");
        deleteIfExists("platform_job");
    }

    @Test
    void verifiesBatch4EndToEndClosedLoopPermissionsConsistencyRecoveryAndMetrics() throws Exception {
        int terminologyVersion = publishTerminologyProfile();
        PreparedContract visible = createContractDocument("第四批综合验证合同", "dept-cross-a", "trace-cross-contract");
        PreparedContract otherDepartment = createContractDocument("第四批越权合同", "dept-cross-b", "trace-cross-other-dept");

        String ocr = mockMvc.perform(post("/api/intelligent-applications/ocr/jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,OCR_CREATE")
                        .header("Idempotency-Key", "idem-cross-ocr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_version_id":"%s","job_purpose":"BATCH4_CROSS_CAPABILITY","terminology_profile_code":"CONTRACT_BASELINE","source_language":"MIXED","response_language":"en-US","display_label_language":"zh-CN","actor_id":"u-cross","trace_id":"trace-cross-ocr"}
                                """.formatted(visible.documentVersionId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.job_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.result.result_status").value("READY"))
                .andExpect(jsonPath("$.i18n_context.profile_version").value(terminologyVersion))
                .andReturn().getResponse().getContentAsString();
        String ocrResultId = jsonString(ocr, "ocr_result_aggregate_id");

        mockMvc.perform(post("/api/intelligent-applications/search/sources/refresh")
                        .header("X-CMP-Permissions", "SEARCH_INDEX_MANAGE,CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_types":["CONTRACT","DOCUMENT","OCR","CLAUSE"],"contract_id":"%s","document_version_id":"%s","ocr_result_aggregate_id":"%s","terminology_profile_code":"CONTRACT_BASELINE","source_language":"MIXED","response_language":"en-US","trace_id":"trace-cross-search-refresh"}
                                """.formatted(visible.contractId(), visible.documentVersionId(), ocrResultId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted_count").value(4))
                .andExpect(jsonPath("$.i18n_context.profile_version").value(terminologyVersion));

        String query = mockMvc.perform(post("/api/intelligent-applications/search/query")
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW,SEARCH_VIEW_BODY")
                        .header("X-CMP-Org-Scope", "dept-cross-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query_text":"第四批综合验证合同","scope_list":["CONTRACT","DOCUMENT","OCR","CLAUSE"],"page":1,"page_size":10,"actor_id":"u-cross","trace_id":"trace-cross-search-query"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items[0].contract_id").value(visible.contractId()))
                .andReturn().getResponse().getContentAsString();
        String resultSetId = jsonString(query, "result_set_id");

        mockMvc.perform(post("/api/intelligent-applications/search/query")
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-cross-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query_text":"第四批综合验证合同","scope_list":["CONTRACT"],"page":1,"page_size":10,"actor_id":"u-cross","trace_id":"trace-cross-search-cross-dept"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(post("/api/intelligent-applications/search/snapshots/{resultSetId}/export", resultSetId)
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-cross-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-cross-export-denied\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("SEARCH_EXPORT_PERMISSION_DENIED"));

        String ai = mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-cross-a")
                        .header("Idempotency-Key", "idem-cross-ai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"SUMMARY","contract_id":"%s","document_version_id":"%s","terminology_profile_code":"CONTRACT_BASELINE","source_language":"MIXED","response_language":"en-US","actor_id":"u-cross","trace_id":"trace-cross-ai"}
                                """.formatted(visible.contractId(), visible.documentVersionId())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_status").value("READY"))
                .andExpect(jsonPath("$.context_envelope.retrieval_layer.result_set_id").isNotEmpty())
                .andExpect(jsonPath("$.guarded_result.guardrail_decision").value("PASS"))
                .andExpect(jsonPath("$.i18n_context.profile_version").value(terminologyVersion))
                .andReturn().getResponse().getContentAsString();
        String aiJobId = jsonString(ai, "ai_application_job_id");

        mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-cross-b")
                        .header("Idempotency-Key", "idem-cross-ai-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"QA","contract_id":"%s","document_version_id":"%s","actor_id":"u-cross","trace_id":"trace-cross-ai-cross-dept"}
                                """.formatted(visible.contractId(), visible.documentVersionId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("AI_APPLICATION_SCOPE_DENIED"));

        String ranking = mockMvc.perform(post("/api/intelligent-applications/candidates/ranking-jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-cross-a")
                        .header("Idempotency-Key", "idem-cross-ranking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"SUMMARY","contract_id":"%s","document_version_id":"%s","terminology_profile_code":"CONTRACT_BASELINE","source_language":"MIXED","response_language":"en-US","quality_inputs":{"force_release_decision":"PUBLISH"},"writeback_payload":{"summary_text":"第四批跨能力综合验证摘要","summary_scope":"FULL","section_list":[{"section":"验证","text":"OCR、搜索、AI、候选排序与回写已联动"}],"citation_reference":[{"citation_ref":"doc:cross:1"}],"summary_digest":"sha256:cross-summary","confidence":0.94},"actor_id":"u-cross","trace_id":"trace-cross-ranking","rule_hit_list":[{"rule_version":"rule-cross-v1","rule_code":"CROSS_CAPABILITY","hit_status":"ACTIVE","severity":"MEDIUM","strong_veto":false,"conflict_group":"cross"}]}
                                """.formatted(visible.contractId(), visible.documentVersionId())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.quality_evaluation.release_decision").value("PUBLISH"))
                .andExpect(jsonPath("$.writeback_gate.writeback_allowed_flag").value(true))
                .andExpect(jsonPath("$.ranking_snapshot.i18n_context_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String resultId = jsonString(ranking, "result_id");
        String rankingSnapshotId = jsonString(ranking, "ranking_snapshot_id");

        mockMvc.perform(post("/api/intelligent-applications/result-writebacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":0,"operator_id":"u-cross","trace_id":"trace-cross-writeback-denied"}
                                """.formatted(resultId, visible.contractId())))
                .andExpect(status().isForbidden());
        String writeback = mockMvc.perform(post("/api/intelligent-applications/result-writebacks")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":0,"operator_id":"u-cross","trace_id":"trace-cross-writeback"}
                                """.formatted(resultId, visible.contractId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.writeback_status").value("WRITTEN"))
                .andExpect(jsonPath("$.i18n_context_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String writebackRecordId = jsonString(writeback, "writeback_record_id");

        mockMvc.perform(get("/api/contracts/{contractId}/master", visible.contractId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_name").value("第四批综合验证合同"))
                .andExpect(jsonPath("$.contract_status").value("DRAFT"));
        mockMvc.perform(get("/api/document-center/versions/{documentVersionId}", visible.documentVersionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document_version_id").value(visible.documentVersionId()))
                .andExpect(jsonPath("$.version_status").value("ACTIVE"));
        mockMvc.perform(get("/api/contracts/{contractId}/semantic-references", visible.contractId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clause_library_ref.library_code").value("clause-lib-cross"))
                .andExpect(jsonPath("$.template_library_ref.library_code").value("tpl-lib-cross"))
                .andExpect(jsonPath("$.clause_library_ref.source_of_truth").value("contract-core"));
        assertRowCount("contract_ai_summary_view", "contract_id = '" + visible.contractId() + "' and summary_reference_id = '" + writebackRecordId + "' and view_status = 'CURRENT'", 1);
        assertJsonContains("ia_ai_application_result", "structured_payload_json", "result_id = '" + resultId + "'", rankingSnapshotId, "writeback_status", "WRITTEN");
        assertRowCount("ia_ocr_result_aggregate", "document_version_id = '" + visible.documentVersionId() + "' and result_status = 'READY'", 1);
        assertRowCount("ia_search_document", "contract_id = '" + visible.contractId() + "' and exposure_status = 'ACTIVE'", 4);
        assertRowCount("ia_ai_audit_event", "ai_application_job_id = '" + aiJobId + "' and action_type = 'AI_GUARDRAIL_PASS'", 1);
        assertRowCount("ia_ai_audit_event", "action_type = 'RESULT_WRITEBACK_WRITTEN' and trace_id = 'trace-cross-writeback'", 1);
        assertRowCount("ia_ai_audit_event", "action_type = 'CANDIDATE_RANKING_COMPLETED' and trace_id = 'trace-cross-ranking'", 1);
        assertRowCount("ia_search_export_record", "trace_id = 'trace-cross-export-denied'", 0);

        seedRecoverableFailures(visible, otherDepartment);
        seedOpsAuthorization("ops-cross", "OPS_ADMIN");
        executeRecovery("recover_ocr_dead_letter", "trace-cross-recover-ocr");
        executeRecovery("recover_search_backfill", "trace-cross-recover-search");
        executeRecovery("recover_terminology_profile_load", "trace-cross-recover-i18n");
        executeRecovery("recover_writeback_dead_letter", "trace-cross-recover-writeback");
        assertRowCount("ia_recovery_operation_log", "execution_status = 'SUCCEEDED'", 4);
        assertRowCount("ia_recovery_operation_log", "execution_status = 'NOT_IMPLEMENTED'", 0);
        assertRowCount("ia_ocr_job", "ocr_job_id = 'ocr-cross-dead' and job_status = 'QUEUED'", 1);
        assertRowCount("ia_search_rebuild_job", "rebuild_job_id = 'search-cross-failed' and rebuild_status = 'QUEUED'", 1);
        assertRowCount("ia_i18n_context", "i18n_context_id = 'i18n-cross-failed' and i18n_status = 'READY'", 1);
        assertRowCount("ia_writeback_dead_letter", "dead_letter_id = 'dl-cross' and dead_letter_status = 'REQUEUED'", 1);

        seedMetric("OCR", "throughput", "ocr_job_queued_depth", 8);
        seedMetric("SEARCH", "latency", "search_query_p95_duration", 1.7);
        seedMetric("AI_GUARDRAIL", "latency", "ai_agent_os_call_duration_p95", 2.4);
        seedMetric("CANDIDATE_RANKING", "latency", "ranking_duration_p95", 0.8);
        seedMetric("RESULT_WRITEBACK", "latency", "writeback_pending_to_written_duration_p95", 1.2);
        seedMetric("OPERATIONS", "latency", "monitoring_refresh_delay_ms", 350);
        mockMvc.perform(get("/api/intelligent-applications/ops/monitoring/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric_matrix.OCR.throughput.ocr_job_queued_depth").value(8.0))
                .andExpect(jsonPath("$.metric_matrix.SEARCH.latency.search_query_p95_duration").value(1.7))
                .andExpect(jsonPath("$.metric_matrix.AI_GUARDRAIL.latency.ai_agent_os_call_duration_p95").value(2.4))
                .andExpect(jsonPath("$.metric_matrix.CANDIDATE_RANKING.latency.ranking_duration_p95").value(0.8))
                .andExpect(jsonPath("$.metric_matrix.RESULT_WRITEBACK.latency.writeback_pending_to_written_duration_p95").value(1.2))
                .andExpect(jsonPath("$.metric_matrix.OPERATIONS.latency.monitoring_refresh_delay_ms").value(350.0));
    }

    private int publishTerminologyProfile() throws Exception {
        String term = mockMvc.perform(post("/api/intelligent-applications/i18n/terms")
                        .header("X-CMP-Permissions", "I18N_GOVERNANCE_MANAGE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"term_key":"PAYMENT_TERM","domain":"CONTRACT","canonical_language":"zh-CN","created_by":"editor-cross","translations":[{"language_code":"zh-CN","surface_form":"付款条件"},{"language_code":"en-US","surface_form":"Payment Term"},{"language_code":"es-ES","surface_form":"Término de pago"}],"trace_id":"trace-cross-i18n"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String termEntryId = jsonString(term, "term_entry_id");
        mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/submit-review", termEntryId)
                        .header("X-CMP-Permissions", "I18N_GOVERNANCE_MANAGE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_id\":\"editor-cross\",\"trace_id\":\"trace-cross-i18n\"}"))
                .andExpect(status().isOk());
        for (String translationUnitId : new String[]{jsonString(term, "zh_translation_unit_id"), jsonString(term, "en_translation_unit_id"), jsonString(term, "es_translation_unit_id")}) {
            mockMvc.perform(post("/api/intelligent-applications/i18n/translation-units/{translationUnitId}/approve", translationUnitId)
                            .header("X-CMP-Permissions", "I18N_GOVERNANCE_MANAGE")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reviewed_by\":\"reviewer-cross\",\"trace_id\":\"trace-cross-i18n\"}"))
                    .andExpect(status().isOk());
        }
        String profile = mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/publish", termEntryId)
                        .header("X-CMP-Permissions", "I18N_GOVERNANCE_MANAGE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile_code\":\"CONTRACT_BASELINE\",\"operator_id\":\"publisher-cross\",\"trace_id\":\"trace-cross-i18n\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return intValue(profile, "profile_version");
    }

    private PreparedContract createContractDocument(String contractName, String orgUnit, String traceId) throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"%s","owner_user_id":"u-cross-owner","owner_org_unit_id":"%s","category_code":"CROSS","category_name":"综合验证","clause_library_code":"clause-lib-cross","template_library_code":"tpl-lib-cross","trace_id":"%s"}
                                """.formatted(contractName, orgUnit, traceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");
        String document = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"%s.pdf","file_upload_token":"%s-token","trace_id":"%s-doc"}
                                """.formatted(contractId, contractName, traceId, traceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new PreparedContract(contractId, jsonString(document, "document_asset_id"), jsonString(document, "document_version_id"));
    }

    private void seedRecoverableFailures(PreparedContract visible, PreparedContract otherDepartment) {
        jdbcTemplate.update("insert into ia_ocr_job (ocr_job_id, contract_id, document_asset_id, document_version_id, input_content_fingerprint, job_purpose, job_status, language_hint_json, quality_profile_code, engine_route_code, current_attempt_no, max_attempt_no, failure_code, failure_reason, idempotency_key, trace_id, created_at, updated_at) values ('ocr-cross-dead', ?, ?, ?, 'sha256:cross-dead', 'BATCH4_RECOVERY', 'FAILED', '[]', 'OCR_BASELINE', 'CMP_OCR_PRIMARY', 5, 5, 'DEAD_LETTER', 'dead letter drill', 'idem-cross-dead', 'trace-cross-recover-ocr-source', current_timestamp, current_timestamp)", visible.contractId(), visible.documentAssetId(), visible.documentVersionId());
        jdbcTemplate.update("insert into ia_search_rebuild_job (rebuild_job_id, rebuild_type, rebuild_status, scope_json, old_generation, new_generation, backfilled_count, alias_status, trace_id, created_at, completed_at) values ('search-cross-failed', 'RANGE', 'FAILED', '{}', 1, 2, 0, 'FAILED', 'trace-cross-recover-search-source', current_timestamp, current_timestamp)");
        jdbcTemplate.update("insert into ia_ai_application_job (ai_application_job_id, application_type, contract_id, document_version_id, job_status, idempotency_key, scope_digest, failure_code, failure_reason, trace_id, created_at, updated_at) values ('ai-cross-failed', 'SUMMARY', ?, ?, 'FAILED', 'idem-ai-cross-failed', 'scope-cross', 'AGENT_OS_TIMEOUT', 'timeout drill', 'trace-cross-recover-ai-source', current_timestamp, current_timestamp)", visible.contractId(), visible.documentVersionId());
        jdbcTemplate.update("insert into ia_ai_application_job (ai_application_job_id, application_type, contract_id, document_version_id, job_status, idempotency_key, scope_digest, failure_code, failure_reason, trace_id, created_at, updated_at) values ('rank-cross-failed', 'SUMMARY', ?, ?, 'FAILED', 'idem-rank-cross-failed', 'scope-rank-cross', 'RANKING_FAILED', 'ranking drill', 'trace-cross-recover-ranking-source', current_timestamp, current_timestamp)", otherDepartment.contractId(), otherDepartment.documentVersionId());
        jdbcTemplate.update("insert into ia_i18n_context (i18n_context_id, owner_type, owner_id, terminology_profile_code, profile_version, input_language, normalized_language, output_language, display_label_language, i18n_status, segment_language_payload_json, terminology_snapshot_json, downstream_degradation_json, created_at) values ('i18n-cross-failed', 'AI_JOB', 'ai-cross-failed', 'CONTRACT_BASELINE', 1, 'zh-CN', 'zh-CN', 'en-US', 'zh-CN', 'FAILED', '[]', '{}', '{\"reason\":\"TERMINOLOGY_PROFILE_LOAD_FAILED\"}', current_timestamp)");
        jdbcTemplate.update("insert into ia_writeback_record (writeback_record_id, result_id, target_type, target_id, writeback_action, writeback_status, target_snapshot_version, conflict_code, failure_reason, retry_count, operator_type, operator_id, payload_json, trace_id, created_at, updated_at) values ('writeback-cross-failed', 'result-cross-failed', 'CONTRACT_SUMMARY', ?, 'UPSERT_REFERENCE', 'FAILED', 0, 'NO_CONFLICT', 'dead letter drill', 5, 'USER', 'ops-cross', '{}', 'trace-cross-recover-writeback-source', current_timestamp, current_timestamp)", visible.contractId());
        jdbcTemplate.update("insert into ia_writeback_dead_letter (dead_letter_id, writeback_record_id, result_id, target_type, target_id, failure_reason, retry_count, dead_letter_status, trace_id, created_at) values ('dl-cross', 'writeback-cross-failed', 'result-cross-failed', 'CONTRACT_SUMMARY', ?, 'dead letter drill', 5, 'OPEN', 'trace-cross-recover-writeback-source', current_timestamp)", visible.contractId());
    }

    private void executeRecovery(String scriptName, String traceId) throws Exception {
        mockMvc.perform(post("/api/intelligent-applications/ops/recovery/scripts/{scriptName}/execute", scriptName)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operator_id":"ops-cross","trace_id":"%s"}
                                """.formatted(traceId)))
                .andExpect(status().isOk());
    }

    private void seedOpsAuthorization(String operatorId, String role) {
        jdbcTemplate.update("insert into ia_ops_operation_authorization (operator_id, operator_role, authorization_status, updated_at) values (?, ?, 'ACTIVE', current_timestamp)", operatorId, role);
    }

    private void seedMetric(String subsystem, String group, String name, double value) {
        jdbcTemplate.update("insert into ia_ops_metric_snapshot (metric_snapshot_id, subsystem, metric_group, metric_name, metric_value, trace_id, captured_at) values (?, ?, ?, ?, ?, ?, current_timestamp)", "metric-" + subsystem + "-" + name, subsystem, group, name, value, "trace-cross-metric");
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

    private void assertJsonContains(String tableName, String columnName, String whereClause, String... expectedFragments) {
        String value = jdbcTemplate.queryForObject("select " + columnName + " from " + tableName + " where " + whereClause, String.class);
        for (String fragment : expectedFragments) {
            assertThat(value).contains(fragment);
        }
    }

    private String jsonString(String json, String fieldName) {
        return jsonString(json, fieldName, 1);
    }

    private String jsonString(String json, String fieldName, int occurrence) {
        String marker = "\"" + fieldName + "\":\"";
        int start = -1;
        int from = 0;
        for (int i = 0; i < occurrence; i++) {
            start = json.indexOf(marker, from);
            assertThat(start).as("响应缺少第 %s 个字段: %s, body=%s", occurrence, fieldName, json).isGreaterThanOrEqualTo(0);
            from = start + marker.length();
        }
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        return json.substring(valueStart, valueEnd);
    }

    private int intValue(String json, String fieldName) {
        String marker = "\"" + fieldName + "\":";
        int start = json.indexOf(marker);
        assertThat(start).as("响应缺少字段: %s, body=%s", fieldName, json).isGreaterThanOrEqualTo(0);
        int valueStart = start + marker.length();
        int valueEnd = valueStart;
        while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
            valueEnd++;
        }
        return Integer.parseInt(json.substring(valueStart, valueEnd));
    }

    private record PreparedContract(String contractId, String documentAssetId, String documentVersionId) {
    }
}

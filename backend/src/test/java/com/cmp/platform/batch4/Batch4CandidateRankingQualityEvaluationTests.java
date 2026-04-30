package com.cmp.platform.batch4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.util.Map;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class Batch4CandidateRankingQualityEvaluationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void cleanCandidateRankingInputs() {
        jdbcTemplate.update("DELETE FROM ia_ai_audit_event");
        jdbcTemplate.update("DELETE FROM ia_protected_result_snapshot");
        jdbcTemplate.update("DELETE FROM ia_ai_application_result");
        jdbcTemplate.update("DELETE FROM ia_ai_context_envelope");
        jdbcTemplate.update("DELETE FROM ia_ai_application_job");
        jdbcTemplate.update("DELETE FROM ia_search_audit_event");
        jdbcTemplate.update("DELETE FROM ia_search_export_record");
        jdbcTemplate.update("DELETE FROM ia_search_rebuild_job");
        jdbcTemplate.update("DELETE FROM ia_search_result_set");
        jdbcTemplate.update("DELETE FROM ia_search_document");
        jdbcTemplate.update("DELETE FROM ia_search_source_envelope");
        jdbcTemplate.update("DELETE FROM ia_ocr_language_segment");
        jdbcTemplate.update("DELETE FROM ia_ocr_field_candidate");
        jdbcTemplate.update("DELETE FROM ia_ocr_seal_region");
        jdbcTemplate.update("DELETE FROM ia_ocr_table_region");
        jdbcTemplate.update("DELETE FROM ia_ocr_layout_block");
        jdbcTemplate.update("DELETE FROM ia_ocr_text_layer");
        jdbcTemplate.update("DELETE FROM ia_ocr_result_aggregate");
        jdbcTemplate.update("DELETE FROM ia_ocr_retry_fact");
        jdbcTemplate.update("DELETE FROM ia_ocr_audit_event");
        jdbcTemplate.update("DELETE FROM ia_ocr_job");
        jdbcTemplate.update("DELETE FROM ao_human_confirmation");
        jdbcTemplate.update("DELETE FROM ao_agent_result");
        jdbcTemplate.update("DELETE FROM ao_agent_run");
        jdbcTemplate.update("DELETE FROM ao_agent_task");
        jdbcTemplate.update("DELETE FROM platform_job");
    }

    @Test
    void normalizesFiveCandidateSourcesIntoSlotRankedSemanticCandidatesWithFrozenProfiles() throws Exception {
        PreparedInputs sample = createPreparedInputs("候选归一合同", "dept-rank-a", "trace-rank-normalize");

        String response = mockMvc.perform(post("/api/intelligent-applications/candidates/ranking-jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-rank-a")
                        .header("Idempotency-Key", "idem-rank-normalize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rankingRequest(sample, "SUMMARY", "trace-rank-normalize", "rule-version-risk-2026-a", false)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.ranking_snapshot.snapshot_status").value("READY"))
                .andExpect(jsonPath("$.ranking_snapshot.ranking_profile.profile_code").value("CANDIDATE_RANKING_BASELINE"))
                .andExpect(jsonPath("$.ranking_snapshot.ranking_profile.profile_version").value("v1"))
                .andExpect(jsonPath("$.ranking_snapshot.quality_profile.profile_version").value("v1"))
                .andExpect(jsonPath("$.ranking_snapshot.semantic_candidates[?(@.candidate_type == 'SEARCH_HIT')]").isNotEmpty())
                .andExpect(jsonPath("$.ranking_snapshot.semantic_candidates[?(@.candidate_type == 'OCR_FIELD')]").isNotEmpty())
                .andExpect(jsonPath("$.ranking_snapshot.semantic_candidates[?(@.candidate_type == 'CLAUSE_REF')]").isNotEmpty())
                .andExpect(jsonPath("$.ranking_snapshot.semantic_candidates[?(@.candidate_type == 'TEMPLATE_REF')]").isNotEmpty())
                .andExpect(jsonPath("$.ranking_snapshot.semantic_candidates[?(@.candidate_type == 'EVIDENCE_SEGMENT')]").isNotEmpty())
                .andExpect(jsonPath("$.ranking_snapshot.semantic_candidates[?(@.candidate_type == 'RULE_HIT')]").isNotEmpty())
                .andExpect(jsonPath("$.ranking_snapshot.semantic_candidates[?(@.candidate_type == 'CLAUSE_REF' && @.source_object_id == 'clause-ver-clause-lib-rank-v2')]").isNotEmpty())
                .andExpect(jsonPath("$.ranking_snapshot.semantic_candidates[?(@.candidate_type == 'TEMPLATE_REF' && @.source_object_id == 'tpl-ver-tpl-lib-rank-v3')]").isNotEmpty())
                .andExpect(jsonPath("$.ranking_snapshot.semantic_candidates[?(@.candidate_type == 'RULE_HIT' && @.source_object_id == 'rule-version-risk-2026-a')]").isNotEmpty())
                .andExpect(jsonPath("$.ranking_snapshot.semantic_candidates[?(@.candidate_type == 'RULE_HIT' && @.source_object_id == 'risk-rule-v1')]").isEmpty())
                .andExpect(jsonPath("$.ranking_snapshot.semantic_candidates[?(@.candidate_type == 'EVIDENCE_SEGMENT' && @.source_anchor.evidence_segment_id)]").isNotEmpty())
                .andExpect(jsonPath("$.ranking_snapshot.slot_rankings.SUMMARY_FACT[0].elimination_status").value("ACTIVE"))
                .andExpect(jsonPath("$.ranking_snapshot.slot_rankings.ANSWER_SUPPORT").isArray())
                .andExpect(jsonPath("$.ranking_snapshot.slot_rankings.RISK_EVIDENCE").isArray())
                .andExpect(jsonPath("$.ranking_snapshot.slot_rankings.RISK_BASELINE").isArray())
                .andExpect(jsonPath("$.ranking_snapshot.slot_rankings.DIFF_BASELINE").isArray())
                .andExpect(jsonPath("$.ranking_snapshot.slot_rankings.DIFF_DELTA").isArray())
                .andExpect(jsonPath("$.ranking_snapshot.slot_rankings.FIELD_VALUE").isArray())
                .andExpect(jsonPath("$.ranking_snapshot.slot_rankings.RULE_OVERRIDE").isArray())
                .andExpect(jsonPath("$.ranking_snapshot.slot_rankings.RULE_OVERRIDE[0].explanation_digest").isNotEmpty())
                .andExpect(jsonPath("$.ranking_snapshot.slot_governance.slot_quota.SUMMARY_FACT").value(2))
                .andExpect(jsonPath("$.ranking_snapshot.slot_governance.dedupe_summary.reserved_count").value(1))
                .andExpect(jsonPath("$.ranking_snapshot.slot_governance.strong_veto_rule_applied").value(false))
                .andExpect(jsonPath("$.quality_evaluation.quality_tier").value("TIER_A"))
                .andExpect(jsonPath("$.quality_evaluation.release_decision").value("PUBLISH"))
                .andReturn().getResponse().getContentAsString();
        String snapshotId = jsonString(response, "ranking_snapshot_id");

        assertNoCandidateFormalTables();
        assertRowCount("ia_ai_application_job", "result_context_id = '" + snapshotId + "' and job_status = 'SUCCEEDED'", 1);
        assertJsonContains("ia_ai_application_result", "structured_payload_json", "result_id = '" + jsonString(response, "result_id") + "'", "semantic_candidates", "quality_evaluation", "rule-version-risk-2026-a");
        assertJsonContains("ia_ai_application_result", "citation_list_json", "result_id = '" + jsonString(response, "result_id") + "'", "candidate_id", "citation_ref");
        assertRowCount("ia_ai_audit_event", "ai_application_job_id = '" + jsonString(response, "ai_application_job_id") + "' and action_type = 'CANDIDATE_RANKING_COMPLETED'", 1);
    }

    @Test
    void evaluatesFourApplicationTypesWithDifferentQualityTiersAndReleaseMappings() throws Exception {
        PreparedInputs sample = createPreparedInputs("质量分层合同", "dept-rank-a", "trace-rank-quality");

        assertRankingDecision(sample, "SUMMARY", "TIER_A", "PUBLISH", "PASS", "SUCCEEDED", "READY", true, "idem-rank-summary");
        assertRankingDecision(sample, "QA", "TIER_B", "PARTIAL_PUBLISH", "PASS_PARTIAL", "SUCCEEDED", "PARTIAL", true, "idem-rank-qa");
        assertRankingDecision(sample, "RISK_ANALYSIS", "TIER_C", "ESCALATE_TO_HUMAN", "REVIEW_REQUIRED", "WAITING_HUMAN_CONFIRMATION", "BLOCKED", false, "idem-rank-risk");
        assertRankingDecision(sample, "DIFF_EXTRACTION", "TIER_B", "PARTIAL_PUBLISH", "PASS_PARTIAL", "SUCCEEDED", "PARTIAL", true, "idem-rank-diff");

        assertRowCount("ao_human_confirmation", "confirmation_type = 'AI_OUTPUT_REVIEW'", 1);
        assertNoCandidateFormalTables();
    }

    @Test
    void escalatesUnresolvedConflictsToHumanAndRejectsLowQualityCandidates() throws Exception {
        PreparedInputs sample = createPreparedInputs("冲突拒绝合同", "dept-rank-a", "trace-rank-conflict");

        mockMvc.perform(post("/api/intelligent-applications/candidates/ranking-jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-rank-a")
                        .header("Idempotency-Key", "idem-rank-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rankingRequest(sample, "RISK_ANALYSIS", "trace-rank-conflict", "rule-version-veto-conflict", true)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.quality_evaluation.quality_tier").value("TIER_C"))
                .andExpect(jsonPath("$.quality_evaluation.release_decision").value("ESCALATE_TO_HUMAN"))
                .andExpect(jsonPath("$.protected_result_snapshot.guardrail_decision").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.human_confirmation.confirmation_id").isNotEmpty());

        mockMvc.perform(post("/api/intelligent-applications/candidates/ranking-jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-rank-a")
                        .header("Idempotency-Key", "idem-rank-low-quality")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"QA","contract_id":"%s","document_version_id":"%s","actor_id":"u-rank","trace_id":"trace-rank-low-quality","quality_inputs":{"evidence_coverage_ratio":0.20,"citation_validity_score":0.20,"consistency_score":0.15,"completeness_score":0.20,"publishability_score":0.10,"anchor_complete":false},"rule_hit_list":[{"rule_version":"rule-version-low-quality","rule_code":"ANCHOR_REQUIRED","hit_status":"ACTIVE","severity":"HIGH","strong_veto":false}]}
                """.formatted(sample.contractId(), sample.documentVersionId())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_status").value("FAILED"))
                .andExpect(jsonPath("$.quality_evaluation.quality_tier").value("TIER_D"))
                .andExpect(jsonPath("$.quality_evaluation.release_decision").value("REJECT"))
                .andExpect(jsonPath("$.guardrail_mapping.guardrail_decision").value("REJECT"))
                .andExpect(jsonPath("$.job_status").value("FAILED"))
                .andExpect(jsonPath("$.writeback_gate.writeback_allowed_flag").value(false));

        assertNoCandidateFormalTables();
    }

    @Test
    void invalidatesRankingSnapshotsAfterDocumentVersionSwitchAndProfileSwitchWithoutRewritingFrozenVersion() throws Exception {
        PreparedInputs sample = createPreparedInputs("快照失效合同", "dept-rank-a", "trace-rank-snapshot");
        String ranking = createRanking(sample, "SUMMARY", "idem-rank-snapshot", "trace-rank-snapshot");
        String snapshotId = jsonString(ranking, "ranking_snapshot_id");

        mockMvc.perform(post("/api/intelligent-applications/candidates/profiles/switch")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ranking_profile_code\":\"CANDIDATE_RANKING_BASELINE\",\"quality_profile_code\":\"CANDIDATE_QUALITY_BASELINE\",\"new_profile_version\":\"v2\",\"trace_id\":\"trace-rank-profile-switch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.new_profile_version").value("v2"));
        assertJsonContains("ia_ai_application_result", "structured_payload_json", "structured_payload_json like '%" + snapshotId + "%'", "\"snapshot_status\":\"SUPERSEDED\"");

        String secondVersion = mockMvc.perform(post("/api/document-center/assets/{documentAssetId}/versions", sample.documentAssetId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"base_version_id":"%s","version_label":"候选第二版","file_upload_token":"rank-token-v2","trace_id":"trace-rank-doc-v2"}
                                """.formatted(sample.documentVersionId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondVersionId = jsonString(secondVersion, "document_version_id");
        mockMvc.perform(post("/api/document-center/versions/{documentVersionId}/activate", secondVersionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_user_id\":\"u-rank\",\"trace_id\":\"trace-rank-doc-activate\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/intelligent-applications/candidates/ranking-snapshots/{snapshotId}/replay", snapshotId)
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-rank-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-rank-replay-expired\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error_code").value("CANDIDATE_SNAPSHOT_SUPERSEDED"));
    }

    @Test
    void replaysAndBlocksWritebackFromOfficialTablesWhenRuntimeCacheIsMissing() throws Exception {
        PreparedInputs sample = createPreparedInputs("回写阻断合同", "dept-rank-a", "trace-rank-writeback");
        String ranking = mockMvc.perform(post("/api/intelligent-applications/candidates/ranking-jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-rank-a")
                        .header("Idempotency-Key", "idem-rank-writeback-review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rankingRequest(sample, "RISK_ANALYSIS", "trace-rank-writeback-review", "rule-version-writeback-veto", true)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.quality_evaluation.release_decision").value("ESCALATE_TO_HUMAN"))
                .andReturn().getResponse().getContentAsString();
        String resultId = jsonString(ranking, "result_id");
        String snapshotId = jsonString(ranking, "ranking_snapshot_id");

        clearRuntimeCandidateCaches();

        mockMvc.perform(post("/api/intelligent-applications/candidates/ranking-snapshots/{snapshotId}/replay", snapshotId)
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-rank-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-rank-replay-from-db\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranking_snapshot_id").value(snapshotId))
                .andExpect(jsonPath("$.semantic_candidates").isArray());

        mockMvc.perform(post("/api/intelligent-applications/candidates/writeback-candidates")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result_id":"%s","trace_id":"trace-rank-writeback-blocked"}
                                """.formatted(resultId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("CANDIDATE_RELEASE_DECISION_BLOCKS_WRITEBACK"));

        mockMvc.perform(post("/api/intelligent-applications/candidates/writeback-candidates")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result_id\":\"missing-anchors\",\"release_decision\":\"PUBLISH\",\"trace_id\":\"trace-rank-writeback-missing\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("CANDIDATE_QUALITY_ANCHOR_REQUIRED"));
    }

    private void assertRankingDecision(PreparedInputs sample, String applicationType, String tier, String releaseDecision,
                                       String guardrailDecision, String jobStatus, String resultStatus, boolean writebackAllowed, String idempotencyKey) throws Exception {
        mockMvc.perform(post("/api/intelligent-applications/candidates/ranking-jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-rank-a")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rankingRequest(sample, applicationType, "trace-rank-" + applicationType, "rule-version-" + applicationType, "RISK_ANALYSIS".equals(applicationType))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_status").value(jobStatus))
                .andExpect(jsonPath("$.guarded_result.result_status").value(resultStatus))
                .andExpect(jsonPath("$.quality_evaluation.quality_tier").value(tier))
                .andExpect(jsonPath("$.quality_evaluation.release_decision").value(releaseDecision))
                .andExpect(jsonPath("$.guardrail_mapping.guardrail_decision").value(guardrailDecision))
                .andExpect(jsonPath("$.writeback_gate.writeback_allowed_flag").value(writebackAllowed));
    }

    private String createRanking(PreparedInputs sample, String applicationType, String idempotencyKey, String traceId) throws Exception {
        return mockMvc.perform(post("/api/intelligent-applications/candidates/ranking-jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-rank-a")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                %s
                                """.formatted(rankingRequest(sample, applicationType, traceId, "rule-version-" + traceId, false))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
    }

    private PreparedInputs createPreparedInputs(String contractName, String ownerOrgUnitId, String traceId) throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"%s","owner_user_id":"u-rank-owner","owner_org_unit_id":"%s","category_code":"RANK","category_name":"推荐合同","clause_library_code":"clause-lib-rank-v2","template_library_code":"tpl-lib-rank-v3","trace_id":"%s"}
                                """.formatted(contractName, ownerOrgUnitId, traceId)))
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
        String documentAssetId = jsonString(document, "document_asset_id");
        String documentVersionId = jsonString(document, "document_version_id");
        String ocr = mockMvc.perform(post("/api/intelligent-applications/ocr/jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,OCR_CREATE")
                        .header("Idempotency-Key", "idem-ocr-" + traceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_version_id":"%s","job_purpose":"CANDIDATE_RANKING_INPUT","actor_id":"u-rank","trace_id":"%s-ocr"}
                                """.formatted(documentVersionId, traceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String ocrResultId = jsonString(ocr, "ocr_result_aggregate_id");
        String search = mockMvc.perform(post("/api/intelligent-applications/search/sources/refresh")
                        .header("X-CMP-Permissions", "SEARCH_INDEX_MANAGE,CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_types":["CONTRACT","DOCUMENT","OCR","CLAUSE"],"contract_id":"%s","document_version_id":"%s","ocr_result_aggregate_id":"%s","trace_id":"%s-search"}
                                """.formatted(contractId, documentVersionId, ocrResultId, traceId)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return new PreparedInputs(contractId, documentAssetId, documentVersionId, ocrResultId, jsonString(search, "search_doc_id"));
    }

    private String jsonString(String json, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = json.indexOf(marker);
        assertThat(start).as("响应缺少字段: %s, body=%s", fieldName, json).isGreaterThanOrEqualTo(0);
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        return json.substring(valueStart, valueEnd);
    }

    private void assertRowCount(String tableName, String whereClause, int expectedCount) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + whereClause, Integer.class);
        assertThat(count).isEqualTo(expectedCount);
    }

    private void assertNoCandidateFormalTables() {
        for (String tableName : java.util.List.of("IA_CANDIDATE_RANKING_SNAPSHOT", "IA_SEMANTIC_CANDIDATE", "IA_QUALITY_EVALUATION_REPORT", "IA_CANDIDATE_WRITEBACK_GATE", "IA_CANDIDATE_AUDIT_EVENT")) {
            Integer count = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_name = ?", Integer.class, tableName);
            assertThat(count).as("不应新增正式主表 %s", tableName).isZero();
        }
    }

    private void assertJsonContains(String tableName, String columnName, String whereClause, String... expectedFragments) {
        String value = jdbcTemplate.queryForObject("select " + columnName + " from " + tableName + " where " + whereClause, String.class);
        for (String fragment : expectedFragments) {
            assertThat(value).contains(fragment);
        }
    }

    private String rankingRequest(PreparedInputs sample, String applicationType, String traceId, String ruleVersion, boolean strongVeto) {
        return """
                {"application_type":"%s","contract_id":"%s","document_version_id":"%s","ranking_profile_code":"CANDIDATE_RANKING_BASELINE","quality_profile_code":"CANDIDATE_QUALITY_BASELINE","actor_id":"u-rank","trace_id":"%s","rule_hit_list":[{"rule_version":"%s","rule_code":"RISK_OVERRIDE","hit_status":"ACTIVE","severity":"HIGH","strong_veto":%s,"conflict_group":"payment-risk"}]}
                """.formatted(applicationType, sample.contractId(), sample.documentVersionId(), traceId, ruleVersion, strongVeto);
    }

    private void clearRuntimeCandidateCaches() throws Exception {
        Object service = applicationContext.getBean("coreChainService");
        for (String fieldName : java.util.List.of("candidateRankingSnapshots", "qualityEvaluationReports", "candidateWritebackGates")) {
            Field field = service.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            ((Map<?, ?>) field.get(service)).clear();
        }
    }

    private record PreparedInputs(String contractId, String documentAssetId, String documentVersionId, String ocrResultId, String firstSearchDocId) {
    }
}

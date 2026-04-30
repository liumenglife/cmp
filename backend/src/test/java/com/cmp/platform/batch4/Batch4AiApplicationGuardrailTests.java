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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class Batch4AiApplicationGuardrailTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanBatch4AiTables() {
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
    void acceptsFourAiApplicationTypesThroughAgentOsAndAssemblesSixLayerContext() throws Exception {
        ContractDocument sample = createPreparedInputs("AI 六层上下文合同", "dept-ai-a", "trace-ai-six-layer");

        for (String applicationType : new String[]{"SUMMARY", "QA", "RISK_ANALYSIS", "DIFF_EXTRACTION"}) {
            mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                            .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                            .header("X-CMP-Org-Scope", "dept-ai-a")
                            .header("Idempotency-Key", "idem-ai-" + applicationType)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"application_type":"%s","contract_id":"%s","document_version_id":"%s","question":"请基于证据处理合同","actor_id":"u-ai","trace_id":"trace-ai-%s"}
                                    """.formatted(applicationType, sample.contractId(), sample.documentVersionId(), applicationType)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.job_status").value("READY"))
                    .andExpect(jsonPath("$.application_type").value(applicationType))
                    .andExpect(jsonPath("$.agent_os.agent_task_id").isNotEmpty())
                    .andExpect(jsonPath("$.context_envelope.task_intent_layer.application_type").value(applicationType))
                    .andExpect(jsonPath("$.context_envelope.contract_anchor_layer.contract_id").value(sample.contractId()))
                    .andExpect(jsonPath("$.context_envelope.document_evidence_layer.evidence_segment_list[0].evidence_segment_id").isNotEmpty())
                    .andExpect(jsonPath("$.context_envelope.retrieval_layer.result_set_id").isNotEmpty())
                    .andExpect(jsonPath("$.context_envelope.semantic_reference_layer.semantic_reference_list[0].semantic_ref_id").isNotEmpty())
                    .andExpect(jsonPath("$.context_envelope.guardrail_layer.guardrail_profile_code").value("AI_GUARDRAIL_BASELINE"))
                    .andExpect(jsonPath("$.guarded_result.guardrail_decision").value("PASS"))
                    .andExpect(jsonPath("$.guarded_result.citation_list[0].evidence_segment_id").isNotEmpty());
        }

        assertRowCount("ao_agent_task", "task_source = 'INTELLIGENT_APPLICATIONS'", 4);
        assertRowCount("ia_ai_application_result", "guardrail_decision = 'PASS' and result_status = 'READY'", 4);
    }

    @Test
    void rejectsNoEvidenceAndUnauthorizedQuestionBeforeAgentOsExecution() throws Exception {
        ContractDocument sample = createContractDocument("AI 拒答合同", "dept-ai-a", "trace-ai-reject-doc");

        mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-ai-a")
                        .header("Idempotency-Key", "idem-ai-no-evidence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"QA","contract_id":"%s","document_version_id":"%s","question":"没有证据也请回答","actor_id":"u-ai","trace_id":"trace-ai-no-evidence"}
                                """.formatted(sample.contractId(), sample.documentVersionId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("AI_CONTEXT_NO_EVIDENCE"))
                .andExpect(jsonPath("$.protected_result_snapshot.guardrail_decision").value("REJECT"));

        mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-ai-b")
                        .header("Idempotency-Key", "idem-ai-unauthorized")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"QA","contract_id":"%s","document_version_id":"%s","question":"请输出其他部门合同正文","actor_id":"u-ai","trace_id":"trace-ai-unauthorized"}
                                """.formatted(sample.contractId(), sample.documentVersionId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("AI_APPLICATION_SCOPE_DENIED"))
                .andExpect(jsonPath("$.protected_result_snapshot.guardrail_decision").value("REJECT"));

        assertRowCount("ao_agent_task", "task_source = 'INTELLIGENT_APPLICATIONS'", 0);
        assertRowCount("ia_protected_result_snapshot", "guardrail_decision = 'REJECT'", 2);
    }

    @Test
    void storesProtectedSnapshotReplaysGuardrailFailureAndEscalatesHighRiskToHumanConfirmation() throws Exception {
        ContractDocument sample = createPreparedInputs("AI 护栏合同", "dept-ai-a", "trace-ai-guardrail");

        String blocked = mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-ai-a")
                        .header("Idempotency-Key", "idem-ai-blocked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"SUMMARY","contract_id":"%s","document_version_id":"%s","simulate_no_citation_output":true,"actor_id":"u-ai","trace_id":"trace-ai-blocked"}
                                """.formatted(sample.contractId(), sample.documentVersionId())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_status").value("GUARDRAIL_BLOCKED"))
                .andExpect(jsonPath("$.guarded_result.guardrail_decision").value("BLOCK"))
                .andExpect(jsonPath("$.protected_result_snapshot.guardrail_failure_code").value("AI_CITATION_MISSING"))
                .andReturn().getResponse().getContentAsString();
        String snapshotId = jsonString(blocked, "protected_result_snapshot_id");

        mockMvc.perform(post("/api/intelligent-applications/ai/protected-results/{snapshotId}/guardrail-replay", snapshotId)
                        .header("X-CMP-Permissions", "AI_GUARDRAIL_REPLAY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor_id\":\"u-ai\",\"trace_id\":\"trace-ai-replay\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replay_result").value("BLOCK"))
                .andExpect(jsonPath("$.protected_result_snapshot.guardrail_failure_code").value("AI_CITATION_MISSING"));

        String review = mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-ai-a")
                        .header("Idempotency-Key", "idem-ai-high-risk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"RISK_ANALYSIS","contract_id":"%s","document_version_id":"%s","simulate_high_risk":true,"actor_id":"u-ai","trace_id":"trace-ai-high-risk"}
                                """.formatted(sample.contractId(), sample.documentVersionId())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_status").value("WAITING_HUMAN_CONFIRMATION"))
                .andExpect(jsonPath("$.guarded_result.guardrail_decision").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.human_confirmation.confirmation_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String confirmationId = jsonString(review, "confirmation_id");

        mockMvc.perform(post("/api/agent-os/human-confirmations/{confirmationId}/decisions", confirmationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"operator_user_id\":\"reviewer-ai\",\"decision_comment\":\"证据充分\",\"trace_id\":\"trace-ai-human-approve\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmation_status").value("APPROVE"));

        assertRowCount("ia_protected_result_snapshot", "guardrail_decision in ('BLOCK','REVIEW_REQUIRED')", 2);
        assertRowCount("ao_human_confirmation", "confirmation_type = 'AI_OUTPUT_REVIEW'", 1);
    }

    @Test
    void recordsAgentOsTimeoutAsFailedProtectedResultWithoutForgingBusinessOutput() throws Exception {
        ContractDocument sample = createPreparedInputs("AI 超时合同", "dept-ai-a", "trace-ai-timeout");

        mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-ai-a")
                        .header("Idempotency-Key", "idem-ai-timeout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"DIFF_EXTRACTION","contract_id":"%s","document_version_id":"%s","simulate_agent_timeout":true,"actor_id":"u-ai","trace_id":"trace-ai-timeout"}
                                """.formatted(sample.contractId(), sample.documentVersionId())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_status").value("FAILED"))
                .andExpect(jsonPath("$.agent_os.failure_code").value("AGENT_OS_TIMEOUT"))
                .andExpect(jsonPath("$.guarded_result.result_status").value("FAILED"))
                .andExpect(jsonPath("$.guarded_result.writeback_allowed_flag").value(false))
                .andExpect(jsonPath("$.protected_result_snapshot.guardrail_failure_code").value("AGENT_OS_TIMEOUT"));

        assertRowCount("ao_agent_task", "task_status = 'FAILED' and task_source = 'INTELLIGENT_APPLICATIONS'", 1);
        assertRowCount("ia_ai_application_result", "result_status = 'FAILED' and writeback_allowed_flag = false", 1);
        assertRowCount("ia_protected_result_snapshot", "guardrail_failure_code = 'AGENT_OS_TIMEOUT'", 1);
    }

    @Test
    void replaysAcceptedAiJobByIdempotencyKeyWithoutCreatingSecondAgentTask() throws Exception {
        ContractDocument sample = createPreparedInputs("AI 幂等合同", "dept-ai-a", "trace-ai-idempotency");
        String requestBody = """
                {"application_type":"SUMMARY","contract_id":"%s","document_version_id":"%s","actor_id":"u-ai","trace_id":"trace-ai-idempotency"}
                """.formatted(sample.contractId(), sample.documentVersionId());

        String first = mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-ai-a")
                        .header("Idempotency-Key", "idem-ai-repeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String jobId = jsonString(first, "ai_application_job_id");

        mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-ai-a")
                        .header("Idempotency-Key", "idem-ai-repeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.ai_application_job_id").value(jobId));

        assertRowCount("ao_agent_task", "task_source = 'INTELLIGENT_APPLICATIONS'", 1);
        assertRowCount("ia_ai_application_job", "idempotency_key = 'idem-ai-repeat'", 1);
    }

    @Test
    void blocksInvalidSchemaOutputIntoProtectedSnapshot() throws Exception {
        ContractDocument sample = createPreparedInputs("AI Schema 合同", "dept-ai-a", "trace-ai-schema");

        mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-ai-a")
                        .header("Idempotency-Key", "idem-ai-schema-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"DIFF_EXTRACTION","contract_id":"%s","document_version_id":"%s","simulate_schema_invalid_output":true,"actor_id":"u-ai","trace_id":"trace-ai-schema"}
                                """.formatted(sample.contractId(), sample.documentVersionId())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_status").value("GUARDRAIL_BLOCKED"))
                .andExpect(jsonPath("$.guarded_result.guardrail_decision").value("BLOCK"))
                .andExpect(jsonPath("$.protected_result_snapshot.guardrail_failure_code").value("AI_OUTPUT_SCHEMA_INVALID"));

        assertRowCount("ia_protected_result_snapshot", "guardrail_failure_code = 'AI_OUTPUT_SCHEMA_INVALID'", 1);
    }

    @Test
    void executesAiJobThroughAgentOsServiceEntryInsteadOfForgingRuntimeTables() throws Exception {
        ContractDocument sample = createPreparedInputs("AI 真实 Agent OS 合同", "dept-ai-a", "trace-ai-real-agent-os");

        String response = mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-ai-a")
                        .header("Idempotency-Key", "idem-ai-real-agent-os")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"SUMMARY","contract_id":"%s","document_version_id":"%s","actor_id":"u-ai","trace_id":"trace-ai-real-agent-os"}
                                """.formatted(sample.contractId(), sample.documentVersionId())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.agent_os.agent_task_id").isNotEmpty())
                .andExpect(jsonPath("$.agent_os.agent_result_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String agentTaskId = jsonString(response, "agent_task_id");
        String agentResultId = jsonString(response, "agent_result_id");

        assertRowCount("ao_prompt_snapshot", "run_id = (select current_run_id from ao_agent_task where task_id = '" + agentTaskId + "')", 1);
        assertRowCount("ao_tool_result", "run_id = (select current_run_id from ao_agent_task where task_id = '" + agentTaskId + "') and tool_name = 'model.generate_text'", 1);
        assertRowCount("ao_agent_task", "task_id = '" + agentTaskId + "' and final_result_id = '" + agentResultId + "' and task_source = 'INTELLIGENT_APPLICATIONS'", 1);
    }

    @Test
    void createsHumanConfirmationThroughAgentOsContractAnchoredToAgentTask() throws Exception {
        ContractDocument sample = createPreparedInputs("AI 人审锚定合同", "dept-ai-a", "trace-ai-human-contract");

        String response = mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-ai-a")
                        .header("Idempotency-Key", "idem-ai-human-contract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"RISK_ANALYSIS","contract_id":"%s","document_version_id":"%s","agent_output":{"risk_level":"HIGH","summary":"证据显示存在高风险","citation_list":["AUTO"]},"actor_id":"u-ai","trace_id":"trace-ai-human-contract"}
                                """.formatted(sample.contractId(), sample.documentVersionId())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_status").value("WAITING_HUMAN_CONFIRMATION"))
                .andExpect(jsonPath("$.human_confirmation.confirmation_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String agentTaskId = jsonString(response, "agent_task_id");
        String confirmationId = jsonString(response, "confirmation_id");

        assertRowCount("ao_human_confirmation", "confirmation_id = '" + confirmationId + "' and source_task_id = '" + agentTaskId + "' and source_result_id = (select final_result_id from ao_agent_task where task_id = '" + agentTaskId + "')", 1);
    }

    @Test
    void validatesRealAgentOutputForSensitiveInformationAndConflictDowngrade() throws Exception {
        ContractDocument sample = createPreparedInputs("AI 真实输出护栏合同", "dept-ai-a", "trace-ai-real-output");

        mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-ai-a")
                        .header("Idempotency-Key", "idem-ai-real-sensitive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"QA","contract_id":"%s","document_version_id":"%s","agent_output":{"answer":"身份证号 11010119900307653X 可直接公开","citation_list":["AUTO"]},"actor_id":"u-ai","trace_id":"trace-ai-real-sensitive"}
                                """.formatted(sample.contractId(), sample.documentVersionId())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_status").value("GUARDRAIL_BLOCKED"))
                .andExpect(jsonPath("$.protected_result_snapshot.guardrail_failure_code").value("AI_SENSITIVE_INFORMATION_BLOCKED"));

        mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-ai-a")
                        .header("Idempotency-Key", "idem-ai-real-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"DIFF_EXTRACTION","contract_id":"%s","document_version_id":"%s","agent_output":{"summary":"OCR 与条款语义存在冲突","conflict_detected":true,"citation_list":["AUTO"]},"actor_id":"u-ai","trace_id":"trace-ai-real-conflict"}
                                """.formatted(sample.contractId(), sample.documentVersionId())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_status").value("GUARDRAIL_BLOCKED"))
                .andExpect(jsonPath("$.guarded_result.result_status").value("PARTIAL"))
                .andExpect(jsonPath("$.guarded_result.guardrail_decision").value("PASS_PARTIAL"))
                .andExpect(jsonPath("$.protected_result_snapshot.guardrail_failure_code").value("AI_CONFLICT_DOWNGRADED"));
    }

    @Test
    void rejectsWhenEvidenceBudgetCannotPreserveRequiredFactsBeforeAgentOsExecution() throws Exception {
        ContractDocument sample = createPreparedInputs("AI 证据预算合同", "dept-ai-a", "trace-ai-budget");

        mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-ai-a")
                        .header("Idempotency-Key", "idem-ai-budget-insufficient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"SUMMARY","contract_id":"%s","document_version_id":"%s","max_input_tokens":1,"reserved_guardrail_tokens":1,"reserved_citation_tokens":1,"max_evidence_segment_count":1,"actor_id":"u-ai","trace_id":"trace-ai-budget"}
                                """.formatted(sample.contractId(), sample.documentVersionId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("AI_CONTEXT_BUDGET_INSUFFICIENT"))
                .andExpect(jsonPath("$.protected_result_snapshot.guardrail_decision").value("REJECT"));

        assertRowCount("ao_agent_task", "task_source = 'INTELLIGENT_APPLICATIONS'", 0);
        assertRowCount("ia_protected_result_snapshot", "guardrail_failure_code = 'AI_CONTEXT_BUDGET_INSUFFICIENT'", 1);
    }

    private ContractDocument createPreparedInputs(String contractName, String ownerOrgUnitId, String traceId) throws Exception {
        ContractDocument sample = createContractDocument(contractName, ownerOrgUnitId, traceId);
        String ocr = mockMvc.perform(post("/api/intelligent-applications/ocr/jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,OCR_CREATE")
                        .header("Idempotency-Key", "idem-ocr-" + traceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_version_id":"%s","job_purpose":"AI_CONTEXT_INPUT","actor_id":"u-ai","trace_id":"%s-ocr"}
                                """.formatted(sample.documentVersionId(), traceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String ocrResultId = jsonString(ocr, "ocr_result_aggregate_id");
        mockMvc.perform(post("/api/intelligent-applications/search/sources/refresh")
                        .header("X-CMP-Permissions", "SEARCH_INDEX_MANAGE,CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_types":["CONTRACT","DOCUMENT","OCR","CLAUSE"],"contract_id":"%s","document_version_id":"%s","ocr_result_aggregate_id":"%s","trace_id":"%s-search"}
                                """.formatted(sample.contractId(), sample.documentVersionId(), ocrResultId, traceId)))
                .andExpect(status().isAccepted());
        return sample;
    }

    private ContractDocument createContractDocument(String contractName, String ownerOrgUnitId, String traceId) throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"%s","owner_user_id":"u-ai-owner","owner_org_unit_id":"%s","category_code":"AI","category_name":"智能合同","trace_id":"%s"}
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
        return new ContractDocument(contractId, jsonString(document, "document_asset_id"), jsonString(document, "document_version_id"));
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

    private record ContractDocument(String contractId, String documentAssetId, String documentVersionId) {
    }
}

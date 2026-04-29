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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class Batch4OcrStableInputLoopTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanOcrTables() {
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
        jdbcTemplate.update("DELETE FROM platform_job");
    }

    @Test
    void createsOcrJobFromControlledDocumentVersionAndPublishesNormalizedResult() throws Exception {
        ContractDocument sample = createContractDocument("OCR 闭环合同", "trace-ocr-doc-v1");

        String job = mockMvc.perform(post("/api/intelligent-applications/ocr/jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,OCR_CREATE")
                        .header("Idempotency-Key", "idem-ocr-stable-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_asset_id":"%s","job_purpose":"SEARCH_INDEX_INPUT","actor_id":"u-ocr","trace_id":"trace-ocr-create"}
                                """.formatted(sample.documentAssetId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.job_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.document_asset_id").value(sample.documentAssetId()))
                .andExpect(jsonPath("$.document_version_id").value(sample.documentVersionId()))
                .andExpect(jsonPath("$.input_content_fingerprint").isNotEmpty())
                .andExpect(jsonPath("$.engine_route.engine_route_code").value("OCR_BASELINE_TEXT"))
                .andExpect(jsonPath("$.result.result_status").value("READY"))
                .andExpect(jsonPath("$.result.text_layer.pages[0].text").value("OCR 闭环合同 示例识别文本"))
                .andExpect(jsonPath("$.result.layout_blocks[0].bbox.page_width").value(1000))
                .andExpect(jsonPath("$.result.table_regions[0].row_count").value(1))
                .andExpect(jsonPath("$.result.seal_regions[0].seal_shape").value("ROUND"))
                .andExpect(jsonPath("$.result.field_candidates[0].field_type").value("CONTRACT_NO"))
                .andExpect(jsonPath("$.result.language_segments[0].language_code").value("zh-CN"))
                .andExpect(jsonPath("$.task_center_ref.job_type").value("IA_OCR_RECOGNITION"))
                .andExpect(jsonPath("$.audit_events[?(@.action_type == 'OCR_JOB_CREATED')]").exists())
                .andExpect(jsonPath("$.audit_events[?(@.action_type == 'OCR_RESULT_READY')]").exists())
                .andReturn().getResponse().getContentAsString();
        String ocrJobId = jsonString(job, "ocr_job_id");
        String resultId = jsonString(job, "ocr_result_aggregate_id");

        mockMvc.perform(post("/api/intelligent-applications/ocr/jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,OCR_CREATE")
                        .header("Idempotency-Key", "idem-ocr-stable-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_version_id":"%s","job_purpose":"SEARCH_INDEX_INPUT","actor_id":"u-ocr","trace_id":"trace-ocr-create-repeat"}
                                """.formatted(sample.documentVersionId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocr_job_id").value(ocrJobId))
                .andExpect(jsonPath("$.idempotency_replayed").value(true));

        mockMvc.perform(get("/api/document-center/assets/{documentAssetId}/capability-bindings", sample.documentAssetId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capability_bindings[?(@.capability_code == 'OCR')].binding_status").value("READY"))
                .andExpect(jsonPath("$.capability_bindings[?(@.capability_code == 'OCR')].result_aggregate_id").value(resultId));
        mockMvc.perform(get("/api/document-center/events").param("consumer_scope", "BATCH4_INTELLIGENT_APPLICATIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.event_type == 'OCR_RESULT_READY' && @.document_version_id == '%s')]".formatted(sample.documentVersionId())).exists())
                .andExpect(jsonPath("$.items[?(@.event_type == 'IA_SEARCH_REINDEX_REQUESTED' && @.document_version_id == '%s')]".formatted(sample.documentVersionId())).exists());

        assertRowCount("ia_ocr_job", "ocr_job_id = '" + ocrJobId + "' and document_version_id = '" + sample.documentVersionId() + "' and job_status = 'SUCCEEDED'", 1);
        assertRowCount("ia_ocr_result_aggregate", "ocr_result_aggregate_id = '" + resultId + "' and result_status = 'READY'", 1);
        assertRowCount("ia_ocr_text_layer", "ocr_result_aggregate_id = '" + resultId + "'", 1);
        assertRowCount("ia_ocr_layout_block", "ocr_result_aggregate_id = '" + resultId + "'", 1);
        assertRowCount("ia_ocr_table_region", "ocr_result_aggregate_id = '" + resultId + "'", 1);
        assertRowCount("ia_ocr_seal_region", "ocr_result_aggregate_id = '" + resultId + "'", 1);
        assertRowCount("ia_ocr_field_candidate", "ocr_result_aggregate_id = '" + resultId + "'", 1);
        assertRowCount("ia_ocr_language_segment", "ocr_result_aggregate_id = '" + resultId + "'", 1);
        assertRowCount("platform_job", "job_type = 'IA_OCR_RECOGNITION' and resource_id = '" + ocrJobId + "'", 1);
    }

    @Test
    void rejectsUnauthorizedInputAndRetriesEngineFailuresWithoutLeakingProviderPayload() throws Exception {
        ContractDocument sample = createContractDocument("OCR 失败合同", "trace-ocr-fail-doc");

        mockMvc.perform(post("/api/intelligent-applications/ocr/jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .header("Idempotency-Key", "idem-ocr-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_version_id":"%s","job_purpose":"SEARCH_INDEX_INPUT","actor_id":"u-ocr","trace_id":"trace-ocr-denied"}
                                """.formatted(sample.documentVersionId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("OCR_INPUT_PERMISSION_DENIED"));

        String job = mockMvc.perform(post("/api/intelligent-applications/ocr/jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,OCR_CREATE")
                        .header("Idempotency-Key", "idem-ocr-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_version_id":"%s","job_purpose":"SEARCH_INDEX_INPUT","actor_id":"u-ocr","simulate_engine_failure_count":2,"trace_id":"trace-ocr-retry"}
                                """.formatted(sample.documentVersionId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.job_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.current_attempt_no").value(3))
                .andExpect(jsonPath("$.retry_facts[0].failure_code").value("ENGINE_TEMPORARY_UNAVAILABLE"))
                .andExpect(jsonPath("$.provider_private_payload").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String ocrJobId = jsonString(job, "ocr_job_id");
        assertRowCount("ia_ocr_retry_fact", "ocr_job_id = '" + ocrJobId + "' and retryable = true", 2);
        assertRowCount("ia_ocr_audit_event", "ocr_job_id = '" + ocrJobId + "' and action_type = 'OCR_ENGINE_RETRY'", 2);
    }

    @Test
    void publishesPartialResultForLowQualityRecognitionsWithQualityEvidence() throws Exception {
        ContractDocument sample = createContractDocument("OCR 低质量合同", "trace-ocr-partial-doc");

        String job = mockMvc.perform(post("/api/intelligent-applications/ocr/jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,OCR_CREATE")
                        .header("Idempotency-Key", "idem-ocr-partial")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_version_id":"%s","job_purpose":"SEARCH_INDEX_INPUT","actor_id":"u-ocr","simulate_partial_result":true,"trace_id":"trace-ocr-partial"}
                                """.formatted(sample.documentVersionId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.job_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.result.result_status").value("PARTIAL"))
                .andExpect(jsonPath("$.result.default_consumable").value(true))
                .andExpect(jsonPath("$.result.quality_score").value(0.71))
                .andExpect(jsonPath("$.result.page_summary[0].quality").value("LOW"))
                .andExpect(jsonPath("$.result.text_layer.pages[0].confidence_score").value(0.72))
                .andExpect(jsonPath("$.result.field_candidates[0].candidate_status").value("LOW_CONFIDENCE"))
                .andExpect(jsonPath("$.audit_events[?(@.action_type == 'OCR_RESULT_PARTIAL')]").exists())
                .andReturn().getResponse().getContentAsString();
        String resultId = jsonString(job, "ocr_result_aggregate_id");

        mockMvc.perform(get("/api/document-center/assets/{documentAssetId}/capability-bindings", sample.documentAssetId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capability_bindings[?(@.capability_code == 'OCR')].binding_status").value("READY"))
                .andExpect(jsonPath("$.capability_bindings[?(@.capability_code == 'OCR')].result_status").value("PARTIAL"));
        mockMvc.perform(get("/api/document-center/events").param("consumer_scope", "BATCH4_INTELLIGENT_APPLICATIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.event_type == 'OCR_RESULT_PARTIAL' && @.document_version_id == '%s')]".formatted(sample.documentVersionId())).exists())
                .andExpect(jsonPath("$.items[?(@.event_type == 'IA_SEARCH_REINDEX_REQUESTED' && @.document_version_id == '%s')]".formatted(sample.documentVersionId())).exists());

        assertRowCount("ia_ocr_result_aggregate", "ocr_result_aggregate_id = '" + resultId + "' and result_status = 'PARTIAL' and default_consumable = true and quality_score = 0.7100", 1);
        assertRowCount("ia_ocr_field_candidate", "ocr_result_aggregate_id = '" + resultId + "' and candidate_status = 'LOW_CONFIDENCE'", 1);
        assertRowCount("ia_ocr_audit_event", "ocr_result_aggregate_id = '" + resultId + "' and action_type = 'OCR_RESULT_PARTIAL' and result_status = 'PARTIAL'", 1);
    }

    @Test
    void marksResultFailedAfterFinalEngineFailureAndKeepsItOutOfDefaultConsumption() throws Exception {
        ContractDocument sample = createContractDocument("OCR 最终失败合同", "trace-ocr-final-failed-doc");

        String job = mockMvc.perform(post("/api/intelligent-applications/ocr/jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,OCR_CREATE")
                        .header("Idempotency-Key", "idem-ocr-final-failed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_version_id":"%s","job_purpose":"SEARCH_INDEX_INPUT","actor_id":"u-ocr","simulate_engine_failure_count":3,"trace_id":"trace-ocr-final-failed"}
                                """.formatted(sample.documentVersionId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.job_status").value("FAILED"))
                .andExpect(jsonPath("$.current_attempt_no").value(3))
                .andExpect(jsonPath("$.failure_code").value("ENGINE_FAILED"))
                .andExpect(jsonPath("$.result.result_status").value("FAILED"))
                .andExpect(jsonPath("$.result.default_consumable").value(false))
                .andExpect(jsonPath("$.result.quality_score").value(0.0))
                .andExpect(jsonPath("$.retry_facts.length()").value(3))
                .andExpect(jsonPath("$.audit_events[?(@.action_type == 'OCR_RESULT_FAILED')]").exists())
                .andReturn().getResponse().getContentAsString();
        String ocrJobId = jsonString(job, "ocr_job_id");
        String resultId = jsonString(job, "ocr_result_aggregate_id");

        mockMvc.perform(get("/api/intelligent-applications/ocr/results")
                        .param("document_asset_id", sample.documentAssetId())
                        .param("default_only", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/api/document-center/assets/{documentAssetId}/capability-bindings", sample.documentAssetId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capability_bindings[?(@.capability_code == 'OCR')].binding_status").value("PENDING"));

        assertRowCount("ia_ocr_job", "ocr_job_id = '" + ocrJobId + "' and job_status = 'FAILED' and failure_code = 'ENGINE_FAILED'", 1);
        assertRowCount("ia_ocr_result_aggregate", "ocr_result_aggregate_id = '" + resultId + "' and result_status = 'FAILED' and default_consumable = false", 1);
        assertRowCount("ia_ocr_retry_fact", "ocr_job_id = '" + ocrJobId + "' and retryable = true", 3);
        assertRowCount("ia_ocr_text_layer", "ocr_result_aggregate_id = '" + resultId + "'", 0);
        assertRowCount("ia_ocr_audit_event", "ocr_result_aggregate_id = '" + resultId + "' and action_type = 'OCR_RESULT_FAILED' and result_status = 'FAILED'", 1);
    }

    @Test
    void supersedesOldDefaultResultWhenDocumentMainVersionSwitchesAndQueuesRebuild() throws Exception {
        ContractDocument sample = createContractDocument("OCR 版本切换合同", "trace-ocr-switch-doc");
        String firstJob = createOcrJob(sample.documentVersionId(), "idem-ocr-v1", "trace-ocr-v1");
        String firstResultId = jsonString(firstJob, "ocr_result_aggregate_id");

        String secondVersion = mockMvc.perform(post("/api/document-center/assets/{documentAssetId}/versions", sample.documentAssetId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"base_version_id":"%s","version_label":"OCR 第二版","file_upload_token":"ocr-token-v2","trace_id":"trace-ocr-v2-append"}
                                """.formatted(sample.documentVersionId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondVersionId = jsonString(secondVersion, "document_version_id");

        mockMvc.perform(post("/api/document-center/versions/{documentVersionId}/activate", secondVersionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_user_id\":\"u-ocr\",\"trace_id\":\"trace-ocr-activate-v2\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/intelligent-applications/ocr/results/{resultId}", firstResultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result_status").value("SUPERSEDED"))
                .andExpect(jsonPath("$.default_consumable").value(false));
        mockMvc.perform(get("/api/intelligent-applications/ocr/results")
                        .param("document_asset_id", sample.documentAssetId())
                        .param("default_only", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/api/document-center/events").param("consumer_scope", "BATCH4_INTELLIGENT_APPLICATIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.event_type == 'OCR_RESULT_SUPERSEDED' && @.document_version_id == '%s')]".formatted(sample.documentVersionId())).exists())
                .andExpect(jsonPath("$.items[?(@.event_type == 'OCR_REBUILD_REQUESTED' && @.document_version_id == '%s')]".formatted(secondVersionId)).exists());
        assertRowCount("ia_ocr_result_aggregate", "ocr_result_aggregate_id = '" + firstResultId + "' and result_status = 'SUPERSEDED' and default_consumable = false", 1);
        assertRowCount("platform_job", "job_type = 'IA_OCR_REBUILD' and resource_id = '" + secondVersionId + "'", 1);
    }

    private ContractDocument createContractDocument(String contractName, String traceId) throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"%s","owner_user_id":"u-ocr-owner","owner_org_unit_id":"dept-ocr","trace_id":"%s"}
                                """.formatted(contractName, traceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");
        String document = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"%s.pdf","file_upload_token":"%s-token","trace_id":"%s"}
                                """.formatted(contractId, contractName, traceId, traceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new ContractDocument(contractId, jsonString(document, "document_asset_id"), jsonString(document, "document_version_id"));
    }

    private String createOcrJob(String documentVersionId, String idempotencyKey, String traceId) throws Exception {
        return mockMvc.perform(post("/api/intelligent-applications/ocr/jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,OCR_CREATE")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_version_id":"%s","job_purpose":"SEARCH_INDEX_INPUT","actor_id":"u-ocr","trace_id":"%s"}
                                """.formatted(documentVersionId, traceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
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

package com.cmp.platform.batch4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class Batch4ResultWritebackConflictResolutionTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void cleanResultWritebackTables() {
        deleteIfExists("ia_writeback_dead_letter");
        deleteIfExists("ia_writeback_lock");
        deleteIfExists("ia_writeback_record");
        deleteIfExists("contract_ai_extraction_view");
        deleteIfExists("contract_ai_risk_view");
        deleteIfExists("contract_ai_summary_view");
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
    void writesApprovedSummaryIntoContractSummaryViewAndAnchorsAiResult() throws Exception {
        PreparedInputs sample = createPreparedInputs("摘要回写合同", "dept-writeback-a", "trace-writeback-summary");
        String ranking = createFormalRanking(sample, "SUMMARY", "idem-writeback-summary", "trace-writeback-summary-rank", "PUBLISH", """
                {"summary_text":"服务端正式摘要，客户端载荷不得覆盖。","summary_scope":"FULL","section_list":[{"section":"付款","text":"分阶段付款"}],"citation_reference":[{"citation_ref":"doc:1"}],"display_language":"zh-CN","summary_digest":"sha256:summary-a","confidence":0.93}
                """);
        String resultId = jsonString(ranking, "result_id");
        String rankingSnapshotId = jsonString(ranking, "ranking_snapshot_id");

        String response = postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-summary","payload":{"summary_text":"客户端伪造摘要不应落库","confidence":0.01}}
                """.formatted(resultId, sample.contractId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.writeback_status").value("WRITTEN"))
                .andExpect(jsonPath("$.conflict_code").value("NO_CONFLICT"))
                .andReturn().getResponse().getContentAsString();
        String writebackRecordId = jsonString(response, "writeback_record_id");

        assertRowCount("ia_writeback_record", "writeback_record_id = '" + writebackRecordId + "' and result_id = '" + resultId + "' and writeback_status = 'WRITTEN'", 1);
        assertRowCount("contract_ai_summary_view", "contract_id = '" + sample.contractId() + "' and summary_reference_id = '" + writebackRecordId + "' and view_status = 'CURRENT'", 1);
        assertJsonContains("contract_ai_summary_view", "summary_text", "contract_id = '" + sample.contractId() + "'", "服务端正式摘要");
        assertJsonContains("ia_ai_application_result", "structured_payload_json", "result_id = '" + resultId + "'", "\"writeback_status\":\"WRITTEN\"", writebackRecordId, rankingSnapshotId);
        assertRowCount("ia_ai_audit_event", "action_type = 'RESULT_WRITEBACK_WRITTEN' and trace_id = 'trace-writeback-summary'", 1);
    }

    @Test
    void writesRiskPartialPublishWithPublicationStatusAndExtractionLowConfidenceAsAuxiliaryOnly() throws Exception {
        PreparedInputs sample = createPreparedInputs("风险提取回写合同", "dept-writeback-a", "trace-writeback-risk");
        String risk = createFormalRanking(sample, "RISK_ANALYSIS", "idem-writeback-risk", "trace-writeback-risk-rank", "PARTIAL_PUBLISH", """
                {"risk_level":"HIGH","risk_item_list":[{"risk_item_id":"payment-delay","risk_level":"HIGH","publish_status":"PARTIAL","confidence":0.82}],"clause_gap_summary":{"missing":["late_fee"]},"recommendation_summary":{"action":"补充违约金"},"evidence_reference":[{"citation_ref":"risk:1"}],"confidence":0.82}
                """);
        String riskResultId = jsonString(risk, "result_id");
        approveResultConfirmation(riskResultId, "trace-writeback-risk-approval");

        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_RISK_VIEW","target_id":"%s:risk:payment","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-risk"}
                """.formatted(riskResultId, sample.contractId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.writeback_status").value("WRITTEN"));
        assertJsonContains("contract_ai_risk_view", "risk_item_list_json", "contract_id = '" + sample.contractId() + "'", "\"publish_status\":\"PARTIAL\"");

        String extraction = createFormalRanking(sample, "DIFF_EXTRACTION", "idem-writeback-extraction", "trace-writeback-extraction-rank", "PARTIAL_PUBLISH", """
                {"comparison_mode":"TEMPLATE_TO_CONTRACT","extracted_field_list":[{"field_path":"amount","field_value":"100000","confidence":0.41},{"field_path":"delivery_date","field_value":"","confidence":0.0,"missing_status":"EXTRACTION_FAILED"}],"confidence_summary":{"amount":0.41,"delivery_date":0.0},"clause_match_summary":{},"diff_summary":{},"confidence":0.41}
                """);
        String extractionResultId = jsonString(extraction, "result_id");
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_EXTRACTION_VIEW","target_id":"%s:field:amount","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-extraction"}
                """.formatted(extractionResultId, sample.contractId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.writeback_status").value("WRITTEN"));
        assertRowCount("contract_ai_extraction_view", "contract_id = '" + sample.contractId() + "' and field_path = 'amount' and default_display_flag = false and confidence_status = 'LOW_CONFIDENCE'", 1);
        assertRowCount("contract_ai_extraction_view", "contract_id = '" + sample.contractId() + "' and field_path = 'delivery_date' and default_display_flag = false and confidence_status = 'LOW_CONFIDENCE'", 1);
    }

    @Test
    void rejectsWritebackWhenTargetContractDoesNotMatchFormalResultContract() throws Exception {
        PreparedInputs contractA = createPreparedInputs("来源合同A", "dept-writeback-a", "trace-writeback-contract-a");
        PreparedInputs contractB = createPreparedInputs("目标合同B", "dept-writeback-a", "trace-writeback-contract-b");
        String ranking = createFormalRanking(contractA, "SUMMARY", "idem-writeback-cross-contract", "trace-writeback-cross-contract-rank", "PUBLISH", """
                {"summary_text":"A 合同摘要不得写入 B 合同","summary_scope":"FULL","summary_digest":"sha256:cross-contract","confidence":0.93}
                """);
        String resultId = jsonString(ranking, "result_id");

        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-cross-contract"}
                """.formatted(resultId, contractB.contractId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("RESULT_WRITEBACK_TARGET_CONTRACT_MISMATCH"));

        assertRowCount("ia_writeback_record", "result_id = '" + resultId + "'", 0);
        assertRowCount("contract_ai_summary_view", "contract_id = '" + contractB.contractId() + "'", 0);
    }

    @Test
    void rejectsInvalidPartialPublishPayloadsAndWithheldRiskItemsStayOutOfTargetView() throws Exception {
        PreparedInputs sample = createPreparedInputs("部分发布校验合同", "dept-writeback-a", "trace-writeback-partial-validation");
        String summary = createFormalRanking(sample, "SUMMARY", "idem-writeback-partial-summary", "trace-writeback-partial-summary-rank", "PARTIAL_PUBLISH", """
                {"summary_text":"缺少正式缺口说明的摘要","summary_scope":"PARTIAL","summary_digest":"sha256:partial-summary","confidence":0.81}
                """);
        String summaryResultId = jsonString(summary, "result_id");
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-partial-summary"}
                """.formatted(summaryResultId, sample.contractId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("RESULT_WRITEBACK_SUMMARY_GAP_REASON_REQUIRED"));
        assertRowCount("contract_ai_summary_view", "contract_id = '" + sample.contractId() + "'", 0);

        String structurallyInvalidSummary = createFormalRanking(sample, "SUMMARY", "idem-writeback-partial-summary-structure", "trace-writeback-partial-summary-structure-rank", "PARTIAL_PUBLISH", """
                {"summary_text":"[缺口说明] 已说明缺口但缺少分段和引用。","summary_scope":"PARTIAL","partial_publish_reason":"证据覆盖不足","summary_digest":"sha256:partial-summary-structure","confidence":0.81}
                """);
        String structurallyInvalidSummaryResultId = jsonString(structurallyInvalidSummary, "result_id");
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-partial-summary-structure"}
                """.formatted(structurallyInvalidSummaryResultId, sample.contractId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("RESULT_WRITEBACK_SUMMARY_PARTIAL_STRUCTURE_REQUIRED"));
        assertRowCount("contract_ai_summary_view", "contract_id = '" + sample.contractId() + "'", 0);

        String blankReasonSummary = createFormalRanking(sample, "SUMMARY", "idem-writeback-partial-summary-blank-reason", "trace-writeback-partial-summary-blank-reason-rank", "PARTIAL_PUBLISH", """
                {"summary_text":"[缺口说明] 缺口原因不能为空白。","summary_scope":"PARTIAL","partial_publish_reason":"   ","section_list":[{"section":"付款","text":"分阶段付款"}],"citation_reference":[{"citation_ref":"doc:partial:reason"}],"summary_digest":"sha256:partial-summary-blank-reason","confidence":0.81}
                """);
        String blankReasonSummaryResultId = jsonString(blankReasonSummary, "result_id");
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-partial-summary-blank-reason"}
                """.formatted(blankReasonSummaryResultId, sample.contractId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("RESULT_WRITEBACK_SUMMARY_GAP_REASON_REQUIRED"));
        assertRowCount("contract_ai_summary_view", "contract_id = '" + sample.contractId() + "'", 0);

        String blankSectionSummary = createFormalRanking(sample, "SUMMARY", "idem-writeback-partial-summary-blank-section", "trace-writeback-partial-summary-blank-section-rank", "PARTIAL_PUBLISH", """
                {"summary_text":"[缺口说明] 分段内容不能为空白。","summary_scope":"PARTIAL","partial_publish_reason":"证据覆盖不足","section_list":[{"section":"   ","text":"   "}],"citation_reference":[{"citation_ref":"doc:partial:section"}],"summary_digest":"sha256:partial-summary-blank-section","confidence":0.81}
                """);
        String blankSectionSummaryResultId = jsonString(blankSectionSummary, "result_id");
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-partial-summary-blank-section"}
                """.formatted(blankSectionSummaryResultId, sample.contractId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("RESULT_WRITEBACK_SUMMARY_PARTIAL_STRUCTURE_REQUIRED"));
        assertRowCount("contract_ai_summary_view", "contract_id = '" + sample.contractId() + "'", 0);

        String blankCitationSummary = createFormalRanking(sample, "SUMMARY", "idem-writeback-partial-summary-blank-citation", "trace-writeback-partial-summary-blank-citation-rank", "PARTIAL_PUBLISH", """
                {"summary_text":"[缺口说明] 引用锚点不能为空白。","summary_scope":"PARTIAL","partial_publish_reason":"证据覆盖不足","section_list":[{"section":"付款","text":"分阶段付款"}],"citation_reference":[{"citation_ref":"   "}],"summary_digest":"sha256:partial-summary-blank-citation","confidence":0.81}
                """);
        String blankCitationSummaryResultId = jsonString(blankCitationSummary, "result_id");
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-partial-summary-blank-citation"}
                """.formatted(blankCitationSummaryResultId, sample.contractId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("RESULT_WRITEBACK_SUMMARY_PARTIAL_STRUCTURE_REQUIRED"));
        assertRowCount("contract_ai_summary_view", "contract_id = '" + sample.contractId() + "'", 0);

        String invalidRisk = createFormalRanking(sample, "RISK_ANALYSIS", "idem-writeback-risk-missing-status", "trace-writeback-risk-missing-status-rank", "PARTIAL_PUBLISH", """
                {"risk_level":"MEDIUM","risk_item_list":[{"risk_item_id":"missing-status","risk_level":"MEDIUM","confidence":0.80}],"clause_gap_summary":{"missing":["penalty"]},"recommendation_summary":{"action":"补充条款"},"evidence_reference":[{"citation_ref":"risk:missing-status"}],"confidence":0.80}
                """);
        String invalidRiskResultId = jsonString(invalidRisk, "result_id");
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_RISK_VIEW","target_id":"%s:risk:missing-status","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-risk-missing-status"}
                """.formatted(invalidRiskResultId, sample.contractId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("RESULT_WRITEBACK_RISK_PUBLISH_STATUS_INVALID"));
        assertRowCount("contract_ai_risk_view", "risk_view_id = '" + sample.contractId() + ":risk:missing-status'", 0);

        String withheldRisk = createFormalRanking(sample, "RISK_ANALYSIS", "idem-writeback-risk-withheld", "trace-writeback-risk-withheld-rank", "PARTIAL_PUBLISH", """
                {"risk_level":"MEDIUM","risk_item_list":[{"risk_item_id":"visible-risk","risk_level":"MEDIUM","publish_status":"FULL","confidence":0.86},{"risk_item_id":"withheld-risk","risk_level":"MEDIUM","publish_status":"WITHHELD","confidence":0.88}],"clause_gap_summary":{"missing":["secret-risk-redacted"]},"recommendation_summary":{"action":"仅发布可公开风险"},"evidence_reference":[{"citation_ref":"risk:withheld"}],"confidence":0.86}
                """);
        String withheldRiskResultId = jsonString(withheldRisk, "result_id");
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_RISK_VIEW","target_id":"%s:risk:withheld","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-risk-withheld"}
                """.formatted(withheldRiskResultId, sample.contractId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.writeback_status").value("WRITTEN"));
        assertJsonContains("contract_ai_risk_view", "risk_item_list_json", "risk_view_id = '" + sample.contractId() + ":risk:withheld'", "visible-risk");
        String riskItems = jdbcTemplate.queryForObject("select risk_item_list_json from contract_ai_risk_view where risk_view_id = ?", String.class, sample.contractId() + ":risk:withheld");
        assertThat(riskItems).doesNotContain("withheld-risk", "WITHHELD");

        String allWithheldRisk = createFormalRanking(sample, "RISK_ANALYSIS", "idem-writeback-risk-all-withheld", "trace-writeback-risk-all-withheld-rank", "PARTIAL_PUBLISH", """
                {"risk_level":"MEDIUM","risk_item_list":[{"risk_item_id":"hidden-a","risk_level":"MEDIUM","publish_status":"WITHHELD","confidence":0.86},{"risk_item_id":"hidden-b","risk_level":"LOW","publish_status":"WITHHELD","confidence":0.80}],"clause_gap_summary":{"missing":["all-hidden"]},"recommendation_summary":{"action":"不公开风险"},"evidence_reference":[{"citation_ref":"risk:all-withheld"}],"confidence":0.86}
                """);
        String allWithheldRiskResultId = jsonString(allWithheldRisk, "result_id");
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_RISK_VIEW","target_id":"%s:risk:all-withheld","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-risk-all-withheld"}
                """.formatted(allWithheldRiskResultId, sample.contractId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("RESULT_WRITEBACK_RISK_VISIBLE_ITEM_REQUIRED"));
        assertRowCount("contract_ai_risk_view", "risk_view_id = '" + sample.contractId() + ":risk:all-withheld'", 0);
    }

    @Test
    void keepsExtractionHistoryWhenSameFieldReceivesHigherConfidenceCandidate() throws Exception {
        PreparedInputs sample = createPreparedInputs("提取历史合同", "dept-writeback-a", "trace-writeback-extraction-history");
        String first = createFormalRanking(sample, "DIFF_EXTRACTION", "idem-writeback-extraction-history-first", "trace-writeback-extraction-history-first-rank", "PUBLISH", """
                {"comparison_mode":"TEMPLATE_TO_CONTRACT","extracted_field_list":[{"field_path":"amount","field_value":"100000","confidence":0.62}],"confidence_summary":{"amount":0.62},"clause_match_summary":{},"diff_summary":{},"confidence":0.62,"source_count":1,"assembled_at":"2026-05-01T01:00:00Z"}
                """);
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_EXTRACTION_VIEW","target_id":"%s:field:amount","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-extraction-history-first"}
                """.formatted(jsonString(first, "result_id"), sample.contractId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.writeback_status").value("WRITTEN"));

        String second = createFormalRanking(sample, "DIFF_EXTRACTION", "idem-writeback-extraction-history-second", "trace-writeback-extraction-history-second-rank", "PUBLISH", """
                {"comparison_mode":"TEMPLATE_TO_CONTRACT","extracted_field_list":[{"field_path":"amount","field_value":"120000","confidence":0.88}],"confidence_summary":{"amount":0.88},"clause_match_summary":{},"diff_summary":{},"confidence":0.88,"source_count":2,"assembled_at":"2026-05-01T02:00:00Z"}
                """);
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_EXTRACTION_VIEW","target_id":"%s:field:amount","target_snapshot_version":1,"operator_id":"u-writeback","trace_id":"trace-writeback-extraction-history-second"}
                """.formatted(jsonString(second, "result_id"), sample.contractId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.writeback_status").value("WRITTEN"));

        assertRowCount("contract_ai_extraction_view", "contract_id = '" + sample.contractId() + "' and field_path = 'amount'", 2);
        assertRowCount("contract_ai_extraction_view", "contract_id = '" + sample.contractId() + "' and field_path = 'amount' and view_status = 'SUPERSEDED' and default_display_flag = false", 1);
        assertRowCount("contract_ai_extraction_view", "contract_id = '" + sample.contractId() + "' and field_path = 'amount' and view_status = 'CURRENT' and default_display_flag = true and confidence_score = 0.8800", 1);
    }

    @Test
    void blocksGateFailuresAndForbiddenFieldsBeforeFormalWrite() throws Exception {
        PreparedInputs sample = createPreparedInputs("阻断回写合同", "dept-writeback-a", "trace-writeback-block");
        String blocked = createLowQualitySummaryRanking(sample, "idem-writeback-blocked", "trace-writeback-blocked-rank");
        String blockedResultId = jsonString(blocked, "result_id");

        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-gate-denied","payload":{"summary_text":"不得回写"}}
                """.formatted(blockedResultId, sample.contractId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("RESULT_WRITEBACK_GATE_DENIED"));
        assertRowCount("ia_writeback_record", "result_id = '" + blockedResultId + "'", 0);

        String approved = createFormalRanking(sample, "SUMMARY", "idem-writeback-forbidden", "trace-writeback-forbidden-rank", "PUBLISH", """
                {"summary_text":"摘要","nested":{"contract_name":"越权改名"},"confidence":0.91}
                """);
        String approvedResultId = jsonString(approved, "result_id");
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-forbidden"}
                """.formatted(approvedResultId, sample.contractId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("RESULT_WRITEBACK_FORBIDDEN_FIELD"));
        assertRowCount("ia_writeback_record", "result_id = '" + approvedResultId + "'", 0);
    }

    @Test
    void handlesIdempotencyVersionSlotConfidenceHumanRejectedAndUnresolvedEqualConflicts() throws Exception {
        PreparedInputs sample = createPreparedInputs("冲突回写合同", "dept-writeback-a", "trace-writeback-conflict");
        String first = createFormalRanking(sample, "SUMMARY", "idem-writeback-conflict-first", "trace-writeback-conflict-first-rank", "PUBLISH", """
                {"summary_text":"高置信摘要","summary_scope":"FULL","summary_digest":"sha256:first","confidence":0.91,"source_count":5,"assembled_at":"2026-05-01T02:00:00Z"}
                """);
        String firstResultId = jsonString(first, "result_id");
        String firstBody = """
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-conflict-first"}
                """.formatted(firstResultId, sample.contractId());
        String firstResponse = postWriteback(firstBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.writeback_status").value("WRITTEN"))
                .andReturn().getResponse().getContentAsString();
        String firstRecordId = jsonString(firstResponse, "writeback_record_id");

        postWriteback(firstBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.writeback_record_id").value(firstRecordId));
        assertRowCount("ia_writeback_record", "result_id = '" + firstResultId + "' and target_type = 'CONTRACT_SUMMARY' and target_id = '" + sample.contractId() + "'", 1);

        String stale = createFormalRanking(sample, "SUMMARY", "idem-writeback-conflict-stale", "trace-writeback-conflict-stale-rank", "PUBLISH", """
                {"summary_text":"旧版本摘要","summary_digest":"sha256:stale","confidence":0.95}
                """);
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-version"}
                """.formatted(jsonString(stale, "result_id"), sample.contractId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.writeback_status").value("FAILED"))
                .andExpect(jsonPath("$.conflict_code").value("VERSION_CONFLICT"));

        String lower = createFormalRanking(sample, "SUMMARY", "idem-writeback-conflict-lower", "trace-writeback-conflict-lower-rank", "PUBLISH", """
                {"summary_text":"低置信摘要","summary_digest":"sha256:lower","confidence":0.40}
                """);
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":1,"operator_id":"u-writeback","trace_id":"trace-writeback-confidence"}
                """.formatted(jsonString(lower, "result_id"), sample.contractId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.writeback_status").value("SKIPPED"))
                .andExpect(jsonPath("$.conflict_code").value("CONFIDENCE_LOWER"));

        String slot = createFormalRanking(sample, "SUMMARY", "idem-writeback-conflict-slot", "trace-writeback-conflict-slot-rank", "PUBLISH", """
                {"summary_text":"低来源数摘要","summary_digest":"sha256:slot","confidence":0.91,"source_count":4,"assembled_at":"2026-05-01T03:00:00Z"}
                """);
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":1,"operator_id":"u-writeback","trace_id":"trace-writeback-slot"}
                """.formatted(jsonString(slot, "result_id"), sample.contractId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.writeback_status").value("SKIPPED"))
                .andExpect(jsonPath("$.conflict_code").value("SLOT_OCCUPIED"));

        String rejected = createFormalRanking(sample, "SUMMARY", "idem-writeback-conflict-human", "trace-writeback-conflict-human-rank", "PUBLISH", """
                {"summary_text":"人工否决摘要","confidence":0.99,"human_confirmation_required":true}
                """);
        String rejectedResultId = jsonString(rejected, "result_id");
        rejectResultConfirmation(rejectedResultId, "trace-writeback-human-rejected");
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-human"}
                """.formatted(rejectedResultId, sample.contractId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.writeback_status").value("FAILED"))
                .andExpect(jsonPath("$.conflict_code").value("HUMAN_REJECTED"));

        String equal = createFormalRanking(sample, "SUMMARY", "idem-writeback-conflict-equal", "trace-writeback-conflict-equal-rank", "PUBLISH", """
                {"summary_text":"相等摘要","summary_digest":"sha256:equal","confidence":0.91,"source_count":5,"assembled_at":"2026-05-01T02:00:00Z"}
                """);
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":1,"operator_id":"u-writeback","trace_id":"trace-writeback-equal"}
                """.formatted(jsonString(equal, "result_id"), sample.contractId()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.writeback_status").value("FAILED"))
                .andExpect(jsonPath("$.conflict_code").value("UNRESOLVED_EQUAL"))
                .andExpect(jsonPath("$.human_escalation_anchor.confirmation_type").value("RESULT_WRITEBACK_CONFLICT"));
    }

    @Test
    void rejectsFormalWritebackWithoutExplicitTargetSnapshotVersionBeforeCreatingRecord() throws Exception {
        PreparedInputs sample = createPreparedInputs("缺少版本合同", "dept-writeback-a", "trace-writeback-missing-version");
        String ranking = createFormalRanking(sample, "SUMMARY", "idem-writeback-missing-version", "trace-writeback-missing-version-rank", "PUBLISH", """
                {"summary_text":"缺少 target_snapshot_version 不得回写","summary_scope":"FULL","summary_digest":"sha256:missing-version","confidence":0.93}
                """);
        String resultId = jsonString(ranking, "result_id");

        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","operator_id":"u-writeback","trace_id":"trace-writeback-missing-version"}
                """.formatted(resultId, sample.contractId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("RESULT_WRITEBACK_TARGET_SNAPSHOT_VERSION_REQUIRED"));

        assertRowCount("ia_writeback_record", "result_id = '" + resultId + "'", 0);
        assertRowCount("contract_ai_summary_view", "contract_id = '" + sample.contractId() + "'", 0);
    }

    @Test
    void resultWritebackEntryPointsDeclareTransactionBoundary() throws Exception {
        Object controller = AopTestUtils.getTargetObject(applicationContext.getBean("coreChainService"));

        assertThat(controller.getClass().getDeclaredMethod("createResultWriteback", String.class, Map.class).isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(controller.getClass().getDeclaredMethod("recoverResultWritebackDeadLetter", String.class, String.class, Map.class).isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void exhaustsLockCompetitionRetriesOnlyAfterFifthAttemptIntoDeadLetterWithAuditTrace() throws Exception {
        PreparedInputs sample = createPreparedInputs("死信回写合同", "dept-writeback-a", "trace-writeback-deadletter");
        String ranking = createFormalRanking(sample, "SUMMARY", "idem-writeback-deadletter", "trace-writeback-deadletter-rank", "PUBLISH", """
                {"summary_text":"临时失败摘要","summary_digest":"sha256:deadletter","confidence":0.88}
                """);
        String resultId = jsonString(ranking, "result_id");
        jdbcTemplate.update("insert into ia_writeback_lock (lock_key, writeback_record_id, lock_status, expires_at, created_at, updated_at) values (?, ?, 'HELD', dateadd('MINUTE', 5, current_timestamp), current_timestamp, current_timestamp)", "CONTRACT_SUMMARY:" + sample.contractId(), "external-writer");

        String body = """
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-deadletter"}
                """.formatted(resultId, sample.contractId());
        for (int attempt = 1; attempt <= 4; attempt++) {
            postWriteback(body)
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.writeback_status").value("PENDING"))
                    .andExpect(jsonPath("$.retry_count").value(attempt));
        }
        String response = postWriteback(body)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.writeback_status").value("FAILED"))
                .andExpect(jsonPath("$.retry_count").value(5))
                .andExpect(jsonPath("$.dead_letter.dead_letter_status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        String writebackRecordId = jsonString(response, "writeback_record_id");

        assertRowCount("ia_writeback_record", "writeback_record_id = '" + writebackRecordId + "' and retry_count = 5 and writeback_status = 'FAILED'", 1);
        assertRowCount("ia_writeback_record", "writeback_record_id = '" + writebackRecordId + "' and failure_reason like '%锁竞争重试耗尽%'", 1);
        assertRowCount("ia_writeback_dead_letter", "writeback_record_id = '" + writebackRecordId + "' and dead_letter_status = 'OPEN'", 1);
        assertRowCount("ia_ai_audit_event", "action_type = 'RESULT_WRITEBACK_DEAD_LETTERED' and trace_id = 'trace-writeback-deadletter'", 1);

        String deadLetterId = jdbcTemplate.queryForObject("select dead_letter_id from ia_writeback_dead_letter where writeback_record_id = ?", String.class, writebackRecordId);
        jdbcTemplate.update("delete from ia_writeback_lock where lock_key = ?", "CONTRACT_SUMMARY:" + sample.contractId());
        mockMvc.perform(post("/api/intelligent-applications/result-writebacks/dead-letters/" + deadLetterId + "/recover")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operator_id":"u-writeback","trace_id":"trace-writeback-deadletter-recover"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.writeback_status").value("WRITTEN"))
                .andExpect(jsonPath("$.dead_letter_status").value("RECOVERED"));
        assertRowCount("contract_ai_summary_view", "contract_id = '" + sample.contractId() + "' and view_status = 'CURRENT'", 1);
        assertRowCount("ia_writeback_dead_letter", "dead_letter_id = '" + deadLetterId + "' and dead_letter_status = 'RECOVERED'", 1);
    }

    @Test
    void deadLetterRecoveryDoesNotDeleteAnotherWritersValidLock() throws Exception {
        PreparedInputs sample = createPreparedInputs("死信锁隔离合同", "dept-writeback-a", "trace-writeback-recover-lock");
        String ranking = createFormalRanking(sample, "SUMMARY", "idem-writeback-recover-lock", "trace-writeback-recover-lock-rank", "PUBLISH", """
                {"summary_text":"恢复时不得删除其他请求锁","summary_scope":"FULL","summary_digest":"sha256:recover-lock","confidence":0.88}
                """);
        String resultId = jsonString(ranking, "result_id");
        jdbcTemplate.update("insert into ia_writeback_lock (lock_key, writeback_record_id, lock_status, expires_at, created_at, updated_at) values (?, ?, 'HELD', dateadd('MINUTE', 5, current_timestamp), current_timestamp, current_timestamp)", "CONTRACT_SUMMARY:" + sample.contractId(), "external-writer");
        String body = """
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-recover-lock"}
                """.formatted(resultId, sample.contractId());
        for (int attempt = 1; attempt <= 5; attempt++) {
            postWriteback(body);
        }
        String writebackRecordId = jdbcTemplate.queryForObject("select writeback_record_id from ia_writeback_record where result_id = ?", String.class, resultId);
        String deadLetterId = jdbcTemplate.queryForObject("select dead_letter_id from ia_writeback_dead_letter where writeback_record_id = ?", String.class, writebackRecordId);

        mockMvc.perform(post("/api/intelligent-applications/result-writebacks/dead-letters/" + deadLetterId + "/recover")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operator_id":"u-writeback","trace_id":"trace-writeback-recover-lock-retry"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.writeback_status").value("PENDING"))
                .andExpect(jsonPath("$.dead_letter_status").value("OPEN"));
        assertRowCount("ia_writeback_lock", "lock_key = 'CONTRACT_SUMMARY:" + sample.contractId() + "' and writeback_record_id = 'external-writer'", 1);
        assertRowCount("contract_ai_summary_view", "contract_id = '" + sample.contractId() + "'", 0);

        jdbcTemplate.update("delete from ia_writeback_lock where lock_key = ?", "CONTRACT_SUMMARY:" + sample.contractId());
        mockMvc.perform(post("/api/intelligent-applications/result-writebacks/dead-letters/" + deadLetterId + "/recover")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operator_id":"u-writeback","trace_id":"trace-writeback-recover-lock-success"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.writeback_status").value("WRITTEN"))
                .andExpect(jsonPath("$.dead_letter_status").value("RECOVERED"));
    }

    @Test
    void unresolvedEqualConflictWithoutTraceIdReturnsHumanEscalationInsteadOfServerError() throws Exception {
        PreparedInputs sample = createPreparedInputs("空追踪人工升级合同", "dept-writeback-a", "trace-writeback-null-trace");
        String first = createFormalRanking(sample, "SUMMARY", "idem-writeback-null-trace-first", "trace-writeback-null-trace-first-rank", "PUBLISH", """
                {"summary_text":"基准摘要","summary_scope":"FULL","summary_digest":"sha256:null-trace-first","confidence":0.91,"source_count":3,"assembled_at":"2026-05-01T02:00:00Z"}
                """);
        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-null-trace-first"}
                """.formatted(jsonString(first, "result_id"), sample.contractId()))
                .andExpect(status().isCreated());
        String equal = createFormalRanking(sample, "SUMMARY", "idem-writeback-null-trace-equal", "trace-writeback-null-trace-equal-rank", "PUBLISH", """
                {"summary_text":"相等摘要","summary_scope":"FULL","summary_digest":"sha256:null-trace-equal","confidence":0.91,"source_count":3,"assembled_at":"2026-05-01T02:00:00Z"}
                """);

        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_SUMMARY","target_id":"%s","target_snapshot_version":1,"operator_id":"u-writeback"}
                """.formatted(jsonString(equal, "result_id"), sample.contractId()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.writeback_status").value("FAILED"))
                .andExpect(jsonPath("$.conflict_code").value("UNRESOLVED_EQUAL"))
                .andExpect(jsonPath("$.human_escalation_anchor.confirmation_type").value("RESULT_WRITEBACK_CONFLICT"));
    }

    @Test
    void writebackLockExpiresWithinThirtySeconds() {
        Object controller = AopTestUtils.getTargetObject(applicationContext.getBean("coreChainService"));

        Boolean acquired = ReflectionTestUtils.invokeMethod(controller, "acquireWritebackLock", "CONTRACT_SUMMARY:ttl-check", "writeback-ttl-check");

        assertThat(acquired).isTrue();
        Integer ttlSeconds = jdbcTemplate.queryForObject("select datediff('SECOND', created_at, expires_at) from ia_writeback_lock where lock_key = ?", Integer.class, "CONTRACT_SUMMARY:ttl-check");
        assertThat(ttlSeconds).isNotNull().isPositive().isLessThanOrEqualTo(30);
    }

    @Test
    void rejectsTargetTypeMismatchedWithFormalApplicationType() throws Exception {
        PreparedInputs sample = createPreparedInputs("目标类型校验合同", "dept-writeback-a", "trace-writeback-target-type");
        String ranking = createFormalRanking(sample, "SUMMARY", "idem-writeback-target-type", "trace-writeback-target-type-rank", "PUBLISH", """
                {"summary_text":"摘要","confidence":0.90}
                """);
        String resultId = jsonString(ranking, "result_id");

        postWriteback("""
                {"result_id":"%s","target_type":"CONTRACT_RISK_VIEW","target_id":"%s:risk:mismatch","target_snapshot_version":0,"operator_id":"u-writeback","trace_id":"trace-writeback-target-type"}
                """.formatted(resultId, sample.contractId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("RESULT_WRITEBACK_TARGET_TYPE_MISMATCH"));
    }

    private org.springframework.test.web.servlet.ResultActions postWriteback(String body) throws Exception {
        return mockMvc.perform(post("/api/intelligent-applications/result-writebacks")
                .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void approveResultConfirmation(String resultId, String traceId) {
        createDecisionForResult(resultId, "APPROVED", traceId);
    }

    private void rejectResultConfirmation(String resultId, String traceId) {
        jdbcTemplate.update("update ia_ai_application_result set confirmation_required_flag = true where result_id = ?", resultId);
        createDecisionForResult(resultId, "REJECTED", traceId);
    }

    private void createDecisionForResult(String resultId, String decision, String traceId) {
        String agentTaskId = jdbcTemplate.queryForObject("select agent_task_id from ia_ai_application_result where result_id = ?", String.class, resultId);
        String agentResultId = jdbcTemplate.queryForObject("select agent_result_id from ia_ai_application_result where result_id = ?", String.class, resultId);
        jdbcTemplate.update("""
                insert into ao_human_confirmation
                (confirmation_id, source_task_id, source_result_id, confirmation_type, business_module, object_type, object_id, confirmation_status,
                 requested_by, decision_result_json, trace_id, created_at, updated_at, decided_by, decided_at)
                values (?, ?, ?, 'AI_OUTPUT_REVIEW', 'intelligent-applications', 'CONTRACT', 'contract-human', ?, 'u-writeback', ?, ?, current_timestamp, current_timestamp, 'reviewer', current_timestamp)
                """, "ao-confirm-" + java.util.UUID.randomUUID(), agentTaskId, agentResultId, decision,
                "{\"decision\":\"" + decision + "\"}", traceId);
    }

    private PreparedInputs createPreparedInputs(String contractName, String ownerOrgUnitId, String traceId) throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"%s","owner_user_id":"u-writeback-owner","owner_org_unit_id":"%s","category_code":"WRITEBACK","category_name":"回写合同","clause_library_code":"clause-lib-writeback","template_library_code":"tpl-lib-writeback","trace_id":"%s"}
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
        String documentVersionId = jsonString(document, "document_version_id");
        String ocr = mockMvc.perform(post("/api/intelligent-applications/ocr/jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,OCR_CREATE")
                        .header("Idempotency-Key", "idem-ocr-" + traceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_version_id":"%s","job_purpose":"RESULT_WRITEBACK_INPUT","actor_id":"u-writeback","trace_id":"%s-ocr"}
                                """.formatted(documentVersionId, traceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String ocrResultId = jsonString(ocr, "ocr_result_aggregate_id");
        mockMvc.perform(post("/api/intelligent-applications/search/sources/refresh")
                        .header("X-CMP-Permissions", "SEARCH_INDEX_MANAGE,CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_types":["CONTRACT","DOCUMENT","OCR","CLAUSE"],"contract_id":"%s","document_version_id":"%s","ocr_result_aggregate_id":"%s","trace_id":"%s-search"}
                                """.formatted(contractId, documentVersionId, ocrResultId, traceId)))
                .andExpect(status().isAccepted());
        return new PreparedInputs(contractId, documentVersionId);
    }

    private String createRanking(PreparedInputs sample, String applicationType, String idempotencyKey, String traceId, boolean strongVeto) throws Exception {
        return mockMvc.perform(post("/api/intelligent-applications/candidates/ranking-jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-writeback-a")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"%s","contract_id":"%s","document_version_id":"%s","ranking_profile_code":"CANDIDATE_RANKING_BASELINE","quality_profile_code":"CANDIDATE_QUALITY_BASELINE","actor_id":"u-writeback","trace_id":"%s","rule_hit_list":[{"rule_version":"rule-version-%s","rule_code":"WRITEBACK_RULE","hit_status":"ACTIVE","severity":"HIGH","strong_veto":%s,"conflict_group":"writeback"}]}
                                """.formatted(applicationType, sample.contractId(), sample.documentVersionId(), traceId, traceId, strongVeto)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
    }

    private String createFormalRanking(PreparedInputs sample, String applicationType, String idempotencyKey, String traceId,
                                       String releaseDecision, String writebackPayloadJson) throws Exception {
        return mockMvc.perform(post("/api/intelligent-applications/candidates/ranking-jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-writeback-a")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"%s","contract_id":"%s","document_version_id":"%s","ranking_profile_code":"CANDIDATE_RANKING_BASELINE","quality_profile_code":"CANDIDATE_QUALITY_BASELINE","quality_inputs":{"force_release_decision":"%s"},"writeback_payload":%s,"actor_id":"u-writeback","trace_id":"%s","rule_hit_list":[{"rule_version":"rule-version-%s","rule_code":"WRITEBACK_RULE","hit_status":"ACTIVE","severity":"MEDIUM","strong_veto":false,"conflict_group":"writeback"}]}
                                """.formatted(applicationType, sample.contractId(), sample.documentVersionId(), releaseDecision, writebackPayloadJson, traceId, traceId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.quality_evaluation.release_decision").value(releaseDecision))
                .andReturn().getResponse().getContentAsString();
    }

    private String createPartialPublishRiskRanking(PreparedInputs sample, String idempotencyKey, String traceId) throws Exception {
        return mockMvc.perform(post("/api/intelligent-applications/candidates/ranking-jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-writeback-a")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"RISK_ANALYSIS","contract_id":"%s","document_version_id":"%s","ranking_profile_code":"CANDIDATE_RANKING_BASELINE","quality_profile_code":"CANDIDATE_QUALITY_BASELINE","quality_inputs":{"force_release_decision":"PARTIAL_PUBLISH"},"actor_id":"u-writeback","trace_id":"%s","rule_hit_list":[{"rule_version":"rule-version-%s","rule_code":"WRITEBACK_RULE","hit_status":"ACTIVE","severity":"MEDIUM","strong_veto":false,"conflict_group":"writeback"}]}
                                """.formatted(sample.contractId(), sample.documentVersionId(), traceId, traceId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.quality_evaluation.release_decision").value("PARTIAL_PUBLISH"))
                .andReturn().getResponse().getContentAsString();
    }

    private String createLowQualitySummaryRanking(PreparedInputs sample, String idempotencyKey, String traceId) throws Exception {
        return mockMvc.perform(post("/api/intelligent-applications/candidates/ranking-jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-writeback-a")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"SUMMARY","contract_id":"%s","document_version_id":"%s","ranking_profile_code":"CANDIDATE_RANKING_BASELINE","quality_profile_code":"CANDIDATE_QUALITY_BASELINE","actor_id":"u-writeback","trace_id":"%s","quality_inputs":{"evidence_coverage_ratio":0.20,"citation_validity_score":0.20,"anchor_complete":false},"rule_hit_list":[{"rule_version":"rule-version-%s","rule_code":"WRITEBACK_RULE","hit_status":"ACTIVE","severity":"HIGH","strong_veto":false,"conflict_group":"writeback"}]}
                                """.formatted(sample.contractId(), sample.documentVersionId(), traceId, traceId)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
    }

    private void deleteIfExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_name = ?", Integer.class, tableName.toUpperCase());
        if (count != null && count > 0) {
            jdbcTemplate.update("DELETE FROM " + tableName);
        }
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

    private void assertJsonContains(String tableName, String columnName, String whereClause, String... expectedFragments) {
        String value = jdbcTemplate.queryForObject("select " + columnName + " from " + tableName + " where " + whereClause, String.class);
        for (String fragment : expectedFragments) {
            assertThat(value).contains(fragment);
        }
    }

    private record PreparedInputs(String contractId, String documentVersionId) {
    }
}

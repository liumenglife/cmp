package com.cmp.platform.contractlifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ContractLifecyclePerformanceTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanLifecycleTables() {
        jdbcTemplate.update("DELETE FROM cl_lifecycle_audit_event");
        jdbcTemplate.update("DELETE FROM cl_lifecycle_timeline_event");
        jdbcTemplate.update("DELETE FROM cl_lifecycle_document_ref");
        jdbcTemplate.update("DELETE FROM cl_lifecycle_summary");
        jdbcTemplate.update("DELETE FROM cl_performance_node");
        jdbcTemplate.update("DELETE FROM cl_performance_record");
    }

    @Test
    void effectiveSignedContractCanStartPerformanceButUnsignedOrIneffectiveContractCannotCreateFormalRecord() throws Exception {
        LifecycleSample signed = createSignedContractSample("履约准入已签合同", "trace-cl-admission-signed");
        String approvedOnly = createApprovedContractSample("履约准入未签章合同", "trace-cl-admission-approved").contractId();
        String draft = createDraftContract("履约准入未生效合同", "trace-cl-admission-draft");

        mockMvc.perform(post("/api/contracts/{contract_id}/performance-records", signed.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_user_id":"u-perf-owner","owner_org_unit_id":"dept-perf","trace_id":"trace-cl-admission-start"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.performance_record_id").exists())
                .andExpect(jsonPath("$.contract_id").value(signed.contractId()))
                .andExpect(jsonPath("$.performance_status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.source_contract_status").value("SIGNED"))
                .andExpect(jsonPath("$.contract_master_copy").doesNotExist())
                .andExpect(jsonPath("$.document_version_chain_copy").doesNotExist());

        mockMvc.perform(post("/api/contracts/{contract_id}/performance-records", approvedOnly)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_user_id":"u-perf-owner","owner_org_unit_id":"dept-perf","trace_id":"trace-cl-admission-approved-deny"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("CONTRACT_NOT_EFFECTIVE_FOR_PERFORMANCE"));

        mockMvc.perform(post("/api/contracts/{contract_id}/performance-records", draft)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_user_id":"u-perf-owner","owner_org_unit_id":"dept-perf","trace_id":"trace-cl-admission-draft-deny"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("CONTRACT_NOT_EFFECTIVE_FOR_PERFORMANCE"));
    }

    @Test
    void performanceOverviewNodesEvidenceReferenceAndRiskSummaryStayOnSameContractId() throws Exception {
        LifecycleSample sample = createSignedContractSample("履约聚合节点合同", "trace-cl-node");
        String record = createPerformanceRecord(sample.contractId(), "trace-cl-node-record");
        String evidence = createEvidenceDocument(sample.contractId(), "trace-cl-node-evidence");
        String evidenceAssetId = jsonString(evidence, "document_asset_id");
        String evidenceVersionId = jsonString(evidence, "document_version_id");

        String node = mockMvc.perform(post("/api/contracts/{contract_id}/performance-nodes", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performance_record_id":"%s","node_type":"DELIVERY","node_name":"首批设备交付","milestone_code":"DELIVERY_ACCEPTED","planned_at":"2026-05-01","due_at":"2026-05-10","owner_user_id":"u-node-owner","owner_org_unit_id":"dept-node","evidence_document_asset_id":"%s","evidence_document_version_id":"%s","trace_id":"trace-cl-node-create"}
                                """.formatted(record, evidenceAssetId, evidenceVersionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.performance_node_id").exists())
                .andExpect(jsonPath("$.performance_record_id").value(record))
                .andExpect(jsonPath("$.contract_id").value(sample.contractId()))
                .andExpect(jsonPath("$.document_ref.document_role").value("PERFORMANCE_EVIDENCE"))
                .andExpect(jsonPath("$.document_ref.document_asset_id").value(evidenceAssetId))
                .andExpect(jsonPath("$.document_ref.document_version_id").value(evidenceVersionId))
                .andReturn().getResponse().getContentAsString();

        String nodeId = jsonString(node, "performance_node_id");
        mockMvc.perform(patch("/api/contracts/{contract_id}/performance-nodes/{node_id}", sample.contractId(), nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"node_status":"IN_PROGRESS","progress_percent":40,"risk_level":"MEDIUM","issue_count":1,"result_summary":"物流排队，存在轻微延迟风险","trace_id":"trace-cl-node-update-risk"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.node_status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.progress_percent").value(40))
                .andExpect(jsonPath("$.risk_level").value("MEDIUM"))
                .andExpect(jsonPath("$.performance_summary.risk_summary.risk_level").value("MEDIUM"));

        mockMvc.perform(get("/api/contracts/{contract_id}/performance-overview", sample.contractId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_id").value(sample.contractId()))
                .andExpect(jsonPath("$.performance_record.performance_record_id").value(record))
                .andExpect(jsonPath("$.performance_record.contract_id").value(sample.contractId()))
                .andExpect(jsonPath("$.nodes[0].performance_node_id").value(nodeId))
                .andExpect(jsonPath("$.nodes[0].contract_id").value(sample.contractId()))
                .andExpect(jsonPath("$.document_refs[0].document_role").value("PERFORMANCE_EVIDENCE"))
                .andExpect(jsonPath("$.risk_summary.risk_level").value("MEDIUM"))
                .andExpect(jsonPath("$.contract_master_copy").doesNotExist());
    }

    @Test
    void performanceProgressOverdueCompletionAndRiskWriteBackContractSummaryTimelineAndAudit() throws Exception {
        LifecycleSample sample = createSignedContractSample("履约回写合同", "trace-cl-writeback");
        createPerformanceRecord(sample.contractId(), "trace-cl-writeback-record");
        String node = mockMvc.perform(post("/api/contracts/{contract_id}/performance-nodes", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"node_type":"PAYMENT","node_name":"首付款回款","milestone_code":"PAYMENT_RECEIVED","planned_at":"2026-05-01","due_at":"2026-05-03","owner_user_id":"u-pay-owner","owner_org_unit_id":"dept-pay","trace_id":"trace-cl-writeback-node"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String nodeId = jsonString(node, "performance_node_id");

        mockMvc.perform(patch("/api/contracts/{contract_id}/performance-nodes/{node_id}", sample.contractId(), nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"node_status":"OVERDUE","progress_percent":70,"risk_level":"HIGH","issue_count":2,"result_summary":"已逾期，需要财务跟进","trace_id":"trace-cl-writeback-overdue"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.performance_summary.performance_status").value("AT_RISK"))
                .andExpect(jsonPath("$.performance_summary.overdue_node_count").value(1))
                .andExpect(jsonPath("$.performance_summary.risk_summary.risk_level").value("HIGH"));

        mockMvc.perform(patch("/api/contracts/{contract_id}/performance-nodes/{node_id}", sample.contractId(), nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"node_status":"COMPLETED","progress_percent":100,"risk_level":"LOW","issue_count":0,"actual_at":"2026-05-04","result_summary":"首付款已完成","trace_id":"trace-cl-writeback-complete"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.performance_summary.performance_status").value("COMPLETED"))
                .andExpect(jsonPath("$.performance_summary.progress_percent").value(100))
                .andExpect(jsonPath("$.performance_summary.risk_summary.risk_level").value("LOW"));

        mockMvc.perform(get("/api/contracts/{contract_id}", sample.contractId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_master.contract_id").value(sample.contractId()))
                .andExpect(jsonPath("$.contract_master.contract_status").value("PERFORMED"))
                .andExpect(jsonPath("$.contract_master.performance_summary.performance_status").value("COMPLETED"))
                .andExpect(jsonPath("$.contract_master.performance_summary.latest_milestone_code").value("PERFORMANCE_COMPLETED"))
                .andExpect(jsonPath("$.lifecycle_summary.performance_summary.performance_status").value("COMPLETED"))
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'PERFORMANCE_NODE_OVERDUE')]", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'PERFORMANCE_COMPLETED')]", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'PERFORMANCE_RISK_CHANGED')]", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void performanceRecordNodeSummaryAndDocumentRefAreDurableFacts() throws Exception {
        LifecycleSample sample = createSignedContractSample("履约持久事实合同", "trace-cl-durable");
        String recordId = createPerformanceRecord(sample.contractId(), "trace-cl-durable-record");
        String evidence = createEvidenceDocument(sample.contractId(), "trace-cl-durable-evidence");
        String evidenceAssetId = jsonString(evidence, "document_asset_id");
        String evidenceVersionId = jsonString(evidence, "document_version_id");

        String node = mockMvc.perform(post("/api/contracts/{contract_id}/performance-nodes", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performance_record_id":"%s","node_type":"DELIVERY","node_name":"首批交付","milestone_code":"DELIVERY_ACCEPTED","planned_at":"2026-05-01","due_at":"2026-05-10","owner_user_id":"u-node-owner","owner_org_unit_id":"dept-node","evidence_document_asset_id":"%s","evidence_document_version_id":"%s","trace_id":"trace-cl-durable-node"}
                                """.formatted(recordId, evidenceAssetId, evidenceVersionId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String nodeId = jsonString(node, "performance_node_id");

        assertRowCount("cl_performance_record", "performance_record_id = ? and contract_id = ? and performance_status = ?", 1, recordId, sample.contractId(), "IN_PROGRESS");
        assertRowCount("cl_performance_node", "performance_node_id = ? and performance_record_id = ? and contract_id = ? and node_status = ?", 1, nodeId, recordId, sample.contractId(), "PENDING");
        assertRowCount("cl_lifecycle_summary", "contract_id = ? and current_stage = ? and stage_status = ?", 1, sample.contractId(), "PERFORMANCE", "IN_PROGRESS");
        assertRowCount("cl_lifecycle_document_ref", "source_resource_type = ? and source_resource_id = ? and document_asset_id = ? and document_version_id = ?", 1, "PERFORMANCE_NODE", nodeId, evidenceAssetId, evidenceVersionId);
    }

    @Test
    void performanceNodeStateMachineRejectsInvalidStatusAndBlockedCompletion() throws Exception {
        LifecycleSample sample = createSignedContractSample("履约状态机合同", "trace-cl-state-machine");
        String recordId = createPerformanceRecord(sample.contractId(), "trace-cl-state-record");
        String node = mockMvc.perform(post("/api/contracts/{contract_id}/performance-nodes", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performance_record_id":"%s","node_type":"DELIVERY","node_name":"状态机节点","milestone_code":"DELIVERY_ACCEPTED","trace_id":"trace-cl-state-node"}
                                """.formatted(recordId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String nodeId = jsonString(node, "performance_node_id");

        mockMvc.perform(patch("/api/contracts/{contract_id}/performance-nodes/{node_id}", sample.contractId(), nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"node_status":"FORCED_DONE","progress_percent":100,"risk_level":"LOW","trace_id":"trace-cl-state-invalid"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("PERFORMANCE_NODE_STATUS_INVALID"));

        mockMvc.perform(patch("/api/contracts/{contract_id}/performance-nodes/{node_id}", sample.contractId(), nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"node_status":"COMPLETED","progress_percent":100,"risk_level":"HIGH","issue_count":1,"actual_at":"2026-05-05","trace_id":"trace-cl-state-risk-block"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("PERFORMANCE_COMPLETION_BLOCKED"));

        assertRowCount("cl_performance_node", "performance_node_id = ? and node_status = ? and risk_level = ? and progress_percent = ?", 1, nodeId, "PENDING", "LOW", 0);
        assertRowCount("cl_performance_record", "performance_record_id = ? and performance_status = ?", 1, recordId, "IN_PROGRESS");
    }

    @Test
    void lifecycleTimelineAndAuditAreSeparatedDurableQueryableFacts() throws Exception {
        LifecycleSample sample = createSignedContractSample("履约时间线审计合同", "trace-cl-timeline-audit");
        String recordId = createPerformanceRecord(sample.contractId(), "trace-cl-timeline-record");
        String node = mockMvc.perform(post("/api/contracts/{contract_id}/performance-nodes", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performance_record_id":"%s","node_type":"PAYMENT","node_name":"回款节点","milestone_code":"PAYMENT_RECEIVED","trace_id":"trace-cl-timeline-node"}
                                """.formatted(recordId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String nodeId = jsonString(node, "performance_node_id");

        mockMvc.perform(patch("/api/contracts/{contract_id}/performance-nodes/{node_id}", sample.contractId(), nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"node_status":"IN_PROGRESS","progress_percent":50,"risk_level":"MEDIUM","issue_count":1,"operator_user_id":"u-auditor","result_summary":"存在回款风险","trace_id":"trace-cl-timeline-risk"}
                                """))
                .andExpect(status().isOk());

        assertRowCount("cl_lifecycle_timeline_event", "contract_id = ? and event_type = ? and source_resource_type = ? and source_resource_id = ? and milestone_code = ? and dedupe_key is not null", 1,
                sample.contractId(), "PERFORMANCE_PROGRESS_UPDATED", "PERFORMANCE_NODE", nodeId, "PAYMENT_RECEIVED");
        assertRowCount("cl_lifecycle_audit_event", "contract_id = ? and event_type = ? and source_resource_type = ? and source_resource_id = ? and actor_user_id = ? and result_status = ? and dedupe_key is not null", 1,
                sample.contractId(), "PERFORMANCE_RISK_CHANGED", "PERFORMANCE_NODE", nodeId, "u-auditor", "SUCCESS");
        assertRowCount("cl_lifecycle_timeline_event", "event_type = ? and event_result is not null", 1, "PERFORMANCE_PROGRESS_UPDATED");
        assertRowCount("cl_lifecycle_audit_event", "event_type = ? and event_result is not null", 1, "PERFORMANCE_RISK_CHANGED");

        String timelineId = jdbcTemplate.queryForObject("SELECT timeline_event_id FROM cl_lifecycle_timeline_event WHERE contract_id = ? AND event_type = ?", String.class,
                sample.contractId(), "PERFORMANCE_PROGRESS_UPDATED");
        String auditId = jdbcTemplate.queryForObject("SELECT audit_event_id FROM cl_lifecycle_audit_event WHERE contract_id = ? AND event_type = ?", String.class,
                sample.contractId(), "PERFORMANCE_RISK_CHANGED");
        assertThat(timelineId).isNotEqualTo(auditId);
    }

    private String createPerformanceRecord(String contractId, String traceId) throws Exception {
        String record = mockMvc.perform(post("/api/contracts/{contract_id}/performance-records", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_user_id":"u-perf-owner","owner_org_unit_id":"dept-perf","trace_id":"%s"}
                                """.formatted(traceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonString(record, "performance_record_id");
    }

    private LifecycleSample createSignedContractSample(String contractName, String tracePrefix) throws Exception {
        ApprovedSample approved = createApprovedContractSample(contractName, tracePrefix);
        String request = mockMvc.perform(post("/api/contracts/{contract_id}/signatures/apply", approved.contractId())
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,SIGNATURE_APPLY")
                        .header("Idempotency-Key", "idem-" + tracePrefix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"main_document_asset_id":"%s","main_document_version_id":"%s","signature_mode":"ELECTRONIC","seal_scheme_id":"seal-standard","signer_list":[{"signer_type":"USER","signer_id":"u-signer-a"},{"signer_type":"USER","signer_id":"u-signer-b"}],"sign_order_mode":"SEQUENTIAL","trace_id":"%s-apply"}
                                """.formatted(approved.documentAssetId(), approved.documentVersionId(), tracePrefix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String signatureRequestId = jsonString(request, "signature_request_id");
        String session = mockMvc.perform(post("/api/signature-requests/{signature_request_id}/sessions", signatureRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sign_order_mode":"SEQUENTIAL","signer_list":[{"signer_type":"USER","signer_id":"u-signer-a"},{"signer_type":"USER","signer_id":"u-signer-b"}],"trace_id":"%s-session"}
                                """.formatted(tracePrefix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String sessionId = jsonString(session, "signature_session_id");
        sign(sessionId, "u-signer-a", 1, tracePrefix + "-sign-a");
        sign(sessionId, "u-signer-b", 2, tracePrefix + "-sign-b");
        mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/results", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"external_result_ref":"%s-result","signed_file_token":"%s-signed","verification_file_token":"%s-verify","verification_status":"PASSED","trace_id":"%s-result"}
                                """.formatted(tracePrefix, tracePrefix, tracePrefix, tracePrefix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_writeback_status").value("COMPLETED"));
        return new LifecycleSample(approved.contractId(), approved.documentAssetId(), approved.documentVersionId());
    }

    private ApprovedSample createApprovedContractSample(String contractName, String tracePrefix) throws Exception {
        String contractId = createDraftContract(contractName, tracePrefix + "-contract");
        String document = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"生命周期主正文.docx","source_channel":"MANUAL_UPLOAD","file_upload_token":"%s-document","trace_id":"%s-document"}
                                """.formatted(contractId, tracePrefix, tracePrefix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String documentAssetId = jsonString(document, "document_asset_id");
        String documentVersionId = jsonString(document, "document_version_id");
        String approval = mockMvc.perform(post("/api/workflow-engine/processes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"business_object_type":"CONTRACT","contract_id":"%s","document_asset_id":"%s","document_version_id":"%s","starter_user_id":"u-cl-owner","trace_id":"%s-approval-start"}
                                """.formatted(contractId, documentAssetId, documentVersionId, tracePrefix)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String processId = jsonString(approval, "process_id");
        mockMvc.perform(post("/api/workflow-engine/processes/{process_id}/results", processId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result":"APPROVED","operator_id":"u-cl-approver","trace_id":"%s-approval-result"}
                                """.formatted(tracePrefix)))
                .andExpect(status().isOk());
        return new ApprovedSample(contractId, documentAssetId, documentVersionId);
    }

    private String createDraftContract(String contractName, String tracePrefix) throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"%s","owner_user_id":"u-cl-owner","owner_org_unit_id":"dept-cl","trace_id":"%s"}
                                """.formatted(contractName, tracePrefix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonString(contract, "contract_id");
    }

    private String createEvidenceDocument(String contractId, String tracePrefix) throws Exception {
        return mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"PERFORMANCE_EVIDENCE","document_title":"履约凭证.pdf","source_channel":"MANUAL_UPLOAD","file_upload_token":"%s-evidence","trace_id":"%s-evidence"}
                                """.formatted(contractId, tracePrefix, tracePrefix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private void sign(String sessionId, String signerId, int sequence, String traceId) throws Exception {
        mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/callbacks", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"callback_source":"BUILT_IN_ENGINE","external_event_id":"%s-%s","event_sequence":%d,"event_type":"SIGNER_SIGNED","signer_id":"%s","trace_id":"%s"}
                                """.formatted(sessionId, signerId, sequence, signerId, traceId)))
                .andExpect(status().isOk());
    }

    private String jsonString(String json, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = json.indexOf(marker);
        assertThat(start).as("响应缺少字段: %s, body=%s", fieldName, json).isGreaterThanOrEqualTo(0);
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        return json.substring(valueStart, valueEnd);
    }

    private void assertRowCount(String tableName, String whereClause, int expected, Object... args) {
        Integer actual = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName + " WHERE " + whereClause, Integer.class, args);
        assertThat(actual).isEqualTo(expected);
    }

    private record ApprovedSample(String contractId, String documentAssetId, String documentVersionId) {
    }

    private record LifecycleSample(String contractId, String documentAssetId, String documentVersionId) {
    }
}

package com.cmp.platform.esignature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ESignatureSessionResultPaperRecordTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsSignatureSessionExpandsSignerSnapshotControlsOrderAndHandlesTimeout() throws Exception {
        SignatureSample sample = createAdmittedSignatureSample("签章会话合同", "trace-es-session");

        String session = mockMvc.perform(post("/api/signature-requests/{signature_request_id}/sessions", sample.signatureRequestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sign_order_mode":"SEQUENTIAL","expires_in_seconds":1,"signer_list":[{"signer_type":"USER","signer_id":"u-signer-a","signer_name":"甲方签署人","signer_org":"法务部","assignment_role":"PRIMARY_SIGNER"},{"signer_type":"USER","signer_id":"u-signer-b","signer_name":"乙方签署人","signer_org":"销售部","assignment_role":"COUNTERSIGNER"}],"trace_id":"trace-es-session-open"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.signature_session_id").exists())
                .andExpect(jsonPath("$.session_status").value("OPEN"))
                .andExpect(jsonPath("$.current_sign_step").value(1))
                .andExpect(jsonPath("$.pending_signer_count").value(2))
                .andExpect(jsonPath("$.assignment_list[0].assignment_status").value("READY"))
                .andExpect(jsonPath("$.assignment_list[1].assignment_status").value("WAITING"))
                .andExpect(jsonPath("$.assignment_list[0].signer_snapshot.signer_name").value("甲方签署人"))
                .andExpect(jsonPath("$.task_center_items[0].task_center_status").value("PUBLISHED"))
                .andExpect(jsonPath("$.notification_items[0].notification_status").value("SENT"))
                .andExpect(jsonPath("$.contract_master_copy").doesNotExist())
                .andExpect(jsonPath("$.document_version_chain_copy").doesNotExist())
                .andExpect(jsonPath("$.approval_instance_master_copy").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String sessionId = jsonString(session, "signature_session_id");
        mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/expire", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-es-session-expire\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_status").value("EXPIRED"))
                .andExpect(jsonPath("$.manual_intervention.required").value(true))
                .andExpect(jsonPath("$.manual_intervention.reason_code").value("SESSION_TIMEOUT"));

        mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/callbacks", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"callback_source":"BUILT_IN_ENGINE","external_event_id":"evt-expired-signer-a","event_sequence":1,"event_type":"SIGNER_SIGNED","signer_id":"u-signer-a","trace_id":"trace-es-session-expired-callback"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.callback_result").value("SESSION_NOT_ADVANCEABLE"))
                .andExpect(jsonPath("$.session_status").value("EXPIRED"))
                .andExpect(jsonPath("$.pending_signer_count").value(2))
                .andExpect(jsonPath("$.completed_signer_count").value(0));
    }

    @Test
    void callbackIsIdempotentRejectsOutOfOrderAndRoutesManualIntervention() throws Exception {
        SignatureSample sample = createAdmittedSignatureSample("签章回调合同", "trace-es-callback");
        String sessionId = createSession(sample.signatureRequestId());

        mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/callbacks", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"callback_source":"BUILT_IN_ENGINE","external_event_id":"evt-out-of-order","event_sequence":2,"event_type":"SIGNER_SIGNED","signer_id":"u-signer-b","trace_id":"trace-es-callback-out-of-order"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.callback_result").value("OUT_OF_ORDER_REJECTED"))
                .andExpect(jsonPath("$.manual_intervention.required").value(true))
                .andExpect(jsonPath("$.manual_intervention.reason_code").value("SIGNER_ORDER_VIOLATION"));

        mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/callbacks", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"callback_source":"BUILT_IN_ENGINE","external_event_id":"evt-out-of-order","event_sequence":2,"event_type":"SIGNER_SIGNED","signer_id":"u-signer-b","trace_id":"trace-es-callback-out-of-order-repeat"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callback_result").value("DUPLICATE_IGNORED"))
                .andExpect(jsonPath("$.session_status").value("FAILED"));

        mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/callbacks", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"callback_source":"BUILT_IN_ENGINE","external_event_id":"evt-signer-after-manual","event_sequence":1,"event_type":"SIGNER_SIGNED","signer_id":"u-signer-a","trace_id":"trace-es-callback-after-manual"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.callback_result").value("SESSION_NOT_ADVANCEABLE"))
                .andExpect(jsonPath("$.session_status").value("FAILED"))
                .andExpect(jsonPath("$.pending_signer_count").value(2))
                .andExpect(jsonPath("$.completed_signer_count").value(0));

        String normalSessionId = createSession(sample.signatureRequestId());
        String first = mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/callbacks", normalSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"callback_source":"BUILT_IN_ENGINE","external_event_id":"evt-signer-a","event_sequence":1,"event_type":"SIGNER_SIGNED","signer_id":"u-signer-a","trace_id":"trace-es-callback-a"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callback_result").value("ACCEPTED"))
                .andExpect(jsonPath("$.session_status").value("PARTIALLY_SIGNED"))
                .andExpect(jsonPath("$.current_sign_step").value(2))
                .andExpect(jsonPath("$.completed_signer_count").value(1))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/callbacks", normalSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"callback_source":"BUILT_IN_ENGINE","external_event_id":"evt-signer-a","event_sequence":1,"event_type":"SIGNER_SIGNED","signer_id":"u-signer-a","trace_id":"trace-es-callback-a-repeat"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callback_result").value("DUPLICATE_IGNORED"))
                .andExpect(jsonPath("$.completed_signer_count").value(1));

        assertThat(jsonInt(first, "completed_signer_count")).isEqualTo(1);

        String errorSessionId = createSession(sample.signatureRequestId());
        mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/callbacks", errorSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"callback_source":"BUILT_IN_ENGINE","external_event_id":"evt-engine-error","event_sequence":3,"event_type":"ENGINE_ERROR","signer_id":"u-signer-b","trace_id":"trace-es-callback-error"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.callback_result").value("MANUAL_INTERVENTION_REQUIRED"))
                .andExpect(jsonPath("$.manual_intervention.required").value(true))
                .andExpect(jsonPath("$.manual_intervention.reason_code").value("ENGINE_CALLBACK_EXCEPTION"));

        mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/callbacks", errorSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"callback_source":"BUILT_IN_ENGINE","external_event_id":"evt-engine-error","event_sequence":3,"event_type":"ENGINE_ERROR","signer_id":"u-signer-b","trace_id":"trace-es-callback-error-repeat"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callback_result").value("DUPLICATE_IGNORED"))
                .andExpect(jsonPath("$.session_status").value("FAILED"));
    }

    @Test
    void completedSignatureWritesBackDocumentCenterBeforeContractSummaryAndCanRebuildSummary() throws Exception {
        SignatureSample sample = createAdmittedSignatureSample("签章结果回写合同", "trace-es-result");
        String sessionId = createSession(sample.signatureRequestId());
        signBoth(sessionId);

        String result = mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/results", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"external_result_ref":"engine-result-001","signed_file_token":"signed-token-001","verification_file_token":"verify-token-001","verification_status":"PASSED","trace_id":"trace-es-result-writeback"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result_status").value("WRITEBACK_COMPLETED"))
                .andExpect(jsonPath("$.document_writeback_status").value("COMPLETED"))
                .andExpect(jsonPath("$.contract_writeback_status").value("COMPLETED"))
                .andExpect(jsonPath("$.document_binding_order[0].binding_role").value("SIGNED_MAIN"))
                .andExpect(jsonPath("$.document_binding_order[1].binding_role").value("VERIFICATION_ARTIFACT"))
                .andExpect(jsonPath("$.contract_signature_summary.signature_status").value("SIGNED"))
                .andExpect(jsonPath("$.contract_signature_summary.latest_signed_document_version_id").exists())
                .andExpect(jsonPath("$.contract_master_copy").doesNotExist())
                .andExpect(jsonPath("$.document_version_chain_copy").doesNotExist())
                .andExpect(jsonPath("$.approval_instance_master_copy").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String signedVersionId = jsonString(result, "latest_signed_document_version_id");
        mockMvc.perform(get("/api/document-center/versions/{document_version_id}", signedVersionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document_version_id").value(signedVersionId));

        mockMvc.perform(get("/api/contracts/{contract_id}", sample.contractId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_master.contract_status").value("SIGNED"))
                .andExpect(jsonPath("$.contract_master.signature_summary.signature_status").value("SIGNED"))
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'SIGNATURE_RESULT_WRITEBACK_COMPLETED')]").exists());

        mockMvc.perform(post("/api/contracts/{contract_id}/signatures/summary/rebuild", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-es-summary-rebuild\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signature_summary.signature_status").value("SIGNED"))
                .andExpect(jsonPath("$.signature_summary.rebuild_source").value("SIGNATURE_RESULT"));

        SignatureSample failing = createAdmittedSignatureSample("签章合同回写失败合同", "trace-es-contract-writeback-fail");
        String failingSessionId = createSession(failing.signatureRequestId());
        signBoth(failingSessionId);
        mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/results", failingSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"external_result_ref":"engine-result-fail","signed_file_token":"signed-token-fail","verification_file_token":"verify-token-fail","verification_status":"PASSED","simulate_contract_writeback_failure":true,"trace_id":"trace-es-contract-writeback-fail"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.document_writeback_status").value("COMPLETED"))
                .andExpect(jsonPath("$.contract_writeback_status").value("PENDING_COMPENSATION"))
                .andExpect(jsonPath("$.compensation_task.task_type").value("ES_CONTRACT_WRITEBACK"));
    }

    @Test
    void paperRecordUsesDocumentCenterScanReferenceAndChecksCoexistenceWithElectronicResult() throws Exception {
        SignatureSample sample = createAdmittedSignatureSample("纸质备案合同", "trace-es-paper");
        String scan = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"PAPER_SCAN","document_title":"纸质签署扫描件.pdf","source_channel":"MANUAL_UPLOAD","file_upload_token":"trace-es-paper-scan","trace_id":"trace-es-paper-scan"}
                                """.formatted(sample.contractId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/contracts/{contract_id}/paper-signature-records", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paper_document_asset_id":"%s","paper_document_version_id":"%s","recorded_sign_date":"2026-04-28","confirmed_by":"u-paper-admin","allow_coexist_electronic":false,"trace_id":"trace-es-paper-record"}
                                """.formatted(jsonString(scan, "document_asset_id"), jsonString(scan, "document_version_id"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.record_status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paper_scan_binding.binding_role").value("PAPER_SCAN"))
                .andExpect(jsonPath("$.signature_summary.signature_mode").value("PAPER_RECORD"))
                .andExpect(jsonPath("$.coexistence.allowed").value(true))
                .andExpect(jsonPath("$.contract_master_copy").doesNotExist())
                .andExpect(jsonPath("$.document_version_chain_copy").doesNotExist());

        String sessionId = createSession(sample.signatureRequestId());
        signBoth(sessionId);
        mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/results", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"external_result_ref":"engine-result-paper-coexist","signed_file_token":"signed-token-paper-coexist","verification_file_token":"verify-token-paper-coexist","verification_status":"PASSED","trace_id":"trace-es-paper-electronic-result"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/contracts/{contract_id}/paper-signature-records", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paper_document_asset_id":"%s","paper_document_version_id":"%s","recorded_sign_date":"2026-04-28","confirmed_by":"u-paper-admin","allow_coexist_electronic":false,"trace_id":"trace-es-paper-conflict"}
                                """.formatted(jsonString(scan, "document_asset_id"), jsonString(scan, "document_version_id"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("PAPER_RECORD_CONFLICTS_WITH_ELECTRONIC_RESULT"));
    }

    private SignatureSample createAdmittedSignatureSample(String contractName, String tracePrefix) throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"%s","owner_user_id":"u-es-owner","owner_org_unit_id":"dept-es","trace_id":"%s-contract"}
                                """.formatted(contractName, tracePrefix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");

        String document = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"签章主正文.docx","source_channel":"MANUAL_UPLOAD","file_upload_token":"%s-document","trace_id":"%s-document"}
                                """.formatted(contractId, tracePrefix, tracePrefix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String documentAssetId = jsonString(document, "document_asset_id");
        String documentVersionId = jsonString(document, "document_version_id");

        String approval = mockMvc.perform(post("/api/workflow-engine/processes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"business_object_type":"CONTRACT","contract_id":"%s","document_asset_id":"%s","document_version_id":"%s","starter_user_id":"u-es-owner","trace_id":"%s-approval-start"}
                                """.formatted(contractId, documentAssetId, documentVersionId, tracePrefix)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String processId = jsonString(approval, "process_id");

        mockMvc.perform(post("/api/workflow-engine/processes/{process_id}/results", processId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result":"APPROVED","operator_id":"u-es-approver","trace_id":"%s-approval-result"}
                                """.formatted(tracePrefix)))
                .andExpect(status().isOk());

        String request = mockMvc.perform(post("/api/contracts/{contract_id}/signatures/apply", contractId)
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,SIGNATURE_APPLY")
                        .header("Idempotency-Key", "idem-" + tracePrefix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"main_document_asset_id":"%s","main_document_version_id":"%s","signature_mode":"ELECTRONIC","seal_scheme_id":"seal-standard","signer_list":[{"signer_type":"USER","signer_id":"u-signer-a"},{"signer_type":"USER","signer_id":"u-signer-b"}],"sign_order_mode":"SEQUENTIAL","trace_id":"%s-apply"}
                                """.formatted(documentAssetId, documentVersionId, tracePrefix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return new SignatureSample(contractId, documentAssetId, documentVersionId, jsonString(request, "signature_request_id"));
    }

    private String createSession(String signatureRequestId) throws Exception {
        String session = mockMvc.perform(post("/api/signature-requests/{signature_request_id}/sessions", signatureRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sign_order_mode":"SEQUENTIAL","signer_list":[{"signer_type":"USER","signer_id":"u-signer-a","signer_name":"甲方签署人","signer_org":"法务部","assignment_role":"PRIMARY_SIGNER"},{"signer_type":"USER","signer_id":"u-signer-b","signer_name":"乙方签署人","signer_org":"销售部","assignment_role":"COUNTERSIGNER"}],"trace_id":"trace-es-session-helper"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonString(session, "signature_session_id");
    }

    private void signBoth(String sessionId) throws Exception {
        mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/callbacks", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"callback_source":"BUILT_IN_ENGINE","external_event_id":"%s-a","event_sequence":1,"event_type":"SIGNER_SIGNED","signer_id":"u-signer-a","trace_id":"trace-es-sign-a"}
                                """.formatted(sessionId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/callbacks", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"callback_source":"BUILT_IN_ENGINE","external_event_id":"%s-b","event_sequence":2,"event_type":"SIGNER_SIGNED","signer_id":"u-signer-b","trace_id":"trace-es-sign-b"}
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_status").value("COMPLETED"));
    }

    private String jsonString(String json, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = json.indexOf(marker);
        assertThat(start).as("响应缺少字段: %s, body=%s", fieldName, json).isGreaterThanOrEqualTo(0);
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        return json.substring(valueStart, valueEnd);
    }

    private int jsonInt(String json, String fieldName) {
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

    private record SignatureSample(String contractId, String documentAssetId, String documentVersionId, String signatureRequestId) {
    }
}

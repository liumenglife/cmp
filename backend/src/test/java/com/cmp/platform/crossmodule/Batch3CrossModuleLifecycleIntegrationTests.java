package com.cmp.platform.crossmodule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class Batch3CrossModuleLifecycleIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contractLifecycleModulesShareOneContractDocumentApprovalTruthAndCloseArchiveRestrictionsAuditLoop() throws Exception {
        ApprovedContract approved = createApprovedContract("第三批跨模块综合验证合同", "trace-xm-e2e");
        String contractId = approved.contractId();

        String signatureRequest = mockMvc.perform(post("/api/contracts/{contract_id}/signatures/apply", contractId)
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,SIGNATURE_APPLY")
                        .header("Idempotency-Key", "idem-trace-xm-e2e")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"main_document_asset_id":"%s","main_document_version_id":"%s","signature_mode":"ELECTRONIC","seal_scheme_id":"seal-cross-module","signer_list":[{"signer_type":"USER","signer_id":"u-signer-a"},{"signer_type":"USER","signer_id":"u-signer-b"}],"sign_order_mode":"SEQUENTIAL","trace_id":"trace-xm-sign-apply"}
                                """.formatted(approved.documentAssetId(), approved.documentVersionId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contract_id").value(contractId))
                .andExpect(jsonPath("$.input_document_binding.document_version_id").value(approved.documentVersionId()))
                .andExpect(jsonPath("$.application_snapshot.approval_summary.process_id").value(approved.approvalProcessId()))
                .andExpect(jsonPath("$.contract_master_copy").doesNotExist())
                .andExpect(jsonPath("$.document_version_chain_copy").doesNotExist())
                .andExpect(jsonPath("$.approval_instance_master_copy").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String signatureRequestId = jsonString(signatureRequest, "signature_request_id");
        String sessionId = createSignatureSession(signatureRequestId, "trace-xm-sign-session");
        sign(sessionId, "u-signer-a", 1, "trace-xm-sign-a");
        sign(sessionId, "u-signer-b", 2, "trace-xm-sign-b");

        String signatureResult = mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/results", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"external_result_ref":"xm-engine-result","signed_file_token":"xm-signed-token","verification_file_token":"xm-verify-token","verification_status":"PASSED","trace_id":"trace-xm-sign-result"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_signature_summary.signature_status").value("SIGNED"))
                .andReturn().getResponse().getContentAsString();
        String signedVersionId = jsonString(signatureResult, "latest_signed_document_version_id");

        String access = mockMvc.perform(post("/api/encrypted-documents/access")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_asset_id":"%s","document_version_id":"%s","access_scene":"PREVIEW","access_subject_type":"USER","access_subject_id":"u-preview","actor_department_id":"dept-cross","trace_id":"trace-xm-decrypt-access"}
                                """.formatted(approved.documentAssetId(), approved.documentVersionId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contract_id").value(contractId))
                .andExpect(jsonPath("$.controlled_read_handle.plaintext_export_allowed").value(false))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/encrypted-documents/access/{decrypt_access_id}/consume", jsonString(access, "decrypt_access_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"access_ticket":"%s","trace_id":"trace-xm-decrypt-consume"}
                                """.formatted(jsonString(access, "access_ticket"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consume_result").value("CONSUMED"));

        grantDownloadAuthorization(contractId, "u-download", "trace-xm-download-auth");
        mockMvc.perform(post("/api/encrypted-documents/download-jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_asset_id":"%s","document_version_id":"%s","requested_by":"u-download","requested_department_id":"dept-cross","download_reason":"跨模块验收导出","request_idempotency_key":"trace-xm-download-job","trace_id":"trace-xm-download-job"}
                                """.formatted(approved.documentAssetId(), approved.documentVersionId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.export_artifact.document_center_truth_replaced").value(false));

        String performanceRecordId = jsonString(mockMvc.perform(post("/api/contracts/{contract_id}/performance-records", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_user_id":"u-performance","owner_org_unit_id":"dept-cross","trace_id":"trace-xm-performance-start"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "performance_record_id");
        String nodeId = jsonString(mockMvc.perform(post("/api/contracts/{contract_id}/performance-nodes", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performance_record_id":"%s","node_type":"DELIVERY","node_name":"跨模块交付节点","milestone_code":"DELIVERY_ACCEPTED","trace_id":"trace-xm-performance-node"}
                                """.formatted(performanceRecordId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "performance_node_id");
        mockMvc.perform(patch("/api/contracts/{contract_id}/performance-nodes/{node_id}", contractId, nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"node_status":"COMPLETED","progress_percent":100,"risk_level":"LOW","issue_count":0,"actual_at":"2026-05-05","trace_id":"trace-xm-performance-complete"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.performance_summary.performance_status").value("COMPLETED"));

        String changeDocument = createDocument(contractId, "CHANGE_AGREEMENT", "跨模块补充协议.pdf", "trace-xm-change-doc");
        String change = mockMvc.perform(post("/api/contracts/{contract_id}/changes", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"change_type":"AMOUNT","change_reason":"跨模块变更","change_summary":"金额调整","effective_date":"2026-06-01","supplemental_document_asset_id":"%s","supplemental_document_version_id":"%s","workflow_instance_id":"wf-xm-change","operator_user_id":"u-change","trace_id":"trace-xm-change-apply"}
                                """.formatted(jsonString(changeDocument, "document_asset_id"), jsonString(changeDocument, "document_version_id"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/contracts/{contract_id}/changes/{change_id}/approval-results", contractId, jsonString(change, "change_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approval_result":"APPROVED","workflow_instance_id":"wf-xm-change","approved_at":"2026-06-01T10:00:00Z","result_summary":"跨模块变更生效","operator_user_id":"u-change-approver","trace_id":"trace-xm-change-approved"}
                                """))
                .andExpect(status().isOk());

        String terminationDocument = createDocument(contractId, "TERMINATION_AGREEMENT", "跨模块终止协议.pdf", "trace-xm-term-doc");
        String termination = mockMvc.perform(post("/api/contracts/{contract_id}/terminations", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"termination_type":"MUTUAL_AGREEMENT","termination_reason":"跨模块终止","termination_summary":"结算后归档","requested_termination_date":"2026-07-01","settlement_summary":"尾款结清","material_document_asset_id":"%s","material_document_version_id":"%s","workflow_instance_id":"wf-xm-term","operator_user_id":"u-term","trace_id":"trace-xm-term-apply"}
                                """.formatted(jsonString(terminationDocument, "document_asset_id"), jsonString(terminationDocument, "document_version_id"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/contracts/{contract_id}/terminations/{termination_id}/approval-results", contractId, jsonString(termination, "termination_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approval_result":"APPROVED","terminated_at":"2026-07-01T18:00:00Z","post_action_status":"SETTLED","settlement_summary":"尾款结清，资料移交","access_restriction":"READ_ONLY_AFTER_TERMINATION","operator_user_id":"u-term-approver","trace_id":"trace-xm-term-approved"}
                                """))
                .andExpect(status().isOk());

        String archivePackage = createDocument(contractId, "ARCHIVE_PACKAGE", "跨模块归档封包.zip", "trace-xm-archive-package");
        String archiveManifest = createDocument(contractId, "ARCHIVE_MANIFEST", "跨模块归档清单.json", "trace-xm-archive-manifest");
        mockMvc.perform(post("/api/contracts/{contract_id}/archives", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"archive_batch_no":"ARCH-XM-001","archive_type":"FINAL","input_set":["CONTRACT_MASTER","MAIN_BODY","PERFORMANCE_SUMMARY","TERMINATION_SUMMARY"],"package_document_asset_id":"%s","package_document_version_id":"%s","manifest_document_asset_id":"%s","manifest_document_version_id":"%s","operator_user_id":"u-archiver","trace_id":"trace-xm-archive"}
                                """.formatted(jsonString(archivePackage, "document_asset_id"), jsonString(archivePackage, "document_version_id"), jsonString(archiveManifest, "document_asset_id"), jsonString(archiveManifest, "document_version_id"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.archive_status").value("ARCHIVED"));

        mockMvc.perform(post("/api/contracts/{contract_id}/signatures/apply", contractId)
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,SIGNATURE_APPLY")
                        .header("Idempotency-Key", "idem-trace-xm-after-archive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"main_document_asset_id":"%s","main_document_version_id":"%s","signature_mode":"ELECTRONIC","seal_scheme_id":"seal-cross-module","signer_list":[{"signer_type":"USER","signer_id":"u-signer-a"}],"trace_id":"trace-xm-sign-after-archive"}
                                """.formatted(approved.documentAssetId(), signedVersionId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("CONTRACT_STATUS_CONFLICT"));
        mockMvc.perform(post("/api/contracts/{contract_id}/changes", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"change_type":"TERM","change_reason":"归档后变更","change_summary":"不应允许","trace_id":"trace-xm-change-after-archive"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("CONTRACT_TERMINATED_CHANGE_RESTRICTED"));
        mockMvc.perform(post("/api/contracts/{contract_id}/performance-records", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_user_id":"u-performance","owner_org_unit_id":"dept-cross","trace_id":"trace-xm-performance-after-archive"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("CONTRACT_NOT_EFFECTIVE_FOR_PERFORMANCE"));
        mockMvc.perform(post("/api/encrypted-documents/access")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_asset_id":"%s","document_version_id":"%s","access_scene":"PREVIEW","access_subject_type":"USER","access_subject_id":"u-preview-after-archive","actor_department_id":"dept-cross","trace_id":"trace-xm-decrypt-after-archive"}
                                """.formatted(approved.documentAssetId(), approved.documentVersionId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("CONTRACT_ARCHIVED_CONTROLLED_ACCESS_RESTRICTED"));

        mockMvc.perform(get("/api/contracts/{contract_id}", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_master.contract_status").value("ARCHIVED"))
                .andExpect(jsonPath("$.document_summary.document_asset_id").exists())
                .andExpect(jsonPath("$.approval_summary.process_id").value(approved.approvalProcessId()))
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'SIGNATURE_RESULT_WRITEBACK_COMPLETED')]").isNotEmpty())
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'PERFORMANCE_COMPLETED')]").isNotEmpty())
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'CHANGE_APPLIED')]").isNotEmpty())
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'TERMINATION_COMPLETED')]").isNotEmpty())
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'ARCHIVE_COMPLETED')]").isNotEmpty())
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'SIGNATURE_RESULT_WRITEBACK_COMPLETED')]").isNotEmpty())
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'DECRYPT_ACCESS_CONSUMED')]").isNotEmpty())
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'PERFORMANCE_RECORD_CREATED')]").isNotEmpty())
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'CHANGE_APPLIED')]").isNotEmpty())
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'TERMINATION_COMPLETED')]").isNotEmpty())
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'ARCHIVE_COMPLETED')]").isNotEmpty())
                .andExpect(jsonPath("$.contract_master_copy").doesNotExist())
                .andExpect(jsonPath("$.document_version_chain_copy").doesNotExist())
                .andExpect(jsonPath("$.approval_instance_master_copy").doesNotExist());

        mockMvc.perform(get("/api/encrypted-documents/audit-events")
                        .param("contract_id", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.event_type == 'DECRYPT_ACCESS_CONSUMED')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.event_type == 'DOWNLOAD_READY')]").isNotEmpty());
    }

    private ApprovedContract createApprovedContract(String contractName, String tracePrefix) throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"%s","owner_user_id":"u-cross-owner","owner_org_unit_id":"dept-cross","trace_id":"%s-contract"}
                                """.formatted(contractName, tracePrefix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");
        String document = createDocument(contractId, "MAIN_BODY", "跨模块主正文.docx", tracePrefix + "-document");
        String approval = mockMvc.perform(post("/api/workflow-engine/processes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"business_object_type":"CONTRACT","contract_id":"%s","document_asset_id":"%s","document_version_id":"%s","starter_user_id":"u-cross-owner","trace_id":"%s-approval-start"}
                                """.formatted(contractId, jsonString(document, "document_asset_id"), jsonString(document, "document_version_id"), tracePrefix)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String processId = jsonString(approval, "process_id");
        mockMvc.perform(post("/api/workflow-engine/processes/{process_id}/results", processId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result":"APPROVED","operator_id":"u-cross-approver","trace_id":"%s-approval-approved"}
                                """.formatted(tracePrefix)))
                .andExpect(status().isOk());
        return new ApprovedContract(contractId, jsonString(document, "document_asset_id"), jsonString(document, "document_version_id"), processId);
    }

    private String createDocument(String contractId, String documentRole, String title, String tracePrefix) throws Exception {
        return mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"%s","document_title":"%s","source_channel":"MANUAL_UPLOAD","file_upload_token":"%s-file","trace_id":"%s"}
                                """.formatted(contractId, documentRole, title, tracePrefix, tracePrefix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String createSignatureSession(String signatureRequestId, String traceId) throws Exception {
        String session = mockMvc.perform(post("/api/signature-requests/{signature_request_id}/sessions", signatureRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sign_order_mode":"SEQUENTIAL","signer_list":[{"signer_type":"USER","signer_id":"u-signer-a"},{"signer_type":"USER","signer_id":"u-signer-b"}],"trace_id":"%s"}
                                """.formatted(traceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonString(session, "signature_session_id");
    }

    private void sign(String sessionId, String signerId, int sequence, String traceId) throws Exception {
        mockMvc.perform(post("/api/signature-sessions/{signature_session_id}/callbacks", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"callback_source":"BUILT_IN_ENGINE","external_event_id":"%s-%s","event_sequence":%d,"event_type":"SIGNER_SIGNED","signer_id":"%s","trace_id":"%s"}
                                """.formatted(sessionId, signerId, sequence, signerId, traceId)))
                .andExpect(status().isOk());
    }

    private void grantDownloadAuthorization(String contractId, String userId, String traceId) throws Exception {
        mockMvc.perform(post("/api/encrypted-documents/download-authorizations")
                        .header("X-CMP-Permissions", "ENCRYPTED_DOCUMENT_AUTH_MANAGE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"authorization_name":"跨模块用户下载授权","subject_type":"USER","subject_id":"%s","scope_type":"CONTRACT","scope_value":"%s","effective_start_offset_seconds":-60,"effective_end_offset_seconds":3600,"priority_no":100,"granted_by":"admin-cross","trace_id":"%s"}
                                """.formatted(userId, contractId, traceId)))
                .andExpect(status().isCreated());
    }

    private String jsonString(String json, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = json.indexOf(marker);
        assertThat(start).as("响应缺少字段: %s, body=%s", fieldName, json).isGreaterThanOrEqualTo(0);
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        return json.substring(valueStart, valueEnd);
    }

    private record ApprovedContract(String contractId, String documentAssetId, String documentVersionId, String approvalProcessId) {
    }
}

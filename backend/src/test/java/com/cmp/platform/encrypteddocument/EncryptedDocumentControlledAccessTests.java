package com.cmp.platform.encrypteddocument;

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
class EncryptedDocumentControlledAccessTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentCenterWriteAutomaticallyCreatesSecurityBindingAcceptsEncryptionTaskAndAudits() throws Exception {
        ContractDocument sample = createContractDocument("加密入库合同", "trace-ed-auto", false);

        mockMvc.perform(get("/api/document-center/assets/{document_asset_id}", sample.documentAssetId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document_asset_id").value(sample.documentAssetId()))
                .andExpect(jsonPath("$.current_version_id").value(sample.documentVersionId()))
                .andExpect(jsonPath("$.document_status").value("FIRST_VERSION_WRITTEN"))
                .andExpect(jsonPath("$.encryption_status").value("ENCRYPTED"))
                .andExpect(jsonPath("$.security_binding_summary.security_binding_id").exists())
                .andExpect(jsonPath("$.security_binding_summary.current_version_id").value(sample.documentVersionId()))
                .andExpect(jsonPath("$.security_binding_summary.encryption_status").value("ENCRYPTED"))
                .andExpect(jsonPath("$.security_binding_summary.internal_access_mode").value("PLATFORM_CONTROLLED"))
                .andExpect(jsonPath("$.security_binding_summary.download_control_mode").value("AUTHORIZED_ONLY"))
                .andExpect(jsonPath("$.security_binding_summary.latest_check_in.check_in_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.security_binding_summary.latest_check_in.platform_job_ref.job_type").value("ED_ENCRYPTION_CHECK_IN"))
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'CHECK_IN_ACCEPTED')]").exists())
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'ENCRYPT_SUCCEEDED')]").exists())
                .andExpect(jsonPath("$.document_version_chain_copy").doesNotExist())
                .andExpect(jsonPath("$.contract_master_copy").doesNotExist());
    }

    @Test
    void automaticEncryptionIsIdempotentAndFailureDoesNotDestroyDocumentTruth() throws Exception {
        ContractDocument sample = createContractDocument("加密幂等合同", "trace-ed-idem", false);

        String first = mockMvc.perform(post("/api/encrypted-documents/check-ins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_asset_id":"%s","document_version_id":"%s","trigger_type":"NEW_VERSION","trace_id":"trace-ed-idem-repeat-1"}
                                """.formatted(sample.documentAssetId(), sample.documentVersionId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.check_in_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.idempotency_replayed").value(true))
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/api/encrypted-documents/check-ins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_asset_id":"%s","document_version_id":"%s","trigger_type":"NEW_VERSION","trace_id":"trace-ed-idem-repeat-2"}
                                """.formatted(sample.documentAssetId(), sample.documentVersionId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotency_replayed").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(jsonString(second, "check_in_id")).isEqualTo(jsonString(first, "check_in_id"));

        ContractDocument failing = createContractDocument("加密失败隔离合同", "trace-ed-fail", true);
        mockMvc.perform(get("/api/document-center/assets/{document_asset_id}", failing.documentAssetId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document_asset_id").value(failing.documentAssetId()))
                .andExpect(jsonPath("$.current_version_id").value(failing.documentVersionId()))
                .andExpect(jsonPath("$.document_status").value("FIRST_VERSION_WRITTEN"))
                .andExpect(jsonPath("$.encryption_status").value("FAILED"))
                .andExpect(jsonPath("$.security_binding_summary.latest_check_in.check_in_status").value("FAILED_RETRYABLE"))
                .andExpect(jsonPath("$.security_binding_summary.latest_check_in.result_code").value("ENCRYPTION_TASK_FAILED"))
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'ENCRYPT_FAILED')]").exists());
        mockMvc.perform(get("/api/document-center/versions/{document_version_id}", failing.documentVersionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document_version_id").value(failing.documentVersionId()))
                .andExpect(jsonPath("$.version_status").value("ACTIVE"));
    }

    @Test
    void platformConsumersReceiveControlledShortLivedTicketsWithoutPlaintextExportChannel() throws Exception {
        ContractDocument sample = createContractDocument("受控访问合同", "trace-ed-access", false);

        assertControlledAccess(sample, "PREVIEW", "USER", "u-preview", "STREAM");
        assertControlledAccess(sample, "SIGNATURE", "INTERNAL_SERVICE", "e-signature", "INTERNAL_HANDLE");
        assertControlledAccess(sample, "ARCHIVE", "INTERNAL_SERVICE", "archive", "INTERNAL_HANDLE");
        assertControlledAccess(sample, "SEARCH", "INTERNAL_SERVICE", "search-indexer", "TEMP_TEXT");
        assertControlledAccess(sample, "AI", "INTERNAL_SERVICE", "ai-app", "TEMP_TEXT");

        mockMvc.perform(post("/api/encrypted-documents/access")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_asset_id":"%s","document_version_id":"%s","access_scene":"EXTERNAL_DOWNLOAD","access_subject_type":"USER","access_subject_id":"u-preview","trace_id":"trace-ed-access-download"}
                                """.formatted(sample.documentAssetId(), sample.documentVersionId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("PLAINTEXT_EXPORT_NOT_ALLOWED"));
    }

    @Test
    void controlledAccessRejectsMissingPermissionExpiresAndRevokesTicketsWithAudit() throws Exception {
        ContractDocument sample = createContractDocument("受控访问异常合同", "trace-ed-access-deny", false);

        mockMvc.perform(post("/api/encrypted-documents/access")
                        .header("X-CMP-Permissions", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accessRequest(sample, "PREVIEW", "USER", "u-no-permission")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("CONTROLLED_ACCESS_DENIED"))
                .andExpect(jsonPath("$.audit_event.event_type").value("DECRYPT_ACCESS_DENIED"));

        String approved = mockMvc.perform(post("/api/encrypted-documents/access")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accessRequest(sample, "PREVIEW", "USER", "u-preview-expire")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accessId = jsonString(approved, "decrypt_access_id");

        mockMvc.perform(post("/api/encrypted-documents/access/{decrypt_access_id}/expire", accessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ed-expire\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_result").value("EXPIRED"))
                .andExpect(jsonPath("$.audit_event.event_type").value("DECRYPT_ACCESS_EXPIRED"));
        mockMvc.perform(post("/api/encrypted-documents/access/{decrypt_access_id}/consume", accessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ed-consume-expired\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error_code").value("ACCESS_TICKET_EXPIRED"));

        String revocable = mockMvc.perform(post("/api/encrypted-documents/access")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accessRequest(sample, "AI", "INTERNAL_SERVICE", "ai-app")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String revocableAccessId = jsonString(revocable, "decrypt_access_id");
        mockMvc.perform(post("/api/encrypted-documents/access/{decrypt_access_id}/revoke", revocableAccessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ed-revoke\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_result").value("REVOKED"))
                .andExpect(jsonPath("$.audit_event.event_type").value("DECRYPT_ACCESS_REVOKED"));
        mockMvc.perform(post("/api/encrypted-documents/access/{decrypt_access_id}/consume", revocableAccessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ed-consume-revoked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ACCESS_TICKET_REVOKED"));
    }

    private void assertControlledAccess(ContractDocument sample, String scene, String subjectType, String subjectId, String mode) throws Exception {
        mockMvc.perform(post("/api/encrypted-documents/access")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accessRequest(sample, scene, subjectType, subjectId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access_result").value("APPROVED"))
                .andExpect(jsonPath("$.access_scene").value(scene))
                .andExpect(jsonPath("$.consumption_mode").value(mode))
                .andExpect(jsonPath("$.access_ticket").exists())
                .andExpect(jsonPath("$.ticket_expires_at").exists())
                .andExpect(jsonPath("$.controlled_read_handle.handle_id").exists())
                .andExpect(jsonPath("$.controlled_read_handle.plaintext_export_allowed").value(false))
                .andExpect(jsonPath("$.audit_event.event_type").value("DECRYPT_ACCESS_APPROVED"))
                .andExpect(jsonPath("$.plaintext_download_url").doesNotExist())
                .andExpect(jsonPath("$.export_artifact_ref").doesNotExist());
    }

    private String accessRequest(ContractDocument sample, String scene, String subjectType, String subjectId) {
        return """
                {"document_asset_id":"%s","document_version_id":"%s","access_scene":"%s","access_subject_type":"%s","access_subject_id":"%s","actor_department_id":"dept-ed","trace_id":"trace-ed-access"}
                """.formatted(sample.documentAssetId(), sample.documentVersionId(), scene, subjectType, subjectId);
    }

    private ContractDocument createContractDocument(String contractName, String tracePrefix, boolean simulateEncryptionFailure) throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"%s","owner_user_id":"u-ed-owner","owner_org_unit_id":"dept-ed","trace_id":"%s-contract"}
                                """.formatted(contractName, tracePrefix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");

        String document = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"加密正文.docx","source_channel":"MANUAL_UPLOAD","file_upload_token":"%s-document","simulate_encryption_failure":%s,"trace_id":"%s-document"}
                                """.formatted(contractId, tracePrefix, simulateEncryptionFailure, tracePrefix)))
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

    private record ContractDocument(String contractId, String documentAssetId, String documentVersionId) {
    }
}

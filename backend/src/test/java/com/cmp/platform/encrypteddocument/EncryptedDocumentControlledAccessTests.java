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
        String accessTicket = jsonString(approved, "access_ticket");

        mockMvc.perform(post("/api/encrypted-documents/access/{decrypt_access_id}/expire", accessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ed-expire\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_result").value("EXPIRED"))
                .andExpect(jsonPath("$.audit_event.event_type").value("DECRYPT_ACCESS_EXPIRED"));
        mockMvc.perform(post("/api/encrypted-documents/access/{decrypt_access_id}/consume", accessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"access_ticket":"%s","trace_id":"trace-ed-consume-expired"}
                                """.formatted(accessTicket)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error_code").value("ACCESS_TICKET_EXPIRED"));

        String revocable = mockMvc.perform(post("/api/encrypted-documents/access")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accessRequest(sample, "AI", "INTERNAL_SERVICE", "ai-app")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String revocableAccessId = jsonString(revocable, "decrypt_access_id");
        String revocableTicket = jsonString(revocable, "access_ticket");
        mockMvc.perform(post("/api/encrypted-documents/access/{decrypt_access_id}/revoke", revocableAccessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ed-revoke\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_result").value("REVOKED"))
                .andExpect(jsonPath("$.audit_event.event_type").value("DECRYPT_ACCESS_REVOKED"));
        mockMvc.perform(post("/api/encrypted-documents/access/{decrypt_access_id}/consume", revocableAccessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"access_ticket":"%s","trace_id":"trace-ed-consume-revoked"}
                                """.formatted(revocableTicket)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ACCESS_TICKET_REVOKED"));
    }

    @Test
    void controlledAccessConsumeRequiresMatchingTicketAndRejectsNaturallyExpiredTicketWithAudit() throws Exception {
        ContractDocument sample = createContractDocument("票据消费校验合同", "trace-ed-ticket", false);

        String approved = mockMvc.perform(post("/api/encrypted-documents/access")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accessRequest(sample, "PREVIEW", "USER", "u-ticket")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accessId = jsonString(approved, "decrypt_access_id");

        mockMvc.perform(post("/api/encrypted-documents/access/{decrypt_access_id}/consume", accessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ed-ticket-missing\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ACCESS_TICKET_INVALID"))
                .andExpect(jsonPath("$.audit_event.event_type").value("DECRYPT_ACCESS_DENIED"));

        String expired = mockMvc.perform(post("/api/encrypted-documents/access")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_asset_id":"%s","document_version_id":"%s","access_scene":"PREVIEW","access_subject_type":"USER","access_subject_id":"u-ticket-expired","actor_department_id":"dept-ed","ttl_seconds":-1,"trace_id":"trace-ed-ticket-expired"}
                                """.formatted(sample.documentAssetId(), sample.documentVersionId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String expiredAccessId = jsonString(expired, "decrypt_access_id");
        String expiredTicket = jsonString(expired, "access_ticket");

        mockMvc.perform(post("/api/encrypted-documents/access/{decrypt_access_id}/consume", expiredAccessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"access_ticket":"%s","trace_id":"trace-ed-ticket-natural-expired"}
                                """.formatted(expiredTicket)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error_code").value("ACCESS_TICKET_EXPIRED"))
                .andExpect(jsonPath("$.audit_event.event_type").value("DECRYPT_ACCESS_EXPIRED"));

        mockMvc.perform(get("/api/document-center/assets/{document_asset_id}", sample.documentAssetId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'DECRYPT_ACCESS_DENIED' && @.trace_id == 'trace-ed-ticket-missing')]").exists())
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'DECRYPT_ACCESS_EXPIRED' && @.trace_id == 'trace-ed-ticket-natural-expired')]").exists());
    }

    @Test
    void controlledAccessRejectsUnknownSceneWithPersistentAudit() throws Exception {
        ContractDocument sample = createContractDocument("未知场景拒绝合同", "trace-ed-unknown-scene", false);

        mockMvc.perform(post("/api/encrypted-documents/access")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accessRequest(sample, "UNKNOWN_SCENE", "USER", "u-unknown-scene")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("CONTROLLED_ACCESS_SCENE_DENIED"))
                .andExpect(jsonPath("$.audit_event.event_type").value("DECRYPT_ACCESS_DENIED"));

        mockMvc.perform(get("/api/document-center/assets/{document_asset_id}", sample.documentAssetId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'DECRYPT_ACCESS_DENIED' && @.trace_id == 'trace-ed-access')]").exists());
    }

    @Test
    void externalDownloadRejectionIsPersistedInDocumentAssetAuditRecord() throws Exception {
        ContractDocument sample = createContractDocument("外放拒绝审计合同", "trace-ed-external-audit", false);

        mockMvc.perform(post("/api/encrypted-documents/access")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_asset_id":"%s","document_version_id":"%s","access_scene":"EXTERNAL_DOWNLOAD","access_subject_type":"USER","access_subject_id":"u-download","actor_department_id":"dept-ed","trace_id":"trace-ed-external-denied"}
                                """.formatted(sample.documentAssetId(), sample.documentVersionId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("PLAINTEXT_EXPORT_NOT_ALLOWED"))
                .andExpect(jsonPath("$.audit_event.event_type").value("DECRYPT_ACCESS_DENIED"));

        mockMvc.perform(get("/api/document-center/assets/{document_asset_id}", sample.documentAssetId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'DECRYPT_ACCESS_DENIED' && @.trace_id == 'trace-ed-external-denied')]").exists());
    }

    @Test
    void downloadAuthorizationSupportsDepartmentUserScopesPriorityRevocationExpirationAndExplanation() throws Exception {
        ContractDocument sample = createContractDocument("授权规则合同", "trace-ed-auth", false);

        mockMvc.perform(post("/api/encrypted-documents/download-authorizations")
                        .header("X-CMP-Permissions", "ENCRYPTED_DOCUMENT_AUTH_MANAGE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"authorization_name":"法务部门正文下载","subject_type":"DEPARTMENT","subject_id":"dept-legal","scope_type":"DOCUMENT_ROLE","scope_value":"MAIN_BODY","effective_start_offset_seconds":-60,"effective_end_offset_seconds":3600,"priority_no":10,"granted_by":"admin-ed","trace_id":"trace-ed-auth-dept"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorization_status").value("ACTIVE"))
                .andExpect(jsonPath("$.subject_type").value("DEPARTMENT"))
                .andExpect(jsonPath("$.policy_snapshot.scope_expression.expression_code").value("DOCUMENT_ROLE:MAIN_BODY"))
                .andExpect(jsonPath("$.audit_event.event_type").value("DOWNLOAD_AUTH_GRANTED"));

        String userAuth = mockMvc.perform(post("/api/encrypted-documents/download-authorizations")
                        .header("X-CMP-Permissions", "ENCRYPTED_DOCUMENT_AUTH_MANAGE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"authorization_name":"专员合同下载","subject_type":"USER","subject_id":"u-auth","scope_type":"CONTRACT","scope_value":"%s","effective_start_offset_seconds":-60,"effective_end_offset_seconds":3600,"priority_no":100,"granted_by":"admin-ed","trace_id":"trace-ed-auth-user"}
                                """.formatted(sample.contractId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String userAuthorizationId = jsonString(userAuth, "authorization_id");

        mockMvc.perform(post("/api/encrypted-documents/download-authorizations/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(downloadExplainRequest(sample, "u-auth", "dept-legal", "trace-ed-auth-hit-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOWED"))
                .andExpect(jsonPath("$.matched_authorization.authorization_id").value(userAuthorizationId))
                .andExpect(jsonPath("$.matched_authorization.subject_type").value("USER"))
                .andExpect(jsonPath("$.matched_authorization.scope_type").value("CONTRACT"))
                .andExpect(jsonPath("$.explanation.priority_no").value(100))
                .andExpect(jsonPath("$.authorization_snapshot.document_version_id").value(sample.documentVersionId()));

        mockMvc.perform(post("/api/encrypted-documents/download-authorizations/{authorization_id}/revoke", userAuthorizationId)
                        .header("X-CMP-Permissions", "ENCRYPTED_DOCUMENT_AUTH_MANAGE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revoked_by\":\"admin-ed\",\"trace_id\":\"trace-ed-auth-revoke\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorization_status").value("REVOKED"))
                .andExpect(jsonPath("$.audit_event.event_type").value("DOWNLOAD_AUTH_REVOKED"));

        mockMvc.perform(post("/api/encrypted-documents/download-authorizations/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(downloadExplainRequest(sample, "u-auth", "dept-legal", "trace-ed-auth-hit-dept")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOWED"))
                .andExpect(jsonPath("$.matched_authorization.subject_type").value("DEPARTMENT"))
                .andExpect(jsonPath("$.matched_authorization.scope_type").value("DOCUMENT_ROLE"));

        mockMvc.perform(post("/api/encrypted-documents/download-authorizations")
                        .header("X-CMP-Permissions", "ENCRYPTED_DOCUMENT_AUTH_MANAGE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"authorization_name":"已过期授权","subject_type":"USER","subject_id":"u-expired","scope_type":"GLOBAL","scope_value":"*","effective_start_offset_seconds":-3600,"effective_end_offset_seconds":-1,"priority_no":200,"granted_by":"admin-ed","trace_id":"trace-ed-auth-expired"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorization_status").value("EXPIRED"));

        mockMvc.perform(post("/api/encrypted-documents/download-authorizations/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(downloadExplainRequest(sample, "u-expired", "dept-expired", "trace-ed-auth-expired-denied")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.decision").value("DENIED"))
                .andExpect(jsonPath("$.reason_code").value("DOWNLOAD_AUTHORIZATION_NOT_FOUND"));
    }

    @Test
    void decryptDownloadJobFreezesAuthorizationGeneratesDetachedPlaintextArtifactExpiresAndCompensatesFailure() throws Exception {
        ContractDocument sample = createContractDocument("下载作业合同", "trace-ed-job", false);
        grantUserDownloadAuthorization(sample, "u-job", "trace-ed-job-auth");

        String job = mockMvc.perform(post("/api/encrypted-documents/download-jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(downloadJobRequest(sample, "u-job", "dept-job", false, "trace-ed-job-ready")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.job_status").value("READY"))
                .andExpect(jsonPath("$.authorization_snapshot.authorization_id").exists())
                .andExpect(jsonPath("$.export_artifact.package_id").exists())
                .andExpect(jsonPath("$.export_artifact.plaintext_detached_usable").value(true))
                .andExpect(jsonPath("$.export_artifact.document_center_truth_replaced").value(false))
                .andExpect(jsonPath("$.download_url.expires_at").exists())
                .andExpect(jsonPath("$.audit_event.event_type").value("DOWNLOAD_READY"))
                .andReturn().getResponse().getContentAsString();
        String jobId = jsonString(job, "decrypt_download_job_id");

        mockMvc.perform(post("/api/encrypted-documents/download-jobs/{job_id}/deliver", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ed-job-deliver\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job_status").value("DELIVERED"))
                .andExpect(jsonPath("$.audit_event.event_type").value("DOWNLOAD_DELIVERED"));

        mockMvc.perform(post("/api/encrypted-documents/download-jobs/{job_id}/expire", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ed-job-expire\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job_status").value("EXPIRED"))
                .andExpect(jsonPath("$.export_artifact.artifact_status").value("EXPIRED"))
                .andExpect(jsonPath("$.audit_event.event_type").value("DOWNLOAD_EXPIRED"));

        mockMvc.perform(post("/api/encrypted-documents/download-jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(downloadJobRequest(sample, "u-job", "dept-job", true, "trace-ed-job-fail")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_status").value("FAILED"))
                .andExpect(jsonPath("$.result_code").value("EXPORT_GENERATION_FAILED"))
                .andExpect(jsonPath("$.compensation_task.task_type").value("ED_DECRYPT_DOWNLOAD_EXPORT"))
                .andExpect(jsonPath("$.audit_event.event_type").value("DOWNLOAD_EXPORT_FAILED"));
    }

    @Test
    void highSensitivityAuditCanQueryGrantHitExportSuccessFailureExpirationAndUnauthorizedDenial() throws Exception {
        ContractDocument sample = createContractDocument("高敏审计合同", "trace-ed-audit", false);
        grantUserDownloadAuthorization(sample, "u-audit", "trace-ed-audit-grant");

        mockMvc.perform(post("/api/encrypted-documents/download-jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(downloadJobRequest(sample, "u-no-auth", "dept-no-auth", false, "trace-ed-audit-denied")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("DOWNLOAD_AUTHORIZATION_DENIED"))
                .andExpect(jsonPath("$.audit_event.event_type").value("DOWNLOAD_AUTH_DENIED"));

        String ready = mockMvc.perform(post("/api/encrypted-documents/download-jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(downloadJobRequest(sample, "u-audit", "dept-audit", false, "trace-ed-audit-ready")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String jobId = jsonString(ready, "decrypt_download_job_id");

        mockMvc.perform(post("/api/encrypted-documents/download-jobs/{job_id}/expire", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ed-audit-expire\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/encrypted-documents/download-jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(downloadJobRequest(sample, "u-audit", "dept-audit", true, "trace-ed-audit-fail")))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/encrypted-documents/audit-events")
                        .param("document_asset_id", sample.documentAssetId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.event_type == 'DOWNLOAD_AUTH_GRANTED' && @.trace_id == 'trace-ed-audit-grant')]").exists())
                .andExpect(jsonPath("$.items[?(@.event_type == 'DOWNLOAD_AUTH_HIT' && @.trace_id == 'trace-ed-audit-ready')]").exists())
                .andExpect(jsonPath("$.items[?(@.event_type == 'DOWNLOAD_READY' && @.trace_id == 'trace-ed-audit-ready')]").exists())
                .andExpect(jsonPath("$.items[?(@.event_type == 'DOWNLOAD_EXPORT_FAILED' && @.trace_id == 'trace-ed-audit-fail')]").exists())
                .andExpect(jsonPath("$.items[?(@.event_type == 'DOWNLOAD_EXPIRED' && @.trace_id == 'trace-ed-audit-expire')]").exists())
                .andExpect(jsonPath("$.items[?(@.event_type == 'DOWNLOAD_AUTH_DENIED' && @.trace_id == 'trace-ed-audit-denied')]").exists());
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

        String body = mockMvc.perform(post("/api/encrypted-documents/access")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accessRequest(sample, scene, subjectType, subjectId + "-consume")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accessId = jsonString(body, "decrypt_access_id");
        String ticket = jsonString(body, "access_ticket");
        mockMvc.perform(post("/api/encrypted-documents/access/{decrypt_access_id}/consume", accessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"access_ticket":"%s","trace_id":"trace-ed-consume"}
                                """.formatted(ticket)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consume_result").value("CONSUMED"));
    }

    private String accessRequest(ContractDocument sample, String scene, String subjectType, String subjectId) {
        return """
                {"document_asset_id":"%s","document_version_id":"%s","access_scene":"%s","access_subject_type":"%s","access_subject_id":"%s","actor_department_id":"dept-ed","trace_id":"trace-ed-access"}
                """.formatted(sample.documentAssetId(), sample.documentVersionId(), scene, subjectType, subjectId);
    }

    private String grantUserDownloadAuthorization(ContractDocument sample, String userId, String traceId) throws Exception {
        String body = mockMvc.perform(post("/api/encrypted-documents/download-authorizations")
                        .header("X-CMP-Permissions", "ENCRYPTED_DOCUMENT_AUTH_MANAGE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"authorization_name":"用户下载授权","subject_type":"USER","subject_id":"%s","scope_type":"CONTRACT","scope_value":"%s","effective_start_offset_seconds":-60,"effective_end_offset_seconds":3600,"priority_no":100,"granted_by":"admin-ed","trace_id":"%s"}
                                """.formatted(userId, sample.contractId(), traceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonString(body, "authorization_id");
    }

    private String downloadExplainRequest(ContractDocument sample, String userId, String departmentId, String traceId) {
        return """
                {"document_asset_id":"%s","document_version_id":"%s","requester_user_id":"%s","requester_department_id":"%s","trace_id":"%s"}
                """.formatted(sample.documentAssetId(), sample.documentVersionId(), userId, departmentId, traceId);
    }

    private String downloadJobRequest(ContractDocument sample, String userId, String departmentId, boolean simulateFailure, String traceId) {
        return """
                {"document_asset_id":"%s","document_version_id":"%s","requested_by":"%s","requested_department_id":"%s","download_reason":"验收导出","request_idempotency_key":"%s","simulate_export_failure":%s,"trace_id":"%s"}
                """.formatted(sample.documentAssetId(), sample.documentVersionId(), userId, departmentId, traceId, simulateFailure, traceId);
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

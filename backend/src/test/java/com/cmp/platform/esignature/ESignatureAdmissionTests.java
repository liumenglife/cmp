package com.cmp.platform.esignature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ESignatureAdmissionTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void approvedContractCanApplySignatureWithSnapshotDocumentBindingFingerprintAndAudit() throws Exception {
        ContractSample sample = createApprovedContractSample("电子签章准入成功合同", "trace-es-ok");

        String body = mockMvc.perform(post("/api/contracts/{contract_id}/signatures/apply", sample.contractId())
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,SIGNATURE_APPLY")
                        .header("Idempotency-Key", "idem-es-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"main_document_asset_id":"%s","main_document_version_id":"%s","signature_mode":"ELECTRONIC","seal_scheme_id":"seal-standard","signer_list":[{"signer_type":"USER","signer_id":"u-sign-a"}],"sign_order_mode":"SERIAL","biz_note":"正式签章申请","trace_id":"trace-es-ok-apply"}
                                """.formatted(sample.documentAssetId(), sample.documentVersionId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.signature_request_id").exists())
                .andExpect(jsonPath("$.contract_id").value(sample.contractId()))
                .andExpect(jsonPath("$.request_status").value("ADMITTED"))
                .andExpect(jsonPath("$.signature_status").value("READY"))
                .andExpect(jsonPath("$.application_snapshot.contract_status").value("APPROVED"))
                .andExpect(jsonPath("$.application_snapshot.approval_summary.final_result").value("APPROVED"))
                .andExpect(jsonPath("$.input_document_binding.binding_role").value("SOURCE_MAIN"))
                .andExpect(jsonPath("$.input_document_binding.document_asset_id").value(sample.documentAssetId()))
                .andExpect(jsonPath("$.input_document_binding.document_version_id").value(sample.documentVersionId()))
                .andExpect(jsonPath("$.request_fingerprint").exists())
                .andExpect(jsonPath("$.audit_record[0].audit_action_type").value("SIGNATURE_APPLY_ADMITTED"))
                .andExpect(jsonPath("$.contract_master_copy").doesNotExist())
                .andExpect(jsonPath("$.document_version_chain_copy").doesNotExist())
                .andExpect(jsonPath("$.approval_instance_master_copy").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String signatureRequestId = jsonString(body, "signature_request_id");
        mockMvc.perform(get("/api/signature-requests/{signature_request_id}", signatureRequestId)
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,SIGNATURE_APPLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signature_request_id").value(signatureRequestId))
                .andExpect(jsonPath("$.contract_id").value(sample.contractId()))
                .andExpect(jsonPath("$.request_status").value("ADMITTED"))
                .andExpect(jsonPath("$.signature_status").value("READY"))
                .andExpect(jsonPath("$.main_document_asset_id").value(sample.documentAssetId()))
                .andExpect(jsonPath("$.main_document_version_id").value(sample.documentVersionId()))
                .andExpect(jsonPath("$.current_session_id").doesNotExist())
                .andExpect(jsonPath("$.engine_adapter_payload").doesNotExist())
                .andExpect(jsonPath("$.certificate_chain").doesNotExist())
                .andExpect(jsonPath("$.internal_runtime_checkpoint").doesNotExist());
    }

    @Test
    void repeatedApplyWithSameIdempotencyKeyReturnsOriginalSignatureRequest() throws Exception {
        ContractSample sample = createApprovedContractSample("电子签章幂等合同", "trace-es-idem");
        String first = applySignature(sample, "idem-es-repeat")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = applySignature(sample, "idem-es-repeat")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotency_replayed").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(jsonString(second, "signature_request_id")).isEqualTo(jsonString(first, "signature_request_id"));
        assertThat(jsonString(second, "request_fingerprint")).isEqualTo(jsonString(first, "request_fingerprint"));
    }

    @Test
    void admissionRejectsRejectedWithdrawnIllegalStatusStaleVersionAndMissingPermission() throws Exception {
        ContractSample rejected = createCompletedContractSample("电子签章审批驳回合同", "trace-es-rejected", "REJECTED");
        applySignature(rejected, "idem-es-rejected")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("APPROVAL_NOT_PASSED"));

        ContractSample withdrawn = createCompletedContractSample("电子签章审批撤回合同", "trace-es-withdrawn", "TERMINATED");
        applySignature(withdrawn, "idem-es-withdrawn")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("APPROVAL_WITHDRAWN"));

        ContractSample illegalStatus = createApprovedContractSample("电子签章状态不合法合同", "trace-es-illegal-status");
        advanceContractMasterStatusForTest(illegalStatus.contractId(), "SIGNED");
        applySignature(illegalStatus, "idem-es-illegal-status")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("CONTRACT_STATUS_CONFLICT"));

        ContractSample staleVersion = createApprovedContractSample("电子签章旧版本合同", "trace-es-stale");
        mockMvc.perform(post("/api/document-center/assets/{document_asset_id}/versions", staleVersion.documentAssetId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version_label":"V2","change_reason":"审批后形成新稿","file_upload_token":"trace-es-stale-v2","trace_id":"trace-es-stale-v2"}
                                """))
                .andExpect(status().isCreated());
        applySignature(staleVersion, "idem-es-stale")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("FILE_VALIDATION_FAILED"));

        ContractSample noPermission = createApprovedContractSample("电子签章权限不足合同", "trace-es-permission");
        mockMvc.perform(post("/api/contracts/{contract_id}/signatures/apply", noPermission.contractId())
                        .header("X-CMP-Permissions", "CONTRACT_VIEW")
                        .header("Idempotency-Key", "idem-es-permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signatureRequestJson(noPermission)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("PERMISSION_DENIED"));
    }

    @Test
    void admissionRejectsCurrentVersionAppendedAfterApprovalBecauseItIsNotApprovedVersion() throws Exception {
        ContractSample sample = createApprovedContractSample("电子签章审批后追加版本合同", "trace-es-post-approval-version");
        String appended = mockMvc.perform(post("/api/document-center/assets/{document_asset_id}/versions", sample.documentAssetId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version_label":"V2","change_reason":"审批通过后追加新稿","file_upload_token":"trace-es-post-approval-version-v2","trace_id":"trace-es-post-approval-version-v2"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String appendedVersionId = jsonString(appended, "document_version_id");

        mockMvc.perform(post("/api/contracts/{contract_id}/signatures/apply", sample.contractId())
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,SIGNATURE_APPLY")
                        .header("Idempotency-Key", "idem-es-post-approval-version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signatureRequestJson(sample, appendedVersionId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("FILE_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.approved_document_version_id").value(sample.documentVersionId()))
                .andExpect(jsonPath("$.requested_document_version_id").value(appendedVersionId));
    }

    private org.springframework.test.web.servlet.ResultActions applySignature(ContractSample sample, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/api/contracts/{contract_id}/signatures/apply", sample.contractId())
                .header("X-CMP-Permissions", "CONTRACT_VIEW,SIGNATURE_APPLY")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(signatureRequestJson(sample)));
    }

    private String signatureRequestJson(ContractSample sample) {
        return signatureRequestJson(sample, sample.documentVersionId());
    }

    private String signatureRequestJson(ContractSample sample, String documentVersionId) {
        return """
                {"main_document_asset_id":"%s","main_document_version_id":"%s","signature_mode":"ELECTRONIC","seal_scheme_id":"seal-standard","signer_list":[{"signer_type":"USER","signer_id":"u-sign-a"}],"sign_order_mode":"SERIAL","biz_note":"正式签章申请","trace_id":"trace-es-apply"}
                """.formatted(sample.documentAssetId(), documentVersionId);
    }

    private ContractSample createApprovedContractSample(String contractName, String tracePrefix) throws Exception {
        return createCompletedContractSample(contractName, tracePrefix, "APPROVED");
    }

    private ContractSample createCompletedContractSample(String contractName, String tracePrefix, String approvalResult) throws Exception {
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
                                {"result":"%s","operator_id":"u-es-approver","trace_id":"%s-approval-result"}
                                """.formatted(approvalResult, tracePrefix)))
                .andExpect(status().isOk());

        return new ContractSample(contractId, documentAssetId, documentVersionId, processId);
    }

    private String jsonString(String json, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = json.indexOf(marker);
        assertThat(start).as("响应缺少字段: %s, body=%s", fieldName, json).isGreaterThanOrEqualTo(0);
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        return json.substring(valueStart, valueEnd);
    }

    @SuppressWarnings("unchecked")
    private void advanceContractMasterStatusForTest(String contractId, String contractStatus) throws Exception {
        Object service = applicationContext.getBean("coreChainService");
        Map<String, Object> contracts = (Map<String, Object>) ReflectionTestUtils.getField(service, "contracts");
        Object contract = contracts.get(contractId);
        assertThat(contract).as("测试辅助路径需要先创建合同主档").isNotNull();

        Class<?> contractType = contract.getClass();
        Constructor<?> constructor = contractType.getDeclaredConstructor(String.class, String.class, String.class, String.class,
                String.class, String.class, String.class, String.class, recordValue(contract, "currentDocument").getClass(),
                List.class, Map.class, String.class, List.class);
        constructor.setAccessible(true);
        contracts.put(contractId, constructor.newInstance(
                recordValue(contract, "contractId"),
                recordValue(contract, "contractNo"),
                recordValue(contract, "contractName"),
                contractStatus,
                recordValue(contract, "ownerOrgUnitId"),
                recordValue(contract, "ownerUserId"),
                recordValue(contract, "amount"),
                recordValue(contract, "currency"),
                recordValue(contract, "currentDocument"),
                new ArrayList<>((List<?>) recordValue(contract, "attachments")),
                recordValue(contract, "approvalSummary"),
                recordValue(contract, "processId"),
                new ArrayList<>((List<?>) recordValue(contract, "events"))));
    }

    private Object recordValue(Object record, String accessorName) throws Exception {
        Method method = record.getClass().getDeclaredMethod(accessorName);
        method.setAccessible(true);
        return method.invoke(record);
    }

    private record ContractSample(String contractId, String documentAssetId, String documentVersionId, String processId) {
    }
}

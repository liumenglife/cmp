package com.cmp.platform.batch3;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@AutoConfigureMockMvc
class Batch3MountPointContractTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void approvedContractAndCurrentDocumentVersionBecomeBatch3SignatureEncryptionArchiveInputsWithoutCopyingCoreTruth() throws Exception {
        ApprovedContractSample sample = createApprovedContractSample("第三批挂载点验收合同", "trace-batch3-gate");

        mockMvc.perform(get("/api/batch3/contracts/{contract_id}/mount-points", sample.contractId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_id").value(sample.contractId()))
                .andExpect(jsonPath("$.contract_status").value("APPROVED"))
                .andExpect(jsonPath("$.approval_summary.process_id").value(sample.processId()))
                .andExpect(jsonPath("$.approval_summary.final_result").value("APPROVED"))
                .andExpect(jsonPath("$.signature_input.accepted").value(true))
                .andExpect(jsonPath("$.signature_input.contract_id").value(sample.contractId()))
                .andExpect(jsonPath("$.signature_input.document_version_ref.document_asset_id").value(sample.documentAssetId()))
                .andExpect(jsonPath("$.signature_input.document_version_ref.document_version_id").value(sample.documentVersionId()))
                .andExpect(jsonPath("$.encryption_input.document_version_ref.document_version_id").value(sample.documentVersionId()))
                .andExpect(jsonPath("$.archive_input.document_version_ref.document_version_id").value(sample.documentVersionId()))
                .andExpect(jsonPath("$.main_truth_reuse.contract_master_owner").value("contract-core"))
                .andExpect(jsonPath("$.main_truth_reuse.document_version_owner").value("document-center"))
                .andExpect(jsonPath("$.main_truth_reuse.approval_summary_owner").value("workflow-engine"))
                .andExpect(jsonPath("$.contract_master_copy").doesNotExist())
                .andExpect(jsonPath("$.document_version_chain_copy").doesNotExist())
                .andExpect(jsonPath("$.approval_instance_master_copy").doesNotExist());
    }

    @Test
    void sharedContractFreezesBatch3StatusesContractStatusMappingAndSummaryWritebackDirections() throws Exception {
        mockMvc.perform(get("/api/batch3/shared-contract"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status_fields[0]").value("signature_status"))
                .andExpect(jsonPath("$.status_fields[1]").value("encryption_status"))
                .andExpect(jsonPath("$.status_fields[2]").value("performance_status"))
                .andExpect(jsonPath("$.status_fields[3]").value("change_status"))
                .andExpect(jsonPath("$.status_fields[4]").value("termination_status"))
                .andExpect(jsonPath("$.status_fields[5]").value("archive_status"))
                .andExpect(jsonPath("$.status_contract_mapping.signature_status.READY.allowed_contract_status[0]").value("APPROVED"))
                .andExpect(jsonPath("$.status_contract_mapping.signature_status.SIGNED.writeback_contract_status").value("SIGNED"))
                .andExpect(jsonPath("$.status_contract_mapping.encryption_status.ENCRYPTED.writeback_contract_status").value("UNCHANGED"))
                .andExpect(jsonPath("$.status_contract_mapping.performance_status.IN_PROGRESS.allowed_contract_status[0]").value("SIGNED"))
                .andExpect(jsonPath("$.status_contract_mapping.change_status.APPROVED.writeback_contract_status").value("CHANGED"))
                .andExpect(jsonPath("$.status_contract_mapping.termination_status.TERMINATED.writeback_contract_status").value("TERMINATED"))
                .andExpect(jsonPath("$.status_contract_mapping.archive_status.ARCHIVED.writeback_contract_status").value("ARCHIVED"))
                .andExpect(jsonPath("$.summary_writeback_direction.signature_status").value("e-signature -> contract.signature_summary"))
                .andExpect(jsonPath("$.summary_writeback_direction.encryption_status").value("encrypted-document -> document-center.encryption_summary -> contract.document_summary"))
                .andExpect(jsonPath("$.summary_writeback_direction.performance_status").value("contract-lifecycle -> contract.performance_summary"))
                .andExpect(jsonPath("$.summary_writeback_direction.change_status").value("contract-lifecycle -> contract.change_summary"))
                .andExpect(jsonPath("$.summary_writeback_direction.termination_status").value("contract-lifecycle -> contract.termination_summary"))
                .andExpect(jsonPath("$.summary_writeback_direction.archive_status").value("contract-lifecycle -> contract.archive_summary"))
                .andExpect(jsonPath("$.truth_ownership.contract_master").value("reference contract_id only"))
                .andExpect(jsonPath("$.truth_ownership.document_version").value("reference document_version_id only"))
                .andExpect(jsonPath("$.truth_ownership.approval").value("reference approval_summary only"));
    }

    @Test
    void unsignedContractCannotEnterPerformanceMountPointEvenWhenSignatureStatusParameterIsSigned() throws Exception {
        ApprovedContractSample sample = createApprovedContractSample("第三批履约入口合同", "trace-batch3-performance");

        mockMvc.perform(get("/api/batch3/contracts/{contract_id}/mount-points", sample.contractId())
                        .param("signature_status", "SIGNED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.performance_input.accepted").value(false))
                .andExpect(jsonPath("$.performance_input.contract_id").value(sample.contractId()))
                .andExpect(jsonPath("$.performance_input.source_contract_status").value("APPROVED"))
                .andExpect(jsonPath("$.performance_input.signature_status").value("SIGNED"))
                .andExpect(jsonPath("$.performance_input.contract_master_copy").doesNotExist())
                .andExpect(jsonPath("$.main_truth_reuse.contract_master_owner").value("contract-core"));
    }

    @Test
    void signedContractCanEnterPerformanceMountPointWithoutCreatingAnotherContractMaster() throws Exception {
        ApprovedContractSample sample = createApprovedContractSample("第三批已签履约入口合同", "trace-batch3-performance-signed");
        advanceContractMasterStatusForTest(sample.contractId(), "SIGNED");

        mockMvc.perform(get("/api/batch3/contracts/{contract_id}/mount-points", sample.contractId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.performance_input.accepted").value(true))
                .andExpect(jsonPath("$.performance_input.contract_id").value(sample.contractId()))
                .andExpect(jsonPath("$.performance_input.source_contract_status").value("SIGNED"))
                .andExpect(jsonPath("$.performance_input.signature_status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.performance_input.contract_master_copy").doesNotExist())
                .andExpect(jsonPath("$.main_truth_reuse.contract_master_owner").value("contract-core"));
    }

    private ApprovedContractSample createApprovedContractSample(String contractName, String tracePrefix) throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"%s","owner_user_id":"u-batch3-owner","owner_org_unit_id":"dept-batch3","trace_id":"%s-contract"}
                                """.formatted(contractName, tracePrefix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");

        String document = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"第三批主正文.docx","source_channel":"MANUAL_UPLOAD","file_upload_token":"%s-document","trace_id":"%s-document"}
                                """.formatted(contractId, tracePrefix, tracePrefix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String documentAssetId = jsonString(document, "document_asset_id");
        String documentVersionId = jsonString(document, "document_version_id");

        String approval = mockMvc.perform(post("/api/workflow-engine/processes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"business_object_type":"CONTRACT","contract_id":"%s","document_asset_id":"%s","document_version_id":"%s","starter_user_id":"u-batch3-owner","trace_id":"%s-approval-start"}
                                """.formatted(contractId, documentAssetId, documentVersionId, tracePrefix)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String processId = jsonString(approval, "process_id");

        mockMvc.perform(post("/api/workflow-engine/processes/{process_id}/results", processId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result":"APPROVED","operator_id":"u-batch3-approver","trace_id":"%s-approval-approved"}
                                """.formatted(tracePrefix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_summary.final_result").value("APPROVED"));

        return new ApprovedContractSample(contractId, documentAssetId, documentVersionId, processId);
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

    private record ApprovedContractSample(String contractId, String documentAssetId, String documentVersionId, String processId) {
    }
}

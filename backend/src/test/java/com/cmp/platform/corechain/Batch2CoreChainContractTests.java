package com.cmp.platform.corechain;

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
class Batch2CoreChainContractTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsContractMasterWithBusinessIdentityOwnershipAndAuditRecord() throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"主档创建测试合同","owner_user_id":"u-contract-owner","owner_org_unit_id":"dept-contract-owner","trace_id":"trace-contract-create"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contract_id").isNotEmpty())
                .andExpect(jsonPath("$.contract_no").isNotEmpty())
                .andExpect(jsonPath("$.contract_status").value("DRAFT"))
                .andExpect(jsonPath("$.owner_org_unit_id").value("dept-contract-owner"))
                .andExpect(jsonPath("$.owner_user_id").value("u-contract-owner"))
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'CONTRACT_CREATED')]").isNotEmpty())
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'CONTRACT_CREATED')].trace_id").value("trace-contract-create"))
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");

        mockMvc.perform(get("/api/contracts/{contract_id}/master", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_id").value(contractId))
                .andExpect(jsonPath("$.contract_no").isNotEmpty())
                .andExpect(jsonPath("$.contract_name").value("主档创建测试合同"))
                .andExpect(jsonPath("$.contract_status").value("DRAFT"))
                .andExpect(jsonPath("$.owner_org_unit_id").value("dept-contract-owner"))
                .andExpect(jsonPath("$.owner_user_id").value("u-contract-owner"));
    }

    @Test
    void editsOnlyDraftContractWhenRequesterHasEditPermission() throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"编辑前合同","owner_user_id":"u-editor","owner_org_unit_id":"dept-editor","trace_id":"trace-edit-create"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");

        mockMvc.perform(patch("/api/contracts/{contract_id}", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"无权限编辑合同","operator_user_id":"u-editor","trace_id":"trace-edit-denied"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("PERMISSION_DENIED"));

        mockMvc.perform(patch("/api/contracts/{contract_id}", contractId)
                        .header("X-CMP-Permissions", "CONTRACT_EDIT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"编辑后合同","amount":"120000.00","currency":"CNY","operator_user_id":"u-editor","trace_id":"trace-edit-ok"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_id").value(contractId))
                .andExpect(jsonPath("$.contract_name").value("编辑后合同"))
                .andExpect(jsonPath("$.amount").value("120000.00"))
                .andExpect(jsonPath("$.currency").value("CNY"))
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'CONTRACT_UPDATED')]").isNotEmpty());

        String document = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_name":"approval-main.docx","document_kind":"CONTRACT_BODY","trace_id":"trace-edit-doc"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String documentAssetId = jsonString(document, "document_asset_id");
        String documentVersionId = jsonString(document, "document_version_id");

        mockMvc.perform(post("/api/workflow-engine/processes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"business_object_type":"CONTRACT","contract_id":"%s","document_asset_id":"%s","document_version_id":"%s","starter_user_id":"u-editor","trace_id":"trace-edit-approval"}
                                """.formatted(contractId, documentAssetId, documentVersionId)))
                .andExpect(status().isAccepted());

        mockMvc.perform(patch("/api/contracts/{contract_id}", contractId)
                        .header("X-CMP-Permissions", "CONTRACT_EDIT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"非草稿编辑合同","operator_user_id":"u-editor","trace_id":"trace-edit-status-denied"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("CONTRACT_STATUS_CONFLICT"));
    }

    @Test
    void ledgerAndDetailAggregateMasterCurrentDocumentApprovalAndTimelineSummaries() throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"台账详情测试合同","owner_user_id":"u-ledger","owner_org_unit_id":"dept-ledger","trace_id":"trace-ledger-create"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");

        String document = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_name":"ledger-main.docx","document_kind":"CONTRACT_BODY","trace_id":"trace-ledger-doc"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String documentAssetId = jsonString(document, "document_asset_id");
        String documentVersionId = jsonString(document, "document_version_id");

        String process = mockMvc.perform(post("/api/workflow-engine/processes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"business_object_type":"CONTRACT","contract_id":"%s","document_asset_id":"%s","document_version_id":"%s","starter_user_id":"u-ledger","trace_id":"trace-ledger-approval"}
                                """.formatted(contractId, documentAssetId, documentVersionId)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String processId = jsonString(process, "process_id");

        mockMvc.perform(get("/api/contracts")
                        .param("keyword", "台账详情测试合同"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.contract_id == '%s')].contract_no".formatted(contractId)).isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.contract_id == '%s')].contract_name".formatted(contractId)).value("台账详情测试合同"))
                .andExpect(jsonPath("$.items[?(@.contract_id == '%s')].contract_status".formatted(contractId)).value("UNDER_APPROVAL"))
                .andExpect(jsonPath("$.items[?(@.contract_id == '%s')].approval_summary.process_id".formatted(contractId)).value(processId));

        mockMvc.perform(get("/api/contracts/{contract_id}", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_master.contract_id").value(contractId))
                .andExpect(jsonPath("$.contract_master.contract_no").isNotEmpty())
                .andExpect(jsonPath("$.document_summary.document_asset_id").value(documentAssetId))
                .andExpect(jsonPath("$.document_summary.document_version_id").value(documentVersionId))
                .andExpect(jsonPath("$.approval_summary.process_id").value(processId))
                .andExpect(jsonPath("$.approval_summary.process_status").value("STARTED"))
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'CONTRACT_CREATED')]").isNotEmpty())
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'DOCUMENT_BOUND')]").isNotEmpty())
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'APPROVAL_STARTED')]").isNotEmpty());
    }

    @Test
    void createdContractBindsDocumentThenApprovalConsumesReferencesAndWritesBackStatus() throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"第二批契约测试合同","owner_user_id":"u-core-chain","owner_org_unit_id":"dept-core-chain","trace_id":"trace-batch2-core-chain"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contract_id").isNotEmpty())
                .andExpect(jsonPath("$.contract_status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");

        String document = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_name":"contract-main.docx","document_kind":"CONTRACT_BODY","trace_id":"trace-batch2-core-chain"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contract_id").value(contractId))
                .andExpect(jsonPath("$.document_asset_id").isNotEmpty())
                .andExpect(jsonPath("$.document_version_id").isNotEmpty())
                .andExpect(jsonPath("$.document_status").value("FIRST_VERSION_WRITTEN"))
                .andReturn().getResponse().getContentAsString();
        String documentAssetId = jsonString(document, "document_asset_id");
        String documentVersionId = jsonString(document, "document_version_id");

        mockMvc.perform(get("/api/contracts/{contract_id}/master", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_id").value(contractId))
                .andExpect(jsonPath("$.current_document.document_asset_id").value(documentAssetId))
                .andExpect(jsonPath("$.current_document.document_version_id").value(documentVersionId))
                .andExpect(jsonPath("$.timeline_event[?(@.event_type == 'DOCUMENT_BOUND')]").isNotEmpty())
                .andExpect(jsonPath("$.timeline_event[?(@.event_type == 'DOCUMENT_BOUND')].summary").value("文档已绑定合同当前主版本"));

        String process = mockMvc.perform(post("/api/workflow-engine/processes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"business_object_type":"CONTRACT","contract_id":"%s","document_asset_id":"%s","document_version_id":"%s","starter_user_id":"u-core-chain","trace_id":"trace-batch2-core-chain"}
                                """.formatted(contractId, documentAssetId, documentVersionId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.process_id").isNotEmpty())
                .andExpect(jsonPath("$.contract_id").value(contractId))
                .andExpect(jsonPath("$.document_asset_id").value(documentAssetId))
                .andExpect(jsonPath("$.document_version_id").value(documentVersionId))
                .andExpect(jsonPath("$.process_status").value("STARTED"))
                .andExpect(jsonPath("$.approval_summary.process_status").value("STARTED"))
                .andReturn().getResponse().getContentAsString();
        String processId = jsonString(process, "process_id");

        mockMvc.perform(get("/api/contracts/{contract_id}/master", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_status").value("UNDER_APPROVAL"))
                .andExpect(jsonPath("$.approval_summary.process_id").value(processId));

        mockMvc.perform(post("/api/workflow-engine/processes/{process_id}/results", processId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result":"APPROVED","operator_id":"approver-core-chain","trace_id":"trace-batch2-core-chain"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.process_id").value(processId))
                .andExpect(jsonPath("$.process_status").value("COMPLETED"))
                .andExpect(jsonPath("$.approval_summary.result").value("APPROVED"));

        mockMvc.perform(get("/api/contracts/{contract_id}/master", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_status").value("APPROVED"))
                .andExpect(jsonPath("$.approval_summary.process_id").value(processId))
                .andExpect(jsonPath("$.approval_summary.result").value("APPROVED"))
                .andExpect(jsonPath("$.timeline_event[?(@.event_type == 'APPROVAL_APPROVED')]").isNotEmpty())
                .andExpect(jsonPath("$.timeline_event[?(@.event_type == 'APPROVAL_APPROVED')].summary").value("审批通过并回写合同状态"));
    }

    private String jsonString(String json, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = json.indexOf(marker);
        assertThat(start).as("响应缺少字段: %s, body=%s", fieldName, json).isGreaterThanOrEqualTo(0);
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        return json.substring(valueStart, valueEnd);
    }
}

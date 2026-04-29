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
class Batch2CoreChainEndToEndTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void runsMinimumContractDocumentApprovalDetailChainWithBusinessSamples() throws Exception {
        String versionId = publishApprovalFlowSample();
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"第二批端到端样例合同","owner_user_id":"u-e2e-owner","owner_org_unit_id":"dept-e2e-sales","amount":"880000.00","currency":"CNY","trace_id":"trace-e2e-contract-create"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contract_status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");

        mockMvc.perform(patch("/api/contracts/{contract_id}", contractId)
                        .header("X-CMP-Permissions", "CONTRACT_EDIT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"第二批端到端样例合同-已编辑","amount":"890000.00","currency":"CNY","operator_user_id":"u-e2e-owner","trace_id":"trace-e2e-contract-edit"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_name").value("第二批端到端样例合同-已编辑"));

        String mainDocument = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"第二批端到端正文.docx","source_channel":"MANUAL_UPLOAD","file_upload_token":"sample-main-body-token","version_label":"正文首版","trace_id":"trace-e2e-main-document"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String documentAssetId = jsonString(mainDocument, "document_asset_id");
        String documentVersionId = jsonString(mainDocument, "document_version_id");

        String attachment = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"ATTACHMENT","document_title":"第二批端到端附件.pdf","source_channel":"MANUAL_UPLOAD","file_upload_token":"sample-attachment-token","version_label":"附件首版","trace_id":"trace-e2e-attachment"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String attachmentAssetId = jsonString(attachment, "document_asset_id");

        String approval = mockMvc.perform(post("/api/contracts/{contract_id}/approvals", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"process_version_id":"%s","document_asset_id":"%s","document_version_id":"%s","starter_user_id":"u-e2e-owner","trace_id":"trace-e2e-approval-start"}
                                """.formatted(versionId, documentAssetId, documentVersionId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.approval_mode").value("OA"))
                .andExpect(jsonPath("$.approval_summary.process_version_id").value(versionId))
                .andReturn().getResponse().getContentAsString();
        String processId = jsonString(approval, "process_id");
        String oaInstanceId = jsonString(approval, "oa_instance_id");

        mockMvc.perform(post("/api/workflow-engine/oa/callbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oa_instance_id":"%s","callback_event_id":"evt-e2e-approved","oa_status":"APPROVED","event_sequence":1,"trace_id":"trace-e2e-oa-approved"}
                                """.formatted(oaInstanceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callback_result").value("ACCEPTED"))
                .andExpect(jsonPath("$.approval_summary.process_version_id").value(versionId))
                .andExpect(jsonPath("$.approval_summary.final_result").value("APPROVED"));

        mockMvc.perform(get("/api/contracts/{contract_id}", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_master.contract_name").value("第二批端到端样例合同-已编辑"))
                .andExpect(jsonPath("$.contract_master.contract_status").value("APPROVED"))
                .andExpect(jsonPath("$.document_summary.document_asset_id").value(documentAssetId))
                .andExpect(jsonPath("$.document_summary.document_version_id").value(documentVersionId))
                .andExpect(jsonPath("$.attachment_summaries[?(@.document_asset_id == '%s')].document_title".formatted(attachmentAssetId)).value("第二批端到端附件.pdf"))
                .andExpect(jsonPath("$.approval_summary.process_id").value(processId))
                .andExpect(jsonPath("$.approval_summary.process_version_id").value(versionId))
                .andExpect(jsonPath("$.approval_summary.final_result").value("APPROVED"))
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'CONTRACT_CREATED')]").isNotEmpty())
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'CONTRACT_UPDATED')]").isNotEmpty())
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'DOCUMENT_BOUND')]").isNotEmpty())
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'ATTACHMENT_BOUND')]").isNotEmpty())
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'APPROVAL_APPROVED')]").isNotEmpty());
    }

    @Test
    void closesKeyExceptionLoopsForDocumentApprovalCallbackWritebackAndOrganizationResolution() throws Exception {
        String versionId = publishApprovalFlowSample();
        String contract = createContractSample("第二批异常闭环样例合同", "trace-exception-contract");
        String contractId = jsonString(contract, "contract_id");

        mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"写入失败正文.docx","file_upload_token":"sample-write-fail","simulate_document_write_failure":true,"trace_id":"trace-exception-document-write-failed"}
                                """.formatted(contractId)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error_code").value("DOCUMENT_WRITE_FAILED"))
                .andExpect(jsonPath("$.recovery_action").value("RETRY_DOCUMENT_WRITE"));

        mockMvc.perform(get("/api/contracts/{contract_id}/master", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_document").doesNotExist());

        String mainDocument = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"异常闭环正文.docx","file_upload_token":"sample-exception-main-v1","trace_id":"trace-exception-document-v1"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String documentAssetId = jsonString(mainDocument, "document_asset_id");
        String firstVersionId = jsonString(mainDocument, "document_version_id");
        String secondVersion = mockMvc.perform(post("/api/document-center/assets/{document_asset_id}/versions", documentAssetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"base_version_id":"%s","version_label":"异常闭环第二版","file_upload_token":"sample-exception-main-v2","trace_id":"trace-exception-document-v2"}
                                """.formatted(firstVersionId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String currentVersionId = jsonString(secondVersion, "document_version_id");

        mockMvc.perform(post("/api/contracts/{contract_id}/approvals", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"process_version_id":"%s","document_asset_id":"%s","document_version_id":"%s","starter_user_id":"u-exception-owner","trace_id":"trace-exception-stale-version"}
                                """.formatted(versionId, documentAssetId, firstVersionId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("DOCUMENT_VERSION_STALE"))
                .andExpect(jsonPath("$.current_document_version_id").value(currentVersionId));

        String rejectedApproval = mockMvc.perform(post("/api/contracts/{contract_id}/approvals", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"process_version_id":"%s","document_asset_id":"%s","document_version_id":"%s","starter_user_id":"u-exception-owner","trace_id":"trace-exception-reject-start"}
                                """.formatted(versionId, documentAssetId, currentVersionId)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String rejectedOaInstanceId = jsonString(rejectedApproval, "oa_instance_id");

        mockMvc.perform(post("/api/workflow-engine/oa/callbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oa_instance_id":"%s","callback_event_id":"evt-exception-rejected","oa_status":"REJECTED","event_sequence":1,"trace_id":"trace-exception-rejected"}
                                """.formatted(rejectedOaInstanceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callback_result").value("ACCEPTED"))
                .andExpect(jsonPath("$.approval_summary.final_result").value("REJECTED"));

        mockMvc.perform(post("/api/workflow-engine/oa/callbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oa_instance_id":"%s","callback_event_id":"evt-exception-rejected","oa_status":"REJECTED","event_sequence":1,"trace_id":"trace-exception-rejected-duplicate"}
                                """.formatted(rejectedOaInstanceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callback_result").value("DUPLICATE_IGNORED"));

        mockMvc.perform(get("/api/contracts/{contract_id}", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_master.contract_status").value("REJECTED"))
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'APPROVAL_REJECTED')]").isNotEmpty());

        String writebackFailureContract = createContractSample("第二批回写失败样例合同", "trace-exception-writeback-contract");
        String writebackFailureContractId = jsonString(writebackFailureContract, "contract_id");
        String writebackFailureDocument = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"回写失败正文.docx","file_upload_token":"sample-writeback-failure-main","trace_id":"trace-exception-writeback-document"}
                                """.formatted(writebackFailureContractId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String writebackFailureAssetId = jsonString(writebackFailureDocument, "document_asset_id");
        String writebackFailureVersionId = jsonString(writebackFailureDocument, "document_version_id");
        String writebackFailureApproval = mockMvc.perform(post("/api/contracts/{contract_id}/approvals", writebackFailureContractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"process_version_id":"%s","document_asset_id":"%s","document_version_id":"%s","starter_user_id":"u-writeback-fail","simulate_contract_writeback_failure":true,"trace_id":"trace-exception-writeback-start"}
                                """.formatted(versionId, writebackFailureAssetId, writebackFailureVersionId)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String writebackFailureOaInstanceId = jsonString(writebackFailureApproval, "oa_instance_id");

        mockMvc.perform(post("/api/workflow-engine/oa/callbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oa_instance_id":"%s","callback_event_id":"evt-exception-writeback-failed","oa_status":"APPROVED","event_sequence":1,"trace_id":"trace-exception-writeback-failed"}
                                """.formatted(writebackFailureOaInstanceId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.callback_result").value("WRITEBACK_COMPENSATING"))
                .andExpect(jsonPath("$.approval_summary.bridge_health.compensation_status").value("SUMMARY_COMPENSATING"));

        mockMvc.perform(get("/api/workflow-engine/compensation-tasks")
                        .param("contract_id", writebackFailureContractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].task_type").value("CONTRACT_APPROVAL_WRITEBACK"));

        String brokenDefinition = mockMvc.perform(post("/api/approval-engine/process-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"process_code":"E2E_BROKEN_ORG_RULE","process_name":"组织解析失败样例","business_type":"CONTRACT","approval_mode":"CMP","operator_user_id":"u-e2e-admin","organization_binding_required":true,"definition_payload":{"nodes":[{"node_key":"broken-org-rule","node_name":"无法解析组织规则","node_type":"APPROVAL","participant_mode":"SINGLE","bindings":[{"binding_type":"ORG_RULE","binding_object_id":"missing-sales-manager","binding_object_name":"缺失销售负责人规则"}]}]}}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String brokenDefinitionId = jsonString(brokenDefinition, "definition_id");

        mockMvc.perform(post("/api/approval-engine/process-definitions/{definition_id}/publish", brokenDefinitionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version_note":"组织解析失败版本","operator_user_id":"u-e2e-admin","trace_id":"trace-exception-org-resolution-failed"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("ORG_NODE_RESOLUTION_FAILED"))
                .andExpect(jsonPath("$.validation_errors[0].node_key").value("broken-org-rule"));
    }

    private String publishApprovalFlowSample() throws Exception {
        String definition = mockMvc.perform(post("/api/approval-engine/process-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"process_code":"E2E_CONTRACT_APPROVAL","process_name":"端到端合同审批样例","business_type":"CONTRACT","approval_mode":"OA","operator_user_id":"u-e2e-admin","organization_binding_required":true,"definition_payload":{"nodes":[{"node_key":"dept-review","node_name":"销售部门审批","node_type":"APPROVAL","participant_mode":"SINGLE","bindings":[{"binding_type":"USER","binding_object_id":"u-e2e-approver","binding_object_name":"端到端审批人"}]}]}}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String definitionId = jsonString(definition, "definition_id");

        String published = mockMvc.perform(post("/api/approval-engine/process-definitions/{definition_id}/publish", definitionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version_note":"端到端验证流程版本","operator_user_id":"u-e2e-admin","trace_id":"trace-e2e-flow-publish"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonString(published, "version_id");
    }

    private String createContractSample(String contractName, String traceId) throws Exception {
        return mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"%s","owner_user_id":"u-exception-owner","owner_org_unit_id":"dept-exception-owner","trace_id":"%s"}
                                """.formatted(contractName, traceId)))
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
}

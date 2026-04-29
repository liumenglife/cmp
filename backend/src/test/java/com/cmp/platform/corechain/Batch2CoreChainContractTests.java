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
    void documentCenterCreatesContractOwnedMainBodyAndAttachmentAssetsWithBindingAndAudit() throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"文档中心主档测试合同","owner_user_id":"u-doc-owner","owner_org_unit_id":"dept-doc-owner","trace_id":"trace-doc-contract"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");

        String mainBody = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"主正文.docx","source_channel":"MANUAL_UPLOAD","file_upload_token":"upload-main-body","trace_id":"trace-doc-main"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document_asset_id").isNotEmpty())
                .andExpect(jsonPath("$.current_version_id").isNotEmpty())
                .andExpect(jsonPath("$.binding_summary.owner_type").value("CONTRACT"))
                .andExpect(jsonPath("$.binding_summary.owner_id").value(contractId))
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'DOCUMENT_ASSET_CREATED')]").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String mainAssetId = jsonString(mainBody, "document_asset_id");
        String mainVersionId = jsonString(mainBody, "current_version_id");

        mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"ATTACHMENT","document_title":"附件清单.xlsx","source_channel":"MANUAL_UPLOAD","file_upload_token":"upload-attachment","trace_id":"trace-doc-attachment"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document_asset_id").isNotEmpty())
                .andExpect(jsonPath("$.current_version_id").isNotEmpty())
                .andExpect(jsonPath("$.document_role").value("ATTACHMENT"));

        mockMvc.perform(get("/api/document-center/assets/{document_asset_id}", mainAssetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document_asset_id").value(mainAssetId))
                .andExpect(jsonPath("$.owner_type").value("CONTRACT"))
                .andExpect(jsonPath("$.owner_id").value(contractId))
                .andExpect(jsonPath("$.document_role").value("MAIN_BODY"))
                .andExpect(jsonPath("$.document_title").value("主正文.docx"))
                .andExpect(jsonPath("$.current_version_id").value(mainVersionId));

        mockMvc.perform(get("/api/document-center/assets")
                        .param("owner_type", "CONTRACT")
                        .param("owner_id", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[?(@.document_asset_id == '%s')].document_role".formatted(mainAssetId)).value("MAIN_BODY"));
    }

    @Test
    void uploadingMainBodyCreatesDocumentFirstVersionAndRefreshesContractDocumentSummary() throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"正文挂接测试合同","owner_user_id":"u-main-body","owner_org_unit_id":"dept-main-body","trace_id":"trace-main-body-contract"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");

        String mainBody = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"合同主正文.docx","source_channel":"MANUAL_UPLOAD","file_upload_token":"upload-main-body-first-version","version_label":"正文首版","trace_id":"trace-main-body-upload"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document_asset_id").isNotEmpty())
                .andExpect(jsonPath("$.document_version_id").isNotEmpty())
                .andExpect(jsonPath("$.current_version_id").isNotEmpty())
                .andExpect(jsonPath("$.latest_version_no").value(1))
                .andExpect(jsonPath("$.document_role").value("MAIN_BODY"))
                .andExpect(jsonPath("$.document_title").value("合同主正文.docx"))
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'DOCUMENT_ASSET_CREATED')].trace_id").value("trace-main-body-upload"))
                .andReturn().getResponse().getContentAsString();
        String documentAssetId = jsonString(mainBody, "document_asset_id");
        String documentVersionId = jsonString(mainBody, "document_version_id");

        mockMvc.perform(get("/api/document-center/assets/{document_asset_id}/versions", documentAssetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].document_version_id").value(documentVersionId))
                .andExpect(jsonPath("$.items[0].version_no").value(1))
                .andExpect(jsonPath("$.items[0].version_label").value("正文首版"))
                .andExpect(jsonPath("$.items[0].version_status").value("ACTIVE"));

        mockMvc.perform(get("/api/contracts/{contract_id}/master", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_document.document_asset_id").value(documentAssetId))
                .andExpect(jsonPath("$.current_document.document_version_id").value(documentVersionId))
                .andExpect(jsonPath("$.current_document.effective_document_version_id").value(documentVersionId))
                .andExpect(jsonPath("$.current_document.document_role").value("MAIN_BODY"))
                .andExpect(jsonPath("$.current_document.document_title").value("合同主正文.docx"))
                .andExpect(jsonPath("$.current_document.latest_version_no").value(1))
                .andExpect(jsonPath("$.current_document.version_chain").doesNotExist())
                .andExpect(jsonPath("$.timeline_event[?(@.event_type == 'DOCUMENT_BOUND')].object_id").value(documentAssetId));
    }

    @Test
    void attachingMultipleDocumentsKeepsVersionTruthInDocumentCenterAndExposesAttachmentSummariesInContractDetail() throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"附件挂接测试合同","owner_user_id":"u-attachment","owner_org_unit_id":"dept-attachment","trace_id":"trace-attachment-contract"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");

        String mainBody = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"附件测试主正文.docx","source_channel":"MANUAL_UPLOAD","file_upload_token":"upload-attachment-main","trace_id":"trace-attachment-main"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String mainAssetId = jsonString(mainBody, "document_asset_id");
        String mainVersionId = jsonString(mainBody, "document_version_id");

        String firstAttachment = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"ATTACHMENT","document_title":"技术协议.pdf","source_channel":"MANUAL_UPLOAD","file_upload_token":"upload-attachment-technical","version_label":"技术协议首版","trace_id":"trace-attachment-technical"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstAttachmentAssetId = jsonString(firstAttachment, "document_asset_id");
        String firstAttachmentVersionId = jsonString(firstAttachment, "document_version_id");

        String secondAttachment = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"ATTACHMENT","document_title":"报价清单.xlsx","source_channel":"MANUAL_UPLOAD","file_upload_token":"upload-attachment-price","version_label":"报价清单首版","trace_id":"trace-attachment-price"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondAttachmentAssetId = jsonString(secondAttachment, "document_asset_id");
        String secondAttachmentVersionId = jsonString(secondAttachment, "document_version_id");

        mockMvc.perform(get("/api/contracts/{contract_id}", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document_summary.document_asset_id").value(mainAssetId))
                .andExpect(jsonPath("$.document_summary.effective_document_version_id").value(mainVersionId))
                .andExpect(jsonPath("$.attachment_summaries.length()").value(2))
                .andExpect(jsonPath("$.attachment_summaries[?(@.document_asset_id == '%s')].document_title".formatted(firstAttachmentAssetId)).value("技术协议.pdf"))
                .andExpect(jsonPath("$.attachment_summaries[?(@.document_asset_id == '%s')].effective_document_version_id".formatted(firstAttachmentAssetId)).value(firstAttachmentVersionId))
                .andExpect(jsonPath("$.attachment_summaries[?(@.document_asset_id == '%s')].document_title".formatted(secondAttachmentAssetId)).value("报价清单.xlsx"))
                .andExpect(jsonPath("$.attachment_summaries[?(@.document_asset_id == '%s')].effective_document_version_id".formatted(secondAttachmentAssetId)).value(secondAttachmentVersionId))
                .andExpect(jsonPath("$.attachment_summaries[0].version_chain").doesNotExist())
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'ATTACHMENT_BOUND')]").isNotEmpty());

        mockMvc.perform(get("/api/document-center/assets/{document_asset_id}/versions", firstAttachmentAssetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].document_version_id").value(firstAttachmentVersionId))
                .andExpect(jsonPath("$.items[0].version_label").value("技术协议首版"));

        mockMvc.perform(get("/api/contracts/{contract_id}/master", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_document.document_asset_id").value(mainAssetId))
                .andExpect(jsonPath("$.current_document.effective_document_version_id").value(mainVersionId));
    }

    @Test
    void switchingMainBodyVersionRefreshesContractSummaryAndTimelineWithoutCopyingVersionChain() throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"正文版本切换测试合同","owner_user_id":"u-main-switch","owner_org_unit_id":"dept-main-switch","trace_id":"trace-main-switch-contract"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");

        String mainBody = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"版本切换正文.docx","source_channel":"MANUAL_UPLOAD","file_upload_token":"upload-main-switch-v1","version_label":"V1","trace_id":"trace-main-switch-v1"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String documentAssetId = jsonString(mainBody, "document_asset_id");
        String firstVersionId = jsonString(mainBody, "document_version_id");

        String secondVersion = mockMvc.perform(post("/api/document-center/assets/{document_asset_id}/versions", documentAssetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"base_version_id":"%s","change_reason":"调整交付范围","version_label":"V2","file_upload_token":"upload-main-switch-v2","source_channel":"MANUAL_UPLOAD","trace_id":"trace-main-switch-v2"}
                                """.formatted(firstVersionId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondVersionId = jsonString(secondVersion, "document_version_id");

        mockMvc.perform(get("/api/contracts/{contract_id}/master", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_document.document_asset_id").value(documentAssetId))
                .andExpect(jsonPath("$.current_document.effective_document_version_id").value(secondVersionId))
                .andExpect(jsonPath("$.current_document.latest_version_no").value(2))
                .andExpect(jsonPath("$.current_document.version_chain").doesNotExist())
                .andExpect(jsonPath("$.timeline_event[?(@.event_type == 'DOCUMENT_MAIN_VERSION_SWITCHED')].object_id").value(secondVersionId))
                .andExpect(jsonPath("$.timeline_event[?(@.event_type == 'DOCUMENT_MAIN_VERSION_SWITCHED')].trace_id").value("trace-main-switch-v2"));

        mockMvc.perform(post("/api/document-center/versions/{document_version_id}/activate", firstVersionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trace_id":"trace-main-switch-back-v1"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/contracts/{contract_id}/master", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_document.effective_document_version_id").value(firstVersionId))
                .andExpect(jsonPath("$.timeline_event[?(@.event_type == 'DOCUMENT_MAIN_VERSION_SWITCHED' && @.object_id == '%s')].trace_id".formatted(firstVersionId)).value("trace-main-switch-back-v1"));
    }

    @Test
    void documentCenterAppendsQueriesAndActivatesVersionsWithSingleCurrentVersion() throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"文档版本链测试合同","owner_user_id":"u-version-owner","owner_org_unit_id":"dept-version-owner","trace_id":"trace-version-contract"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");

        String asset = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"版本正文.docx","source_channel":"MANUAL_UPLOAD","file_upload_token":"upload-version-v1","trace_id":"trace-version-v1"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String documentAssetId = jsonString(asset, "document_asset_id");
        String firstVersionId = jsonString(asset, "current_version_id");

        String secondVersion = mockMvc.perform(post("/api/document-center/assets/{document_asset_id}/versions", documentAssetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"base_version_id":"%s","change_reason":"补充付款条款","version_label":"V2","file_upload_token":"upload-version-v2","source_channel":"MANUAL_UPLOAD","trace_id":"trace-version-v2"}
                                """.formatted(firstVersionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document_version_id").isNotEmpty())
                .andExpect(jsonPath("$.version_no").value(2))
                .andExpect(jsonPath("$.version_status").value("ACTIVE"))
                .andExpect(jsonPath("$.is_current_version").value(true))
                .andReturn().getResponse().getContentAsString();
        String secondVersionId = jsonString(secondVersion, "document_version_id");

        mockMvc.perform(get("/api/document-center/assets/{document_asset_id}/versions", documentAssetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[?(@.document_version_id == '%s')].is_current_version".formatted(firstVersionId)).value(false))
                .andExpect(jsonPath("$.items[?(@.document_version_id == '%s')].is_current_version".formatted(secondVersionId)).value(true));

        mockMvc.perform(get("/api/document-center/versions/{document_version_id}", secondVersionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document_asset_id").value(documentAssetId))
                .andExpect(jsonPath("$.document_version_id").value(secondVersionId))
                .andExpect(jsonPath("$.base_version_id").value(firstVersionId))
                .andExpect(jsonPath("$.change_reason").value("补充付款条款"));

        mockMvc.perform(post("/api/document-center/versions/{document_version_id}/activate", firstVersionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trace_id":"trace-version-activate-v1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document_asset_id").value(documentAssetId))
                .andExpect(jsonPath("$.current_version_id").value(firstVersionId));

        mockMvc.perform(get("/api/document-center/assets/{document_asset_id}/versions", documentAssetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.document_version_id == '%s')].is_current_version".formatted(firstVersionId)).value(true))
                .andExpect(jsonPath("$.items[?(@.document_version_id == '%s')].is_current_version".formatted(secondVersionId)).value(false));

        mockMvc.perform(get("/api/document-center/assets/{document_asset_id}", documentAssetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_version_id").value(firstVersionId));
    }

    @Test
    void workflowDefinitionPublishesVersionOnlyWhenApprovalNodesHaveOrganizationBinding() throws Exception {
        String definition = mockMvc.perform(post("/api/approval-engine/process-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"process_code":"CONTRACT_APPROVAL_BOUND","process_name":"合同审批流程","business_type":"CONTRACT","approval_mode":"CMP","operator_user_id":"u-workflow-admin","organization_binding_required":true,"definition_payload":{"nodes":[{"node_key":"start","node_name":"开始","node_type":"START"},{"node_key":"dept-review","node_name":"部门审批","node_type":"APPROVAL","participant_mode":"SINGLE","bindings":[{"binding_type":"USER","binding_object_id":"u-approver-1","binding_object_name":"审批人一"}]},{"node_key":"end","node_name":"结束","node_type":"END"}]}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.definition_id").isNotEmpty())
                .andExpect(jsonPath("$.process_code").value("CONTRACT_APPROVAL_BOUND"))
                .andExpect(jsonPath("$.definition_status").value("DRAFTING"))
                .andExpect(jsonPath("$.current_draft_version.version_status").value("DRAFT"))
                .andExpect(jsonPath("$.organization_binding_required").value(true))
                .andReturn().getResponse().getContentAsString();
        String definitionId = jsonString(definition, "definition_id");

        mockMvc.perform(post("/api/approval-engine/process-definitions/{definition_id}/publish", definitionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version_note":"首个可运行版本","publish_comment":"组织绑定已校验","operator_user_id":"u-workflow-admin","trace_id":"trace-workflow-publish"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definition_id").value(definitionId))
                .andExpect(jsonPath("$.definition_status").value("PUBLISHED"))
                .andExpect(jsonPath("$.latest_published_version.version_no").value(1))
                .andExpect(jsonPath("$.latest_published_version.version_status").value("PUBLISHED"))
                .andExpect(jsonPath("$.latest_published_version.version_snapshot.nodes[?(@.node_key == 'dept-review')].bindings[0].binding_object_id").value("u-approver-1"))
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'PROCESS_VERSION_PUBLISHED')].trace_id").value("trace-workflow-publish"));

        mockMvc.perform(post("/api/approval-engine/process-definitions/{definition_id}/disable", definitionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operator_user_id":"u-workflow-admin","trace_id":"trace-workflow-disable"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definition_status").value("DISABLED"))
                .andExpect(jsonPath("$.latest_published_version.version_status").value("DISABLED"))
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'PROCESS_VERSION_DISABLED')].trace_id").value("trace-workflow-disable"));

        String unboundDefinition = mockMvc.perform(post("/api/approval-engine/process-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"process_code":"CONTRACT_APPROVAL_UNBOUND","process_name":"未绑定审批流程","business_type":"CONTRACT","approval_mode":"CMP","operator_user_id":"u-workflow-admin","organization_binding_required":true,"definition_payload":{"nodes":[{"node_key":"review","node_name":"无组织审批","node_type":"APPROVAL","participant_mode":"SINGLE"}]}}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String unboundDefinitionId = jsonString(unboundDefinition, "definition_id");

        mockMvc.perform(post("/api/approval-engine/process-definitions/{definition_id}/publish", unboundDefinitionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version_note":"缺少绑定版本","operator_user_id":"u-workflow-admin","trace_id":"trace-workflow-publish-failed"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("WORKFLOW_NODE_BINDING_REQUIRED"))
                .andExpect(jsonPath("$.validation_errors[0].node_key").value("review"));
    }

    @Test
    void workflowRuntimeStartsPublishedProcessAndAdvancesSerialParallelCountersignRejectAndTerminatePaths() throws Exception {
        String serialVersionId = publishWorkflowDefinition("CONTRACT_APPROVAL_SERIAL", "serial-review", "SINGLE",
                "[{\"binding_type\":\"USER\",\"binding_object_id\":\"u-serial-1\",\"binding_object_name\":\"串行一审\"},{\"binding_type\":\"USER\",\"binding_object_id\":\"u-serial-2\",\"binding_object_name\":\"串行二审\"}]");
        String serialProcess = mockMvc.perform(post("/api/approval-engine/processes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_id":"ctr-workflow-serial","version_id":"%s","approval_mode":"CMP","starter_user_id":"u-starter","business_context":{"business_title":"串行审批合同"},"trace_id":"trace-runtime-serial-start"}
                                """.formatted(serialVersionId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.process_id").isNotEmpty())
                .andExpect(jsonPath("$.instance_status").value("RUNNING"))
                .andExpect(jsonPath("$.task_center_items[0].assignee_user_id").value("u-serial-1"))
                .andExpect(jsonPath("$.approval_summary.summary_status").value("RUNNING"))
                .andReturn().getResponse().getContentAsString();
        String serialProcessId = jsonString(serialProcess, "process_id");
        String firstSerialTaskId = jsonString(serialProcess, "task_id");

        String firstApproval = mockMvc.perform(post("/api/approval-engine/processes/{process_id}/actions", serialProcessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"task_id":"%s","action_type":"APPROVE","operator_user_id":"u-serial-1","comment":"一审通过","trace_id":"trace-runtime-serial-approve-1"}
                                """.formatted(firstSerialTaskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instance_status").value("RUNNING"))
                .andExpect(jsonPath("$.next_tasks[0].assignee_user_id").value("u-serial-2"))
                .andReturn().getResponse().getContentAsString();
        String secondSerialTaskId = jsonString(firstApproval, "task_id");

        mockMvc.perform(post("/api/approval-engine/processes/{process_id}/actions", serialProcessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"task_id":"%s","action_type":"APPROVE","operator_user_id":"u-serial-2","comment":"二审通过","trace_id":"trace-runtime-serial-approve-2"}
                                """.formatted(secondSerialTaskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instance_status").value("COMPLETED"))
                .andExpect(jsonPath("$.approval_summary.summary_status").value("COMPLETED"))
                .andExpect(jsonPath("$.approval_summary.latest_action.action_type").value("APPROVE"));

        String rejectVersionId = publishWorkflowDefinition("CONTRACT_APPROVAL_REJECT", "reject-review", "SINGLE",
                "[{\"binding_type\":\"USER\",\"binding_object_id\":\"u-reject-1\",\"binding_object_name\":\"驳回审批人\"}]");
        String rejectProcess = startWorkflowProcess("ctr-workflow-reject", rejectVersionId, "trace-runtime-reject-start");
        String rejectProcessId = jsonString(rejectProcess, "process_id");
        String rejectTaskId = jsonString(rejectProcess, "task_id");

        mockMvc.perform(post("/api/approval-engine/processes/{process_id}/actions", rejectProcessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"task_id":"%s","action_type":"REJECT","operator_user_id":"u-reject-1","comment":"资料不完整","trace_id":"trace-runtime-reject"}
                                """.formatted(rejectTaskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instance_status").value("REJECTED"))
                .andExpect(jsonPath("$.approval_summary.summary_status").value("REJECTED"));

        String terminateProcess = startWorkflowProcess("ctr-workflow-terminate", rejectVersionId, "trace-runtime-terminate-start");
        String terminateProcessId = jsonString(terminateProcess, "process_id");
        String terminateTaskId = jsonString(terminateProcess, "task_id");

        mockMvc.perform(post("/api/approval-engine/processes/{process_id}/actions", terminateProcessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"task_id":"%s","action_type":"TERMINATE","operator_user_id":"u-admin","comment":"管理员终止","trace_id":"trace-runtime-terminate"}
                                """.formatted(terminateTaskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instance_status").value("TERMINATED"))
                .andExpect(jsonPath("$.approval_summary.summary_status").value("TERMINATED"));

        String parallelVersionId = publishWorkflowDefinition("CONTRACT_APPROVAL_PARALLEL", "parallel-review", "PARALLEL",
                "[{\"binding_type\":\"USER\",\"binding_object_id\":\"u-parallel-1\",\"binding_object_name\":\"并行一审\"},{\"binding_type\":\"USER\",\"binding_object_id\":\"u-parallel-2\",\"binding_object_name\":\"并行二审\"}]");
        String parallelProcess = startWorkflowProcess("ctr-workflow-parallel", parallelVersionId, "trace-runtime-parallel-start");
        String parallelProcessId = jsonString(parallelProcess, "process_id");

        mockMvc.perform(get("/api/approval-engine/tasks")
                        .param("process_id", parallelProcessId)
                        .param("task_status", "PENDING_ACTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[?(@.assignee_user_id == 'u-parallel-1')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.assignee_user_id == 'u-parallel-2')]").isNotEmpty());
        String firstParallelTaskId = jsonString(parallelProcess, "task_id");

        mockMvc.perform(post("/api/approval-engine/processes/{process_id}/actions", parallelProcessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"task_id":"%s","action_type":"APPROVE","operator_user_id":"u-parallel-1","trace_id":"trace-runtime-parallel-approve-1"}
                                """.formatted(firstParallelTaskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instance_status").value("RUNNING"));
        String secondParallelTaskId = jsonString(mockMvc.perform(get("/api/approval-engine/tasks")
                        .param("process_id", parallelProcessId)
                        .param("task_status", "PENDING_ACTION"))
                .andReturn().getResponse().getContentAsString(), "task_id");

        mockMvc.perform(post("/api/approval-engine/processes/{process_id}/actions", parallelProcessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"task_id":"%s","action_type":"APPROVE","operator_user_id":"u-parallel-2","trace_id":"trace-runtime-parallel-approve-2"}
                                """.formatted(secondParallelTaskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instance_status").value("COMPLETED"));

        String countersignVersionId = publishWorkflowDefinition("CONTRACT_APPROVAL_COUNTERSIGN", "countersign-review", "COUNTERSIGN",
                "[{\"binding_type\":\"USER\",\"binding_object_id\":\"u-countersign-1\",\"binding_object_name\":\"会签一审\"},{\"binding_type\":\"USER\",\"binding_object_id\":\"u-countersign-2\",\"binding_object_name\":\"会签二审\"}]");
        String countersignProcess = startWorkflowProcess("ctr-workflow-countersign", countersignVersionId, "trace-runtime-countersign-start");
        String countersignProcessId = jsonString(countersignProcess, "process_id");
        String firstCountersignTaskId = jsonString(countersignProcess, "task_id");

        mockMvc.perform(post("/api/approval-engine/processes/{process_id}/actions", countersignProcessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"task_id":"%s","action_type":"COUNTERSIGN_PASS","operator_user_id":"u-countersign-1","trace_id":"trace-runtime-countersign-pass-1"}
                                """.formatted(firstCountersignTaskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instance_status").value("RUNNING"));
        String secondCountersignTaskId = jsonString(mockMvc.perform(get("/api/approval-engine/tasks")
                        .param("process_id", countersignProcessId)
                        .param("task_status", "PENDING_ACTION"))
                .andReturn().getResponse().getContentAsString(), "task_id");

        mockMvc.perform(post("/api/approval-engine/processes/{process_id}/actions", countersignProcessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"task_id":"%s","action_type":"COUNTERSIGN_PASS","operator_user_id":"u-countersign-2","trace_id":"trace-runtime-countersign-pass-2"}
                                """.formatted(secondCountersignTaskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instance_status").value("COMPLETED"))
                .andExpect(jsonPath("$.approval_actions[?(@.action_type == 'COUNTERSIGN_PASS')]").isNotEmpty());
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

    @Test
    void unifiedApprovalEntryCanUsePlatformTakeoverAndWriteBackApprovedStatusAndTimeline() throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"平台承接审批合同","owner_user_id":"u-platform-owner","owner_org_unit_id":"dept-platform-owner","trace_id":"trace-platform-contract"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contract_status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");

        String document = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"平台审批正文.docx","trace_id":"trace-platform-document"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String documentAssetId = jsonString(document, "document_asset_id");
        String documentVersionId = jsonString(document, "document_version_id");

        String approval = mockMvc.perform(post("/api/contracts/{contract_id}/approvals", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"acceptance_strategy":"CMP_WORKFLOW","document_asset_id":"%s","document_version_id":"%s","starter_user_id":"u-platform-owner","trace_id":"trace-platform-approval-start"}
                                """.formatted(documentAssetId, documentVersionId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.approval_mode").value("CMP_WORKFLOW"))
                .andExpect(jsonPath("$.approval_summary.summary_status").value("RUNNING"))
                .andReturn().getResponse().getContentAsString();
        String processId = jsonString(approval, "process_id");

        mockMvc.perform(get("/api/contracts/{contract_id}/master", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_status").value("UNDER_APPROVAL"))
                .andExpect(jsonPath("$.approval_summary.process_id").value(processId))
                .andExpect(jsonPath("$.approval_summary.approval_mode").value("CMP_WORKFLOW"))
                .andExpect(jsonPath("$.timeline_event[?(@.event_type == 'APPROVAL_STARTED')].trace_id").value("trace-platform-approval-start"));

        mockMvc.perform(post("/api/workflow-engine/approvals/{process_id}/results", processId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result":"APPROVED","operator_user_id":"u-platform-approver","trace_id":"trace-platform-approval-approved"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_summary.summary_status").value("COMPLETED"))
                .andExpect(jsonPath("$.approval_summary.final_result").value("APPROVED"));

        mockMvc.perform(get("/api/contracts/{contract_id}/master", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_status").value("APPROVED"))
                .andExpect(jsonPath("$.approval_summary.process_id").value(processId))
                .andExpect(jsonPath("$.approval_summary.approval_mode").value("CMP_WORKFLOW"))
                .andExpect(jsonPath("$.approval_summary.summary_status").value("COMPLETED"))
                .andExpect(jsonPath("$.approval_summary.final_result").value("APPROVED"))
                .andExpect(jsonPath("$.timeline_event[?(@.event_type == 'APPROVAL_APPROVED')].trace_id").value("trace-platform-approval-approved"));
    }

    @Test
    void unifiedApprovalEntryDefaultsToOaBridgeAndHandlesCallbacksIdempotencyOrderingSummaryAndCompensation() throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"OA桥接审批合同","owner_user_id":"u-oa-owner","owner_org_unit_id":"dept-oa-owner","trace_id":"trace-oa-contract"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");

        String document = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"OA审批正文.docx","trace_id":"trace-oa-document"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String documentAssetId = jsonString(document, "document_asset_id");
        String documentVersionId = jsonString(document, "document_version_id");

        String approval = mockMvc.perform(post("/api/contracts/{contract_id}/approvals", contractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_asset_id":"%s","document_version_id":"%s","starter_user_id":"u-oa-owner","trace_id":"trace-oa-approval-start"}
                                """.formatted(documentAssetId, documentVersionId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.approval_mode").value("OA"))
                .andExpect(jsonPath("$.oa_instance_id").isNotEmpty())
                .andExpect(jsonPath("$.approval_summary.summary_status").value("WAITING_CALLBACK"))
                .andReturn().getResponse().getContentAsString();
        String processId = jsonString(approval, "process_id");
        String oaInstanceId = jsonString(approval, "oa_instance_id");

        mockMvc.perform(post("/api/workflow-engine/oa/callbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oa_instance_id":"%s","callback_event_id":"evt-oa-approved","oa_status":"APPROVED","event_sequence":2,"trace_id":"trace-oa-approved"}
                                """.formatted(oaInstanceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callback_result").value("ACCEPTED"))
                .andExpect(jsonPath("$.approval_summary.summary_status").value("COMPLETED"))
                .andExpect(jsonPath("$.approval_summary.final_result").value("APPROVED"));

        mockMvc.perform(post("/api/workflow-engine/oa/callbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oa_instance_id":"%s","callback_event_id":"evt-oa-approved","oa_status":"APPROVED","event_sequence":2,"trace_id":"trace-oa-approved-duplicate"}
                                """.formatted(oaInstanceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callback_result").value("DUPLICATE_IGNORED"))
                .andExpect(jsonPath("$.approval_summary.summary_status").value("COMPLETED"));

        mockMvc.perform(post("/api/workflow-engine/oa/callbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oa_instance_id":"%s","callback_event_id":"evt-oa-running-late","oa_status":"APPROVING","event_sequence":1,"trace_id":"trace-oa-out-of-order"}
                                """.formatted(oaInstanceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callback_result").value("OUT_OF_ORDER_IGNORED"))
                .andExpect(jsonPath("$.approval_summary.summary_status").value("COMPLETED"));

        mockMvc.perform(get("/api/workflow-engine/approvals/{process_id}/summary", processId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.process_id").value(processId))
                .andExpect(jsonPath("$.approval_mode").value("OA"))
                .andExpect(jsonPath("$.summary_status").value("COMPLETED"))
                .andExpect(jsonPath("$.final_result").value("APPROVED"))
                .andExpect(jsonPath("$.bridge_health.compensation_status").value("HEALTHY"));

        mockMvc.perform(get("/api/contracts/{contract_id}", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_summary.process_id").value(processId))
                .andExpect(jsonPath("$.approval_summary.approval_mode").value("OA"))
                .andExpect(jsonPath("$.contract_master.contract_status").value("APPROVED"))
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'APPROVAL_APPROVED')].trace_id").value("trace-oa-approved"));

        mockMvc.perform(get("/api/contracts")
                        .param("keyword", "OA桥接审批合同"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.contract_id == '%s')].approval_summary.process_id".formatted(contractId)).value(processId))
                .andExpect(jsonPath("$.items[?(@.contract_id == '%s')].approval_summary.approval_mode".formatted(contractId)).value("OA"));

        String failingContract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"OA回写补偿合同","owner_user_id":"u-oa-fail","owner_org_unit_id":"dept-oa-fail","trace_id":"trace-oa-fail-contract"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String failingContractId = jsonString(failingContract, "contract_id");
        String failingDocument = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"OA补偿正文.docx","trace_id":"trace-oa-fail-document"}
                                """.formatted(failingContractId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String failingAssetId = jsonString(failingDocument, "document_asset_id");
        String failingVersionId = jsonString(failingDocument, "document_version_id");
        String failingApproval = mockMvc.perform(post("/api/contracts/{contract_id}/approvals", failingContractId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_asset_id":"%s","document_version_id":"%s","starter_user_id":"u-oa-fail","simulate_contract_writeback_failure":true,"trace_id":"trace-oa-fail-start"}
                                """.formatted(failingAssetId, failingVersionId)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String failingOaInstanceId = jsonString(failingApproval, "oa_instance_id");

        mockMvc.perform(post("/api/workflow-engine/oa/callbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oa_instance_id":"%s","callback_event_id":"evt-oa-fail-approved","oa_status":"APPROVED","event_sequence":1,"trace_id":"trace-oa-fail-approved"}
                                """.formatted(failingOaInstanceId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.callback_result").value("WRITEBACK_COMPENSATING"))
                .andExpect(jsonPath("$.approval_summary.summary_status").value("COMPLETED"))
                .andExpect(jsonPath("$.approval_summary.bridge_health.compensation_status").value("SUMMARY_COMPENSATING"));

        mockMvc.perform(get("/api/workflow-engine/compensation-tasks")
                        .param("contract_id", failingContractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].task_type").value("CONTRACT_APPROVAL_WRITEBACK"))
                .andExpect(jsonPath("$.items[0].compensation_status").value("PENDING_RETRY"));
    }

    private String publishWorkflowDefinition(String processCode, String nodeKey, String participantMode, String bindingsJson) throws Exception {
        String definition = mockMvc.perform(post("/api/approval-engine/process-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"process_code":"%s","process_name":"运行时流程","business_type":"CONTRACT","approval_mode":"CMP","operator_user_id":"u-workflow-admin","organization_binding_required":true,"definition_payload":{"nodes":[{"node_key":"%s","node_name":"审批节点","node_type":"APPROVAL","participant_mode":"%s","bindings":%s}]}}
                                """.formatted(processCode, nodeKey, participantMode, bindingsJson)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String definitionId = jsonString(definition, "definition_id");

        String published = mockMvc.perform(post("/api/approval-engine/process-definitions/{definition_id}/publish", definitionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version_note":"运行时测试版本","operator_user_id":"u-workflow-admin","trace_id":"trace-runtime-publish"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonString(published, "version_id");
    }

    private String startWorkflowProcess(String contractId, String versionId, String traceId) throws Exception {
        return mockMvc.perform(post("/api/approval-engine/processes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_id":"%s","version_id":"%s","approval_mode":"CMP","starter_user_id":"u-starter","business_context":{"business_title":"运行时审批合同"},"trace_id":"%s"}
                                """.formatted(contractId, versionId, traceId)))
                .andExpect(status().isAccepted())
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

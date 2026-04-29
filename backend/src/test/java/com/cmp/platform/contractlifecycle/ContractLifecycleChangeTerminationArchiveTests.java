package com.cmp.platform.contractlifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ContractLifecycleChangeTerminationArchiveTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanLifecycleTables() {
        jdbcTemplate.update("DELETE FROM cl_lifecycle_process_ref");
        jdbcTemplate.update("DELETE FROM cl_archive_borrow_record");
        jdbcTemplate.update("DELETE FROM cl_archive_record");
        jdbcTemplate.update("DELETE FROM cl_contract_termination");
        jdbcTemplate.update("DELETE FROM cl_contract_change");
        jdbcTemplate.update("DELETE FROM cl_lifecycle_audit_event");
        jdbcTemplate.update("DELETE FROM cl_lifecycle_timeline_event");
        jdbcTemplate.update("DELETE FROM cl_lifecycle_document_ref");
        jdbcTemplate.update("DELETE FROM cl_lifecycle_summary");
        jdbcTemplate.update("DELETE FROM cl_performance_node");
        jdbcTemplate.update("DELETE FROM cl_performance_record");
    }

    @Test
    void changeApprovalAppliesSupplementAgreementAndWritesBackCurrentEffectiveSummary() throws Exception {
        LifecycleSample sample = createPerformedContractSample("变更主链合同", "trace-cl-change");
        String agreement = createDocument(sample.contractId(), "CHANGE_AGREEMENT", "补充协议.pdf", "trace-cl-change-agreement");

        String change = mockMvc.perform(post("/api/contracts/{contract_id}/changes", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"change_type":"AMOUNT_AND_TERM","change_reason":"客户扩大采购范围","change_summary":"合同金额调整为 120000，期限延长至 2026-12-31","impact_scope":{"amount":"120000","term_end":"2026-12-31","impact_level":"HIGH"},"effective_date":"2026-06-01","supplemental_document_asset_id":"%s","supplemental_document_version_id":"%s","workflow_instance_id":"wf-change-approval-1","operator_user_id":"u-change-owner","trace_id":"trace-cl-change-apply"}
                                """.formatted(jsonString(agreement, "document_asset_id"), jsonString(agreement, "document_version_id"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.change_id").exists())
                .andExpect(jsonPath("$.contract_id").value(sample.contractId()))
                .andExpect(jsonPath("$.change_status").value("APPROVING"))
                .andExpect(jsonPath("$.process_ref.workflow_instance_id").value("wf-change-approval-1"))
                .andExpect(jsonPath("$.document_ref.document_role").value("CHANGE_AGREEMENT"))
                .andReturn().getResponse().getContentAsString();
        String changeId = jsonString(change, "change_id");

        mockMvc.perform(post("/api/contracts/{contract_id}/changes/{change_id}/approval-results", sample.contractId(), changeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approval_result":"APPROVED","workflow_instance_id":"wf-change-approval-1","approved_at":"2026-06-01T10:00:00Z","result_summary":"补充协议已生效","operator_user_id":"u-change-approver","trace_id":"trace-cl-change-approved"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.change_status").value("APPLIED"))
                .andExpect(jsonPath("$.change_summary.current_effective_summary").value("补充协议已生效"))
                .andExpect(jsonPath("$.change_summary.latest_change_id").value(changeId));

        mockMvc.perform(get("/api/contracts/{contract_id}", sample.contractId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_master.contract_status").value("CHANGED"))
                .andExpect(jsonPath("$.contract_master.change_summary.change_status").value("APPLIED"))
                .andExpect(jsonPath("$.lifecycle_summary.change_summary.latest_change_id").value(changeId))
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'CHANGE_APPLIED')]", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'CHANGE_APPLIED')]", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void terminationApprovalWritesBackSettlementAccessRestrictionAndBlocksLaterChange() throws Exception {
        LifecycleSample sample = createPerformedContractSample("终止主链合同", "trace-cl-termination");
        String material = createDocument(sample.contractId(), "TERMINATION_AGREEMENT", "终止协议.pdf", "trace-cl-termination-material");

        String termination = mockMvc.perform(post("/api/contracts/{contract_id}/terminations", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"termination_type":"MUTUAL_AGREEMENT","termination_reason":"双方协商提前终止","termination_summary":"终止后进入结算与归档","requested_termination_date":"2026-07-01","settlement_summary":"尾款已结清，售后义务关闭","material_document_asset_id":"%s","material_document_version_id":"%s","workflow_instance_id":"wf-term-approval-1","operator_user_id":"u-term-owner","trace_id":"trace-cl-term-apply"}
                                """.formatted(jsonString(material, "document_asset_id"), jsonString(material, "document_version_id"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.termination_status").value("APPROVING"))
                .andExpect(jsonPath("$.document_ref.document_role").value("TERMINATION_AGREEMENT"))
                .andReturn().getResponse().getContentAsString();
        String terminationId = jsonString(termination, "termination_id");

        mockMvc.perform(post("/api/contracts/{contract_id}/terminations/{termination_id}/approval-results", sample.contractId(), terminationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approval_result":"APPROVED","workflow_instance_id":"wf-term-approval-1","terminated_at":"2026-07-01T18:00:00Z","post_action_status":"SETTLED","settlement_summary":"尾款已结清，资料已移交","access_restriction":"READ_ONLY_AFTER_TERMINATION","operator_user_id":"u-term-approver","trace_id":"trace-cl-term-approved"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termination_status").value("TERMINATED"))
                .andExpect(jsonPath("$.termination_summary.post_action_status").value("SETTLED"))
                .andExpect(jsonPath("$.termination_summary.access_restriction").value("READ_ONLY_AFTER_TERMINATION"));

        mockMvc.perform(post("/api/contracts/{contract_id}/changes", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"change_type":"TERM","change_reason":"终止后尝试变更","change_summary":"不应允许","effective_date":"2026-08-01","workflow_instance_id":"wf-change-denied","operator_user_id":"u-change-owner","trace_id":"trace-cl-change-after-term"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("CONTRACT_TERMINATED_CHANGE_RESTRICTED"));

        mockMvc.perform(get("/api/contracts/{contract_id}", sample.contractId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_master.contract_status").value("TERMINATED"))
                .andExpect(jsonPath("$.contract_master.termination_summary.latest_termination_id").value(terminationId))
                .andExpect(jsonPath("$.lifecycle_summary.termination_summary.access_restriction").value("READ_ONLY_AFTER_TERMINATION"))
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'TERMINATION_COMPLETED')]", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void archiveValidatesInputSetRecordsPackageReferenceAndSupportsBorrowReturnEntry() throws Exception {
        LifecycleSample sample = createTerminatedContractSample("归档主链合同", "trace-cl-archive");
        String packageDocument = createDocument(sample.contractId(), "ARCHIVE_PACKAGE", "归档封包.zip", "trace-cl-archive-package");
        String manifestDocument = createDocument(sample.contractId(), "ARCHIVE_MANIFEST", "归档清单.json", "trace-cl-archive-manifest");

        mockMvc.perform(post("/api/contracts/{contract_id}/archives", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"archive_batch_no":"ARCH-EMPTY","archive_type":"FINAL","input_set":[],"package_document_asset_id":"%s","package_document_version_id":"%s","manifest_document_asset_id":"%s","manifest_document_version_id":"%s","operator_user_id":"u-archiver","trace_id":"trace-cl-archive-missing"}
                                """.formatted(jsonString(packageDocument, "document_asset_id"), jsonString(packageDocument, "document_version_id"), jsonString(manifestDocument, "document_asset_id"), jsonString(manifestDocument, "document_version_id"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("ARCHIVE_INPUT_SET_INCOMPLETE"));

        String archive = mockMvc.perform(post("/api/contracts/{contract_id}/archives", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"archive_batch_no":"ARCH-2026-001","archive_type":"FINAL","archive_reason":"合同终止后归档","input_set":["CONTRACT_MASTER","MAIN_BODY","PERFORMANCE_SUMMARY","TERMINATION_SUMMARY"],"package_document_asset_id":"%s","package_document_version_id":"%s","manifest_document_asset_id":"%s","manifest_document_version_id":"%s","archive_keeper_user_id":"u-keeper","archive_location_code":"ROOM-A-01","operator_user_id":"u-archiver","trace_id":"trace-cl-archive-create"}
                                """.formatted(jsonString(packageDocument, "document_asset_id"), jsonString(packageDocument, "document_version_id"), jsonString(manifestDocument, "document_asset_id"), jsonString(manifestDocument, "document_version_id"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.archive_status").value("ARCHIVED"))
                .andExpect(jsonPath("$.archive_summary.archive_batch_no").value("ARCH-2026-001"))
                .andExpect(jsonPath("$.package_ref.document_role").value("ARCHIVE_PACKAGE"))
                .andReturn().getResponse().getContentAsString();
        String archiveId = jsonString(archive, "archive_record_id");

        String borrow = mockMvc.perform(post("/api/contracts/{contract_id}/archives/{archive_record_id}/borrows", sample.contractId(), archiveId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"borrow_purpose":"审计复核","requested_by":"u-auditor","requested_org_unit_id":"dept-audit","due_at":"2026-08-01T00:00:00Z","trace_id":"trace-cl-borrow"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.borrow_status").value("BORROWED"))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/contracts/{contract_id}/archive-borrows/{borrow_record_id}/return", sample.contractId(), jsonString(borrow, "borrow_record_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"returned_by":"u-auditor","trace_id":"trace-cl-return"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.return_status").value("RETURNED"));

        mockMvc.perform(get("/api/contracts/{contract_id}", sample.contractId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_master.contract_status").value("ARCHIVED"))
                .andExpect(jsonPath("$.contract_master.archive_summary.latest_archive_record_id").value(archiveId))
                .andExpect(jsonPath("$.lifecycle_summary.archive_summary.borrow_status").value("RETURNED"))
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'ARCHIVE_COMPLETED')]", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'ARCHIVE_BORROW_RETURNED')]", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void lifecycleEndToEndExposesQueryAuditAndNotificationStableEvents() throws Exception {
        LifecycleSample sample = createTerminatedContractSample("生命周期端到端合同", "trace-cl-e2e");
        String packageDocument = createDocument(sample.contractId(), "ARCHIVE_PACKAGE", "端到端归档封包.zip", "trace-cl-e2e-package");
        String manifestDocument = createDocument(sample.contractId(), "ARCHIVE_MANIFEST", "端到端归档清单.json", "trace-cl-e2e-manifest");

        mockMvc.perform(post("/api/contracts/{contract_id}/archives", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"archive_batch_no":"ARCH-E2E-001","archive_type":"FINAL","archive_reason":"端到端归档","input_set":["CONTRACT_MASTER","MAIN_BODY","PERFORMANCE_SUMMARY","TERMINATION_SUMMARY"],"package_document_asset_id":"%s","package_document_version_id":"%s","manifest_document_asset_id":"%s","manifest_document_version_id":"%s","archive_keeper_user_id":"u-keeper","archive_location_code":"ROOM-E2E","operator_user_id":"u-archiver","trace_id":"trace-cl-e2e-archive"}
                                """.formatted(jsonString(packageDocument, "document_asset_id"), jsonString(packageDocument, "document_version_id"), jsonString(manifestDocument, "document_asset_id"), jsonString(manifestDocument, "document_version_id"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/contracts/{contract_id}", sample.contractId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_master.contract_status").value("ARCHIVED"))
                .andExpect(jsonPath("$.lifecycle_summary.current_stage").value("ARCHIVE"))
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'PERFORMANCE_COMPLETED')]", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'TERMINATION_COMPLETED')]", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'ARCHIVE_COMPLETED' && @.visible_to_notify == true)]", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.audit_record[?(@.event_type == 'ARCHIVE_COMPLETED')]", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void rejectedChangeAndTerminationDoNotWriteCompletionMilestonesOrCompletionTimestamps() throws Exception {
        LifecycleSample changeSample = createPerformedContractSample("变更驳回合同", "trace-cl-change-reject");
        String agreement = createDocument(changeSample.contractId(), "CHANGE_AGREEMENT", "驳回补充协议.pdf", "trace-cl-change-reject-agreement");
        String change = mockMvc.perform(post("/api/contracts/{contract_id}/changes", changeSample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"change_type":"TERM","change_reason":"驳回演示","change_summary":"不应生效","effective_date":"2026-06-01","supplemental_document_asset_id":"%s","supplemental_document_version_id":"%s","workflow_instance_id":"wf-change-rejected","operator_user_id":"u-change-owner","trace_id":"trace-cl-change-reject-apply"}
                                """.formatted(jsonString(agreement, "document_asset_id"), jsonString(agreement, "document_version_id"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/contracts/{contract_id}/changes/{change_id}/approval-results", changeSample.contractId(), jsonString(change, "change_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approval_result":"REJECTED","workflow_instance_id":"wf-change-rejected","result_summary":"不同意变更","operator_user_id":"u-change-approver","trace_id":"trace-cl-change-rejected"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.change_status").value("REJECTED"));

        assertThat(jdbcTemplate.queryForObject("SELECT approved_at FROM cl_contract_change WHERE change_id = ?", String.class, jsonString(change, "change_id"))).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT applied_at FROM cl_contract_change WHERE change_id = ?", String.class, jsonString(change, "change_id"))).isNull();
        mockMvc.perform(get("/api/contracts/{contract_id}", changeSample.contractId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_master.contract_status").value("PERFORMED"))
                .andExpect(jsonPath("$.contract_master.change_summary").doesNotExist())
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'CHANGE_REJECTED')]", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'CHANGE_APPLIED')]").isEmpty());

        LifecycleSample terminationSample = createPerformedContractSample("终止驳回合同", "trace-cl-term-reject");
        String material = createDocument(terminationSample.contractId(), "TERMINATION_AGREEMENT", "驳回终止协议.pdf", "trace-cl-term-reject-material");
        String termination = mockMvc.perform(post("/api/contracts/{contract_id}/terminations", terminationSample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"termination_type":"MUTUAL_AGREEMENT","termination_reason":"驳回演示","termination_summary":"不应终止","requested_termination_date":"2026-07-01","material_document_asset_id":"%s","material_document_version_id":"%s","workflow_instance_id":"wf-term-rejected","operator_user_id":"u-term-owner","trace_id":"trace-cl-term-reject-apply"}
                                """.formatted(jsonString(material, "document_asset_id"), jsonString(material, "document_version_id"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/contracts/{contract_id}/terminations/{termination_id}/approval-results", terminationSample.contractId(), jsonString(termination, "termination_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approval_result":"REJECTED","workflow_instance_id":"wf-term-rejected","operator_user_id":"u-term-approver","trace_id":"trace-cl-term-rejected"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termination_status").value("REJECTED"));

        assertThat(jdbcTemplate.queryForObject("SELECT terminated_at FROM cl_contract_termination WHERE termination_id = ?", String.class, jsonString(termination, "termination_id"))).isNull();
        mockMvc.perform(get("/api/contracts/{contract_id}", terminationSample.contractId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_master.contract_status").value("PERFORMED"))
                .andExpect(jsonPath("$.contract_master.termination_summary").doesNotExist())
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'TERMINATION_REJECTED')]", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.timeline_summary[?(@.event_type == 'TERMINATION_COMPLETED')]").isEmpty());
    }

    @Test
    void lifecycleSummariesAndApprovalProcessRefsPersistFinalFacts() throws Exception {
        LifecycleSample sample = createPerformedContractSample("摘要持久化合同", "trace-cl-persist");
        String agreement = createDocument(sample.contractId(), "CHANGE_AGREEMENT", "持久化补充协议.pdf", "trace-cl-persist-change-doc");
        String change = mockMvc.perform(post("/api/contracts/{contract_id}/changes", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"change_type":"AMOUNT","change_reason":"金额调整","change_summary":"金额调整为 150000","effective_date":"2026-06-15","supplemental_document_asset_id":"%s","supplemental_document_version_id":"%s","workflow_instance_id":"wf-change-persist","operator_user_id":"u-change-owner","trace_id":"trace-cl-persist-change"}
                                """.formatted(jsonString(agreement, "document_asset_id"), jsonString(agreement, "document_version_id"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/contracts/{contract_id}/changes/{change_id}/approval-results", sample.contractId(), jsonString(change, "change_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approval_result":"APPROVED","workflow_instance_id":"wf-change-persist","approved_at":"2026-06-15T08:00:00Z","result_summary":"金额调整已生效","operator_user_id":"u-change-approver","trace_id":"trace-cl-persist-change-approved"}
                                """))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT process_status_snapshot FROM cl_lifecycle_process_ref WHERE source_resource_id = ?", String.class, jsonString(change, "change_id"))).isEqualTo("APPLIED");
        String persistedChangeSummary = jdbcTemplate.queryForObject("SELECT change_summary_json FROM cl_lifecycle_summary WHERE contract_id = ?", String.class, sample.contractId());
        assertThat(persistedChangeSummary).contains(jsonString(change, "change_id"), "金额调整已生效");
        assertThatCode(() -> objectMapper.readValue(persistedChangeSummary, new TypeReference<java.util.Map<String, Object>>() {})).doesNotThrowAnyException();

        String material = createDocument(sample.contractId(), "TERMINATION_AGREEMENT", "持久化终止协议.pdf", "trace-cl-persist-term-doc");
        String termination = mockMvc.perform(post("/api/contracts/{contract_id}/terminations", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"termination_type":"MUTUAL_AGREEMENT","termination_reason":"持久化终止","termination_summary":"终止后归档","requested_termination_date":"2026-07-01","material_document_asset_id":"%s","material_document_version_id":"%s","workflow_instance_id":"wf-term-persist","operator_user_id":"u-term-owner","trace_id":"trace-cl-persist-term"}
                                """.formatted(jsonString(material, "document_asset_id"), jsonString(material, "document_version_id"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/contracts/{contract_id}/terminations/{termination_id}/approval-results", sample.contractId(), jsonString(termination, "termination_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approval_result":"APPROVED","workflow_instance_id":"wf-term-persist","terminated_at":"2026-07-01T18:00:00Z","post_action_status":"SETTLED","settlement_summary":"结算完成","access_restriction":"READ_ONLY_AFTER_TERMINATION","operator_user_id":"u-term-approver","trace_id":"trace-cl-persist-term-approved"}
                                """))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT process_status_snapshot FROM cl_lifecycle_process_ref WHERE source_resource_id = ?", String.class, jsonString(termination, "termination_id"))).isEqualTo("TERMINATED");
        String persistedTerminationSummary = jdbcTemplate.queryForObject("SELECT termination_summary_json FROM cl_lifecycle_summary WHERE contract_id = ?", String.class, sample.contractId());
        assertThat(persistedTerminationSummary).contains(jsonString(termination, "termination_id"), "READ_ONLY_AFTER_TERMINATION");
        assertThatCode(() -> objectMapper.readValue(persistedTerminationSummary, new TypeReference<java.util.Map<String, Object>>() {})).doesNotThrowAnyException();

        String packageDocument = createDocument(sample.contractId(), "ARCHIVE_PACKAGE", "持久化归档封包.zip", "trace-cl-persist-package");
        String manifestDocument = createDocument(sample.contractId(), "ARCHIVE_MANIFEST", "持久化归档清单.json", "trace-cl-persist-manifest");
        String archive = mockMvc.perform(post("/api/contracts/{contract_id}/archives", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"archive_batch_no":"ARCH-PERSIST-001","archive_type":"FINAL","input_set":["CONTRACT_MASTER","MAIN_BODY","PERFORMANCE_SUMMARY","TERMINATION_SUMMARY"],"package_document_asset_id":"%s","package_document_version_id":"%s","manifest_document_asset_id":"%s","manifest_document_version_id":"%s","operator_user_id":"u-archiver","trace_id":"trace-cl-persist-archive"}
                                """.formatted(jsonString(packageDocument, "document_asset_id"), jsonString(packageDocument, "document_version_id"), jsonString(manifestDocument, "document_asset_id"), jsonString(manifestDocument, "document_version_id"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String persistedArchiveSummary = jdbcTemplate.queryForObject("SELECT archive_summary_json FROM cl_lifecycle_summary WHERE contract_id = ?", String.class, sample.contractId());
        assertThat(persistedArchiveSummary).contains(jsonString(archive, "archive_record_id"), "ARCH-PERSIST-001");
        assertThatCode(() -> objectMapper.readValue(persistedArchiveSummary, new TypeReference<java.util.Map<String, Object>>() {})).doesNotThrowAnyException();
    }

    @Test
    void archiveRejectsPackageAndManifestDocumentsWithWrongRoles() throws Exception {
        LifecycleSample sample = createTerminatedContractSample("归档角色校验合同", "trace-cl-archive-role");
        String wrongPackage = createDocument(sample.contractId(), "MAIN_BODY", "不能冒充封包.docx", "trace-cl-archive-wrong-package");
        String wrongManifest = createDocument(sample.contractId(), "CHANGE_AGREEMENT", "不能冒充清单.pdf", "trace-cl-archive-wrong-manifest");

        mockMvc.perform(post("/api/contracts/{contract_id}/archives", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"archive_batch_no":"ARCH-WRONG-ROLE","archive_type":"FINAL","input_set":["CONTRACT_MASTER","MAIN_BODY","PERFORMANCE_SUMMARY","TERMINATION_SUMMARY"],"package_document_asset_id":"%s","package_document_version_id":"%s","manifest_document_asset_id":"%s","manifest_document_version_id":"%s","operator_user_id":"u-archiver","trace_id":"trace-cl-archive-wrong-role"}
                                """.formatted(jsonString(wrongPackage, "document_asset_id"), jsonString(wrongPackage, "document_version_id"), jsonString(wrongManifest, "document_asset_id"), jsonString(wrongManifest, "document_version_id"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("ARCHIVE_DOCUMENT_ROLE_MISMATCH"));
    }

    @Test
    void archiveBorrowReturnRejectsRepeatedReturn() throws Exception {
        LifecycleSample sample = createTerminatedContractSample("重复归还合同", "trace-cl-borrow-state");
        String packageDocument = createDocument(sample.contractId(), "ARCHIVE_PACKAGE", "重复归还封包.zip", "trace-cl-borrow-package");
        String manifestDocument = createDocument(sample.contractId(), "ARCHIVE_MANIFEST", "重复归还清单.json", "trace-cl-borrow-manifest");
        String archive = mockMvc.perform(post("/api/contracts/{contract_id}/archives", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"archive_batch_no":"ARCH-BORROW-STATE","archive_type":"FINAL","input_set":["CONTRACT_MASTER","MAIN_BODY","PERFORMANCE_SUMMARY","TERMINATION_SUMMARY"],"package_document_asset_id":"%s","package_document_version_id":"%s","manifest_document_asset_id":"%s","manifest_document_version_id":"%s","operator_user_id":"u-archiver","trace_id":"trace-cl-borrow-archive"}
                                """.formatted(jsonString(packageDocument, "document_asset_id"), jsonString(packageDocument, "document_version_id"), jsonString(manifestDocument, "document_asset_id"), jsonString(manifestDocument, "document_version_id"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String borrow = mockMvc.perform(post("/api/contracts/{contract_id}/archives/{archive_record_id}/borrows", sample.contractId(), jsonString(archive, "archive_record_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"borrow_purpose":"状态机复核","requested_by":"u-auditor","requested_org_unit_id":"dept-audit","due_at":"2026-08-01T00:00:00Z","trace_id":"trace-cl-borrow-state"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/contracts/{contract_id}/archive-borrows/{borrow_record_id}/return", sample.contractId(), jsonString(borrow, "borrow_record_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"returned_by":"u-auditor","trace_id":"trace-cl-borrow-return"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/contracts/{contract_id}/archive-borrows/{borrow_record_id}/return", sample.contractId(), jsonString(borrow, "borrow_record_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"returned_by":"u-auditor","trace_id":"trace-cl-borrow-return-again"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("ARCHIVE_BORROW_STATUS_CONFLICT"));
    }

    private LifecycleSample createTerminatedContractSample(String contractName, String tracePrefix) throws Exception {
        LifecycleSample sample = createPerformedContractSample(contractName, tracePrefix);
        String material = createDocument(sample.contractId(), "TERMINATION_AGREEMENT", "终止协议.pdf", tracePrefix + "-term-material");
        String termination = mockMvc.perform(post("/api/contracts/{contract_id}/terminations", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"termination_type":"MUTUAL_AGREEMENT","termination_reason":"端到端终止","termination_summary":"终止后归档","requested_termination_date":"2026-07-01","settlement_summary":"结算完成","material_document_asset_id":"%s","material_document_version_id":"%s","workflow_instance_id":"%s-term-wf","operator_user_id":"u-term-owner","trace_id":"%s-term-apply"}
                                """.formatted(jsonString(material, "document_asset_id"), jsonString(material, "document_version_id"), tracePrefix, tracePrefix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/contracts/{contract_id}/terminations/{termination_id}/approval-results", sample.contractId(), jsonString(termination, "termination_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approval_result":"APPROVED","terminated_at":"2026-07-01T18:00:00Z","post_action_status":"SETTLED","settlement_summary":"结算完成","access_restriction":"READ_ONLY_AFTER_TERMINATION","operator_user_id":"u-term-approver","trace_id":"%s-term-approved"}
                                """.formatted(tracePrefix)))
                .andExpect(status().isOk());
        return sample;
    }

    private LifecycleSample createPerformedContractSample(String contractName, String tracePrefix) throws Exception {
        LifecycleSample sample = createSignedContractSample(contractName, tracePrefix);
        String record = createPerformanceRecord(sample.contractId(), tracePrefix + "-perf-record");
        String node = mockMvc.perform(post("/api/contracts/{contract_id}/performance-nodes", sample.contractId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performance_record_id":"%s","node_type":"DELIVERY","node_name":"设备交付","milestone_code":"DELIVERY_ACCEPTED","trace_id":"%s-perf-node"}
                                """.formatted(record, tracePrefix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(patch("/api/contracts/{contract_id}/performance-nodes/{node_id}", sample.contractId(), jsonString(node, "performance_node_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"node_status":"COMPLETED","progress_percent":100,"risk_level":"LOW","issue_count":0,"actual_at":"2026-05-05","trace_id":"%s-perf-complete"}
                                """.formatted(tracePrefix)))
                .andExpect(status().isOk());
        return sample;
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
                .andExpect(status().isOk());
        return new LifecycleSample(approved.contractId(), approved.documentAssetId(), approved.documentVersionId());
    }

    private ApprovedSample createApprovedContractSample(String contractName, String tracePrefix) throws Exception {
        String contractId = createDraftContract(contractName, tracePrefix + "-contract");
        String document = createDocument(contractId, "MAIN_BODY", "生命周期主正文.docx", tracePrefix + "-document");
        String approval = mockMvc.perform(post("/api/workflow-engine/processes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"business_object_type":"CONTRACT","contract_id":"%s","document_asset_id":"%s","document_version_id":"%s","starter_user_id":"u-cl-owner","trace_id":"%s-approval-start"}
                                """.formatted(contractId, jsonString(document, "document_asset_id"), jsonString(document, "document_version_id"), tracePrefix)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/workflow-engine/processes/{process_id}/results", jsonString(approval, "process_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result":"APPROVED","operator_id":"u-cl-approver","trace_id":"%s-approval-result"}
                                """.formatted(tracePrefix)))
                .andExpect(status().isOk());
        return new ApprovedSample(contractId, jsonString(document, "document_asset_id"), jsonString(document, "document_version_id"));
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

    private String createDocument(String contractId, String documentRole, String title, String tracePrefix) throws Exception {
        return mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"%s","document_title":"%s","source_channel":"MANUAL_UPLOAD","file_upload_token":"%s-file","trace_id":"%s"}
                                """.formatted(contractId, documentRole, title, tracePrefix, tracePrefix)))
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

    private record ApprovedSample(String contractId, String documentAssetId, String documentVersionId) {
    }

    private record LifecycleSample(String contractId, String documentAssetId, String documentVersionId) {
    }
}

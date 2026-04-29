package com.cmp.platform.batch4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class Batch4SearchIndexRebuildTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanSearchTables() {
        jdbcTemplate.update("DELETE FROM ia_search_audit_event");
        jdbcTemplate.update("DELETE FROM ia_search_export_record");
        jdbcTemplate.update("DELETE FROM ia_search_rebuild_job");
        jdbcTemplate.update("DELETE FROM ia_search_result_set");
        jdbcTemplate.update("DELETE FROM ia_search_document");
        jdbcTemplate.update("DELETE FROM ia_search_source_envelope");
        jdbcTemplate.update("DELETE FROM ia_ocr_language_segment");
        jdbcTemplate.update("DELETE FROM ia_ocr_field_candidate");
        jdbcTemplate.update("DELETE FROM ia_ocr_seal_region");
        jdbcTemplate.update("DELETE FROM ia_ocr_table_region");
        jdbcTemplate.update("DELETE FROM ia_ocr_layout_block");
        jdbcTemplate.update("DELETE FROM ia_ocr_text_layer");
        jdbcTemplate.update("DELETE FROM ia_ocr_result_aggregate");
        jdbcTemplate.update("DELETE FROM ia_ocr_retry_fact");
        jdbcTemplate.update("DELETE FROM ia_ocr_audit_event");
        jdbcTemplate.update("DELETE FROM ia_ocr_job");
        jdbcTemplate.update("DELETE FROM platform_job");
    }

    @Test
    void admitsContractDocumentOcrAndClauseSourcesThenBuildsSearchDocuments() throws Exception {
        ContractDocument sample = createContractDocument("检索闭环合同", "dept-search-a", "trace-search-admit");
        String ocr = createOcrJob(sample.documentVersionId(), "idem-search-ocr", "trace-search-ocr");
        String ocrResultId = jsonString(ocr, "ocr_result_aggregate_id");

        String refresh = mockMvc.perform(post("/api/intelligent-applications/search/sources/refresh")
                        .header("X-CMP-Permissions", "SEARCH_INDEX_MANAGE,CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_types":["CONTRACT","DOCUMENT","OCR","CLAUSE"],"contract_id":"%s","document_version_id":"%s","ocr_result_aggregate_id":"%s","trace_id":"trace-search-refresh"}
                                """.formatted(sample.contractId(), sample.documentVersionId(), ocrResultId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted_count").value(4))
                .andExpect(jsonPath("$.source_envelopes[?(@.doc_type == 'CONTRACT')].source_object_id").value(sample.contractId()))
                .andExpect(jsonPath("$.source_envelopes[?(@.doc_type == 'DOCUMENT')].source_object_id").value(sample.documentVersionId()))
                .andExpect(jsonPath("$.source_envelopes[?(@.doc_type == 'OCR')].source_object_id").value(ocrResultId))
                .andExpect(jsonPath("$.search_documents[0].search_doc_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String contractDocId = jsonString(refresh, "search_doc_id");
        assertRowCount("ia_search_source_envelope", "contract_id = '" + sample.contractId() + "'", 4);
        assertRowCount("ia_search_document", "search_doc_id = '" + contractDocId + "' and exposure_status = 'ACTIVE'", 1);
        assertRowCount("platform_job", "job_type = 'IA_SEARCH_REINDEX' and resource_type = 'SEARCH_SOURCE' and business_object_id = '" + sample.contractId() + "'", 1);
    }

    @Test
    void queriesWithPermissionCroppingStableSnapshotAggregationPaginationAndExportRecheck() throws Exception {
        ContractDocument visible = createContractDocument("精准合同 Alpha", "dept-search-a", "trace-search-visible");
        ContractDocument denied = createContractDocument("精准合同 Beta", "dept-search-b", "trace-search-denied");
        refreshAllSources(visible, "idem-search-visible", "trace-search-visible-refresh");
        refreshAllSources(denied, "idem-search-denied", "trace-search-denied-refresh");

        String query = mockMvc.perform(post("/api/intelligent-applications/search/query")
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query_text":"精准合同","match_mode":"FUZZY","scope_list":["CONTRACT","DOCUMENT","OCR","CLAUSE"],"filter_payload":{"owner_org_unit_id":"dept-search-a"},"sort_by":"updated_at","sort_order":"desc","page":1,"page_size":2,"aggregate_fields":["doc_type","owner_org_unit_id"],"actor_id":"u-search","trace_id":"trace-search-query"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items[0].contract_id").value(visible.contractId()))
                .andExpect(jsonPath("$.items[0].body_snippet").doesNotExist())
                .andExpect(jsonPath("$.facets.doc_type.CONTRACT").value(1))
                .andExpect(jsonPath("$.result_set_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String resultSetId = jsonString(query, "result_set_id");

        mockMvc.perform(get("/api/intelligent-applications/search/snapshots/{resultSetId}", resultSetId)
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot_status").value("READY"))
                .andExpect(jsonPath("$.stable_order_digest").isNotEmpty());
        mockMvc.perform(post("/api/intelligent-applications/search/snapshots/{resultSetId}/export", resultSetId)
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"export_profile_code\":\"DEFAULT\",\"trace_id\":\"trace-search-export-denied\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("SEARCH_EXPORT_PERMISSION_DENIED"));
        mockMvc.perform(post("/api/intelligent-applications/search/snapshots/{resultSetId}/export", resultSetId)
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW,SEARCH_EXPORT")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"export_profile_code\":\"DEFAULT\",\"trace_id\":\"trace-search-export\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.export_status").value("READY"))
                .andExpect(jsonPath("$.items[0].sensitive_body_text").doesNotExist());

        assertRowCount("ia_search_result_set", "result_set_id = '" + resultSetId + "' and result_status = 'READY'", 1);
        assertRowCount("ia_search_export_record", "result_set_id = '" + resultSetId + "' and export_status = 'READY'", 1);
    }

    @Test
    void rejectsUnauthorizedChangedPermissionAndExpiredSnapshotReplay() throws Exception {
        ContractDocument sample = createContractDocument("快照重放安全合同", "dept-search-a", "trace-search-replay-secure");
        refreshAllSources(sample, "idem-search-replay-secure", "trace-search-replay-secure-refresh");
        String query = searchByText("快照重放安全合同", "dept-search-a");
        String resultSetId = jsonString(query, "result_set_id");

        mockMvc.perform(post("/api/intelligent-applications/search/snapshots/{resultSetId}/replay", resultSetId)
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-search-replay-no-permission\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("SEARCH_QUERY_PERMISSION_DENIED"));

        mockMvc.perform(post("/api/intelligent-applications/search/snapshots/{resultSetId}/replay", resultSetId)
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-search-replay-org-changed\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error_code").value("SEARCH_SNAPSHOT_EXPIRED"));

        String secondQuery = searchByText("快照重放安全合同", "dept-search-a");
        String expiredResultSetId = jsonString(secondQuery, "result_set_id");
        mockMvc.perform(post("/api/intelligent-applications/search/permissions/changed")
                        .header("X-CMP-Permissions", "SEARCH_INDEX_MANAGE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor_id\":\"u-search\",\"trace_id\":\"trace-search-replay-expire\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/intelligent-applications/search/snapshots/{resultSetId}/replay", expiredResultSetId)
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-search-replay-expired\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error_code").value("SEARCH_SNAPSHOT_EXPIRED"));
    }

    @Test
    void supportsExactMatchFiltersSortingAndStablePagination() throws Exception {
        ContractDocument exact = createContractDocument("精准唯一", "dept-search-a", "trace-search-exact");
        ContractDocument fuzzy = createContractDocument("精准唯一扩展", "dept-search-a", "trace-search-fuzzy");
        refreshAllSources(exact, "idem-search-exact", "trace-search-exact-refresh");
        refreshAllSources(fuzzy, "idem-search-fuzzy", "trace-search-fuzzy-refresh");

        mockMvc.perform(post("/api/intelligent-applications/search/query")
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query_text":"精准唯一","match_mode":"EXACT","scope_list":["CONTRACT"],"page":1,"page_size":10,"actor_id":"u-search","trace_id":"trace-search-exact-query"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].contract_id").value(exact.contractId()));

        String asc = mockMvc.perform(post("/api/intelligent-applications/search/query")
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query_text":"精准唯一","match_mode":"FUZZY","scope_list":["CONTRACT"],"sort_by":"title_text","sort_order":"asc","page":1,"page_size":1,"actor_id":"u-search","trace_id":"trace-search-sort-asc"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].contract_id").value(exact.contractId()))
                .andReturn().getResponse().getContentAsString();
        String pageTwo = mockMvc.perform(post("/api/intelligent-applications/search/query")
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query_text":"精准唯一","match_mode":"FUZZY","scope_list":["CONTRACT"],"sort_by":"title_text","sort_order":"asc","page":2,"page_size":1,"actor_id":"u-search","trace_id":"trace-search-sort-page-2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].contract_id").value(fuzzy.contractId()))
                .andReturn().getResponse().getContentAsString();
        assertThat(jsonString(asc, "stable_order_digest")).isNotEqualTo(jsonString(pageTwo, "stable_order_digest"));

        mockMvc.perform(post("/api/intelligent-applications/search/query")
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query_text":"精准唯一","match_mode":"FUZZY","scope_list":["CONTRACT"],"sort_by":"title_text","sort_order":"desc","page":1,"page_size":1,"actor_id":"u-search","trace_id":"trace-search-sort-desc"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].contract_id").value(fuzzy.contractId()));

        mockMvc.perform(post("/api/intelligent-applications/search/query")
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query_text":"精准唯一","match_mode":"FUZZY","scope_list":["CONTRACT"],"filter_payload":{"contract_id":"%s"},"page":1,"page_size":10,"actor_id":"u-search","trace_id":"trace-search-filter-contract"}
                                """.formatted(fuzzy.contractId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].contract_id").value(fuzzy.contractId()));
    }

    @Test
    void isolatesRangeRebuildBackfillsMissingRowsAndHonorsAliasSwitchFlag() throws Exception {
        ContractDocument scoped = createContractDocument("范围重建合同", "dept-search-a", "trace-search-range");
        ContractDocument other = createContractDocument("范围外合同", "dept-search-a", "trace-search-range-other");
        refreshAllSources(scoped, "idem-search-range", "trace-search-range-refresh");
        refreshAllSources(other, "idem-search-range-other", "trace-search-range-other-refresh");
        jdbcTemplate.update("DELETE FROM ia_search_document WHERE contract_id = ? AND doc_type = 'CONTRACT'", scoped.contractId());

        mockMvc.perform(post("/api/intelligent-applications/search/rebuilds")
                        .header("X-CMP-Permissions", "SEARCH_INDEX_MANAGE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rebuild_type":"RANGE","scope":{"contract_id":"%s"},"double_generation":true,"switch_alias":false,"trace_id":"trace-search-range-rebuild"}
                                """.formatted(scoped.contractId())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rebuild_status").value("BUILT"))
                .andExpect(jsonPath("$.alias_switch.alias_status").value("STAGED"))
                .andExpect(jsonPath("$.backfill_result.backfilled_count").value(4));
        assertRowCount("ia_search_document", "contract_id = '" + scoped.contractId() + "' and rebuild_generation = 2", 4);
        assertRowCount("ia_search_document", "contract_id = '" + other.contractId() + "' and rebuild_generation = 1", 4);

        mockMvc.perform(post("/api/intelligent-applications/search/query")
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query_text":"范围重建合同","scope_list":["CONTRACT"],"page":1,"page_size":10,"actor_id":"u-search","trace_id":"trace-search-range-query-before-switch"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].contract_id").value(scoped.contractId()))
                .andExpect(jsonPath("$.items[0].rebuild_generation").value(1));

        mockMvc.perform(post("/api/intelligent-applications/search/rebuilds")
                        .header("X-CMP-Permissions", "SEARCH_INDEX_MANAGE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rebuild_type":"RANGE","scope":{"contract_id":"%s"},"double_generation":true,"switch_alias":true,"trace_id":"trace-search-range-switch"}
                                """.formatted(scoped.contractId())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rebuild_status").value("SWITCHED"))
                .andExpect(jsonPath("$.alias_switch.alias_status").value("ACTIVE"));
        mockMvc.perform(post("/api/intelligent-applications/search/query")
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query_text":"范围重建合同","scope_list":["CONTRACT"],"page":1,"page_size":10,"actor_id":"u-search","trace_id":"trace-search-range-query-after-switch"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].contract_id").value(scoped.contractId()))
                .andExpect(jsonPath("$.items[0].rebuild_generation").value(2));
        mockMvc.perform(post("/api/intelligent-applications/search/query")
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query_text":"范围外合同","scope_list":["CONTRACT"],"page":1,"page_size":10,"actor_id":"u-search","trace_id":"trace-search-range-other-query-after-switch"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].contract_id").value(other.contractId()))
                .andExpect(jsonPath("$.items[0].rebuild_generation").value(1));
    }

    @Test
    void rejectsExpiredSnapshotReadAndReplayByExpiresAt() throws Exception {
        ContractDocument sample = createContractDocument("快照自然过期合同", "dept-search-a", "trace-search-expired-at");
        refreshAllSources(sample, "idem-search-expired-at", "trace-search-expired-at-refresh");
        String query = searchByText("快照自然过期合同", "dept-search-a");
        String resultSetId = jsonString(query, "result_set_id");
        jdbcTemplate.update("update ia_search_result_set set expires_at = DATEADD('SECOND', -1, CURRENT_TIMESTAMP) where result_set_id = ?", resultSetId);

        mockMvc.perform(get("/api/intelligent-applications/search/snapshots/{resultSetId}", resultSetId)
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-a"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error_code").value("SEARCH_SNAPSHOT_EXPIRED"));
        mockMvc.perform(post("/api/intelligent-applications/search/snapshots/{resultSetId}/replay", resultSetId)
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-search-expired-at-replay\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error_code").value("SEARCH_SNAPSHOT_EXPIRED"));
    }

    @Test
    void exportRechecksPermissionAndCropsBodyEvenWhenSnapshotContainsBody() throws Exception {
        ContractDocument sample = createContractDocument("导出字段裁剪合同", "dept-search-a", "trace-search-export-crop");
        refreshAllSources(sample, "idem-search-export-crop", "trace-search-export-crop-refresh");
        String query = mockMvc.perform(post("/api/intelligent-applications/search/query")
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW,SEARCH_VIEW_BODY")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query_text":"导出字段裁剪合同","scope_list":["CONTRACT"],"page":1,"page_size":10,"actor_id":"u-search","trace_id":"trace-search-export-crop-query"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].body_snippet").exists())
                .andReturn().getResponse().getContentAsString();
        String resultSetId = jsonString(query, "result_set_id");

        mockMvc.perform(post("/api/intelligent-applications/search/snapshots/{resultSetId}/export", resultSetId)
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW,SEARCH_VIEW_BODY")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"export_profile_code\":\"DEFAULT\",\"trace_id\":\"trace-search-export-crop-denied\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("SEARCH_EXPORT_PERMISSION_DENIED"));
        mockMvc.perform(post("/api/intelligent-applications/search/snapshots/{resultSetId}/export", resultSetId)
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW,SEARCH_VIEW_BODY,SEARCH_EXPORT")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"export_profile_code\":\"DEFAULT\",\"trace_id\":\"trace-search-export-crop\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].body_snippet").doesNotExist())
                .andExpect(jsonPath("$.items[0].sensitive_body_text").doesNotExist());
    }

    @Test
    void expiresSnapshotsOnPermissionChangeAndHidesOldDocumentVersionAfterSwitch() throws Exception {
        ContractDocument sample = createContractDocument("版本切换检索合同", "dept-search-a", "trace-search-switch");
        refreshAllSources(sample, "idem-search-switch-v1", "trace-search-switch-v1");
        String query = searchByText("版本切换检索合同", "dept-search-a");
        String resultSetId = jsonString(query, "result_set_id");

        mockMvc.perform(post("/api/intelligent-applications/search/permissions/changed")
                        .header("X-CMP-Permissions", "SEARCH_INDEX_MANAGE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor_id\":\"u-search\",\"trace_id\":\"trace-search-perm-change\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expired_snapshot_count").value(1));
        mockMvc.perform(get("/api/intelligent-applications/search/snapshots/{resultSetId}", resultSetId)
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-a"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error_code").value("SEARCH_SNAPSHOT_EXPIRED"));

        String secondVersion = mockMvc.perform(post("/api/document-center/assets/{documentAssetId}/versions", sample.documentAssetId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"base_version_id":"%s","version_label":"搜索第二版","file_upload_token":"search-token-v2","trace_id":"trace-search-v2-append"}
                                """.formatted(sample.documentVersionId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondVersionId = jsonString(secondVersion, "document_version_id");
        mockMvc.perform(post("/api/document-center/versions/{documentVersionId}/activate", secondVersionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_user_id\":\"u-search\",\"trace_id\":\"trace-search-v2-activate\"}"))
                .andExpect(status().isOk());
        refreshDocument(sample.contractId(), secondVersionId, "trace-search-v2-refresh");

        mockMvc.perform(post("/api/intelligent-applications/search/query")
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW,SEARCH_VIEW_BODY")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query_text":"版本切换检索合同","scope_list":["DOCUMENT"],"page":1,"page_size":10,"actor_id":"u-search","trace_id":"trace-search-v2-query"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].document_version_id").value(secondVersionId));
        assertRowCount("ia_search_document", "doc_type = 'DOCUMENT' and document_version_id = '" + sample.documentVersionId() + "' and exposure_status = 'STALE'", 1);
    }

    @Test
    void rebuildsBackfillsReplaysSwitchesGenerationAndFallsBackWithoutPollutingIndex() throws Exception {
        ContractDocument sample = createContractDocument("重建降级合同", "dept-search-a", "trace-search-rebuild");
        refreshAllSources(sample, "idem-search-rebuild", "trace-search-rebuild-refresh");
        String query = searchByText("重建降级合同", "dept-search-a");
        String resultSetId = jsonString(query, "result_set_id");

        mockMvc.perform(post("/api/intelligent-applications/search/rebuilds")
                        .header("X-CMP-Permissions", "SEARCH_INDEX_MANAGE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rebuild_type":"FULL","scope":{"contract_id":"%s"},"double_generation":true,"switch_alias":true,"trace_id":"trace-search-full-rebuild"}
                                """.formatted(sample.contractId())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rebuild_status").value("SWITCHED"))
                .andExpect(jsonPath("$.alias_switch.alias_status").value("ACTIVE"))
                .andExpect(jsonPath("$.backfill_result.backfilled_count").value(4));
        mockMvc.perform(post("/api/intelligent-applications/search/snapshots/{resultSetId}/replay", resultSetId)
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-search-replay\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replay_source").value("SNAPSHOT"))
                .andExpect(jsonPath("$.items[0].contract_id").value(sample.contractId()));
        mockMvc.perform(post("/api/intelligent-applications/search/query")
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", "dept-search-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query_text":"重建降级合同","scope_list":["CONTRACT"],"simulate_engine_unavailable":true,"page":1,"page_size":10,"actor_id":"u-search","trace_id":"trace-search-fallback"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded_query").value(true))
                .andExpect(jsonPath("$.degrade_reason").value("SEARCH_ENGINE_UNAVAILABLE"))
                .andExpect(jsonPath("$.items[0].doc_type").value("CONTRACT"));

        assertRowCount("ia_search_rebuild_job", "rebuild_type = 'FULL' and rebuild_status = 'SWITCHED'", 1);
        assertRowCount("ia_search_document", "doc_type = 'CONTRACT' and title_text = '重建降级合同'", 2);
    }

    private ContractDocument createContractDocument(String contractName, String ownerOrgUnitId, String traceId) throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"%s","owner_user_id":"u-search-owner","owner_org_unit_id":"%s","category_code":"SEARCH","category_name":"检索合同","trace_id":"%s"}
                                """.formatted(contractName, ownerOrgUnitId, traceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");
        String document = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"%s.pdf","file_upload_token":"%s-token","trace_id":"%s"}
                                """.formatted(contractId, contractName, traceId, traceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new ContractDocument(contractId, jsonString(document, "document_asset_id"), jsonString(document, "document_version_id"));
    }

    private String createOcrJob(String documentVersionId, String idempotencyKey, String traceId) throws Exception {
        return mockMvc.perform(post("/api/intelligent-applications/ocr/jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,OCR_CREATE")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_version_id":"%s","job_purpose":"SEARCH_INDEX_INPUT","actor_id":"u-search","trace_id":"%s"}
                                """.formatted(documentVersionId, traceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private void refreshAllSources(ContractDocument sample, String ocrIdempotencyKey, String traceId) throws Exception {
        String ocr = createOcrJob(sample.documentVersionId(), ocrIdempotencyKey, traceId + "-ocr");
        String ocrResultId = jsonString(ocr, "ocr_result_aggregate_id");
        mockMvc.perform(post("/api/intelligent-applications/search/sources/refresh")
                        .header("X-CMP-Permissions", "SEARCH_INDEX_MANAGE,CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_types":["CONTRACT","DOCUMENT","OCR","CLAUSE"],"contract_id":"%s","document_version_id":"%s","ocr_result_aggregate_id":"%s","trace_id":"%s"}
                                """.formatted(sample.contractId(), sample.documentVersionId(), ocrResultId, traceId)))
                .andExpect(status().isAccepted());
    }

    private void refreshDocument(String contractId, String documentVersionId, String traceId) throws Exception {
        mockMvc.perform(post("/api/intelligent-applications/search/sources/refresh")
                        .header("X-CMP-Permissions", "SEARCH_INDEX_MANAGE,CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_types":["DOCUMENT"],"contract_id":"%s","document_version_id":"%s","trace_id":"%s"}
                                """.formatted(contractId, documentVersionId, traceId)))
                .andExpect(status().isAccepted());
    }

    private String searchByText(String text, String orgScope) throws Exception {
        return mockMvc.perform(post("/api/intelligent-applications/search/query")
                        .header("X-CMP-Permissions", "SEARCH_QUERY,CONTRACT_VIEW")
                        .header("X-CMP-Org-Scope", orgScope)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query_text":"%s","scope_list":["CONTRACT","DOCUMENT","OCR","CLAUSE"],"page":1,"page_size":10,"actor_id":"u-search","trace_id":"trace-search-helper"}
                                """.formatted(text)))
                .andExpect(status().isOk())
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

    private void assertRowCount(String tableName, String whereClause, int expectedCount) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + whereClause, Integer.class);
        assertThat(count).isEqualTo(expectedCount);
    }

    private record ContractDocument(String contractId, String documentAssetId, String documentVersionId) {
    }
}

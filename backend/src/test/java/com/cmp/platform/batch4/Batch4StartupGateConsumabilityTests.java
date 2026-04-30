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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class Batch4StartupGateConsumabilityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanBatch4Tables() {
        jdbcTemplate.update("DELETE FROM platform_job");
        jdbcTemplate.update("DELETE FROM ao_human_confirmation");
    }

    @Test
    void exposesContractDocumentAgentAndTaskConsumabilityEntrypointsForBatch4() throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"第四批门禁样例合同","owner_user_id":"u-batch4-owner","owner_org_unit_id":"dept-batch4-sales","amount":"120000.00","currency":"CNY","classification_chain":[{"category_code":"SALES","category_name":"销售合同"},{"category_code":"EQUIPMENT","category_name":"装备采购"}],"clause_library_code":"clause-lib-standard","template_library_code":"tpl-lib-equipment","trace_id":"trace-batch4-contract"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");

        mockMvc.perform(get("/api/contracts/{contractId}/batch4-consumption-summary", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_id").value(contractId))
                .andExpect(jsonPath("$.classification_master_link.category_path").value("SALES/EQUIPMENT"))
                .andExpect(jsonPath("$.classification_master_link.source_of_truth").value("contract-core"))
                .andExpect(jsonPath("$.semantic_reference_summary.clause_library_ref.library_code").value("clause-lib-standard"))
                .andExpect(jsonPath("$.semantic_reference_summary.template_library_ref.library_code").value("tpl-lib-equipment"));

        mockMvc.perform(get("/api/contracts/{contractId}/semantic-references", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clause_library.items[0].stable_clause_version_id").isNotEmpty())
                .andExpect(jsonPath("$.template_library.items[0].stable_template_version_id").isNotEmpty());

        String document = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"第四批门禁正文.pdf","source_channel":"MANUAL_UPLOAD","file_upload_token":"batch4-main-token","version_label":"正文首版","trace_id":"trace-batch4-document"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String documentAssetId = jsonString(document, "document_asset_id");
        String firstVersionId = jsonString(document, "document_version_id");

        mockMvc.perform(get("/api/document-center/assets/{documentAssetId}/capability-bindings", documentAssetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document_asset_id").value(documentAssetId))
                .andExpect(jsonPath("$.capability_bindings[?(@.capability_code == 'OCR')]").isNotEmpty())
                .andExpect(jsonPath("$.capability_bindings[?(@.capability_code == 'SEARCH')]").isNotEmpty())
                .andExpect(jsonPath("$.capability_bindings[?(@.capability_code == 'AI_APPLICATION')]").isNotEmpty());

        String secondVersion = mockMvc.perform(post("/api/document-center/assets/{documentAssetId}/versions", documentAssetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"base_version_id":"%s","version_label":"正文第二版","file_upload_token":"batch4-main-token-v2","trace_id":"trace-batch4-document-v2"}
                                """.formatted(firstVersionId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondVersionId = jsonString(secondVersion, "document_version_id");

        mockMvc.perform(post("/api/document-center/versions/{documentVersionId}/activate", secondVersionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_user_id\":\"u-batch4-owner\",\"trace_id\":\"trace-batch4-activate\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/document-center/events")
                        .param("consumer_scope", "BATCH4_INTELLIGENT_APPLICATIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription_entry.consumer_scope").value("BATCH4_INTELLIGENT_APPLICATIONS"))
                .andExpect(jsonPath("$.items[?(@.event_type == 'DOCUMENT_VERSION_ACTIVATED' && @.document_version_id == '%s')].delivery_status".formatted(secondVersionId)).value("READY"));

        String confirmation = mockMvc.perform(post("/api/agent-os/human-confirmations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_task_id":"agt-task-batch4-manual","source_result_id":"agt-result-batch4-risk","confirmation_type":"HIGH_RISK_AI_OUTPUT","business_module":"INTELLIGENT_APPLICATIONS","object_type":"CONTRACT","object_id":"%s","requested_by":"agent-os","trace_id":"trace-batch4-confirmation"}
                                """.formatted(contractId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.confirmation_status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String confirmationId = jsonString(confirmation, "confirmation_id");

        mockMvc.perform(post("/api/agent-os/human-confirmations/{confirmationId}/decisions", confirmationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"operator_user_id\":\"u-reviewer\",\"decision_comment\":\"允许回写摘要\",\"trace_id\":\"trace-batch4-confirmation-decision\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmation_status").value("APPROVED"));

        mockMvc.perform(get("/api/agent-os/human-confirmations/{confirmationId}", confirmationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision_result.decision").value("APPROVED"));

        String job = mockMvc.perform(post("/api/platform/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"job_type":"IA_SEARCH_REINDEX","source_module":"document-center","consumer_module":"intelligent-applications","resource_type":"DOCUMENT_VERSION","resource_id":"%s","business_object_type":"CONTRACT","business_object_id":"%s","trace_id":"trace-batch4-job"}
                                """.formatted(secondVersionId, contractId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job_status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String platformJobId = jsonString(job, "platform_job_id");

        mockMvc.perform(get("/api/platform/jobs/{platformJobId}", platformJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job_type").value("IA_SEARCH_REINDEX"));
        mockMvc.perform(get("/api/platform/jobs")
                        .param("consumer_module", "intelligent-applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.platform_job_id == '%s')]".formatted(platformJobId)).isNotEmpty());

        assertTableExists("platform_job");
        assertTableExists("ao_human_confirmation");
        assertRowCount("platform_job", "platform_job_id = '" + platformJobId + "' and job_status = 'PENDING'", 1);
        assertRowCount("ao_human_confirmation", "confirmation_id = '" + confirmationId + "' and confirmation_status = 'APPROVED'", 1);
    }

    private String jsonString(String json, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = json.indexOf(marker);
        assertThat(start).as("响应缺少字段: %s, body=%s", fieldName, json).isGreaterThanOrEqualTo(0);
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        return json.substring(valueStart, valueEnd);
    }

    private void assertTableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where lower(table_name) = lower(?)
                """, Integer.class, tableName);
        assertThat(count).isEqualTo(1);
    }

    private void assertRowCount(String tableName, String whereClause, int expectedCount) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + whereClause, Integer.class);
        assertThat(count).isEqualTo(expectedCount);
    }
}

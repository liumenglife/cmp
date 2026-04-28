package com.cmp.platform.agentos;

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
class AgentOsToolSandboxGovernanceTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanAgentOsTables() {
        jdbcTemplate.update("DELETE FROM ao_verification_report");
        jdbcTemplate.update("DELETE FROM ao_provider_usage");
        jdbcTemplate.update("DELETE FROM ao_tool_result");
        jdbcTemplate.update("DELETE FROM ao_tool_grant");
        jdbcTemplate.update("DELETE FROM ao_tool_definition_snapshot");
        jdbcTemplate.update("DELETE FROM ao_tool_definition");
        jdbcTemplate.update("DELETE FROM ao_agent_audit_event");
        jdbcTemplate.update("DELETE FROM ao_tool_invocation");
        jdbcTemplate.update("DELETE FROM ao_prompt_snapshot");
        jdbcTemplate.update("DELETE FROM ao_environment_event");
        jdbcTemplate.update("DELETE FROM ao_agent_result");
        jdbcTemplate.update("DELETE FROM ao_agent_run");
        jdbcTemplate.update("DELETE FROM ao_agent_task");

        jdbcTemplate.update("DELETE FROM ia_identity_audit");
        jdbcTemplate.update("DELETE FROM ia_authorization_hit_result");
        jdbcTemplate.update("DELETE FROM ia_authorization_decision");
        jdbcTemplate.update("DELETE FROM ia_data_scope");
        jdbcTemplate.update("DELETE FROM ia_permission_grant");
        jdbcTemplate.update("DELETE FROM ia_org_membership");
        jdbcTemplate.update("DELETE FROM ia_org_unit");
        jdbcTemplate.update("DELETE FROM ia_user");

        seedAuthorizedAgentUser();
    }

    @Test
    void registersDiscoversAndSnapshotsToolDefinitionsWithGrant() throws Exception {
        String runId = createRun("trace-tool-discovery", "MANUAL_START");

        mockMvc.perform(get("/api/agent-os/internal/tools").param("capability_code", "CONTRACT_READ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool_list[0].tool_name").value("platform.contract.readonly.lookup"))
                .andExpect(jsonPath("$.tool_list[0].risk_level").value("L1_READONLY"));

        mockMvc.perform(get("/api/agent-os/internal/tools/{toolName}/schema", "platform.contract.readonly.lookup")
                        .param("run_id", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool_name").value("platform.contract.readonly.lookup"))
                .andExpect(jsonPath("$.snapshot_ref").isNotEmpty())
                .andExpect(jsonPath("$.grant_status").value("GRANTED"))
                .andExpect(jsonPath("$.schema_snapshot.input_schema.object_id.type").value("string"));

        assertTableExists("ao_tool_definition");
        assertTableExists("ao_tool_definition_snapshot");
        assertTableExists("ao_tool_grant");
        assertRowCount("ao_tool_definition", "tool_name = 'platform.contract.readonly.lookup' and risk_level = 'L1_READONLY'", 1);
        assertRowCount("ao_tool_definition_snapshot", "run_id = '" + runId + "' and tool_name = 'platform.contract.readonly.lookup'", 1);
        assertRowCount("ao_tool_grant", "run_id = '" + runId + "' and grant_status = 'GRANTED'", 1);
    }

    @Test
    void queryEngineInvokesModelAndPlatformReadonlyToolsThroughContractWithPairedAudit() throws Exception {
        String response = createTask("trace-tool-query-engine", "TOOL_SUCCESS");
        String runId = jsonString(response, "run_id");

        mockMvc.perform(get("/api/agent-os/runs/{runId}/tool-pair-check", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pair_status").value("PAIRED"))
                .andExpect(jsonPath("$.invocation_count").value(2))
                .andExpect(jsonPath("$.result_count").value(2));

        assertRowCount("ao_tool_invocation", "run_id = '" + runId + "' and tool_type = 'MODEL' and invocation_status = 'SUCCEEDED'", 1);
        assertRowCount("ao_tool_invocation", "run_id = '" + runId + "' and tool_type = 'INTERNAL_SERVICE' and invocation_status = 'SUCCEEDED'", 1);
        assertRowCount("ao_tool_result", "run_id = '" + runId + "' and result_status = 'SUCCEEDED'", 2);
        assertRowCount("ao_agent_audit_event", "parent_object_id = '" + runId + "' and action_type = 'TOOL_INVOKED'", 2);
        assertRowCount("ao_agent_audit_event", "parent_object_id = '" + runId + "' and action_type = 'TOOL_RESULT_RECORDED'", 2);
    }

    @Test
    void toolFailureTimeoutSandboxRejectionOffloadAndProviderFallbackArePersisted() throws Exception {
        String runId = createRun("trace-tool-errors", "MANUAL_START");

        invokeTool(runId, "platform.contract.readonly.lookup", "{\"object_id\":\"ctr-agent-001\",\"simulate\":\"FAILURE\"}")
                .andExpect(jsonPath("$.result_status").value("FAILED"));
        invokeTool(runId, "platform.contract.readonly.lookup", "{\"object_id\":\"ctr-agent-001\",\"simulate\":\"TIMEOUT\"}")
                .andExpect(jsonPath("$.result_status").value("TIMED_OUT"));
        invokeTool(runId, "platform.contract.guarded.writeback", "{\"object_id\":\"ctr-agent-001\",\"bypass_sandbox\":true}")
                .andExpect(jsonPath("$.result_status").value("REJECTED"))
                .andExpect(jsonPath("$.failure_code").value("SANDBOX_REJECTED"));
        invokeTool(runId, "platform.contract.readonly.lookup", "{\"object_id\":\"ctr-agent-001\",\"simulate\":\"LARGE_OUTPUT\"}")
                .andExpect(jsonPath("$.result_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.offloaded").value(true))
                .andExpect(jsonPath("$.artifact_ref").isNotEmpty());
        invokeTool(runId, "model.generate_text", "{\"capability\":\"generate_text\",\"simulate\":\"PRIMARY_CIRCUIT_OPEN\"}")
                .andExpect(jsonPath("$.result_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.provider_code").value("MOCK_FALLBACK_PROVIDER"));

        assertRowCount("ao_tool_result", "run_id = '" + runId + "' and result_status = 'FAILED'", 1);
        assertRowCount("ao_tool_result", "run_id = '" + runId + "' and result_status = 'TIMED_OUT'", 1);
        assertRowCount("ao_tool_result", "run_id = '" + runId + "' and failure_code = 'SANDBOX_REJECTED'", 1);
        assertRowCount("ao_tool_result", "run_id = '" + runId + "' and output_artifact_ref is not null and output_summary like '%已卸载%'", 1);
        assertRowCount("ao_provider_usage", "run_id = '" + runId + "' and route_status = 'DEGRADED' and provider_code = 'MOCK_FALLBACK_PROVIDER'", 1);
    }

    @Test
    void detectsBrokenToolInvocationResultPairs() throws Exception {
        String runId = createRun("trace-tool-broken-pair", "MANUAL_START");
        jdbcTemplate.update("""
                insert into ao_tool_invocation
                (tool_invocation_id, run_id, prompt_snapshot_id, tool_type, tool_name, provider_code, invocation_status,
                 input_digest, output_digest, output_artifact_ref, output_truncated_reason, latency_ms, token_in, token_out,
                 error_code, error_message_summary, retry_no, created_at, updated_at)
                values ('tool-broken-pair', ?, 'prompt-manual', 'MODEL', 'model.generate_text', 'MOCK_PROVIDER', 'SUCCEEDED',
                        'input-broken', 'output-broken', null, null, 1, 1, 1, null, null, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, runId);

        mockMvc.perform(get("/api/agent-os/runs/{runId}/tool-pair-check", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pair_status").value("BROKEN"))
                .andExpect(jsonPath("$.failure_code").value("TOOL_PAIR_BROKEN"));

        assertRowCount("ao_agent_audit_event", "object_id = '" + runId + "' and action_type = 'TOOL_PAIR_BROKEN' and result_status = 'FAILED'", 1);
    }

    @Test
    void detectsMissingAndDuplicateToolResultsByInvocationId() throws Exception {
        String runId = createRun("trace-tool-duplicate-pair", "MANUAL_START");
        insertManualInvocation(runId, "tool-missing-result");
        insertManualInvocation(runId, "tool-duplicate-result");
        insertManualResult(runId, "tool-result-duplicate-a", "tool-duplicate-result");
        insertManualResult(runId, "tool-result-duplicate-b", "tool-duplicate-result");

        mockMvc.perform(get("/api/agent-os/runs/{runId}/tool-pair-check", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pair_status").value("BROKEN"))
                .andExpect(jsonPath("$.failure_code").value("TOOL_PAIR_BROKEN"))
                .andExpect(jsonPath("$.broken_invocation_count").value(2));

        assertRowCount("ao_agent_audit_event", "object_id = '" + runId + "' and action_type = 'TOOL_PAIR_BROKEN' and result_status = 'FAILED'", 1);
    }

    @Test
    void verificationReportFailsWhenToolResultPairingOrAuditPairingIsBroken() throws Exception {
        String runId = createRun("trace-tool-verification-broken", "MANUAL_START");
        insertManualInvocation(runId, "tool-audit-missing");
        insertManualResult(runId, "tool-result-audit-missing", "tool-audit-missing");

        mockMvc.perform(post("/api/agent-os/runs/{runId}/verification-reports", runId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"agent-os tool sandbox governance\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.check_items[?(@.check_code == 'TOOL_RESULT_PAIRING')].status").value("PASSED"))
                .andExpect(jsonPath("$.check_items[?(@.check_code == 'TOOL_AUDIT_PAIRING')].status").value("FAILED"))
                .andExpect(jsonPath("$.failure_evidence[0].evidence_type").value("TOOL_AUDIT_PAIR_BROKEN"))
                .andExpect(jsonPath("$.conclusion").value("FAILED"));

        assertRowCount("ao_verification_report", "run_id = '" + runId + "' and conclusion = 'FAILED'", 1);
    }

    @Test
    void verificationReportContainsEvidenceChecklistFailureBaselineAndRegressionEntry() throws Exception {
        String runId = createRun("trace-tool-verification", "TOOL_SUCCESS");

        mockMvc.perform(post("/api/agent-os/runs/{runId}/verification-reports", runId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"agent-os tool sandbox governance\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verification_target").value("agent-os tool sandbox governance"))
                .andExpect(jsonPath("$.check_items[?(@.check_code == 'TOOL_RESULT_PAIRING')].status").value("PASSED"))
                .andExpect(jsonPath("$.check_items[?(@.check_code == 'TOOL_AUDIT_PAIRING')].status").value("PASSED"))
                .andExpect(jsonPath("$.failure_evidence[0].evidence_type").value("NONE"))
                .andExpect(jsonPath("$.performance_baseline.p95_latency_ms").value(500))
                .andExpect(jsonPath("$.regression_baseline_entry").isNotEmpty())
                .andExpect(jsonPath("$.conclusion").value("PASSED"));

        assertTableExists("ao_verification_report");
        assertRowCount("ao_verification_report", "run_id = '" + runId + "' and conclusion = 'PASSED'", 1);
    }

    private org.springframework.test.web.servlet.ResultActions invokeTool(String runId, String toolName, String payload) throws Exception {
        String authorizedPayload = payload;
        if (toolName.startsWith("platform.contract.") && !payload.contains("requester_id")) {
            authorizedPayload = payload.substring(0, payload.length() - 1)
                    + ",\"requester_id\":\"u-agent-tool-001\",\"active_org_id\":\"org-root\",\"active_org_unit_id\":\"dept-agent-tool\"}";
        }
        return mockMvc.perform(post("/api/agent-os/runs/{runId}/tools/{toolName}/invoke", runId, toolName)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authorizedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool_invocation_id").isNotEmpty())
                .andExpect(jsonPath("$.tool_result_id").isNotEmpty());
    }

    private void seedAuthorizedAgentUser() {
        jdbcTemplate.update("""
                insert into ia_user (user_id, login_name, display_name, user_status, default_org_id, default_org_unit_id, created_at)
                values ('u-agent-tool-001', 'u-agent-tool-001', 'u-agent-tool-001', 'ACTIVE', 'org-root', 'dept-agent-tool', CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                insert into ia_org_unit
                (org_unit_id, org_id, parent_org_unit_id, org_unit_code, org_unit_name, org_unit_type, org_status, org_path, path_depth, sort_order, created_at, updated_at)
                values ('org-root', 'org-root', null, 'org-root', 'org-root', 'ORG', 'ACTIVE', '/org-root/', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                insert into ia_org_unit
                (org_unit_id, org_id, parent_org_unit_id, org_unit_code, org_unit_name, org_unit_type, org_status, org_path, path_depth, sort_order, created_at, updated_at)
                values ('dept-agent-tool', 'org-root', 'org-root', 'dept-agent-tool', 'dept-agent-tool', 'DEPARTMENT', 'ACTIVE', '/org-root/dept-agent-tool/', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                insert into ia_org_membership
                (membership_id, user_id, org_id, org_unit_id, membership_type, membership_status, is_primary_department, created_at, updated_at)
                values ('m-u-agent-tool-001', 'u-agent-tool-001', 'org-root', 'dept-agent-tool', 'PRIMARY', 'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                insert into ia_permission_grant
                (permission_grant_id, grant_target_type, grant_target_id, permission_type, permission_code, resource_type, grant_status, priority_no, effect_mode, created_at, updated_at)
                values ('pg-agent-tool-001', 'USER', 'u-agent-tool-001', 'FUNCTION', 'AGENT_TOOL:INVOKE', 'CONTRACT', 'ACTIVE', 10, 'ALLOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                insert into ia_data_scope
                (data_scope_id, subject_type, subject_id, resource_type, scope_type, scope_ref, scope_status, priority_no, effect_mode, created_at, updated_at)
                values ('ds-agent-tool-001', 'USER', 'u-agent-tool-001', 'CONTRACT', 'USER_LIST', 'ctr-agent-001', 'ACTIVE', 10, 'ALLOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
    }

    private String createRun(String traceId, String scenario) throws Exception {
        return jsonString(createTask(traceId, scenario), "run_id");
    }

    private String createTask(String traceId, String scenario) throws Exception {
        return mockMvc.perform(post("/api/agent-os/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractRiskRequest(traceId, scenario)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
    }

    private String contractRiskRequest(String traceId, String scenario) {
        return """
                {
                  "task_type": "RISK_ANALYSIS",
                  "task_source": "BUSINESS_MODULE",
                  "requester_type": "USER",
                  "requester_id": "u-agent-tool-001",
                  "specialized_agent_code": "CONTRACT_REVIEW_AGENT",
                  "input_context": {
                    "business_module": "CONTRACT",
                    "object_type": "CONTRACT",
                    "object_id": "ctr-agent-001"
                  },
                  "input_payload": {
                    "question": "请通过工具契约读取合同并生成风险摘要",
                    "scenario": "%s"
                  },
                  "max_loop_count": 1,
                  "trace_id": "%s"
                }
                """.formatted(scenario, traceId);
    }

    private void insertManualInvocation(String runId, String invocationId) {
        jdbcTemplate.update("""
                insert into ao_tool_invocation
                (tool_invocation_id, run_id, prompt_snapshot_id, tool_type, tool_name, provider_code, invocation_status,
                 input_digest, output_digest, output_artifact_ref, output_truncated_reason, latency_ms, token_in, token_out,
                 error_code, error_message_summary, retry_no, created_at, updated_at)
                values (?, ?, 'prompt-manual', 'MODEL', 'model.generate_text', 'MOCK_PROVIDER', 'SUCCEEDED',
                        'input-manual', 'output-manual', null, null, 1, 1, 1, null, null, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, invocationId, runId);
    }

    private void insertManualResult(String runId, String resultId, String invocationId) {
        jdbcTemplate.update("""
                insert into ao_tool_result
                (tool_result_id, tool_invocation_id, run_id, tool_name, result_status, result_class, output_summary,
                 output_artifact_ref, failure_code, failure_class, retryable, resource_usage_json, created_at)
                values (?, ?, ?, 'model.generate_text', 'SUCCEEDED', 'STRUCTURED_EXTRACTION', 'manual observation',
                        null, null, null, true, '{"latency_ms":1}', CURRENT_TIMESTAMP)
                """, resultId, invocationId, runId);
    }

    private String jsonString(String json, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new AssertionError("响应缺少字段: " + fieldName + ", body=" + json);
        }
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

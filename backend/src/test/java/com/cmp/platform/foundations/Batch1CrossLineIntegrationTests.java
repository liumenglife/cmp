package com.cmp.platform.foundations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class Batch1CrossLineIntegrationTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanFoundationTables() {
        jdbcTemplate.update("DELETE FROM ih_reconciliation_diff");
        jdbcTemplate.update("DELETE FROM ih_reconciliation_record");
        jdbcTemplate.update("DELETE FROM ih_reconciliation_task");
        jdbcTemplate.update("DELETE FROM ih_recovery_ticket");
        jdbcTemplate.update("DELETE FROM ih_outbound_attempt");
        jdbcTemplate.update("DELETE FROM ih_integration_audit_event");
        jdbcTemplate.update("DELETE FROM ih_integration_job");
        jdbcTemplate.update("DELETE FROM ih_callback_receipt");
        jdbcTemplate.update("DELETE FROM ih_outbound_dispatch");
        jdbcTemplate.update("DELETE FROM ih_integration_binding");
        jdbcTemplate.update("DELETE FROM ih_inbound_message");
        jdbcTemplate.update("DELETE FROM ih_security_nonce");
        jdbcTemplate.update("DELETE FROM ih_endpoint_profile");

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
        jdbcTemplate.update("DELETE FROM ia_org_rule_resolution_record");
        jdbcTemplate.update("DELETE FROM ia_org_rule_version");
        jdbcTemplate.update("DELETE FROM ia_org_rule");
        jdbcTemplate.update("DELETE FROM ia_authorization_hit_result");
        jdbcTemplate.update("DELETE FROM ia_authorization_decision");
        jdbcTemplate.update("DELETE FROM ia_data_scope");
        jdbcTemplate.update("DELETE FROM ia_permission_grant");
        jdbcTemplate.update("DELETE FROM ia_role_assignment");
        jdbcTemplate.update("DELETE FROM ia_role");
        jdbcTemplate.update("DELETE FROM ia_org_membership");
        jdbcTemplate.update("DELETE FROM ia_org_unit");
        jdbcTemplate.update("DELETE FROM ia_idempotency_record");
        jdbcTemplate.update("DELETE FROM ia_identity_manual_disposition");
        jdbcTemplate.update("DELETE FROM ia_identity_binding_precheck");
        jdbcTemplate.update("DELETE FROM ia_identity_session");
        jdbcTemplate.update("DELETE FROM ia_protocol_exchange");
        jdbcTemplate.update("DELETE FROM ia_identity_binding");
        jdbcTemplate.update("DELETE FROM ia_user");
    }

    @Test
    void wecomTicketEntersThroughIntegrationHubAndPlatformTokenIsIssuedOnlyByIdentityAccess() throws Exception {
        createUser("u-wecom-cross");

        String handoff = mockMvc.perform(signedPost("/api/integration-hub/wecom/protocol-exchanges", "INBOUND", "WECOM", "WECOM_TICKET_EXCHANGE", "nonce-cross-wecom", "2026-04-28T08:00:00Z", """
                        {"code":"trusted:wecom-cross","state":"state-cross","trace_id":"trace-cross-wecom"}
                        """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.handoff_target").value("identity-access"))
                .andExpect(jsonPath("$.access_token").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/auth/sessions/exchanges")
                        .header("Idempotency-Key", "idem-cross-wecom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"WECOM","ticket":"trusted:wecom-cross","candidate_user_ids":["u-wecom-cross"],"trace_id":"trace-cross-wecom","integration_handoff_ref":"%s"}
                                """.formatted(jsonString(handoff, "protocol_exchange_ref"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.protocol_exchange_id").isNotEmpty());

        assertRowCount("ih_integration_audit_event", "trace_id = 'trace-cross-wecom' and action_type = 'WECOM_TICKET_HANDOFF' and result_status = 'SUCCESS'", 1);
        assertRowCount("ia_protocol_exchange", "provider = 'WECOM' and external_identity = 'wecom-cross' and exchange_status = 'SESSION_ALLOWED'", 1);
        assertRowCount("ia_identity_audit", "trace_id = 'trace-cross-wecom' and event_type = 'PROTOCOL_EXCHANGE_SUCCEEDED' and result_status = 'SUCCESS'", 1);
    }

    @Test
    void agentToolExecutionMustPassIdentityAccessAuthorizationBeforeToolAudit() throws Exception {
        seedAuthorizedAgentUser("u-agent-cross", "dept-agent-cross", "ctr-cross-001");

        String task = mockMvc.perform(post("/api/agent-os/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentTaskRequest("u-agent-cross", "dept-agent-cross", "ctr-cross-001", "trace-cross-agent-allow", true)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.task_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.authorization.decision_result").value("ALLOW"))
                .andExpect(jsonPath("$.authorization.decision_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String runId = jsonString(task, "run_id");
        assertRowCount("ia_authorization_decision", "request_trace_id = 'trace-cross-agent-allow' and subject_user_id = 'u-agent-cross' and action_code = 'AGENT_TOOL:INVOKE' and decision_result = 'ALLOW'", 1);
        assertRowCount("ia_identity_audit", "trace_id = 'trace-cross-agent-allow' and event_type = 'AUTHORIZATION_DECISION' and result_status = 'SUCCESS'", 1);
        assertRowCount("ao_tool_invocation", "run_id = '" + runId + "' and tool_name = 'platform.contract.readonly.lookup' and invocation_status = 'SUCCEEDED'", 1);
        assertRowCount("ao_agent_audit_event", "parent_object_id = '" + runId + "' and action_type = 'TOOL_INVOKED' and trace_id = 'trace-cross-agent-allow'", 2);
    }

    @Test
    void unauthorizedAgentToolExecutionIsRejectedAndLeavesIdentityAuditWithoutCreatingAgentRun() throws Exception {
        createUser("u-agent-denied");
        createOrgUnit("org-root", null, "ORG");
        createOrgUnit("dept-agent-denied", "org-root", "DEPARTMENT");
        addMembership("m-u-agent-denied", "u-agent-denied", "dept-agent-denied");

        mockMvc.perform(post("/api/agent-os/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentTaskRequest("u-agent-denied", "dept-agent-denied", "ctr-cross-denied", "trace-cross-agent-denied", true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("40301"))
                .andExpect(jsonPath("$.error").value("IDENTITY_ACCESS_AUTHZ_DENIED"))
                .andExpect(jsonPath("$.authorization.decision_result").value("DENY"));

        assertRowCount("ia_authorization_decision", "request_trace_id = 'trace-cross-agent-denied' and action_code = 'AGENT_TOOL:INVOKE' and decision_result = 'DENY'", 1);
        assertRowCount("ia_identity_audit", "trace_id = 'trace-cross-agent-denied' and event_type = 'AUTHZ_DENIED' and result_status = 'DENIED'", 1);
        assertRowCount("ao_agent_run", "trace_id = 'trace-cross-agent-denied'", 0);
    }

    @Test
    void unauthorizedAgentToolTaskIsRejectedEvenWhenRequestDisablesAuthorization() throws Exception {
        createUser("u-agent-denied-flag");
        createOrgUnit("org-root", null, "ORG");
        createOrgUnit("dept-agent-denied-flag", "org-root", "DEPARTMENT");
        addMembership("m-u-agent-denied-flag", "u-agent-denied-flag", "dept-agent-denied-flag");

        mockMvc.perform(post("/api/agent-os/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentTaskRequest("u-agent-denied-flag", "dept-agent-denied-flag", "ctr-cross-denied-flag", "trace-cross-agent-denied-flag", false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("40301"))
                .andExpect(jsonPath("$.error").value("IDENTITY_ACCESS_AUTHZ_DENIED"))
                .andExpect(jsonPath("$.authorization.decision_result").value("DENY"));

        assertRowCount("ia_authorization_decision", "request_trace_id = 'trace-cross-agent-denied-flag' and action_code = 'AGENT_TOOL:INVOKE' and decision_result = 'DENY'", 1);
        assertRowCount("ao_agent_run", "trace_id = 'trace-cross-agent-denied-flag'", 0);
    }

    @Test
    void directAgentToolInvocationIsRejectedWithoutIdentityAccessAuthorization() throws Exception {
        createUser("u-agent-direct-denied");
        createOrgUnit("org-root", null, "ORG");
        createOrgUnit("dept-agent-direct-denied", "org-root", "DEPARTMENT");
        addMembership("m-u-agent-direct-denied", "u-agent-direct-denied", "dept-agent-direct-denied");

        String task = mockMvc.perform(post("/api/agent-os/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentTaskRequest("u-agent-direct-denied", "dept-agent-direct-denied", "ctr-direct-denied", "trace-cross-agent-direct-run", false, "NORMAL")))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String runId = jsonString(task, "run_id");
        mockMvc.perform(post("/api/agent-os/runs/%s/tools/platform.contract.readonly.lookup/invoke".formatted(runId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentToolInvokeRequest("u-agent-direct-denied", "dept-agent-direct-denied", "ctr-direct-denied", "trace-cross-agent-direct-denied", false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("40301"))
                .andExpect(jsonPath("$.error").value("IDENTITY_ACCESS_AUTHZ_DENIED"))
                .andExpect(jsonPath("$.authorization.decision_result").value("DENY"));

        assertRowCount("ia_authorization_decision", "request_trace_id = 'trace-cross-agent-direct-denied' and action_code = 'AGENT_TOOL:INVOKE' and decision_result = 'DENY'", 1);
        assertRowCount("ao_tool_invocation", "run_id = '" + runId + "' and tool_name = 'platform.contract.readonly.lookup'", 0);
    }

    @Test
    void crossLineAuditTraceReturnsIdentityAgentAndIntegrationEvents() throws Exception {
        seedAuthorizedAgentUser("u-audit-cross", "dept-audit-cross", "ctr-audit-cross");
        mockMvc.perform(signedPost("/api/integration-hub/inbound-messages", "INBOUND", "CRM", "DEFAULT_INBOUND", "nonce-cross-audit-in", "2026-04-28T08:00:00Z", """
                        {"source_system":"CRM","message_type":"MASTER_DATA","external_request_id":"crm-cross-audit","trace_id":"trace-cross-audit","payload":{"customerName":"星邦客户"}}
                        """))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/agent-os/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentTaskRequest("u-audit-cross", "dept-audit-cross", "ctr-audit-cross", "trace-cross-audit", true)))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/foundations/audit-trace").param("trace_id", "trace-cross-audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace_id").value("trace-cross-audit"))
                .andExpect(jsonPath("$.identity_access.item_list[?(@.event_type == 'AUTHORIZATION_DECISION')]").isNotEmpty())
                .andExpect(jsonPath("$.agent_os.item_list[?(@.action_type == 'TOOL_INVOKED')]").isNotEmpty())
                .andExpect(jsonPath("$.integration_hub.item_list[?(@.summary == 'INBOUND_ACCEPTED')]").isNotEmpty());
    }

    private void seedAuthorizedAgentUser(String userId, String orgUnitId, String contractId) throws Exception {
        createUser(userId);
        createOrgUnit("org-root", null, "ORG");
        createOrgUnit(orgUnitId, "org-root", "DEPARTMENT");
        addMembership("m-" + userId, userId, orgUnitId);
        createPermissionGrant("pg-agent-" + userId, userId, "AGENT_TOOL:INVOKE", "CONTRACT", "ALLOW");
        createDataScope("ds-agent-" + userId, userId, "CONTRACT", contractId);
    }

    private void createUser(String userId) throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"user_id":"%s","login_name":"%s","display_name":"%s"}
                                """.formatted(userId, userId, userId)))
                .andExpect(status().isCreated());
    }

    private void createOrgUnit(String orgUnitId, String parentId, String type) throws Exception {
        mockMvc.perform(post("/api/org-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"org_unit_id":"%s","parent_org_unit_id":%s,"org_unit_code":"%s","org_unit_name":"%s","org_unit_type":"%s"}
                                """.formatted(orgUnitId, jsonOrNull(parentId), orgUnitId, orgUnitId, type)))
                .andExpect(status().isCreated());
    }

    private void addMembership(String membershipId, String userId, String orgUnitId) throws Exception {
        mockMvc.perform(post("/api/org-memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"membership_id":"%s","user_id":"%s","org_unit_id":"%s","membership_type":"PRIMARY","is_primary_department":true}
                                """.formatted(membershipId, userId, orgUnitId)))
                .andExpect(status().isCreated());
    }

    private void createPermissionGrant(String grantId, String userId, String permissionCode, String resourceType, String effect) throws Exception {
        mockMvc.perform(post("/api/permission-grants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permission_grant_id":"%s","grant_target_type":"USER","grant_target_id":"%s","permission_type":"FUNCTION","permission_code":"%s","resource_type":"%s","effect_mode":"%s","priority_no":10}
                                """.formatted(grantId, userId, permissionCode, resourceType, effect)))
                .andExpect(status().isCreated());
    }

    private void createDataScope(String dataScopeId, String userId, String resourceType, String resourceId) throws Exception {
        mockMvc.perform(post("/api/data-scopes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data_scope_id":"%s","subject_type":"USER","subject_id":"%s","resource_type":"%s","scope_type":"USER_LIST","scope_ref":"%s","effect_mode":"ALLOW","priority_no":10}
                                """.formatted(dataScopeId, userId, resourceType, resourceId)))
                .andExpect(status().isCreated());
    }

    private String agentTaskRequest(String userId, String orgUnitId, String contractId, String traceId, boolean authorizationRequired) {
        return agentTaskRequest(userId, orgUnitId, contractId, traceId, authorizationRequired, "TOOL_SUCCESS");
    }

    private String agentTaskRequest(String userId, String orgUnitId, String contractId, String traceId, boolean authorizationRequired, String scenario) {
        return """
                {
                  "task_type": "RISK_ANALYSIS",
                  "task_source": "BUSINESS_MODULE",
                  "requester_type": "USER",
                  "requester_id": "%s",
                  "authorization_required": %s,
                  "authorization_ref": {
                    "subject_ref": {"user_id":"%s","org_id":"org-root","org_unit_id":"%s"},
                    "action_code":"AGENT_TOOL:INVOKE",
                    "resource_type":"CONTRACT",
                    "resource_ref":{"resource_id":"%s"}
                  },
                  "specialized_agent_code": "CONTRACT_REVIEW_AGENT",
                  "input_context": {"business_module":"CONTRACT","object_type":"CONTRACT","object_id":"%s"},
                  "input_payload": {"question":"请通过工具契约读取合同并生成风险摘要","scenario":"%s"},
                  "max_loop_count": 1,
                  "trace_id": "%s"
                }
                """.formatted(userId, authorizationRequired, userId, orgUnitId, contractId, contractId, scenario, traceId);
    }

    private String agentToolInvokeRequest(String userId, String orgUnitId, String contractId, String traceId, boolean authorizationRequired) {
        return """
                {
                  "requester_id": "%s",
                  "authorization_required": %s,
                  "authorization_ref": {
                    "subject_ref": {"user_id":"%s","org_id":"org-root","org_unit_id":"%s"},
                    "action_code":"AGENT_TOOL:INVOKE",
                    "resource_type":"CONTRACT",
                    "resource_ref":{"resource_id":"%s"}
                  },
                  "object_id": "%s",
                  "trace_id": "%s"
                }
                """.formatted(userId, authorizationRequired, userId, orgUnitId, contractId, contractId, traceId);
    }

    private MockHttpServletRequestBuilder signedPost(String path, String direction, String system, String endpoint, String nonce, String timestamp, String body) throws Exception {
        String currentTimestamp = Instant.now().toString();
        return post(path)
                .headers(signatureHeaders(direction, system, endpoint, nonce, currentTimestamp, body))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private org.springframework.http.HttpHeaders signatureHeaders(String direction, String system, String endpoint, String nonce, String timestamp, String body) throws Exception {
        String normalizedSystem = system.toUpperCase().replace('-', '_');
        String digest = requestDigest(OBJECT_MAPPER.readValue(body, Map.class));
        String signature = "cmp-sha256:" + sha256(String.join("\n", direction, normalizedSystem, endpoint, timestamp, nonce, "security-v1", "cert-v1", digest));
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-CMP-Signature", signature);
        headers.add("X-CMP-Timestamp", timestamp);
        headers.add("X-CMP-Nonce", nonce);
        headers.add("X-CMP-Security-Profile-Version", "security-v1");
        headers.add("X-CMP-Certificate-Version", "cert-v1");
        return headers;
    }

    private String requestDigest(Map<String, Object> request) throws Exception {
        TreeMap<String, Object> canonical = new TreeMap<>(request);
        canonical.remove("trace_id");
        return sha256(OBJECT_MAPPER.writeValueAsString(canonical));
    }

    private String sha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
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

    private String jsonOrNull(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private void assertRowCount(String tableName, String whereClause, int expectedCount) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + whereClause, Integer.class);
        assertThat(count).isEqualTo(expectedCount);
    }
}

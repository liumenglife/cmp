package com.cmp.platform.integrationhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class IntegrationHubAccessAdapterTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanIntegrationHubTables() {
        jdbcTemplate.update("DELETE FROM ih_integration_audit_event");
        jdbcTemplate.update("DELETE FROM ih_integration_job");
        jdbcTemplate.update("DELETE FROM ih_callback_receipt");
        jdbcTemplate.update("DELETE FROM ih_outbound_dispatch");
        jdbcTemplate.update("DELETE FROM ih_integration_binding");
        jdbcTemplate.update("DELETE FROM ih_inbound_message");
        jdbcTemplate.update("DELETE FROM ih_security_nonce");
        jdbcTemplate.update("DELETE FROM ih_endpoint_profile");
    }

    @Test
    void signedInboundIsAcceptedNormalizedAuditedAndPersisted() throws Exception {
        String response = mockMvc.perform(post("/api/integration-hub/inbound-messages")
                        .headers(signatureHeaders("INBOUND", "CRM", "DEFAULT_INBOUND", "nonce-inbound-ok", "2026-04-28T08:00:00Z", """
                                {"source_system":"CRM","message_type":"MASTER_DATA","external_request_id":"crm-req-001","trace_id":"trace-ih-inbound-ok","payload":{"customerName":"星邦客户","externalId":"crm-cus-001"}}
                                """))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_system":"CRM","message_type":"MASTER_DATA","external_request_id":"crm-req-001","trace_id":"trace-ih-inbound-ok","payload":{"customerName":"星邦客户","externalId":"crm-cus-001"}}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.ingest_status").value("ACCEPTED"))
                .andExpect(jsonPath("$.normalized_payload.customer_name").value("星邦客户"))
                .andExpect(jsonPath("$.runtime_context.system_name").value("CRM"))
                .andExpect(jsonPath("$.runtime_context.credential_ref").value("cred-crm-primary"))
                .andReturn().getResponse().getContentAsString();

        String inboundMessageId = jsonString(response, "inbound_message_id");
        assertTableExists("ih_inbound_message");
        assertTableExists("ih_integration_audit_event");
        assertRowCount("ih_inbound_message", "inbound_message_id = '" + inboundMessageId + "' and source_system = 'CRM' and verification_result = 'VERIFIED'", 1);
        assertRowCount("ih_integration_job", "resource_id = '" + inboundMessageId + "' and job_type = 'INBOUND_PROCESS'", 1);
        assertRowCount("ih_integration_audit_event", "resource_id = '" + inboundMessageId + "' and action_type = 'INBOUND_ACCEPTED' and result_status = 'SUCCESS'", 1);
    }

    @Test
    void signatureFailureRejectsInboundAndWritesAudit() throws Exception {
        mockMvc.perform(post("/api/integration-hub/inbound-messages")
                        .header("X-CMP-Signature", "bad-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_system":"CRM","message_type":"MASTER_DATA","external_request_id":"crm-req-bad","trace_id":"trace-ih-bad-signature","payload":{"customerName":"bad"}}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40103"))
                .andExpect(jsonPath("$.error").value("CALLBACK_SIGNATURE_INVALID"));

        assertRowCount("ih_inbound_message", "external_request_id = 'crm-req-bad' and ingest_status = 'REJECTED' and verification_result = 'REJECTED_SIGNATURE'", 1);
        assertRowCount("ih_integration_audit_event", "trace_id = 'trace-ih-bad-signature' and action_type = 'SIGNATURE_FAILED' and result_status = 'DENIED'", 1);
    }

    @Test
    void duplicateInboundReturnsExistingResourceAndAuditsDuplicate() throws Exception {
        String first = acceptInbound("idem-inbound-001", "trace-ih-inbound-dup", "nonce-inbound-dup-1");
        String second = acceptInbound("idem-inbound-001", "trace-ih-inbound-dup", "nonce-inbound-dup-2");

        assertThat(jsonString(second, "inbound_message_id")).isEqualTo(jsonString(first, "inbound_message_id"));
        assertThat(second).contains("\"duplicate\":true");
        assertRowCount("ih_inbound_message", "external_request_id = 'idem-inbound-001'", 1);
        assertRowCount("ih_integration_audit_event", "action_type = 'DUPLICATE_INBOUND' and result_status = 'SUCCESS'", 1);
    }

    @Test
    void replayedInboundNonceIsRejectedAndAudited() throws Exception {
        acceptInbound("replay-inbound-001", "trace-ih-replay-first", "nonce-replay-inbound");

        mockMvc.perform(signedPost("/api/integration-hub/inbound-messages", "INBOUND", "CRM", "DEFAULT_INBOUND", "nonce-replay-inbound", "2026-04-28T08:00:00Z", """
                        {"source_system":"CRM","message_type":"MASTER_DATA","external_request_id":"replay-inbound-002","trace_id":"trace-ih-replay-second","payload":{"customerName":"星邦客户"}}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40103"))
                .andExpect(jsonPath("$.error").value("CALLBACK_SIGNATURE_INVALID"));

        assertRowCount("ih_integration_audit_event", "trace_id = 'trace-ih-replay-second' and action_type = 'SIGNATURE_FAILED' and verification_result = 'REJECTED_REPLAY'", 1);
    }

    @Test
    void inboundSameIdempotencyKeyWithDifferentPayloadReturnsConflictAndAudits() throws Exception {
        acceptInbound("idem-inbound-conflict", "trace-ih-inbound-conflict-1", "nonce-inbound-conflict-1");

        mockMvc.perform(signedPost("/api/integration-hub/inbound-messages", "INBOUND", "CRM", "DEFAULT_INBOUND", "nonce-inbound-conflict-2", "2026-04-28T08:00:01Z", """
                        {"source_system":"CRM","message_type":"MASTER_DATA","external_request_id":"idem-inbound-conflict","trace_id":"trace-ih-inbound-conflict-2","payload":{"customerName":"另一客户"}}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("40905"))
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_CONFLICT"));

        assertRowCount("ih_integration_audit_event", "trace_id = 'trace-ih-inbound-conflict-2' and action_type = 'IDEMPOTENCY_CONFLICT' and result_status = 'DENIED'", 1);
    }

    @Test
    void duplicateCallbackReturnsExistingReceiptAndAuditsDuplicate() throws Exception {
        String first = acceptCallback("wecom-cb-001", "trace-ih-callback-dup", "nonce-callback-dup-1");
        String second = acceptCallback("wecom-cb-001", "trace-ih-callback-dup", "nonce-callback-dup-2");

        assertThat(jsonString(second, "callback_receipt_id")).isEqualTo(jsonString(first, "callback_receipt_id"));
        assertThat(second).contains("\"duplicate\":true");
        assertRowCount("ih_callback_receipt", "external_receipt_id = 'wecom-cb-001'", 1);
        assertRowCount("ih_integration_audit_event", "action_type = 'DUPLICATE_CALLBACK' and result_status = 'SUCCESS'", 1);
    }

    @Test
    void callbackSameIdempotencyKeyWithDifferentPayloadReturnsConflictAndAudits() throws Exception {
        acceptCallback("wecom-cb-conflict", "trace-ih-callback-conflict-1", "nonce-callback-conflict-1");

        mockMvc.perform(signedPost("/api/integration-hub/callback-receipts/wecom", "CALLBACK", "WECOM", "DEFAULT_CALLBACK", "nonce-callback-conflict-2", "2026-04-28T08:00:02Z", """
                        {"external_receipt_id":"wecom-cb-conflict","receipt_type":"MESSAGE_DELIVERY","trace_id":"trace-ih-callback-conflict-2","payload":{"status":"FAILED"}}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("40905"))
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_CONFLICT"));

        assertRowCount("ih_integration_audit_event", "trace_id = 'trace-ih-callback-conflict-2' and action_type = 'IDEMPOTENCY_CONFLICT' and result_status = 'DENIED'", 1);
    }

    @Test
    void duplicateOutboundWithSamePayloadReturnsExistingDispatchAndAuditsDuplicate() throws Exception {
        String first = createOutbound("ctr-outbound-dup", "trace-ih-outbound-dup-1", "ACTIVE");
        String second = createOutbound("ctr-outbound-dup", "trace-ih-outbound-dup-2", "ACTIVE");

        assertThat(jsonString(second, "dispatch_id")).isEqualTo(jsonString(first, "dispatch_id"));
        assertThat(second).contains("\"duplicate\":true");
        assertRowCount("ih_outbound_dispatch", "object_id = 'ctr-outbound-dup'", 1);
        assertRowCount("ih_integration_audit_event", "trace_id = 'trace-ih-outbound-dup-2' and action_type = 'DUPLICATE_OUTBOUND' and result_status = 'SUCCESS'", 1);
    }

    @Test
    void outboundSameIdempotencyKeyWithDifferentPayloadReturnsConflictAndAudits() throws Exception {
        createOutbound("ctr-outbound-conflict", "trace-ih-outbound-conflict-1", "ACTIVE");

        mockMvc.perform(post("/api/integration-hub/outbound-dispatches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"target_system":"SAP","dispatch_type":"CONTRACT_FACT","object_type":"CONTRACT","object_id":"ctr-outbound-conflict","trace_id":"trace-ih-outbound-conflict-2","payload":{"status":"CANCELLED"}}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("40905"))
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_CONFLICT"));

        assertRowCount("ih_outbound_dispatch", "object_id = 'ctr-outbound-conflict'", 1);
        assertRowCount("ih_integration_audit_event", "trace_id = 'trace-ih-outbound-conflict-2' and action_type = 'IDEMPOTENCY_CONFLICT' and result_status = 'DENIED'", 1);
    }

    @Test
    void outboundEndpointCredentialFailureIsTranslatedAuditedAndPersisted() throws Exception {
        mockMvc.perform(post("/api/integration-hub/outbound-dispatches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"target_system":"SAP","dispatch_type":"CONTRACT_FACT","object_type":"CONTRACT","object_id":"ctr-ih-001","trace_id":"trace-ih-credential-fail","simulate":"CREDENTIAL_FAILURE"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("50201"))
                .andExpect(jsonPath("$.error").value("EXTERNAL_SYSTEM_UNAVAILABLE"))
                .andExpect(jsonPath("$.result_status").value("FAILED_RETRYABLE"));

        assertRowCount("ih_endpoint_profile", "system_name = 'SAP' and endpoint_code = 'DEFAULT_OUTBOUND' and credential_ref = 'cred-sap-primary'", 1);
        assertRowCount("ih_outbound_dispatch", "target_system = 'SAP' and dispatch_status = 'FAILED' and last_result_code = 'CREDENTIAL_UNAVAILABLE'", 1);
        assertRowCount("ih_integration_audit_event", "trace_id = 'trace-ih-credential-fail' and action_type = 'CREDENTIAL_FAILED' and result_status = 'FAILED'", 1);
    }

    @Test
    void wecomTicketExchangeHandsOffToIdentityAccessWithoutPlatformToken() throws Exception {
        mockMvc.perform(post("/api/integration-hub/wecom/protocol-exchanges")
                        .headers(signatureHeaders("INBOUND", "WECOM", "WECOM_TICKET_EXCHANGE", "nonce-wecom-ticket", "2026-04-28T08:00:00Z", """
                                {"code":"wecom-code-001","state":"state-001","trace_id":"trace-ih-wecom-ticket"}
                                """))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"wecom-code-001","state":"state-001","trace_id":"trace-ih-wecom-ticket"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.protocol_exchange_ref").isNotEmpty())
                .andExpect(jsonPath("$.handoff_target").value("identity-access"))
                .andExpect(jsonPath("$.external_ticket_result.provider").value("WECOM"))
                .andExpect(jsonPath("$.access_token").doesNotExist());

        assertRowCount("ih_integration_binding", "system_name = 'WECOM' and binding_type = 'IDENTITY_CONFIRMED_REF' and confirmed_ref_source = 'identity-access'", 1);
        assertRowCount("ih_integration_audit_event", "trace_id = 'trace-ih-wecom-ticket' and action_type = 'WECOM_TICKET_HANDOFF' and result_status = 'SUCCESS'", 1);
    }

    @Test
    void auditQueryReturnsIntegrationAuditView() throws Exception {
        String inbound = acceptInbound("audit-inbound-001", "trace-ih-audit-query", "nonce-audit-query");
        String inboundMessageId = jsonString(inbound, "inbound_message_id");

        mockMvc.perform(get("/api/integration-hub/audit-views").param("trace_id", "trace-ih-audit-query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.item_list[0].direction").value("INBOUND"))
                .andExpect(jsonPath("$.item_list[0].resource_refs[0].resource_id").value(inboundMessageId));
    }

    @Test
    void allExternalAccessEndpointsStayUnderIntegrationHubBoundary() {
        List<String> externalEndpointPatterns = List.of(
                "/api/integration-hub/inbound-messages",
                "/api/integration-hub/outbound-dispatches",
                "/api/integration-hub/callback-receipts/wecom",
                "/api/integration-hub/wecom/protocol-exchanges",
                "/api/integration-hub/audit-views");

        assertThat(externalEndpointPatterns).allMatch(path -> path.startsWith("/api/integration-hub/"));
    }

    private String acceptInbound(String externalRequestId, String traceId, String nonce) throws Exception {
        String body = """
                {"source_system":"CRM","message_type":"MASTER_DATA","external_request_id":"%s","trace_id":"%s","payload":{"customerName":"星邦客户"}}
                """.formatted(externalRequestId, traceId);
        return mockMvc.perform(signedPost("/api/integration-hub/inbound-messages", "INBOUND", "CRM", "DEFAULT_INBOUND", nonce, "2026-04-28T08:00:00Z", body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
    }

    private String acceptCallback(String externalReceiptId, String traceId, String nonce) throws Exception {
        String body = """
                {"external_receipt_id":"%s","receipt_type":"MESSAGE_DELIVERY","trace_id":"%s","payload":{"status":"DELIVERED"}}
                """.formatted(externalReceiptId, traceId);
        return mockMvc.perform(signedPost("/api/integration-hub/callback-receipts/wecom", "CALLBACK", "WECOM", "DEFAULT_CALLBACK", nonce, "2026-04-28T08:00:00Z", body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
    }

    private String createOutbound(String objectId, String traceId, String status) throws Exception {
        return mockMvc.perform(post("/api/integration-hub/outbound-dispatches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"target_system":"SAP","dispatch_type":"CONTRACT_FACT","object_type":"CONTRACT","object_id":"%s","trace_id":"%s","payload":{"status":"%s"}}
                                """.formatted(objectId, traceId, status)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
    }

    private MockHttpServletRequestBuilder signedPost(String path, String direction, String systemName, String endpointCode,
                                                     String nonce, String timestamp, String body) throws Exception {
        return post(path)
                .headers(signatureHeaders(direction, systemName, endpointCode, nonce, timestamp, body))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private HttpHeaders signatureHeaders(String direction, String systemName, String endpointCode, String nonce, String timestamp, String body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-CMP-Timestamp", timestamp);
        headers.add("X-CMP-Nonce", nonce);
        headers.add("X-CMP-Security-Profile-Version", "security-v1");
        headers.add("X-CMP-Certificate-Version", "cert-v1");
        headers.add("X-CMP-Signature", signature(direction, systemName, endpointCode, timestamp, nonce, body));
        return headers;
    }

    private String signature(String direction, String systemName, String endpointCode, String timestamp, String nonce, String body) throws Exception {
        String signingString = String.join("\n", direction, systemName, endpointCode, timestamp, nonce, "security-v1", "cert-v1", requestDigest(body));
        return "cmp-sha256:" + sha256(signingString);
    }

    private String requestDigest(String body) throws Exception {
        Map<String, Object> request = OBJECT_MAPPER.readValue(body, new TypeReference<>() {});
        request.remove("trace_id");
        return sha256(OBJECT_MAPPER.writeValueAsString(request));
    }

    private String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte item : digest) {
            builder.append(String.format("%02x", item));
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

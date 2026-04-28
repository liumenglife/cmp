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
import java.util.TreeMap;
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
    void repeatedInvalidInboundReusesRejectedRecordWithoutUniqueConstraintError() throws Exception {
        String body = """
                {"source_system":"CRM","message_type":"MASTER_DATA","external_request_id":"crm-req-bad-repeat","trace_id":"trace-ih-bad-repeat","payload":{"customerName":"bad"}}
                """;
        String first = mockMvc.perform(post("/api/integration-hub/inbound-messages")
                        .header("X-CMP-Signature", "bad-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40103"))
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/api/integration-hub/inbound-messages")
                        .header("X-CMP-Signature", "bad-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40103"))
                .andExpect(jsonPath("$.inbound_message_id").value(jsonString(first, "inbound_message_id")))
                .andReturn().getResponse().getContentAsString();

        assertThat(jsonString(second, "inbound_message_id")).isEqualTo(jsonString(first, "inbound_message_id"));
        assertRowCount("ih_inbound_message", "external_request_id = 'crm-req-bad-repeat' and ingest_status = 'REJECTED' and verification_result = 'REJECTED_SIGNATURE'", 1);
        assertRowCount("ih_integration_audit_event", "trace_id = 'trace-ih-bad-repeat' and action_type = 'SIGNATURE_FAILED' and result_status = 'DENIED'", 2);
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
    void repeatedInvalidCallbackReusesRejectedReceiptWithoutUniqueConstraintError() throws Exception {
        String body = """
                {"external_receipt_id":"wecom-cb-bad-repeat","receipt_type":"MESSAGE_DELIVERY","trace_id":"trace-ih-callback-bad-repeat","payload":{"status":"DELIVERED"}}
                """;
        String first = mockMvc.perform(post("/api/integration-hub/callback-receipts/wecom")
                        .header("X-CMP-Signature", "bad-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40103"))
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/api/integration-hub/callback-receipts/wecom")
                        .header("X-CMP-Signature", "bad-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40103"))
                .andExpect(jsonPath("$.callback_receipt_id").value(jsonString(first, "callback_receipt_id")))
                .andReturn().getResponse().getContentAsString();

        assertThat(jsonString(second, "callback_receipt_id")).isEqualTo(jsonString(first, "callback_receipt_id"));
        assertRowCount("ih_callback_receipt", "external_receipt_id = 'wecom-cb-bad-repeat' and receipt_status = 'REJECTED' and verification_result = 'REJECTED_SIGNATURE'", 1);
        assertRowCount("ih_integration_audit_event", "trace_id = 'trace-ih-callback-bad-repeat' and action_type = 'SIGNATURE_FAILED' and result_status = 'DENIED'", 2);
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
    void inboundProcessingSuccessAdvancesPersistedStatusAndAudit() throws Exception {
        String inbound = acceptInbound("process-inbound-ok", "trace-ih-inbound-process-ok", "nonce-process-inbound-ok");
        String inboundMessageId = jsonString(inbound, "inbound_message_id");

        mockMvc.perform(post("/api/integration-hub/inbound-messages/{inbound_message_id}/process", inboundMessageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-inbound-process-ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processing_status").value("SUCCEEDED"));

        assertRowCount("ih_inbound_message", "inbound_message_id = '" + inboundMessageId + "' and processing_status = 'SUCCEEDED' and processed_at is not null", 1);
        assertRowCount("ih_integration_job", "resource_id = '" + inboundMessageId + "' and job_type = 'INBOUND_PROCESS' and job_status = 'SUCCEEDED'", 1);
        assertRowCount("ih_integration_audit_event", "resource_id = '" + inboundMessageId + "' and action_type = 'INBOUND_PROCESSED' and result_status = 'SUCCESS'", 1);
    }

    @Test
    void inboundProcessingFailureCreatesRecoveryCandidateAndAudit() throws Exception {
        String inbound = acceptInbound("process-inbound-fail", "trace-ih-inbound-process-fail", "nonce-process-inbound-fail");
        String inboundMessageId = jsonString(inbound, "inbound_message_id");

        mockMvc.perform(post("/api/integration-hub/inbound-messages/{inbound_message_id}/process", inboundMessageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-inbound-process-fail\",\"simulate\":\"DOWNSTREAM_FAILURE\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.processing_status").value("FAILED"));

        assertRowCount("ih_inbound_message", "inbound_message_id = '" + inboundMessageId + "' and processing_status = 'FAILED'", 1);
        assertRowCount("ih_integration_job", "resource_id = '" + inboundMessageId + "' and job_type = 'INBOUND_PROCESS' and job_status = 'FAILED_RETRYABLE'", 1);
        assertRowCount("ih_integration_audit_event", "resource_id = '" + inboundMessageId + "' and action_type = 'INBOUND_PROCESS_FAILED' and result_status = 'FAILED'", 1);
    }

    @Test
    void rejectedInboundCannotBeProcessedAsSuccess() throws Exception {
        String rejected = mockMvc.perform(post("/api/integration-hub/inbound-messages")
                        .header("X-CMP-Signature", "bad-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_system":"CRM","message_type":"MASTER_DATA","external_request_id":"process-rejected-inbound","trace_id":"trace-ih-inbound-rejected-process","payload":{"customerName":"bad"}}
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String inboundMessageId = jsonString(rejected, "inbound_message_id");

        mockMvc.perform(post("/api/integration-hub/inbound-messages/{inbound_message_id}/process", inboundMessageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-inbound-rejected-process\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("40906"))
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));

        assertRowCount("ih_inbound_message", "inbound_message_id = '" + inboundMessageId + "' and ingest_status = 'REJECTED' and verification_result = 'REJECTED_SIGNATURE' and processing_status = 'FAILED'", 1);
        assertRowCount("ih_integration_audit_event", "resource_id = '" + inboundMessageId + "' and action_type = 'INBOUND_PROCESSED'", 0);
    }

    @Test
    void outboundSendSuccessAdvancesStatusAndPersistsAttempt() throws Exception {
        String outbound = createOutbound("ctr-send-ok", "trace-ih-outbound-send-ok", "ACTIVE");
        String dispatchId = jsonString(outbound, "dispatch_id");

        mockMvc.perform(post("/api/integration-hub/outbound-dispatches/{dispatch_id}/send", dispatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-outbound-send-ok\",\"target_request_ref\":\"sap-req-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatch_status").value("SENT"));

        assertRowCount("ih_outbound_dispatch", "dispatch_id = '" + dispatchId + "' and dispatch_status = 'SENT' and target_request_ref = 'sap-req-001'", 1);
        assertRowCount("ih_outbound_attempt", "dispatch_id = '" + dispatchId + "' and attempt_status = 'SUCCEEDED'", 1);
        assertRowCount("ih_integration_audit_event", "resource_id = '" + dispatchId + "' and action_type = 'OUTBOUND_SENT' and result_status = 'SUCCESS'", 1);
    }

    @Test
    void outboundSendFailureMovesToCompensationAndPersistsAttempt() throws Exception {
        String outbound = createOutbound("ctr-send-fail", "trace-ih-outbound-send-fail", "ACTIVE");
        String dispatchId = jsonString(outbound, "dispatch_id");

        mockMvc.perform(post("/api/integration-hub/outbound-dispatches/{dispatch_id}/send", dispatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-outbound-send-fail\",\"simulate\":\"EXTERNAL_TIMEOUT\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.dispatch_status").value("WAIT_COMPENSATION"));

        assertRowCount("ih_outbound_dispatch", "dispatch_id = '" + dispatchId + "' and dispatch_status = 'WAIT_COMPENSATION' and last_result_code = 'EXTERNAL_TIMEOUT'", 1);
        assertRowCount("ih_outbound_attempt", "dispatch_id = '" + dispatchId + "' and attempt_status = 'FAILED'", 1);
        assertRowCount("ih_integration_audit_event", "resource_id = '" + dispatchId + "' and action_type = 'OUTBOUND_SEND_FAILED' and result_status = 'FAILED'", 1);
    }

    @Test
    void callbackProcessingSuccessAcknowledgesLinkedDispatch() throws Exception {
        String outbound = createOutbound("ctr-callback-ok", "trace-ih-callback-process-ok", "ACTIVE");
        String dispatchId = jsonString(outbound, "dispatch_id");
        String body = """
                {"external_receipt_id":"cb-process-ok","receipt_type":"MESSAGE_DELIVERY","linked_dispatch_id":"%s","trace_id":"trace-ih-callback-process-ok","payload":{"status":"DELIVERED"}}
                """.formatted(dispatchId);
        String callback = mockMvc.perform(signedPost("/api/integration-hub/callback-receipts/wecom", "CALLBACK", "WECOM", "DEFAULT_CALLBACK", "nonce-callback-process-ok", "2026-04-28T08:00:00Z", body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String callbackReceiptId = jsonString(callback, "callback_receipt_id");

        mockMvc.perform(post("/api/integration-hub/callback-receipts/{callback_receipt_id}/process", callbackReceiptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-callback-process-ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processing_status").value("SUCCEEDED"));

        assertRowCount("ih_callback_receipt", "callback_receipt_id = '" + callbackReceiptId + "' and processing_status = 'SUCCEEDED'", 1);
        assertRowCount("ih_outbound_dispatch", "dispatch_id = '" + dispatchId + "' and dispatch_status = 'ACKED'", 1);
        assertRowCount("ih_integration_audit_event", "resource_id = '" + callbackReceiptId + "' and action_type = 'CALLBACK_PROCESSED' and result_status = 'SUCCESS'", 1);
    }

    @Test
    void compensationDiscoveryAndExecutionProcessesFailedOutbound() throws Exception {
        String outbound = createOutbound("ctr-compensate-outbound", "trace-ih-compensation", "ACTIVE");
        String dispatchId = jsonString(outbound, "dispatch_id");
        mockMvc.perform(post("/api/integration-hub/outbound-dispatches/{dispatch_id}/send", dispatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-compensation\",\"simulate\":\"EXTERNAL_TIMEOUT\"}"))
                .andExpect(status().isBadGateway());

        String discovered = mockMvc.perform(post("/api/integration-hub/compensations/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-compensation\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.created_count").value(1))
                .andReturn().getResponse().getContentAsString();
        String recoveryTicketId = jsonString(discovered, "first_recovery_ticket_id");

        mockMvc.perform(post("/api/integration-hub/compensations/{recovery_ticket_id}/execute", recoveryTicketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-compensation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recovery_status").value("RESOLVED"));

        assertRowCount("ih_recovery_ticket", "recovery_ticket_id = '" + recoveryTicketId + "' and recovery_status = 'RESOLVED'", 1);
        assertRowCount("ih_outbound_dispatch", "dispatch_id = '" + dispatchId + "' and dispatch_status = 'SENT'", 1);
        assertRowCount("ih_integration_audit_event", "trace_id = 'trace-ih-compensation' and action_type = 'COMPENSATION_EXECUTED' and result_status = 'SUCCESS'", 1);
    }

    @Test
    void rejectedInboundIsNotDiscoveredOrAdvancedByCompensation() throws Exception {
        String rejected = mockMvc.perform(post("/api/integration-hub/inbound-messages")
                        .header("X-CMP-Signature", "bad-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_system":"CRM","message_type":"MASTER_DATA","external_request_id":"compensation-rejected-inbound","trace_id":"trace-ih-compensation-rejected-inbound","payload":{"customerName":"bad"}}
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String inboundMessageId = jsonString(rejected, "inbound_message_id");

        mockMvc.perform(post("/api/integration-hub/compensations/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-compensation-rejected-inbound\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.created_count").value(0));

        String recoveryTicketId = "ih-rec-rejected-inbound";
        jdbcTemplate.update("""
                insert into ih_recovery_ticket
                (recovery_ticket_id, resource_type, resource_id, failure_stage, ticket_round_no, root_ticket_id, diff_id, ledger_entry_id,
                 result_evidence_group_id, result_evidence_object_id, last_audit_ref_id, recovery_status, recovery_strategy, manual_owner_id,
                 root_cause_code, root_cause_summary, trace_id, last_retry_at, resolved_at, created_at, updated_at)
                values (?, 'InboundMessage', ?, 'INBOUND_PROCESS', 1, null, null, null, 'ev-rejected-inbound', null, null, 'OPEN', 'REPLAY_OR_RETRY', null,
                        'REJECTED_SIGNATURE', '被拒绝入站不得补偿执行', 'trace-ih-compensation-rejected-inbound', null, null, current_timestamp, current_timestamp)
                """, recoveryTicketId, inboundMessageId);

        mockMvc.perform(post("/api/integration-hub/compensations/{recovery_ticket_id}/execute", recoveryTicketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-compensation-rejected-inbound\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("40906"))
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));

        assertRowCount("ih_recovery_ticket", "recovery_ticket_id = '" + recoveryTicketId + "' and recovery_status = 'OPEN'", 1);
        assertRowCount("ih_inbound_message", "inbound_message_id = '" + inboundMessageId + "' and ingest_status = 'REJECTED' and verification_result <> 'VERIFIED' and processing_status = 'FAILED'", 1);
        assertRowCount("ih_integration_audit_event", "resource_id = '" + recoveryTicketId + "' and action_type = 'COMPENSATION_EXECUTED'", 0);
    }

    @Test
    void reconciliationGeneratesConsistentAndDiffResults() throws Exception {
        String outbound = createOutbound("ctr-reconcile-consistent", "trace-ih-reconcile", "ACTIVE");
        String dispatchId = jsonString(outbound, "dispatch_id");
        mockMvc.perform(post("/api/integration-hub/outbound-dispatches/{dispatch_id}/send", dispatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-reconcile\",\"target_request_ref\":\"sap-reconcile-ok\"}"))
                .andExpect(status().isOk());
        String body = """
                {"external_receipt_id":"cb-reconcile-ok","receipt_type":"MESSAGE_DELIVERY","linked_dispatch_id":"%s","trace_id":"trace-ih-reconcile","payload":{"status":"DELIVERED"}}
                """.formatted(dispatchId);
        String callback = mockMvc.perform(signedPost("/api/integration-hub/callback-receipts/wecom", "CALLBACK", "WECOM", "DEFAULT_CALLBACK", "nonce-callback-reconcile-ok", "2026-04-28T08:00:00Z", body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/integration-hub/callback-receipts/{callback_receipt_id}/process", jsonString(callback, "callback_receipt_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-reconcile\"}"))
                .andExpect(status().isOk());
        String failedOutbound = createOutbound("ctr-reconcile-diff", "trace-ih-reconcile", "ACTIVE");
        mockMvc.perform(post("/api/integration-hub/outbound-dispatches/{dispatch_id}/send", jsonString(failedOutbound, "dispatch_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-reconcile\",\"simulate\":\"EXTERNAL_TIMEOUT\"}"))
                .andExpect(status().isBadGateway());

        mockMvc.perform(post("/api/integration-hub/reconciliations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-reconcile\",\"system_name\":\"SAP\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.record_count").value(2))
                .andExpect(jsonPath("$.diff_count").value(1));

        assertRowCount("ih_reconciliation_record", "record_result = 'CONSISTENT'", 1);
        assertRowCount("ih_reconciliation_record", "record_result = 'DIFF'", 1);
        assertRowCount("ih_reconciliation_diff", "diff_type = 'MISSING_ON_EXTERNAL' and current_state = 'PENDING_RECOVERY_TICKET'", 1);
        assertRowCount("ih_integration_audit_event", "trace_id = 'trace-ih-reconcile' and action_type = 'RECONCILIATION_FINISHED' and result_status = 'SUCCESS'", 1);
    }

    @Test
    void repeatedReconciliationForSameUnresolvedDiffIsIdempotent() throws Exception {
        String failedOutbound = createOutbound("ctr-reconcile-repeat-diff", "trace-ih-reconcile-repeat", "ACTIVE");
        mockMvc.perform(post("/api/integration-hub/outbound-dispatches/{dispatch_id}/send", jsonString(failedOutbound, "dispatch_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-reconcile-repeat\",\"simulate\":\"EXTERNAL_TIMEOUT\"}"))
                .andExpect(status().isBadGateway());

        mockMvc.perform(post("/api/integration-hub/reconciliations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-reconcile-repeat\",\"system_name\":\"SAP\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.record_count").value(1))
                .andExpect(jsonPath("$.diff_count").value(1));
        String existingDiffId = jdbcTemplate.queryForObject("select diff_id from ih_reconciliation_diff where diff_identity_key = 'SAP:CONTRACT:ctr-reconcile-repeat-diff:MISSING_ON_EXTERNAL'", String.class);

        mockMvc.perform(post("/api/integration-hub/reconciliations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-reconcile-repeat\",\"system_name\":\"SAP\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.record_count").value(1))
                .andExpect(jsonPath("$.diff_count").value(0));

        assertRowCount("ih_reconciliation_diff", "diff_identity_key = 'SAP:CONTRACT:ctr-reconcile-repeat-diff:MISSING_ON_EXTERNAL' and current_state = 'PENDING_RECOVERY_TICKET'", 1);
        assertThat(jdbcTemplate.queryForObject("select diff_id from ih_reconciliation_diff where diff_identity_key = 'SAP:CONTRACT:ctr-reconcile-repeat-diff:MISSING_ON_EXTERNAL'", String.class))
                .isEqualTo(existingDiffId);
    }

    @Test
    void auditQueryContainsInboundOutboundCallbackCompensationAndReconciliationEvents() throws Exception {
        String inbound = acceptInbound("audit-all-inbound", "trace-ih-audit-all", "nonce-audit-all-inbound");
        mockMvc.perform(post("/api/integration-hub/inbound-messages/{inbound_message_id}/process", jsonString(inbound, "inbound_message_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-audit-all\"}"))
                .andExpect(status().isOk());
        String outbound = createOutbound("ctr-audit-all", "trace-ih-audit-all", "ACTIVE");
        String dispatchId = jsonString(outbound, "dispatch_id");
        mockMvc.perform(post("/api/integration-hub/outbound-dispatches/{dispatch_id}/send", dispatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-audit-all\",\"simulate\":\"EXTERNAL_TIMEOUT\"}"))
                .andExpect(status().isBadGateway());
        String callback = acceptCallback("audit-all-callback", "trace-ih-audit-all", "nonce-audit-all-callback");
        mockMvc.perform(post("/api/integration-hub/callback-receipts/{callback_receipt_id}/process", jsonString(callback, "callback_receipt_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-audit-all\"}"))
                .andExpect(status().isOk());
        String discovered = mockMvc.perform(post("/api/integration-hub/compensations/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-audit-all\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/integration-hub/compensations/{recovery_ticket_id}/execute", jsonString(discovered, "first_recovery_ticket_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-audit-all\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/integration-hub/reconciliations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-audit-all\",\"system_name\":\"SAP\"}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/integration-hub/audit-views").param("trace_id", "trace-ih-audit-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item_list[?(@.summary == 'INBOUND_PROCESSED')]").isNotEmpty())
                .andExpect(jsonPath("$.item_list[?(@.summary == 'OUTBOUND_SEND_FAILED')]").isNotEmpty())
                .andExpect(jsonPath("$.item_list[?(@.summary == 'CALLBACK_PROCESSED')]").isNotEmpty())
                .andExpect(jsonPath("$.item_list[?(@.summary == 'COMPENSATION_EXECUTED')]").isNotEmpty())
                .andExpect(jsonPath("$.item_list[?(@.summary == 'RECONCILIATION_FINISHED')]").isNotEmpty());
    }

    @Test
    void traceIdChangesDoNotTriggerIdempotencyConflictButBusinessFieldChangesDo() throws Exception {
        String first = acceptInbound("trace-change-retry", "trace-ih-business-equivalent-1", "nonce-business-equivalent-1");
        String second = acceptInbound("trace-change-retry", "trace-ih-business-equivalent-2", "nonce-business-equivalent-2");

        assertThat(jsonString(second, "inbound_message_id")).isEqualTo(jsonString(first, "inbound_message_id"));
        assertThat(second).contains("\"duplicate\":true");

        mockMvc.perform(signedPost("/api/integration-hub/inbound-messages", "INBOUND", "CRM", "DEFAULT_INBOUND", "nonce-business-equivalent-3", "2026-04-28T08:00:03Z", """
                        {"source_system":"CRM","message_type":"MASTER_DATA","external_request_id":"trace-change-retry","trace_id":"trace-ih-business-equivalent-3","payload":{"customerName":"另一客户"}}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("40905"));
    }

    @Test
    void equivalentInboundWithDifferentObjectFieldOrderReturnsExistingResource() throws Exception {
        String firstBody = """
                {"source_system":"CRM","message_type":"MASTER_DATA","external_request_id":"field-order-retry","trace_id":"trace-ih-field-order-1","payload":{"customerName":"星邦客户","externalId":"crm-cus-order"}}
                """;
        String secondBody = """
                {"payload":{"externalId":"crm-cus-order","customerName":"星邦客户"},"trace_id":"trace-ih-field-order-2","external_request_id":"field-order-retry","message_type":"MASTER_DATA","source_system":"CRM"}
                """;
        String first = mockMvc.perform(signedPost("/api/integration-hub/inbound-messages", "INBOUND", "CRM", "DEFAULT_INBOUND", "nonce-field-order-1", "2026-04-28T08:00:00Z", firstBody))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(signedPost("/api/integration-hub/inbound-messages", "INBOUND", "CRM", "DEFAULT_INBOUND", "nonce-field-order-2", "2026-04-28T08:00:01Z", secondBody))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(jsonString(second, "inbound_message_id")).isEqualTo(jsonString(first, "inbound_message_id"));
        assertThat(second).contains("\"duplicate\":true");
        assertRowCount("ih_inbound_message", "external_request_id = 'field-order-retry'", 1);
    }

    @Test
    void callbackProcessingWithUnknownLinkedDispatchFailsAndAudits() throws Exception {
        String body = """
                {"external_receipt_id":"cb-missing-dispatch","receipt_type":"MESSAGE_DELIVERY","linked_dispatch_id":"ih-out-missing","trace_id":"trace-ih-callback-missing-dispatch","payload":{"status":"DELIVERED"}}
                """;
        String callback = mockMvc.perform(signedPost("/api/integration-hub/callback-receipts/wecom", "CALLBACK", "WECOM", "DEFAULT_CALLBACK", "nonce-callback-missing-dispatch", "2026-04-28T08:00:00Z", body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String callbackReceiptId = jsonString(callback, "callback_receipt_id");

        mockMvc.perform(post("/api/integration-hub/callback-receipts/{callback_receipt_id}/process", callbackReceiptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-callback-missing-dispatch\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.processing_status").value("FAILED"))
                .andExpect(jsonPath("$.code").value("42208"));

        assertRowCount("ih_callback_receipt", "callback_receipt_id = '" + callbackReceiptId + "' and processing_status = 'FAILED'", 1);
        assertRowCount("ih_integration_audit_event", "resource_id = '" + callbackReceiptId + "' and action_type = 'CALLBACK_LINKED_DISPATCH_MISSING' and result_status = 'FAILED'", 1);
    }

    @Test
    void rejectedCallbackCannotBeProcessedAsSuccess() throws Exception {
        String rejected = mockMvc.perform(post("/api/integration-hub/callback-receipts/wecom")
                        .header("X-CMP-Signature", "bad-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"external_receipt_id":"cb-rejected-process","receipt_type":"MESSAGE_DELIVERY","trace_id":"trace-ih-callback-rejected-process","payload":{"status":"DELIVERED"}}
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String callbackReceiptId = jsonString(rejected, "callback_receipt_id");

        mockMvc.perform(post("/api/integration-hub/callback-receipts/{callback_receipt_id}/process", callbackReceiptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-callback-rejected-process\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("40906"))
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));

        assertRowCount("ih_callback_receipt", "callback_receipt_id = '" + callbackReceiptId + "' and receipt_status = 'REJECTED' and verification_result = 'REJECTED_SIGNATURE' and processing_status = 'FAILED'", 1);
        assertRowCount("ih_integration_audit_event", "resource_id = '" + callbackReceiptId + "' and action_type = 'CALLBACK_PROCESSED'", 0);
    }

    @Test
    void compensationDiscoveryCreatesNextRoundAfterResolvedTicketFailsAgain() throws Exception {
        String outbound = createOutbound("ctr-compensation-round", "trace-ih-compensation-round", "ACTIVE");
        String dispatchId = jsonString(outbound, "dispatch_id");
        mockMvc.perform(post("/api/integration-hub/outbound-dispatches/{dispatch_id}/send", dispatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-compensation-round\",\"simulate\":\"EXTERNAL_TIMEOUT\"}"))
                .andExpect(status().isBadGateway());
        String firstDiscovered = mockMvc.perform(post("/api/integration-hub/compensations/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-compensation-round\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.created_count").value(1))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/integration-hub/compensations/{recovery_ticket_id}/execute", jsonString(firstDiscovered, "first_recovery_ticket_id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-compensation-round\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/integration-hub/outbound-dispatches/{dispatch_id}/send", dispatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-compensation-round\",\"simulate\":\"EXTERNAL_TIMEOUT\"}"))
                .andExpect(status().isBadGateway());

        String secondDiscovered = mockMvc.perform(post("/api/integration-hub/compensations/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-compensation-round\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.created_count").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(jsonString(secondDiscovered, "first_recovery_ticket_id")).isNotEqualTo(jsonString(firstDiscovered, "first_recovery_ticket_id"));
        assertRowCount("ih_recovery_ticket", "resource_id = '" + dispatchId + "'", 2);
        assertRowCount("ih_recovery_ticket", "resource_id = '" + dispatchId + "' and ticket_round_no = 2 and recovery_status = 'OPEN'", 1);
    }

    @Test
    void resolvedCompensationTicketCannotBeExecutedAgain() throws Exception {
        String outbound = createOutbound("ctr-compensation-repeat", "trace-ih-compensation-repeat", "ACTIVE");
        String dispatchId = jsonString(outbound, "dispatch_id");
        mockMvc.perform(post("/api/integration-hub/outbound-dispatches/{dispatch_id}/send", dispatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-compensation-repeat\",\"simulate\":\"EXTERNAL_TIMEOUT\"}"))
                .andExpect(status().isBadGateway());
        String discovered = mockMvc.perform(post("/api/integration-hub/compensations/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-compensation-repeat\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String recoveryTicketId = jsonString(discovered, "first_recovery_ticket_id");

        mockMvc.perform(post("/api/integration-hub/compensations/{recovery_ticket_id}/execute", recoveryTicketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-compensation-repeat\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/integration-hub/compensations/{recovery_ticket_id}/execute", recoveryTicketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trace_id\":\"trace-ih-compensation-repeat\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("40906"))
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));

        assertRowCount("ih_recovery_ticket", "recovery_ticket_id = '" + recoveryTicketId + "' and recovery_status = 'RESOLVED'", 1);
        assertRowCount("ih_integration_audit_event", "resource_id = '" + recoveryTicketId + "' and action_type = 'COMPENSATION_EXECUTED'", 1);
    }

    @Test
    void allExternalAccessEndpointsStayUnderIntegrationHubBoundary() {
        List<String> externalEndpointPatterns = List.of(
                "/api/integration-hub/inbound-messages",
                "/api/integration-hub/inbound-messages/{inbound_message_id}/process",
                "/api/integration-hub/outbound-dispatches",
                "/api/integration-hub/outbound-dispatches/{dispatch_id}/send",
                "/api/integration-hub/callback-receipts/wecom",
                "/api/integration-hub/callback-receipts/{callback_receipt_id}/process",
                "/api/integration-hub/compensations/discover",
                "/api/integration-hub/compensations/{recovery_ticket_id}/execute",
                "/api/integration-hub/reconciliations",
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
        return sha256(OBJECT_MAPPER.writeValueAsString(businessFields(request)));
    }

    private Object businessFields(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> stable = new TreeMap<>();
            raw.forEach((key, item) -> {
                String fieldName = key.toString();
                if (!"trace_id".equals(fieldName) && !"span_id".equals(fieldName) && !"request_trace_id".equals(fieldName)) {
                    stable.put(fieldName, businessFields(item));
                }
            });
            return stable;
        }
        if (value instanceof List<?> raw) {
            return raw.stream().map(this::businessFields).toList();
        }
        return value;
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

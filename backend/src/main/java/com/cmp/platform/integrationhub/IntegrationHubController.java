package com.cmp.platform.integrationhub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class IntegrationHubController {

    private final IntegrationHubService service;

    IntegrationHubController(IntegrationHubService service) {
        this.service = service;
    }

    @PostMapping("/api/integration-hub/inbound-messages")
    ResponseEntity<Map<String, Object>> inbound(
            @RequestHeader(value = "X-CMP-Signature", required = false) String signature,
            @RequestHeader(value = "X-CMP-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-CMP-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-CMP-Security-Profile-Version", required = false) String securityProfileVersion,
            @RequestHeader(value = "X-CMP-Certificate-Version", required = false) String certificateVersion,
            @RequestBody Map<String, Object> request) {
        IntegrationHubService.Outcome outcome = service.acceptInbound(new IntegrationHubService.SecurityHeaders(
                signature, timestamp, nonce, securityProfileVersion, certificateVersion), request);
        return ResponseEntity.status(outcome.status()).body(outcome.body());
    }

    @PostMapping("/api/integration-hub/outbound-dispatches")
    ResponseEntity<Map<String, Object>> outbound(@RequestBody Map<String, Object> request) {
        IntegrationHubService.Outcome outcome = service.createOutbound(request);
        return ResponseEntity.status(outcome.status()).body(outcome.body());
    }

    @PostMapping("/api/integration-hub/callback-receipts/{sourceSystem}")
    ResponseEntity<Map<String, Object>> callback(
            @PathVariable String sourceSystem,
            @RequestHeader(value = "X-CMP-Signature", required = false) String signature,
            @RequestHeader(value = "X-CMP-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-CMP-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-CMP-Security-Profile-Version", required = false) String securityProfileVersion,
            @RequestHeader(value = "X-CMP-Certificate-Version", required = false) String certificateVersion,
            @RequestBody Map<String, Object> request) {
        IntegrationHubService.Outcome outcome = service.acceptCallback(sourceSystem, new IntegrationHubService.SecurityHeaders(
                signature, timestamp, nonce, securityProfileVersion, certificateVersion), request);
        return ResponseEntity.status(outcome.status()).body(outcome.body());
    }

    @PostMapping("/api/integration-hub/wecom/protocol-exchanges")
    ResponseEntity<Map<String, Object>> wecomProtocolExchange(
            @RequestHeader(value = "X-CMP-Signature", required = false) String signature,
            @RequestHeader(value = "X-CMP-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-CMP-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-CMP-Security-Profile-Version", required = false) String securityProfileVersion,
            @RequestHeader(value = "X-CMP-Certificate-Version", required = false) String certificateVersion,
            @RequestBody Map<String, Object> request) {
        IntegrationHubService.Outcome outcome = service.wecomProtocolExchange(new IntegrationHubService.SecurityHeaders(
                signature, timestamp, nonce, securityProfileVersion, certificateVersion), request);
        return ResponseEntity.status(outcome.status()).body(outcome.body());
    }

    @GetMapping("/api/integration-hub/audit-views")
    Map<String, Object> auditViews(@RequestParam(value = "trace_id", required = false) String traceId) {
        return service.auditViews(traceId);
    }
}

@org.springframework.stereotype.Service
class IntegrationHubService {

    private static final String MAPPING_VERSION = "mapping-v1";
    private static final String MODEL_VERSION = "model-v1";
    private static final String SECURITY_PROFILE_VERSION = "security-v1";
    private static final String CERTIFICATE_VERSION = "cert-v1";
    private static final String PROFILE_VERSION = "profile-v1";
    private static final Duration SIGNATURE_REPLAY_WINDOW = Duration.ofHours(24);

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    IntegrationHubService(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    Outcome acceptInbound(SecurityHeaders headers, Map<String, Object> request) {
        String sourceSystem = system(text(request, "source_system", "UNKNOWN"));
        String messageType = text(request, "message_type", "EVENT");
        String externalRequestId = text(request, "external_request_id", "req-" + UUID.randomUUID());
        String idempotencyKey = sourceSystem + ":" + messageType + ":" + externalRequestId;
        String traceId = trace(request);
        EndpointProfile endpoint = endpoint(sourceSystem, "INBOUND", "DEFAULT_INBOUND");
        String requestDigest = requestDigest(request);
        SecurityCheck security = verify(headers, requestDigest, sourceSystem, "INBOUND", endpoint.endpointCode());
        String inboundId = "ih-in-" + UUID.randomUUID();
        Instant now = now();
        if (!security.verified()) {
            insertInbound(inboundId, sourceSystem, messageType, externalRequestId, idempotencyKey, requestDigest, request, "REJECTED", "FAILED",
                    security.verificationResult(), traceId, now);
            audit("INBOUND", "InboundMessage", inboundId, "SIGNATURE_FAILED", "DENIED", sourceSystem, text(request, "object_type", "MASTER_DATA"),
                    externalRequestId, security.verificationResult(), traceId, "40103", "CALLBACK_SIGNATURE_INVALID");
            return new Outcome(HttpStatus.UNAUTHORIZED, Map.of("code", "40103", "error", "CALLBACK_SIGNATURE_INVALID", "inbound_message_id", inboundId));
        }
        rememberNonce(headers, requestDigest, sourceSystem, "INBOUND", endpoint.endpointCode(), now);

        Optional<Map<String, Object>> existing = findInbound(sourceSystem, idempotencyKey);
        if (existing.isPresent()) {
            String existingDigest = existing.get().get("request_digest").toString();
            if (!existingDigest.equals(requestDigest)) {
                return idempotencyConflict("INBOUND", "InboundMessage", existing.get().get("inbound_message_id").toString(), sourceSystem,
                        text(request, "object_type", "MASTER_DATA"), externalRequestId, traceId);
            }
            audit("INBOUND", "InboundMessage", existing.get().get("inbound_message_id").toString(), "DUPLICATE_INBOUND", "SUCCESS",
                    sourceSystem, text(request, "object_type", "MASTER_DATA"), externalRequestId, "VERIFIED", traceId, null, null);
            Map<String, Object> body = inboundBody(existing.get().get("inbound_message_id").toString());
            body.put("duplicate", true);
            return new Outcome(HttpStatus.ACCEPTED, body);
        }

        insertInbound(inboundId, sourceSystem, messageType, externalRequestId, idempotencyKey, requestDigest, request, "ACCEPTED", "PENDING", "VERIFIED", traceId, now);
        insertJob("INBOUND_PROCESS", "InboundMessage", inboundId, sourceSystem, null, null, null);
        audit("INBOUND", "InboundMessage", inboundId, "INBOUND_ACCEPTED", "SUCCESS", sourceSystem, text(request, "object_type", "MASTER_DATA"),
                externalRequestId, "VERIFIED", traceId, null, null);

        Map<String, Object> body = inboundBody(inboundId);
        body.put("runtime_context", runtimeContext(sourceSystem, "INBOUND", endpoint));
        body.put("normalized_payload", normalizePayload(objectMap(request.get("payload"))));
        body.put("duplicate", false);
        return new Outcome(HttpStatus.ACCEPTED, body);
    }

    @Transactional
    Outcome createOutbound(Map<String, Object> request) {
        String targetSystem = system(text(request, "target_system", "UNKNOWN"));
        String dispatchType = text(request, "dispatch_type", "MESSAGE");
        String objectType = text(request, "object_type", "OBJECT");
        String objectId = text(request, "object_id", "obj-" + UUID.randomUUID());
        String traceId = trace(request);
        EndpointProfile endpoint = endpoint(targetSystem, "OUTBOUND", "DEFAULT_OUTBOUND");
        String dispatchId = "ih-out-" + UUID.randomUUID();
        String idempotencyKey = targetSystem + ":" + dispatchType + ":" + objectType + ":" + objectId;
        String requestDigest = requestDigest(request);
        Optional<Map<String, Object>> existing = findOutbound(targetSystem, idempotencyKey);
        if (existing.isPresent()) {
            String existingDigest = existing.get().get("request_digest").toString();
            if (!existingDigest.equals(requestDigest)) {
                return idempotencyConflict("OUTBOUND", "OutboundDispatch", existing.get().get("dispatch_id").toString(), targetSystem, objectType, objectId, traceId);
            }
            audit("OUTBOUND", "OutboundDispatch", existing.get().get("dispatch_id").toString(), "DUPLICATE_OUTBOUND", "SUCCESS",
                    targetSystem, objectType, objectId, "VERIFIED", traceId, null, null);
            Map<String, Object> body = dispatchBody(existing.get().get("dispatch_id").toString());
            body.put("duplicate", true);
            return new Outcome(HttpStatus.ACCEPTED, body);
        }
        boolean credentialFailure = "CREDENTIAL_FAILURE".equals(text(request, "simulate", ""));
        Instant now = now();
        jdbcTemplate.update("""
                insert into ih_outbound_dispatch
                (dispatch_id, target_system, dispatch_type, object_type, object_id, projection_version, mapping_version, model_version,
                 security_profile_version, certificate_version, verification_result, profile_version, dispatch_status, callback_expected,
                 target_request_ref, idempotency_key, evidence_group_id, dispatch_payload_ref, request_digest, last_result_code, last_result_message,
                 attempt_count, next_retry_at, last_attempt_at, completed_at, trace_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, dispatchId, targetSystem, dispatchType, objectType, objectId, "projection-v1", MAPPING_VERSION, MODEL_VERSION,
                SECURITY_PROFILE_VERSION, CERTIFICATE_VERSION, credentialFailure ? "REJECTED_CONTEXT_MISMATCH" : "VERIFIED", PROFILE_VERSION,
                credentialFailure ? "FAILED" : "CREATED", false, null, idempotencyKey, "ev-" + dispatchId,
                "payload://dispatch/" + dispatchId, requestDigest, credentialFailure ? "CREDENTIAL_UNAVAILABLE" : null,
                credentialFailure ? "凭证引用不可用或外部端点不可达" : null, 1, credentialFailure ? ts(now.plusSeconds(60)) : null,
                ts(now), credentialFailure ? null : ts(now), traceId, ts(now), ts(now));
        if (credentialFailure) {
            insertJob("OUTBOUND_DISPATCH", "OutboundDispatch", dispatchId, null, targetSystem, "CREDENTIAL_UNAVAILABLE", "凭证失败，等待重试");
            audit("OUTBOUND", "OutboundDispatch", dispatchId, "CREDENTIAL_FAILED", "FAILED", targetSystem, objectType, objectId,
                    "REJECTED_CONTEXT_MISMATCH", traceId, "50201", "EXTERNAL_SYSTEM_UNAVAILABLE");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", "50201");
            body.put("error", "EXTERNAL_SYSTEM_UNAVAILABLE");
            body.put("dispatch_id", dispatchId);
            body.put("result_status", "FAILED_RETRYABLE");
            body.put("runtime_context", runtimeContext(targetSystem, "OUTBOUND", endpoint));
            return new Outcome(HttpStatus.BAD_GATEWAY, body);
        }
        audit("OUTBOUND", "OutboundDispatch", dispatchId, "OUTBOUND_ACCEPTED", "SUCCESS", targetSystem, objectType, objectId, "VERIFIED", traceId, null, null);
        return new Outcome(HttpStatus.ACCEPTED, dispatchBody(dispatchId));
    }

    @Transactional
    Outcome acceptCallback(String sourceSystemPath, SecurityHeaders headers, Map<String, Object> request) {
        String sourceSystem = system(sourceSystemPath);
        String receiptType = text(request, "receipt_type", "EVENT");
        String externalReceiptId = text(request, "external_receipt_id", "cb-" + UUID.randomUUID());
        String idempotencyKey = sourceSystem + ":" + receiptType + ":" + externalReceiptId;
        String traceId = trace(request);
        EndpointProfile endpoint = endpoint(sourceSystem, "CALLBACK", "DEFAULT_CALLBACK");
        String requestDigest = requestDigest(request);
        SecurityCheck security = verify(headers, requestDigest, sourceSystem, "CALLBACK", endpoint.endpointCode());
        String callbackId = "ih-cb-" + UUID.randomUUID();
        Instant now = now();
        if (!security.verified()) {
            insertCallback(callbackId, sourceSystem, receiptType, externalReceiptId, idempotencyKey, requestDigest, request, "REJECTED", "FAILED",
                    security.verificationResult(), traceId, now);
            audit("CALLBACK", "CallbackReceipt", callbackId, "SIGNATURE_FAILED", "DENIED", sourceSystem, "CALLBACK", externalReceiptId,
                    security.verificationResult(), traceId, "40103", "CALLBACK_SIGNATURE_INVALID");
            return new Outcome(HttpStatus.UNAUTHORIZED, Map.of("code", "40103", "error", "CALLBACK_SIGNATURE_INVALID", "callback_receipt_id", callbackId));
        }
        rememberNonce(headers, requestDigest, sourceSystem, "CALLBACK", endpoint.endpointCode(), now);

        Optional<Map<String, Object>> existing = findCallback(sourceSystem, idempotencyKey);
        if (existing.isPresent()) {
            String existingDigest = existing.get().get("request_digest").toString();
            if (!existingDigest.equals(requestDigest)) {
                return idempotencyConflict("CALLBACK", "CallbackReceipt", existing.get().get("callback_receipt_id").toString(), sourceSystem,
                        "CALLBACK", externalReceiptId, traceId);
            }
            audit("CALLBACK", "CallbackReceipt", existing.get().get("callback_receipt_id").toString(), "DUPLICATE_CALLBACK", "SUCCESS",
                    sourceSystem, "CALLBACK", externalReceiptId, "VERIFIED", traceId, null, null);
            Map<String, Object> body = callbackBody(existing.get().get("callback_receipt_id").toString());
            body.put("duplicate", true);
            return new Outcome(HttpStatus.ACCEPTED, body);
        }
        insertCallback(callbackId, sourceSystem, receiptType, externalReceiptId, idempotencyKey, requestDigest, request, "ACCEPTED", "PENDING", "VERIFIED", traceId, now);
        audit("CALLBACK", "CallbackReceipt", callbackId, "CALLBACK_ACCEPTED", "SUCCESS", sourceSystem, "CALLBACK", externalReceiptId, "VERIFIED", traceId, null, null);
        Map<String, Object> body = callbackBody(callbackId);
        body.put("duplicate", false);
        return new Outcome(HttpStatus.ACCEPTED, body);
    }

    private void insertCallback(String callbackId, String sourceSystem, String receiptType, String externalReceiptId, String idempotencyKey,
                                String requestDigest, Map<String, Object> request, String receiptStatus, String processingStatus,
                                String verification, String traceId, Instant now) {
        jdbcTemplate.update("""
                insert into ih_callback_receipt
                (callback_receipt_id, source_system, receipt_type, external_receipt_id, linked_dispatch_id, linked_binding_id,
                  idempotency_key, receipt_status, processing_status, mapping_version, model_version, security_profile_version,
                  certificate_version, verification_result, profile_version, evidence_group_id, event_sequence, occurred_at, received_at,
                  raw_payload_ref, normalized_payload_json, request_digest, conflict_reason, trace_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, callbackId, sourceSystem, receiptType, externalReceiptId, text(request, "linked_dispatch_id", null), null, idempotencyKey,
                receiptStatus, processingStatus, MAPPING_VERSION, MODEL_VERSION,
                SECURITY_PROFILE_VERSION, CERTIFICATE_VERSION, verification, PROFILE_VERSION, "ev-" + callbackId, number(request, "event_sequence", 1),
                ts(now), ts(now), "raw://callback/" + callbackId, json(normalizePayload(objectMap(request.get("payload")))), requestDigest, null, traceId, ts(now), ts(now));
    }

    @Transactional
    Outcome wecomProtocolExchange(SecurityHeaders headers, Map<String, Object> request) {
        String traceId = trace(request);
        String exchangeRef = "pex-wecom-" + UUID.randomUUID();
        EndpointProfile endpoint = endpoint("WECOM", "INBOUND", "WECOM_TICKET_EXCHANGE");
        String requestDigest = requestDigest(request);
        SecurityCheck security = verify(headers, requestDigest, "WECOM", "INBOUND", endpoint.endpointCode());
        if (!security.verified()) {
            audit("INBOUND", "ProtocolExchange", exchangeRef, "SIGNATURE_FAILED", "DENIED", "WECOM", "IDENTITY", exchangeRef,
                    security.verificationResult(), traceId, "40103", "CALLBACK_SIGNATURE_INVALID");
            return new Outcome(HttpStatus.UNAUTHORIZED, Map.of("code", "40103", "error", "CALLBACK_SIGNATURE_INVALID"));
        }
        rememberNonce(headers, requestDigest, "WECOM", "INBOUND", endpoint.endpointCode(), now());
        Instant now = now();
        String bindingId = "ih-bind-" + UUID.randomUUID();
        jdbcTemplate.update("""
                insert into ih_integration_binding
                (binding_id, system_name, binding_type, object_type, object_id, external_object_type, external_object_id,
                 external_parent_id, binding_status, binding_role, confirmed_ref_source, first_bound_at, last_verified_at,
                 last_inbound_message_id, last_dispatch_id, created_at, updated_at)
                values (?, 'WECOM', 'IDENTITY_CONFIRMED_REF', 'PROTOCOL_EXCHANGE', ?, 'WECOM_LOGIN_CODE', ?, null,
                        'BOUND', 'IDENTITY_HANDOFF', 'identity-access', ?, ?, null, null, ?, ?)
                """, bindingId, exchangeRef, text(request, "code", "unknown-code"), ts(now), ts(now), ts(now), ts(now));
        audit("INBOUND", "ProtocolExchange", exchangeRef, "WECOM_TICKET_HANDOFF", "SUCCESS", "WECOM", "IDENTITY", exchangeRef,
                "VERIFIED", traceId, null, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("protocol_exchange_ref", exchangeRef);
        body.put("external_ticket_result", Map.of("provider", "WECOM", "ticket_status", "STANDARDIZED", "ticket_subject", text(request, "code", "unknown-code")));
        body.put("handoff_target", "identity-access");
        body.put("identity_context", Map.of("provider", "WECOM", "protocol_exchange_ref", exchangeRef, "session_gate_owner", "identity-access"));
        return new Outcome(HttpStatus.ACCEPTED, body);
    }

    Map<String, Object> auditViews(String traceId) {
        List<Map<String, Object>> items = jdbcTemplate.query("""
                select audit_event_id, direction, system_name, object_type, object_id, trace_id, result_status, resource_type, resource_id, action_type, occurred_at
                from ih_integration_audit_event
                where ? is null or trace_id = ?
                order by occurred_at, audit_event_id
                """, (rs, rowNum) -> auditViewBody(rs), traceId, traceId);
        return Map.of("item_list", items, "total", items.size());
    }

    private void insertInbound(String inboundId, String sourceSystem, String messageType, String externalRequestId, String idempotencyKey,
                               String requestDigest, Map<String, Object> request, String ingestStatus, String processingStatus, String verificationResult,
                               String traceId, Instant now) {
        jdbcTemplate.update("""
                insert into ih_inbound_message
                (inbound_message_id, source_system, message_type, external_request_id, idempotency_key, object_type, object_hint,
                  ingest_status, processing_status, route_target, binding_status, mapping_version, model_version, security_profile_version,
                  certificate_version, verification_result, profile_version, evidence_group_id, raw_payload_ref, normalized_payload_json,
                  request_digest, trace_id, received_at, processed_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, inboundId, sourceSystem, messageType, externalRequestId, idempotencyKey, text(request, "object_type", "MASTER_DATA"),
                json(Map.of("external_request_id", externalRequestId)), ingestStatus, processingStatus, "CANONICAL_ENVELOPE", "UNBOUND",
                MAPPING_VERSION, MODEL_VERSION, SECURITY_PROFILE_VERSION, CERTIFICATE_VERSION, verificationResult, PROFILE_VERSION,
                "ev-" + inboundId, "raw://inbound/" + inboundId, json(normalizePayload(objectMap(request.get("payload")))), requestDigest, traceId, ts(now),
                "FAILED".equals(processingStatus) ? ts(now) : null, ts(now), ts(now));
    }

    private void insertJob(String jobType, String resourceType, String resourceId, String sourceSystem, String targetSystem, String errorCode, String errorMessage) {
        Instant now = now();
        jdbcTemplate.update("""
                insert into ih_integration_job
                (job_id, platform_job_id, job_type, job_status, resource_type, resource_id, job_round_no, source_system, target_system,
                 priority, attempt_no, max_attempts, runner_code, next_run_at, last_error_code, last_error_message, manual_action_required,
                 finished_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "ih-job-" + UUID.randomUUID(), null, jobType, errorCode == null ? "PENDING" : "FAILED_RETRYABLE", resourceType, resourceId,
                1, sourceSystem, targetSystem, 100, 1, 3, "integration-hub-runtime", ts(now), errorCode, errorMessage, false, null, ts(now), ts(now));
    }

    private void audit(String direction, String resourceType, String resourceId, String actionType, String resultStatus, String systemName,
                       String objectType, String objectId, String verificationResult, String traceId, String errorCode, String errorMessage) {
        jdbcTemplate.update("""
                insert into ih_integration_audit_event
                (audit_event_id, trace_id, direction, resource_type, resource_id, action_type, result_status, system_name, object_type,
                 object_id, operator_type, operator_id, mapping_version, model_version, security_profile_version, certificate_version,
                 verification_result, profile_version, evidence_group_id, error_code, error_message, payload_snapshot_ref, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "ih-audit-" + UUID.randomUUID(), traceId, direction, resourceType, resourceId, actionType, resultStatus, systemName,
                objectType, objectId, "SYSTEM", systemName, MAPPING_VERSION, MODEL_VERSION, SECURITY_PROFILE_VERSION, CERTIFICATE_VERSION,
                verificationResult, PROFILE_VERSION, "ev-" + resourceId, errorCode, errorMessage, "audit://" + resourceId + "/" + actionType, ts(now()));
    }

    private EndpointProfile endpoint(String systemName, String endpointType, String endpointCode) {
        Optional<EndpointProfile> existing = queryOptional("""
                select endpoint_profile_id, system_name, endpoint_type, endpoint_code, base_url, auth_mode, credential_ref,
                       timeout_ms, retry_policy_code, callback_enabled, rate_limit_bucket, profile_status, profile_version
                from ih_endpoint_profile
                where system_name = ? and endpoint_type = ? and endpoint_code = ?
                """, (rs, rowNum) -> endpointProfile(rs), systemName, endpointType, endpointCode);
        if (existing.isPresent()) {
            return existing.get();
        }
        EndpointProfile profile = defaultEndpoint(systemName, endpointType, endpointCode);
        Instant now = now();
        jdbcTemplate.update("""
                insert into ih_endpoint_profile
                (endpoint_profile_id, system_name, endpoint_type, endpoint_code, base_url, auth_mode, credential_ref, timeout_ms,
                 retry_policy_code, callback_enabled, rate_limit_bucket, profile_status, profile_version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, profile.endpointProfileId(), profile.systemName(), profile.endpointType(), profile.endpointCode(), profile.baseUrl(),
                profile.authMode(), profile.credentialRef(), profile.timeoutMs(), profile.retryPolicyCode(), profile.callbackEnabled(),
                profile.rateLimitBucket(), profile.profileStatus(), profile.profileVersion(), ts(now), ts(now));
        return profile;
    }

    private EndpointProfile defaultEndpoint(String systemName, String endpointType, String endpointCode) {
        return new EndpointProfile("ih-endpoint-" + UUID.randomUUID(), systemName, endpointType, endpointCode,
                "https://internal.example/" + systemName.toLowerCase(), "SIGNATURE", "cred-" + systemName.toLowerCase() + "-primary",
                3000, "retry-standard-v1", "CALLBACK".equals(endpointType), systemName + ":" + endpointType + ":DEFAULT", "ACTIVE", PROFILE_VERSION);
    }

    private Map<String, Object> inboundBody(String inboundId) {
        return queryOptional("""
                select inbound_message_id, source_system, message_type, external_request_id, ingest_status, processing_status,
                       verification_result, normalized_payload_json, trace_id
                from ih_inbound_message where inbound_message_id = ?
                """, (rs, rowNum) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("inbound_message_id", rs.getString("inbound_message_id"));
            body.put("source_system", rs.getString("source_system"));
            body.put("message_type", rs.getString("message_type"));
            body.put("external_request_id", rs.getString("external_request_id"));
            body.put("ingest_status", rs.getString("ingest_status"));
            body.put("processing_status", rs.getString("processing_status"));
            body.put("verification_result", rs.getString("verification_result"));
            body.put("normalized_payload", parseMap(rs.getString("normalized_payload_json")));
            body.put("trace_id", rs.getString("trace_id"));
            return body;
        }, inboundId).orElseThrow(() -> new IllegalArgumentException("inbound_message 不存在: " + inboundId));
    }

    private Map<String, Object> dispatchBody(String dispatchId) {
        return queryOptional("""
                select dispatch_id, target_system, dispatch_status, last_result_code, trace_id
                from ih_outbound_dispatch where dispatch_id = ?
                """, (rs, rowNum) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("dispatch_id", rs.getString("dispatch_id"));
            body.put("target_system", rs.getString("target_system"));
            body.put("dispatch_status", rs.getString("dispatch_status"));
            body.put("last_result_code", rs.getString("last_result_code"));
            body.put("trace_id", rs.getString("trace_id"));
            return body;
        }, dispatchId).orElseThrow(() -> new IllegalArgumentException("dispatch 不存在: " + dispatchId));
    }

    private Map<String, Object> callbackBody(String callbackId) {
        return queryOptional("""
                select callback_receipt_id, source_system, receipt_type, external_receipt_id, receipt_status, processing_status, trace_id
                from ih_callback_receipt where callback_receipt_id = ?
                """, (rs, rowNum) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("callback_receipt_id", rs.getString("callback_receipt_id"));
            body.put("source_system", rs.getString("source_system"));
            body.put("receipt_type", rs.getString("receipt_type"));
            body.put("external_receipt_id", rs.getString("external_receipt_id"));
            body.put("accepted", "ACCEPTED".equals(rs.getString("receipt_status")));
            body.put("receipt_status", rs.getString("receipt_status"));
            body.put("processing_status", rs.getString("processing_status"));
            body.put("trace_id", rs.getString("trace_id"));
            return body;
        }, callbackId).orElseThrow(() -> new IllegalArgumentException("callback 不存在: " + callbackId));
    }

    private Map<String, Object> auditViewBody(ResultSet rs) throws SQLException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("audit_view_id", rs.getString("audit_event_id"));
        body.put("direction", rs.getString("direction"));
        body.put("system_name", rs.getString("system_name"));
        body.put("object_type", rs.getString("object_type"));
        body.put("object_id", rs.getString("object_id"));
        body.put("trace_id", rs.getString("trace_id"));
        body.put("latest_result", rs.getString("result_status"));
        body.put("resource_refs", List.of(Map.of("resource_type", rs.getString("resource_type"), "resource_id", rs.getString("resource_id"))));
        body.put("summary", rs.getString("action_type"));
        body.put("occurred_at", rs.getTimestamp("occurred_at").toInstant().toString());
        return body;
    }

    private Optional<Map<String, Object>> findInbound(String sourceSystem, String idempotencyKey) {
        return queryOptional("select inbound_message_id, request_digest from ih_inbound_message where source_system = ? and idempotency_key = ?",
                (rs, rowNum) -> Map.of("inbound_message_id", rs.getString("inbound_message_id"), "request_digest", rs.getString("request_digest")), sourceSystem, idempotencyKey);
    }

    private Optional<Map<String, Object>> findOutbound(String targetSystem, String idempotencyKey) {
        return queryOptional("select dispatch_id, request_digest from ih_outbound_dispatch where target_system = ? and idempotency_key = ?",
                (rs, rowNum) -> Map.of("dispatch_id", rs.getString("dispatch_id"), "request_digest", rs.getString("request_digest")), targetSystem, idempotencyKey);
    }

    private Optional<Map<String, Object>> findCallback(String sourceSystem, String idempotencyKey) {
        return queryOptional("select callback_receipt_id, request_digest from ih_callback_receipt where source_system = ? and idempotency_key = ?",
                (rs, rowNum) -> Map.of("callback_receipt_id", rs.getString("callback_receipt_id"), "request_digest", rs.getString("request_digest")), sourceSystem, idempotencyKey);
    }

    private Map<String, Object> runtimeContext(String systemName, String direction, EndpointProfile endpoint) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("system_name", systemName);
        body.put("direction", direction);
        body.put("endpoint_code", endpoint.endpointCode());
        body.put("credential_ref", endpoint.credentialRef());
        body.put("profile_version", endpoint.profileVersion());
        body.put("security_verdict", "VERIFIED");
        body.put("rate_limit_bucket", endpoint.rateLimitBucket());
        return body;
    }

    private Map<String, Object> normalizePayload(Map<String, Object> payload) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            normalized.put(toSnakeCase(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    private SecurityCheck verify(SecurityHeaders headers, String requestDigest, String systemName, String direction, String endpointCode) {
        if (blank(headers.signature()) || blank(headers.timestamp()) || blank(headers.nonce())
                || blank(headers.securityProfileVersion()) || blank(headers.certificateVersion())) {
            return new SecurityCheck(false, "REJECTED_SIGNATURE");
        }
        if (!SECURITY_PROFILE_VERSION.equals(headers.securityProfileVersion()) || !CERTIFICATE_VERSION.equals(headers.certificateVersion())) {
            return new SecurityCheck(false, "REJECTED_CONTEXT_MISMATCH");
        }
        Instant issuedAt;
        try {
            issuedAt = Instant.parse(headers.timestamp());
        } catch (RuntimeException exception) {
            return new SecurityCheck(false, "REJECTED_SIGNATURE");
        }
        Instant current = now();
        if (issuedAt.isBefore(current.minus(SIGNATURE_REPLAY_WINDOW)) || issuedAt.isAfter(current.plus(SIGNATURE_REPLAY_WINDOW))) {
            return new SecurityCheck(false, "REJECTED_EXPIRED");
        }
        if (nonceUsed(systemName, direction, endpointCode, headers)) {
            return new SecurityCheck(false, "REJECTED_REPLAY");
        }
        String signingString = String.join("\n", direction, systemName, endpointCode, headers.timestamp(), headers.nonce(),
                headers.securityProfileVersion(), headers.certificateVersion(), requestDigest);
        String expected = "cmp-sha256:" + sha256(signingString);
        return new SecurityCheck(expected.equals(headers.signature()), expected.equals(headers.signature()) ? "VERIFIED" : "REJECTED_SIGNATURE");
    }

    private boolean nonceUsed(String systemName, String direction, String endpointCode, SecurityHeaders headers) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from ih_security_nonce
                where system_name = ? and direction = ? and endpoint_code = ? and nonce = ?
                  and security_profile_version = ? and certificate_version = ? and expires_at >= ?
                """, Integer.class, systemName, direction, endpointCode, headers.nonce(), headers.securityProfileVersion(), headers.certificateVersion(), ts(now()));
        return count != null && count > 0;
    }

    private void rememberNonce(SecurityHeaders headers, String requestDigest, String systemName, String direction, String endpointCode, Instant now) {
        Instant issuedAt = Instant.parse(headers.timestamp());
        try {
            jdbcTemplate.update("""
                    insert into ih_security_nonce
                    (nonce_id, system_name, direction, endpoint_code, nonce, request_digest, security_profile_version, certificate_version,
                     issued_at, expires_at, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, "ih-nonce-" + UUID.randomUUID(), systemName, direction, endpointCode, headers.nonce(), requestDigest,
                    headers.securityProfileVersion(), headers.certificateVersion(), ts(issuedAt), ts(issuedAt.plus(SIGNATURE_REPLAY_WINDOW)), ts(now));
        } catch (DataIntegrityViolationException exception) {
            throw exception;
        }
    }

    private Outcome idempotencyConflict(String direction, String resourceType, String resourceId, String systemName,
                                        String objectType, String objectId, String traceId) {
        audit(direction, resourceType, resourceId, "IDEMPOTENCY_CONFLICT", "DENIED", systemName, objectType, objectId,
                "VERIFIED", traceId, "40905", "IDEMPOTENCY_CONFLICT");
        return new Outcome(HttpStatus.CONFLICT, Map.of("code", "40905", "error", "IDEMPOTENCY_CONFLICT", "resource_id", resourceId));
    }

    private String requestDigest(Map<String, Object> request) {
        Map<String, Object> stableRequest = new LinkedHashMap<>(request);
        stableRequest.remove("trace_id");
        return sha256(json(stableRequest));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String system(String value) {
        return value == null ? "UNKNOWN" : value.trim().replace('-', '_').toUpperCase();
    }

    private String trace(Map<String, Object> request) {
        return text(request, "trace_id", "trace-" + UUID.randomUUID());
    }

    private String text(Map<String, Object> map, String fieldName, String defaultValue) {
        Object value = map.get(fieldName);
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }

    private int number(Map<String, Object> map, String fieldName, int defaultValue) {
        Object value = map.get(fieldName);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? defaultValue : Integer.parseInt(value.toString());
    }

    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            raw.forEach((key, item) -> mapped.put(key.toString(), item));
            return mapped;
        }
        return Map.of();
    }

    private String toSnakeCase(String value) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (Character.isUpperCase(ch) && index > 0) {
                builder.append('_');
            }
            builder.append(Character.toLowerCase(ch));
        }
        return builder.toString();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON 序列化失败", exception);
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON 解析失败", exception);
        }
    }

    private <T> Optional<T> queryOptional(String sql, org.springframework.jdbc.core.RowMapper<T> rowMapper, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, args));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private EndpointProfile endpointProfile(ResultSet rs) throws SQLException {
        return new EndpointProfile(
                rs.getString("endpoint_profile_id"),
                rs.getString("system_name"),
                rs.getString("endpoint_type"),
                rs.getString("endpoint_code"),
                rs.getString("base_url"),
                rs.getString("auth_mode"),
                rs.getString("credential_ref"),
                rs.getInt("timeout_ms"),
                rs.getString("retry_policy_code"),
                rs.getBoolean("callback_enabled"),
                rs.getString("rate_limit_bucket"),
                rs.getString("profile_status"),
                rs.getString("profile_version"));
    }

    private Instant now() {
        return Instant.now();
    }

    private Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    record Outcome(HttpStatus status, Map<String, Object> body) {
    }

    record SecurityHeaders(String signature, String timestamp, String nonce, String securityProfileVersion, String certificateVersion) {
    }

    record SecurityCheck(boolean verified, String verificationResult) {
    }

    record EndpointProfile(String endpointProfileId, String systemName, String endpointType, String endpointCode, String baseUrl,
                           String authMode, String credentialRef, int timeoutMs, String retryPolicyCode, boolean callbackEnabled,
                           String rateLimitBucket, String profileStatus, String profileVersion) {
    }
}

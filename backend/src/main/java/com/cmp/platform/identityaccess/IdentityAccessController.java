package com.cmp.platform.identityaccess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class IdentityAccessController {

    private final IdentityAccessService service;

    IdentityAccessController(IdentityAccessService service) {
        this.service = service;
    }

    @PostMapping("/api/users")
    ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createUser(request));
    }

    @PostMapping("/api/identity-bindings")
    ResponseEntity<Map<String, Object>> createBinding(@RequestBody Map<String, Object> request) {
        IdentityAccessService.BindingOutcome outcome = service.createBinding(request);
        return ResponseEntity.status(outcome.status()).body(outcome.body());
    }

    @PostMapping("/api/auth/password/sessions")
    Map<String, Object> passwordSession(@RequestBody Map<String, Object> request) {
        return service.passwordSession(request);
    }

    @PostMapping("/api/auth/sessions/exchanges")
    ResponseEntity<Map<String, Object>> exchangeSession(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> request) {
        IdentityAccessService.ExchangeOutcome outcome = service.exchangeSession(idempotencyKey, request);
        return ResponseEntity.status(outcome.status()).body(outcome.body());
    }

    @PostMapping("/api/identity-bindings/manual-dispositions")
    Map<String, Object> disposeManually(@RequestBody Map<String, Object> request) {
        return service.disposeManually(request);
    }

    @GetMapping("/api/auth/me")
    ResponseEntity<Map<String, Object>> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> body = service.me(authorization);
        if (body == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", "40101", "error", "AUTH_REQUIRED"));
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/api/identity-audit-views")
    Map<String, Object> auditViews(@RequestParam(value = "trace_id", required = false) String traceId) {
        return service.auditViews(traceId);
    }
}

@org.springframework.stereotype.Service
class IdentityAccessService {

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    IdentityAccessService(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    Map<String, Object> createUser(Map<String, Object> request) {
        User user = new User(
                text(request, "user_id", "u-" + UUID.randomUUID()),
                text(request, "login_name", null),
                text(request, "display_name", text(request, "login_name", "未命名用户")),
                "ACTIVE",
                "ORG-DEFAULT",
                "DEPT-DEFAULT");
        Instant now = now();
        jdbcTemplate.update("""
                insert into ia_user (user_id, login_name, display_name, user_status, default_org_id, default_org_unit_id, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, user.userId(), user.loginName(), user.displayName(), user.userStatus(), user.defaultOrgId(), user.defaultOrgUnitId(), ts(now));
        audit("USER_CREATED", "SUCCESS", user.userId(), user.userId(), null, null, trace(request));
        return userContext(user);
    }

    @Transactional
    BindingOutcome createBinding(Map<String, Object> request) {
        User user = requireUser(text(request, "user_id", null));
        String provider = provider(request);
        String externalIdentity = text(request, "external_identity", text(request, "external_identity_key", user.loginName()));
        Optional<IdentityBinding> existing = findBinding(provider, externalIdentity);
        if (existing.isPresent() && existing.get().userId().equals(user.userId())) {
            return new BindingOutcome(HttpStatus.OK, bindingBody(existing.get()));
        }
        if (existing.isPresent()) {
            String precheckId = recordPrecheck(null, provider, externalIdentity, "CONFLICT", "MANUAL_REQUIRED", List.of(existing.get().userId(), user.userId()));
            audit("BINDING_CONFLICT", "DENIED", user.userId(), existing.get().userId(), precheckId, null, trace(request));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("precheck_id", precheckId);
            body.put("binding_status", "CONFLICT");
            body.put("session_gate_result", "MANUAL_REQUIRED");
            body.put("existing_user_id", existing.get().userId());
            body.put("requested_user_id", user.userId());
            return new BindingOutcome(HttpStatus.CONFLICT, body);
        }

        IdentityBinding binding = new IdentityBinding("bind-" + UUID.randomUUID(), provider, externalIdentity, user.userId(), "ACTIVE", null);
        insertBinding(binding);
        audit("BINDING_LINKED", "SUCCESS", user.userId(), user.userId(), binding.bindingId(), null, trace(request));
        return new BindingOutcome(HttpStatus.CREATED, bindingBody(binding));
    }

    @Transactional
    Map<String, Object> passwordSession(Map<String, Object> request) {
        String loginName = text(request, "login_name", null);
        User user = findUserByLoginName(loginName).orElseThrow(() -> new IllegalArgumentException("login_name 不存在"));
        IdentityBinding binding = findBinding("LOCAL", loginName).orElseGet(() -> {
            IdentityBinding created = new IdentityBinding("bind-" + UUID.randomUUID(), "LOCAL", loginName, user.userId(), "ACTIVE", null);
            insertBinding(created);
            return created;
        });
        IdentitySession session = newSession(user, binding.bindingId(), "LOCAL");
        audit("LOGIN_SUCCEEDED", "SUCCESS", user.userId(), user.userId(), binding.bindingId(), null, trace(request));
        return sessionBody(session, user, binding, false, null);
    }

    @Transactional
    ExchangeOutcome exchangeSession(String idempotencyKey, Map<String, Object> request) {
        String provider = provider(request);
        String externalIdentity = text(request, "external_identity_key", tokenSubject(request));
        String payloadFingerprint = canonicalFingerprint(request);
        Optional<IdempotencyRecord> existingIdempotency = findIdempotency(idempotencyKey);
        if (existingIdempotency.isPresent() && !existingIdempotency.get().payloadFingerprint().equals(payloadFingerprint)) {
            return new ExchangeOutcome(HttpStatus.CONFLICT, Map.of("code", "40905", "error", "IDEMPOTENCY_CONFLICT"));
        }
        if (existingIdempotency.isPresent()) {
            ProtocolExchange exchange = requireExchange(existingIdempotency.get().exchangeId());
            Map<String, Object> body = new LinkedHashMap<>(exchange.lastResponse());
            body.put("duplicate", true);
            return new ExchangeOutcome(HttpStatus.valueOf(exchange.httpStatus()), body);
        }

        String exchangeId = "pex-" + UUID.randomUUID();
        String traceId = trace(request);
        audit("PROTOCOL_CALLBACK_RECEIVED", "SUCCESS", null, null, exchangeId, exchangeId, traceId);
        if (!trustedProtocolCredential(request)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("protocol_exchange_id", exchangeId);
            body.put("exchange_status", "FAILED");
            body.put("error", "UNTRUSTED_PROTOCOL_TICKET");
            insertExchange(new ProtocolExchange(exchangeId, provider, externalIdentity, "FAILED", "NOT_RETRYABLE", HttpStatus.UNAUTHORIZED.value(), body));
            rememberIdempotency(idempotencyKey, payloadFingerprint, exchangeId);
            audit("PROTOCOL_EXCHANGE_FAILED", "DENIED", null, null, exchangeId, exchangeId, traceId);
            return new ExchangeOutcome(HttpStatus.UNAUTHORIZED, body);
        }

        List<String> candidateUserIds = existingUserIds(request.get("candidate_user_ids"));
        Optional<IdentityBinding> existingBinding = findBinding(provider, externalIdentity);
        boolean conflict = candidateUserIds.size() > 1
                || existingBinding.filter(binding -> !"ACTIVE".equals(binding.bindingStatus())).isPresent()
                || existingBinding.filter(binding -> !candidateUserIds.isEmpty() && !candidateUserIds.contains(binding.userId())).isPresent();
        if (conflict) {
            String precheckId = recordPrecheck(exchangeId, provider, externalIdentity, "CONFLICT", "MANUAL_REQUIRED", candidateUserIds);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("protocol_exchange_id", exchangeId);
            body.put("precheck_id", precheckId);
            body.put("binding_status", "CONFLICT");
            body.put("exchange_status", "FROZEN");
            body.put("session_gate_result", "MANUAL_REQUIRED");
            body.put("candidate_user_list", candidateUserIds.stream().map(this::userSummary).toList());
            insertExchange(new ProtocolExchange(exchangeId, provider, externalIdentity, "FROZEN", "MANUAL_ONLY", HttpStatus.CONFLICT.value(), body));
            rememberIdempotency(idempotencyKey, payloadFingerprint, exchangeId);
            audit("BINDING_CONFLICT", "DENIED", null, null, precheckId, exchangeId, traceId);
            audit("PROTOCOL_FROZEN", "DENIED", null, null, precheckId, exchangeId, traceId);
            return new ExchangeOutcome(HttpStatus.CONFLICT, body);
        }

        IdentityBinding binding = existingBinding.orElseGet(() -> createMatchedBinding(provider, externalIdentity, candidateUserIds, request));
        User user = requireUser(binding.userId());
        IdentitySession session = newSession(user, binding.bindingId(), provider);
        Map<String, Object> body = sessionBody(session, user, binding, false, exchangeId);
        body.put("binding_status", binding.bindingStatus());
        body.put("protocol_exchange_id", exchangeId);
        body.put("duplicate", false);
        insertExchange(new ProtocolExchange(exchangeId, provider, externalIdentity, "SESSION_ALLOWED", "RETRYABLE", HttpStatus.OK.value(), body));
        rememberIdempotency(idempotencyKey, payloadFingerprint, exchangeId);
        audit("PROTOCOL_EXCHANGE_SUCCEEDED", "SUCCESS", user.userId(), user.userId(), binding.bindingId(), exchangeId, traceId);
        audit("LOGIN_SUCCEEDED", "SUCCESS", user.userId(), user.userId(), binding.bindingId(), exchangeId, traceId);
        return new ExchangeOutcome(HttpStatus.OK, body);
    }

    @Transactional
    Map<String, Object> disposeManually(Map<String, Object> request) {
        String exchangeId = text(request, "protocol_exchange_id", null);
        ProtocolExchange exchange = requireExchange(exchangeId);
        User target = requireUser(text(request, "target_user_id", null));
        IdentityBinding binding = new IdentityBinding("bind-" + UUID.randomUUID(), exchange.provider(), exchange.externalIdentity(), target.userId(), "ACTIVE", null);
        upsertBinding(binding);
        String dispositionId = "disp-" + UUID.randomUUID();
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("binding_id", binding.bindingId());
        after.put("user_id", target.userId());
        after.put("binding_status", "ACTIVE");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("disposition_id", dispositionId);
        body.put("protocol_exchange_id", exchangeId);
        body.put("binding_status", "ACTIVE");
        body.put("after_status_snapshot", after);
        body.put("retry_policy_status", "RETRYABLE");
        body.put("exchange_status", "PRECHECKED");
        updateExchange(exchangeId, "PRECHECKED", "RETRYABLE", HttpStatus.OK.value(), body);
        jdbcTemplate.update("""
                insert into ia_identity_manual_disposition
                (disposition_id, protocol_exchange_id, target_user_id, operator_id, disposition_action, disposition_reason, after_status_snapshot_json, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, dispositionId, exchangeId, target.userId(), text(request, "operator_id", "SYSTEM"), text(request, "disposition_action", "RELINK"),
                text(request, "disposition_reason", null), json(after), ts(now()));
        audit("PROTOCOL_MANUAL_REBOUND", "SUCCESS", text(request, "operator_id", "SYSTEM"), target.userId(), dispositionId, exchangeId, trace(request));
        return body;
    }

    Map<String, Object> me(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        Optional<IdentitySession> session = findSessionByToken(authorization.substring("Bearer ".length()));
        if (session.isEmpty() || !"ACTIVE".equals(session.get().sessionStatus())) {
            return null;
        }
        User user = requireUser(session.get().userId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", userContext(user));
        body.put("org_context", orgContext(user));
        body.put("role_list", List.of());
        body.put("permission_summary", Map.of("permission_list", List.of(), "data_scope_list", List.of()));
        return body;
    }

    Map<String, Object> auditViews(String traceId) {
        List<Map<String, Object>> items = jdbcTemplate.query("""
                select audit_view_id, event_type, result_status, actor_user_id, target_user_id, target_resource_id,
                       protocol_exchange_id, trace_id, occurred_at
                from ia_identity_audit
                where ? is null or trace_id = ?
                order by occurred_at, audit_view_id
                """, (rs, rowNum) -> auditEvent(rs).body(), traceId, traceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("item_list", items);
        body.put("total", items.size());
        return body;
    }

    private IdentityBinding createMatchedBinding(String provider, String externalIdentity, List<String> candidateUserIds, Map<String, Object> request) {
        String matchedUserId = candidateUserIds.size() == 1 ? candidateUserIds.getFirst() : null;
        User matched = matchedUserId == null ? null : requireUser(matchedUserId);
        if (matched == null) {
            matched = findUserByLoginName(text(request, "external_login_name", ""))
                    .orElseThrow(() -> new IllegalArgumentException("外部身份未绑定到平台主体"));
        }
        IdentityBinding binding = new IdentityBinding("bind-" + UUID.randomUUID(), provider, externalIdentity, matched.userId(), "ACTIVE", null);
        insertBinding(binding);
        return binding;
    }

    private IdentitySession newSession(User user, String bindingId, String provider) {
        IdentitySession session = new IdentitySession("ses-" + UUID.randomUUID(), "tok-" + UUID.randomUUID(), user.userId(), bindingId, provider, "ACTIVE", now(), now().plus(2, ChronoUnit.HOURS));
        jdbcTemplate.update("""
                insert into ia_identity_session (session_id, access_token, user_id, binding_id, provider, session_status, issued_at, expires_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, session.sessionId(), session.accessToken(), session.userId(), session.bindingId(), session.provider(), session.sessionStatus(), ts(session.issuedAt()), ts(session.expiresAt()));
        return session;
    }

    private void insertBinding(IdentityBinding binding) {
        Instant now = now();
        jdbcTemplate.update("""
                insert into ia_identity_binding (binding_id, provider, external_identity, user_id, binding_status, conflict_group_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, binding.bindingId(), binding.provider(), binding.externalIdentity(), binding.userId(), binding.bindingStatus(), binding.conflictGroupId(), ts(now), ts(now));
    }

    private void upsertBinding(IdentityBinding binding) {
        if (findBinding(binding.provider(), binding.externalIdentity()).isPresent()) {
            jdbcTemplate.update("""
                    update ia_identity_binding
                    set binding_id = ?, user_id = ?, binding_status = ?, conflict_group_id = ?, updated_at = ?
                    where provider = ? and external_identity = ?
                    """, binding.bindingId(), binding.userId(), binding.bindingStatus(), binding.conflictGroupId(), ts(now()), binding.provider(), binding.externalIdentity());
        } else {
            insertBinding(binding);
        }
    }

    private void insertExchange(ProtocolExchange exchange) {
        Instant now = now();
        jdbcTemplate.update("""
                insert into ia_protocol_exchange
                (exchange_id, provider, external_identity, exchange_status, retry_policy_status, http_status, last_response_json, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, exchange.exchangeId(), exchange.provider(), exchange.externalIdentity(), exchange.exchangeStatus(), exchange.retryPolicyStatus(), exchange.httpStatus(), json(exchange.lastResponse()), ts(now), ts(now));
    }

    private void updateExchange(String exchangeId, String exchangeStatus, String retryPolicyStatus, int httpStatus, Map<String, Object> body) {
        jdbcTemplate.update("""
                update ia_protocol_exchange
                set exchange_status = ?, retry_policy_status = ?, http_status = ?, last_response_json = ?, updated_at = ?
                where exchange_id = ?
                """, exchangeStatus, retryPolicyStatus, httpStatus, json(body), ts(now()), exchangeId);
    }

    private String recordPrecheck(String exchangeId, String provider, String externalIdentity, String status, String gateResult, List<String> candidateUserIds) {
        String precheckId = "pre-" + UUID.randomUUID();
        jdbcTemplate.update("""
                insert into ia_identity_binding_precheck
                (precheck_id, protocol_exchange_id, provider, external_identity, precheck_status, session_gate_result, candidate_user_ids, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, precheckId, exchangeId, provider, externalIdentity, status, gateResult, json(candidateUserIds), ts(now()));
        return precheckId;
    }

    private void audit(String eventType, String result, String actorId, String targetUserId, String resourceId, String exchangeId, String traceId) {
        jdbcTemplate.update("""
                insert into ia_identity_audit
                (audit_view_id, event_type, result_status, actor_user_id, target_user_id, target_resource_id, protocol_exchange_id, trace_id, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "aud-" + UUID.randomUUID(), eventType, result, actorId, targetUserId, resourceId, exchangeId, traceId, ts(now()));
    }

    private void rememberIdempotency(String idempotencyKey, String payloadFingerprint, String exchangeId) {
        if (idempotencyKey != null) {
            jdbcTemplate.update("""
                    insert into ia_idempotency_record (idempotency_key, payload_fingerprint, exchange_id, created_at)
                    values (?, ?, ?, ?)
                    """, idempotencyKey, payloadFingerprint, exchangeId, ts(now()));
        }
    }

    private Optional<User> findUserByLoginName(String loginName) {
        return queryOptional("select * from ia_user where login_name = ?", (rs, rowNum) -> user(rs), loginName);
    }

    private User requireUser(String userId) {
        return queryOptional("select * from ia_user where user_id = ?", (rs, rowNum) -> user(rs), userId)
                .orElseThrow(() -> new IllegalArgumentException("user_id 不存在: " + userId));
    }

    private Optional<IdentityBinding> findBinding(String provider, String externalIdentity) {
        return queryOptional("select * from ia_identity_binding where provider = ? and external_identity = ?", (rs, rowNum) -> binding(rs), provider, externalIdentity);
    }

    private Optional<IdentitySession> findSessionByToken(String token) {
        return queryOptional("select * from ia_identity_session where access_token = ?", (rs, rowNum) -> session(rs), token);
    }

    private Optional<IdempotencyRecord> findIdempotency(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return queryOptional("select payload_fingerprint, exchange_id from ia_idempotency_record where idempotency_key = ?",
                (rs, rowNum) -> new IdempotencyRecord(rs.getString("payload_fingerprint"), rs.getString("exchange_id")), idempotencyKey);
    }

    private ProtocolExchange requireExchange(String exchangeId) {
        return queryOptional("select * from ia_protocol_exchange where exchange_id = ?", (rs, rowNum) -> exchange(rs), exchangeId)
                .orElseThrow(() -> new IllegalArgumentException("protocol_exchange_id 不存在"));
    }

    private <T> Optional<T> queryOptional(String sql, org.springframework.jdbc.core.RowMapper<T> mapper, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, mapper, args));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> existingUserIds(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(userId -> queryOptional("select user_id from ia_user where user_id = ?", (rs, rowNum) -> rs.getString("user_id"), userId).isPresent())
                .toList();
    }

    private Map<String, Object> sessionBody(IdentitySession session, User user, IdentityBinding binding, boolean duplicate, String exchangeId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session_id", session.sessionId());
        body.put("access_token", session.accessToken());
        body.put("expires_at", session.expiresAt().toString());
        body.put("binding_id", binding.bindingId());
        body.put("binding_status", binding.bindingStatus());
        body.put("provider", binding.provider());
        body.put("user_context", userContext(user));
        body.put("org_context", orgContext(user));
        body.put("role_list", List.of());
        body.put("permission_summary", Map.of("permission_list", List.of(), "data_scope_list", List.of()));
        body.put("duplicate", duplicate);
        if (exchangeId != null) {
            body.put("protocol_exchange_id", exchangeId);
        }
        return body;
    }

    private Map<String, Object> userContext(User user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user_id", user.userId());
        body.put("login_name", user.loginName());
        body.put("display_name", user.displayName());
        body.put("user_status", user.userStatus());
        body.put("default_org_id", user.defaultOrgId());
        return body;
    }

    private Map<String, Object> userSummary(String userId) {
        User user = requireUser(userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user_id", user.userId());
        body.put("display_name", user.displayName());
        return body;
    }

    private Map<String, Object> orgContext(User user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("active_org_id", user.defaultOrgId());
        body.put("active_org_unit_id", user.defaultOrgUnitId());
        body.put("membership_list", List.of(Map.of("org_id", user.defaultOrgId(), "org_unit_id", user.defaultOrgUnitId(), "membership_type", "PRIMARY")));
        return body;
    }

    private Map<String, Object> bindingBody(IdentityBinding binding) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("binding_id", binding.bindingId());
        body.put("provider", binding.provider());
        body.put("external_identity", binding.externalIdentity());
        body.put("user_id", binding.userId());
        body.put("binding_status", binding.bindingStatus());
        return body;
    }

    private String canonicalFingerprint(Map<String, Object> request) {
        try {
            return objectMapper.writeValueAsString(new java.util.TreeMap<>(request));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("请求体无法规范化", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("数据无法序列化", exception);
        }
    }

    private Map<String, Object> mapFromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (IOException exception) {
            throw new IllegalArgumentException("数据无法反序列化", exception);
        }
    }

    private boolean trustedProtocolCredential(Map<String, Object> request) {
        String credential = text(request, "ticket", text(request, "code", ""));
        return credential.startsWith("trusted:");
    }

    private String tokenSubject(Map<String, Object> request) {
        String ticket = text(request, "ticket", text(request, "code", ""));
        return ticket.startsWith("trusted:") ? ticket.substring("trusted:".length()) : ticket;
    }

    private String provider(Map<String, Object> request) {
        return text(request, "provider", "LOCAL").toUpperCase(java.util.Locale.ROOT);
    }

    private String trace(Map<String, Object> request) {
        return text(request, "trace_id", "trace-" + UUID.randomUUID());
    }

    private String text(Map<String, Object> request, String key, String defaultValue) {
        Object value = request.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private User user(ResultSet rs) throws SQLException {
        return new User(rs.getString("user_id"), rs.getString("login_name"), rs.getString("display_name"), rs.getString("user_status"), rs.getString("default_org_id"), rs.getString("default_org_unit_id"));
    }

    private IdentityBinding binding(ResultSet rs) throws SQLException {
        return new IdentityBinding(rs.getString("binding_id"), rs.getString("provider"), rs.getString("external_identity"), rs.getString("user_id"), rs.getString("binding_status"), rs.getString("conflict_group_id"));
    }

    private IdentitySession session(ResultSet rs) throws SQLException {
        return new IdentitySession(rs.getString("session_id"), rs.getString("access_token"), rs.getString("user_id"), rs.getString("binding_id"), rs.getString("provider"), rs.getString("session_status"), rs.getTimestamp("issued_at").toInstant(), rs.getTimestamp("expires_at").toInstant());
    }

    private ProtocolExchange exchange(ResultSet rs) throws SQLException {
        return new ProtocolExchange(rs.getString("exchange_id"), rs.getString("provider"), rs.getString("external_identity"), rs.getString("exchange_status"), rs.getString("retry_policy_status"), rs.getInt("http_status"), mapFromJson(rs.getString("last_response_json")));
    }

    private AuditEvent auditEvent(ResultSet rs) throws SQLException {
        return new AuditEvent(rs.getString("audit_view_id"), rs.getString("event_type"), rs.getString("result_status"), rs.getString("actor_user_id"), rs.getString("target_user_id"), rs.getString("target_resource_id"), rs.getString("protocol_exchange_id"), rs.getString("trace_id"), rs.getTimestamp("occurred_at").toInstant());
    }

    record BindingOutcome(HttpStatus status, Map<String, Object> body) {}

    record ExchangeOutcome(HttpStatus status, Map<String, Object> body) {}

    private record User(String userId, String loginName, String displayName, String userStatus, String defaultOrgId, String defaultOrgUnitId) {}

    private record IdentityBinding(String bindingId, String provider, String externalIdentity, String userId, String bindingStatus, String conflictGroupId) {}

    private record IdentitySession(String sessionId, String accessToken, String userId, String bindingId, String provider, String sessionStatus, Instant issuedAt, Instant expiresAt) {}

    private record ProtocolExchange(String exchangeId, String provider, String externalIdentity, String exchangeStatus, String retryPolicyStatus, int httpStatus, Map<String, Object> lastResponse) {}

    private record IdempotencyRecord(String payloadFingerprint, String exchangeId) {}

    private record AuditEvent(String auditViewId, String eventType, String resultStatus, String actorUserId, String targetUserId, String resourceId, String protocolExchangeId, String traceId, Instant occurredAt) {
        private Map<String, Object> body() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("audit_view_id", auditViewId);
            body.put("event_type", eventType);
            body.put("result_status", resultStatus);
            body.put("actor_user_id", actorUserId);
            body.put("target_user_id", targetUserId);
            body.put("target_resource_id", resourceId);
            body.put("protocol_exchange_id", protocolExchangeId);
            body.put("trace_id", traceId);
            body.put("occurred_at", occurredAt.toString());
            return body;
        }
    }
}

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
import org.springframework.web.bind.annotation.PathVariable;
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

    @PostMapping("/api/org-units")
    ResponseEntity<Map<String, Object>> createOrgUnit(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createOrgUnit(request));
    }

    @GetMapping("/api/org-units/tree")
    Map<String, Object> orgTree(@RequestParam("org_id") String orgId) {
        return service.orgTree(orgId);
    }

    @PostMapping("/api/org-memberships")
    ResponseEntity<Map<String, Object>> createMembership(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createMembership(request));
    }

    @GetMapping("/api/org-memberships")
    Map<String, Object> memberships(@RequestParam("user_id") String userId) {
        return service.memberships(userId);
    }

    @PostMapping("/api/roles")
    ResponseEntity<Map<String, Object>> createRole(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRole(request));
    }

    @PostMapping("/api/role-assignments")
    ResponseEntity<Map<String, Object>> assignRole(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.assignRole(request));
    }

    @PostMapping("/api/permission-grants")
    ResponseEntity<Map<String, Object>> grantPermission(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.grantPermission(request));
    }

    @GetMapping("/api/menus/visible")
    Map<String, Object> visibleMenus(@RequestParam("user_id") String userId, @RequestParam("org_id") String orgId) {
        return service.visibleMenus(userId, orgId);
    }

    @GetMapping("/api/permissions/function-check")
    Map<String, Object> functionCheck(
            @RequestParam("user_id") String userId,
            @RequestParam("permission_code") String permissionCode,
            @RequestParam(value = "trace_id", required = false) String traceId) {
        return service.functionCheck(userId, permissionCode, traceId);
    }

    @PostMapping("/api/data-scopes")
    ResponseEntity<Map<String, Object>> createDataScope(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createDataScope(request));
    }

    @PostMapping("/api/data-scope-predicates")
    Map<String, Object> dataScopePredicate(@RequestBody Map<String, Object> request) {
        return service.dataScopePredicate(request);
    }

    @PostMapping("/api/org-rules")
    ResponseEntity<Map<String, Object>> createOrgRule(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createOrgRule(request));
    }

    @PostMapping("/api/org-rule-versions")
    ResponseEntity<Map<String, Object>> freezeOrgRuleVersion(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.freezeOrgRuleVersion(request));
    }

    @PostMapping("/api/org-rule-resolutions")
    Map<String, Object> resolveOrgRule(@RequestBody Map<String, Object> request) {
        return service.resolveOrgRule(request);
    }

    @PostMapping("/api/authorization/decisions")
    Map<String, Object> authorizationDecision(@RequestBody Map<String, Object> request) {
        return service.authorizationDecision(request);
    }

    @GetMapping("/api/authorization/decisions/{decisionId}")
    Map<String, Object> authorizationDecisionDetail(@PathVariable String decisionId) {
        return service.authorizationDecisionDetail(decisionId);
    }

    @PostMapping("/api/authorization/decrypt-download-grants")
    ResponseEntity<Map<String, Object>> createDecryptDownloadGrant(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createDecryptDownloadGrant(request));
    }

    @PostMapping("/api/authorization/decrypt-download-grants/{permissionGrantId}/revocations")
    Map<String, Object> revokeDecryptDownloadGrant(@PathVariable String permissionGrantId, @RequestBody Map<String, Object> request) {
        return service.changeDecryptDownloadGrantStatus(permissionGrantId, "REVOKED", "PERMISSION_REVOKED", request);
    }

    @PostMapping("/api/authorization/decrypt-download-grants/{permissionGrantId}/recoveries")
    Map<String, Object> recoverDecryptDownloadGrant(@PathVariable String permissionGrantId, @RequestBody Map<String, Object> request) {
        return service.changeDecryptDownloadGrantStatus(permissionGrantId, "ACTIVE", "PERMISSION_RECOVERED", request);
    }

    @PostMapping("/api/authorization/decrypt-download-hits")
    Map<String, Object> decryptDownloadHit(@RequestBody Map<String, Object> request) {
        return service.decryptDownloadHit(request);
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

    @Transactional
    Map<String, Object> createOrgUnit(Map<String, Object> request) {
        String orgUnitId = text(request, "org_unit_id", "ou-" + UUID.randomUUID());
        String parentId = text(request, "parent_org_unit_id", null);
        String orgId = parentId == null ? orgUnitId : orgUnit(parentId).get("org_id").toString();
        String parentPath = parentId == null ? "/" : orgUnit(parentId).get("org_path").toString();
        String orgPath = parentPath + orgUnitId + "/";
        int pathDepth = (int) orgPath.chars().filter(ch -> ch == '/').count() - 1;
        Instant now = now();
        jdbcTemplate.update("""
                insert into ia_org_unit
                (org_unit_id, org_id, parent_org_unit_id, org_unit_code, org_unit_name, org_unit_type, org_status, org_path, path_depth, manager_user_id, sort_order, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, orgUnitId, orgId, parentId, text(request, "org_unit_code", orgUnitId), text(request, "org_unit_name", orgUnitId),
                text(request, "org_unit_type", "DEPARTMENT"), "ACTIVE", orgPath, pathDepth, text(request, "manager_user_id", null), number(request, "sort_order", 0), ts(now), ts(now));
        audit("ORG_CHANGED", "SUCCESS", text(request, "operator_id", "SYSTEM"), null, orgUnitId, null, trace(request));
        return orgUnit(orgUnitId);
    }

    Map<String, Object> orgTree(String orgId) {
        List<Map<String, Object>> items = jdbcTemplate.query("""
                select org_unit_id, org_id, parent_org_unit_id, org_unit_code, org_unit_name, org_unit_type, org_status, org_path, path_depth, manager_user_id
                from ia_org_unit
                where org_id = ?
                order by org_path
                """, (rs, rowNum) -> orgUnitBody(rs), orgId);
        return Map.of("item_list", items, "total", items.size());
    }

    @Transactional
    Map<String, Object> createMembership(Map<String, Object> request) {
        String orgUnitId = text(request, "org_unit_id", null);
        Map<String, Object> unit = orgUnit(orgUnitId);
        String membershipId = text(request, "membership_id", "mem-" + UUID.randomUUID());
        boolean primary = bool(request, "is_primary_department", false);
        Instant now = now();
        jdbcTemplate.update("""
                insert into ia_org_membership
                (membership_id, user_id, org_id, org_unit_id, membership_type, membership_status, is_primary_department, position_title, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, membershipId, text(request, "user_id", null), unit.get("org_id"), orgUnitId, text(request, "membership_type", "PRIMARY"),
                "ACTIVE", primary, text(request, "position_title", null), ts(now), ts(now));
        audit("ORG_CHANGED", "SUCCESS", text(request, "operator_id", "SYSTEM"), text(request, "user_id", null), membershipId, null, trace(request));
        return membership(membershipId);
    }

    Map<String, Object> memberships(String userId) {
        List<Map<String, Object>> items = jdbcTemplate.query("""
                select m.membership_id, m.user_id, m.org_id, m.org_unit_id, m.membership_type, m.is_primary_department,
                       u.org_unit_name, u.org_path
                from ia_org_membership m
                join ia_org_unit u on u.org_unit_id = m.org_unit_id
                where m.user_id = ? and m.membership_status = 'ACTIVE'
                order by m.is_primary_department desc, m.membership_id
                """, (rs, rowNum) -> membershipBody(rs), userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("membership_list", items);
        body.put("primary_department", items.stream().filter(item -> Boolean.TRUE.equals(item.get("is_primary_department"))).findFirst().orElse(null));
        body.put("part_time_departments", items.stream().filter(item -> "PART_TIME".equals(item.get("membership_type"))).toList());
        return body;
    }

    @Transactional
    Map<String, Object> createRole(Map<String, Object> request) {
        String roleId = text(request, "role_id", "role-" + UUID.randomUUID());
        Instant now = now();
        jdbcTemplate.update("""
                insert into ia_role (role_id, role_code, role_name, role_scope, role_type, role_status, inherits_role_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, roleId, text(request, "role_code", roleId), text(request, "role_name", roleId), text(request, "role_scope", "PLATFORM"),
                text(request, "role_type", "BUSINESS"), "ACTIVE", text(request, "inherits_role_id", null), ts(now), ts(now));
        return role(roleId);
    }

    @Transactional
    Map<String, Object> assignRole(Map<String, Object> request) {
        String assignmentId = text(request, "assignment_id", "ra-" + UUID.randomUUID());
        Instant now = now();
        jdbcTemplate.update("""
                insert into ia_role_assignment
                (assignment_id, role_id, subject_type, subject_id, grant_org_id, assignment_status, granted_reason, granted_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, assignmentId, text(request, "role_id", null), text(request, "subject_type", "USER"), text(request, "subject_id", null),
                text(request, "grant_org_id", "ORG-DEFAULT"), "ACTIVE", text(request, "granted_reason", null), text(request, "granted_by", "SYSTEM"), ts(now), ts(now));
        audit("ROLE_GRANTED", "SUCCESS", text(request, "granted_by", "SYSTEM"), text(request, "subject_id", null), assignmentId, null, trace(request));
        return roleAssignment(assignmentId);
    }

    @Transactional
    Map<String, Object> grantPermission(Map<String, Object> request) {
        String grantId = text(request, "permission_grant_id", "pg-" + UUID.randomUUID());
        Instant now = now();
        jdbcTemplate.update("""
                insert into ia_permission_grant
                (permission_grant_id, grant_target_type, grant_target_id, permission_type, permission_code, resource_type, resource_scope_ref, grant_status, priority_no, effect_mode, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, grantId, text(request, "grant_target_type", "ROLE"), text(request, "grant_target_id", null), text(request, "permission_type", "FUNCTION"),
                text(request, "permission_code", null), text(request, "resource_type", null), text(request, "resource_scope_ref", null), "ACTIVE",
                number(request, "priority_no", 100), text(request, "effect_mode", "ALLOW"), ts(now), ts(now));
        audit("PERMISSION_GRANTED", "SUCCESS", text(request, "granted_by", "SYSTEM"), null, grantId, null, trace(request));
        return permissionGrant(grantId);
    }

    Map<String, Object> visibleMenus(String userId, String orgId) {
        List<String> roleIds = effectiveRoleIds(userId, orgId);
        List<Map<String, Object>> grants = permissionGrants(userId, roleIds, "MENU", null);
        List<String> menus = grants.stream()
                .filter(grant -> "ALLOW".equals(grant.get("effect_mode")))
                .map(grant -> grant.get("permission_code").toString())
                .distinct()
                .toList();
        return Map.of("user_id", userId, "menu_codes", menus);
    }

    @Transactional
    Map<String, Object> functionCheck(String userId, String permissionCode, String traceId) {
        List<String> roleIds = effectiveRoleIds(userId, null);
        List<Map<String, Object>> grants = permissionGrants(userId, roleIds, "FUNCTION", permissionCode);
        boolean denied = grants.stream().anyMatch(grant -> "DENY".equals(grant.get("effect_mode")));
        boolean allowed = !denied && grants.stream().anyMatch(grant -> "ALLOW".equals(grant.get("effect_mode")));
        String effect = denied ? "DENY" : allowed ? "ALLOW" : "NONE";
        audit(allowed ? "PERMISSION_GRANTED" : "AUTHZ_DENIED", allowed ? "SUCCESS" : "DENIED", userId, userId, permissionCode, null, traceId == null ? "trace-" + UUID.randomUUID() : traceId);
        return Map.of("user_id", userId, "permission_code", permissionCode, "allowed", allowed, "effect_mode", effect);
    }

    @Transactional
    Map<String, Object> createDataScope(Map<String, Object> request) {
        String dataScopeId = text(request, "data_scope_id", "ds-" + UUID.randomUUID());
        Instant now = now();
        jdbcTemplate.update("""
                insert into ia_data_scope
                (data_scope_id, subject_type, subject_id, resource_type, scope_type, scope_ref, scope_status, priority_no, effect_mode, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, dataScopeId, text(request, "subject_type", "USER"), text(request, "subject_id", null), text(request, "resource_type", "CONTRACT"),
                text(request, "scope_type", "SELF"), text(request, "scope_ref", null), "ACTIVE", number(request, "priority_no", 100), text(request, "effect_mode", "ALLOW"), ts(now), ts(now));
        return dataScope(dataScopeId);
    }

    @Transactional
    Map<String, Object> createDecryptDownloadGrant(Map<String, Object> request) {
        Map<String, Object> grantRequest = new LinkedHashMap<>(request);
        grantRequest.put("permission_type", "SPECIAL");
        grantRequest.put("permission_code", "DECRYPT_DOWNLOAD");
        Map<String, Object> grant = grantPermission(grantRequest);
        if ("DENY".equals(grant.get("effect_mode"))) {
            audit("PERMISSION_EXPLICIT_DENY", "SUCCESS", text(request, "granted_by", "SYSTEM"), null, grant.get("permission_grant_id").toString(), null, trace(request));
        }
        grant.put("cache_invalidated", true);
        return grant;
    }

    @Transactional
    Map<String, Object> changeDecryptDownloadGrantStatus(String permissionGrantId, String status, String eventType, Map<String, Object> request) {
        jdbcTemplate.update("""
                update ia_permission_grant
                set grant_status = ?, updated_at = ?
                where permission_grant_id = ? and permission_type = 'SPECIAL' and permission_code = 'DECRYPT_DOWNLOAD'
                """, status, ts(now()), permissionGrantId);
        Map<String, Object> grant = permissionGrant(permissionGrantId);
        audit(eventType, "SUCCESS", text(request, "operator_id", "SYSTEM"), null, permissionGrantId, null, trace(request));
        grant.put("cache_invalidated", true);
        return grant;
    }

    @Transactional
    Map<String, Object> decryptDownloadHit(Map<String, Object> request) {
        String userId = text(request, "user_id", null);
        String activeOrgId = text(request, "active_org_id", "ORG-DEFAULT");
        String activeOrgUnitId = text(request, "active_org_unit_id", null);
        String resourceType = request.containsKey("contract_id") ? "CONTRACT" : "DOCUMENT";
        String resourceId = text(request, resourceType.equals("CONTRACT") ? "contract_id" : "document_id", null);
        String traceId = trace(request);
        AuthorizationOutcome outcome = evaluateAuthorization(userId, activeOrgId, activeOrgUnitId, "SPECIAL:DECRYPT_DOWNLOAD", resourceType, resourceId, traceId);
        Map<String, Object> decisionRef = new LinkedHashMap<>();
        decisionRef.put("decision_id", outcome.decisionId());
        decisionRef.put("subject_user_id", userId);
        decisionRef.put("action_code", "SPECIAL:DECRYPT_DOWNLOAD");
        decisionRef.put("resource_type", resourceType);
        decisionRef.put("resource_id", resourceId);
        decisionRef.put("decision_result", outcome.decisionResult());
        decisionRef.put("expires_at", outcome.expiresAt().toString());
        decisionRef.put("request_trace_id", traceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hit", "ALLOW".equals(outcome.decisionResult()));
        body.put("decision_ref", decisionRef);
        body.put("reason_list", List.of(outcome.reasonCode()));
        body.put("matched_grant_list", "EXPLICIT_DENY".equals(outcome.reasonCode()) ? outcome.denyGrants() : outcome.allowGrants());
        body.put("data_scope_hit", outcome.matchingDataScopes());
        body.put("org_rule_evidence_list", outcome.orgRuleEvidenceList());
        return body;
    }

    @Transactional
    Map<String, Object> authorizationDecision(Map<String, Object> request) {
        Map<String, Object> subjectRef = objectMap(request.get("subject_ref"));
        Map<String, Object> resourceRef = objectMap(request.get("resource_ref"));
        String userId = text(subjectRef, "user_id", text(request, "user_id", null));
        String activeOrgId = text(subjectRef, "org_id", text(request, "active_org_id", "ORG-DEFAULT"));
        String activeOrgUnitId = text(subjectRef, "org_unit_id", text(request, "active_org_unit_id", null));
        String actionCode = text(request, "action_code", null);
        String resourceType = text(request, "resource_type", null);
        String resourceId = text(resourceRef, "resource_id", text(request, "resource_id", null));
        AuthorizationOutcome outcome = evaluateAuthorization(userId, activeOrgId, activeOrgUnitId, actionCode, resourceType, resourceId, trace(request));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision_id", outcome.decisionId());
        body.put("decision_result", outcome.decisionResult());
        body.put("reason_list", List.of(outcome.reasonCode()));
        body.put("subject_ref", Map.of("user_id", userId, "org_id", activeOrgId, "org_unit_id", activeOrgUnitId));
        body.put("action_code", actionCode);
        body.put("resource_type", resourceType);
        body.put("resource_ref", Map.of("resource_id", resourceId));
        body.put("matched_permission_list", "EXPLICIT_DENY".equals(outcome.reasonCode()) ? outcome.denyGrants() : outcome.allowGrants());
        body.put("data_scope_hit", outcome.matchingDataScopes());
        body.put("org_rule_evidence_list", outcome.orgRuleEvidenceList());
        return body;
    }

    Map<String, Object> authorizationDecisionDetail(String decisionId) {
        Map<String, Object> decision = queryOptional("""
                select decision_id, subject_user_id, subject_org_id, subject_org_unit_id, action_code, resource_type, resource_id, decision_result, decision_reason_code, request_trace_id, evaluated_at
                from ia_authorization_decision where decision_id = ?
                """, (rs, rowNum) -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("decision_id", rs.getString("decision_id"));
                    body.put("subject_ref", Map.of("user_id", rs.getString("subject_user_id"), "org_id", rs.getString("subject_org_id"), "org_unit_id", rs.getString("subject_org_unit_id")));
                    body.put("action_code", rs.getString("action_code"));
                    body.put("resource_type", rs.getString("resource_type"));
                    body.put("resource_ref", Map.of("resource_id", rs.getString("resource_id")));
                    body.put("decision_result", rs.getString("decision_result"));
                    body.put("reason_list", List.of(rs.getString("decision_reason_code")));
                    body.put("request_trace_id", rs.getString("request_trace_id"));
                    body.put("evaluated_at", rs.getTimestamp("evaluated_at").toInstant().toString());
                    return body;
                }, decisionId).orElseThrow(() -> new IllegalArgumentException("decision_id 不存在: " + decisionId));
        List<Map<String, Object>> hits = jdbcTemplate.query("""
                select hit_type, hit_ref_id, frozen_ref_id, resolution_record_id, hit_result, hit_priority_no, evidence_snapshot
                from ia_authorization_hit_result
                where decision_id = ?
                order by hit_priority_no, hit_type, hit_ref_id
                """, (rs, rowNum) -> {
                    Map<String, Object> hit = new LinkedHashMap<>();
                    hit.put("hit_type", rs.getString("hit_type"));
                    hit.put("hit_ref_id", rs.getString("hit_ref_id"));
                    hit.put("frozen_ref_id", rs.getString("frozen_ref_id"));
                    hit.put("resolution_record_id", rs.getString("resolution_record_id"));
                    hit.put("hit_result", rs.getString("hit_result"));
                    hit.put("hit_priority_no", rs.getInt("hit_priority_no"));
                    hit.put("evidence_snapshot", rs.getString("evidence_snapshot"));
                    return hit;
                }, decisionId);
        decision.put("authorization_hit_list", hits);
        return Map.of("decision_detail", decision);
    }

    @Transactional
    Map<String, Object> dataScopePredicate(Map<String, Object> request) {
        String userId = text(request, "user_id", null);
        String activeOrgId = text(request, "active_org_id", "ORG-DEFAULT");
        String activeOrgUnitId = text(request, "active_org_unit_id", null);
        String resourceType = text(request, "resource_type", "CONTRACT");
        String traceId = trace(request);
        List<Map<String, Object>> scopes = effectiveDataScopes(userId, activeOrgId, resourceType);
        String decisionId = "dec-" + UUID.randomUUID();
        List<Map<String, Object>> allowPredicates = new java.util.ArrayList<>();
        List<Map<String, Object>> denyPredicates = new java.util.ArrayList<>();
        List<Map<String, Object>> hitRows = new java.util.ArrayList<>();
        for (Map<String, Object> scope : scopes) {
            Map<String, Object> predicate = predicateForScope(scope, userId, activeOrgId, activeOrgUnitId, traceId);
            if ("DENY".equals(scope.get("effect_mode"))) {
                denyPredicates.add(predicate);
            } else {
                allowPredicates.add(predicate);
            }
            Map<String, Object> dataScopeHit = new LinkedHashMap<>();
            dataScopeHit.put("hit_type", "DATA_SCOPE");
            dataScopeHit.put("hit_ref_id", scope.get("data_scope_id"));
            dataScopeHit.put("frozen_ref_id", null);
            dataScopeHit.put("resolution_record_id", null);
            dataScopeHit.put("hit_result", scope.get("effect_mode"));
            dataScopeHit.put("hit_priority_no", scope.get("priority_no"));
            dataScopeHit.put("evidence_snapshot", scope);
            hitRows.add(dataScopeHit);
            if ("RULE".equals(scope.get("scope_type"))) {
                hitRows.add(Map.of(
                        "hit_type", "ORG_RULE",
                        "hit_ref_id", scope.get("scope_ref"),
                        "frozen_ref_id", predicate.get("org_rule_version_id"),
                        "resolution_record_id", predicate.get("org_rule_resolution_record_id"),
                        "hit_result", scope.get("effect_mode"),
                        "hit_priority_no", scope.get("priority_no"),
                        "evidence_snapshot", predicate));
            }
        }
        String decisionResult = allowPredicates.isEmpty() ? "DENY" : denyPredicates.isEmpty() ? "ALLOW" : "CONDITIONAL";
        String reasonCode = scopes.isEmpty() ? "NO_DATA_SCOPE" : allowPredicates.isEmpty() ? "EXPLICIT_DENY_NO_ALLOW" : denyPredicates.isEmpty() ? "DATA_SCOPE_MATCHED" : "DATA_SCOPE_WITH_DENY";
        String snapshotChecksum = "scope-" + scopes.size() + "-allow-" + allowPredicates.size() + "-deny-" + denyPredicates.size();
        jdbcTemplate.update("""
                insert into ia_authorization_decision
                (decision_id, subject_user_id, subject_org_id, subject_org_unit_id, action_code, resource_type, decision_result, decision_reason_code, data_scope_snapshot_checksum, request_trace_id, evaluated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, decisionId, userId, activeOrgId, activeOrgUnitId, text(request, "action_code", "QUERY"), resourceType,
                decisionResult, reasonCode, snapshotChecksum, traceId, ts(now()));
        for (Map<String, Object> hitRow : hitRows) {
            jdbcTemplate.update("""
                    insert into ia_authorization_hit_result
                    (hit_result_id, decision_id, hit_type, hit_ref_id, frozen_ref_id, resolution_record_id, hit_result, hit_priority_no, evidence_snapshot)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, "hit-" + UUID.randomUUID(), decisionId, hitRow.get("hit_type"), hitRow.get("hit_ref_id"), hitRow.get("frozen_ref_id"),
                    hitRow.get("resolution_record_id"), hitRow.get("hit_result"), hitRow.get("hit_priority_no"), json(hitRow.get("evidence_snapshot")));
        }
        audit("DENY".equals(decisionResult) ? "AUTHZ_DENIED" : "DATA_SCOPE_HIT", "DENY".equals(decisionResult) ? "DENIED" : "SUCCESS", userId, userId, decisionId, null, traceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resource_type", resourceType);
        body.put("decision_id", decisionId);
        body.put("effect", decisionResult);
        body.put("deny_predicates", denyPredicates);
        body.put("allow_predicates", allowPredicates);
        body.put("field_mapping_version", "1.0");
        body.put("scope_snapshot_checksum", snapshotChecksum);
        return body;
    }

    @Transactional
    Map<String, Object> createOrgRule(Map<String, Object> request) {
        String ruleId = text(request, "org_rule_id", "rule-" + UUID.randomUUID());
        Instant now = now();
        jdbcTemplate.update("""
                insert into ia_org_rule
                (org_rule_id, rule_code, rule_name, rule_type, rule_status, rule_scope_type, rule_scope_ref, resolver_config, fallback_policy, version_no, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, ruleId, text(request, "rule_code", ruleId), text(request, "rule_name", ruleId), text(request, "rule_type", "MANAGER_OF_ORG_UNIT"),
                "ACTIVE", text(request, "rule_scope_type", "GLOBAL"), text(request, "rule_scope_ref", "*"), json(request.getOrDefault("resolver_config", Map.of())),
                text(request, "fallback_policy", "EMPTY_SET"), number(request, "version_no", 1), ts(now), ts(now));
        return orgRule(ruleId);
    }

    @Transactional
    Map<String, Object> freezeOrgRuleVersion(Map<String, Object> request) {
        Map<String, Object> rule = orgRule(text(request, "org_rule_id", null));
        String versionId = text(request, "org_rule_version_id", "rulever-" + UUID.randomUUID());
        int versionNo = number(request, "version_no", ((Number) rule.get("version_no")).intValue());
        String checksum = "rule-" + rule.get("org_rule_id") + "-v" + versionNo;
        jdbcTemplate.update("""
                insert into ia_org_rule_version
                (org_rule_version_id, org_rule_id, version_no, rule_type, rule_scope_type, rule_scope_ref, resolver_config_snapshot, fallback_policy_snapshot, schema_version, version_checksum, version_status, effective_from)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, versionId, rule.get("org_rule_id"), versionNo, rule.get("rule_type"), rule.get("rule_scope_type"), rule.get("rule_scope_ref"),
                rule.get("resolver_config"), rule.get("fallback_policy"), "1.0", checksum, "EFFECTIVE", ts(now()));
        return orgRuleVersion(versionId);
    }

    @Transactional
    Map<String, Object> resolveOrgRule(Map<String, Object> request) {
        Map<String, Object> version = orgRuleVersion(text(request, "org_rule_version_id", null));
        String orgUnitId = text(request, "candidate_org_unit_id", text(request, "active_org_unit_id", null));
        Map<String, Object> unit = orgUnit(orgUnitId);
        String managerUserId = unit.get("manager_user_id") == null ? null : unit.get("manager_user_id").toString();
        List<Map<String, Object>> subjects = managerUserId == null ? List.of() : List.of(Map.of(
                "user_id", managerUserId,
                "source_type", "ORG_MANAGER",
                "source_ref", orgUnitId,
                "priority_no", 1));
        List<Map<String, Object>> evidence = List.of(Map.of(
                "source_type", "ORG_MANAGER",
                "source_ref", orgUnitId,
                "org_rule_version_id", version.get("org_rule_version_id")));
        String recordId = "orr-" + UUID.randomUUID();
        String traceId = trace(request);
        jdbcTemplate.update("""
                insert into ia_org_rule_resolution_record
                (org_rule_resolution_record_id, org_rule_version_id, request_trace_id, resolution_scene, context_checksum, resolution_status, resolved_subject_snapshot, evidence_snapshot, fallback_used, resolver_version, resolved_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, recordId, version.get("org_rule_version_id"), traceId, text(request, "resolution_scene", "AUTHORIZATION"),
                "ctx-" + orgUnitId, subjects.isEmpty() ? "EMPTY" : "RESOLVED", json(subjects), json(evidence), false, "1.0", ts(now()));
        audit("ORG_RULE_RESOLVED", subjects.isEmpty() ? "DENIED" : "SUCCESS", text(request, "user_id", "SYSTEM"), managerUserId, recordId, null, traceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("org_rule_resolution_record_id", recordId);
        body.put("org_rule_version_id", version.get("org_rule_version_id"));
        body.put("resolution_status", subjects.isEmpty() ? "EMPTY" : "RESOLVED");
        body.put("resolved_subject_list", subjects);
        body.put("evidence_list", evidence);
        body.put("context_checksum", "ctx-" + orgUnitId);
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

    private Map<String, Object> orgUnit(String orgUnitId) {
        return queryOptional("""
                select org_unit_id, org_id, parent_org_unit_id, org_unit_code, org_unit_name, org_unit_type, org_status, org_path, path_depth, manager_user_id
                from ia_org_unit where org_unit_id = ?
                """, (rs, rowNum) -> orgUnitBody(rs), orgUnitId)
                .orElseThrow(() -> new IllegalArgumentException("org_unit_id 不存在: " + orgUnitId));
    }

    private Map<String, Object> orgUnitBody(ResultSet rs) throws SQLException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("org_unit_id", rs.getString("org_unit_id"));
        body.put("org_id", rs.getString("org_id"));
        body.put("parent_org_unit_id", rs.getString("parent_org_unit_id"));
        body.put("org_unit_code", rs.getString("org_unit_code"));
        body.put("org_unit_name", rs.getString("org_unit_name"));
        body.put("org_unit_type", rs.getString("org_unit_type"));
        body.put("org_status", rs.getString("org_status"));
        body.put("org_path", rs.getString("org_path"));
        body.put("path_depth", rs.getInt("path_depth"));
        body.put("manager_user_id", rs.getString("manager_user_id"));
        return body;
    }

    private Map<String, Object> membership(String membershipId) {
        return queryOptional("""
                select m.membership_id, m.user_id, m.org_id, m.org_unit_id, m.membership_type, m.is_primary_department,
                       u.org_unit_name, u.org_path
                from ia_org_membership m
                join ia_org_unit u on u.org_unit_id = m.org_unit_id
                where m.membership_id = ?
                """, (rs, rowNum) -> membershipBody(rs), membershipId)
                .orElseThrow(() -> new IllegalArgumentException("membership_id 不存在: " + membershipId));
    }

    private Map<String, Object> membershipBody(ResultSet rs) throws SQLException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("membership_id", rs.getString("membership_id"));
        body.put("user_id", rs.getString("user_id"));
        body.put("org_id", rs.getString("org_id"));
        body.put("org_unit_id", rs.getString("org_unit_id"));
        body.put("membership_type", rs.getString("membership_type"));
        body.put("is_primary_department", rs.getBoolean("is_primary_department"));
        body.put("org_unit_name", rs.getString("org_unit_name"));
        body.put("org_path", rs.getString("org_path"));
        return body;
    }

    private Map<String, Object> role(String roleId) {
        return queryOptional("select role_id, role_code, role_name, role_scope, role_type, role_status from ia_role where role_id = ?",
                (rs, rowNum) -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("role_id", rs.getString("role_id"));
                    body.put("role_code", rs.getString("role_code"));
                    body.put("role_name", rs.getString("role_name"));
                    body.put("role_scope", rs.getString("role_scope"));
                    body.put("role_type", rs.getString("role_type"));
                    body.put("role_status", rs.getString("role_status"));
                    return body;
                }, roleId)
                .orElseThrow(() -> new IllegalArgumentException("role_id 不存在: " + roleId));
    }

    private Map<String, Object> roleAssignment(String assignmentId) {
        return queryOptional("select assignment_id, role_id, subject_type, subject_id, grant_org_id, assignment_status from ia_role_assignment where assignment_id = ?",
                (rs, rowNum) -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("assignment_id", rs.getString("assignment_id"));
                    body.put("role_id", rs.getString("role_id"));
                    body.put("subject_type", rs.getString("subject_type"));
                    body.put("subject_id", rs.getString("subject_id"));
                    body.put("grant_org_id", rs.getString("grant_org_id"));
                    body.put("assignment_status", rs.getString("assignment_status"));
                    return body;
                }, assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("assignment_id 不存在: " + assignmentId));
    }

    private Map<String, Object> permissionGrant(String grantId) {
        return queryOptional("""
                select permission_grant_id, grant_target_type, grant_target_id, permission_type, permission_code, resource_type, grant_status, priority_no, effect_mode
                from ia_permission_grant where permission_grant_id = ?
                """, (rs, rowNum) -> permissionGrantBody(rs), grantId)
                .orElseThrow(() -> new IllegalArgumentException("permission_grant_id 不存在: " + grantId));
    }

    private Map<String, Object> permissionGrantBody(ResultSet rs) throws SQLException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("permission_grant_id", rs.getString("permission_grant_id"));
        body.put("grant_target_type", rs.getString("grant_target_type"));
        body.put("grant_target_id", rs.getString("grant_target_id"));
        body.put("permission_type", rs.getString("permission_type"));
        body.put("permission_code", rs.getString("permission_code"));
        body.put("resource_type", rs.getString("resource_type"));
        body.put("grant_status", rs.getString("grant_status"));
        body.put("priority_no", rs.getInt("priority_no"));
        body.put("effect_mode", rs.getString("effect_mode"));
        return body;
    }

    private List<String> effectiveRoleIds(String userId, String orgId) {
        java.util.LinkedHashSet<String> roleIds = new java.util.LinkedHashSet<>(jdbcTemplate.query("""
                select role_id from ia_role_assignment
                where subject_type = 'USER' and subject_id = ? and assignment_status = 'ACTIVE'
                  and (? is null or grant_org_id = ?)
                order by assignment_id
                """, (rs, rowNum) -> rs.getString("role_id"), userId, orgId, orgId));
        roleIds.addAll(jdbcTemplate.query("""
                select ra.role_id
                from ia_role_assignment ra
                join ia_org_membership m on m.org_unit_id = ra.subject_id
                where ra.subject_type = 'ORG_UNIT' and m.user_id = ? and ra.assignment_status = 'ACTIVE' and m.membership_status = 'ACTIVE'
                order by ra.assignment_id
                """, (rs, rowNum) -> rs.getString("role_id"), userId));
        return roleIds.stream().toList();
    }

    private List<Map<String, Object>> permissionGrants(String userId, List<String> roleIds, String permissionType, String permissionCode) {
        List<Map<String, Object>> grants = new java.util.ArrayList<>();
        grants.addAll(jdbcTemplate.query("""
                select permission_grant_id, grant_target_type, grant_target_id, permission_type, permission_code, resource_type, grant_status, priority_no, effect_mode
                from ia_permission_grant
                where grant_target_type = 'USER' and grant_target_id = ? and permission_type = ? and grant_status = 'ACTIVE'
                  and (? is null or permission_code = ?)
                """, (rs, rowNum) -> permissionGrantBody(rs), userId, permissionType, permissionCode, permissionCode));
        for (String roleId : roleIds) {
            grants.addAll(jdbcTemplate.query("""
                    select permission_grant_id, grant_target_type, grant_target_id, permission_type, permission_code, resource_type, grant_status, priority_no, effect_mode
                    from ia_permission_grant
                    where grant_target_type = 'ROLE' and grant_target_id = ? and permission_type = ? and grant_status = 'ACTIVE'
                      and (? is null or permission_code = ?)
                    """, (rs, rowNum) -> permissionGrantBody(rs), roleId, permissionType, permissionCode, permissionCode));
        }
        return grants.stream()
                .sorted(java.util.Comparator.comparingInt(grant -> ((Number) grant.get("priority_no")).intValue()))
                .toList();
    }

    private List<Map<String, Object>> effectiveDecryptDownloadGrants(String userId, String orgId, String resourceType) {
        List<Map<String, Object>> grants = new java.util.ArrayList<>(permissionGrants(userId, effectiveRoleIds(userId, orgId), "SPECIAL", "DECRYPT_DOWNLOAD"));
        grants.addAll(jdbcTemplate.query("""
                select pg.permission_grant_id, pg.grant_target_type, pg.grant_target_id, pg.permission_type, pg.permission_code, pg.resource_type, pg.grant_status, pg.priority_no, pg.effect_mode
                from ia_permission_grant pg
                join ia_org_membership m on m.org_unit_id = pg.grant_target_id
                where pg.grant_target_type = 'ORG_UNIT'
                  and pg.permission_type = 'SPECIAL'
                  and pg.permission_code = 'DECRYPT_DOWNLOAD'
                  and pg.grant_status = 'ACTIVE'
                  and (? is null or pg.resource_type is null or pg.resource_type = ?)
                  and m.user_id = ? and m.membership_status = 'ACTIVE'
                """, (rs, rowNum) -> permissionGrantBody(rs), resourceType, resourceType, userId));
        return grants.stream()
                .filter(grant -> grant.get("resource_type") == null || resourceType.equals(grant.get("resource_type")))
                .sorted(java.util.Comparator.comparingInt(grant -> ((Number) grant.get("priority_no")).intValue()))
                .toList();
    }

    private AuthorizationOutcome evaluateAuthorization(String userId, String activeOrgId, String activeOrgUnitId, String actionCode, String resourceType, String resourceId, String traceId) {
        boolean decryptDownload = "SPECIAL:DECRYPT_DOWNLOAD".equals(actionCode);
        List<Map<String, Object>> grants = decryptDownload
                ? effectiveDecryptDownloadGrants(userId, activeOrgId, resourceType)
                : effectiveFunctionGrants(userId, activeOrgId, actionCode, resourceType);
        List<Map<String, Object>> denyGrants = grants.stream().filter(grant -> "DENY".equals(grant.get("effect_mode"))).toList();
        List<Map<String, Object>> allowGrants = grants.stream().filter(grant -> "ALLOW".equals(grant.get("effect_mode"))).toList();
        List<Map<String, Object>> matchingDataScopes = matchingDataScopes(userId, activeOrgId, activeOrgUnitId, resourceType, resourceId, traceId);
        boolean dataScopeDenied = matchingDataScopes.stream().anyMatch(scope -> "DENY".equals(scope.get("effect_mode")));
        boolean dataScopeAllowed = matchingDataScopes.stream().anyMatch(scope -> "ALLOW".equals(scope.get("effect_mode")));
        String decisionResult;
        String reasonCode;
        if (!denyGrants.isEmpty()) {
            decisionResult = "DENY";
            reasonCode = "EXPLICIT_DENY";
        } else if (allowGrants.isEmpty()) {
            decisionResult = "DENY";
            reasonCode = decryptDownload ? "NO_DECRYPT_DOWNLOAD_GRANT" : "NO_PERMISSION_GRANT";
        } else if (dataScopeDenied || !dataScopeAllowed) {
            decisionResult = "DENY";
            reasonCode = "DATA_SCOPE_MISS";
        } else {
            decisionResult = "ALLOW";
            reasonCode = decryptDownload ? "DECRYPT_DOWNLOAD_AUTHORIZED" : "AUTHORIZATION_ALLOWED";
        }
        String decisionId = "dec-" + UUID.randomUUID();
        Instant now = now();
        Instant expiresAt = now.plus(5, ChronoUnit.MINUTES);
        String permissionChecksum = "grant-" + grants.size() + "-allow-" + allowGrants.size() + "-deny-" + denyGrants.size();
        String dataScopeChecksum = "scope-hit-" + matchingDataScopes.size();
        jdbcTemplate.update("""
                insert into ia_authorization_decision
                (decision_id, subject_user_id, subject_org_id, subject_org_unit_id, action_code, resource_type, resource_id, decision_result, decision_reason_code, permission_snapshot_checksum, data_scope_snapshot_checksum, request_trace_id, expires_at, evaluated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, decisionId, userId, activeOrgId, activeOrgUnitId, actionCode, resourceType, resourceId,
                decisionResult, reasonCode, permissionChecksum, dataScopeChecksum, traceId, ts(expiresAt), ts(now));
        for (Map<String, Object> grant : grants) {
            insertAuthorizationHit(decisionId, "PERMISSION_GRANT", grant.get("permission_grant_id"), null, null, grant.get("effect_mode"), grant.get("priority_no"), grant);
        }
        List<Map<String, Object>> orgRuleEvidenceList = new java.util.ArrayList<>();
        for (Map<String, Object> scope : matchingDataScopes) {
            insertAuthorizationHit(decisionId, "DATA_SCOPE", scope.get("data_scope_id"), null, null, scope.get("effect_mode"), scope.get("priority_no"), scope);
            if (scope.containsKey("org_rule_evidence")) {
                Map<String, Object> evidence = objectMap(scope.get("org_rule_evidence"));
                orgRuleEvidenceList.add(evidence);
                insertAuthorizationHit(decisionId, "ORG_RULE", scope.get("scope_ref"), evidence.get("frozen_ref_id"), evidence.get("resolution_record_id"), scope.get("effect_mode"), scope.get("priority_no"), evidence);
            }
        }
        audit(decryptDownload ? ("ALLOW".equals(decisionResult) ? "DECRYPT_DOWNLOAD_HIT" : "DECRYPT_DOWNLOAD_DENIED") : ("DENY".equals(decisionResult) ? "AUTHZ_DENIED" : "AUTHORIZATION_DECISION"),
                "ALLOW".equals(decisionResult) ? "SUCCESS" : "DENIED", userId, userId, decisionId, null, traceId);
        return new AuthorizationOutcome(decisionId, decisionResult, reasonCode, expiresAt, grants, denyGrants, allowGrants, matchingDataScopes, orgRuleEvidenceList);
    }

    private List<Map<String, Object>> effectiveFunctionGrants(String userId, String orgId, String actionCode, String resourceType) {
        return permissionGrants(userId, effectiveRoleIds(userId, orgId), "FUNCTION", actionCode).stream()
                .filter(grant -> grant.get("resource_type") == null || resourceType.equals(grant.get("resource_type")))
                .toList();
    }

    private List<Map<String, Object>> matchingDataScopes(String userId, String orgId, String activeOrgUnitId, String resourceType, String resourceId, String traceId) {
        List<Map<String, Object>> matches = new java.util.ArrayList<>();
        for (Map<String, Object> scope : effectiveDataScopes(userId, orgId, resourceType)) {
            Optional<Map<String, Object>> matched = dataScopeMatch(scope, userId, orgId, activeOrgUnitId, resourceId, traceId);
            matched.ifPresent(matches::add);
        }
        return matches;
    }

    private Optional<Map<String, Object>> dataScopeMatch(Map<String, Object> scope, String userId, String activeOrgId, String activeOrgUnitId, String resourceId, String traceId) {
        String scopeType = scope.get("scope_type").toString();
        String scopeRef = scope.get("scope_ref").toString();
        boolean matched;
        Map<String, Object> enriched = new LinkedHashMap<>(scope);
        if ("USER_LIST".equals(scopeType)) {
            matched = List.of(scopeRef.split(",")).contains(resourceId);
        } else if ("ORG".equals(scopeType)) {
            matched = true;
        } else if ("SELF".equals(scopeType)) {
            matched = scopeRef.equals(resourceId);
        } else if ("RULE".equals(scopeType)) {
            Map<String, Object> predicate = rulePredicate(scopeRef, userId, activeOrgId, activeOrgUnitId, traceId);
            @SuppressWarnings("unchecked")
            List<String> userIds = (List<String>) predicate.get("values");
            matched = userIds.contains(userId);
            if (matched) {
                enriched.put("org_rule_evidence", Map.of(
                        "hit_type", "ORG_RULE",
                        "hit_ref_id", scopeRef,
                        "frozen_ref_id", predicate.get("org_rule_version_id"),
                        "resolution_record_id", predicate.get("org_rule_resolution_record_id"),
                        "resolution_status", predicate.get("resolution_status")));
            }
        } else {
            matched = scopeRef.equals(resourceId);
        }
        return matched ? Optional.of(enriched) : Optional.empty();
    }

    private void insertAuthorizationHit(String decisionId, String hitType, Object hitRefId, Object frozenRefId, Object resolutionRecordId, Object hitResult, Object priorityNo, Object evidence) {
        jdbcTemplate.update("""
                insert into ia_authorization_hit_result
                (hit_result_id, decision_id, hit_type, hit_ref_id, frozen_ref_id, resolution_record_id, hit_result, hit_priority_no, evidence_snapshot)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "hit-" + UUID.randomUUID(), decisionId, hitType, hitRefId, frozenRefId, resolutionRecordId, hitResult, priorityNo, json(evidence));
    }

    private Map<String, Object> dataScope(String dataScopeId) {
        return queryOptional("""
                select data_scope_id, subject_type, subject_id, resource_type, scope_type, scope_ref, scope_status, priority_no, effect_mode
                from ia_data_scope where data_scope_id = ?
                """, (rs, rowNum) -> dataScopeBody(rs), dataScopeId)
                .orElseThrow(() -> new IllegalArgumentException("data_scope_id 不存在: " + dataScopeId));
    }

    private Map<String, Object> dataScopeBody(ResultSet rs) throws SQLException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data_scope_id", rs.getString("data_scope_id"));
        body.put("subject_type", rs.getString("subject_type"));
        body.put("subject_id", rs.getString("subject_id"));
        body.put("resource_type", rs.getString("resource_type"));
        body.put("scope_type", rs.getString("scope_type"));
        body.put("scope_ref", rs.getString("scope_ref"));
        body.put("scope_status", rs.getString("scope_status"));
        body.put("priority_no", rs.getInt("priority_no"));
        body.put("effect_mode", rs.getString("effect_mode"));
        return body;
    }

    private List<Map<String, Object>> effectiveDataScopes(String userId, String orgId, String resourceType) {
        List<Map<String, Object>> scopes = new java.util.ArrayList<>(jdbcTemplate.query("""
                select data_scope_id, subject_type, subject_id, resource_type, scope_type, scope_ref, scope_status, priority_no, effect_mode
                from ia_data_scope
                where subject_type = 'USER' and subject_id = ? and resource_type = ? and scope_status = 'ACTIVE'
                """, (rs, rowNum) -> dataScopeBody(rs), userId, resourceType));
        for (String roleId : effectiveRoleIds(userId, orgId)) {
            scopes.addAll(jdbcTemplate.query("""
                    select data_scope_id, subject_type, subject_id, resource_type, scope_type, scope_ref, scope_status, priority_no, effect_mode
                    from ia_data_scope
                    where subject_type = 'ROLE' and subject_id = ? and resource_type = ? and scope_status = 'ACTIVE'
                    """, (rs, rowNum) -> dataScopeBody(rs), roleId, resourceType));
        }
        return scopes.stream()
                .sorted(java.util.Comparator.comparingInt(scope -> ((Number) scope.get("priority_no")).intValue()))
                .toList();
    }

    private Map<String, Object> predicateForScope(Map<String, Object> scope, String userId, String activeOrgId, String activeOrgUnitId, String traceId) {
        String scopeType = scope.get("scope_type").toString();
        String scopeRef = scope.get("scope_ref").toString();
        return switch (scopeType) {
            case "SELF" -> Map.of("field", "owner_user_id", "operator", "EQ", "value", userId, "scope_type", scopeType);
            case "ORG" -> Map.of("field", "tenant_or_org_id", "operator", "EQ", "value", activeOrgId, "scope_type", scopeType);
            case "ORG_UNIT" -> Map.of("field", "owner_org_unit_id", "operator", "IN", "values", List.of(scopeRef), "scope_type", scopeType);
            case "ORG_SUBTREE" -> Map.of("field", "owner_org_path", "operator", "STARTS_WITH_PATH", "value", orgUnit(scopeRef).get("org_path"), "scope_type", scopeType);
            case "USER_LIST" -> Map.of("field", "owner_user_id", "operator", "IN", "values", List.of(scopeRef.split(",")), "scope_type", scopeType);
            case "RULE" -> rulePredicate(scopeRef, userId, activeOrgId, activeOrgUnitId, traceId);
            default -> throw new IllegalArgumentException("未知 scope_type: " + scopeType);
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rulePredicate(String orgRuleVersionId, String userId, String activeOrgId, String activeOrgUnitId, String traceId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("org_rule_version_id", orgRuleVersionId);
        request.put("resolution_scene", "AUTHORIZATION");
        request.put("user_id", userId);
        request.put("active_org_id", activeOrgId);
        request.put("active_org_unit_id", activeOrgUnitId);
        request.put("trace_id", traceId);
        Map<String, Object> resolution = resolveOrgRule(request);
        List<Map<String, Object>> subjects = (List<Map<String, Object>>) resolution.get("resolved_subject_list");
        Map<String, Object> predicate = new LinkedHashMap<>();
        predicate.put("field", "owner_user_id");
        predicate.put("operator", "IN");
        predicate.put("values", subjects.stream().map(subject -> subject.get("user_id")).toList());
        predicate.put("scope_type", "RULE");
        predicate.put("org_rule_version_id", resolution.get("org_rule_version_id"));
        predicate.put("org_rule_resolution_record_id", resolution.get("org_rule_resolution_record_id"));
        predicate.put("resolution_status", resolution.get("resolution_status"));
        return predicate;
    }

    private Map<String, Object> orgRule(String ruleId) {
        return queryOptional("""
                select org_rule_id, rule_code, rule_name, rule_type, rule_status, rule_scope_type, rule_scope_ref, resolver_config, fallback_policy, version_no
                from ia_org_rule where org_rule_id = ?
                """, (rs, rowNum) -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("org_rule_id", rs.getString("org_rule_id"));
                    body.put("rule_code", rs.getString("rule_code"));
                    body.put("rule_name", rs.getString("rule_name"));
                    body.put("rule_type", rs.getString("rule_type"));
                    body.put("rule_status", rs.getString("rule_status"));
                    body.put("rule_scope_type", rs.getString("rule_scope_type"));
                    body.put("rule_scope_ref", rs.getString("rule_scope_ref"));
                    body.put("resolver_config", rs.getString("resolver_config"));
                    body.put("fallback_policy", rs.getString("fallback_policy"));
                    body.put("version_no", rs.getInt("version_no"));
                    return body;
                }, ruleId).orElseThrow(() -> new IllegalArgumentException("org_rule_id 不存在: " + ruleId));
    }

    private Map<String, Object> orgRuleVersion(String versionId) {
        return queryOptional("""
                select org_rule_version_id, org_rule_id, version_no, rule_type, rule_scope_type, rule_scope_ref, resolver_config_snapshot, fallback_policy_snapshot, schema_version, version_checksum, version_status
                from ia_org_rule_version where org_rule_version_id = ?
                """, (rs, rowNum) -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("org_rule_version_id", rs.getString("org_rule_version_id"));
                    body.put("org_rule_id", rs.getString("org_rule_id"));
                    body.put("version_no", rs.getInt("version_no"));
                    body.put("rule_type", rs.getString("rule_type"));
                    body.put("rule_scope_type", rs.getString("rule_scope_type"));
                    body.put("rule_scope_ref", rs.getString("rule_scope_ref"));
                    body.put("resolver_config_snapshot", rs.getString("resolver_config_snapshot"));
                    body.put("fallback_policy_snapshot", rs.getString("fallback_policy_snapshot"));
                    body.put("schema_version", rs.getString("schema_version"));
                    body.put("version_checksum", rs.getString("version_checksum"));
                    body.put("version_status", rs.getString("version_status"));
                    return body;
                }, versionId).orElseThrow(() -> new IllegalArgumentException("org_rule_version_id 不存在: " + versionId));
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String provider(Map<String, Object> request) {
        return text(request, "provider", "LOCAL").toUpperCase(java.util.Locale.ROOT);
    }

    private String trace(Map<String, Object> request) {
        return text(request, "trace_id", "trace-" + UUID.randomUUID());
    }

    private int number(Map<String, Object> request, String key, int defaultValue) {
        Object value = request.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? defaultValue : Integer.parseInt(value.toString());
    }

    private boolean bool(Map<String, Object> request, String key, boolean defaultValue) {
        Object value = request.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? defaultValue : Boolean.parseBoolean(value.toString());
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

    private record AuthorizationOutcome(
            String decisionId,
            String decisionResult,
            String reasonCode,
            Instant expiresAt,
            List<Map<String, Object>> grants,
            List<Map<String, Object>> denyGrants,
            List<Map<String, Object>> allowGrants,
            List<Map<String, Object>> matchingDataScopes,
            List<Map<String, Object>> orgRuleEvidenceList) {}

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

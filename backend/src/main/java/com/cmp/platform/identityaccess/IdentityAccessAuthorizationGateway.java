package com.cmp.platform.identityaccess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityAccessAuthorizationGateway {

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    IdentityAccessAuthorizationGateway(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> decide(Map<String, Object> request) {
        Map<String, Object> subject = objectMap(request.get("subject_ref"));
        Map<String, Object> resource = objectMap(request.get("resource_ref"));
        String userId = text(subject, "user_id", text(request, "user_id", null));
        String orgId = text(subject, "org_id", text(request, "active_org_id", "ORG-DEFAULT"));
        String orgUnitId = text(subject, "org_unit_id", text(request, "active_org_unit_id", null));
        String actionCode = text(request, "action_code", null);
        String resourceType = text(request, "resource_type", null);
        String resourceId = text(resource, "resource_id", text(request, "resource_id", null));
        String traceId = text(request, "trace_id", "trace-" + UUID.randomUUID());

        List<Map<String, Object>> grants = permissionGrants(userId, actionCode, resourceType);
        List<Map<String, Object>> denyGrants = grants.stream().filter(grant -> "DENY".equals(grant.get("effect_mode"))).toList();
        List<Map<String, Object>> allowGrants = grants.stream().filter(grant -> "ALLOW".equals(grant.get("effect_mode"))).toList();
        List<Map<String, Object>> dataScopes = matchingDataScopes(userId, resourceType, resourceId);
        boolean dataDenied = dataScopes.stream().anyMatch(scope -> "DENY".equals(scope.get("effect_mode")));
        boolean dataAllowed = dataScopes.stream().anyMatch(scope -> "ALLOW".equals(scope.get("effect_mode")));

        String decisionResult;
        String reasonCode;
        if (!denyGrants.isEmpty()) {
            decisionResult = "DENY";
            reasonCode = "EXPLICIT_DENY";
        } else if (allowGrants.isEmpty()) {
            decisionResult = "DENY";
            reasonCode = "NO_PERMISSION_GRANT";
        } else if (dataDenied || !dataAllowed) {
            decisionResult = "DENY";
            reasonCode = "DATA_SCOPE_MISS";
        } else {
            decisionResult = "ALLOW";
            reasonCode = "AUTHORIZATION_ALLOWED";
        }

        String decisionId = "dec-" + UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        jdbcTemplate.update("""
                insert into ia_authorization_decision
                (decision_id, subject_user_id, subject_org_id, subject_org_unit_id, action_code, resource_type, resource_id,
                 decision_result, decision_reason_code, permission_snapshot_checksum, data_scope_snapshot_checksum, request_trace_id, expires_at, evaluated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, decisionId, userId, orgId, orgUnitId, actionCode, resourceType, resourceId, decisionResult, reasonCode,
                "grant-" + grants.size() + "-allow-" + allowGrants.size() + "-deny-" + denyGrants.size(),
                "scope-hit-" + dataScopes.size(), traceId, Timestamp.from(now.plus(5, ChronoUnit.MINUTES)), Timestamp.from(now));
        for (Map<String, Object> grant : grants) {
            insertHit(decisionId, "PERMISSION_GRANT", grant.get("permission_grant_id"), grant.get("effect_mode"), grant.get("priority_no"), grant);
        }
        for (Map<String, Object> scope : dataScopes) {
            insertHit(decisionId, "DATA_SCOPE", scope.get("data_scope_id"), scope.get("effect_mode"), scope.get("priority_no"), scope);
        }
        audit("ALLOW".equals(decisionResult) ? "AUTHORIZATION_DECISION" : "AUTHZ_DENIED", "ALLOW".equals(decisionResult) ? "SUCCESS" : "DENIED", userId, decisionId, traceId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision_id", decisionId);
        body.put("decision_result", decisionResult);
        body.put("reason_list", List.of(reasonCode));
        Map<String, Object> subjectRef = new LinkedHashMap<>();
        subjectRef.put("user_id", userId);
        subjectRef.put("org_id", orgId);
        subjectRef.put("org_unit_id", orgUnitId);
        body.put("subject_ref", subjectRef);
        body.put("action_code", actionCode);
        body.put("resource_type", resourceType);
        Map<String, Object> resourceRef = new LinkedHashMap<>();
        resourceRef.put("resource_id", resourceId);
        body.put("resource_ref", resourceRef);
        body.put("matched_permission_list", "EXPLICIT_DENY".equals(reasonCode) ? denyGrants : allowGrants);
        body.put("data_scope_hit", dataScopes);
        return body;
    }

    private List<Map<String, Object>> permissionGrants(String userId, String actionCode, String resourceType) {
        return jdbcTemplate.query("""
                select permission_grant_id, grant_target_type, grant_target_id, permission_type, permission_code, resource_type, priority_no, effect_mode
                from ia_permission_grant
                where grant_status = 'ACTIVE'
                  and permission_type = 'FUNCTION'
                  and permission_code = ?
                  and (resource_type is null or resource_type = ?)
                  and grant_target_type = 'USER'
                  and grant_target_id = ?
                order by priority_no, permission_grant_id
                """, (rs, rowNum) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("permission_grant_id", rs.getString("permission_grant_id"));
            body.put("grant_target_type", rs.getString("grant_target_type"));
            body.put("grant_target_id", rs.getString("grant_target_id"));
            body.put("permission_type", rs.getString("permission_type"));
            body.put("permission_code", rs.getString("permission_code"));
            body.put("resource_type", rs.getString("resource_type"));
            body.put("priority_no", rs.getInt("priority_no"));
            body.put("effect_mode", rs.getString("effect_mode"));
            return body;
        }, actionCode, resourceType, userId);
    }

    private List<Map<String, Object>> matchingDataScopes(String userId, String resourceType, String resourceId) {
        return jdbcTemplate.query("""
                select data_scope_id, subject_type, subject_id, resource_type, scope_type, scope_ref, priority_no, effect_mode
                from ia_data_scope
                where scope_status = 'ACTIVE'
                  and subject_type = 'USER'
                  and subject_id = ?
                  and resource_type = ?
                  and (scope_type = 'ORG' or scope_ref = ? or (scope_type = 'USER_LIST' and position(? in scope_ref) > 0))
                order by priority_no, data_scope_id
                """, (rs, rowNum) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("data_scope_id", rs.getString("data_scope_id"));
            body.put("subject_type", rs.getString("subject_type"));
            body.put("subject_id", rs.getString("subject_id"));
            body.put("resource_type", rs.getString("resource_type"));
            body.put("scope_type", rs.getString("scope_type"));
            body.put("scope_ref", rs.getString("scope_ref"));
            body.put("priority_no", rs.getInt("priority_no"));
            body.put("effect_mode", rs.getString("effect_mode"));
            return body;
        }, userId, resourceType, resourceId, resourceId);
    }

    private void insertHit(String decisionId, String hitType, Object hitRefId, Object result, Object priorityNo, Object evidence) {
        jdbcTemplate.update("""
                insert into ia_authorization_hit_result
                (hit_result_id, decision_id, hit_type, hit_ref_id, frozen_ref_id, resolution_record_id, hit_result, hit_priority_no, evidence_snapshot)
                values (?, ?, ?, ?, null, null, ?, ?, ?)
                """, "hit-" + UUID.randomUUID(), decisionId, hitType, hitRefId, result, priorityNo, json(evidence));
    }

    private void audit(String eventType, String resultStatus, String userId, String decisionId, String traceId) {
        jdbcTemplate.update("""
                insert into ia_identity_audit
                (audit_view_id, event_type, result_status, actor_user_id, target_user_id, target_resource_id, protocol_exchange_id, trace_id, occurred_at)
                values (?, ?, ?, ?, ?, ?, null, ?, ?)
                """, "aud-" + UUID.randomUUID(), eventType, resultStatus, userId, userId, decisionId, traceId, Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MILLIS)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String text(Map<String, Object> map, String fieldName, String defaultValue) {
        Object value = map.get(fieldName);
        return value == null ? defaultValue : value.toString();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON 序列化失败", exception);
        }
    }
}

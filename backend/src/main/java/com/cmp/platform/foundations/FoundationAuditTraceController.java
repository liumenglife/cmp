package com.cmp.platform.foundations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FoundationAuditTraceController {

    private final JdbcTemplate jdbcTemplate;

    FoundationAuditTraceController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/foundations/audit-trace")
    Map<String, Object> auditTrace(@RequestParam("trace_id") String traceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trace_id", traceId);
        body.put("identity_access", Map.of("item_list", identityEvents(traceId)));
        body.put("agent_os", Map.of("item_list", agentEvents(traceId)));
        body.put("integration_hub", Map.of("item_list", integrationEvents(traceId)));
        return body;
    }

    private List<Map<String, Object>> identityEvents(String traceId) {
        return jdbcTemplate.query("""
                select audit_view_id, event_type, result_status, actor_user_id, target_user_id, target_resource_id, occurred_at
                from ia_identity_audit
                where trace_id = ?
                order by occurred_at, audit_view_id
                """, (rs, rowNum) -> {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("audit_view_id", rs.getString("audit_view_id"));
            event.put("event_type", rs.getString("event_type"));
            event.put("result_status", rs.getString("result_status"));
            event.put("actor_user_id", rs.getString("actor_user_id"));
            event.put("target_user_id", rs.getString("target_user_id"));
            event.put("target_resource_id", rs.getString("target_resource_id"));
            event.put("occurred_at", rs.getTimestamp("occurred_at").toInstant().toString());
            return event;
        }, traceId);
    }

    private List<Map<String, Object>> agentEvents(String traceId) {
        return jdbcTemplate.query("""
                select audit_event_id, object_type, object_id, parent_object_id, action_type, actor_id, result_status, occurred_at
                from ao_agent_audit_event
                where trace_id = ?
                order by occurred_at, audit_event_id
                """, (rs, rowNum) -> {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("audit_event_id", rs.getString("audit_event_id"));
            event.put("object_type", rs.getString("object_type"));
            event.put("object_id", rs.getString("object_id"));
            event.put("parent_object_id", rs.getString("parent_object_id"));
            event.put("action_type", rs.getString("action_type"));
            event.put("actor_id", rs.getString("actor_id"));
            event.put("result_status", rs.getString("result_status"));
            event.put("occurred_at", rs.getTimestamp("occurred_at").toInstant().toString());
            return event;
        }, traceId);
    }

    private List<Map<String, Object>> integrationEvents(String traceId) {
        return jdbcTemplate.query("""
                select audit_event_id, direction, resource_type, resource_id, action_type, result_status, system_name, occurred_at
                from ih_integration_audit_event
                where trace_id = ?
                order by occurred_at, audit_event_id
                """, (rs, rowNum) -> {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("audit_event_id", rs.getString("audit_event_id"));
            event.put("direction", rs.getString("direction"));
            event.put("resource_type", rs.getString("resource_type"));
            event.put("resource_id", rs.getString("resource_id"));
            event.put("summary", rs.getString("action_type"));
            event.put("result_status", rs.getString("result_status"));
            event.put("system_name", rs.getString("system_name"));
            event.put("occurred_at", rs.getTimestamp("occurred_at").toInstant().toString());
            return event;
        }, traceId);
    }
}

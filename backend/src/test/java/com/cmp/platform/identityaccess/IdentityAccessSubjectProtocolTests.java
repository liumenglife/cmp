package com.cmp.platform.identityaccess;

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
class IdentityAccessSubjectProtocolTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanIdentityAccessTables() {
        jdbcTemplate.update("DELETE FROM ia_idempotency_record");
        jdbcTemplate.update("DELETE FROM ia_identity_audit");
        jdbcTemplate.update("DELETE FROM ia_identity_manual_disposition");
        jdbcTemplate.update("DELETE FROM ia_identity_binding_precheck");
        jdbcTemplate.update("DELETE FROM ia_identity_session");
        jdbcTemplate.update("DELETE FROM ia_protocol_exchange");
        jdbcTemplate.update("DELETE FROM ia_identity_binding");
        jdbcTemplate.update("DELETE FROM ia_user");
    }

    @Test
    void localAccountLoginCreatesUnifiedSubjectAndSession() throws Exception {
        createUser("u-local-001", "zhangsan", "张三");

        mockMvc.perform(post("/api/auth/password/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "login_name": "zhangsan",
                                  "password": "password",
                                  "client_type": "WEB"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_id").isNotEmpty())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.user_context.user_id").value("u-local-001"))
                .andExpect(jsonPath("$.user_context.login_name").value("zhangsan"));

        assertTableExists("ia_user");
        assertTableExists("ia_identity_binding");
        assertTableExists("ia_identity_session");
        assertRowCount("ia_user", "user_id = 'u-local-001'", 1);
        assertRowCount("ia_identity_binding", "provider = 'LOCAL' and external_identity = 'zhangsan'", 1);
        assertRowCount("ia_identity_session", "user_id = 'u-local-001' and session_status = 'ACTIVE'", 1);
    }

    @Test
    void externalProtocolExchangeMapsTrustedSsoIdentityToUnifiedSubject() throws Exception {
        createUser("u-sso-001", "lisi", "李四");
        bindIdentity("SSO", "sso-lisi", "u-sso-001");

        mockMvc.perform(post("/api/auth/sessions/exchanges")
                        .header("Idempotency-Key", "idem-sso-lisi-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "SSO",
                                  "provider_tenant_ref": "tenant-a",
                                  "protocol_message_key": "msg-sso-lisi-1",
                                  "ticket": "trusted:sso-lisi",
                                  "external_identity_key": "sso-lisi",
                                  "external_login_name": "lisi"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.binding_status").value("ACTIVE"))
                .andExpect(jsonPath("$.protocol_exchange_id").isNotEmpty())
                .andExpect(jsonPath("$.user_context.user_id").value("u-sso-001"));
    }

    @Test
    void duplicateExternalCallbackIsIdempotentAndDoesNotCreateSecondExchange() throws Exception {
        createUser("u-wecom-001", "wangwu", "王五");
        bindIdentity("WECOM", "wecom-wangwu", "u-wecom-001");
        String body = """
                {
                  "provider": "WECOM",
                  "provider_tenant_ref": "corp-a",
                  "protocol_message_key": "callback-001",
                  "code": "trusted:wecom-wangwu",
                  "external_identity_key": "wecom-wangwu",
                  "external_login_name": "wangwu"
                }
                """;

        mockMvc.perform(post("/api/auth/sessions/exchanges")
                        .header("Idempotency-Key", "idem-wecom-callback-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false));

        mockMvc.perform(post("/api/auth/sessions/exchanges")
                        .header("Idempotency-Key", "idem-wecom-callback-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.protocol_exchange_id").isNotEmpty())
                .andExpect(jsonPath("$.user_context.user_id").value("u-wecom-001"));
    }

    @Test
    void candidateSubjectConflictFreezesIdentityAndBlocksSession() throws Exception {
        createUser("u-conflict-001", "zhao-a", "赵一");
        createUser("u-conflict-002", "zhao-b", "赵二");

        mockMvc.perform(post("/api/auth/sessions/exchanges")
                        .header("Idempotency-Key", "idem-ldap-conflict-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "LDAP",
                                  "provider_tenant_ref": "ldap-main",
                                  "protocol_message_key": "ldap-conflict-1",
                                  "ticket": "trusted:ldap-zhao",
                                  "external_identity_key": "ldap-zhao",
                                  "external_mobile": "13800000000",
                                  "candidate_user_ids": ["u-conflict-001", "u-conflict-002"]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.binding_status").value("CONFLICT"))
                .andExpect(jsonPath("$.exchange_status").value("FROZEN"))
                .andExpect(jsonPath("$.session_gate_result").value("MANUAL_REQUIRED"))
                .andExpect(jsonPath("$.candidate_user_list.length()").value(2));

        assertTableExists("ia_protocol_exchange");
        assertTableExists("ia_identity_binding_precheck");
        assertTableExists("ia_identity_audit");
        assertRowCount("ia_protocol_exchange", "provider = 'LDAP' and external_identity = 'ldap-zhao' and exchange_status = 'FROZEN'", 1);
        assertRowCount("ia_identity_binding_precheck", "provider = 'LDAP' and external_identity = 'ldap-zhao' and precheck_status = 'CONFLICT'", 1);
        assertRowCount("ia_identity_audit", "event_type = 'BINDING_CONFLICT' and result_status = 'DENIED'", 1);
    }

    @Test
    void manualDispositionCanUnfreezeAndRelinkConflictedBinding() throws Exception {
        createUser("u-manual-001", "sunyi", "孙一");
        createUser("u-manual-002", "suner", "孙二");
        String exchangeId = freezeConflict();

        mockMvc.perform(post("/api/identity-bindings/manual-dispositions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "protocol_exchange_id": "%s",
                                  "disposition_action": "RELINK",
                                  "target_user_id": "u-manual-001",
                                  "operator_id": "admin-001",
                                  "disposition_reason": "人工确认同一自然人"
                                }
                                """.formatted(exchangeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.binding_status").value("ACTIVE"))
                .andExpect(jsonPath("$.after_status_snapshot.user_id").value("u-manual-001"))
                .andExpect(jsonPath("$.retry_policy_status").value("RETRYABLE"));

        assertTableExists("ia_identity_manual_disposition");
        assertRowCount("ia_identity_manual_disposition", "protocol_exchange_id = '" + exchangeId + "' and target_user_id = 'u-manual-001'", 1);
        assertRowCount("ia_identity_binding", "provider = 'LDAP' and user_id = 'u-manual-001' and binding_status = 'ACTIVE'", 1);
    }

    @Test
    void duplicateBindingForSameSubjectIsIdempotentButCrossSubjectConflictIsFrozen() throws Exception {
        createUser("u-bind-001", "bind-a", "绑定甲");
        createUser("u-bind-002", "bind-b", "绑定乙");
        bindIdentity("SSO", "same-external", "u-bind-001");

        mockMvc.perform(post("/api/identity-bindings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "SSO",
                                  "external_identity": "same-external",
                                  "user_id": "u-bind-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value("u-bind-001"));

        mockMvc.perform(post("/api/identity-bindings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "SSO",
                                  "external_identity": "same-external",
                                  "user_id": "u-bind-002",
                                  "trace_id": "trace-binding-conflict"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.binding_status").value("CONFLICT"))
                .andExpect(jsonPath("$.session_gate_result").value("MANUAL_REQUIRED"));

        assertRowCount("ia_identity_binding", "provider = 'SSO' and external_identity = 'same-external' and user_id = 'u-bind-001'", 1);
        assertRowCount("ia_identity_binding", "provider = 'SSO' and external_identity = 'same-external' and user_id = 'u-bind-002'", 0);
        assertRowCount("ia_identity_binding_precheck", "provider = 'SSO' and external_identity = 'same-external' and precheck_status = 'CONFLICT'", 1);
    }

    @Test
    void untrustedExternalProtocolTicketIsRejectedAndPersistedAsFailedExchange() throws Exception {
        createUser("u-untrusted-001", "unsafe-user", "不可信用户");

        mockMvc.perform(post("/api/auth/sessions/exchanges")
                        .header("Idempotency-Key", "idem-untrusted-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "SSO",
                                  "protocol_message_key": "unsafe-msg-1",
                                  "ticket": "unsafe:sso-user",
                                  "external_identity_key": "sso-user",
                                  "external_login_name": "unsafe-user",
                                  "trace_id": "trace-untrusted-ticket"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.exchange_status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("UNTRUSTED_PROTOCOL_TICKET"));

        assertRowCount("ia_protocol_exchange", "provider = 'SSO' and external_identity = 'sso-user' and exchange_status = 'FAILED'", 1);
        assertRowCount("ia_identity_audit", "event_type = 'PROTOCOL_EXCHANGE_FAILED' and result_status = 'DENIED'", 1);
        assertRowCount("ia_identity_session", "user_id = 'u-untrusted-001'", 0);
    }

    @Test
    void meReturnsControlledSessionContextWithEmptyRoleAndPermissionSummaries() throws Exception {
        createUser("u-me-001", "zhouliu", "周六");
        String token = loginAndExtractToken("zhouliu");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.user_id").value("u-me-001"))
                .andExpect(jsonPath("$.org_context.active_org_id").value("ORG-DEFAULT"))
                .andExpect(jsonPath("$.role_list").isArray())
                .andExpect(jsonPath("$.permission_summary.permission_list").isArray());
    }

    @Test
    void auditViewCanTraceProtocolAndManualEventsByTraceId() throws Exception {
        createUser("u-audit-001", "audit-a", "审计甲");
        createUser("u-audit-002", "audit-b", "审计乙");
        String exchangeId = freezeConflictWithTrace("trace-audit-001");

        mockMvc.perform(post("/api/identity-bindings/manual-dispositions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "protocol_exchange_id": "%s",
                                  "disposition_action": "RELINK",
                                  "target_user_id": "u-audit-001",
                                  "operator_id": "admin-002",
                                  "trace_id": "trace-audit-001",
                                  "disposition_reason": "审计链路验证"
                                }
                                """.formatted(exchangeId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/identity-audit-views")
                        .queryParam("trace_id", "trace-audit-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.item_list[0].trace_id").value("trace-audit-001"));
    }

    @Test
    void sameIdempotencyKeyWithDifferentExchangePayloadReturnsConflict() throws Exception {
        createUser("u-idem-001", "idem-user", "幂等用户");
        bindIdentity("SSO", "idem-sso-a", "u-idem-001");

        mockMvc.perform(post("/api/auth/sessions/exchanges")
                        .header("Idempotency-Key", "idem-conflict-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "SSO",
                                  "protocol_message_key": "idem-msg-a",
                                  "ticket": "trusted:idem-sso-a",
                                  "external_identity_key": "idem-sso-a"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/sessions/exchanges")
                        .header("Idempotency-Key", "idem-conflict-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "SSO",
                                  "protocol_message_key": "idem-msg-b",
                                  "ticket": "trusted:idem-sso-a",
                                  "external_identity_key": "idem-sso-a"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("40905"))
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_CONFLICT"));
    }

    private void createUser(String userId, String loginName, String displayName) throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "user_id": "%s",
                                  "login_name": "%s",
                                  "display_name": "%s"
                                }
                                """.formatted(userId, loginName, displayName)))
                .andExpect(status().isCreated());
    }

    private void bindIdentity(String provider, String externalIdentity, String userId) throws Exception {
        mockMvc.perform(post("/api/identity-bindings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "%s",
                                  "external_identity": "%s",
                                  "user_id": "%s"
                                }
                                """.formatted(provider, externalIdentity, userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.binding_status").value("ACTIVE"));
    }

    private String loginAndExtractToken(String loginName) throws Exception {
        return jsonString(mockMvc.perform(post("/api/auth/password/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "login_name": "%s",
                                  "password": "password",
                                  "client_type": "WEB"
                                }
                                """.formatted(loginName)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), "access_token");
    }

    private String freezeConflict() throws Exception {
        return freezeConflictWithTrace("trace-manual-001");
    }

    private String freezeConflictWithTrace(String traceId) throws Exception {
        return jsonString(mockMvc.perform(post("/api/auth/sessions/exchanges")
                        .header("Idempotency-Key", "idem-" + traceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "LDAP",
                                  "provider_tenant_ref": "ldap-main",
                                  "protocol_message_key": "%s",
                                  "ticket": "trusted:ldap-sun-%s",
                                  "external_identity_key": "ldap-sun-%s",
                                  "trace_id": "%s",
                                  "candidate_user_ids": ["u-manual-001", "u-manual-002", "u-audit-001", "u-audit-002"]
                                }
                                """.formatted(traceId, traceId, traceId, traceId)))
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse()
                .getContentAsString(), "protocol_exchange_id");
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
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    private void assertRowCount(String tableName, String whereClause, int expectedCount) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + whereClause, Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(expectedCount);
    }
}

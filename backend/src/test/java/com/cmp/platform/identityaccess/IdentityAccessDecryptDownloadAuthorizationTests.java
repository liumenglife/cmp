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
class IdentityAccessDecryptDownloadAuthorizationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanIdentityAccessTables() {
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
    void userGrantAllowsDecryptDownloadAndPassesDecisionCredential() throws Exception {
        seedUserOrgAndDocumentScope("u-decrypt-user", "dept-a", "doc-allow");

        createDecryptGrant("pg-decrypt-user", "USER", "u-decrypt-user", "DOCUMENT", "ALLOW", 10);

        mockMvc.perform(post("/api/authorization/decrypt-download-hits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "user_id": "u-decrypt-user",
                                  "active_org_id": "org-root",
                                  "active_org_unit_id": "dept-a",
                                  "document_id": "doc-allow",
                                  "trace_id": "trace-decrypt-allow"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hit").value(true))
                .andExpect(jsonPath("$.decision_ref.decision_result").value("ALLOW"))
                .andExpect(jsonPath("$.decision_ref.decision_id").isNotEmpty())
                .andExpect(jsonPath("$.decision_ref.action_code").value("SPECIAL:DECRYPT_DOWNLOAD"))
                .andExpect(jsonPath("$.decision_ref.resource_id").value("doc-allow"))
                .andExpect(jsonPath("$.matched_grant_list[0].permission_grant_id").value("pg-decrypt-user"));

        assertTableExists("ia_authorization_decision");
        assertTableExists("ia_authorization_hit_result");
        assertRowCount("ia_authorization_decision", "request_trace_id = 'trace-decrypt-allow' and decision_result = 'ALLOW' and resource_id = 'doc-allow' and expires_at is not null", 1);
        assertRowCount("ia_authorization_hit_result", "hit_type = 'PERMISSION_GRANT' and hit_ref_id = 'pg-decrypt-user' and hit_result = 'ALLOW'", 1);
        assertRowCount("ia_authorization_hit_result", "hit_type = 'DATA_SCOPE' and hit_ref_id = 'ds-doc-allow' and hit_result = 'ALLOW'", 1);
        assertRowCount("ia_identity_audit", "event_type = 'DECRYPT_DOWNLOAD_HIT' and trace_id = 'trace-decrypt-allow'", 1);
    }

    @Test
    void missingDecryptGrantRejectsBeforeExecutionSide() throws Exception {
        seedUserOrgAndDocumentScope("u-no-grant", "dept-a", "doc-no-grant");

        mockMvc.perform(post("/api/authorization/decrypt-download-hits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "user_id": "u-no-grant",
                                  "active_org_id": "org-root",
                                  "active_org_unit_id": "dept-a",
                                  "document_id": "doc-no-grant",
                                  "trace_id": "trace-decrypt-denied"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hit").value(false))
                .andExpect(jsonPath("$.decision_ref.decision_result").value("DENY"))
                .andExpect(jsonPath("$.reason_list[0]").value("NO_DECRYPT_DOWNLOAD_GRANT"));

        assertRowCount("ia_authorization_decision", "request_trace_id = 'trace-decrypt-denied' and decision_result = 'DENY' and decision_reason_code = 'NO_DECRYPT_DOWNLOAD_GRANT'", 1);
        assertRowCount("ia_identity_audit", "event_type = 'DECRYPT_DOWNLOAD_DENIED' and trace_id = 'trace-decrypt-denied'", 1);
    }

    @Test
    void explicitDenyGrantWinsOverDepartmentAllow() throws Exception {
        seedUserOrgAndDocumentScope("u-denied", "dept-denied", "doc-denied");
        createDecryptGrant("pg-dept-allow", "ORG_UNIT", "dept-denied", "DOCUMENT", "ALLOW", 20);
        createDecryptGrant("pg-user-deny", "USER", "u-denied", "DOCUMENT", "DENY", 1);

        mockMvc.perform(post("/api/authorization/decrypt-download-hits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "user_id": "u-denied",
                                  "active_org_id": "org-root",
                                  "active_org_unit_id": "dept-denied",
                                  "document_id": "doc-denied",
                                  "trace_id": "trace-explicit-deny"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hit").value(false))
                .andExpect(jsonPath("$.decision_ref.decision_result").value("DENY"))
                .andExpect(jsonPath("$.reason_list[0]").value("EXPLICIT_DENY"));

        assertRowCount("ia_authorization_hit_result", "hit_ref_id = 'pg-user-deny' and hit_result = 'DENY'", 1);
        assertRowCount("ia_identity_audit", "event_type = 'PERMISSION_EXPLICIT_DENY' and trace_id = 'trace-pg-user-deny'", 1);
        assertRowCount("ia_identity_audit", "event_type = 'DECRYPT_DOWNLOAD_DENIED' and trace_id = 'trace-explicit-deny'", 1);
    }

    @Test
    void dataScopeMissRejectsEvenWhenDecryptGrantMatches() throws Exception {
        seedUserOrgAndDocumentScope("u-scope-miss", "dept-a", "doc-other");
        createDecryptGrant("pg-scope-miss", "USER", "u-scope-miss", "DOCUMENT", "ALLOW", 10);

        mockMvc.perform(post("/api/authorization/decrypt-download-hits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "user_id": "u-scope-miss",
                                  "active_org_id": "org-root",
                                  "active_org_unit_id": "dept-a",
                                  "document_id": "doc-not-in-scope",
                                  "trace_id": "trace-scope-miss"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hit").value(false))
                .andExpect(jsonPath("$.reason_list[0]").value("DATA_SCOPE_MISS"));

        assertRowCount("ia_authorization_decision", "request_trace_id = 'trace-scope-miss' and decision_reason_code = 'DATA_SCOPE_MISS'", 1);
        assertRowCount("ia_identity_audit", "event_type = 'DECRYPT_DOWNLOAD_DENIED' and trace_id = 'trace-scope-miss'", 1);
    }

    @Test
    void departmentGrantMatchesCurrentActiveMembership() throws Exception {
        seedUserOrgAndDocumentScope("u-dept-grant", "dept-authorized", "doc-dept");
        createDecryptGrant("pg-dept-download", "ORG_UNIT", "dept-authorized", "DOCUMENT", "ALLOW", 10);

        mockMvc.perform(post("/api/authorization/decrypt-download-hits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "user_id": "u-dept-grant",
                                  "active_org_id": "org-root",
                                  "active_org_unit_id": "dept-authorized",
                                  "document_id": "doc-dept",
                                  "trace_id": "trace-dept-grant"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hit").value(true))
                .andExpect(jsonPath("$.matched_grant_list[0].grant_target_type").value("ORG_UNIT"));

        assertRowCount("ia_authorization_hit_result", "hit_ref_id = 'pg-dept-download' and hit_result = 'ALLOW'", 1);
    }

    @Test
    void revokedGrantInvalidatesShortTermDecisionAndRecoveryAuditsAction() throws Exception {
        seedUserOrgAndDocumentScope("u-revoke", "dept-a", "doc-revoke");
        createDecryptGrant("pg-revoke", "USER", "u-revoke", "DOCUMENT", "ALLOW", 10);

        mockMvc.perform(post("/api/authorization/decrypt-download-hits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hitRequest("u-revoke", "dept-a", "doc-revoke", "trace-before-revoke")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hit").value(true));

        mockMvc.perform(post("/api/authorization/decrypt-download-grants/pg-revoke/revocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operator_id":"security-admin","trace_id":"trace-revoke"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grant_status").value("REVOKED"));

        mockMvc.perform(post("/api/authorization/decrypt-download-hits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hitRequest("u-revoke", "dept-a", "doc-revoke", "trace-after-revoke")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hit").value(false))
                .andExpect(jsonPath("$.reason_list[0]").value("NO_DECRYPT_DOWNLOAD_GRANT"));

        mockMvc.perform(post("/api/authorization/decrypt-download-grants/pg-revoke/recoveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operator_id":"security-admin","trace_id":"trace-recover"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grant_status").value("ACTIVE"));

        assertRowCount("ia_identity_audit", "event_type = 'PERMISSION_REVOKED' and trace_id = 'trace-revoke'", 1);
        assertRowCount("ia_identity_audit", "event_type = 'PERMISSION_RECOVERED' and trace_id = 'trace-recover'", 1);
    }

    @Test
    void unifiedAuthorizationDecisionEndpointReturnsReusableContractForBusinessConsumers() throws Exception {
        seedUserOrgAndDocumentScope("u-unified", "dept-unified", "doc-unified");
        createFunctionGrant("pg-contract-approve", "u-unified", "CONTRACT:APPROVE", "CONTRACT", "ALLOW", 10);
        createFunctionGrant("pg-workflow-handle", "u-unified", "WORKFLOW_TASK:HANDLE", "WORKFLOW_TASK", "ALLOW", 10);
        createFunctionGrant("pg-document-read", "u-unified", "DOCUMENT:READ", "DOCUMENT", "ALLOW", 10);
        createDataScopeForResource("ds-contract-unified", "u-unified", "CONTRACT", "USER_LIST", "contract-001");
        createDataScopeForResource("ds-workflow-unified", "u-unified", "WORKFLOW_TASK", "USER_LIST", "task-001");

        mockMvc.perform(post("/api/authorization/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionRequest("u-unified", "dept-unified", "CONTRACT:APPROVE", "CONTRACT", "contract-001", "trace-contract-decision")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision_result").value("ALLOW"))
                .andExpect(jsonPath("$.reason_list[0]").value("AUTHORIZATION_ALLOWED"))
                .andExpect(jsonPath("$.matched_permission_list[0].permission_grant_id").value("pg-contract-approve"))
                .andExpect(jsonPath("$.data_scope_hit[0].data_scope_id").value("ds-contract-unified"));

        mockMvc.perform(post("/api/authorization/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionRequest("u-unified", "dept-unified", "WORKFLOW_TASK:HANDLE", "WORKFLOW_TASK", "task-001", "trace-workflow-decision")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision_result").value("ALLOW"))
                .andExpect(jsonPath("$.matched_permission_list[0].permission_grant_id").value("pg-workflow-handle"));

        mockMvc.perform(post("/api/authorization/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionRequest("u-unified", "dept-unified", "DOCUMENT:READ", "DOCUMENT", "doc-unified", "trace-document-decision")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision_result").value("ALLOW"))
                .andExpect(jsonPath("$.matched_permission_list[0].permission_grant_id").value("pg-document-read"));

        assertRowCount("ia_authorization_decision", "request_trace_id in ('trace-contract-decision','trace-workflow-decision','trace-document-decision') and decision_result = 'ALLOW'", 3);
        assertRowCount("ia_authorization_hit_result", "hit_type = 'PERMISSION_GRANT' and hit_ref_id in ('pg-contract-approve','pg-workflow-handle','pg-document-read')", 3);
    }

    @Test
    void ruleDataScopeDecryptDownloadRecordsOrgRuleEvidenceAndAuditChain() throws Exception {
        createUser("u-rule-manager");
        createOrgUnit("org-root", null, "ORG");
        createOrgUnit("dept-rule", "org-root", "DEPARTMENT", "u-rule-manager");
        addMembership("m-u-rule-manager", "u-rule-manager", "dept-rule");
        createOrgRuleVersion("rule-document-owner", "rulever-document-owner", "dept-rule");
        createDataScopeForResource("ds-rule-document", "u-rule-manager", "DOCUMENT", "RULE", "rulever-document-owner");
        createDecryptGrant("pg-rule-decrypt", "USER", "u-rule-manager", "DOCUMENT", "ALLOW", 10);

        String decisionId = mockMvc.perform(post("/api/authorization/decrypt-download-hits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hitRequest("u-rule-manager", "dept-rule", "doc-rule", "trace-rule-decrypt")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hit").value(true))
                .andExpect(jsonPath("$.data_scope_hit[0].scope_type").value("RULE"))
                .andExpect(jsonPath("$.org_rule_evidence_list[0].frozen_ref_id").value("rulever-document-owner"))
                .andExpect(jsonPath("$.org_rule_evidence_list[0].resolution_record_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String savedDecisionId = jdbcTemplate.queryForObject("select decision_id from ia_authorization_decision where request_trace_id = 'trace-rule-decrypt'", String.class);
        org.assertj.core.api.Assertions.assertThat(decisionId).contains(savedDecisionId);
        assertRowCount("ia_authorization_hit_result", "decision_id = '" + savedDecisionId + "' and hit_type = 'ORG_RULE' and frozen_ref_id = 'rulever-document-owner' and resolution_record_id is not null", 1);
        assertRowCount("ia_org_rule_resolution_record", "org_rule_version_id = 'rulever-document-owner' and request_trace_id = 'trace-rule-decrypt'", 1);
        assertRowCount("ia_identity_audit", "event_type = 'DECRYPT_DOWNLOAD_HIT' and target_resource_id = '" + savedDecisionId + "' and actor_user_id = 'u-rule-manager' and trace_id = 'trace-rule-decrypt'", 1);

        mockMvc.perform(get("/api/authorization/decisions/{decisionId}", savedDecisionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision_detail.subject_ref.user_id").value("u-rule-manager"))
                .andExpect(jsonPath("$.decision_detail.subject_ref.org_id").value("org-root"))
                .andExpect(jsonPath("$.decision_detail.subject_ref.org_unit_id").value("dept-rule"))
                .andExpect(jsonPath("$.decision_detail.authorization_hit_list[?(@.hit_type == 'PERMISSION_GRANT')].hit_ref_id").value("pg-rule-decrypt"))
                .andExpect(jsonPath("$.decision_detail.authorization_hit_list[?(@.hit_type == 'DATA_SCOPE')].hit_ref_id").value("ds-rule-document"))
                .andExpect(jsonPath("$.decision_detail.authorization_hit_list[?(@.hit_type == 'ORG_RULE')].frozen_ref_id").value("rulever-document-owner"));
    }

    private void seedUserOrgAndDocumentScope(String userId, String orgUnitId, String documentId) throws Exception {
        createUser(userId);
        createOrgUnit("org-root", null, "ORG");
        createOrgUnit(orgUnitId, "org-root", "DEPARTMENT");
        addMembership("m-" + userId, userId, orgUnitId);
        createDataScope("ds-" + documentId, userId, documentId);
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
        createOrgUnit(orgUnitId, parentId, type, null);
    }

    private void createOrgUnit(String orgUnitId, String parentId, String type, String managerUserId) throws Exception {
        mockMvc.perform(post("/api/org-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"org_unit_id":"%s","parent_org_unit_id":%s,"org_unit_code":"%s","org_unit_name":"%s","org_unit_type":"%s","manager_user_id":%s}
                                """.formatted(orgUnitId, jsonOrNull(parentId), orgUnitId, orgUnitId, type, jsonOrNull(managerUserId))))
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

    private void createDataScope(String dataScopeId, String userId, String documentId) throws Exception {
        createDataScopeForResource(dataScopeId, userId, "DOCUMENT", "USER_LIST", documentId);
    }

    private void createDataScopeForResource(String dataScopeId, String userId, String resourceType, String scopeType, String scopeRef) throws Exception {
        mockMvc.perform(post("/api/data-scopes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data_scope_id":"%s","subject_type":"USER","subject_id":"%s","resource_type":"%s","scope_type":"%s","scope_ref":"%s","effect_mode":"ALLOW","priority_no":10}
                                """.formatted(dataScopeId, userId, resourceType, scopeType, scopeRef)))
                .andExpect(status().isCreated());
    }

    private void createFunctionGrant(String grantId, String userId, String permissionCode, String resourceType, String effect, int priority) throws Exception {
        mockMvc.perform(post("/api/permission-grants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permission_grant_id":"%s","grant_target_type":"USER","grant_target_id":"%s","permission_type":"FUNCTION","permission_code":"%s","resource_type":"%s","effect_mode":"%s","priority_no":%d,"trace_id":"trace-%s"}
                                """.formatted(grantId, userId, permissionCode, resourceType, effect, priority, grantId)))
                .andExpect(status().isCreated());
    }

    private void createOrgRuleVersion(String ruleId, String versionId, String candidateOrgUnitId) throws Exception {
        mockMvc.perform(post("/api/org-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"org_rule_id":"%s","rule_code":"%s","rule_name":"%s","rule_type":"MANAGER_OF_ORG_UNIT","rule_scope_ref":"%s"}
                                """.formatted(ruleId, ruleId, ruleId, candidateOrgUnitId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/org-rule-versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"org_rule_id":"%s","org_rule_version_id":"%s"}
                                """.formatted(ruleId, versionId)))
                .andExpect(status().isCreated());
    }

    private void createDecryptGrant(String grantId, String targetType, String targetId, String resourceType, String effect, int priority) throws Exception {
        mockMvc.perform(post("/api/authorization/decrypt-download-grants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permission_grant_id":"%s","grant_target_type":"%s","grant_target_id":"%s","resource_type":"%s","effect_mode":"%s","priority_no":%d,"trace_id":"trace-%s"}
                                """.formatted(grantId, targetType, targetId, resourceType, effect, priority, grantId)))
                .andExpect(status().isCreated());
    }

    private String hitRequest(String userId, String orgUnitId, String documentId, String traceId) {
        return """
                {"user_id":"%s","active_org_id":"org-root","active_org_unit_id":"%s","document_id":"%s","trace_id":"%s"}
                """.formatted(userId, orgUnitId, documentId, traceId);
    }

    private String decisionRequest(String userId, String orgUnitId, String actionCode, String resourceType, String resourceId, String traceId) {
        return """
                {"subject_ref":{"user_id":"%s","org_id":"org-root","org_unit_id":"%s"},"action_code":"%s","resource_type":"%s","resource_ref":{"resource_id":"%s"},"trace_id":"%s"}
                """.formatted(userId, orgUnitId, actionCode, resourceType, resourceId, traceId);
    }

    private String jsonOrNull(String value) {
        return value == null ? "null" : "\"" + value + "\"";
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

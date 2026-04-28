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
class IdentityAccessOrgRolePermissionTests {

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
    void organizationTreePersistsMembersAndPrimaryPartTimeDepartments() throws Exception {
        createUser("u-org-001", "org-user", "组织用户");

        createOrgUnit("org-root", null, "ROOT", "星邦集团", "ORG", "u-org-001");
        createOrgUnit("dept-sales", "org-root", "SALES", "销售部", "DEPARTMENT", "u-org-001");
        createOrgUnit("dept-legal", "org-root", "LEGAL", "法务部", "DEPARTMENT", null);
        addMembership("m-primary", "u-org-001", "dept-sales", "PRIMARY", true);
        addMembership("m-part", "u-org-001", "dept-legal", "PART_TIME", false);

        mockMvc.perform(get("/api/org-units/tree").queryParam("org_id", "org-root"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item_list[0].org_unit_id").value("org-root"))
                .andExpect(jsonPath("$.item_list[0].org_path").value("/org-root/"))
                .andExpect(jsonPath("$.item_list[2].org_path").value("/org-root/dept-sales/"));

        mockMvc.perform(get("/api/org-memberships").queryParam("user_id", "u-org-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primary_department.org_unit_id").value("dept-sales"))
                .andExpect(jsonPath("$.part_time_departments[0].org_unit_id").value("dept-legal"));

        assertTableExists("ia_org_unit");
        assertTableExists("ia_org_membership");
        assertRowCount("ia_org_unit", "org_unit_id = 'dept-sales' and org_path = '/org-root/dept-sales/'", 1);
        assertRowCount("ia_org_membership", "user_id = 'u-org-001' and is_primary_department = true", 1);
    }

    @Test
    void roleGrantControlsVisibleMenusAndExplicitFunctionDenyWins() throws Exception {
        createUser("u-perm-001", "perm-user", "权限用户");
        createOrgUnit("org-root", null, "ROOT", "星邦集团", "ORG", null);
        addMembership("m-perm", "u-perm-001", "org-root", "PRIMARY", true);
        createRole("role-contract-admin", "CONTRACT_ADMIN", "合同管理员");

        mockMvc.perform(post("/api/role-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assignment_id": "ra-001",
                                  "role_id": "role-contract-admin",
                                  "subject_type": "USER",
                                  "subject_id": "u-perm-001",
                                  "grant_org_id": "org-root",
                                  "trace_id": "trace-role-grant"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignment_status").value("ACTIVE"));

        grantPermission("pg-menu-contract", "ROLE", "role-contract-admin", "MENU", "MENU_CONTRACT", "ALLOW", 10);
        grantPermission("pg-fn-approve", "ROLE", "role-contract-admin", "FUNCTION", "CONTRACT_APPROVE", "ALLOW", 10);
        grantPermission("pg-fn-deny", "USER", "u-perm-001", "FUNCTION", "CONTRACT_APPROVE", "DENY", 1);

        mockMvc.perform(get("/api/menus/visible")
                        .queryParam("user_id", "u-perm-001")
                        .queryParam("org_id", "org-root"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menu_codes[0]").value("MENU_CONTRACT"));

        mockMvc.perform(get("/api/permissions/function-check")
                        .queryParam("user_id", "u-perm-001")
                        .queryParam("permission_code", "CONTRACT_APPROVE")
                        .queryParam("trace_id", "trace-function-deny"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.effect_mode").value("DENY"));

        assertTableExists("ia_role");
        assertTableExists("ia_role_assignment");
        assertTableExists("ia_permission_grant");
        assertRowCount("ia_role_assignment", "role_id = 'role-contract-admin' and subject_id = 'u-perm-001'", 1);
        assertRowCount("ia_identity_audit", "event_type = 'ROLE_GRANTED' and trace_id = 'trace-role-grant'", 1);
        assertRowCount("ia_identity_audit", "event_type = 'AUTHZ_DENIED' and trace_id = 'trace-function-deny'", 1);
    }

    @Test
    void dataScopePredicateUsesControlledConditionsAndPersistsDecisionHits() throws Exception {
        createUser("u-scope-001", "scope-user", "范围用户");
        createOrgUnit("org-root", null, "ROOT", "星邦集团", "ORG", null);
        createOrgUnit("dept-scope", "org-root", "SCOPE", "范围部门", "DEPARTMENT", null);
        createRole("role-scope", "SCOPE_VIEWER", "范围查看员");
        addMembership("m-scope", "u-scope-001", "dept-scope", "PRIMARY", true);
        assignRole("ra-scope", "role-scope", "USER", "u-scope-001");

        createDataScope("ds-self", "USER", "u-scope-001", "CONTRACT", "SELF", "u-scope-001", "ALLOW", 10);
        createDataScope("ds-org", "ROLE", "role-scope", "CONTRACT", "ORG", "org-root", "ALLOW", 20);
        createDataScope("ds-unit", "ROLE", "role-scope", "CONTRACT", "ORG_UNIT", "dept-scope", "ALLOW", 30);
        createDataScope("ds-subtree", "ROLE", "role-scope", "CONTRACT", "ORG_SUBTREE", "dept-scope", "ALLOW", 40);
        createDataScope("ds-users", "USER", "u-scope-001", "CONTRACT", "USER_LIST", "u-scope-001,u-other", "ALLOW", 50);

        mockMvc.perform(post("/api/data-scope-predicates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "user_id": "u-scope-001",
                                  "active_org_id": "org-root",
                                  "active_org_unit_id": "dept-scope",
                                  "resource_type": "CONTRACT",
                                  "action_code": "CONTRACT_LIST",
                                  "trace_id": "trace-data-scope"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource_type").value("CONTRACT"))
                .andExpect(jsonPath("$.allow_predicates[0].operator").value("EQ"))
                .andExpect(jsonPath("$.allow_predicates[1].field").value("tenant_or_org_id"))
                .andExpect(jsonPath("$.allow_predicates[3].operator").value("STARTS_WITH_PATH"))
                .andExpect(jsonPath("$.allow_predicates[4].operator").value("IN"));

        assertTableExists("ia_data_scope");
        assertTableExists("ia_authorization_decision");
        assertTableExists("ia_authorization_hit_result");
        assertRowCount("ia_authorization_hit_result", "hit_type = 'DATA_SCOPE'", 5);
        assertRowCount("ia_identity_audit", "event_type = 'DATA_SCOPE_HIT' and trace_id = 'trace-data-scope'", 1);
    }

    @Test
    void ruleDataScopeResolvesFrozenOrgRuleAndPersistsHitReferences() throws Exception {
        createUser("u-rule-user", "rule-user", "规则范围用户");
        createUser("u-rule-manager", "rule-manager", "规则命中负责人");
        createOrgUnit("org-root", null, "ROOT", "星邦集团", "ORG", null);
        createOrgUnit("dept-rule-scope", "org-root", "RULE_SCOPE", "规则范围部门", "DEPARTMENT", "u-rule-manager");
        addMembership("m-rule-user", "u-rule-user", "dept-rule-scope", "PRIMARY", true);

        createManagerRuleAndVersion("rule-data-scope", "rulever-data-scope-v1");
        createDataScope("ds-rule", "USER", "u-rule-user", "CONTRACT", "RULE", "rulever-data-scope-v1", "ALLOW", 10);

        mockMvc.perform(post("/api/data-scope-predicates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "user_id": "u-rule-user",
                                  "active_org_id": "org-root",
                                  "active_org_unit_id": "dept-rule-scope",
                                  "resource_type": "CONTRACT",
                                  "action_code": "CONTRACT_LIST",
                                  "trace_id": "trace-rule-data-scope"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effect").value("ALLOW"))
                .andExpect(jsonPath("$.allow_predicates[0].scope_type").value("RULE"))
                .andExpect(jsonPath("$.allow_predicates[0].operator").value("IN"))
                .andExpect(jsonPath("$.allow_predicates[0].values[0]").value("u-rule-manager"));

        assertRowCount("ia_org_rule_resolution_record", "org_rule_version_id = 'rulever-data-scope-v1' and request_trace_id = 'trace-rule-data-scope'", 1);
        assertRowCount("ia_authorization_hit_result", "hit_type = 'ORG_RULE' and frozen_ref_id = 'rulever-data-scope-v1' and resolution_record_id is not null", 1);
        assertRowCount("ia_authorization_hit_result", "hit_type = 'DATA_SCOPE' and hit_ref_id = 'ds-rule'", 1);
    }

    @Test
    void explicitDenyDataScopeIsPreservedWithAllowAndCannotBeOverridden() throws Exception {
        createUser("u-deny-mixed", "deny-mixed", "混合拒绝用户");
        createOrgUnit("org-root", null, "ROOT", "星邦集团", "ORG", null);
        createOrgUnit("dept-deny", "org-root", "DENY", "拒绝部门", "DEPARTMENT", null);

        createDataScope("ds-mixed-allow", "USER", "u-deny-mixed", "CONTRACT", "ORG", "org-root", "ALLOW", 20);
        createDataScope("ds-mixed-deny", "USER", "u-deny-mixed", "CONTRACT", "ORG_UNIT", "dept-deny", "DENY", 1);

        mockMvc.perform(post("/api/data-scope-predicates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "user_id": "u-deny-mixed",
                                  "active_org_id": "org-root",
                                  "active_org_unit_id": "dept-deny",
                                  "resource_type": "CONTRACT",
                                  "action_code": "CONTRACT_LIST",
                                  "trace_id": "trace-deny-mixed"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effect").value("CONDITIONAL"))
                .andExpect(jsonPath("$.allow_predicates[0].scope_type").value("ORG"))
                .andExpect(jsonPath("$.deny_predicates[0].scope_type").value("ORG_UNIT"));

        assertRowCount("ia_authorization_decision", "request_trace_id = 'trace-deny-mixed' and decision_result = 'CONDITIONAL'", 1);
        assertRowCount("ia_authorization_hit_result", "hit_ref_id = 'ds-mixed-deny' and hit_result = 'DENY'", 1);
    }

    @Test
    void onlyExplicitDenyDataScopeRejectsWithoutFullVisibleAllowPredicate() throws Exception {
        createUser("u-deny-only", "deny-only", "仅拒绝用户");
        createOrgUnit("org-root", null, "ROOT", "星邦集团", "ORG", null);
        createOrgUnit("dept-deny-only", "org-root", "DENY_ONLY", "仅拒绝部门", "DEPARTMENT", null);

        createDataScope("ds-deny-only", "USER", "u-deny-only", "CONTRACT", "ORG_UNIT", "dept-deny-only", "DENY", 1);

        mockMvc.perform(post("/api/data-scope-predicates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "user_id": "u-deny-only",
                                  "active_org_id": "org-root",
                                  "active_org_unit_id": "dept-deny-only",
                                  "resource_type": "CONTRACT",
                                  "action_code": "CONTRACT_LIST",
                                  "trace_id": "trace-deny-only"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effect").value("DENY"))
                .andExpect(jsonPath("$.allow_predicates").isEmpty())
                .andExpect(jsonPath("$.deny_predicates[0].scope_type").value("ORG_UNIT"));

        assertRowCount("ia_authorization_decision", "request_trace_id = 'trace-deny-only' and decision_result = 'DENY' and decision_reason_code = 'EXPLICIT_DENY_NO_ALLOW'", 1);
        assertRowCount("ia_authorization_hit_result", "hit_ref_id = 'ds-deny-only' and hit_result = 'DENY'", 1);
        assertRowCount("ia_identity_audit", "event_type = 'AUTHZ_DENIED' and trace_id = 'trace-deny-only'", 1);
    }

    @Test
    void orgRuleResolutionFreezesRuleVersionAndRecordsReplayEvidence() throws Exception {
        createUser("u-manager-001", "manager", "部门负责人");
        createOrgUnit("org-root", null, "ROOT", "星邦集团", "ORG", null);
        createOrgUnit("dept-rule", "org-root", "RULE", "规则部门", "DEPARTMENT", "u-manager-001");

        mockMvc.perform(post("/api/org-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "org_rule_id": "rule-manager",
                                  "rule_code": "DEPT_MANAGER",
                                  "rule_name": "部门负责人",
                                  "rule_type": "MANAGER_OF_ORG_UNIT",
                                  "rule_scope_type": "ORG",
                                  "rule_scope_ref": "org-root",
                                  "resolver_config": {"subject_anchor":{"type":"EXPLICIT_ORG_UNIT"}},
                                  "fallback_policy": "EMPTY_SET"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/org-rule-versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "org_rule_id": "rule-manager",
                                  "org_rule_version_id": "rulever-manager-v1",
                                  "version_no": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version_status").value("EFFECTIVE"));

        mockMvc.perform(post("/api/org-rule-resolutions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "org_rule_version_id": "rulever-manager-v1",
                                  "resolution_scene": "AUTHORIZATION",
                                  "active_org_id": "org-root",
                                  "candidate_org_unit_id": "dept-rule",
                                  "trace_id": "trace-rule-resolution"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.org_rule_version_id").value("rulever-manager-v1"))
                .andExpect(jsonPath("$.resolved_subject_list[0].user_id").value("u-manager-001"))
                .andExpect(jsonPath("$.evidence_list[0].source_ref").value("dept-rule"));

        assertTableExists("ia_org_rule");
        assertTableExists("ia_org_rule_version");
        assertTableExists("ia_org_rule_resolution_record");
        assertRowCount("ia_org_rule_version", "org_rule_version_id = 'rulever-manager-v1' and version_status = 'EFFECTIVE'", 1);
        assertRowCount("ia_org_rule_resolution_record", "org_rule_version_id = 'rulever-manager-v1' and request_trace_id = 'trace-rule-resolution'", 1);
        assertRowCount("ia_identity_audit", "event_type = 'ORG_RULE_RESOLVED' and trace_id = 'trace-rule-resolution'", 1);
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

    private void createOrgUnit(String orgUnitId, String parentId, String code, String name, String type, String managerUserId) throws Exception {
        mockMvc.perform(post("/api/org-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "org_unit_id": "%s",
                                  "parent_org_unit_id": %s,
                                  "org_unit_code": "%s",
                                  "org_unit_name": "%s",
                                  "org_unit_type": "%s",
                                  "manager_user_id": %s
                                }
                                """.formatted(orgUnitId, jsonOrNull(parentId), code, name, type, jsonOrNull(managerUserId))))
                .andExpect(status().isCreated());
    }

    private void addMembership(String membershipId, String userId, String orgUnitId, String type, boolean primary) throws Exception {
        mockMvc.perform(post("/api/org-memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "membership_id": "%s",
                                  "user_id": "%s",
                                  "org_unit_id": "%s",
                                  "membership_type": "%s",
                                  "is_primary_department": %s
                                }
                                """.formatted(membershipId, userId, orgUnitId, type, primary)))
                .andExpect(status().isCreated());
    }

    private void createRole(String roleId, String roleCode, String roleName) throws Exception {
        mockMvc.perform(post("/api/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role_id": "%s",
                                  "role_code": "%s",
                                  "role_name": "%s"
                                }
                                """.formatted(roleId, roleCode, roleName)))
                .andExpect(status().isCreated());
    }

    private void assignRole(String assignmentId, String roleId, String subjectType, String subjectId) throws Exception {
        mockMvc.perform(post("/api/role-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assignment_id": "%s",
                                  "role_id": "%s",
                                  "subject_type": "%s",
                                  "subject_id": "%s",
                                  "grant_org_id": "org-root"
                                }
                                """.formatted(assignmentId, roleId, subjectType, subjectId)))
                .andExpect(status().isCreated());
    }

    private void grantPermission(String grantId, String targetType, String targetId, String permissionType, String code, String effect, int priority) throws Exception {
        mockMvc.perform(post("/api/permission-grants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permission_grant_id": "%s",
                                  "grant_target_type": "%s",
                                  "grant_target_id": "%s",
                                  "permission_type": "%s",
                                  "permission_code": "%s",
                                  "effect_mode": "%s",
                                  "priority_no": %d
                                }
                                """.formatted(grantId, targetType, targetId, permissionType, code, effect, priority)))
                .andExpect(status().isCreated());
    }

    private void createDataScope(String dataScopeId, String subjectType, String subjectId, String resourceType, String scopeType, String scopeRef, String effect, int priority) throws Exception {
        mockMvc.perform(post("/api/data-scopes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "data_scope_id": "%s",
                                  "subject_type": "%s",
                                  "subject_id": "%s",
                                  "resource_type": "%s",
                                  "scope_type": "%s",
                                  "scope_ref": "%s",
                                  "effect_mode": "%s",
                                  "priority_no": %d
                                }
                                """.formatted(dataScopeId, subjectType, subjectId, resourceType, scopeType, scopeRef, effect, priority)))
                .andExpect(status().isCreated());
    }

    private void createManagerRuleAndVersion(String ruleId, String versionId) throws Exception {
        mockMvc.perform(post("/api/org-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "org_rule_id": "%s",
                                  "rule_code": "%s",
                                  "rule_name": "部门负责人规则",
                                  "rule_type": "MANAGER_OF_ORG_UNIT",
                                  "rule_scope_type": "ORG",
                                  "rule_scope_ref": "org-root",
                                  "resolver_config": {"subject_anchor":{"type":"ACTIVE_ORG_UNIT"}},
                                  "fallback_policy": "EMPTY_SET"
                                }
                                """.formatted(ruleId, ruleId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/org-rule-versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "org_rule_id": "%s",
                                  "org_rule_version_id": "%s",
                                  "version_no": 1
                                }
                                """.formatted(ruleId, versionId)))
                .andExpect(status().isCreated());
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

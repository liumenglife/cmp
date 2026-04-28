package com.cmp.platform.agentos;

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
class AgentOsQueryEngineKernelTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanAgentOsTables() {
        jdbcTemplate.update("DELETE FROM ao_agent_audit_event");
        jdbcTemplate.update("DELETE FROM ao_tool_invocation");
        jdbcTemplate.update("DELETE FROM ao_prompt_snapshot");
        jdbcTemplate.update("DELETE FROM ao_environment_event");
        jdbcTemplate.update("DELETE FROM ao_agent_result");
        jdbcTemplate.update("DELETE FROM ao_agent_run");
        jdbcTemplate.update("DELETE FROM ao_agent_task");
    }

    @Test
    void contractRiskTaskCompletesMinimalQueryEngineLoopAndPersistsEvidence() throws Exception {
        String response = mockMvc.perform(post("/api/agent-os/tasks")
                        .header("Idempotency-Key", "idem-agent-success-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractRiskRequest("trace-agent-success", "NORMAL")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.task_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.run_id").isNotEmpty())
                .andExpect(jsonPath("$.result_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String taskId = jsonString(response, "task_id");
        String runId = jsonString(response, "run_id");

        mockMvc.perform(get("/api/agent-os/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.latest_checkpoint.runtime_state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.latest_checkpoint.termination_reason").value("RESULT_CONTRACT_SATISFIED"))
                .andExpect(jsonPath("$.latest_checkpoint.trace_id").value("trace-agent-success"));

        mockMvc.perform(get("/api/agent-os/tasks/{taskId}/result", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.result_type").value("SUMMARY"))
                .andExpect(jsonPath("$.output_payload.risk_level").value("MEDIUM"))
                .andExpect(jsonPath("$.citation_list[0].object_id").value("ctr-agent-001"));

        mockMvc.perform(get("/api/agent-os/runs/{runId}/audit-view", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace_id").value("trace-agent-success"))
                .andExpect(jsonPath("$.checkpoint_summary.loop_count").value(1))
                .andExpect(jsonPath("$.event_list[?(@.action_type == 'STATE_CHECKPOINT')]").isArray());

        assertTableExists("ao_agent_task");
        assertTableExists("ao_agent_run");
        assertTableExists("ao_agent_result");
        assertTableExists("ao_prompt_snapshot");
        assertTableExists("ao_tool_invocation");
        assertTableExists("ao_agent_audit_event");
        assertRowCount("ao_agent_task", "task_id = '" + taskId + "' and task_status = 'SUCCEEDED' and trace_id = 'trace-agent-success'", 1);
        assertRowCount("ao_agent_run", "run_id = '" + runId + "' and run_status = 'SUCCEEDED' and loop_count = 1", 1);
        assertRowCount("ao_prompt_snapshot", "run_id = '" + runId + "' and platform_root_version = 'platform-root-v1' and context_token_estimate > 0", 1);
        assertRowCount("ao_tool_invocation", "run_id = '" + runId + "' and tool_type = 'MODEL' and invocation_status = 'SUCCEEDED'", 1);
        assertRowCount("ao_agent_audit_event", "object_id = '" + runId + "' and action_type = 'STATE_CHECKPOINT' and trace_id = 'trace-agent-success'", 1);
    }

    @Test
    void modelFailureMarksTaskRunAndResultFailedWithAudit() throws Exception {
        String response = mockMvc.perform(post("/api/agent-os/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractRiskRequest("trace-agent-model-fail", "MODEL_FAIL")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.task_status").value("FAILED"))
                .andReturn().getResponse().getContentAsString();

        String runId = jsonString(response, "run_id");
        assertRowCount("ao_agent_run", "run_id = '" + runId + "' and run_status = 'FAILED' and failure_code = 'MODEL_CALL_FAILED'", 1);
        assertRowCount("ao_agent_result", "run_id = '" + runId + "' and result_status = 'FAILED'", 1);
        assertRowCount("ao_agent_audit_event", "object_id = '" + runId + "' and action_type = 'MODEL_CALL_FAILED' and result_status = 'FAILED'", 1);
    }

    @Test
    void providerBudgetRejectionBlocksModelCall() throws Exception {
        String response = mockMvc.perform(post("/api/agent-os/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractRiskRequest("trace-agent-budget", "BUDGET_REJECT")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.task_status").value("FAILED"))
                .andReturn().getResponse().getContentAsString();

        String runId = jsonString(response, "run_id");
        assertRowCount("ao_agent_run", "run_id = '" + runId + "' and failure_code = 'PROVIDER_BUDGET_EXCEEDED'", 1);
        assertRowCount("ao_tool_invocation", "run_id = '" + runId + "' and tool_type = 'MODEL'", 0);
        assertRowCount("ao_agent_audit_event", "object_id = '" + runId + "' and action_type = 'PROVIDER_BUDGET_REJECTED'", 1);
    }

    @Test
    void externalCancelKeepsTaskAndRunStatusConsistent() throws Exception {
        String response = mockMvc.perform(post("/api/agent-os/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractRiskRequest("trace-agent-cancel", "MANUAL_START")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.task_status").value("ACCEPTED"))
                .andReturn().getResponse().getContentAsString();

        String taskId = jsonString(response, "task_id");
        String runId = jsonString(response, "run_id");

        mockMvc.perform(post("/api/agent-os/runs/{runId}/cancel", runId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_id\":\"ops-001\",\"trace_id\":\"trace-agent-cancel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run_status").value("CANCELLED"));

        assertRowCount("ao_agent_task", "task_id = '" + taskId + "' and task_status = 'CANCELLED'", 1);
        assertRowCount("ao_agent_run", "run_id = '" + runId + "' and run_status = 'CANCELLED' and failure_code = 'EXTERNAL_CANCELLED'", 1);
        assertRowCount("ao_agent_audit_event", "object_id = '" + runId + "' and action_type = 'RUN_CANCELLED' and trace_id = 'trace-agent-cancel'", 1);
    }

    @Test
    void maxLoopTerminatesAfterCheckpointWithoutSuccessfulResult() throws Exception {
        String response = mockMvc.perform(post("/api/agent-os/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractRiskRequest("trace-agent-max-loop", "NEED_MORE")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.task_status").value("FAILED"))
                .andReturn().getResponse().getContentAsString();

        String runId = jsonString(response, "run_id");
        assertRowCount("ao_agent_run", "run_id = '" + runId + "' and loop_count = 1 and failure_code = 'MAX_LOOP_EXCEEDED'", 1);
        assertRowCount("ao_agent_audit_event", "object_id = '" + runId + "' and action_type = 'STATE_CHECKPOINT'", 1);
    }

    @Test
    void auditMissingBlocksResultRelease() throws Exception {
        String response = mockMvc.perform(post("/api/agent-os/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractRiskRequest("trace-agent-audit-missing", "AUDIT_MISSING")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.task_status").value("FAILED"))
                .andReturn().getResponse().getContentAsString();

        String runId = jsonString(response, "run_id");
        assertRowCount("ao_agent_run", "run_id = '" + runId + "' and failure_code = 'AUDIT_REQUIRED'", 1);
        assertRowCount("ao_agent_result", "run_id = '" + runId + "' and result_status = 'FAILED' and output_text_summary like '%审计缺失%'", 1);
    }

    @Test
    void sameIdempotencyKeyReturnsExistingTaskAndDifferentPayloadConflicts() throws Exception {
        String first = mockMvc.perform(post("/api/agent-os/tasks")
                        .header("Idempotency-Key", "idem-agent-idempotent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractRiskRequest("trace-agent-idem", "NORMAL")))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/api/agent-os/tasks")
                        .header("Idempotency-Key", "idem-agent-idempotent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractRiskRequest("trace-agent-idem", "NORMAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(jsonString(second, "task_id")).isEqualTo(jsonString(first, "task_id"));

        mockMvc.perform(post("/api/agent-os/tasks")
                        .header("Idempotency-Key", "idem-agent-idempotent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractRiskRequest("trace-agent-idem-changed", "NORMAL")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("40905"))
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void environmentEventCanBeCreatedQueriedAndLinkedToAgentTask() throws Exception {
        String eventResponse = mockMvc.perform(post("/api/agent-os/environment-events")
                        .header("Idempotency-Key", "idem-env-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"event_type":"TOOL_TIMEOUT","event_source":"contract-service","severity":"HIGH","event_payload":{"tool":"contract-reader"},"agent_processing_required":true,"trace_id":"trace-env-001"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processing_status").value("TURNED_INTO_TASK"))
                .andExpect(jsonPath("$.derived_task_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String eventId = jsonString(eventResponse, "event_id");
        mockMvc.perform(get("/api/agent-os/environment-events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_type").value("TOOL_TIMEOUT"))
                .andExpect(jsonPath("$.derived_task_id").isNotEmpty());

        String derivedTaskId = jsonString(eventResponse, "derived_task_id");
        String derivedRunId = jdbcTemplate.queryForObject(
                "select current_run_id from ao_agent_task where task_id = ?", String.class, derivedTaskId);

        mockMvc.perform(get("/api/agent-os/runs/{runId}", derivedRunId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task_id").value(derivedTaskId))
                .andExpect(jsonPath("$.run_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.latest_checkpoint.runtime_state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.latest_checkpoint.trace_id").value("trace-env-001"));

        String derivedResult = mockMvc.perform(get("/api/agent-os/tasks/{taskId}/result", derivedTaskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run_id").value(derivedRunId))
                .andExpect(jsonPath("$.result_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.output_payload.source_event_type").value("TOOL_TIMEOUT"))
                .andReturn().getResponse().getContentAsString();
        String derivedResultId = jsonString(derivedResult, "result_id");

        mockMvc.perform(get("/api/agent-os/results/{resultId}", derivedResultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task_id").value(derivedTaskId))
                .andExpect(jsonPath("$.run_id").value(derivedRunId))
                .andExpect(jsonPath("$.result_status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/agent-os/runs/{runId}/audit-view", derivedRunId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace_id").value("trace-env-001"))
                .andExpect(jsonPath("$.checkpoint_summary.loop_count").value(1))
                .andExpect(jsonPath("$.event_list[?(@.action_type == 'PROMPT_SNAPSHOT_CREATED')]").isArray())
                .andExpect(jsonPath("$.event_list[?(@.action_type == 'STATE_CHECKPOINT')]").isArray())
                .andExpect(jsonPath("$.event_list[?(@.action_type == 'RESULT_RELEASED')]").isArray());

        assertRowCount("ao_environment_event", "event_id = '" + eventId + "' and processing_status = 'TURNED_INTO_TASK'", 1);
        assertRowCount("ao_agent_task", "task_id = '" + derivedTaskId + "' and task_status = 'SUCCEEDED' and final_result_id = '" + derivedResultId + "'", 1);
        assertRowCount("ao_agent_run", "run_id = '" + derivedRunId + "' and run_status = 'SUCCEEDED' and loop_count = 1", 1);
        assertRowCount("ao_agent_result", "result_id = '" + derivedResultId + "' and task_id = '" + derivedTaskId + "' and run_id = '" + derivedRunId + "'", 1);
        assertRowCount("ao_prompt_snapshot", "run_id = '" + derivedRunId + "' and platform_root_version = 'platform-root-v1'", 1);
        assertRowCount("ao_agent_audit_event", "object_id = '" + derivedRunId + "' and action_type = 'STATE_CHECKPOINT' and trace_id = 'trace-env-001'", 1);
    }

    private String contractRiskRequest(String traceId, String scenario) {
        return """
                {
                  "task_type": "RISK_ANALYSIS",
                  "task_source": "BUSINESS_MODULE",
                  "requester_type": "USER",
                  "requester_id": "u-agent-001",
                  "specialized_agent_code": "CONTRACT_REVIEW_AGENT",
                  "input_context": {
                    "business_module": "CONTRACT",
                    "object_type": "CONTRACT",
                    "object_id": "ctr-agent-001",
                    "session_scope": "CONTRACT_DETAIL"
                  },
                  "input_payload": {
                    "question": "请给出合同风险提示",
                    "document_id_list": ["doc-agent-001"],
                    "scenario": "%s"
                  },
                  "max_loop_count": 1,
                  "trace_id": "%s"
                }
                """.formatted(scenario, traceId);
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

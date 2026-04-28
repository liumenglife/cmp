create table ao_tool_definition (
    tool_name varchar(120) primary key,
    tool_family varchar(40) not null,
    capability_code varchar(80) not null,
    risk_level varchar(40) not null,
    side_effect_level varchar(40) not null,
    search_hint varchar(256) not null,
    schema_json json not null,
    definition_stability varchar(40) not null,
    cache_order_group varchar(40) not null,
    sandbox_policy_ref varchar(120) not null,
    offload_policy_ref varchar(120) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    key idx_ao_tool_definition_capability (capability_code, risk_level)
);

create table ao_tool_definition_snapshot (
    snapshot_ref varchar(80) primary key,
    run_id varchar(80) not null,
    tool_name varchar(120) not null,
    schema_snapshot_json json not null,
    sandbox_policy_ref varchar(120) not null,
    offload_policy_ref varchar(120) not null,
    created_at timestamp not null,
    key idx_ao_tool_snapshot_run (run_id, tool_name, created_at)
);

create table ao_tool_grant (
    grant_id varchar(80) primary key,
    run_id varchar(80) not null,
    tool_name varchar(120) not null,
    snapshot_ref varchar(80) not null,
    grant_status varchar(40) not null,
    grant_scope_json json not null,
    denied_reason varchar(512),
    created_at timestamp not null,
    key idx_ao_tool_grant_run (run_id, tool_name, grant_status)
);

create table ao_tool_result (
    tool_result_id varchar(80) primary key,
    tool_invocation_id varchar(80) not null,
    run_id varchar(80) not null,
    tool_name varchar(120) not null,
    result_status varchar(40) not null,
    result_class varchar(80) not null,
    output_summary text,
    output_artifact_ref varchar(256),
    failure_code varchar(80),
    failure_class varchar(80),
    retryable boolean not null,
    resource_usage_json json not null,
    created_at timestamp not null,
    key idx_ao_tool_result_run (run_id, tool_invocation_id, result_status)
);

create table ao_provider_usage (
    provider_usage_id varchar(80) primary key,
    run_id varchar(80) not null,
    tool_invocation_id varchar(80) not null,
    capability_code varchar(80) not null,
    provider_code varchar(80) not null,
    model_code varchar(80) not null,
    route_status varchar(40) not null,
    quota_status varchar(40) not null,
    rate_status varchar(40) not null,
    circuit_status varchar(40) not null,
    degrade_reason varchar(256),
    token_in int not null,
    token_out int not null,
    estimated_cost decimal(12, 6) not null,
    created_at timestamp not null,
    key idx_ao_provider_usage_run (run_id, provider_code, route_status)
);

create table ao_verification_report (
    verification_report_id varchar(80) primary key,
    run_id varchar(80) not null,
    verification_target varchar(256) not null,
    independent_context_sources_json json not null,
    check_items_json json not null,
    failure_evidence_json json not null,
    performance_baseline_json json not null,
    uncovered_risks_json json not null,
    conclusion varchar(40) not null,
    regression_baseline_entry varchar(256) not null,
    created_at timestamp not null,
    key idx_ao_verification_report_run (run_id, conclusion)
);

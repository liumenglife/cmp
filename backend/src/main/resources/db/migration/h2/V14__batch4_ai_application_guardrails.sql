create table ia_ai_application_job (
    ai_application_job_id varchar(80) primary key,
    application_type varchar(40) not null,
    contract_id varchar(120) not null,
    document_version_id varchar(80),
    job_status varchar(40) not null,
    context_assembly_job_id varchar(80),
    result_context_id varchar(80),
    agent_task_id varchar(80),
    agent_result_id varchar(80),
    idempotency_key varchar(160),
    scope_digest varchar(160) not null,
    failure_code varchar(80),
    failure_reason varchar(512),
    trace_id varchar(120),
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index uk_ai_job_idempotency on ia_ai_application_job(application_type, contract_id, idempotency_key);

create table ia_ai_context_envelope (
    result_context_id varchar(80) primary key,
    ai_application_job_id varchar(80) not null,
    context_assembly_job_id varchar(80) not null,
    application_type varchar(40) not null,
    contract_id varchar(120) not null,
    envelope_json clob not null,
    source_digest varchar(160) not null,
    budget_snapshot_json clob not null,
    degradation_action_json clob not null,
    guardrail_profile_code varchar(80) not null,
    assembled_at timestamp not null
);

create table ia_ai_application_result (
    result_id varchar(80) primary key,
    ai_application_job_id varchar(80) not null,
    agent_task_id varchar(80),
    agent_result_id varchar(80),
    application_type varchar(40) not null,
    result_status varchar(40) not null,
    structured_payload_json clob not null,
    citation_list_json clob not null,
    evidence_coverage_ratio decimal(8,4) not null,
    guardrail_decision varchar(40) not null,
    guardrail_failure_code varchar(80),
    confirmation_required_flag boolean not null,
    writeback_allowed_flag boolean not null,
    risk_flag_list_json clob not null,
    created_at timestamp not null
);

create table ia_protected_result_snapshot (
    protected_result_snapshot_id varchar(80) primary key,
    ai_application_job_id varchar(80) not null,
    result_id varchar(80),
    agent_task_id varchar(80),
    agent_result_id varchar(80),
    guardrail_decision varchar(40) not null,
    guardrail_failure_code varchar(80),
    confirmation_required_flag boolean not null,
    protected_payload_ref varchar(256) not null,
    protected_payload_json clob not null,
    expires_at timestamp not null,
    created_at timestamp not null
);

create table ia_ai_audit_event (
    audit_event_id varchar(80) primary key,
    ai_application_job_id varchar(80),
    action_type varchar(80) not null,
    actor_id varchar(80),
    result_status varchar(40) not null,
    trace_id varchar(120),
    occurred_at timestamp not null
);

create index idx_ai_audit_trace on ia_ai_audit_event(trace_id, occurred_at);

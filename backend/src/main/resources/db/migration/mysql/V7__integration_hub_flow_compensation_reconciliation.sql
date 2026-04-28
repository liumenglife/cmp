create table ih_outbound_attempt (
    attempt_id varchar(80) primary key,
    dispatch_id varchar(80) not null,
    attempt_no int not null,
    attempt_status varchar(40) not null,
    target_request_ref varchar(120),
    result_code varchar(80),
    result_message varchar(512),
    evidence_group_id varchar(80),
    attempted_at timestamp not null,
    created_at timestamp not null,
    unique key uk_ih_outbound_attempt (dispatch_id, attempt_no),
    key idx_ih_outbound_attempt_dispatch (dispatch_id, attempted_at)
);

create table ih_recovery_ticket (
    recovery_ticket_id varchar(80) primary key,
    resource_type varchar(80) not null,
    resource_id varchar(80) not null,
    failure_stage varchar(80) not null,
    ticket_round_no int not null,
    root_ticket_id varchar(80),
    diff_id varchar(80),
    ledger_entry_id varchar(80),
    result_evidence_group_id varchar(80),
    result_evidence_object_id varchar(80),
    last_audit_ref_id varchar(80),
    recovery_status varchar(40) not null,
    recovery_strategy varchar(80) not null,
    manual_owner_id varchar(80),
    root_cause_code varchar(80),
    root_cause_summary varchar(512),
    trace_id varchar(120) not null,
    last_retry_at timestamp null,
    resolved_at timestamp null,
    created_at timestamp not null,
    updated_at timestamp not null,
    unique key uk_ih_recovery_ticket (resource_type, resource_id, failure_stage, ticket_round_no),
    key idx_ih_recovery_status (recovery_status, manual_owner_id)
);

create table ih_reconciliation_task (
    reconciliation_task_id varchar(80) primary key,
    task_kind varchar(40) not null,
    system_name varchar(40) not null,
    scope_type varchar(80) not null,
    scope_key varchar(120) not null,
    window_start timestamp not null,
    window_end timestamp not null,
    task_status varchar(40) not null,
    baseline_version varchar(80) not null,
    trace_id varchar(120) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    key idx_ih_reconciliation_task (system_name, task_status, window_end)
);

create table ih_reconciliation_record (
    reconciliation_record_id varchar(80) primary key,
    reconciliation_task_id varchar(80) not null,
    reconciliation_subject_key varchar(200) not null,
    binding_id varchar(80),
    platform_object_id varchar(120),
    normalized_external_object_key varchar(160),
    platform_snapshot_ref varchar(256),
    external_snapshot_ref varchar(256),
    record_result varchar(40) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    key idx_ih_reconciliation_record_task (reconciliation_task_id, record_result),
    key idx_ih_reconciliation_subject (reconciliation_subject_key)
);

create table ih_reconciliation_diff (
    diff_id varchar(80) primary key,
    reconciliation_record_id varchar(80) not null,
    diff_identity_key varchar(200) not null,
    diff_type varchar(80) not null,
    severity varchar(40) not null,
    baseline_fingerprint varchar(120) not null,
    primary_evidence_group_id varchar(80),
    current_state varchar(80) not null,
    resolution_channel varchar(80) not null,
    recovery_ticket_id varchar(80),
    created_at timestamp not null,
    updated_at timestamp not null,
    unique key uk_ih_reconciliation_diff (diff_identity_key, baseline_fingerprint),
    key idx_ih_reconciliation_diff_record (reconciliation_record_id, current_state)
);

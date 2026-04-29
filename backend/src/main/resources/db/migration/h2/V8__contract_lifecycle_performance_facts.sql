create table cl_performance_record (
    performance_record_id varchar(80) primary key,
    contract_id varchar(80) not null,
    performance_status varchar(40) not null,
    progress_percent integer not null,
    risk_level varchar(40) not null,
    owner_user_id varchar(80),
    owner_org_unit_id varchar(80),
    open_node_count integer not null,
    overdue_node_count integer not null,
    latest_due_at varchar(40),
    summary_text varchar(512),
    latest_milestone_code varchar(80),
    last_evaluated_at varchar(40) not null,
    last_writeback_at varchar(40) not null
);

create unique index uk_cl_performance_record_contract on cl_performance_record(contract_id);
create index idx_cl_performance_record_status on cl_performance_record(performance_status, risk_level);

create table cl_performance_node (
    performance_node_id varchar(80) primary key,
    performance_record_id varchar(80) not null,
    contract_id varchar(80) not null,
    node_type varchar(80) not null,
    node_name varchar(160) not null,
    milestone_code varchar(80),
    planned_at varchar(40),
    due_at varchar(40),
    actual_at varchar(40),
    node_status varchar(40) not null,
    progress_percent integer not null,
    risk_level varchar(40) not null,
    issue_count integer not null,
    is_overdue boolean not null,
    result_summary varchar(512),
    last_result_at varchar(40),
    owner_user_id varchar(80),
    owner_org_unit_id varchar(80)
);

create index idx_cl_performance_node_record on cl_performance_node(performance_record_id, node_status);
create index idx_cl_performance_node_contract on cl_performance_node(contract_id, milestone_code);

create table cl_lifecycle_summary (
    contract_id varchar(80) primary key,
    current_stage varchar(40) not null,
    stage_status varchar(40) not null,
    performance_record_id varchar(80),
    performance_status varchar(40),
    progress_percent integer not null,
    risk_level varchar(40) not null,
    open_node_count integer not null,
    overdue_node_count integer not null,
    issue_count integer not null,
    latest_milestone_code varchar(80),
    latest_milestone_at varchar(40) not null,
    summary_version varchar(40) not null,
    updated_at varchar(40) not null
);

create table cl_lifecycle_timeline_event (
    timeline_event_id varchar(80) primary key,
    contract_id varchar(80) not null,
    event_type varchar(80) not null,
    source_resource_type varchar(80) not null,
    source_resource_id varchar(80) not null,
    milestone_code varchar(80),
    dedupe_key varchar(160) not null,
    actor_user_id varchar(80),
    event_result varchar(40) not null,
    related_document_ref_id varchar(80),
    trace_id varchar(120),
    occurred_at varchar(40) not null
);

create unique index uk_cl_timeline_dedupe on cl_lifecycle_timeline_event(dedupe_key);
create index idx_cl_timeline_contract on cl_lifecycle_timeline_event(contract_id, occurred_at);

create table cl_lifecycle_document_ref (
    lifecycle_document_ref_id varchar(80) primary key,
    contract_id varchar(80) not null,
    source_resource_type varchar(80) not null,
    source_resource_id varchar(80) not null,
    document_role varchar(80) not null,
    document_asset_id varchar(80) not null,
    document_version_id varchar(80) not null,
    is_primary boolean not null,
    created_at varchar(40) not null
);

create index idx_cl_document_ref_source on cl_lifecycle_document_ref(source_resource_type, source_resource_id);
create index idx_cl_document_ref_contract on cl_lifecycle_document_ref(contract_id, document_role);

create table cl_lifecycle_audit_event (
    audit_event_id varchar(80) primary key,
    contract_id varchar(80) not null,
    event_type varchar(80) not null,
    source_resource_type varchar(80) not null,
    source_resource_id varchar(80) not null,
    actor_user_id varchar(80),
    result_status varchar(40) not null,
    event_result varchar(40) not null,
    dedupe_key varchar(160) not null,
    related_document_ref_id varchar(80),
    trace_id varchar(120),
    occurred_at varchar(40) not null
);

create unique index uk_cl_audit_dedupe on cl_lifecycle_audit_event(dedupe_key);
create index idx_cl_audit_contract on cl_lifecycle_audit_event(contract_id, occurred_at);

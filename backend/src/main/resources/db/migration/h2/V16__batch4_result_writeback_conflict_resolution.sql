create table ia_writeback_record (
    writeback_record_id varchar(80) primary key,
    result_id varchar(80) not null,
    target_type varchar(40) not null,
    target_id varchar(160) not null,
    writeback_action varchar(40) not null,
    writeback_status varchar(40) not null,
    target_snapshot_version int not null,
    conflict_code varchar(80) not null,
    failure_reason varchar(512),
    retry_count int not null,
    ranking_snapshot_id varchar(80),
    quality_evaluation_id varchar(80),
    operator_type varchar(40) not null,
    operator_id varchar(80),
    payload_json clob not null,
    trace_id varchar(120),
    created_at timestamp not null,
    updated_at timestamp not null,
    completed_at timestamp,
    constraint uk_writeback_result_target unique (result_id, target_type, target_id)
);

create table ia_writeback_dead_letter (
    dead_letter_id varchar(80) primary key,
    writeback_record_id varchar(80) not null,
    result_id varchar(80) not null,
    target_type varchar(40) not null,
    target_id varchar(160) not null,
    failure_reason varchar(512) not null,
    retry_count int not null,
    dead_letter_status varchar(40) not null,
    trace_id varchar(120),
    created_at timestamp not null
);

create table ia_writeback_lock (
    lock_key varchar(240) primary key,
    writeback_record_id varchar(80) not null,
    lock_status varchar(40) not null,
    expires_at timestamp not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table contract_ai_summary_view (
    summary_view_id varchar(80) primary key,
    contract_id varchar(120) not null,
    summary_reference_id varchar(80) not null,
    summary_text clob not null,
    summary_scope varchar(40) not null,
    section_list_json clob not null,
    citation_reference_json clob not null,
    display_language varchar(20) not null,
    summary_digest varchar(160) not null,
    ranking_snapshot_id varchar(80),
    quality_evaluation_id varchar(80),
    guardrail_decision varchar(40) not null,
    confidence_score decimal(8,4) not null,
    source_count int not null,
    assembled_at timestamp not null,
    target_version int not null,
    view_status varchar(40) not null,
    written_at timestamp not null
);

create table contract_ai_risk_view (
    risk_view_id varchar(160) primary key,
    contract_id varchar(120) not null,
    risk_reference_id varchar(80) not null,
    risk_level varchar(40) not null,
    risk_item_list_json clob not null,
    clause_gap_summary_json clob not null,
    recommendation_summary_json clob not null,
    evidence_reference_json clob not null,
    requires_manual_review boolean not null,
    ranking_snapshot_id varchar(80),
    quality_evaluation_id varchar(80),
    guardrail_decision varchar(40) not null,
    confidence_score decimal(8,4) not null,
    source_count int not null,
    assembled_at timestamp not null,
    target_version int not null,
    view_status varchar(40) not null,
    written_at timestamp not null
);

create table contract_ai_extraction_view (
    extraction_view_id varchar(160) primary key,
    contract_id varchar(120) not null,
    extraction_reference_id varchar(80) not null,
    field_path varchar(160) not null,
    comparison_mode varchar(40) not null,
    extracted_field_list_json clob not null,
    clause_match_summary_json clob not null,
    diff_summary_json clob not null,
    confidence_summary_json clob not null,
    requires_manual_review boolean not null,
    ranking_snapshot_id varchar(80),
    quality_evaluation_id varchar(80),
    guardrail_decision varchar(40) not null,
    confidence_score decimal(8,4) not null,
    source_count int not null,
    assembled_at timestamp not null,
    confidence_status varchar(40) not null,
    default_display_flag boolean not null,
    target_version int not null,
    view_status varchar(40) not null,
    written_at timestamp not null
);

create index idx_writeback_record_result on ia_writeback_record(result_id, writeback_status);
create index idx_writeback_record_target on ia_writeback_record(target_type, target_id, writeback_status);
create index idx_writeback_dead_letter_status on ia_writeback_dead_letter(dead_letter_status, created_at);

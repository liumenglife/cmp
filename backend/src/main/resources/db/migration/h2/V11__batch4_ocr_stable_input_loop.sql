create table ia_ocr_job (
    ocr_job_id varchar(80) primary key,
    contract_id varchar(120),
    document_asset_id varchar(80) not null,
    document_version_id varchar(80) not null,
    input_content_fingerprint varchar(256) not null,
    job_purpose varchar(80) not null,
    job_status varchar(40) not null,
    language_hint_json clob not null,
    quality_profile_code varchar(80) not null,
    engine_route_code varchar(80) not null,
    current_attempt_no integer not null,
    max_attempt_no integer not null,
    result_aggregate_id varchar(80),
    failure_code varchar(80),
    failure_reason varchar(512),
    idempotency_key varchar(160) not null,
    trace_id varchar(120) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index uk_ocr_idempotency on ia_ocr_job(document_version_id, job_purpose, idempotency_key);
create index idx_ocr_job_document on ia_ocr_job(document_asset_id, document_version_id, job_status);

create table ia_ocr_result_aggregate (
    ocr_result_aggregate_id varchar(80) primary key,
    ocr_job_id varchar(80) not null,
    contract_id varchar(120),
    document_asset_id varchar(80) not null,
    document_version_id varchar(80) not null,
    result_status varchar(40) not null,
    result_schema_version varchar(40) not null,
    quality_profile_code varchar(80) not null,
    full_text_ref varchar(256),
    page_summary_json clob not null,
    layout_block_ref varchar(256),
    field_candidate_ref varchar(256),
    language_segment_ref varchar(256),
    table_payload_ref varchar(256),
    seal_payload_ref varchar(256),
    citation_payload_ref varchar(256),
    quality_score decimal(6, 4) not null,
    content_fingerprint varchar(256) not null,
    superseded_by_result_id varchar(80),
    default_consumable boolean not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_ocr_result_document on ia_ocr_result_aggregate(document_asset_id, document_version_id, result_status, default_consumable);

create table ia_ocr_text_layer (
    text_layer_id varchar(80) primary key,
    ocr_result_aggregate_id varchar(80) not null,
    page_no integer not null,
    text_content clob not null,
    bbox_json clob not null,
    confidence_score decimal(6, 4) not null,
    language_code varchar(20) not null,
    source_engine_code varchar(80) not null
);

create table ia_ocr_layout_block (
    layout_block_id varchar(80) primary key,
    ocr_result_aggregate_id varchar(80) not null,
    page_no integer not null,
    block_type varchar(40) not null,
    text_excerpt varchar(512),
    bbox_json clob not null,
    reading_order integer not null,
    confidence_score decimal(6, 4) not null,
    parent_block_id varchar(80),
    source_engine_code varchar(80) not null
);

create table ia_ocr_table_region (
    table_region_id varchar(80) primary key,
    ocr_result_aggregate_id varchar(80) not null,
    page_no integer not null,
    bbox_json clob not null,
    row_count integer not null,
    column_count integer not null,
    cell_list_json clob not null,
    header_candidate_list_json clob not null,
    table_confidence_score decimal(6, 4) not null
);

create table ia_ocr_seal_region (
    seal_region_id varchar(80) primary key,
    ocr_result_aggregate_id varchar(80) not null,
    page_no integer not null,
    bbox_json clob not null,
    seal_text_candidate varchar(256),
    seal_shape varchar(40) not null,
    color_hint varchar(40),
    overlap_signature_flag boolean not null,
    confidence_score decimal(6, 4) not null
);

create table ia_ocr_field_candidate (
    field_candidate_id varchar(80) primary key,
    ocr_result_aggregate_id varchar(80) not null,
    field_type varchar(80) not null,
    candidate_value varchar(512) not null,
    normalized_value varchar(512),
    source_layout_block_id varchar(80),
    page_no integer not null,
    bbox_json clob not null,
    confidence_score decimal(6, 4) not null,
    quality_profile_code varchar(80) not null,
    field_threshold_code varchar(80) not null,
    evidence_text varchar(512),
    candidate_status varchar(40) not null
);

create table ia_ocr_language_segment (
    language_segment_id varchar(80) primary key,
    ocr_result_aggregate_id varchar(80) not null,
    page_no integer not null,
    layout_block_id varchar(80),
    language_code varchar(20) not null,
    text_range_json clob not null,
    bbox_json clob not null,
    confidence_score decimal(6, 4) not null,
    normalization_profile_code varchar(80) not null
);

create table ia_ocr_retry_fact (
    retry_fact_id varchar(80) primary key,
    ocr_job_id varchar(80) not null,
    attempt_no integer not null,
    failure_code varchar(80) not null,
    failure_reason varchar(512) not null,
    retryable boolean not null,
    next_retry_after timestamp,
    trace_id varchar(120) not null,
    created_at timestamp not null
);

create table ia_ocr_audit_event (
    audit_event_id varchar(80) primary key,
    ocr_job_id varchar(80),
    ocr_result_aggregate_id varchar(80),
    contract_id varchar(120),
    document_asset_id varchar(80),
    document_version_id varchar(80),
    content_fingerprint varchar(256),
    actor_id varchar(80),
    trace_id varchar(120) not null,
    action_type varchar(80) not null,
    result_status varchar(40) not null,
    failure_reason varchar(512),
    occurred_at timestamp not null
);

create index idx_ocr_audit_trace on ia_ocr_audit_event(trace_id, occurred_at);
create index idx_ocr_audit_object on ia_ocr_audit_event(document_asset_id, document_version_id, occurred_at);

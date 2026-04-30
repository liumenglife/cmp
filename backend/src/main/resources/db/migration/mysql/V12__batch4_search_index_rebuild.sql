create table ia_search_source_envelope (
    envelope_id varchar(80) primary key,
    doc_type varchar(40) not null,
    source_object_id varchar(120) not null,
    source_version_digest varchar(160) not null,
    contract_id varchar(120),
    document_asset_id varchar(80),
    document_version_id varchar(80),
    semantic_ref_id varchar(120),
    source_anchor_json text not null,
    title_text varchar(512) not null,
    body_text text not null,
    keyword_text text not null,
    owner_org_unit_id varchar(120),
    locale_code varchar(20) not null,
    admission_status varchar(40) not null,
    created_at timestamp not null
);

create index idx_search_source_object on ia_search_source_envelope(doc_type, source_object_id, source_version_digest);

create table ia_search_document (
    search_doc_id varchar(120) primary key,
    doc_type varchar(40) not null,
    source_object_id varchar(120) not null,
    source_anchor_json text not null,
    source_version_digest varchar(160) not null,
    contract_id varchar(120),
    document_asset_id varchar(80),
    document_version_id varchar(80),
    semantic_ref_id varchar(120),
    title_text varchar(512) not null,
    body_text text not null,
    keyword_text text not null,
    filter_payload_json text not null,
    sort_payload_json text not null,
    locale_code varchar(20) not null,
    visibility_scope_json text not null,
    exposure_status varchar(40) not null,
    rebuild_generation integer not null,
    indexed_at timestamp not null
);

create index idx_search_document_query on ia_search_document(doc_type, exposure_status, rebuild_generation);
create index idx_search_document_version on ia_search_document(document_asset_id, document_version_id, exposure_status);

create table ia_search_result_set (
    result_set_id varchar(80) primary key,
    search_query_json text not null,
    result_status varchar(40) not null,
    item_payload_json text not null,
    facet_payload_json text not null,
    total integer not null,
    ranking_profile_code varchar(80) not null,
    expires_at timestamp not null,
    cache_hit_flag boolean not null,
    permission_scope_digest varchar(160) not null,
    actor_id varchar(80),
    rebuild_generation integer not null,
    stable_order_digest varchar(160) not null,
    created_at timestamp not null
);

create index idx_search_result_actor on ia_search_result_set(actor_id, result_status, created_at);

create table ia_search_export_record (
    export_id varchar(80) primary key,
    result_set_id varchar(80) not null,
    export_profile_code varchar(80) not null,
    export_status varchar(40) not null,
    artifact_ref varchar(256) not null,
    item_count integer not null,
    permission_scope_digest varchar(160) not null,
    trace_id varchar(120),
    created_at timestamp not null
);

create table ia_search_rebuild_job (
    rebuild_job_id varchar(80) primary key,
    rebuild_type varchar(40) not null,
    rebuild_status varchar(40) not null,
    scope_json text not null,
    old_generation integer not null,
    new_generation integer not null,
    backfilled_count integer not null,
    alias_status varchar(40) not null,
    trace_id varchar(120),
    created_at timestamp not null,
    completed_at timestamp
);

create table ia_search_audit_event (
    audit_event_id varchar(80) primary key,
    action_type varchar(80) not null,
    actor_id varchar(80),
    contract_id varchar(120),
    result_set_id varchar(80),
    rebuild_generation integer not null,
    result_status varchar(40) not null,
    trace_id varchar(120),
    occurred_at timestamp not null
);

create index idx_search_audit_trace on ia_search_audit_event(trace_id, occurred_at);

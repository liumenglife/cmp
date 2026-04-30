create table ia_term_entry (
    term_entry_id varchar(80) primary key,
    term_key varchar(120) not null unique,
    domain varchar(40) not null,
    status varchar(40) not null,
    canonical_language varchar(20) not null,
    created_by varchar(80),
    published_at timestamp null,
    version_no int not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table ia_translation_unit (
    translation_unit_id varchar(80) primary key,
    term_entry_id varchar(80) not null,
    language_code varchar(20) not null,
    surface_form varchar(512) not null,
    alt_forms_json text not null,
    status varchar(40) not null,
    reviewed_by varchar(80),
    reviewed_at timestamp null,
    version_no int not null,
    superseded_by_id varchar(80),
    created_at timestamp not null,
    updated_at timestamp not null,
    key idx_translation_unit_term_language (term_entry_id, language_code, status, version_no)
);

create table ia_terminology_profile (
    profile_code varchar(80) not null,
    profile_version int not null,
    domain_filter varchar(120),
    language_scope_json text not null,
    included_term_keys_json text not null,
    snapshot_payload_json text not null,
    published_at timestamp not null,
    status varchar(40) not null,
    primary key (profile_code, profile_version)
);

create table ia_terminology_profile_term_snapshot (
    profile_code varchar(80) not null,
    profile_version int not null,
    term_key varchar(120) not null,
    term_entry_id varchar(80) not null,
    translations_json text not null,
    key idx_terminology_snapshot_profile (profile_code, profile_version)
);

create table ia_i18n_context (
    i18n_context_id varchar(80) primary key,
    owner_type varchar(40) not null,
    owner_id varchar(120) not null,
    terminology_profile_code varchar(80) not null,
    profile_version int not null,
    input_language varchar(20) not null,
    normalized_language varchar(20) not null,
    output_language varchar(20) not null,
    display_label_language varchar(20) not null,
    i18n_status varchar(40) not null,
    segment_language_payload_json text not null,
    terminology_snapshot_json text not null,
    downstream_degradation_json text not null,
    created_at timestamp not null,
    unique key uk_i18n_owner (owner_type, owner_id)
);

create table ia_i18n_audit_event (
    audit_event_id varchar(80) primary key,
    operator_type varchar(20) not null,
    operator_id varchar(80),
    action varchar(80) not null,
    target_type varchar(60) not null,
    target_id varchar(120) not null,
    profile_code varchar(80),
    profile_version int,
    trace_id varchar(120),
    occurred_at timestamp not null,
    key idx_i18n_audit_target (target_type, target_id, occurred_at)
);

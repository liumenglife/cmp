create table cl_contract_change (
    change_id varchar(80) primary key,
    contract_id varchar(80) not null,
    change_type varchar(80) not null,
    change_reason varchar(512),
    change_summary varchar(512),
    impact_scope_json text,
    effective_date varchar(40),
    change_status varchar(40) not null,
    workflow_instance_id varchar(80),
    approved_at varchar(40),
    applied_at varchar(40),
    change_result_summary varchar(512),
    result_version_no int not null,
    key idx_cl_change_contract (contract_id, change_status),
    key idx_cl_change_workflow (workflow_instance_id)
);

create table cl_contract_termination (
    termination_id varchar(80) primary key,
    contract_id varchar(80) not null,
    termination_type varchar(80) not null,
    termination_reason varchar(512),
    termination_summary varchar(512),
    requested_termination_date varchar(40),
    settlement_summary varchar(512),
    termination_status varchar(40) not null,
    workflow_instance_id varchar(80),
    terminated_at varchar(40),
    post_action_status varchar(40),
    access_restriction varchar(80),
    key idx_cl_term_contract (contract_id, termination_status),
    key idx_cl_term_workflow (workflow_instance_id)
);

create table cl_archive_record (
    archive_record_id varchar(80) primary key,
    contract_id varchar(80) not null,
    archive_batch_no varchar(80) not null,
    archive_type varchar(40) not null,
    archive_reason varchar(512),
    input_set_snapshot text,
    archive_status varchar(40) not null,
    archive_integrity_status varchar(40) not null,
    archive_keeper_user_id varchar(80),
    archive_location_code varchar(80),
    package_document_asset_id varchar(80),
    package_document_version_id varchar(80),
    manifest_document_asset_id varchar(80),
    manifest_document_version_id varchar(80),
    archived_at varchar(40) not null,
    unique key uk_cl_archive_contract_batch (contract_id, archive_batch_no),
    key idx_cl_archive_contract (contract_id, archive_status)
);

create table cl_archive_borrow_record (
    borrow_record_id varchar(80) primary key,
    contract_id varchar(80) not null,
    archive_record_id varchar(80) not null,
    archive_batch_no varchar(80) not null,
    package_document_asset_id varchar(80),
    manifest_document_asset_id varchar(80),
    borrow_status varchar(40) not null,
    borrow_purpose varchar(512),
    requested_by varchar(80),
    requested_org_unit_id varchar(80),
    requested_at varchar(40) not null,
    due_at varchar(40),
    returned_at varchar(40),
    key idx_cl_borrow_archive (archive_record_id, borrow_status),
    key idx_cl_borrow_contract (contract_id, borrow_status)
);

create table cl_lifecycle_process_ref (
    lifecycle_process_ref_id varchar(80) primary key,
    contract_id varchar(80) not null,
    source_resource_type varchar(80) not null,
    source_resource_id varchar(80) not null,
    workflow_instance_id varchar(80) not null,
    process_purpose varchar(80) not null,
    process_status_snapshot varchar(40) not null,
    last_synced_at varchar(40) not null,
    key idx_cl_process_ref_contract (contract_id, process_purpose),
    key idx_cl_process_ref_instance (workflow_instance_id)
);

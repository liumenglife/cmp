create table ao_human_confirmation (
    confirmation_id varchar(80) primary key,
    source_task_id varchar(80),
    source_result_id varchar(80),
    confirmation_type varchar(80) not null,
    business_module varchar(80) not null,
    object_type varchar(80) not null,
    object_id varchar(120),
    confirmation_status varchar(40) not null,
    requested_by varchar(80) not null,
    decision_result_json clob not null,
    decided_by varchar(80),
    decided_at timestamp,
    trace_id varchar(120) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_ao_confirm_object on ao_human_confirmation(object_type, object_id, created_at);
create index idx_ao_confirm_status on ao_human_confirmation(confirmation_status, created_at);

create table platform_job (
    platform_job_id varchar(80) primary key,
    job_type varchar(80) not null,
    job_status varchar(40) not null,
    source_module varchar(80) not null,
    consumer_module varchar(80) not null,
    resource_type varchar(80) not null,
    resource_id varchar(120),
    business_object_type varchar(80),
    business_object_id varchar(120),
    priority integer not null,
    attempt_no integer not null,
    max_attempts integer not null,
    runner_code varchar(80) not null,
    trace_id varchar(120) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_platform_job_consumer on platform_job(consumer_module, job_status, created_at);
create index idx_platform_job_resource on platform_job(resource_type, resource_id);

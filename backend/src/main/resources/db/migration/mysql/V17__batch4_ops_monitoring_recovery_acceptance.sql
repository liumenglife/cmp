create table ia_recovery_operation_log (
    operation_log_id varchar(80) primary key,
    script_name varchar(120) not null,
    operator_id varchar(80) not null,
    operator_role varchar(40) not null,
    action_type varchar(80) not null,
    execution_status varchar(40) not null,
    target_subsystem varchar(60) not null,
    impact_level varchar(20) not null,
    trace_id varchar(120),
    review_operator_id varchar(80),
    request_payload_json text not null,
    result_payload_json text not null,
    failure_reason varchar(512),
    created_at timestamp not null,
    completed_at timestamp,
    key idx_recovery_operation_script (script_name, execution_status),
    key idx_recovery_operation_trace (trace_id, created_at)
);

create table ia_ops_maintenance_window (
    maintenance_window_id varchar(80) primary key,
    subsystem varchar(60) not null,
    window_level varchar(40) not null,
    starts_at timestamp not null,
    ends_at timestamp not null,
    configured_by varchar(80) not null,
    created_at timestamp not null,
    key idx_ops_maintenance_active (subsystem, starts_at, ends_at)
);

create table ia_ops_alert_notification (
    notification_id varchar(80) primary key,
    subsystem varchar(60) not null,
    metric_name varchar(120) not null,
    severity varchar(20) not null,
    trace_id varchar(120),
    notification_status varchar(40) not null,
    sent_at timestamp not null,
    key idx_ops_alert_notification_rule (subsystem, metric_name, sent_at)
);

create table ia_ops_metric_snapshot (
    metric_snapshot_id varchar(80) primary key,
    subsystem varchar(60) not null,
    metric_group varchar(40) not null,
    metric_name varchar(120) not null,
    metric_value decimal(18, 6) not null,
    trace_id varchar(120),
    captured_at timestamp not null,
    key idx_ops_metric_snapshot_latest (subsystem, metric_group, metric_name, captured_at)
);

create table ia_ops_alert_threshold_profile (
    profile_code varchar(80) not null,
    subsystem varchar(60) not null,
    metric_name varchar(120) not null,
    severity varchar(20) not null,
    threshold_value decimal(18, 6) not null,
    threshold_direction varchar(40) not null,
    enabled_flag boolean not null,
    updated_at timestamp not null,
    primary key (profile_code, subsystem, metric_name, severity)
);

create table ia_ops_dependency_health (
    dependency_name varchar(80) primary key,
    dependency_status varchar(40) not null,
    blocking_failure boolean not null,
    checked_at timestamp not null,
    detail_json text not null
);

create table ia_ops_operation_authorization (
    operator_id varchar(80) primary key,
    operator_role varchar(40) not null,
    authorization_status varchar(40) not null,
    updated_at timestamp not null
);

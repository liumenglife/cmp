create table ia_user (
  user_id varchar(64) primary key,
  login_name varchar(128) not null unique,
  display_name varchar(128) not null,
  user_status varchar(32) not null,
  default_org_id varchar(64) not null,
  default_org_unit_id varchar(64) not null,
  created_at timestamp(3) not null
) engine=InnoDB default charset=utf8mb4;

create table ia_identity_binding (
  binding_id varchar(64) primary key,
  provider varchar(64) not null,
  external_identity varchar(256) not null,
  user_id varchar(64) not null,
  binding_status varchar(32) not null,
  conflict_group_id varchar(64),
  created_at timestamp(3) not null,
  updated_at timestamp(3) not null,
  constraint uk_ia_identity_binding_provider_external unique (provider, external_identity)
) engine=InnoDB default charset=utf8mb4;

create table ia_identity_session (
  session_id varchar(64) primary key,
  access_token varchar(128) not null unique,
  user_id varchar(64) not null,
  binding_id varchar(64) not null,
  provider varchar(64) not null,
  session_status varchar(32) not null,
  issued_at timestamp(3) not null,
  expires_at timestamp(3) not null
) engine=InnoDB default charset=utf8mb4;

create table ia_protocol_exchange (
  exchange_id varchar(64) primary key,
  provider varchar(64) not null,
  external_identity varchar(256) not null,
  exchange_status varchar(32) not null,
  retry_policy_status varchar(32) not null,
  http_status int not null,
  last_response_json json not null,
  created_at timestamp(3) not null,
  updated_at timestamp(3) not null
) engine=InnoDB default charset=utf8mb4;

create table ia_idempotency_record (
  idempotency_key varchar(128) primary key,
  payload_fingerprint text not null,
  exchange_id varchar(64) not null,
  created_at timestamp(3) not null
) engine=InnoDB default charset=utf8mb4;

create table ia_identity_binding_precheck (
  precheck_id varchar(64) primary key,
  protocol_exchange_id varchar(64),
  provider varchar(64) not null,
  external_identity varchar(256) not null,
  precheck_status varchar(32) not null,
  session_gate_result varchar(32) not null,
  candidate_user_ids json not null,
  created_at timestamp(3) not null
) engine=InnoDB default charset=utf8mb4;

create table ia_identity_manual_disposition (
  disposition_id varchar(64) primary key,
  protocol_exchange_id varchar(64) not null,
  target_user_id varchar(64) not null,
  operator_id varchar(64) not null,
  disposition_action varchar(32) not null,
  disposition_reason varchar(512),
  after_status_snapshot_json json not null,
  created_at timestamp(3) not null
) engine=InnoDB default charset=utf8mb4;

create table ia_identity_audit (
  audit_view_id varchar(64) primary key,
  event_type varchar(64) not null,
  result_status varchar(32) not null,
  actor_user_id varchar(64),
  target_user_id varchar(64),
  target_resource_id varchar(64),
  protocol_exchange_id varchar(64),
  trace_id varchar(128) not null,
  occurred_at timestamp(3) not null
) engine=InnoDB default charset=utf8mb4;

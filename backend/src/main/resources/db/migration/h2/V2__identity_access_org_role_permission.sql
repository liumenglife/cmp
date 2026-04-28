create table ia_org_unit (
  org_unit_id varchar(64) primary key,
  org_id varchar(64) not null,
  parent_org_unit_id varchar(64),
  org_unit_code varchar(128) not null,
  org_unit_name varchar(128) not null,
  org_unit_type varchar(32) not null,
  org_status varchar(32) not null,
  org_path varchar(512) not null,
  path_depth int not null,
  manager_user_id varchar(64),
  sort_order int not null,
  created_at timestamp(3) not null,
  updated_at timestamp(3) not null
);

create table ia_org_membership (
  membership_id varchar(64) primary key,
  user_id varchar(64) not null,
  org_id varchar(64) not null,
  org_unit_id varchar(64) not null,
  membership_type varchar(32) not null,
  membership_status varchar(32) not null,
  is_primary_department boolean not null,
  position_title varchar(128),
  created_at timestamp(3) not null,
  updated_at timestamp(3) not null
);

create table ia_role (
  role_id varchar(64) primary key,
  role_code varchar(128) not null unique,
  role_name varchar(128) not null,
  role_scope varchar(32) not null,
  role_type varchar(32) not null,
  role_status varchar(32) not null,
  inherits_role_id varchar(64),
  created_at timestamp(3) not null,
  updated_at timestamp(3) not null
);

create table ia_role_assignment (
  assignment_id varchar(64) primary key,
  role_id varchar(64) not null,
  subject_type varchar(32) not null,
  subject_id varchar(64) not null,
  grant_org_id varchar(64) not null,
  assignment_status varchar(32) not null,
  granted_reason varchar(512),
  granted_by varchar(64),
  created_at timestamp(3) not null,
  updated_at timestamp(3) not null
);

create table ia_permission_grant (
  permission_grant_id varchar(64) primary key,
  grant_target_type varchar(32) not null,
  grant_target_id varchar(64) not null,
  permission_type varchar(32) not null,
  permission_code varchar(128) not null,
  resource_type varchar(64),
  resource_scope_ref varchar(256),
  grant_status varchar(32) not null,
  priority_no int not null,
  effect_mode varchar(32) not null,
  created_at timestamp(3) not null,
  updated_at timestamp(3) not null
);

create table ia_data_scope (
  data_scope_id varchar(64) primary key,
  subject_type varchar(32) not null,
  subject_id varchar(64) not null,
  resource_type varchar(64) not null,
  scope_type varchar(32) not null,
  scope_ref varchar(512) not null,
  scope_status varchar(32) not null,
  priority_no int not null,
  effect_mode varchar(32) not null,
  created_at timestamp(3) not null,
  updated_at timestamp(3) not null
);

create table ia_authorization_decision (
  decision_id varchar(64) primary key,
  subject_user_id varchar(64) not null,
  subject_org_id varchar(64) not null,
  subject_org_unit_id varchar(64),
  action_code varchar(128) not null,
  resource_type varchar(64) not null,
  decision_result varchar(32) not null,
  decision_reason_code varchar(128),
  data_scope_snapshot_checksum varchar(128),
  request_trace_id varchar(128) not null,
  evaluated_at timestamp(3) not null
);

create table ia_authorization_hit_result (
  hit_result_id varchar(64) primary key,
  decision_id varchar(64) not null,
  hit_type varchar(32) not null,
  hit_ref_id varchar(64) not null,
  frozen_ref_id varchar(64),
  resolution_record_id varchar(64),
  hit_result varchar(32) not null,
  hit_priority_no int not null,
  evidence_snapshot clob
);

create table ia_org_rule (
  org_rule_id varchar(64) primary key,
  rule_code varchar(128) not null unique,
  rule_name varchar(128) not null,
  rule_type varchar(64) not null,
  rule_status varchar(32) not null,
  rule_scope_type varchar(32) not null,
  rule_scope_ref varchar(64) not null,
  resolver_config clob not null,
  fallback_policy varchar(64) not null,
  version_no int not null,
  created_at timestamp(3) not null,
  updated_at timestamp(3) not null
);

create table ia_org_rule_version (
  org_rule_version_id varchar(64) primary key,
  org_rule_id varchar(64) not null,
  version_no int not null,
  rule_type varchar(64) not null,
  rule_scope_type varchar(32) not null,
  rule_scope_ref varchar(64) not null,
  resolver_config_snapshot clob not null,
  fallback_policy_snapshot varchar(64) not null,
  schema_version varchar(32) not null,
  version_checksum varchar(128) not null,
  version_status varchar(32) not null,
  effective_from timestamp(3) not null
);

create table ia_org_rule_resolution_record (
  org_rule_resolution_record_id varchar(64) primary key,
  org_rule_version_id varchar(64) not null,
  request_trace_id varchar(128) not null,
  resolution_scene varchar(64) not null,
  context_checksum varchar(128) not null,
  resolution_status varchar(32) not null,
  resolved_subject_snapshot clob not null,
  evidence_snapshot clob not null,
  fallback_used boolean not null,
  resolver_version varchar(32) not null,
  resolved_at timestamp(3) not null
);

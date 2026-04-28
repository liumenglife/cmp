alter table ia_authorization_decision add column resource_id varchar(128);
alter table ia_authorization_decision add column permission_snapshot_checksum varchar(128);
alter table ia_authorization_decision add column expires_at timestamp(3);

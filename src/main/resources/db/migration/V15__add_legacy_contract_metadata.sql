alter table story_content_versions
  add column legacy_contract_metadata jsonb not null default '{}'::jsonb;

alter table story_content_versions
  add constraint ck_story_content_versions_legacy_contract_metadata_object
  check (jsonb_typeof(legacy_contract_metadata) = 'object');

alter table legacy_migration_watermarks
  add column snapshot_hash char(64);

alter table legacy_migration_watermarks
  add constraint ck_legacy_migration_watermarks_snapshot_hash
  check (snapshot_hash is null or snapshot_hash ~ '^[0-9a-f]{64}$');

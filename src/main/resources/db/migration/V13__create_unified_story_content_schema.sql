create table stories (
  id bigserial primary key,
  origin varchar(32) not null check (origin in ('CURATED','AI_GENERATED')),
  origin_ref varchar(128), owner_user_id bigint references users(id),
  visibility varchar(32) not null check (visibility in ('OWNER_PRIVATE','SHARED','PUBLISHED','ARCHIVED')),
  title_ko varchar(255) not null, title_ja varchar(255) not null,
  description_ko text, description_ja text, category_keys jsonb not null default '[]',
  published_version_id bigint, archived_at timestamptz, created_at timestamptz not null, updated_at timestamptz not null,
  check ((visibility in ('OWNER_PRIVATE','SHARED') and owner_user_id is not null) or visibility in ('PUBLISHED','ARCHIVED'))
);

create table story_content_versions (
  id bigserial primary key, story_id bigint not null references stories(id), version_no integer not null,
  status varchar(24) not null constraint ck_story_content_version_status
    check (status in ('DRAFT','IN_REVIEW','APPROVED','PUBLISHED','SUPERSEDED','ARCHIVED')),
  schema_version integer not null, lock_version bigint not null default 0,
  source_revision bigint not null default 0,
  created_by bigint references users(id), last_modified_by bigint references users(id),
  reviewed_by bigint references users(id), published_by bigint references users(id),
  created_at timestamptz not null, updated_at timestamptz not null, reviewed_at timestamptz, published_at timestamptz,
  unique (story_id, version_no)
);

alter table stories add constraint fk_stories_published_version
  foreign key (published_version_id) references story_content_versions(id);

create unique index uq_story_active_draft
  on story_content_versions(story_id) where status = 'DRAFT';

create table story_version_locales (
  id bigserial primary key,
  version_id bigint not null references story_content_versions(id) on delete cascade,
  locale varchar(8) not null check (locale in ('ko','ja')),
  default_voice_type varchar(64),
  unique (version_id, locale)
);

create table media_assets (
  id bigserial primary key,
  owner_version_id bigint not null references story_content_versions(id),
  kind varchar(16) not null check (kind in ('IMAGE','AUDIO','VIDEO','CAPTION','MANIFEST')),
  storage_key varchar(1024) not null unique,
  public_url varchar(2048) not null,
  sha256 char(64) not null check (sha256 ~ '^[0-9a-f]{64}$'),
  actual_mime_type varchar(128) not null,
  byte_size bigint not null check (byte_size >= 0),
  width integer check (width is null or width > 0),
  height integer check (height is null or height > 0),
  duration_ms bigint check (duration_ms is null or duration_ms >= 0),
  status varchar(16) not null check (status in ('QUARANTINED','INSPECTING','READY','REJECTED','DELETED')),
  created_by bigint references users(id),
  created_at timestamptz not null default now()
);

create table story_scenes (
  id bigserial primary key,
  version_id bigint not null references story_content_versions(id) on delete cascade,
  scene_key varchar(128) not null,
  order_index integer not null check (order_index >= 0),
  width integer not null check (width > 0),
  height integer not null check (height > 0),
  duration_ms bigint not null default 0 check (duration_ms >= 0),
  fallback_asset_id bigint references media_assets(id),
  unique (version_id, scene_key),
  unique (version_id, order_index)
);

create table scene_localized_contents (
  id bigserial primary key,
  scene_id bigint not null references story_scenes(id) on delete cascade,
  locale varchar(8) not null check (locale in ('ko','ja')),
  display_text text not null,
  script_text text not null,
  caption_asset_id bigint references media_assets(id),
  unique (scene_id, locale)
);

create table story_layers (
  id bigserial primary key,
  scene_id bigint not null references story_scenes(id) on delete cascade,
  layer_key varchar(128) not null,
  type varchar(24) not null check (type in ('BACKGROUND','IMAGE','TEXT','SPRITE','CHARACTER_SLOT','SHAPE')),
  z_index integer not null,
  asset_id bigint references media_assets(id),
  x numeric(12,6) not null default 0,
  y numeric(12,6) not null default 0,
  scale_x numeric(12,6) not null default 1,
  scale_y numeric(12,6) not null default 1,
  rotation_deg numeric(12,6) not null default 0,
  opacity numeric(5,4) not null default 1 check (opacity between 0 and 1),
  visible boolean not null default true,
  properties_json jsonb not null default '{}',
  unique (scene_id, layer_key),
  unique (scene_id, z_index, layer_key)
);

create table scene_audio_cues (
  id bigserial primary key,
  scene_id bigint not null references story_scenes(id) on delete cascade,
  cue_key varchar(128) not null,
  role varchar(16) not null check (role in ('NARRATION','SFX','BGM')),
  start_ms bigint not null default 0 check (start_ms >= 0),
  required boolean not null default false,
  unique (scene_id, cue_key)
);

create table audio_variants (
  id bigserial primary key,
  audio_cue_id bigint not null references scene_audio_cues(id) on delete cascade,
  locale varchar(8) not null,
  voice_type varchar(64) not null,
  asset_id bigint not null references media_assets(id),
  status varchar(16) not null check (status in ('READY','STALE','REJECTED')),
  unique (audio_cue_id, locale, voice_type)
);

create table content_renditions (
  id bigserial primary key,
  version_id bigint not null references story_content_versions(id) on delete cascade,
  type varchar(16) not null check (type in ('SLIDE','VIDEO','INTERACTIVE')),
  status varchar(16) not null check (status in ('BUILDING','READY','STALE','FAILED','DISABLED')),
  manifest_asset_id bigint references media_assets(id),
  renderer_version integer not null default 1,
  checksum char(64) check (checksum is null or checksum ~ '^[0-9a-f]{64}$'),
  compatibility_fallback boolean not null default false,
  unique (version_id, type)
);

create unique index uq_ready_compatibility_slide
  on content_renditions(version_id)
  where type = 'SLIDE' and status = 'READY' and compatibility_fallback = true;

create table content_rendition_variants (
  id bigserial primary key,
  rendition_id bigint not null references content_renditions(id) on delete cascade,
  locale varchar(8) not null,
  voice_type varchar(64) not null,
  output_asset_id bigint not null references media_assets(id),
  output_mode varchar(24) not null check (output_mode in ('UPLOADED_MASTER','GENERATED')),
  status varchar(16) not null check (status in ('BUILDING','READY','STALE','FAILED','DISABLED')),
  source_revision bigint not null check (source_revision >= 0),
  unique (rendition_id, locale, voice_type)
);

create table content_review_records (
  id bigserial primary key,
  version_id bigint not null references story_content_versions(id),
  actor_user_id bigint not null references users(id),
  action varchar(24) not null check (action in ('SUBMITTED','APPROVED','REJECTED')),
  comment text,
  created_at timestamptz not null default now()
);

create table content_publish_events (
  id bigserial primary key,
  story_id bigint not null references stories(id),
  version_id bigint not null references story_content_versions(id),
  previous_version_id bigint references story_content_versions(id),
  actor_user_id bigint not null references users(id),
  idempotency_key varchar(128) not null,
  request_fingerprint char(64) not null check (request_fingerprint ~ '^[0-9a-f]{64}$'),
  manifest_checksum char(64) not null check (manifest_checksum ~ '^[0-9a-f]{64}$'),
  created_at timestamptz not null default now(),
  unique (story_id, idempotency_key)
);

create table asset_upload_sessions (
  id uuid primary key,
  version_id bigint not null references story_content_versions(id),
  asset_id bigint references media_assets(id),
  admin_user_id bigint not null references users(id),
  original_file_name varchar(255) not null,
  asset_kind varchar(16) not null check (asset_kind in ('IMAGE','AUDIO','VIDEO','CAPTION')),
  declared_size bigint not null check (declared_size >= 0),
  declared_sha256 char(64) not null check (declared_sha256 ~ '^[0-9a-f]{64}$'),
  declared_mime_type varchar(128) not null,
  quarantine_key varchar(1024) not null unique,
  status varchar(16) not null check (status in ('CREATED','UPLOADED','INSPECTING','READY','REJECTED','EXPIRED')),
  rejection_code varchar(64),
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table legacy_story_links (
  id bigserial primary key,
  legacy_type varchar(16) not null check (legacy_type in ('CURATED','AI')),
  legacy_id bigint not null,
  story_id bigint not null references stories(id),
  content_version_id bigint not null references story_content_versions(id),
  legacy_format varchar(16),
  legacy_status_code varchar(32),
  legacy_language varchar(8),
  imported_generation_job_id bigint,
  imported_video_job_id bigint,
  source_hash char(64) not null check (source_hash ~ '^[0-9a-f]{64}$'),
  imported_at timestamptz not null default now(),
  unique (legacy_type, legacy_id)
);

create table legacy_migration_watermarks (
  migration_kind varchar(32) primary key,
  watermark_at timestamptz not null,
  last_legacy_id bigint not null default 0,
  updated_at timestamptz not null default now()
);

create table legacy_shadow_mismatches (
  id bigserial primary key,
  legacy_type varchar(16) not null check (legacy_type in ('CURATED','AI')),
  legacy_id bigint not null,
  legacy_checksum char(64) not null check (legacy_checksum ~ '^[0-9a-f]{64}$'),
  canonical_checksum char(64) not null check (canonical_checksum ~ '^[0-9a-f]{64}$'),
  diff_json jsonb not null,
  resolved_at timestamptz,
  created_at timestamptz not null default now()
);

create unique index uq_legacy_shadow_mismatch_open
  on legacy_shadow_mismatches(legacy_type, legacy_id) where resolved_at is null;

create table content_migration_reconciliations (
  epoch bigint primary key,
  status varchar(16) not null check (status in ('SUCCEEDED','FAILED')),
  checksum char(64) not null check (checksum ~ '^[0-9a-f]{64}$'),
  report_json jsonb not null,
  completed_at timestamptz not null default now()
);

create table content_migration_control (
  singleton_id smallint primary key check (singleton_id = 1),
  state varchar(24) not null check (state in ('OPEN','FREEZE_REQUESTED','FROZEN','CUTOVER_PENDING')),
  read_source varchar(16) not null check (read_source in ('LEGACY','CANONICAL')),
  write_source varchar(16) not null check (write_source in ('LEGACY','CANONICAL')),
  barrier_epoch bigint not null default 0,
  backend_ack_epoch bigint,
  admin_backend_ack_epoch bigint,
  reconciliation_hash char(64),
  smoke_hash char(64),
  smoke_passed_at timestamptz,
  updated_at timestamptz not null default now()
);

insert into content_migration_control(singleton_id, state, read_source, write_source)
values (1, 'OPEN', 'LEGACY', 'LEGACY');

create table content_outbox_events (
  id uuid primary key,
  aggregate_type varchar(64) not null,
  aggregate_id bigint not null,
  event_type varchar(64) not null,
  barrier_epoch bigint,
  payload_json jsonb not null,
  delivery_state varchar(16) not null check (delivery_state in ('PENDING','DELIVERED','FAILED')),
  created_at timestamptz not null default now(),
  delivered_at timestamptz
);

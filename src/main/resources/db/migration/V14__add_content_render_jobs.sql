create table content_render_jobs (
  id bigserial primary key, version_id bigint not null references story_content_versions(id),
  kind varchar(32) not null check (kind in ('CONTENT_GENERATION','VIDEO_RENDER')),
  status varchar(16) not null check (status in ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
  locale varchar(8) not null default 'und', voice_type varchar(64) not null default 'none',
  source_revision bigint not null constraint ck_content_render_jobs_source_revision check (source_revision >= 0),
  attempt integer not null check (attempt > 0),
  error_code varchar(64), error_message text, created_at timestamptz not null, started_at timestamptz, finished_at timestamptz,
  constraint ck_content_render_jobs_finished_at check (
    (status in ('SUCCEEDED','FAILED','CANCELLED') and finished_at is not null)
    or (status in ('QUEUED','RUNNING') and finished_at is null)
  ),
  unique (version_id,kind,locale,voice_type,source_revision,attempt)
);

create unique index uq_content_render_jobs_active
  on content_render_jobs(version_id,kind,locale,voice_type,source_revision)
  where status in ('QUEUED','RUNNING');

alter table legacy_story_links
  add constraint fk_legacy_story_links_generation_job
  foreign key (imported_generation_job_id) references content_render_jobs(id);

alter table legacy_story_links
  add constraint fk_legacy_story_links_video_job
  foreign key (imported_video_job_id) references content_render_jobs(id);

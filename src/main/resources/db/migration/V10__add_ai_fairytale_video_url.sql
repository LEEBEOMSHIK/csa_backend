-- V10: add video_url to ai_fairytales for the server-side ffmpeg slide+TTS ->
-- mp4 assembly path (format = 'video'). Nullable: slide-format rows never set
-- it, and video-format rows leave it null until assembly succeeds (assembly
-- failure keeps the already-generated text/image/audio pages and marks
-- status = 'FAILED' instead of discarding them -- see AiFairytaleService).
--
-- Identifiers unquoted lowercase snake_case to match Hibernate's runtime
-- PhysicalNamingStrategySnakeCaseImpl (Spring Boot 4 default), consistent
-- with V1/V9, so ddl-auto: validate passes.

alter table if exists ai_fairytales
    add column if not exists video_url varchar(1000);

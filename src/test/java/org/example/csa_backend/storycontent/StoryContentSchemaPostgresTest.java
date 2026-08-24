package org.example.csa_backend.storycontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres")
@Testcontainers
@SpringBootTest
class StoryContentSchemaPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=front");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.schemas", () -> "front");
        registry.add("spring.flyway.default-schema", () -> "front");
        registry.add("spring.flyway.create-schemas", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "front");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void allPhaseOneCanonicalAndRenderTablesExist() {
        List<String> tables = List.of(
            "stories", "story_content_versions", "story_version_locales", "media_assets",
            "story_scenes", "scene_localized_contents", "story_layers", "scene_audio_cues",
            "audio_variants", "content_renditions", "content_rendition_variants",
            "content_review_records", "content_publish_events", "asset_upload_sessions",
            "legacy_story_links", "legacy_migration_watermarks", "legacy_shadow_mismatches",
            "content_migration_reconciliations", "content_migration_control", "content_outbox_events",
            "content_render_jobs"
        );

        for (String table : tables) {
            assertThat(jdbc.queryForObject("select to_regclass(?)", String.class, "front." + table))
                .isNotNull();
        }
        assertThat(jdbc.queryForObject("select to_regclass('front.story_timeline_events')", String.class)).isNull();
        assertThat(jdbc.queryForObject("select to_regclass('front.content_trigger_actions')", String.class)).isNull();
    }

    @Test
    void scenePropertiesJsonDefaultsToAnEmptyObjectAndPersistsStructuredData() {
        long versionId = insertVersion();
        long sceneId = jdbc.queryForObject(
            "insert into story_scenes (version_id, scene_key, order_index, width, height) "
                + "values (?, 'scene-v2', 0, 1280, 720) returning id",
            Long.class,
            versionId
        );

        assertThat(jdbc.queryForObject(
            "select properties_json::text from story_scenes where id = ?",
            String.class,
            sceneId
        )).isEqualTo("{}");

        jdbc.update(
            "update story_scenes set properties_json = ?::jsonb where id = ?",
            "{\"schemaVersion\":1,\"tracks\":[]}",
            sceneId
        );

        assertThat(jdbc.queryForObject(
            "select properties_json ->> 'schemaVersion' from story_scenes where id = ?",
            Integer.class,
            sceneId
        )).isEqualTo(1);
    }

    @Test
    void publishedPointerAndVersionConstraintsAreEnforced() {
        long storyId = insertStory();

        assertThatThrownBy(() -> jdbc.update(
            "insert into story_content_versions (story_id, version_no, status, schema_version, lock_version, created_at, updated_at) "
                + "values (?, 1, 'PROCESSING', 1, 0, now(), now())",
            storyId
        )).hasMessageContaining("ck_story_content_version_status");
    }

    @Test
    void publishedPointerForeignKeyRejectsAnUnknownVersion() {
        assertThatThrownBy(() -> jdbc.update(
            "insert into stories (origin, visibility, title_ko, title_ja, published_version_id, created_at, updated_at) "
                + "values ('CURATED', 'PUBLISHED', '테스트', 'テスト', 999999, now(), now())"
        )).hasMessageContaining("fk_stories_published_version");
    }

    @Test
    void activeDraftIndexRejectsASecondDraftForTheSameStory() {
        long storyId = insertStory();
        insertVersion(storyId, 1, "DRAFT");

        assertThatThrownBy(() -> insertVersion(storyId, 2, "DRAFT"))
            .hasMessageContaining("uq_story_active_draft");
    }

    @Test
    void readyCompatibilitySlideIndexRejectsASecondReadyFallback() {
        long versionId = insertVersion();
        long assetId = insertMediaAsset(versionId);
        jdbc.update(
            "insert into content_renditions (version_id, type, status, manifest_asset_id, compatibility_fallback) "
                + "values (?, 'SLIDE', 'READY', ?, true)",
            versionId,
            assetId
        );

        jdbc.execute("alter table content_renditions drop constraint content_renditions_version_id_type_key");
        try {
            assertThatThrownBy(() -> jdbc.update(
                "insert into content_renditions (version_id, type, status, manifest_asset_id, compatibility_fallback) "
                    + "values (?, 'SLIDE', 'READY', ?, true)",
                versionId,
                assetId
            )).hasMessageContaining("uq_ready_compatibility_slide");
        } finally {
            jdbc.execute(
                "alter table content_renditions add constraint content_renditions_version_id_type_key unique (version_id, type)"
            );
        }
    }

    @Test
    void openLegacyShadowMismatchIndexRejectsASecondOpenMismatch() {
        jdbc.update(
            "insert into legacy_shadow_mismatches (legacy_type, legacy_id, legacy_checksum, canonical_checksum, diff_json) "
                + "values ('CURATED', 91, ?, ?, '{}'::jsonb)",
            "b".repeat(64),
            "c".repeat(64)
        );

        assertThatThrownBy(() -> jdbc.update(
            "insert into legacy_shadow_mismatches (legacy_type, legacy_id, legacy_checksum, canonical_checksum, diff_json) "
                + "values ('CURATED', 91, ?, ?, '{}'::jsonb)",
            "d".repeat(64),
            "e".repeat(64)
        )).hasMessageContaining("uq_legacy_shadow_mismatch_open");
    }

    @Test
    void activeRenderJobIndexRejectsASecondActiveAttemptForTheSameRevision() {
        long versionId = insertVersion();
        jdbc.update(
            "insert into content_render_jobs (version_id, kind, status, source_revision, attempt, created_at) "
                + "values (?, 'CONTENT_GENERATION', 'QUEUED', 0, 1, now())",
            versionId
        );

        assertThatThrownBy(() -> jdbc.update(
            "insert into content_render_jobs (version_id, kind, status, source_revision, attempt, created_at) "
                + "values (?, 'CONTENT_GENERATION', 'RUNNING', 0, 2, now())",
            versionId
        )).hasMessageContaining("uq_content_render_jobs_active");
    }

    @Test
    void renditionAndVoiceVariantKeysAreUnique() {
        long versionId = insertVersion();
        long assetId = insertMediaAsset(versionId);
        long renditionId = jdbc.queryForObject(
            "insert into content_renditions (version_id, type, status, manifest_asset_id) values (?, 'SLIDE', 'BUILDING', ?) returning id",
            Long.class,
            versionId,
            assetId
        );
        long sceneId = jdbc.queryForObject(
            "insert into story_scenes (version_id, scene_key, order_index, width, height) values (?, 'scene-1', 0, 100, 100) returning id",
            Long.class,
            versionId
        );
        long cueId = jdbc.queryForObject(
            "insert into scene_audio_cues (scene_id, cue_key, role) values (?, 'narration', 'NARRATION') returning id",
            Long.class,
            sceneId
        );

        jdbc.update(
            "insert into audio_variants (audio_cue_id, locale, voice_type, asset_id, status) values (?, 'ko', 'narrator', ?, 'READY')",
            cueId,
            assetId
        );
        jdbc.update(
            "insert into content_rendition_variants (rendition_id, locale, voice_type, output_asset_id, output_mode, status, source_revision) "
                + "values (?, 'ko', 'narrator', ?, 'GENERATED', 'READY', 0)",
            renditionId,
            assetId
        );

        assertUnique("content_renditions", "version_id, type", () -> jdbc.update(
            "insert into content_renditions (version_id, type, status) values (?, 'SLIDE', 'BUILDING')", versionId));
        assertUnique("audio_variants", "audio_cue_id, locale, voice_type", () -> jdbc.update(
            "insert into audio_variants (audio_cue_id, locale, voice_type, asset_id, status) values (?, 'ko', 'narrator', ?, 'READY')",
            cueId,
            assetId));
        assertUnique("content_rendition_variants", "rendition_id, locale, voice_type", () -> jdbc.update(
            "insert into content_rendition_variants (rendition_id, locale, voice_type, output_asset_id, output_mode, status, source_revision) "
                + "values (?, 'ko', 'narrator', ?, 'GENERATED', 'READY', 0)",
            renditionId,
            assetId));
    }

    @Test
    void renderJobChecksRejectInvalidSourceRevisionAndUnfinishedTerminalState() {
        long versionId = insertVersion();

        assertThatThrownBy(() -> jdbc.update(
            "insert into content_render_jobs (version_id, kind, status, source_revision, attempt, created_at) "
                + "values (?, 'CONTENT_GENERATION', 'QUEUED', -1, 1, now())",
            versionId
        )).hasMessageContaining("ck_content_render_jobs_source_revision");
        assertThatThrownBy(() -> jdbc.update(
            "insert into content_render_jobs (version_id, kind, status, source_revision, attempt, created_at) "
                + "values (?, 'CONTENT_GENERATION', 'FAILED', 0, 1, now())",
            versionId
        )).hasMessageContaining("ck_content_render_jobs_finished_at");
    }

    private long insertStory() {
        return jdbc.queryForObject(
            "insert into stories (origin, visibility, title_ko, title_ja, created_at, updated_at) "
                + "values ('CURATED', 'PUBLISHED', '테스트', 'テスト', now(), now()) returning id",
            Long.class
        );
    }

    private long insertVersion() {
        return insertVersion(insertStory(), 1, "DRAFT");
    }

    private long insertVersion(long storyId, int versionNo, String status) {
        return jdbc.queryForObject(
            "insert into story_content_versions (story_id, version_no, status, schema_version, created_at, updated_at) "
                + "values (?, ?, ?, 1, now(), now()) returning id",
            Long.class,
            storyId,
            versionNo,
            status
        );
    }

    private long insertMediaAsset(long versionId) {
        return jdbc.queryForObject(
            "insert into media_assets (owner_version_id, kind, storage_key, public_url, sha256, actual_mime_type, byte_size, status) "
                + "values (?, 'AUDIO', ?, 'https://example.test/media', ?, 'audio/mpeg', 1, 'READY') returning id",
            Long.class,
            versionId,
            "media/" + versionId,
            "a".repeat(64)
        );
    }

    private void assertUnique(String table, String columns, ThrowingRunnable duplicateInsert) {
        assertThatThrownBy(duplicateInsert::run)
            .hasMessageContaining("duplicate key value violates unique constraint")
            .hasMessageContaining(table);
        assertThat(columns).isNotBlank();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

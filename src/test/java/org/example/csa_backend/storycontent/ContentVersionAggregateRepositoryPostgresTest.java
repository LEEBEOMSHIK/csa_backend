package org.example.csa_backend.storycontent;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "spring.datasource.hikari.maximum-pool-size=2",
    "spring.datasource.hikari.minimum-idle=0"
})
class ContentVersionAggregateRepositoryPostgresTest {

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
    private ContentVersionAggregateRepository aggregateRepository;

    @Autowired
    private ContentVersionValidator validator;

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void roundTripsMultipleParentsWithAStatementBound() {
        long versionId = insertCompleteAggregate();
        Statistics statistics = entityManager.getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();
        statistics.clear();
        entityManager.clear();

        ContentVersionAggregate aggregate = aggregateRepository.findForPublish(versionId).orElseThrow();

        assertThat(aggregate.version().getStatus()).isEqualTo(ContentVersionStatus.APPROVED);
        assertThat(aggregate.version().getLockVersion()).isZero();
        assertThat(aggregate.scenesInOrder()).hasSize(3);
        assertThat(aggregate.localizedContents()).hasSize(6);
        assertThat(aggregate.assets()).hasSize(1);
        assertThat(aggregate.layersInOrder()).hasSize(3);
        assertThat(aggregate.audioCues()).hasSize(6);
        assertThat(aggregate.audioVariants()).hasSize(12);
        assertThat(aggregate.locales()).hasSize(1);
        assertThat(aggregate.renditions()).hasSize(3);
        assertThat(aggregate.renditionVariants()).hasSize(6);
        assertThat(aggregate.scenesInOrder())
            .extracting(Scene::getSceneKey)
            .containsExactly("opening", "middle", "ending");
        assertThat(aggregate.localizedContents())
            .extracting(SceneLocalizedContent::getLocale)
            .containsExactly("ja", "ko", "ja", "ko", "ja", "ko");
        assertThat(aggregate.layersInOrder())
            .extracting(Layer::getLayerKey)
            .containsExactly("hero-0", "hero-1", "hero-2");
        assertThat(aggregate.audioCues())
            .extracting(AudioCue::getCueKey)
            .containsExactly(
                "narration-0-0", "narration-0-1",
                "narration-1-0", "narration-1-1",
                "narration-2-0", "narration-2-1"
            );
        assertThat(aggregate.audioVariants())
            .extracting(AudioVariant::getVoiceType)
            .containsExactly(
                "dad", "mom", "dad", "mom", "dad", "mom",
                "dad", "mom", "dad", "mom", "dad", "mom"
            );
        assertThat(aggregate.renditions())
            .extracting(Rendition::getType)
            .containsExactly(RenditionType.SLIDE, RenditionType.VIDEO, RenditionType.INTERACTIVE);
        assertThat(aggregate.renditionVariants())
            .extracting(RenditionVariant::getVoiceType)
            .containsExactly("dad", "mom", "dad", "mom", "dad", "mom");
        assertThat(aggregate.layersInOrder().get(0).getX()).isEqualByComparingTo(new BigDecimal("0.125000"));
        assertThat(aggregate.layersInOrder().get(0).getPropertiesJson()).containsEntry("slot", "hero-0");
        assertThat(aggregate.assets().get(0).getSha256()).hasSize(64);
        assertThat(aggregate.renditionVariants().get(0).getSourceRevision()).isEqualTo(7);
        assertThat(validator.validateForPublish(aggregate).valid()).isTrue();
        assertThat(statistics.getPrepareStatementCount()).isBetween(1L, 10L);
        assertThat(aggregateRepository.findPublished(versionId)).isEmpty();

        jdbc.update("update front.story_content_versions set status = 'PUBLISHED' where id = ?", versionId);
        entityManager.clear();

        assertThat(aggregateRepository.findPublished(versionId))
            .get()
            .extracting(loaded -> loaded.version().getStatus())
            .isEqualTo(ContentVersionStatus.PUBLISHED);
    }

    @Test
    void implementationIsReadOnlyTransactional() {
        Transactional transactional = JpaContentVersionAggregateRepository.class
            .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private long insertCompleteAggregate() {
        String marker = UUID.randomUUID().toString();
        long storyId = jdbc.queryForObject(
            "insert into front.stories "
                + "(origin, visibility, title_ko, title_ja, created_at, updated_at) "
                + "values ('CURATED', 'PUBLISHED', ?, ?, now(), now()) returning id",
            Long.class,
            "aggregate-" + marker,
            "aggregate-ja-" + marker
        );
        long versionId = jdbc.queryForObject(
            "insert into front.story_content_versions "
                + "(story_id, version_no, status, schema_version, source_revision, created_at, updated_at) "
                + "values (?, 1, 'APPROVED', 1, 7, now(), now()) returning id",
            Long.class,
            storyId
        );
        jdbc.update(
            "insert into front.story_version_locales (version_id, locale, default_voice_type) "
                + "values (?, 'ko', 'dad')",
            versionId
        );
        long assetId = jdbc.queryForObject(
            "insert into front.media_assets "
                + "(owner_version_id, kind, storage_key, public_url, sha256, actual_mime_type, byte_size, status) "
                + "values (?, 'IMAGE', ?, ?, ?, 'image/png', 1, 'READY') returning id",
            Long.class,
            versionId,
            "aggregate/" + marker,
            "https://example.test/" + marker,
            "a".repeat(64)
        );
        String[] sceneKeys = {"opening", "middle", "ending"};
        for (int sceneIndex = 0; sceneIndex < sceneKeys.length; sceneIndex++) {
            long sceneId = jdbc.queryForObject(
                "insert into front.story_scenes "
                    + "(version_id, scene_key, order_index, width, height, fallback_asset_id) "
                    + "values (?, ?, ?, 100, 100, ?) returning id",
                Long.class,
                versionId,
                sceneKeys[sceneIndex],
                sceneIndex,
                assetId
            );
            jdbc.update(
                "insert into front.scene_localized_contents (scene_id, locale, display_text, script_text) "
                    + "values (?, 'ko', '한국어', '한국어'), (?, 'ja', '日本語', '日本語')",
                sceneId,
                sceneId
            );
            jdbc.update(
                "insert into front.story_layers "
                    + "(scene_id, layer_key, type, z_index, asset_id, x, properties_json) "
                    + "values (?, ?, 'IMAGE', ?, ?, 0.125, cast(? as jsonb))",
                sceneId,
                "hero-" + sceneIndex,
                sceneIndex + 1,
                assetId,
                "{\"slot\":\"hero-" + sceneIndex + "\"}"
            );
            for (int cueIndex = 0; cueIndex < 2; cueIndex++) {
                long cueId = jdbc.queryForObject(
                    "insert into front.scene_audio_cues (scene_id, cue_key, role, required) "
                        + "values (?, ?, 'NARRATION', true) returning id",
                    Long.class,
                    sceneId,
                    "narration-" + sceneIndex + "-" + cueIndex
                );
                jdbc.update(
                    "insert into front.audio_variants "
                        + "(audio_cue_id, locale, voice_type, asset_id, status) "
                        + "values (?, 'ko', 'dad', ?, 'READY'), (?, 'ko', 'mom', ?, 'READY')",
                    cueId,
                    assetId,
                    cueId,
                    assetId
                );
            }
        }
        String[] renditionTypes = {"SLIDE", "VIDEO", "INTERACTIVE"};
        for (int renditionIndex = 0; renditionIndex < renditionTypes.length; renditionIndex++) {
            long renditionId = jdbc.queryForObject(
                "insert into front.content_renditions "
                    + "(version_id, type, status, manifest_asset_id, compatibility_fallback) "
                    + "values (?, ?, 'READY', ?, ?) returning id",
                Long.class,
                versionId,
                renditionTypes[renditionIndex],
                assetId,
                renditionIndex == 0
            );
            jdbc.update(
                "insert into front.content_rendition_variants "
                    + "(rendition_id, locale, voice_type, output_asset_id, output_mode, status, source_revision) "
                    + "values (?, 'ko', 'dad', ?, 'GENERATED', 'READY', 7), "
                    + "(?, 'ko', 'mom', ?, 'GENERATED', 'READY', 7)",
                renditionId,
                assetId,
                renditionId,
                assetId
            );
        }
        return versionId;
    }
}

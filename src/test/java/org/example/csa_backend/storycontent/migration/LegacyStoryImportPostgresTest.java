package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.example.csa_backend.fairytale.FairytaleService;
import org.example.csa_backend.fairytale.dto.CuratedSlidesResponse;
import org.example.csa_backend.fairytale.dto.FairytaleGenerateResponse;
import org.example.csa_backend.fairytale.service.AiFairytaleService;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.storycontent.LegacyStoryLinkRepository;
import org.example.csa_backend.storycontent.LegacyAiReadAdapter;
import org.example.csa_backend.storycontent.LegacyCuratedReadAdapter;
import org.example.csa_backend.storycontent.LegacyType;
import org.example.csa_backend.storycontent.StoryRuntimeService;
import org.example.csa_backend.storycontent.StoryRuntimeException;
import org.example.csa_backend.storycontent.dto.RuntimeCapabilities;
import org.example.csa_backend.storycontent.dto.StoryRuntimeManifestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
class LegacyStoryImportPostgresTest {

    private static final Path MEDIA_ROOT = createMediaRoot();

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
        registry.add("csa.media.storage-mode", () -> "local");
        registry.add("csa.media.storage-root", () -> MEDIA_ROOT.toString());
        registry.add("csa.media.public-base-url", () -> "http://localhost:18080/uploads");
        registry.add("storage.local-base-path", () -> MEDIA_ROOT.resolve("legacy-ai").toString());
        registry.add("storage.server-base-url", () -> "http://localhost:18080");
        registry.add("csa.migration.shadow-read-enabled", () -> true);
    }

    @Autowired
    private LegacyStoryImportService importService;

    @Autowired
    private LegacyStoryLinkRepository linkRepository;

    @Autowired
    private StoryRuntimeService runtimeService;

    @Autowired
    private FairytaleService fairytaleService;

    @Autowired
    private AiFairytaleService aiFairytaleService;

    @Autowired
    private ContentCutoverService cutoverService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContractChecksum checksum;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private LegacyStoryProjectionMapper projectionMapper;

    @MockitoSpyBean
    private LegacyMediaSnapshotStore mediaStore;

    @Autowired
    private CanonicalStoryWriter canonicalWriter;

    @Autowired
    private LegacyCuratedReadAdapter curatedReadAdapter;

    @Autowired
    private LegacyAiReadAdapter aiReadAdapter;

    @Autowired
    private LegacyContractNormalizer contractNormalizer;

    @Autowired
    private LegacyShadowCompareService shadowCompareService;

    @BeforeEach
    void createReadableLegacyMedia() throws IOException {
        Files.createDirectories(MEDIA_ROOT.resolve("legacy/curated/41"));
        Files.write(MEDIA_ROOT.resolve("legacy/curated/41/page-0.png"), "image-41".getBytes());
        Files.write(MEDIA_ROOT.resolve("legacy/curated/41/page-0-dad-ko.mp3"), "audio-ko-41".getBytes());
        Files.write(MEDIA_ROOT.resolve("legacy/curated/41/page-0-dad-ja.mp3"), "audio-ja-41".getBytes());
    }

    @AfterEach
    void resetFutureDatedDeltaFixtures() {
        jdbc.update(
            "update front.curated_fairytale_pages set mod_dt = cre_dt "
                + "where fairytale_id in (44, 65, 66) and mod_dt > ?",
            Timestamp.from(Instant.parse("2030-01-01T00:00:00Z"))
        );
        jdbc.update(
            "update front.ai_fairytale_pages set mod_dt = cre_dt "
                + "where ai_fairytale_id = 740 and mod_dt > ?",
            Timestamp.from(Instant.parse("2030-01-01T00:00:00Z"))
        );
        jdbc.update(
            "update front.curated_fairytale_pages set image_url = ?, mod_dt = cre_dt "
                + "where fairytale_id = 59",
            "/uploads/legacy/curated/59/page-0.png"
        );
    }

    @Test
    void importsReadableCuratedSnapshotAndServesPublishedRuntime() {
        insertCurated(41L);

        ImportBatchResult result = importService.importCuratedBatch(40, 1);

        assertThat(result.imported()).isEqualTo(1);
        long storyId = linkRepository.findByLegacyTypeAndLegacyId(LegacyType.CURATED, 41L)
            .orElseThrow()
            .getStoryId();
        StoryRuntimeManifestResponse runtime = runtimeService.getPublishedRuntime(
            storyId,
            new RuntimeCapabilities("SLIDE", "ko", "dad", List.of(1), List.of("SLIDE"), 1)
        );
        assertThat(runtime.origin()).isEqualTo("CURATED");
        assertThat(runtime.scenes()).singleElement().satisfies(scene -> {
            assertThat(scene.text()).containsEntry("ko", "첫 장면").containsEntry("ja", "最初の場面");
            assertThat(scene.fallbackAssetKey()).isNotBlank();
        });
    }

    @Test
    void pageLessCuratedIsQuarantinedWithoutAbortingBatchAndRepairsOnSameStory() throws IOException {
        insertCuratedParent(750L);
        writeCuratedMedia(751L);
        insertCurated(751L);

        ImportBatchResult batch = importService.importCuratedBatch(749, 2);
        var quarantinedLink = linkRepository
            .findByLegacyTypeAndLegacyId(LegacyType.CURATED, 750L)
            .orElseThrow();

        assertThat(batch.imported()).isEqualTo(2);
        assertThat(batch.unchanged()).isZero();
        assertThat(quarantinedLink.getLegacyStatusCode()).isEqualTo("INCOMPLETE_PAGES");
        assertThat(versionStatus(quarantinedLink.getContentVersionId())).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject(
            "select visibility from front.stories where id = ?",
            String.class,
            quarantinedLink.getStoryId()
        )).isEqualTo("ARCHIVED");
        assertThat(jdbc.queryForObject(
            "select published_version_id from front.stories where id = ?",
            Long.class,
            quarantinedLink.getStoryId()
        )).isNull();
        assertThat(jdbc.queryForObject(
            "select (select count(*) from front.story_scenes where version_id = ?) "
                + "+ (select count(*) from front.content_renditions where version_id = ?) "
                + "+ (select count(*) from front.media_assets where owner_version_id = ?)",
            Integer.class,
            quarantinedLink.getContentVersionId(),
            quarantinedLink.getContentVersionId(),
            quarantinedLink.getContentVersionId()
        )).isZero();
        assertThat(linkRepository.findPublishedStoryId("CURATED", 750L)).isEmpty();
        assertThat(linkRepository.findPublishedStoryId("CURATED", 751L)).isPresent();
        var legacySnapshot = contractNormalizer.normalize(curatedReadAdapter.readLegacy(750L));
        assertThat(legacySnapshot.at("/migration/visibility").asText()).isEqualTo("ARCHIVED");
        assertThat(legacySnapshot.at("/migration/legacyStatusCode").asText())
            .isEqualTo("INCOMPLETE_PAGES");
        assertThat(legacySnapshot.path("curatedSlides").isNull()).isTrue();
        assertThat(shadowCompareService.compare(LegacyType.CURATED, 750L).matches()).isTrue();

        long quarantinedStoryId = quarantinedLink.getStoryId();
        long quarantinedVersionId = quarantinedLink.getContentVersionId();
        writeCuratedMedia(750L);
        insertCuratedPage(750L);

        ImportBatchResult repair = importService.importCuratedBatch(749, 1);
        var repairedLink = linkRepository
            .findByLegacyTypeAndLegacyId(LegacyType.CURATED, 750L)
            .orElseThrow();

        assertThat(repair.imported()).isEqualTo(1);
        assertThat(repairedLink.getStoryId()).isEqualTo(quarantinedStoryId);
        assertThat(repairedLink.getContentVersionId()).isNotEqualTo(quarantinedVersionId);
        assertThat(versionStatus(quarantinedVersionId)).isEqualTo("SUPERSEDED");
        assertThat(versionStatus(repairedLink.getContentVersionId())).isEqualTo("PUBLISHED");
        assertThat(linkRepository.findPublishedStoryId("CURATED", 750L))
            .contains(quarantinedStoryId);
        assertThat(shadowCompareService.compare(LegacyType.CURATED, 750L).matches()).isTrue();
    }

    @Test
    void identicalCuratedReplayIsACompleteNoOp() throws IOException {
        writeCuratedMedia(42L);
        insertCurated(42L);
        ImportBatchResult first = importService.importCuratedBatch(41, 1);
        ImportState beforeReplay = importState(LegacyType.CURATED, 42L);
        Map<String, FileState> filesBeforeReplay = canonicalFileState(42L);

        ImportBatchResult replay = importService.importCuratedBatch(41, 1);
        ImportState afterReplay = importState(LegacyType.CURATED, 42L);
        Map<String, FileState> filesAfterReplay = canonicalFileState(42L);

        assertThat(first.imported()).isEqualTo(1);
        assertThat(replay.imported()).isZero();
        assertThat(replay.unchanged()).isEqualTo(1);
        assertThat(afterReplay)
            .usingRecursiveComparison()
            .ignoringFields("watermarkAt")
            .isEqualTo(beforeReplay);
        assertThat(afterReplay.watermarkAt()).isAfter(beforeReplay.watermarkAt());
        assertThat(filesAfterReplay).isEqualTo(filesBeforeReplay);
    }

    @Test
    void samePathLegacyMediaByteChangeIsDetectedWithoutDatabaseTimestamp() throws IOException {
        writeCuratedMedia(752L);
        insertCurated(752L);
        importService.importCuratedBatch(751, 1);
        ImportState original = importState(LegacyType.CURATED, 752L);
        Map<String, FileState> originalFiles = canonicalFileState(752L);
        Files.write(
            MEDIA_ROOT.resolve("legacy/curated/752/page-0.png"),
            "image-752-mutated".getBytes()
        );

        String driftedSourceHash = importService.sourceHashes(LegacyType.CURATED).get(752L);

        assertThat(driftedSourceHash).isNotEqualTo(original.sourceHash());
        ImportBatchResult replay = importService.importCuratedBatch(751, 1);
        ImportState replacement = importState(LegacyType.CURATED, 752L);
        assertThat(replay.imported()).isEqualTo(1);
        assertThat(replacement.storyId()).isEqualTo(original.storyId());
        assertThat(replacement.contentVersionId()).isNotEqualTo(original.contentVersionId());
        assertThat(replacement.sourceHash()).isEqualTo(driftedSourceHash);
        assertThat(versionStatus(original.contentVersionId())).isEqualTo("SUPERSEDED");
        assertThat(canonicalFileState(752L)).containsAllEntriesOf(originalFiles);
        assertThat(jdbc.queryForObject(
            "select trim(a.sha256) from front.story_scenes s "
                + "join front.media_assets a on a.id = s.fallback_asset_id where s.version_id = ?",
            String.class,
            replacement.contentVersionId()
        )).isEqualTo(checksum.ofBytes("image-752-mutated".getBytes()));
    }

    @Test
    void changedChildCreatesANewImmutableCanonicalVersion() throws IOException {
        writeCuratedMedia(43L);
        insertCurated(43L);
        importService.importCuratedBatch(42, 1);
        ImportState original = importState(LegacyType.CURATED, 43L);
        ManifestFile originalManifest = manifestFile(original.contentVersionId());

        jdbc.update(
            "update front.curated_fairytale_pages set text_ko = '변경된 첫 장면' "
                + "where fairytale_id = 43 and page_index = 0"
        );
        ImportBatchResult changed = importService.importCuratedBatch(42, 1);
        ImportState replacement = importState(LegacyType.CURATED, 43L);

        assertThat(changed.imported()).isEqualTo(1);
        assertThat(replacement.storyId()).isEqualTo(original.storyId());
        assertThat(replacement.contentVersionId()).isNotEqualTo(original.contentVersionId());
        assertThat(replacement.publishedVersionId()).isEqualTo(replacement.contentVersionId());
        assertThat(replacement.versionCount()).isEqualTo(2);
        assertThat(versionStatus(original.contentVersionId())).isEqualTo("SUPERSEDED");
        assertThat(versionStatus(replacement.contentVersionId())).isEqualTo("PUBLISHED");
        assertThat(manifestFile(original.contentVersionId())).isEqualTo(originalManifest);

        StoryRuntimeManifestResponse runtime = runtimeService.getPublishedRuntime(
            replacement.storyId(),
            new RuntimeCapabilities("SLIDE", "ko", "dad", List.of(1), List.of("SLIDE"), 1)
        );
        assertThat(runtime.contentVersionId()).isEqualTo(replacement.contentVersionId());
        assertThat(runtime.scenes()).singleElement()
            .satisfies(scene -> assertThat(scene.text()).containsEntry("ko", "변경된 첫 장면"));
    }

    @Test
    void deltaImportDetectsAChildOnlyChangeAfterTheWatermark() throws IOException {
        writeCuratedMedia(44L);
        insertCurated(44L);
        importService.importCuratedBatch(43, 1);
        ImportState original = importState(LegacyType.CURATED, 44L);
        Instant watermark = Instant.parse("2030-01-01T00:00:00Z");
        jdbc.update(
            "update front.curated_fairytale_pages set text_ko = 'delta child', mod_dt = ? "
                + "where fairytale_id = 44 and page_index = 0",
            Timestamp.from(Instant.parse("2031-01-01T00:00:00Z"))
        );

        ImportBatchResult delta = importService.importDelta(watermark);
        ImportState replacement = importState(LegacyType.CURATED, 44L);

        assertThat(delta.imported()).isEqualTo(1);
        assertThat(replacement.storyId()).isEqualTo(original.storyId());
        assertThat(replacement.contentVersionId()).isNotEqualTo(original.contentVersionId());
        assertThat(versionStatus(original.contentVersionId())).isEqualTo("SUPERSEDED");
    }

    @Test
    void deltaFingerprintSweepConvergesCategoryRenameAndSamePathMediaBytesWithoutTimestamps()
        throws IOException {
        long id = 760L;
        writeCuratedMedia(id);
        insertCurated(id);
        importService.importCuratedBatch(id - 1, 1);
        ImportState original = importState(LegacyType.CURATED, id);
        jdbc.update(
            "insert into front.categories (category_key, name_ko, name_ja, cre_dt, cre_id, del_yn) "
                + "values ('delta-category-760', '변경', '変更', now(), 'test', 'N')"
        );
        jdbc.update(
            "insert into front.fairytale_categories (fairytale_id, category_id) "
                + "select ?, id from front.categories where category_key = 'delta-category-760'",
            id
        );
        Instant futureTimestampWatermark = Instant.parse("2099-01-01T00:00:00Z");

        ImportBatchResult membership = importService.importDelta(futureTimestampWatermark);
        ImportState afterMembership = importState(LegacyType.CURATED, id);

        assertThat(membership.imported()).isEqualTo(1);
        assertThat(membership.unchanged()).isZero();
        assertThat(afterMembership.storyId()).isEqualTo(original.storyId());
        assertThat(afterMembership.contentVersionId()).isNotEqualTo(original.contentVersionId());
        jdbc.update(
            "update front.categories set category_key = 'delta-category-renamed-760' "
                + "where category_key = 'delta-category-760'"
        );

        ImportBatchResult renamed = importService.importDelta(futureTimestampWatermark);
        ImportState afterRename = importState(LegacyType.CURATED, id);

        assertThat(renamed.imported()).isEqualTo(1);
        assertThat(afterRename.contentVersionId()).isNotEqualTo(afterMembership.contentVersionId());
        Files.write(
            MEDIA_ROOT.resolve("legacy/curated/760/page-0.png"),
            "same-path-new-bytes-760".getBytes()
        );

        ImportBatchResult mediaChanged = importService.importDelta(futureTimestampWatermark);
        ImportState afterMedia = importState(LegacyType.CURATED, id);

        assertThat(mediaChanged.imported()).isEqualTo(1);
        assertThat(afterMedia.contentVersionId()).isNotEqualTo(afterRename.contentVersionId());
        Timestamp beforeNoOp = jdbc.queryForObject(
            "select watermark_at from front.legacy_migration_watermarks where migration_kind = 'CURATED'",
            Timestamp.class
        );

        ImportBatchResult noOp = importService.importDelta(futureTimestampWatermark);
        Timestamp afterNoOp = jdbc.queryForObject(
            "select watermark_at from front.legacy_migration_watermarks where migration_kind = 'CURATED'",
            Timestamp.class
        );
        String snapshotHash = jdbc.queryForObject(
            "select trim(snapshot_hash) from front.legacy_migration_watermarks "
                + "where migration_kind = 'CURATED'",
            String.class
        );

        assertThat(noOp.imported()).isZero();
        assertThat(noOp.unchanged()).isZero();
        assertThat(afterNoOp).isAfter(beforeNoOp);
        assertThat(snapshotHash).matches("[0-9a-f]{64}");
    }

    @Test
    void deltaSoftDeletedAudioAndPageStageRepairWithoutReplacingPublishedPointer() throws IOException {
        long id = 761L;
        writeCuratedMedia(id);
        insertCurated(id);
        importService.importCuratedBatch(id - 1, 1);
        ImportState published = importState(LegacyType.CURATED, id);
        ManifestFile publishedManifest = manifestFile(published.contentVersionId());
        Instant futureTimestampWatermark = Instant.parse("2099-01-01T00:00:00Z");
        jdbc.update(
            "update front.curated_fairytale_audios set del_yn = 'Y', mod_dt = now() "
                + "where page_id in (select id from front.curated_fairytale_pages where fairytale_id = ?) "
                + "and locale = 'ja'",
            id
        );

        ImportBatchResult audioDeleted = importService.importDelta(futureTimestampWatermark);
        ImportState audioDraft = importState(LegacyType.CURATED, id);

        assertThat(audioDeleted.imported()).isEqualTo(1);
        assertThat(audioDraft.contentVersionId()).isNotEqualTo(published.contentVersionId());
        assertThat(versionStatus(audioDraft.contentVersionId())).isEqualTo("DRAFT");
        assertThat(audioDraft.publishedVersionId()).isEqualTo(published.contentVersionId());
        assertThat(manifestFile(published.contentVersionId())).isEqualTo(publishedManifest);
        jdbc.update(
            "update front.curated_fairytale_audios set del_yn = 'N', mod_dt = now() "
                + "where page_id in (select id from front.curated_fairytale_pages where fairytale_id = ?) "
                + "and locale = 'ja'",
            id
        );
        assertThat(importService.importDelta(futureTimestampWatermark).imported()).isEqualTo(1);
        ImportState audioRepaired = importState(LegacyType.CURATED, id);
        assertThat(audioRepaired.publishedVersionId()).isEqualTo(audioRepaired.contentVersionId());
        jdbc.update(
            "update front.curated_fairytale_pages set del_yn = 'Y', mod_dt = now() where fairytale_id = ?",
            id
        );

        assertThat(importService.importDelta(futureTimestampWatermark).imported()).isEqualTo(1);
        ImportState pageDraft = importState(LegacyType.CURATED, id);

        assertThat(versionStatus(pageDraft.contentVersionId())).isEqualTo("DRAFT");
        assertThat(pageDraft.publishedVersionId()).isEqualTo(audioRepaired.contentVersionId());
        assertThat(manifestFile(published.contentVersionId())).isEqualTo(publishedManifest);
        jdbc.update(
            "update front.curated_fairytale_pages set del_yn = 'N', mod_dt = now() where fairytale_id = ?",
            id
        );

        assertThat(importService.importDelta(futureTimestampWatermark).imported()).isEqualTo(1);
        ImportState pageRepaired = importState(LegacyType.CURATED, id);
        assertThat(pageRepaired.publishedVersionId()).isEqualTo(pageRepaired.contentVersionId());
        assertThat(versionStatus(pageDraft.contentVersionId())).isEqualTo("SUPERSEDED");
    }

    @Test
    void noOpDeltaAdvancesToOneFixedDatabaseThroughAndStoresSnapshotHashes() throws IOException {
        long id = 762L;
        writeCuratedMedia(id);
        insertCurated(id);
        importService.importCuratedBatch(id - 1, 1);
        jdbc.update(
            "update front.legacy_migration_watermarks set watermark_at = timestamp '2000-01-01', "
                + "snapshot_hash = null where migration_kind in ('CURATED', 'AI')"
        );
        Instant lowerBound = jdbc.queryForObject(
            "select clock_timestamp()",
            Timestamp.class
        ).toInstant();

        ImportBatchResult noOp = importService.importDelta(Instant.parse("2099-01-01T00:00:00Z"));

        Instant upperBound = jdbc.queryForObject(
            "select clock_timestamp()",
            Timestamp.class
        ).toInstant();
        List<Map<String, Object>> watermarks = jdbc.queryForList(
            "select migration_kind, watermark_at, trim(snapshot_hash) as snapshot_hash "
                + "from front.legacy_migration_watermarks where migration_kind in ('CURATED', 'AI') "
                + "order by migration_kind"
        );
        assertThat(noOp.imported()).isZero();
        assertThat(noOp.unchanged()).isZero();
        assertThat(watermarks).hasSize(2).allSatisfy(row -> {
            Instant watermarkAt = ((Timestamp) row.get("watermark_at")).toInstant();
            assertThat(watermarkAt).isBetween(lowerBound, upperBound);
            assertThat((String) row.get("snapshot_hash")).matches("[0-9a-f]{64}");
        });
        assertThat(watermarks.get(0).get("watermark_at"))
            .isEqualTo(watermarks.get(1).get("watermark_at"));
    }

    @Test
    void curatedAndAiWithTheSameNumericIdRemainDistinctAndOnlyPublicStoryIsServed() throws IOException {
        writeCuratedMedia(45L);
        insertCurated(45L);
        writeAiMedia(45L, false);
        insertAi(45L, "COMPLETED", "slide", false);

        importService.importCuratedBatch(44, 1);
        importService.importAiBatch(44, 1);

        var curated = linkRepository.findByLegacyTypeAndLegacyId(LegacyType.CURATED, 45L).orElseThrow();
        var ai = linkRepository.findByLegacyTypeAndLegacyId(LegacyType.AI, 45L).orElseThrow();
        assertThat(ai.getStoryId()).isNotEqualTo(curated.getStoryId());
        assertThat(ai.getContentVersionId()).isNotEqualTo(curated.getContentVersionId());
        assertThat(versionStatus(curated.getContentVersionId())).isEqualTo("PUBLISHED");
        assertThat(versionStatus(ai.getContentVersionId())).isEqualTo("PUBLISHED");

        StoryRuntimeManifestResponse curatedRuntime = runtimeService.getPublishedRuntime(
            curated.getStoryId(),
            new RuntimeCapabilities("SLIDE", "ko", "dad", List.of(1), List.of("SLIDE"), 1)
        );
        assertThat(curatedRuntime.origin()).isEqualTo("CURATED");
        assertThatThrownBy(() -> runtimeService.getPublishedRuntime(
            ai.getStoryId(),
            new RuntimeCapabilities("SLIDE", "ko", "dad", List.of(1), List.of("SLIDE"), 1)
        )).isInstanceOf(StoryRuntimeException.class);
    }

    @Test
    void importsEveryApprovedAiLifecycleAndRenderJobMapping() throws IOException {
        insertAiCase(51L, "COMPLETED", "slide", true, true, false);
        insertAiCase(52L, "COMPLETED", "video", true, true, true);
        insertAiCase(53L, "PENDING", "slide", true, false, false);
        insertAiCase(54L, "GENERATING", "slide", true, false, false);
        insertAiCase(55L, "FAILED", "slide", true, false, false);
        insertAiCase(56L, "FAILED", "video", true, true, false);

        ImportBatchResult result = importService.importAiBatch(50, 6);

        assertThat(result.imported()).isEqualTo(6);
        assertAiState(51L, "PUBLISHED", true,
            List.of("CONTENT_GENERATION:SUCCEEDED"), List.of("SLIDE:READY"));
        assertAiState(52L, "PUBLISHED", true,
            List.of("CONTENT_GENERATION:SUCCEEDED", "VIDEO_RENDER:SUCCEEDED"),
            List.of("SLIDE:READY", "VIDEO:READY"));
        assertAiState(53L, "DRAFT", false,
            List.of("CONTENT_GENERATION:QUEUED"), List.of());
        assertAiState(54L, "DRAFT", false,
            List.of("CONTENT_GENERATION:RUNNING"), List.of());
        assertAiState(55L, "DRAFT", false,
            List.of("CONTENT_GENERATION:FAILED"), List.of());
        assertAiState(56L, "DRAFT", false,
            List.of("CONTENT_GENERATION:SUCCEEDED", "VIDEO_RENDER:FAILED"),
            List.of("SLIDE:READY"));
    }

    @Test
    void nullOwnerAiIsArchivedWithoutAPublicPointerOrRuntime() {
        insertAiCase(57L, "COMPLETED", "slide", false, true, false);

        ImportBatchResult result = importService.importAiBatch(56, 1);

        assertThat(result.imported()).isEqualTo(1);
        var link = linkRepository.findByLegacyTypeAndLegacyId(LegacyType.AI, 57L).orElseThrow();
        assertThat(versionStatus(link.getContentVersionId())).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject(
            "select visibility from front.stories where id = ?",
            String.class,
            link.getStoryId()
        )).isEqualTo("ARCHIVED");
        assertThat(jdbc.queryForObject(
            "select published_version_id from front.stories where id = ?",
            Long.class,
            link.getStoryId()
        )).isNull();
        assertThatThrownBy(() -> runtimeService.getPublishedRuntime(
            link.getStoryId(),
            new RuntimeCapabilities("SLIDE", "ko", "dad", List.of(1), List.of("SLIDE"), 1)
        )).isInstanceOfSatisfying(
            StoryRuntimeException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("STORY_NOT_FOUND")
        );
    }

    @Test
    void incompleteCuratedImportStagesADraftAndRepairsWithoutPublicExposure() throws IOException {
        writeCuratedMedia(58L);
        insertCurated(58L);
        jdbc.update(
            "update front.curated_fairytale_audios set del_yn = 'Y' "
                + "where locale = 'ja' and page_id in "
                + "(select id from front.curated_fairytale_pages where fairytale_id = 58)"
        );

        ImportBatchResult staged = importService.importCuratedBatch(57, 1);
        var stagedLink = linkRepository.findByLegacyTypeAndLegacyId(LegacyType.CURATED, 58L).orElseThrow();

        assertThat(staged.imported()).isEqualTo(1);
        assertThat(versionStatus(stagedLink.getContentVersionId())).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject(
            "select published_version_id from front.stories where id = ?",
            Long.class,
            stagedLink.getStoryId()
        )).isNull();
        assertThatThrownBy(() -> runtimeService.getPublishedRuntime(
            stagedLink.getStoryId(),
            new RuntimeCapabilities("SLIDE", "ko", "dad", List.of(1), List.of("SLIDE"), 1)
        )).isInstanceOfSatisfying(
            StoryRuntimeException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("PUBLISHED_MANIFEST_UNAVAILABLE")
        );
        assertThat(fairytaleService.getFairytales(null, "latest").stream()
            .filter(item -> item.id().equals(58L))
            .findFirst().orElseThrow().canonicalStoryId()).isNull();

        jdbc.update(
            "update front.curated_fairytale_audios set del_yn = 'N', mod_dt = now() "
                + "where locale = 'ja' and page_id in "
                + "(select id from front.curated_fairytale_pages where fairytale_id = 58)"
        );
        ImportBatchResult repaired = importService.importCuratedBatch(57, 1);
        var repairedLink = linkRepository.findByLegacyTypeAndLegacyId(LegacyType.CURATED, 58L).orElseThrow();

        assertThat(repaired.imported()).isEqualTo(1);
        assertThat(repairedLink.getStoryId()).isEqualTo(stagedLink.getStoryId());
        assertThat(repairedLink.getContentVersionId()).isNotEqualTo(stagedLink.getContentVersionId());
        assertThat(versionStatus(stagedLink.getContentVersionId())).isEqualTo("SUPERSEDED");
        assertThat(versionStatus(repairedLink.getContentVersionId())).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject(
            "select published_version_id from front.stories where id = ?",
            Long.class,
            repairedLink.getStoryId()
        )).isEqualTo(repairedLink.getContentVersionId());
    }

    @Test
    void failedMediaPreflightLeavesLinkPointerVersionAndWatermarkUnchanged() throws IOException {
        writeCuratedMedia(59L);
        insertCurated(59L);
        importService.importCuratedBatch(58, 1);
        ImportState beforeFailure = importState(LegacyType.CURATED, 59L);

        jdbc.update(
            "update front.curated_fairytale_pages set image_url = ?, mod_dt = now() "
                + "where fairytale_id = 59 and page_index = 0",
            "/uploads/legacy/curated/59/missing.png"
        );

        assertThatThrownBy(() -> importService.importCuratedBatch(58, 1))
            .isInstanceOfSatisfying(
                LegacyImportException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("LEGACY_MEDIA_PREFLIGHT_FAILED")
            );
        assertThat(importState(LegacyType.CURATED, 59L)).isEqualTo(beforeFailure);
    }

    @Test
    void changedIncompleteSourceKeepsExistingPublishedPointerAndBytesUntilRepair() throws IOException {
        writeCuratedMedia(68L);
        insertCurated(68L);
        importService.importCuratedBatch(67, 1);
        ImportState published = importState(LegacyType.CURATED, 68L);
        ManifestFile publishedManifest = manifestFile(published.contentVersionId());
        List<Long> publishedMediaAssetIds = runtimeMediaAssetIds(published.contentVersionId());
        jdbc.update(
            "update front.curated_fairytale_audios set del_yn = 'Y', mod_dt = now() "
                + "where locale = 'ja' and page_id in "
                + "(select id from front.curated_fairytale_pages where fairytale_id = 68)"
        );

        ImportBatchResult result = importService.importCuratedBatch(67, 1);
        ImportState staged = importState(LegacyType.CURATED, 68L);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(staged.storyId()).isEqualTo(published.storyId());
        assertThat(staged.contentVersionId()).isNotEqualTo(published.contentVersionId());
        assertThat(staged.publishedVersionId()).isEqualTo(published.contentVersionId());
        assertThat(versionStatus(published.contentVersionId())).isEqualTo("PUBLISHED");
        assertThat(versionStatus(staged.contentVersionId())).isEqualTo("DRAFT");
        assertThat(manifestFile(published.contentVersionId())).isEqualTo(publishedManifest);
        StoryRuntimeManifestResponse runtime = runtimeService.getPublishedRuntime(
            staged.storyId(),
            new RuntimeCapabilities("SLIDE", "ko", "dad", List.of(1), List.of("SLIDE"), 1)
        );
        assertThat(runtime.contentVersionId()).isEqualTo(published.contentVersionId());
        assertThat(fairytaleService.getFairytales(null, "latest").stream()
            .filter(item -> item.id().equals(68L))
            .findFirst().orElseThrow().canonicalStoryId()).isNull();
        Map<String, FileState> filesBeforeRepair = canonicalFileState(68L);

        jdbc.update(
            "update front.curated_fairytale_audios set del_yn = 'N', mod_dt = now() "
                + "where locale = 'ja' and page_id in "
                + "(select id from front.curated_fairytale_pages where fairytale_id = 68)"
        );
        ImportBatchResult repairResult = importService.importCuratedBatch(67, 1);
        ImportState repaired = importState(LegacyType.CURATED, 68L);

        assertThat(repairResult.imported()).isEqualTo(1);
        assertThat(repaired.storyId()).isEqualTo(published.storyId());
        assertThat(repaired.contentVersionId()).isNotIn(
            published.contentVersionId(), staged.contentVersionId());
        assertThat(repaired.publishedVersionId()).isEqualTo(repaired.contentVersionId());
        assertThat(versionStatus(published.contentVersionId())).isEqualTo("SUPERSEDED");
        assertThat(versionStatus(staged.contentVersionId())).isEqualTo("SUPERSEDED");
        assertThat(versionStatus(repaired.contentVersionId())).isEqualTo("PUBLISHED");
        assertThat(manifestFile(published.contentVersionId())).isEqualTo(publishedManifest);
        assertThat(runtimeMediaAssetIds(repaired.contentVersionId()))
            .containsExactlyElementsOf(publishedMediaAssetIds);
        assertThat(repaired.assetCount()).isEqualTo(staged.assetCount() + 1);
        assertThat(canonicalFileState(68L)).containsAllEntriesOf(filesBeforeRepair);
        assertThat(runtimeService.getPublishedRuntime(
            repaired.storyId(),
            new RuntimeCapabilities("SLIDE", "ko", "dad", List.of(1), List.of("SLIDE"), 1)
        ).contentVersionId()).isEqualTo(repaired.contentVersionId());
        assertThat(fairytaleService.getFairytales(null, "latest").stream()
            .filter(item -> item.id().equals(68L))
            .findFirst().orElseThrow().canonicalStoryId()).isEqualTo(repaired.storyId());
    }

    @Test
    void realCuratedAndAiAdaptersMatchTheirLinkedCanonicalGraphs() throws IOException {
        writeCuratedMedia(69L);
        insertCurated(69L);
        insertAiCase(70L, "COMPLETED", "slide", true, true, false);
        jdbc.update(
            "update front.fairytales set rating = 4.75, color_hex = '#123456', "
                + "theme_tag = 'bedtime', character_supported = false, "
                + "cre_dt = timestamp '2024-01-02 03:04:05' where id = 69"
        );
        jdbc.update(
            "update front.fairytale_details set author_ko = '풍부한 작가', author_ja = '豊かな作家', "
                + "age_range = '6-8', duration_min = 7, page_count = 9, "
                + "full_content_ko = '전체 본문', full_content_ja = '全文', content_version = 'legacy-rich-v2' "
                + "where fairytale_id = 69"
        );
        jdbc.update(
            "update front.curated_fairytale_pages set content_version = 'legacy-rich-v2', "
                + "placement_x = 0.18, placement_y = 0.42, placement_width = 0.24, "
                + "placement_height = 0.36, placement_z_index = 2, placement_pose = 'standing', "
                + "placement_flip_x = false where fairytale_id = 69"
        );
        jdbc.update(
            "insert into front.categories (category_key, name_ko, name_ja, cre_dt, cre_id, del_yn) "
                + "values ('bedtime-69', '잠자리', '就寝', now(), 'test', 'N')"
        );
        jdbc.update(
            "insert into front.fairytale_categories (fairytale_id, category_id) "
                + "select 69, id from front.categories where category_key = 'bedtime-69'"
        );
        jdbc.update(
            "update front.ai_fairytales set cre_dt = timestamp '2024-02-03 04:05:06' where id = 70"
        );
        importService.importCuratedBatch(68, 1);
        importService.importAiBatch(69, 1);

        var curatedLegacy = contractNormalizer.normalize(curatedReadAdapter.readLegacy(69L));
        var curatedCanonical = contractNormalizer.normalize(curatedReadAdapter.readCanonical(69L));
        var aiLegacy = contractNormalizer.normalize(aiReadAdapter.readLegacy(70L));
        var aiCanonical = contractNormalizer.normalize(aiReadAdapter.readCanonical(70L));
        assertThat(curatedLegacy.at("/curatedList/rating").asDouble()).isEqualTo(4.75);
        assertThat(curatedLegacy.at("/curatedList/categories").get(0).asText())
            .isEqualTo("bedtime-69");
        assertThat(curatedLegacy.at("/curatedDetail/contentVersion").asText())
            .isEqualTo("legacy-rich-v2");
        assertThat(curatedLegacy.at("/curatedDetail/pageCount").asInt()).isEqualTo(9);
        assertThat(curatedLegacy.at("/curatedSlides/characterSupported").asBoolean()).isFalse();
        assertThat(curatedLegacy.at("/curatedSlides/pages/0/characterPlacement").isNull()).isTrue();
        assertThat(curatedLegacy.at("/curatedSlides/pages/0/imageUrl").asText())
            .isEqualTo("sha256:" + checksum.ofBytes("image-69".getBytes()));
        assertThat(aiLegacy.at("/aiList/thumbnailUrl").asText())
            .isEqualTo("sha256:" + checksum.ofBytes("ai-image-70".getBytes()));
        assertThat(aiLegacy.at("/aiList/pageCount").asInt()).isEqualTo(1);
        assertThat(aiLegacy.at("/aiList/createdAt").asText())
            .isEqualTo("2024-02-03T04:05:06");
        assertThat(aiLegacy.at("/aiSlides/pages/0/audioUrl").asText())
            .isEqualTo("sha256:" + checksum.ofBytes("ai-audio-70".getBytes()));
        assertThat(curatedCanonical).isEqualTo(curatedLegacy);
        assertThat(aiCanonical).isEqualTo(aiLegacy);
        assertThat(jdbc.queryForObject(
            "select count(*) from front.story_layers l join front.story_scenes s on s.id = l.scene_id "
                + "join front.legacy_story_links x on x.content_version_id = s.version_id "
                + "where x.legacy_type = 'CURATED' and x.legacy_id = 69 and l.type = 'CHARACTER_SLOT'",
            Integer.class
        )).isZero();
        assertThat(shadowCompareService.compare(LegacyType.CURATED, 69L).matches()).isTrue();
        assertThat(shadowCompareService.compare(LegacyType.AI, 70L).matches()).isTrue();
        assertThat(jdbc.queryForObject(
            "select count(*) from front.legacy_shadow_mismatches where resolved_at is null "
                + "and ((legacy_type = 'CURATED' and legacy_id = 69) "
                + "or (legacy_type = 'AI' and legacy_id = 70))",
            Integer.class
        )).isZero();
    }

    @Test
    void publicServicesReadCanonicalDtosAndMediaAfterCutoverThenLegacyAfterRollback()
        throws IOException {
        long curatedId = 9_801L;
        long privateAiId = 9_802L;
        long sharedAiId = 9_803L;
        long epoch = 88_001L;
        writeCuratedMedia(curatedId);
        insertCurated(curatedId);
        insertAiCase(privateAiId, "COMPLETED", "slide", true, true, false);
        insertAiCase(sharedAiId, "COMPLETED", "slide", true, true, false);
        jdbc.update("update front.ai_fairytales set shared = 'Y' where id = ?", sharedAiId);
        importService.importCuratedBatch(curatedId - 1, 1);
        importService.importAiBatch(privateAiId - 1, 2);
        jdbc.update(
            "update front.content_migration_control set state = 'CUTOVER_PENDING', "
                + "read_source = 'CANONICAL', write_source = 'CANONICAL', barrier_epoch = ?, "
                + "backend_ack_epoch = ?, admin_backend_ack_epoch = ?, updated_at = now() "
                + "where singleton_id = 1",
            epoch,
            epoch,
            epoch
        );

        try {
            var curatedSummary = fairytaleService.getFairytales(null, "latest").stream()
                .filter(item -> item.id().equals(curatedId)).findFirst().orElseThrow();
            assertThat(curatedSummary.title()).isEqualTo("동화 " + curatedId);
            assertThat(curatedSummary.canonicalStoryId()).isNotNull();
            assertThat(fairytaleService.getHomePage(null).newItems())
                .extracting(item -> item.id()).contains(curatedId);
            assertThat(fairytaleService.getFairytaleDetail(curatedId).authorKo()).isEqualTo("작가");
            CuratedSlidesResponse curatedSlides = fairytaleService.getCuratedSlides(curatedId);
            assertThat(curatedSlides.pages()).singleElement().satisfies(page -> {
                assertThat(page.imageUrl())
                    .startsWith("http://localhost:18080/uploads/story-assets/");
                assertThat(page.audioUrls().get("dad").get("ko"))
                    .startsWith("http://localhost:18080/uploads/story-assets/");
            });

            long privateOwnerId = 10_000L + privateAiId;
            var privateSummary = aiFairytaleService.getMyFairytales(privateOwnerId).stream()
                .filter(item -> item.id().equals(privateAiId)).findFirst().orElseThrow();
            assertThat(privateSummary.thumbnailUrl())
                .startsWith("http://localhost:18080/uploads/story-assets/");
            FairytaleGenerateResponse privateSlides =
                aiFairytaleService.getMyFairytaleSlides(privateOwnerId, privateAiId);
            assertThat(privateSlides.pages()).singleElement().satisfies(page -> {
                assertThat(page.imageUrl())
                    .startsWith("http://localhost:18080/uploads/story-assets/");
                assertThat(page.audioUrl())
                    .startsWith("http://localhost:18080/uploads/story-assets/");
            });
            assertThatThrownBy(() -> aiFairytaleService.getMyFairytaleSlides(1L, privateAiId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
            assertThatThrownBy(() -> aiFairytaleService.getSharedFairytaleSlides(privateAiId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

            var sharedSummary = aiFairytaleService.getSharedFairytales().stream()
                .filter(item -> item.id().equals(sharedAiId)).findFirst().orElseThrow();
            assertThat(sharedSummary.shared()).isTrue();
            assertThat(aiFairytaleService.getSharedFairytaleSlides(sharedAiId).pages())
                .singleElement()
                .extracting(FairytaleGenerateResponse.PageDto::audioUrl)
                .asString()
                .startsWith("http://localhost:18080/uploads/story-assets/");

            cutoverService.rollbackToLegacy(epoch, "postgres routed read regression");

            assertThat(fairytaleService.getCuratedSlides(curatedId).pages())
                .singleElement()
                .extracting(CuratedSlidesResponse.Page::imageUrl)
                .isEqualTo("/uploads/legacy/curated/" + curatedId + "/page-0.png");
            assertThat(aiFairytaleService.getMyFairytaleSlides(privateOwnerId, privateAiId).pages())
                .singleElement()
                .extracting(FairytaleGenerateResponse.PageDto::imageUrl)
                .isEqualTo("/files/generated-fairytales/" + privateAiId + "/page_0.png");
        } finally {
            jdbc.update(
                "update front.content_migration_control set state = 'OPEN', read_source = 'LEGACY', "
                    + "write_source = 'LEGACY', smoke_hash = null, smoke_passed_at = null, "
                    + "updated_at = now() where singleton_id = 1"
            );
        }
    }

    @Test
    void shadowMismatchUpsertsOnceAndResolvesWhenCanonicalCatchesUp() throws IOException {
        writeCuratedMedia(71L);
        insertCurated(71L);
        importService.importCuratedBatch(70, 1);
        var link = linkRepository.findByLegacyTypeAndLegacyId(LegacyType.CURATED, 71L).orElseThrow();
        jdbc.update(
            "update front.scene_localized_contents c set display_text = 'canonical drift' "
                + "from front.story_scenes s where c.scene_id = s.id and s.version_id = ? and c.locale = 'ko'",
            link.getContentVersionId()
        );

        CuratedSlidesResponse legacyResponse = fairytaleService.getCuratedSlides(71L);
        ShadowCompareResult replay = shadowCompareService.compare(LegacyType.CURATED, 71L);

        assertThat(legacyResponse.fairytaleId()).isEqualTo(71L);
        assertThat(legacyResponse.pages()).singleElement()
            .satisfies(page -> assertThat(page.text().ko()).isEqualTo("첫 장면"));
        assertThat(replay.matches()).isFalse();
        assertThat(jdbc.queryForObject(
            "select count(*) from front.legacy_shadow_mismatches "
                + "where legacy_type = 'CURATED' and legacy_id = 71 and resolved_at is null",
            Integer.class
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select diff_json::text from front.legacy_shadow_mismatches "
                + "where legacy_type = 'CURATED' and legacy_id = 71 and resolved_at is null",
            String.class
        )).contains("canonical drift");

        jdbc.update(
            "update front.scene_localized_contents c set display_text = '첫 장면' "
                + "from front.story_scenes s where c.scene_id = s.id and s.version_id = ? and c.locale = 'ko'",
            link.getContentVersionId()
        );
        assertThat(shadowCompareService.compare(LegacyType.CURATED, 71L).matches()).isTrue();
        assertThat(jdbc.queryForObject(
            "select count(*) from front.legacy_shadow_mismatches "
                + "where legacy_type = 'CURATED' and legacy_id = 71 and resolved_at is null",
            Integer.class
        )).isZero();
        assertThat(jdbc.queryForObject(
            "select count(*) from front.legacy_shadow_mismatches "
                + "where legacy_type = 'CURATED' and legacy_id = 71 and resolved_at is not null",
            Integer.class
        )).isEqualTo(1);
    }

    @Test
    void aiReadOnlyServiceReturnsLegacySnapshotAndPersistsShadowMismatch() {
        insertAiCase(73L, "COMPLETED", "slide", true, true, false);
        importService.importAiBatch(72, 1);
        var link = linkRepository.findByLegacyTypeAndLegacyId(LegacyType.AI, 73L).orElseThrow();
        jdbc.update(
            "update front.scene_localized_contents c set display_text = 'AI canonical drift' "
                + "from front.story_scenes s where c.scene_id = s.id and s.version_id = ? and c.locale = 'ko'",
            link.getContentVersionId()
        );

        FairytaleGenerateResponse legacyResponse = aiFairytaleService.getMyFairytaleSlides(10_073L, 73L);

        assertThat(legacyResponse.id()).isEqualTo(73L);
        assertThat(legacyResponse.pages()).singleElement()
            .satisfies(page -> assertThat(page.text()).isEqualTo("AI page 73"));
        assertThat(jdbc.queryForObject(
            "select count(*) from front.legacy_shadow_mismatches "
                + "where legacy_type = 'AI' and legacy_id = 73 and resolved_at is null",
            Integer.class
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select diff_json::text from front.legacy_shadow_mismatches "
                + "where legacy_type = 'AI' and legacy_id = 73 and resolved_at is null",
            String.class
        )).contains("AI canonical drift");

        jdbc.update(
            "update front.scene_localized_contents c set display_text = 'AI page 73' "
                + "from front.story_scenes s where c.scene_id = s.id and s.version_id = ? and c.locale = 'ko'",
            link.getContentVersionId()
        );
        assertThat(shadowCompareService.compare(LegacyType.AI, 73L).matches()).isTrue();
        assertThat(jdbc.queryForObject(
            "select count(*) from front.legacy_shadow_mismatches "
                + "where legacy_type = 'AI' and legacy_id = 73 and resolved_at is null",
            Integer.class
        )).isZero();
    }

    @Test
    void missingCanonicalSnapshotReturnsLegacyReadAndRecordsOneOpenMismatch() throws IOException {
        writeCuratedMedia(72L);
        insertCurated(72L);

        CuratedSlidesResponse legacyResponse = fairytaleService.getCuratedSlides(72L);

        assertThat(legacyResponse.fairytaleId()).isEqualTo(72L);
        assertThat(legacyResponse.pages()).singleElement()
            .satisfies(page -> assertThat(page.text().ko()).isEqualTo("첫 장면"));
        assertThat(jdbc.queryForObject(
            "select count(*) from front.legacy_shadow_mismatches "
                + "where legacy_type = 'CURATED' and legacy_id = 72 and resolved_at is null",
            Integer.class
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select diff_json::text from front.legacy_shadow_mismatches "
                + "where legacy_type = 'CURATED' and legacy_id = 72 and resolved_at is null",
            String.class
        )).contains("missingCanonical");
    }

    @Test
    void concurrentSameKeyImportCreatesExactlyOneStoryLinkAndVersion() throws Exception {
        writeCuratedMedia(60L);
        insertCurated(60L);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> concurrentImport(ready, start, 59L));
            var second = executor.submit(() -> concurrentImport(ready, start, 59L));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ImportBatchResult> results = List.of(
                first.get(30, TimeUnit.SECONDS),
                second.get(30, TimeUnit.SECONDS)
            );
            assertThat(results.stream().mapToInt(ImportBatchResult::imported).sum()).isEqualTo(1);
            assertThat(results.stream().mapToInt(ImportBatchResult::unchanged).sum()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
            "select count(*) from front.stories where origin = 'CURATED' and origin_ref = '60'",
            Integer.class
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from front.legacy_story_links where legacy_type = 'CURATED' and legacy_id = 60",
            Integer.class
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from front.story_content_versions v join front.stories s on s.id = v.story_id "
                + "where s.origin = 'CURATED' and s.origin_ref = '60'",
            Integer.class
        )).isEqualTo(1);
        try (var paths = Files.walk(MEDIA_ROOT.resolve("story-assets/imports/curated/60"))) {
            assertThat(paths
                .filter(path -> path.getFileName().toString().startsWith(".published-media-")))
                .isEmpty();
        }
    }

    @Test
    void staleConcurrentDeltaCannotReplaceNewerLinkPointerOrWatermarkSnapshot() throws Exception {
        long legacyId = 790L;
        writeCuratedMedia(legacyId);
        insertCurated(legacyId);
        importService.importCuratedBatch(legacyId - 1, 1);
        jdbc.update(
            "update front.fairytales set title = 'stale-A', mod_dt = clock_timestamp() where id = ?",
            legacyId
        );

        CountDownLatch aPrepared = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        CountDownLatch bStarted = new CountDownLatch(1);
        CountDownLatch bPrepared = new CountDownLatch(1);
        AtomicReference<ConcurrentDeltaState> bCommitted = new AtomicReference<>();
        doAnswer(invocation -> {
            LegacyMediaSnapshotStore.PreparedImport prepared =
                (LegacyMediaSnapshotStore.PreparedImport) invocation.callRealMethod();
            String title = prepared.projection().titleKo();
            if ("stale-A".equals(title)) {
                aPrepared.countDown();
                if (!releaseA.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("stale delta release timed out");
                }
            } else if ("new-B".equals(title)) {
                bPrepared.countDown();
            }
            return prepared;
        }).when(mediaStore).prepare(any(LegacyProjection.class));

        var executor = Executors.newFixedThreadPool(2);
        try {
            Instant fullSweep = Instant.parse("2099-01-01T00:00:00Z");
            var first = executor.submit(() -> importService.importDelta(fullSweep));
            assertThat(aPrepared.await(10, TimeUnit.SECONDS)).isTrue();

            jdbc.update(
                "update front.fairytales set title = 'new-B', mod_dt = clock_timestamp() where id = ?",
                legacyId
            );
            var second = executor.submit(() -> {
                bStarted.countDown();
                ImportBatchResult result = importService.importDelta(fullSweep);
                bCommitted.set(concurrentDeltaState(legacyId));
                return result;
            });
            assertThat(bStarted.await(5, TimeUnit.SECONDS)).isTrue();

            boolean newerCompletedWhileStalePaused = true;
            try {
                second.get(3, TimeUnit.SECONDS);
            } catch (TimeoutException expectedWhileSerialized) {
                newerCompletedWhileStalePaused = false;
            }
            if (newerCompletedWhileStalePaused) {
                assertThat(bPrepared.getCount()).isZero();
                second.get(30, TimeUnit.SECONDS);
            }
            releaseA.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);

            assertThat(concurrentDeltaState(legacyId)).isEqualTo(bCommitted.get());
            assertThat(bCommitted.get().title()).isEqualTo("new-B");
        } finally {
            releaseA.countDown();
            executor.shutdownNow();
            reset(mediaStore);
        }
    }

    @Test
    void olderWatermarkCandidateCannotReplaceNewerSnapshotOrUpdatedAt() throws IOException {
        long legacyId = 791L;
        writeCuratedMedia(legacyId);
        insertCurated(legacyId);
        importService.importCuratedBatch(legacyId - 1, 1);
        jdbc.update(
            "update front.legacy_migration_watermarks set "
                + "watermark_at = timestamp with time zone '2099-01-01 00:00:00+00', "
                + "last_legacy_id = 900, snapshot_hash = repeat('a', 64), "
                + "updated_at = timestamp with time zone '2099-01-01 00:00:00+00' "
                + "where migration_kind = 'CURATED'"
        );
        WatermarkState newer = watermarkState(LegacyType.CURATED);

        importService.importCuratedBatch(legacyId - 1, 1);

        WatermarkState afterOlderCandidate = watermarkState(LegacyType.CURATED);
        assertThat(afterOlderCandidate).isEqualTo(newer);
    }

    @Test
    void laterRowFailureKeepsEarlierCommittedImportWithoutAdvancingWatermark() throws IOException {
        long firstId = 792L;
        long failingId = 793L;
        writeCuratedMedia(firstId);
        insertCurated(firstId);
        insertCurated(failingId);
        jdbc.update(
            "insert into front.legacy_migration_watermarks "
                + "(migration_kind, watermark_at, last_legacy_id, snapshot_hash, updated_at) "
                + "values ('CURATED', timestamp with time zone '2024-01-01 00:00:00+00', ?, "
                + "repeat('b', 64), timestamp with time zone '2024-01-01 00:00:00+00') "
                + "on conflict (migration_kind) do update set "
                + "watermark_at = excluded.watermark_at, last_legacy_id = excluded.last_legacy_id, "
                + "snapshot_hash = excluded.snapshot_hash, updated_at = excluded.updated_at",
            firstId - 1
        );
        WatermarkState before = watermarkState(LegacyType.CURATED);

        assertThatThrownBy(() -> importService.importCuratedBatch(firstId - 1, 2))
            .isInstanceOfSatisfying(
                LegacyImportException.class,
                exception -> assertThat(exception.getCode())
                    .isEqualTo("LEGACY_MEDIA_PREFLIGHT_FAILED")
            );

        assertThat(jdbc.queryForObject(
            "select count(*) from front.legacy_story_links l join front.stories s on s.id = l.story_id "
                + "where l.legacy_type = 'CURATED' and l.legacy_id = ? "
                + "and s.published_version_id = l.content_version_id",
            Integer.class,
            firstId
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from front.legacy_story_links "
                + "where legacy_type = 'CURATED' and legacy_id = ?",
            Integer.class,
            failingId
        )).isZero();
        assertThat(watermarkState(LegacyType.CURATED)).isEqualTo(before);
    }

    @Test
    void mediaPreparationRunsOutsideSpringTransactionAndExceptionReleasesExecutionLock()
        throws Exception {
        long legacyId = 794L;
        insertCurated(legacyId);
        List<Boolean> transactionStates = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            LegacyProjection projection = invocation.getArgument(0);
            if (projection.legacyId() == legacyId) {
                transactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            }
            return invocation.callRealMethod();
        }).when(mediaStore).prepare(any(LegacyProjection.class));

        var executor = Executors.newSingleThreadExecutor();
        try {
            assertThatThrownBy(() -> importService.importCuratedBatch(legacyId - 1, 1))
                .isInstanceOfSatisfying(
                    LegacyImportException.class,
                    exception -> assertThat(exception.getCode())
                        .isEqualTo("LEGACY_MEDIA_PREFLIGHT_FAILED")
                );
            writeCuratedMedia(legacyId);

            ImportBatchResult repaired = executor
                .submit(() -> importService.importCuratedBatch(legacyId - 1, 1))
                .get(10, TimeUnit.SECONDS);

            assertThat(repaired.imported()).isEqualTo(1);
            assertThat(transactionStates).hasSize(2).containsOnly(false);
        } finally {
            executor.shutdownNow();
            reset(mediaStore);
        }
    }

    @Test
    void multiParentBatchesUseFixedBulkSourceSelectCounts() throws IOException {
        writeCuratedMedia(61L);
        writeCuratedMedia(62L);
        insertCurated(61L);
        insertCurated(62L);
        insertAiCase(63L, "COMPLETED", "slide", true, true, false);
        insertAiCase(64L, "COMPLETED", "slide", true, true, false);
        var countingJdbc = new CountingJdbcTemplate(dataSource);
        var countedService = countedService(countingJdbc);

        ImportBatchResult curated = countedService.importCuratedBatch(60, 2);
        int curatedSourceSelects = countingJdbc.sourceSelectCount();
        countingJdbc.reset();
        ImportBatchResult ai = countedService.importAiBatch(62, 2);

        assertThat(curated.imported()).isEqualTo(2);
        assertThat(curatedSourceSelects).isEqualTo(4);
        assertThat(ai.imported()).isEqualTo(2);
        assertThat(countingJdbc.sourceSelectCount()).isEqualTo(2);
    }

    @Test
    void deltaLoadsMultipleChangedIdsThroughOneBulkSourceSnapshot() throws IOException {
        writeCuratedMedia(65L);
        writeCuratedMedia(66L);
        writeCuratedMedia(67L);
        insertCurated(65L);
        insertCurated(66L);
        insertCurated(67L);
        importService.importCuratedBatch(64, 3);
        Instant watermark = Instant.parse("2030-01-01T00:00:00Z");
        jdbc.update(
            "update front.curated_fairytale_pages set text_ko = 'delta bulk', mod_dt = ? "
                + "where fairytale_id in (65, 66)",
            Timestamp.from(Instant.parse("2031-01-01T00:00:00Z"))
        );
        var countingJdbc = new CountingJdbcTemplate(dataSource);

        ImportBatchResult result = countedService(countingJdbc).importDelta(watermark);

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.unchanged()).isZero();
        assertThat(countingJdbc.sourceSelectCount()).isEqualTo(8);
    }

    @Test
    void aiPageOnlyDeltaBulkImportsChangedRowAndExcludesOldLinkedRow() {
        insertAiCase(740L, "COMPLETED", "slide", true, true, false);
        insertAiCase(741L, "COMPLETED", "slide", true, true, false);
        importService.importAiBatch(739, 2);
        ImportState changedBefore = importState(LegacyType.AI, 740L);
        ImportState unchangedBefore = importState(LegacyType.AI, 741L);
        Instant watermark = Instant.parse("2030-01-01T00:00:00Z");
        jdbc.update(
            "update front.ai_fairytale_pages set text = 'AI delta page', mod_dt = ? "
                + "where ai_fairytale_id = 740 and page_index = 0",
            Timestamp.from(Instant.parse("2031-01-01T00:00:00Z"))
        );
        var countingJdbc = new CountingJdbcTemplate(dataSource);

        ImportBatchResult result = countedService(countingJdbc).importDelta(watermark);
        ImportState changedAfter = importState(LegacyType.AI, 740L);
        ImportState unchangedAfter = importState(LegacyType.AI, 741L);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.unchanged()).isZero();
        assertThat(result.nextLegacyId()).isEqualTo(740L);
        assertThat(changedAfter.storyId()).isEqualTo(changedBefore.storyId());
        assertThat(changedAfter.contentVersionId()).isNotEqualTo(changedBefore.contentVersionId());
        assertThat(unchangedAfter.storyId()).isEqualTo(unchangedBefore.storyId());
        assertThat(unchangedAfter.contentVersionId()).isEqualTo(unchangedBefore.contentVersionId());
        assertThat(unchangedAfter.sourceHash()).isEqualTo(unchangedBefore.sourceHash());
        assertThat(unchangedAfter.importedAt()).isEqualTo(unchangedBefore.importedAt());
        assertThat(unchangedAfter.publishedVersionId()).isEqualTo(unchangedBefore.publishedVersionId());
        assertThat(unchangedAfter.versionCount()).isEqualTo(unchangedBefore.versionCount());
        assertThat(unchangedAfter.assetCount()).isEqualTo(unchangedBefore.assetCount());
        assertThat(unchangedBefore.lastLegacyId()).isEqualTo(741L);
        assertThat(unchangedAfter.lastLegacyId()).isEqualTo(741L);
        assertThat(unchangedAfter.watermarkAt()).isAfterOrEqualTo(unchangedBefore.watermarkAt());
        assertThat(countingJdbc.sourceSelectCount()).isEqualTo(4);
    }

    private LegacyStoryImportService countedService(JdbcTemplate countedJdbc) {
        return new LegacyStoryImportService(
            countedJdbc,
            projectionMapper,
            mediaStore,
            canonicalWriter,
            checksum
        );
    }

    private ImportBatchResult concurrentImport(
        CountDownLatch ready,
        CountDownLatch start,
        long afterLegacyId
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent import start timed out");
        }
        return importService.importCuratedBatch(afterLegacyId, 1);
    }

    private void insertCurated(long id) {
        insertCuratedParent(id);
        insertCuratedPage(id);
    }

    private void insertCuratedParent(long id) {
        jdbc.update(
            "insert into front.fairytales "
                + "(id, title, title_ja, description, description_ja, is_theme, is_new, is_recommended, "
                + "character_supported, cre_dt, cre_id, del_yn) "
                + "values (?, ?, ?, '설명', '説明', 'N', 'Y', 'N', false, now(), 'test', 'N')",
            id,
            "동화 " + id,
            "童話 " + id
        );
        jdbc.update(
            "insert into front.fairytale_details "
                + "(fairytale_id, author_ko, author_ja, age_range, duration_min, page_count, content_version, "
                + "cre_dt, cre_id, del_yn) values (?, '작가', '作家', '3-5', 1, 1, 'legacy-v1', now(), 'test', 'N')",
            id
        );
    }

    private void insertCuratedPage(long id) {
        long pageId = jdbc.queryForObject(
            "insert into front.curated_fairytale_pages "
                + "(fairytale_id, page_index, image_url, text_ko, text_ja, content_version, "
                + "cre_dt, cre_id, del_yn) "
                + "values (?, 0, ?, '첫 장면', '最初の場面', "
                + "'legacy-v1', now(), 'test', 'N') returning id",
            Long.class,
            id,
            "/uploads/legacy/curated/" + id + "/page-0.png"
        );
        jdbc.update(
            "insert into front.curated_fairytale_audios "
                + "(page_id, voice_type, locale, audio_url, cre_dt, cre_id, del_yn) values "
                + "(?, 'dad', 'ko', ?, now(), 'test', 'N'), "
                + "(?, 'dad', 'ja', ?, now(), 'test', 'N')",
            pageId,
            "/uploads/legacy/curated/" + id + "/page-0-dad-ko.mp3",
            pageId,
            "/uploads/legacy/curated/" + id + "/page-0-dad-ja.mp3"
        );
    }

    private void writeCuratedMedia(long id) throws IOException {
        Path directory = MEDIA_ROOT.resolve("legacy/curated/" + id);
        Files.createDirectories(directory);
        Files.write(directory.resolve("page-0.png"), ("image-" + id).getBytes());
        Files.write(directory.resolve("page-0-dad-ko.mp3"), ("audio-ko-" + id).getBytes());
        Files.write(directory.resolve("page-0-dad-ja.mp3"), ("audio-ja-" + id).getBytes());
    }

    private void insertAi(long id, String status, String format, boolean shared) {
        insertAiCase(id, status, format, true, true, false);
    }

    private void insertAiCase(
        long id,
        String status,
        String format,
        boolean withOwner,
        boolean withPage,
        boolean withVideo
    ) {
        long ownerId = 10_000L + id;
        if (withOwner) {
            jdbc.update(
                "insert into front.users (id, email, password, created_at) values (?, ?, 'password', now())",
                ownerId,
                "legacy-ai-" + id + "@example.test"
            );
        }
        jdbc.update(
            "insert into front.ai_fairytales "
                + "(id, user_id, title, settings, genre, theme, chapter_count, voice_type, language, format, "
                + "status, shared, video_url, cre_dt, cre_id, del_yn) "
                + "values (?, ?, ?, 'forest', 'adventure', 'courage', ?, 'dad', 'ko', ?, ?, 'N', ?, now(), 'test', 'N')",
            id,
            withOwner ? ownerId : null,
            "AI " + id,
            withPage ? 1 : 0,
            format,
            status,
            withVideo ? "/files/generated-fairytales/" + id + "/video.mp4" : null
        );
        if (withPage) {
            jdbc.update(
                "insert into front.ai_fairytale_pages "
                    + "(ai_fairytale_id, page_index, text, image_url, audio_url, cre_dt, cre_id, del_yn) "
                    + "values (?, 0, ?, ?, ?, now(), 'test', 'N')",
                id,
                "AI page " + id,
                "/files/generated-fairytales/" + id + "/page_0.png",
                "/files/generated-fairytales/" + id + "/page_0_dad_ko.mp3"
            );
            try {
                writeAiMedia(id, withVideo);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private void writeAiMedia(long id, boolean video) throws IOException {
        Path directory = MEDIA_ROOT.resolve("legacy-ai/" + id);
        Files.createDirectories(directory);
        Files.write(directory.resolve("page_0.png"), ("ai-image-" + id).getBytes());
        Files.write(directory.resolve("page_0_dad_ko.mp3"), ("ai-audio-" + id).getBytes());
        if (video) {
            Files.write(directory.resolve("video.mp4"), ("ai-video-" + id).getBytes());
        }
    }

    private ImportState importState(LegacyType type, long legacyId) {
        return jdbc.queryForObject(
            "select l.story_id, l.content_version_id, trim(l.source_hash), l.imported_at, "
                + "s.published_version_id, "
                + "(select count(*) from front.story_content_versions v where v.story_id = l.story_id), "
                + "(select count(*) from front.media_assets a join front.story_content_versions v "
                + "on v.id = a.owner_version_id where v.story_id = l.story_id), "
                + "w.watermark_at, w.last_legacy_id "
                + "from front.legacy_story_links l join front.stories s on s.id = l.story_id "
                + "join front.legacy_migration_watermarks w on w.migration_kind = l.legacy_type "
                + "where l.legacy_type = ? and l.legacy_id = ?",
            (resultSet, rowNum) -> new ImportState(
                resultSet.getLong(1),
                resultSet.getLong(2),
                resultSet.getString(3),
                resultSet.getTimestamp(4).toInstant(),
                resultSet.getLong(5),
                resultSet.getLong(6),
                resultSet.getLong(7),
                resultSet.getTimestamp(8).toInstant(),
                resultSet.getLong(9)
            ),
            type.name(),
            legacyId
        );
    }

    private ConcurrentDeltaState concurrentDeltaState(long legacyId) {
        return jdbc.queryForObject(
            "select l.content_version_id, trim(l.source_hash), s.published_version_id, s.title_ko, "
                + "w.watermark_at, trim(w.snapshot_hash), w.updated_at "
                + "from front.legacy_story_links l join front.stories s on s.id = l.story_id "
                + "join front.legacy_migration_watermarks w on w.migration_kind = l.legacy_type "
                + "where l.legacy_type = 'CURATED' and l.legacy_id = ?",
            (resultSet, rowNum) -> new ConcurrentDeltaState(
                resultSet.getLong(1),
                resultSet.getString(2),
                resultSet.getLong(3),
                resultSet.getString(4),
                resultSet.getTimestamp(5).toInstant(),
                resultSet.getString(6),
                resultSet.getTimestamp(7).toInstant()
            ),
            legacyId
        );
    }

    private WatermarkState watermarkState(LegacyType type) {
        return jdbc.queryForObject(
            "select watermark_at, last_legacy_id, trim(snapshot_hash), updated_at "
                + "from front.legacy_migration_watermarks where migration_kind = ?",
            (resultSet, rowNum) -> new WatermarkState(
                resultSet.getTimestamp(1).toInstant(),
                resultSet.getLong(2),
                resultSet.getString(3),
                resultSet.getTimestamp(4).toInstant()
            ),
            type.name()
        );
    }

    private Map<String, FileState> canonicalFileState(long legacyId) throws IOException {
        Path root = MEDIA_ROOT.resolve("story-assets/imports/curated/" + legacyId);
        if (!Files.exists(root)) {
            return Map.of();
        }
        Map<String, FileState> state = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                state.put(
                    root.relativize(path).toString(),
                    new FileState(checksum.ofBytes(Files.readAllBytes(path)), Files.getLastModifiedTime(path).toMillis())
                );
            }
        }
        return state;
    }

    private ManifestFile manifestFile(long versionId) throws IOException {
        String storageKey = jdbc.queryForObject(
            "select a.storage_key from front.content_renditions r "
                + "join front.media_assets a on a.id = r.manifest_asset_id where r.version_id = ?",
            String.class,
            versionId
        );
        Path path = MEDIA_ROOT.resolve(storageKey);
        return new ManifestFile(
            storageKey,
            checksum.ofBytes(Files.readAllBytes(path)),
            Files.getLastModifiedTime(path).toMillis()
        );
    }

    private List<Long> runtimeMediaAssetIds(long versionId) {
        return jdbc.queryForList(
            "select asset_id from ("
                + "select s.fallback_asset_id as asset_id, s.order_index, '' as locale, '' as voice_type "
                + "from front.story_scenes s where s.version_id = ? "
                + "union all "
                + "select a.asset_id, s.order_index, a.locale, a.voice_type "
                + "from front.audio_variants a "
                + "join front.scene_audio_cues c on c.id = a.audio_cue_id "
                + "join front.story_scenes s on s.id = c.scene_id where s.version_id = ?) media "
                + "order by order_index, locale, voice_type",
            Long.class,
            versionId,
            versionId
        );
    }

    private String versionStatus(long versionId) {
        return jdbc.queryForObject(
            "select status from front.story_content_versions where id = ?",
            String.class,
            versionId
        );
    }

    private void assertAiState(
        long legacyId,
        String expectedVersionStatus,
        boolean expectedPointer,
        List<String> expectedJobs,
        List<String> expectedRenditions
    ) {
        var link = linkRepository.findByLegacyTypeAndLegacyId(LegacyType.AI, legacyId).orElseThrow();
        assertThat(versionStatus(link.getContentVersionId())).isEqualTo(expectedVersionStatus);
        Long pointer = jdbc.queryForObject(
            "select published_version_id from front.stories where id = ?",
            Long.class,
            link.getStoryId()
        );
        if (expectedPointer) {
            assertThat(pointer).isEqualTo(link.getContentVersionId());
        } else {
            assertThat(pointer).isNull();
        }
        assertThat(jdbc.query(
            "select kind || ':' || status from front.content_render_jobs where version_id = ? order by id",
            (resultSet, rowNum) -> resultSet.getString(1),
            link.getContentVersionId()
        )).containsExactlyElementsOf(expectedJobs);
        assertThat(jdbc.query(
            "select type || ':' || status from front.content_renditions where version_id = ? order by id",
            (resultSet, rowNum) -> resultSet.getString(1),
            link.getContentVersionId()
        )).containsExactlyElementsOf(expectedRenditions);
        if (expectedJobs.stream().anyMatch(value -> value.startsWith("CONTENT_GENERATION"))) {
            assertThat(link.getImportedGenerationJobId()).isNotNull();
        }
        if (expectedJobs.stream().anyMatch(value -> value.startsWith("VIDEO_RENDER"))) {
            assertThat(link.getImportedVideoJobId()).isNotNull();
        }
    }

    private static Path createMediaRoot() {
        try {
            return Files.createTempDirectory("legacy-story-import-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static final class CountingJdbcTemplate extends JdbcTemplate {

        private final List<String> sourceSelects = new java.util.concurrent.CopyOnWriteArrayList<>();

        private CountingJdbcTemplate(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            countSourceSelect(sql);
            return super.query(sql, rowMapper, args);
        }

        @Override
        public void query(String sql, RowCallbackHandler rowCallbackHandler, Object... args) {
            countSourceSelect(sql);
            super.query(sql, rowCallbackHandler, args);
        }

        private void countSourceSelect(String sql) {
            String normalized = sql.toLowerCase(java.util.Locale.ROOT);
            if (normalized.startsWith("select ") && (
                normalized.contains(" from fairytales ")
                    || normalized.contains(" curated_fairytale_pages")
                    || normalized.contains(" curated_fairytale_audios")
                    || normalized.contains(" fairytale_categories")
                    || normalized.contains(" from ai_fairytales ")
                    || normalized.contains(" from ai_fairytale_pages")
            )) {
                sourceSelects.add(normalized);
            }
        }

        private int sourceSelectCount() {
            return sourceSelects.size();
        }

        private void reset() {
            sourceSelects.clear();
        }
    }

    private record ImportState(
        long storyId,
        long contentVersionId,
        String sourceHash,
        java.time.Instant importedAt,
        long publishedVersionId,
        long versionCount,
        long assetCount,
        java.time.Instant watermarkAt,
        long lastLegacyId
    ) {
    }

    private record ConcurrentDeltaState(
        long contentVersionId,
        String sourceHash,
        long publishedVersionId,
        String title,
        Instant watermarkAt,
        String snapshotHash,
        Instant watermarkUpdatedAt
    ) {
    }

    private record WatermarkState(
        Instant watermarkAt,
        long lastLegacyId,
        String snapshotHash,
        Instant updatedAt
    ) {
    }

    private record FileState(String sha256, long lastModifiedMillis) {
    }

    private record ManifestFile(String storageKey, String sha256, long lastModifiedMillis) {
    }
}

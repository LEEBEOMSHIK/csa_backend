package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import javax.sql.DataSource;
import org.example.csa_backend.storycontent.ContentMigrationControl;
import org.example.csa_backend.storycontent.ContentMigrationControlRepository;
import org.example.csa_backend.storycontent.ContentSource;
import org.example.csa_backend.storycontent.LegacyStoryLink;
import org.example.csa_backend.storycontent.LegacyStoryLinkRepository;
import org.example.csa_backend.storycontent.LegacyType;
import org.example.csa_backend.storycontent.MigrationState;
import org.example.csa_backend.storycontent.OutboxEventRepository;
import org.example.csa_backend.storycontent.PublishedRuntimeManifestLookup;
import org.example.csa_backend.storycontent.StoryRuntimeException;
import org.example.csa_backend.storycontent.StoryRuntimeService;
import org.example.csa_backend.storycontent.dto.RuntimeCapabilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
class ContentCutoverPostgresIntegrationTest {

    private static final Path MEDIA_ROOT = createMediaRoot();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=front");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 4);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.schemas", () -> "front");
        registry.add("spring.flyway.default-schema", () -> "front");
        registry.add("spring.flyway.create-schemas", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "front");
        registry.add("csa.media.storage-mode", () -> "local");
        registry.add("csa.media.storage-root", MEDIA_ROOT::toString);
        registry.add("csa.media.public-base-url", () -> "http://localhost:18080/uploads");
        registry.add("storage.local-base-path", () -> MEDIA_ROOT.resolve("legacy-ai").toString());
        registry.add("storage.server-base-url", () -> "http://localhost:18080");
    }

    @Autowired
    private ContentCutoverService cutoverService;

    @Autowired
    private ContentMigrationControlRepository controlRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private LegacyStoryImportService importService;

    @Autowired
    private LegacyStoryReconciliationService reconciliationService;

    @Autowired
    private LegacyStoryLinkRepository linkRepository;

    @Autowired
    private StoryRuntimeService runtimeService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private CutoverTransactionHook transactionHook;

    @MockitoBean
    private PublishedRuntimeManifestLookup runtimeManifestLookup;

    @BeforeEach
    void isolateLegacySourcesAndMocks() {
        reset(transactionHook, runtimeManifestLookup);
        jdbc.update("update front.fairytales set del_yn = 'Y'");
        jdbc.update("update front.ai_fairytales set del_yn = 'Y'");
        jdbc.update("update front.legacy_shadow_mismatches set resolved_at = now() where resolved_at is null");
    }

    @Test
    void failedCutoverTransactionRollsBackSourcesStateAndOutboxTogether() {
        long epoch = 55L;
        String checksum = "c".repeat(64);
        jdbc.update("delete from front.content_outbox_events where barrier_epoch = ?", epoch);
        jdbc.update("delete from front.content_migration_reconciliations where epoch = ?", epoch);
        jdbc.update(
            "update front.content_migration_control set state = 'FROZEN', read_source = 'LEGACY', "
                + "write_source = 'LEGACY', barrier_epoch = ?, backend_ack_epoch = ?, "
                + "admin_backend_ack_epoch = ?, reconciliation_hash = null, smoke_hash = null, "
                + "smoke_passed_at = null where singleton_id = 1",
            epoch,
            epoch,
            epoch
        );
        jdbc.update(
            "insert into front.content_migration_reconciliations "
                + "(epoch, status, checksum, report_json, completed_at) "
                + "values (?, 'SUCCEEDED', ?, cast(? as jsonb), now())",
            epoch,
            checksum,
            "{\"complete\":true}"
        );
        doThrow(new RuntimeException("cutover failure"))
            .when(transactionHook).afterCanonicalSourceUpdate();

        assertThatThrownBy(() -> cutoverService.cutover(epoch))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("cutover failure");

        ContentMigrationControl control = controlRepository.getSingleton();
        assertThat(control.getReadSource()).isEqualTo(ContentSource.LEGACY);
        assertThat(control.getWriteSource()).isEqualTo(ContentSource.LEGACY);
        assertThat(control.getState()).isEqualTo(MigrationState.FROZEN);
        assertThat(control.getBarrierEpoch()).isEqualTo(epoch);
        assertThat(outboxRepository.findByBarrierEpoch(epoch)).isEmpty();
    }

    @Test
    void releasedSessionLockCanBeAcquiredFromTwoDistinctPhysicalConnections() throws Exception {
        importService.importCuratedBatch(0, 1);

        try (Connection first = dataSource.getConnection();
             Connection second = dataSource.getConnection()) {
            assertThat(backendPid(first)).isNotEqualTo(backendPid(second));
            assertThat(tryLock(first, -8_411_001L)).isTrue();
            assertThat(unlock(first, -8_411_001L)).isTrue();
            assertThat(tryLock(second, -8_411_001L)).isTrue();
            assertThat(unlock(second, -8_411_001L)).isTrue();
        }
    }

    @Test
    void softDeletedParentArchivesCanonicalGraphRetainsLinkAndHidesRuntime() throws IOException {
        long legacyId = 9_901L;
        LegacyStoryLink link = importCurated(legacyId);

        jdbc.update("update front.fairytales set del_yn = 'Y' where id = ?", legacyId);
        reconciliationService.reconcileAll();

        assertArchivedAndNotPublic(link);
    }

    @Test
    void hardDeletedDetailArchivesCanonicalGraphRetainsLinkAndHidesRuntime() throws IOException {
        long legacyId = 9_902L;
        LegacyStoryLink link = importCurated(legacyId);

        jdbc.update("delete from front.fairytale_details where fairytale_id = ?", legacyId);
        reconciliationService.reconcileAll();

        assertArchivedAndNotPublic(link);
    }

    private LegacyStoryLink importCurated(long legacyId) throws IOException {
        insertCurated(legacyId);
        writeCuratedMedia(legacyId);
        ImportBatchResult result = importService.importCuratedBatch(legacyId - 1, 10);
        assertThat(result.imported()).isEqualTo(1);
        return linkRepository.findByLegacyTypeAndLegacyId(LegacyType.CURATED, legacyId)
            .orElseThrow();
    }

    private void assertArchivedAndNotPublic(LegacyStoryLink link) {
        assertThat(jdbc.queryForObject(
            "select visibility from front.stories where id = ?",
            String.class,
            link.getStoryId()
        )).isEqualTo("ARCHIVED");
        assertThat(jdbc.queryForObject(
            "select status from front.story_content_versions where id = ?",
            String.class,
            link.getContentVersionId()
        )).isEqualTo("ARCHIVED");
        assertThat(linkRepository.findByLegacyTypeAndLegacyId(
            link.getLegacyType(), link.getLegacyId())).isPresent();
        clearInvocations(runtimeManifestLookup);
        assertThatThrownBy(() -> runtimeService.getPublishedRuntime(
            link.getStoryId(),
            new RuntimeCapabilities("SLIDE", "ko", null, List.of(1), List.of("SLIDE"), 1)
        )).isInstanceOfSatisfying(
            StoryRuntimeException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("STORY_NOT_FOUND")
        );
        verifyNoInteractions(runtimeManifestLookup);
    }

    private void insertCurated(long id) {
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
                + "(fairytale_id, author_ko, author_ja, age_range, duration_min, page_count, "
                + "content_version, cre_dt, cre_id, del_yn) "
                + "values (?, '작가', '作家', '3-5', 1, 1, 'legacy-v1', now(), 'test', 'N')",
            id
        );
        long pageId = jdbc.queryForObject(
            "insert into front.curated_fairytale_pages "
                + "(fairytale_id, page_index, image_url, text_ko, text_ja, content_version, "
                + "cre_dt, cre_id, del_yn) values (?, 0, ?, '첫 장면', '最初の場面', "
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

    private int backendPid(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("select pg_backend_pid()");
             ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private boolean tryLock(Connection connection, long key) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("select pg_try_advisory_lock(?)")) {
            statement.setLong(1, key);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private boolean unlock(Connection connection, long key) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_unlock(?)")) {
            statement.setLong(1, key);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private static Path createMediaRoot() {
        try {
            return Files.createTempDirectory("content-cutover-postgres-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}

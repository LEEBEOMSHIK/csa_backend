package org.example.csa_backend.storycontent.migration;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.example.csa_backend.storycontent.LegacyType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

@Service
public class LegacyStoryImportService {

    private static final int FINGERPRINT_PAGE_SIZE = 1_000;
    private static final long CURATED_EXECUTION_LOCK = -8_411_001L;
    private static final long AI_EXECUTION_LOCK = -8_411_002L;
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private final JdbcTemplate jdbc;
    private final LegacyStoryProjectionMapper projectionMapper;
    private final LegacyMediaSnapshotStore mediaStore;
    private final CanonicalStoryWriter canonicalWriter;
    private final ContractChecksum checksum;

    public LegacyStoryImportService(
        JdbcTemplate jdbc,
        LegacyStoryProjectionMapper projectionMapper,
        LegacyMediaSnapshotStore mediaStore,
        CanonicalStoryWriter canonicalWriter,
        ContractChecksum checksum
    ) {
        this.jdbc = jdbc;
        this.projectionMapper = projectionMapper;
        this.mediaStore = mediaStore;
        this.canonicalWriter = canonicalWriter;
        this.checksum = checksum;
    }

    public ImportBatchResult importCuratedBatch(long afterLegacyId, int batchSize) {
        validateBatch(afterLegacyId, batchSize);
        return withExecutionLocks(
            List.of(LegacyType.CURATED),
            () -> importCuratedBatchLocked(afterLegacyId, batchSize)
        );
    }

    private ImportBatchResult importCuratedBatchLocked(long afterLegacyId, int batchSize) {
        List<CuratedParent> parents = jdbc.query(
            "select f.id, f.title, f.title_ja, f.description, f.description_ja, "
                + "f.rating, f.color_hex, f.theme_tag, f.character_supported, "
                + "f.is_theme, f.is_new, f.is_recommended, f.cre_dt, "
                + "d.author_ko, d.author_ja, d.age_range, d.duration_min, d.page_count, "
                + "d.full_content_ko, d.full_content_ja, d.content_version "
                + "from fairytales f join fairytale_details d on d.fairytale_id = f.id "
                + "where f.id > ? and f.del_yn = 'N' and d.del_yn = 'N' "
                + "order by f.id asc limit ?",
            (resultSet, rowNum) -> curatedParent(resultSet),
            afterLegacyId,
            batchSize
        );
        if (parents.isEmpty()) {
            return new ImportBatchResult(0, 0, afterLegacyId, true);
        }
        Map<Long, String> fingerprints = new LinkedHashMap<>();
        ImportCounts counts = importCuratedParents(parents, fingerprints, true);
        long nextLegacyId = parents.get(parents.size() - 1).id();
        advanceWatermark(
            LegacyType.CURATED,
            captureDbTime(),
            nextLegacyId,
            snapshotHash(LegacyType.CURATED, fingerprints)
        );
        return new ImportBatchResult(
            counts.imported(), counts.unchanged(), nextLegacyId, parents.size() < batchSize);
    }

    public ImportBatchResult importAiBatch(long afterLegacyId, int batchSize) {
        validateBatch(afterLegacyId, batchSize);
        return withExecutionLocks(
            List.of(LegacyType.AI),
            () -> importAiBatchLocked(afterLegacyId, batchSize)
        );
    }

    private ImportBatchResult importAiBatchLocked(long afterLegacyId, int batchSize) {
        List<AiParent> parents = jdbc.query(
            "select id, user_id, title, settings, genre, theme, chapter_count, voice_type, language, "
                + "format, status, shared, video_url, cre_dt from ai_fairytales "
                + "where id > ? and del_yn = 'N' order by id asc limit ?",
            (resultSet, rowNum) -> aiParent(resultSet),
            afterLegacyId,
            batchSize
        );
        if (parents.isEmpty()) {
            return new ImportBatchResult(0, 0, afterLegacyId, true);
        }
        Map<Long, String> fingerprints = new LinkedHashMap<>();
        ImportCounts counts = importAiParents(parents, fingerprints, true);
        long nextLegacyId = parents.get(parents.size() - 1).id();
        advanceWatermark(
            LegacyType.AI,
            captureDbTime(),
            nextLegacyId,
            snapshotHash(LegacyType.AI, fingerprints)
        );
        return new ImportBatchResult(
            counts.imported(), counts.unchanged(), nextLegacyId, parents.size() < batchSize);
    }

    public ImportBatchResult importDelta(Instant watermark) {
        if (watermark == null) {
            throw new IllegalArgumentException("watermark is required");
        }
        return withExecutionLocks(
            List.of(LegacyType.CURATED, LegacyType.AI),
            () -> importDeltaLocked(watermark)
        );
    }

    private ImportBatchResult importDeltaLocked(Instant watermark) {
        Instant through = captureDbTime();
        ScanResult curated = scanCuratedDelta(watermark, through);
        ScanResult ai = scanAiDelta(watermark, through);
        advanceDeltaWatermarks(through, curated, ai);
        return new ImportBatchResult(
            curated.counts().imported() + ai.counts().imported(),
            curated.counts().unchanged() + ai.counts().unchanged(),
            Math.max(curated.maxChangedId(), ai.maxChangedId()),
            true
        );
    }

    private ScanResult scanCuratedDelta(Instant watermark, Instant through) {
        Map<Long, String> fingerprints = new LinkedHashMap<>();
        ImportCounts total = new ImportCounts(0, 0, 0);
        long afterLegacyId = 0;
        while (true) {
            List<Long> ids = loadCuratedDeltaIds(afterLegacyId, watermark, through);
            if (ids.isEmpty()) {
                break;
            }
            ImportCounts page = importCuratedParents(
                loadCuratedParents(ids),
                fingerprints,
                false
            );
            total = total.plus(page);
            afterLegacyId = ids.get(ids.size() - 1);
            if (ids.size() < FINGERPRINT_PAGE_SIZE) {
                break;
            }
        }
        return new ScanResult(
            LegacyType.CURATED,
            total,
            total.maxChangedId(),
            afterLegacyId,
            snapshotHash(LegacyType.CURATED, fingerprints)
        );
    }

    private ScanResult scanAiDelta(Instant watermark, Instant through) {
        Map<Long, String> fingerprints = new LinkedHashMap<>();
        ImportCounts total = new ImportCounts(0, 0, 0);
        long afterLegacyId = 0;
        while (true) {
            List<Long> ids = loadAiDeltaIds(afterLegacyId, watermark, through);
            if (ids.isEmpty()) {
                break;
            }
            ImportCounts page = importAiParents(loadAiParents(ids), fingerprints, false);
            total = total.plus(page);
            afterLegacyId = ids.get(ids.size() - 1);
            if (ids.size() < FINGERPRINT_PAGE_SIZE) {
                break;
            }
        }
        return new ScanResult(
            LegacyType.AI,
            total,
            total.maxChangedId(),
            afterLegacyId,
            snapshotHash(LegacyType.AI, fingerprints)
        );
    }

    private List<Long> loadCuratedDeltaIds(long afterLegacyId, Instant watermark, Instant through) {
        String sql = "select f.id from fairytales f join fairytale_details d on d.fairytale_id = f.id "
            + "where f.id > ? and f.del_yn = 'N' and d.del_yn = 'N' and ("
            + "exists (select 1 from legacy_story_links l where l.legacy_type = 'CURATED' "
            + "and l.legacy_id = f.id) "
            + "or (coalesce(f.mod_dt, f.cre_dt) > ? and coalesce(f.mod_dt, f.cre_dt) <= ?) "
            + "or (coalesce(d.mod_dt, d.cre_dt) > ? and coalesce(d.mod_dt, d.cre_dt) <= ?) "
            + "or exists (select 1 from curated_fairytale_pages p where p.fairytale_id = f.id "
            + "and coalesce(p.mod_dt, p.cre_dt) > ? and coalesce(p.mod_dt, p.cre_dt) <= ?) "
            + "or exists (select 1 from curated_fairytale_pages p join curated_fairytale_audios a "
            + "on a.page_id = p.id where p.fairytale_id = f.id "
            + "and coalesce(a.mod_dt, a.cre_dt) > ? and coalesce(a.mod_dt, a.cre_dt) <= ?)) "
            + "order by f.id asc limit ?";
        return jdbc.query(
            sql,
            (resultSet, rowNum) -> resultSet.getLong(1),
            afterLegacyId,
            Timestamp.from(watermark), Timestamp.from(through),
            Timestamp.from(watermark), Timestamp.from(through),
            Timestamp.from(watermark), Timestamp.from(through),
            Timestamp.from(watermark), Timestamp.from(through),
            FINGERPRINT_PAGE_SIZE
        );
    }

    private List<Long> loadAiDeltaIds(long afterLegacyId, Instant watermark, Instant through) {
        String sql = "select f.id from ai_fairytales f where f.id > ? and f.del_yn = 'N' and ("
            + "exists (select 1 from legacy_story_links l where l.legacy_type = 'AI' "
            + "and l.legacy_id = f.id) "
            + "or (coalesce(f.mod_dt, f.cre_dt) > ? and coalesce(f.mod_dt, f.cre_dt) <= ?) "
            + "or exists (select 1 from ai_fairytale_pages p where p.ai_fairytale_id = f.id "
            + "and coalesce(p.mod_dt, p.cre_dt) > ? and coalesce(p.mod_dt, p.cre_dt) <= ?)) "
            + "order by f.id asc limit ?";
        return jdbc.query(
            sql,
            (resultSet, rowNum) -> resultSet.getLong(1),
            afterLegacyId,
            Timestamp.from(watermark), Timestamp.from(through),
            Timestamp.from(watermark), Timestamp.from(through),
            FINGERPRINT_PAGE_SIZE
        );
    }

    private Instant captureDbTime() {
        Timestamp timestamp = jdbc.queryForObject("select clock_timestamp()", Timestamp.class);
        if (timestamp == null) {
            throw new LegacyImportException("LEGACY_DB_CLOCK_UNAVAILABLE", null);
        }
        return timestamp.toInstant();
    }

    private String snapshotHash(LegacyType type, Map<Long, String> fingerprints) {
        List<String> parts = new ArrayList<>();
        parts.add("LEGACY_SNAPSHOT_V1");
        parts.add(type.name());
        fingerprints.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                parts.add(Long.toString(entry.getKey()));
                parts.add(entry.getValue());
            });
        return checksum.ofParts(parts);
    }

    private void advanceWatermark(
        LegacyType type,
        Instant through,
        long lastLegacyId,
        String snapshotHash
    ) {
        jdbc.update(
            "insert into legacy_migration_watermarks "
                + "(migration_kind, watermark_at, last_legacy_id, snapshot_hash, updated_at) "
                + "values (?, ?, ?, ?, ?) on conflict (migration_kind) do update set "
                + "watermark_at = greatest(legacy_migration_watermarks.watermark_at, excluded.watermark_at), "
                + "last_legacy_id = greatest(legacy_migration_watermarks.last_legacy_id, excluded.last_legacy_id), "
                + "snapshot_hash = case when excluded.watermark_at >= legacy_migration_watermarks.watermark_at "
                + "then excluded.snapshot_hash else legacy_migration_watermarks.snapshot_hash end, "
                + "updated_at = case when excluded.watermark_at >= legacy_migration_watermarks.watermark_at "
                + "then excluded.updated_at else legacy_migration_watermarks.updated_at end",
            type.name(),
            Timestamp.from(through),
            lastLegacyId,
            snapshotHash,
            Timestamp.from(through)
        );
    }

    private void advanceDeltaWatermarks(Instant through, ScanResult curated, ScanResult ai) {
        jdbc.update(
            "insert into legacy_migration_watermarks "
                + "(migration_kind, watermark_at, last_legacy_id, snapshot_hash, updated_at) values "
                + "(?, ?, ?, ?, ?), (?, ?, ?, ?, ?) on conflict (migration_kind) do update set "
                + "watermark_at = greatest(legacy_migration_watermarks.watermark_at, excluded.watermark_at), "
                + "last_legacy_id = greatest(legacy_migration_watermarks.last_legacy_id, excluded.last_legacy_id), "
                + "snapshot_hash = case when excluded.watermark_at >= legacy_migration_watermarks.watermark_at "
                + "then excluded.snapshot_hash else legacy_migration_watermarks.snapshot_hash end, "
                + "updated_at = case when excluded.watermark_at >= legacy_migration_watermarks.watermark_at "
                + "then excluded.updated_at else legacy_migration_watermarks.updated_at end",
            curated.type().name(), Timestamp.from(through), curated.lastScannedId(),
            curated.snapshotHash(), Timestamp.from(through),
            ai.type().name(), Timestamp.from(through), ai.lastScannedId(),
            ai.snapshotHash(), Timestamp.from(through)
        );
    }

    private <T> T withExecutionLocks(List<LegacyType> types, Supplier<T> action) {
        try (ExecutionLockHandle ignored = acquireExecutionLocks(types)) {
            return action.get();
        }
    }

    private ExecutionLockHandle acquireExecutionLocks(List<LegacyType> types) {
        DataSource dataSource = jdbc.getDataSource();
        if (dataSource == null) {
            throw new LegacyImportException("LEGACY_IMPORT_LOCK_DATASOURCE_REQUIRED", null);
        }
        assertMigrationPoolCapacity(dataSource);
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException exception) {
            throw new LegacyImportException(
                "LEGACY_IMPORT_LOCK_CONNECTION_FAILED",
                lockDetail(types),
                exception
            );
        }

        List<Long> acquiredKeys = new ArrayList<>();
        try {
            for (LegacyType type : types) {
                long key = executionLockKey(type);
                acquireSessionLock(connection, key);
                acquiredKeys.add(key);
            }
            return new ExecutionLockHandle(connection, acquiredKeys, lockDetail(types));
        } catch (SQLException | RuntimeException exception) {
            LegacyImportException failure = exception instanceof LegacyImportException legacy
                ? legacy
                : new LegacyImportException(
                    "LEGACY_IMPORT_LOCK_ACQUIRE_FAILED",
                    lockDetail(types),
                    exception
                );
            invalidateConnection(connection, failure);
            throw failure;
        }
    }

    private void acquireSessionLock(Connection connection, long key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "select pg_advisory_lock(?)"
        )) {
            statement.setLong(1, key);
            statement.execute();
        }
    }

    private void assertMigrationPoolCapacity(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource
            && hikariDataSource.getMaximumPoolSize() < 2) {
            throw new LegacyImportException(
                "LEGACY_IMPORT_POOL_SIZE_TOO_SMALL",
                "minimum=2,actual=" + hikariDataSource.getMaximumPoolSize()
            );
        }
    }

    private boolean releaseSessionLock(Connection connection, long key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "select pg_advisory_unlock(?)"
        )) {
            statement.setLong(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private long executionLockKey(LegacyType type) {
        return type == LegacyType.CURATED ? CURATED_EXECUTION_LOCK : AI_EXECUTION_LOCK;
    }

    private String lockDetail(List<LegacyType> types) {
        return types.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private void invalidateConnection(Connection connection, Throwable failure) {
        try {
            connection.abort(DIRECT_EXECUTOR);
        } catch (SQLException | RuntimeException abortFailure) {
            failure.addSuppressed(abortFailure);
        }
        try {
            connection.close();
        } catch (SQLException | RuntimeException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    Map<Long, String> sourceHashes(LegacyType type) {
        Map<Long, String> result = new LinkedHashMap<>();
        long afterLegacyId = 0;
        while (true) {
            List<LegacySourceHash> page = sourceHashPage(
                type,
                afterLegacyId,
                FINGERPRINT_PAGE_SIZE
            );
            page.forEach(source -> result.put(source.legacyId(), source.sourceHash()));
            if (page.size() < FINGERPRINT_PAGE_SIZE) {
                break;
            }
            afterLegacyId = page.get(page.size() - 1).legacyId();
        }
        return Collections.unmodifiableMap(result);
    }

    List<LegacySourceHash> sourceHashPage(LegacyType type, long afterLegacyId, int limit) {
        if (afterLegacyId < 0) {
            throw new IllegalArgumentException("afterLegacyId must be non-negative");
        }
        if (limit <= 0 || limit > FINGERPRINT_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + FINGERPRINT_PAGE_SIZE);
        }
        if (type == LegacyType.CURATED) {
            return curatedSourceHashPage(afterLegacyId, limit);
        }
        if (type == LegacyType.AI) {
            return aiSourceHashPage(afterLegacyId, limit);
        }
        throw new IllegalArgumentException("Unsupported legacy type: " + type);
    }

    public LegacyProjection projectLegacy(LegacyType type, long legacyId) {
        if (legacyId <= 0) {
            throw new IllegalArgumentException("legacyId must be positive");
        }
        if (type == LegacyType.CURATED) {
            List<CuratedParent> parents = loadCuratedParents(List.of(legacyId));
            if (parents.isEmpty()) {
                throw new LegacyImportException("LEGACY_STORY_NOT_FOUND", type.name() + ":" + legacyId);
            }
            CuratedParent parent = parents.get(0);
            Map<Long, List<PageBuilder>> pagesByFairytale = loadCuratedPages(List.of(legacyId));
            loadCuratedAudios(pagesByFairytale);
            Map<Long, List<String>> categoriesByFairytale = loadCategoryKeys(List.of(legacyId));
            return projectionMapper.projectCurated(new LegacyStoryProjectionMapper.CuratedSource(
                parent.id(),
                parent.titleKo(),
                parent.titleJa(),
                parent.descriptionKo(),
                parent.descriptionJa(),
                parent.durationMin(),
                parent.contentVersion(),
                categoriesByFairytale.getOrDefault(parent.id(), List.of()),
                pagesByFairytale.getOrDefault(parent.id(), List.of()).stream()
                    .sorted(Comparator.comparingInt(PageBuilder::pageIndex))
                    .map(PageBuilder::toSource)
                    .toList(),
                parent.metadata()
            ));
        }
        if (type == LegacyType.AI) {
            List<AiParent> parents = loadAiParents(List.of(legacyId));
            if (parents.isEmpty()) {
                throw new LegacyImportException("LEGACY_STORY_NOT_FOUND", type.name() + ":" + legacyId);
            }
            AiParent parent = parents.get(0);
            Map<Long, List<LegacyStoryProjectionMapper.AiPageSource>> pagesByFairytale =
                loadAiPages(List.of(legacyId));
            return projectionMapper.projectAi(new LegacyStoryProjectionMapper.AiSource(
                parent.id(),
                parent.title(),
                parent.settings(),
                parent.genre(),
                parent.theme(),
                parent.chapterCount(),
                parent.voiceType(),
                parent.language(),
                parent.format(),
                parent.status(),
                parent.shared(),
                parent.videoUrl(),
                parent.ownerUserId(),
                pagesByFairytale.getOrDefault(parent.id(), List.of()),
                parent.metadata()
            ));
        }
        throw new IllegalArgumentException("Unsupported legacy type: " + type);
    }

    private List<LegacySourceHash> curatedSourceHashPage(long afterLegacyId, int limit) {
        List<Long> ids = loadCuratedActiveIds(afterLegacyId, limit);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<CuratedParent> parents = loadCuratedParents(ids);
        List<Long> parentIds = parents.stream().map(CuratedParent::id).toList();
        Map<Long, List<PageBuilder>> pagesByFairytale = loadCuratedPages(parentIds);
        loadCuratedAudios(pagesByFairytale);
        Map<Long, List<String>> categoriesByFairytale = loadCategoryKeys(parentIds);
        List<LegacySourceHash> result = new ArrayList<>(parents.size());
        for (CuratedParent parent : parents) {
            LegacyProjection projection = projectionMapper.projectCurated(
                new LegacyStoryProjectionMapper.CuratedSource(
                    parent.id(),
                    parent.titleKo(),
                    parent.titleJa(),
                    parent.descriptionKo(),
                    parent.descriptionJa(),
                    parent.durationMin(),
                    parent.contentVersion(),
                    categoriesByFairytale.getOrDefault(parent.id(), List.of()),
                    pagesByFairytale.getOrDefault(parent.id(), List.of()).stream()
                        .sorted(Comparator.comparingInt(PageBuilder::pageIndex))
                        .map(PageBuilder::toSource)
                        .toList(),
                    parent.metadata()
                )
            );
            result.add(new LegacySourceHash(
                parent.id(),
                mediaStore.prepare(projection).projection().sourceHash()
            ));
        }
        return List.copyOf(result);
    }

    private List<LegacySourceHash> aiSourceHashPage(long afterLegacyId, int limit) {
        List<Long> ids = loadAiActiveIds(afterLegacyId, limit);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<AiParent> parents = loadAiParents(ids);
        Map<Long, List<LegacyStoryProjectionMapper.AiPageSource>> pagesByFairytale =
            loadAiPages(ids);
        List<LegacySourceHash> result = new ArrayList<>(parents.size());
        for (AiParent parent : parents) {
            LegacyProjection projection = projectionMapper.projectAi(
                new LegacyStoryProjectionMapper.AiSource(
                    parent.id(),
                    parent.title(),
                    parent.settings(),
                    parent.genre(),
                    parent.theme(),
                    parent.chapterCount(),
                    parent.voiceType(),
                    parent.language(),
                    parent.format(),
                    parent.status(),
                    parent.shared(),
                    parent.videoUrl(),
                    parent.ownerUserId(),
                    pagesByFairytale.getOrDefault(parent.id(), List.of()),
                    parent.metadata()
                )
            );
            result.add(new LegacySourceHash(
                parent.id(),
                mediaStore.prepare(projection).projection().sourceHash()
            ));
        }
        return List.copyOf(result);
    }

    private List<Long> loadCuratedActiveIds(long afterLegacyId, int limit) {
        return jdbc.query(
            "select f.id from fairytales f join fairytale_details d on d.fairytale_id = f.id "
                + "where f.id > ? and f.del_yn = 'N' and d.del_yn = 'N' "
                + "order by f.id asc limit ?",
            (resultSet, rowNum) -> resultSet.getLong(1),
            afterLegacyId,
            limit
        );
    }

    private List<Long> loadAiActiveIds(long afterLegacyId, int limit) {
        return jdbc.query(
            "select f.id from ai_fairytales f where f.id > ? and f.del_yn = 'N' "
                + "order by f.id asc limit ?",
            (resultSet, rowNum) -> resultSet.getLong(1),
            afterLegacyId,
            limit
        );
    }

    private List<CuratedParent> loadCuratedParents(List<Long> fairytaleIds) {
        String sql = "select f.id, f.title, f.title_ja, f.description, f.description_ja, "
            + "f.rating, f.color_hex, f.theme_tag, f.character_supported, "
            + "f.is_theme, f.is_new, f.is_recommended, f.cre_dt, "
            + "d.author_ko, d.author_ja, d.age_range, d.duration_min, d.page_count, "
            + "d.full_content_ko, d.full_content_ja, d.content_version "
            + "from fairytales f join fairytale_details d on d.fairytale_id = f.id "
            + "where f.id in (" + placeholders(fairytaleIds.size()) + ") "
            + "and f.del_yn = 'N' and d.del_yn = 'N' order by f.id asc";
        return jdbc.query(sql, (resultSet, rowNum) -> curatedParent(resultSet), fairytaleIds.toArray());
    }

    private List<AiParent> loadAiParents(List<Long> fairytaleIds) {
        String sql = "select id, user_id, title, settings, genre, theme, chapter_count, voice_type, language, "
            + "format, status, shared, video_url, cre_dt from ai_fairytales where id in ("
            + placeholders(fairytaleIds.size()) + ") and del_yn = 'N' order by id asc";
        return jdbc.query(sql, (resultSet, rowNum) -> aiParent(resultSet), fairytaleIds.toArray());
    }

    private ImportCounts importCuratedParents(
        List<CuratedParent> parents,
        Map<Long, String> fingerprints,
        boolean countUnchanged
    ) {
        if (parents.isEmpty()) {
            return new ImportCounts(0, 0, 0);
        }
        List<Long> fairytaleIds = parents.stream().map(CuratedParent::id).toList();
        Map<Long, List<PageBuilder>> pagesByFairytale = loadCuratedPages(fairytaleIds);
        loadCuratedAudios(pagesByFairytale);
        Map<Long, List<String>> categoriesByFairytale = loadCategoryKeys(fairytaleIds);
        Map<Long, String> linkedHashes = loadLinkHashes(LegacyType.CURATED, fairytaleIds);

        int imported = 0;
        int unchanged = 0;
        long maxChangedId = 0;
        for (CuratedParent parent : parents) {
            LegacyProjection projection = projectionMapper.projectCurated(new LegacyStoryProjectionMapper.CuratedSource(
                parent.id(),
                parent.titleKo(),
                parent.titleJa(),
                parent.descriptionKo(),
                parent.descriptionJa(),
                parent.durationMin(),
                parent.contentVersion(),
                categoriesByFairytale.getOrDefault(parent.id(), List.of()),
                pagesByFairytale.getOrDefault(parent.id(), List.of()).stream()
                    .sorted(Comparator.comparingInt(PageBuilder::pageIndex))
                    .map(PageBuilder::toSource)
                    .toList(),
                parent.metadata()
            ));
            LegacyMediaSnapshotStore.PreparedImport preparedImport = mediaStore.prepare(projection);
            projection = preparedImport.projection();
            fingerprints.put(parent.id(), projection.sourceHash());
            if (projection.sourceHash().equals(linkedHashes.get(parent.id()))) {
                if (countUnchanged) {
                    unchanged++;
                }
                continue;
            }
            maxChangedId = Math.max(maxChangedId, parent.id());
            mediaStore.materialize(preparedImport);
            CanonicalStoryWriter.WriteResult writeResult = canonicalWriter.write(
                projection,
                preparedImport.media()
            );
            if (writeResult.imported()) {
                imported++;
            } else {
                unchanged++;
            }
        }
        return new ImportCounts(imported, unchanged, maxChangedId);
    }

    private ImportCounts importAiParents(
        List<AiParent> parents,
        Map<Long, String> fingerprints,
        boolean countUnchanged
    ) {
        if (parents.isEmpty()) {
            return new ImportCounts(0, 0, 0);
        }
        List<Long> fairytaleIds = parents.stream().map(AiParent::id).toList();
        Map<Long, List<LegacyStoryProjectionMapper.AiPageSource>> pagesByFairytale =
            loadAiPages(fairytaleIds);
        Map<Long, String> linkedHashes = loadLinkHashes(LegacyType.AI, fairytaleIds);
        int imported = 0;
        int unchanged = 0;
        long maxChangedId = 0;
        for (AiParent parent : parents) {
            LegacyProjection projection = projectionMapper.projectAi(new LegacyStoryProjectionMapper.AiSource(
                parent.id(),
                parent.title(),
                parent.settings(),
                parent.genre(),
                parent.theme(),
                parent.chapterCount(),
                parent.voiceType(),
                parent.language(),
                parent.format(),
                parent.status(),
                parent.shared(),
                parent.videoUrl(),
                parent.ownerUserId(),
                pagesByFairytale.getOrDefault(parent.id(), List.of()),
                parent.metadata()
            ));
            LegacyMediaSnapshotStore.PreparedImport preparedImport = mediaStore.prepare(projection);
            projection = preparedImport.projection();
            fingerprints.put(parent.id(), projection.sourceHash());
            if (projection.sourceHash().equals(linkedHashes.get(parent.id()))) {
                if (countUnchanged) {
                    unchanged++;
                }
                continue;
            }
            maxChangedId = Math.max(maxChangedId, parent.id());
            mediaStore.materialize(preparedImport);
            CanonicalStoryWriter.WriteResult writeResult = canonicalWriter.write(
                projection,
                preparedImport.media()
            );
            if (writeResult.imported()) {
                imported++;
            } else {
                unchanged++;
            }
        }
        return new ImportCounts(imported, unchanged, maxChangedId);
    }

    private Map<Long, String> loadLinkHashes(LegacyType type, List<Long> legacyIds) {
        Map<Long, String> result = new HashMap<>();
        String sql = "select legacy_id, trim(source_hash) from legacy_story_links "
            + "where legacy_type = ? and legacy_id in (" + placeholders(legacyIds.size()) + ")";
        List<Object> arguments = new ArrayList<>();
        arguments.add(type.name());
        arguments.addAll(legacyIds);
        jdbc.query(sql, (RowCallbackHandler) resultSet -> result.put(
            resultSet.getLong("legacy_id"),
            resultSet.getString(2)
        ), arguments.toArray());
        return result;
    }

    private Map<Long, List<PageBuilder>> loadCuratedPages(List<Long> fairytaleIds) {
        Map<Long, List<PageBuilder>> pages = new LinkedHashMap<>();
        String sql = "select p.id, p.fairytale_id, p.page_index, p.image_url, p.text_ko, p.text_ja, "
            + "p.placement_x, p.placement_y, p.placement_width, p.placement_height, "
            + "p.placement_z_index, p.placement_pose, p.placement_flip_x "
            + "from curated_fairytale_pages p join fairytale_details d on d.fairytale_id = p.fairytale_id "
            + "where p.fairytale_id in (" + placeholders(fairytaleIds.size()) + ") "
            + "and p.del_yn = 'N' and d.del_yn = 'N' and p.content_version = d.content_version "
            + "order by p.fairytale_id asc, p.page_index asc";
        jdbc.query(sql, resultSet -> {
            PageBuilder page = new PageBuilder(
                resultSet.getLong("id"),
                resultSet.getLong("fairytale_id"),
                resultSet.getInt("page_index"),
                resultSet.getString("image_url"),
                resultSet.getString("text_ko"),
                resultSet.getString("text_ja"),
                placement(resultSet.getObject("placement_x", Double.class),
                    resultSet.getObject("placement_y", Double.class),
                    resultSet.getObject("placement_width", Double.class),
                    resultSet.getObject("placement_height", Double.class),
                    resultSet.getObject("placement_z_index", Integer.class),
                    resultSet.getString("placement_pose"),
                    resultSet.getObject("placement_flip_x", Boolean.class))
            );
            pages.computeIfAbsent(page.fairytaleId(), ignored -> new ArrayList<>()).add(page);
        }, fairytaleIds.toArray());
        return pages;
    }

    private Map<Long, List<LegacyStoryProjectionMapper.AiPageSource>> loadAiPages(List<Long> fairytaleIds) {
        Map<Long, List<LegacyStoryProjectionMapper.AiPageSource>> pages = new LinkedHashMap<>();
        String sql = "select id, ai_fairytale_id, page_index, text, image_url, audio_url "
            + "from ai_fairytale_pages where ai_fairytale_id in (" + placeholders(fairytaleIds.size()) + ") "
            + "and del_yn = 'N' order by ai_fairytale_id asc, page_index asc";
        jdbc.query(sql, (RowCallbackHandler) resultSet -> pages
            .computeIfAbsent(resultSet.getLong("ai_fairytale_id"), ignored -> new ArrayList<>())
            .add(new LegacyStoryProjectionMapper.AiPageSource(
                resultSet.getLong("id"),
                resultSet.getInt("page_index"),
                resultSet.getString("text"),
                resultSet.getString("image_url"),
                resultSet.getString("audio_url")
            )), fairytaleIds.toArray());
        return pages;
    }

    private void loadCuratedAudios(Map<Long, List<PageBuilder>> pagesByFairytale) {
        Map<Long, PageBuilder> pagesById = pagesByFairytale.values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toMap(PageBuilder::id, page -> page));
        if (pagesById.isEmpty()) {
            return;
        }
        List<Long> pageIds = pagesById.keySet().stream().sorted().toList();
        for (int start = 0; start < pageIds.size(); start += FINGERPRINT_PAGE_SIZE) {
            List<Long> page = pageIds.subList(
                start,
                Math.min(start + FINGERPRINT_PAGE_SIZE, pageIds.size())
            );
            String sql = "select page_id, voice_type, locale, audio_url from curated_fairytale_audios "
                + "where page_id in (" + placeholders(page.size()) + ") and del_yn = 'N' "
                + "order by page_id asc, locale asc, voice_type asc";
            jdbc.query(sql, (RowCallbackHandler) resultSet -> pagesById.get(
                resultSet.getLong("page_id")
            ).audios().add(new LegacyStoryProjectionMapper.CuratedAudioSource(
                resultSet.getString("voice_type"),
                resultSet.getString("locale"),
                resultSet.getString("audio_url")
            )), page.toArray());
        }
    }

    private Map<Long, List<String>> loadCategoryKeys(List<Long> fairytaleIds) {
        Map<Long, List<String>> categories = new HashMap<>();
        String sql = "select fc.fairytale_id, c.category_key from fairytale_categories fc "
            + "join categories c on c.id = fc.category_id "
            + "where fc.fairytale_id in (" + placeholders(fairytaleIds.size()) + ") "
            + "order by fc.fairytale_id asc, c.category_key asc";
        jdbc.query(sql, (RowCallbackHandler) resultSet -> categories
            .computeIfAbsent(resultSet.getLong("fairytale_id"), ignored -> new ArrayList<>())
            .add(resultSet.getString("category_key")), fairytaleIds.toArray());
        return categories;
    }

    private LegacyProjection.CharacterPlacement placement(
        Double x,
        Double y,
        Double width,
        Double height,
        Integer zIndex,
        String pose,
        Boolean flipX
    ) {
        if (x == null || y == null || width == null || height == null
            || zIndex == null || pose == null || flipX == null) {
            return null;
        }
        return new LegacyProjection.CharacterPlacement(x, y, width, height, zIndex, pose, flipX);
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private void validateBatch(long afterLegacyId, int batchSize) {
        if (afterLegacyId < 0) {
            throw new IllegalArgumentException("afterLegacyId must be non-negative");
        }
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
    }

    private CuratedParent curatedParent(ResultSet resultSet) throws SQLException {
        return new CuratedParent(
            resultSet.getLong("id"),
            resultSet.getString("title"),
            resultSet.getString("title_ja"),
            resultSet.getString("description"),
            resultSet.getString("description_ja"),
            resultSet.getInt("duration_min"),
            resultSet.getString("content_version"),
            new LegacyContractMetadata(
                resultSet.getTimestamp("cre_dt").toLocalDateTime().toString(),
                resultSet.getObject("rating", Double.class),
                resultSet.getString("color_hex"),
                resultSet.getString("theme_tag"),
                resultSet.getBoolean("character_supported"),
                resultSet.getString("author_ko"),
                resultSet.getString("author_ja"),
                resultSet.getString("age_range"),
                resultSet.getInt("duration_min"),
                resultSet.getInt("page_count"),
                resultSet.getString("full_content_ko"),
                resultSet.getString("full_content_ja"),
                resultSet.getString("content_version"),
                null, null, null, null, null, null
            ).withCuratedHomeFlags(
                "Y".equals(resultSet.getString("is_theme")),
                "Y".equals(resultSet.getString("is_new")),
                "Y".equals(resultSet.getString("is_recommended"))
            )
        );
    }

    private AiParent aiParent(ResultSet resultSet) throws SQLException {
        boolean shared = "Y".equals(resultSet.getString("shared"));
        return new AiParent(
            resultSet.getLong("id"),
            resultSet.getObject("user_id", Long.class),
            resultSet.getString("title"),
            resultSet.getString("settings"),
            resultSet.getString("genre"),
            resultSet.getString("theme"),
            resultSet.getInt("chapter_count"),
            resultSet.getString("voice_type"),
            resultSet.getString("language"),
            resultSet.getString("format"),
            resultSet.getString("status"),
            shared,
            resultSet.getString("video_url"),
            new LegacyContractMetadata(
                resultSet.getTimestamp("cre_dt").toLocalDateTime().toString(),
                null, null, null, null, null, null, null, null, null, null, null, null,
                shared,
                resultSet.getString("voice_type"),
                resultSet.getString("settings"),
                resultSet.getString("genre"),
                resultSet.getString("theme"),
                resultSet.getInt("chapter_count")
            )
        );
    }

    private final class ExecutionLockHandle implements AutoCloseable {

        private final Connection connection;
        private final List<Long> keys;
        private final String detail;
        private boolean closed;

        private ExecutionLockHandle(Connection connection, List<Long> keys, String detail) {
            this.connection = connection;
            this.keys = List.copyOf(keys);
            this.detail = detail;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            try {
                for (int index = keys.size() - 1; index >= 0; index--) {
                    if (!releaseSessionLock(connection, keys.get(index))) {
                        throw new SQLException("PostgreSQL session advisory lock was not held");
                    }
                }
            } catch (SQLException | RuntimeException exception) {
                LegacyImportException failure = new LegacyImportException(
                    "LEGACY_IMPORT_LOCK_RELEASE_FAILED",
                    detail,
                    exception
                );
                invalidateConnection(connection, failure);
                closed = true;
                throw failure;
            }
            try {
                connection.close();
                closed = true;
            } catch (SQLException | RuntimeException exception) {
                LegacyImportException failure = new LegacyImportException(
                    "LEGACY_IMPORT_LOCK_CONNECTION_CLOSE_FAILED",
                    detail,
                    exception
                );
                invalidateConnection(connection, failure);
                closed = true;
                throw failure;
            }
        }
    }

    private record CuratedParent(
        long id,
        String titleKo,
        String titleJa,
        String descriptionKo,
        String descriptionJa,
        int durationMin,
        String contentVersion,
        LegacyContractMetadata metadata
    ) {
    }

    private record AiParent(
        long id,
        Long ownerUserId,
        String title,
        String settings,
        String genre,
        String theme,
        int chapterCount,
        String voiceType,
        String language,
        String format,
        String status,
        boolean shared,
        String videoUrl,
        LegacyContractMetadata metadata
    ) {
    }

    private record ImportCounts(int imported, int unchanged, long maxChangedId) {

        ImportCounts plus(ImportCounts other) {
            return new ImportCounts(
                imported + other.imported,
                unchanged + other.unchanged,
                Math.max(maxChangedId, other.maxChangedId)
            );
        }
    }

    private record ScanResult(
        LegacyType type,
        ImportCounts counts,
        long maxChangedId,
        long lastScannedId,
        String snapshotHash
    ) {
    }

    private record PageBuilder(
        long id,
        long fairytaleId,
        int pageIndex,
        String imageUrl,
        String textKo,
        String textJa,
        LegacyProjection.CharacterPlacement characterPlacement,
        List<LegacyStoryProjectionMapper.CuratedAudioSource> audios
    ) {
        private PageBuilder(
            long id,
            long fairytaleId,
            int pageIndex,
            String imageUrl,
            String textKo,
            String textJa,
            LegacyProjection.CharacterPlacement characterPlacement
        ) {
            this(id, fairytaleId, pageIndex, imageUrl, textKo, textJa, characterPlacement, new ArrayList<>());
        }

        LegacyStoryProjectionMapper.CuratedPageSource toSource() {
            return new LegacyStoryProjectionMapper.CuratedPageSource(
                id, pageIndex, imageUrl, textKo, textJa, characterPlacement, audios);
        }
    }
}

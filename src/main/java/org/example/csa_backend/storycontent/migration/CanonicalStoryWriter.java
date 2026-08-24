package org.example.csa_backend.storycontent.migration;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.csa_backend.storycontent.ContentVersionStatus;
import org.example.csa_backend.storycontent.LegacyType;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeAsset;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeAudioCue;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeAudioVariant;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeLayer;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeScene;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeVideo;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class CanonicalStoryWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final LegacyMediaSnapshotStore mediaStore;

    public CanonicalStoryWriter(
        JdbcTemplate jdbc,
        ObjectMapper objectMapper,
        LegacyMediaSnapshotStore mediaStore
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.mediaStore = mediaStore;
    }

    @Transactional
    public WriteResult write(
        LegacyProjection projection,
        LegacyMediaSnapshotStore.PreparedMedia preparedMedia
    ) {
        acquireImportLock(projection.legacyType(), projection.legacyId());
        LinkSnapshot currentLink = findLink(projection.legacyType(), projection.legacyId());
        if (currentLink != null && currentLink.sourceHash().equals(projection.sourceHash())) {
            return WriteResult.unchanged(currentLink.storyId(), currentLink.contentVersionId());
        }

        Instant now = Instant.now();
        StorySnapshot story = currentLink == null
            ? findRepairableStory(projection)
            : findStory(currentLink.storyId());
        long storyId = story == null ? insertStory(projection, now) : story.id();
        Long previousPublishedVersionId = story == null ? null : story.publishedVersionId();
        supersedeLinkedDraft(currentLink, now);
        int versionNo = nextVersionNo(storyId);
        long versionId = insertDraftVersion(storyId, versionNo, projection, now);

        Map<String, Long> assetIds = insertPreparedAssets(versionId, preparedMedia, now);
        insertVersionLocales(versionId, projection);
        List<RuntimeScene> runtimeScenes = new ArrayList<>();
        List<RuntimeAudioVariant> runtimeAudioVariants = new ArrayList<>();
        insertScenes(
            versionId,
            projection,
            preparedMedia,
            assetIds,
            runtimeScenes,
            runtimeAudioVariants
        );

        if (!projection.scenes().isEmpty()) {
            StoredRuntimeManifest manifest = manifest(
                storyId,
                versionId,
                versionNo,
                projection,
                preparedMedia,
                runtimeScenes,
                runtimeAudioVariants
            );
            byte[] manifestBytes = serializeManifest(manifest);
            LegacyMediaSnapshotStore.PreparedAsset manifestAsset = mediaStore.writeManifest(
                projection,
                storyId,
                versionId,
                manifestBytes
            );
            long manifestAssetId = insertAsset(versionId, manifestAsset, now);
            jdbc.update(
                "insert into content_renditions "
                    + "(version_id, type, status, manifest_asset_id, renderer_version, checksum, compatibility_fallback) "
                    + "values (?, 'SLIDE', 'READY', ?, 1, ?, true)",
                versionId,
                manifestAssetId,
                manifestAsset.sha256()
            );
            insertVideoRendition(versionId, projection, assetIds, manifestAssetId, manifestAsset.sha256());
        }

        ImportedJobs importedJobs = insertJobs(versionId, projection, now);
        finishVersionAndPointer(
            storyId,
            versionId,
            previousPublishedVersionId,
            projection,
            now
        );
        upsertLink(storyId, versionId, projection, importedJobs, now);
        return WriteResult.imported(storyId, versionId);
    }

    private void acquireImportLock(LegacyType type, long legacyId) {
        long lockKey = Math.addExact(Math.multiplyExact(legacyId, 2L), type == LegacyType.AI ? 1L : 0L);
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_xact_lock(?)")) {
                statement.setLong(1, lockKey);
                statement.execute();
            }
            return null;
        });
    }

    private LinkSnapshot findLink(LegacyType type, long legacyId) {
        return jdbc.query(
            "select story_id, content_version_id, trim(source_hash) "
                + "from legacy_story_links where legacy_type = ? and legacy_id = ?",
            (resultSet, rowNum) -> new LinkSnapshot(
                resultSet.getLong(1), resultSet.getLong(2), resultSet.getString(3)),
            type.name(),
            legacyId
        ).stream().findFirst().orElse(null);
    }

    private StorySnapshot findRepairableStory(LegacyProjection projection) {
        return jdbc.query(
            "select id, published_version_id from stories "
                + "where origin = ? and origin_ref = ? order by id asc limit 1",
            (resultSet, rowNum) -> new StorySnapshot(
                resultSet.getLong(1),
                resultSet.getObject(2, Long.class)
            ),
            projection.origin().name(),
            projection.originRef()
        ).stream().findFirst().orElse(null);
    }

    private StorySnapshot findStory(long storyId) {
        return jdbc.queryForObject(
            "select id, published_version_id from stories where id = ?",
            (resultSet, rowNum) -> new StorySnapshot(
                resultSet.getLong(1),
                resultSet.getObject(2, Long.class)
            ),
            storyId
        );
    }

    private long insertStory(LegacyProjection projection, Instant now) {
        String sourceCreatedAt = projection.contractMetadata().createdAt();
        Timestamp createdAt = sourceCreatedAt == null
            ? Timestamp.from(now)
            : legacyCreatedAt(sourceCreatedAt);
        return jdbc.queryForObject(
            "insert into stories "
                + "(origin, origin_ref, owner_user_id, visibility, title_ko, title_ja, description_ko, "
                + "description_ja, category_keys, created_at, updated_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?) returning id",
            Long.class,
            projection.origin().name(),
            projection.originRef(),
            projection.ownerUserId(),
            projection.visibility().name(),
            projection.titleKo(),
            projection.titleJa(),
            projection.descriptionKo(),
            projection.descriptionJa(),
            json(projection.categoryKeys()),
            createdAt,
            Timestamp.from(now)
        );
    }

    private Timestamp legacyCreatedAt(String value) {
        if (value.chars().allMatch(Character::isDigit)) {
            return Timestamp.from(Instant.ofEpochMilli(Long.parseLong(value)));
        }
        return Timestamp.valueOf(LocalDateTime.parse(value));
    }

    private int nextVersionNo(long storyId) {
        Integer current = jdbc.queryForObject(
            "select coalesce(max(version_no), 0) from story_content_versions where story_id = ?",
            Integer.class,
            storyId
        );
        return (current == null ? 0 : current) + 1;
    }

    private void supersedeLinkedDraft(LinkSnapshot currentLink, Instant now) {
        if (currentLink == null) {
            return;
        }
        jdbc.update(
            "update story_content_versions set status = 'SUPERSEDED', updated_at = ? "
                + "where id = ? and status = 'DRAFT'",
            Timestamp.from(now),
            currentLink.contentVersionId()
        );
    }

    private long insertDraftVersion(
        long storyId,
        int versionNo,
        LegacyProjection projection,
        Instant now
    ) {
        return jdbc.queryForObject(
            "insert into story_content_versions "
                + "(story_id, version_no, status, schema_version, lock_version, source_revision, "
                + "legacy_contract_metadata, created_at, updated_at) "
                + "values (?, ?, 'DRAFT', 1, 0, 0, cast(? as jsonb), ?, ?) returning id",
            Long.class,
            storyId,
            versionNo,
            json(projection.contractMetadata()),
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    private Map<String, Long> insertPreparedAssets(
        long versionId,
        LegacyMediaSnapshotStore.PreparedMedia preparedMedia,
        Instant now
    ) {
        Map<String, Long> ids = new LinkedHashMap<>();
        for (LegacyMediaSnapshotStore.PreparedAsset asset : preparedMedia.assets().values().stream()
            .sorted(java.util.Comparator.comparing(LegacyMediaSnapshotStore.PreparedAsset::assetKey))
            .toList()) {
            ids.put(asset.assetKey(), reuseOrInsertPreparedAsset(versionId, asset, now));
        }
        return ids;
    }

    private long reuseOrInsertPreparedAsset(
        long versionId,
        LegacyMediaSnapshotStore.PreparedAsset asset,
        Instant now
    ) {
        ExistingAsset existing = jdbc.query(
            "select id, kind, trim(sha256) as sha256, status from media_assets where storage_key = ?",
            (resultSet, rowNum) -> new ExistingAsset(
                resultSet.getLong("id"),
                resultSet.getString("kind"),
                resultSet.getString("sha256"),
                resultSet.getString("status")
            ),
            asset.storageKey()
        ).stream().findFirst().orElse(null);
        if (existing == null) {
            return insertAsset(versionId, asset, now);
        }
        if (!asset.kind().name().equals(existing.kind())
            || !asset.sha256().equals(existing.sha256())
            || !"READY".equals(existing.status())) {
            throw new LegacyImportException("LEGACY_MEDIA_ASSET_CONFLICT", asset.storageKey());
        }
        return existing.id();
    }

    private long insertAsset(
        long versionId,
        LegacyMediaSnapshotStore.PreparedAsset asset,
        Instant now
    ) {
        return jdbc.queryForObject(
            "insert into media_assets "
                + "(owner_version_id, kind, storage_key, public_url, sha256, actual_mime_type, byte_size, status, created_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, 'READY', ?) returning id",
            Long.class,
            versionId,
            asset.kind().name(),
            asset.storageKey(),
            asset.publicUrl(),
            asset.sha256(),
            asset.mimeType(),
            asset.byteSize(),
            Timestamp.from(now)
        );
    }

    private record ExistingAsset(long id, String kind, String sha256, String status) {
    }

    private void insertVersionLocales(long versionId, LegacyProjection projection) {
        for (String locale : projection.availableVoiceTypes().keySet().stream().sorted().toList()) {
            jdbc.update(
                "insert into story_version_locales (version_id, locale, default_voice_type) values (?, ?, ?)",
                versionId,
                locale,
                projection.defaultVoiceTypes().get(locale)
            );
        }
    }

    private void insertScenes(
        long versionId,
        LegacyProjection projection,
        LegacyMediaSnapshotStore.PreparedMedia preparedMedia,
        Map<String, Long> assetIds,
        List<RuntimeScene> runtimeScenes,
        List<RuntimeAudioVariant> runtimeAudioVariants
    ) {
        for (LegacyProjection.SceneProjection scene : projection.scenes().stream()
            .sorted(java.util.Comparator.comparingInt(LegacyProjection.SceneProjection::orderIndex)
                .thenComparing(LegacyProjection.SceneProjection::sceneKey))
            .toList()) {
            String imageKey = scene.sceneKey() + "-image";
            Long fallbackAssetId = requiredAssetId(assetIds, imageKey);
            long sceneId = jdbc.queryForObject(
                "insert into story_scenes "
                    + "(version_id, scene_key, order_index, width, height, duration_ms, fallback_asset_id) "
                    + "values (?, ?, ?, ?, ?, ?, ?) returning id",
                Long.class,
                versionId,
                scene.sceneKey(),
                scene.orderIndex(),
                scene.width(),
                scene.height(),
                scene.durationMs(),
                fallbackAssetId
            );
            scene.text().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> jdbc.update(
                    "insert into scene_localized_contents (scene_id, locale, display_text, script_text) "
                        + "values (?, ?, ?, ?)",
                    sceneId,
                    entry.getKey(),
                    entry.getValue(),
                    entry.getValue()
                ));

            List<RuntimeAudioCue> runtimeCues = insertSceneAudio(
                sceneId,
                scene,
                projection,
                assetIds,
                runtimeAudioVariants
            );
            List<RuntimeLayer> runtimeLayers = insertCharacterLayer(sceneId, scene);
            runtimeScenes.add(new RuntimeScene(
                scene.sceneKey(),
                scene.orderIndex(),
                scene.durationMs(),
                sortedMap(scene.text()),
                imageKey,
                null,
                runtimeCues,
                runtimeLayers,
                List.of(),
                List.of(),
                List.of()
            ));
        }
    }

    private List<RuntimeAudioCue> insertSceneAudio(
        long sceneId,
        LegacyProjection.SceneProjection scene,
        LegacyProjection projection,
        Map<String, Long> assetIds,
        List<RuntimeAudioVariant> runtimeAudioVariants
    ) {
        if (scene.audios().isEmpty()) {
            return List.of();
        }
        String cueKey = "narration";
        long cueId = jdbc.queryForObject(
            "insert into scene_audio_cues (scene_id, cue_key, role, start_ms, required) "
                + "values (?, ?, 'NARRATION', 0, true) returning id",
            Long.class,
            sceneId,
            cueKey
        );
        for (LegacyProjection.AudioProjection audio : scene.audios().stream()
            .sorted(java.util.Comparator.comparing(LegacyProjection.AudioProjection::locale)
                .thenComparing(LegacyProjection.AudioProjection::voiceType)
                .thenComparing(LegacyProjection.AudioProjection::audioUrl))
            .toList()) {
            String assetKey = audioAssetKey(scene, audio);
            jdbc.update(
                "insert into audio_variants (audio_cue_id, locale, voice_type, asset_id, status) "
                    + "values (?, ?, ?, ?, 'READY')",
                cueId,
                audio.locale(),
                audio.voiceType(),
                requiredAssetId(assetIds, assetKey)
            );
            runtimeAudioVariants.add(new RuntimeAudioVariant(
                scene.sceneKey(), cueKey, audio.locale(), audio.voiceType(), assetKey));
        }
        String locale = projection.availableVoiceTypes().containsKey("ko") ? "ko"
            : projection.availableVoiceTypes().keySet().iterator().next();
        String voice = projection.defaultVoiceTypes().get(locale);
        LegacyProjection.AudioProjection selected = scene.audios().stream()
            .filter(audio -> audio.locale().equals(locale) && audio.voiceType().equals(voice))
            .findFirst()
            .orElse(scene.audios().get(0));
        return List.of(new RuntimeAudioCue(cueKey, "NARRATION", audioAssetKey(scene, selected), 0));
    }

    private List<RuntimeLayer> insertCharacterLayer(
        long sceneId,
        LegacyProjection.SceneProjection scene
    ) {
        LegacyProjection.CharacterPlacement placement = scene.characterPlacement();
        if (placement == null) {
            return List.of();
        }
        Map<String, Object> properties = Map.of(
            "pose", placement.pose(),
            "flipX", placement.flipX()
        );
        jdbc.update(
            "insert into story_layers "
                + "(scene_id, layer_key, type, z_index, x, y, scale_x, scale_y, rotation_deg, opacity, visible, properties_json) "
                + "values (?, 'legacy-character', 'CHARACTER_SLOT', ?, ?, ?, ?, ?, 0, 1, true, cast(? as jsonb))",
            sceneId,
            placement.zIndex(),
            placement.x(),
            placement.y(),
            placement.width(),
            placement.height(),
            json(properties)
        );
        return List.of(new RuntimeLayer(
            "legacy-character",
            "CHARACTER_SLOT",
            placement.zIndex(),
            null,
            BigDecimal.valueOf(placement.x()),
            BigDecimal.valueOf(placement.y()),
            BigDecimal.valueOf(placement.width()),
            BigDecimal.valueOf(placement.height()),
            BigDecimal.ZERO,
            BigDecimal.ONE,
            true,
            properties
        ));
    }

    private StoredRuntimeManifest manifest(
        long storyId,
        long versionId,
        int versionNo,
        LegacyProjection projection,
        LegacyMediaSnapshotStore.PreparedMedia preparedMedia,
        List<RuntimeScene> runtimeScenes,
        List<RuntimeAudioVariant> runtimeAudioVariants
    ) {
        List<RuntimeAsset> assets = preparedMedia.assets().values().stream()
            .sorted(java.util.Comparator.comparing(LegacyMediaSnapshotStore.PreparedAsset::assetKey))
            .map(asset -> new RuntimeAsset(
                asset.assetKey(), asset.kind().name(), asset.publicUrl(), asset.sha256(), asset.byteSize()))
            .toList();
        List<String> locales = projection.availableVoiceTypes().keySet().stream().sorted().toList();
        String selectedLocale = locales.contains("ko") ? "ko" : locales.get(0);
        String selectedVoice = projection.defaultVoiceTypes().get(selectedLocale);
        List<RuntimeVideo> videoVariants = projection.video() == null
            ? List.of()
            : List.of(new RuntimeVideo(
                "video",
                projection.video().locale(),
                projection.video().voiceType(),
                "UPLOADED_MASTER"
            ));
        return new StoredRuntimeManifest(
            1,
            storyId,
            versionId,
            Integer.toString(versionNo),
            projection.origin().name(),
            "SLIDE",
            "SLIDE",
            "1",
            locales,
            selectedLocale,
            selectedVoice,
            sortedListMap(projection.availableVoiceTypes()),
            sortedMap(projection.defaultVoiceTypes()),
            assets,
            runtimeAudioVariants,
            runtimeScenes,
            videoVariants,
            null
        );
    }

    private byte[] serializeManifest(StoredRuntimeManifest manifest) {
        try {
            return objectMapper.writeValueAsBytes(manifest);
        } catch (RuntimeException exception) {
            throw new LegacyImportException("MANIFEST_SERIALIZATION_FAILED", null, exception);
        }
    }

    private ImportedJobs insertJobs(long versionId, LegacyProjection projection, Instant now) {
        Long generationJobId = null;
        Long videoJobId = null;
        for (LegacyProjection.JobProjection job : projection.jobs()) {
            boolean terminal = List.of("SUCCEEDED", "FAILED", "CANCELLED").contains(job.status());
            Long jobId = jdbc.queryForObject(
                "insert into content_render_jobs "
                    + "(version_id, kind, status, locale, voice_type, source_revision, attempt, error_code, "
                    + "created_at, started_at, finished_at) values (?, ?, ?, 'und', 'none', 0, 1, ?, ?, ?, ?) "
                    + "returning id",
                Long.class,
                versionId,
                job.kind(),
                job.status(),
                job.errorCode(),
                Timestamp.from(now),
                "RUNNING".equals(job.status()) || terminal ? Timestamp.from(now) : null,
                terminal ? Timestamp.from(now) : null
            );
            if ("CONTENT_GENERATION".equals(job.kind())) {
                generationJobId = jobId;
            } else if ("VIDEO_RENDER".equals(job.kind())) {
                videoJobId = jobId;
            }
        }
        return new ImportedJobs(generationJobId, videoJobId);
    }

    private void insertVideoRendition(
        long versionId,
        LegacyProjection projection,
        Map<String, Long> assetIds,
        long manifestAssetId,
        String manifestChecksum
    ) {
        if (projection.video() == null) {
            return;
        }
        long renditionId = jdbc.queryForObject(
            "insert into content_renditions "
                + "(version_id, type, status, manifest_asset_id, renderer_version, checksum, compatibility_fallback) "
                + "values (?, 'VIDEO', 'READY', ?, 1, ?, false) returning id",
            Long.class,
            versionId,
            manifestAssetId,
            manifestChecksum
        );
        jdbc.update(
            "insert into content_rendition_variants "
                + "(rendition_id, locale, voice_type, output_asset_id, output_mode, status, source_revision) "
                + "values (?, ?, ?, ?, 'UPLOADED_MASTER', 'READY', 0)",
            renditionId,
            projection.video().locale(),
            projection.video().voiceType(),
            requiredAssetId(assetIds, "video")
        );
    }

    private void finishVersionAndPointer(
        long storyId,
        long versionId,
        Long previousPublishedVersionId,
        LegacyProjection projection,
        Instant now
    ) {
        if (projection.publishedPointer()) {
            if (previousPublishedVersionId != null && previousPublishedVersionId != versionId) {
                jdbc.update(
                    "update story_content_versions set status = 'SUPERSEDED', updated_at = ? "
                        + "where id = ? and status = 'PUBLISHED'",
                    Timestamp.from(now),
                    previousPublishedVersionId
                );
            }
            jdbc.update(
                "update story_content_versions set status = 'PUBLISHED', published_at = ?, updated_at = ? where id = ?",
                Timestamp.from(now),
                Timestamp.from(now),
                versionId
            );
            jdbc.update(
                "update stories set owner_user_id = ?, visibility = ?, title_ko = ?, title_ja = ?, "
                    + "description_ko = ?, description_ja = ?, category_keys = cast(? as jsonb), "
                    + "published_version_id = ?, updated_at = ? where id = ?",
                projection.ownerUserId(),
                projection.visibility().name(),
                projection.titleKo(),
                projection.titleJa(),
                projection.descriptionKo(),
                projection.descriptionJa(),
                json(projection.categoryKeys()),
                versionId,
                Timestamp.from(now),
                storyId
            );
        } else {
            jdbc.update(
                "update stories set owner_user_id = ?, visibility = ?, title_ko = ?, title_ja = ?, "
                    + "description_ko = ?, description_ja = ?, category_keys = cast(? as jsonb), updated_at = ? "
                    + "where id = ?",
                projection.ownerUserId(),
                projection.visibility().name(),
                projection.titleKo(),
                projection.titleJa(),
                projection.descriptionKo(),
                projection.descriptionJa(),
                json(projection.categoryKeys()),
                Timestamp.from(now),
                storyId
            );
        }
    }

    private void upsertLink(
        long storyId,
        long versionId,
        LegacyProjection projection,
        ImportedJobs importedJobs,
        Instant now
    ) {
        jdbc.update(
            "insert into legacy_story_links "
                + "(legacy_type, legacy_id, story_id, content_version_id, legacy_format, legacy_status_code, "
                + "legacy_language, imported_generation_job_id, imported_video_job_id, source_hash, imported_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (legacy_type, legacy_id) do update set "
                + "story_id = excluded.story_id, content_version_id = excluded.content_version_id, "
                + "legacy_format = excluded.legacy_format, legacy_status_code = excluded.legacy_status_code, "
                + "legacy_language = excluded.legacy_language, "
                + "imported_generation_job_id = excluded.imported_generation_job_id, "
                + "imported_video_job_id = excluded.imported_video_job_id, source_hash = excluded.source_hash, "
                + "imported_at = excluded.imported_at",
            projection.legacyType().name(),
            projection.legacyId(),
            storyId,
            versionId,
            projection.legacyFormat(),
            projection.legacyStatusCode(),
            projection.legacyLanguage(),
            importedJobs.generationJobId(),
            importedJobs.videoJobId(),
            projection.sourceHash(),
            Timestamp.from(now)
        );
    }

    private Long requiredAssetId(Map<String, Long> assetIds, String assetKey) {
        Long id = assetIds.get(assetKey);
        if (id == null) {
            throw new LegacyImportException("CANONICAL_ASSET_MISSING", assetKey);
        }
        return id;
    }

    private String audioAssetKey(
        LegacyProjection.SceneProjection scene,
        LegacyProjection.AudioProjection audio
    ) {
        return scene.sceneKey() + "-audio-" + audio.voiceType() + "-" + audio.locale();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new LegacyImportException("CANONICAL_JSON_SERIALIZATION_FAILED", null, exception);
        }
    }

    private <T> Map<String, T> sortedMap(Map<String, T> source) {
        Map<String, T> ordered = new LinkedHashMap<>();
        source.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return java.util.Collections.unmodifiableMap(ordered);
    }

    private Map<String, List<String>> sortedListMap(Map<String, List<String>> source) {
        Map<String, List<String>> ordered = new LinkedHashMap<>();
        source.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> ordered.put(entry.getKey(), List.copyOf(entry.getValue())));
        return java.util.Collections.unmodifiableMap(ordered);
    }

    private record LinkSnapshot(long storyId, long contentVersionId, String sourceHash) {
    }

    private record StorySnapshot(long id, Long publishedVersionId) {
    }

    private record ImportedJobs(Long generationJobId, Long videoJobId) {
    }

    public record WriteResult(boolean imported, long storyId, long contentVersionId) {
        static WriteResult imported(long storyId, long contentVersionId) {
            return new WriteResult(true, storyId, contentVersionId);
        }

        static WriteResult unchanged(long storyId, long contentVersionId) {
            return new WriteResult(false, storyId, contentVersionId);
        }
    }
}

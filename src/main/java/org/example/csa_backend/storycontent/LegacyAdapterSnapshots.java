package org.example.csa_backend.storycontent;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.example.csa_backend.storycontent.migration.LegacyContractMetadata;
import org.example.csa_backend.storycontent.migration.LegacyImportException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
final class LegacyAdapterSnapshots {

    private final JdbcTemplate jdbc;
    private final LegacyContractMediaIdentity mediaIdentity;
    private final ObjectMapper objectMapper;

    LegacyAdapterSnapshots(
        JdbcTemplate jdbc,
        LegacyContractMediaIdentity mediaIdentity,
        ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.mediaIdentity = mediaIdentity;
        this.objectMapper = objectMapper;
    }

    Object fromLegacy(LegacyType type, long legacyId) {
        return type == LegacyType.CURATED
            ? curatedLegacy(legacyId)
            : aiLegacy(legacyId);
    }

    Object fromCanonical(LegacyType type, long legacyId) {
        List<CanonicalHeader> headers = jdbc.query(
            "select l.legacy_type, l.legacy_id, l.legacy_format, l.legacy_status_code, "
                + "l.legacy_language, l.content_version_id, s.id as story_id, s.owner_user_id, "
                + "s.visibility, s.title_ko, s.title_ja, s.description_ko, s.description_ja, "
                + "s.category_keys::text as category_keys, s.published_version_id, s.created_at, "
                + "v.status, v.legacy_contract_metadata::text as legacy_contract_metadata "
                + "from legacy_story_links l join stories s on s.id = l.story_id "
                + "join story_content_versions v on v.id = l.content_version_id "
                + "where l.legacy_type = ? and l.legacy_id = ?",
            (resultSet, rowNum) -> canonicalHeader(resultSet),
            type.name(),
            legacyId
        );
        if (headers.isEmpty()) {
            return new LegacyFairytaleReadAdapter.MissingCanonicalSnapshot(
                type.name(), legacyId, true);
        }
        CanonicalHeader header = headers.get(0);
        Map<Long, CanonicalSceneBuilder> scenes = canonicalScenes(header.versionId());
        LegacyFairytaleReadAdapter.MigrationState migration = migration(
            header.ownerUserId(),
            header.visibility(),
            header.versionStatus(),
            Objects.equals(header.publishedVersionId(), header.versionId())
                && "PUBLISHED".equals(header.versionStatus()),
            header.legacyFormat(),
            header.legacyStatusCode(),
            header.legacyLanguage(),
            scenes
        );
        List<LegacyFairytaleReadAdapter.Job> jobs = canonicalJobs(header.versionId());
        String video = canonicalVideoIdentity(header.versionId());
        return type == LegacyType.CURATED
            ? canonicalCurated(header, migration, scenes, jobs)
            : canonicalAi(header, migration, scenes, jobs, video);
    }

    private LegacyFairytaleReadAdapter.Snapshot curatedLegacy(long legacyId) {
        CuratedHeader header = jdbc.query(
            "select f.id, f.title, f.title_ja, f.description, f.description_ja, f.rating, "
                + "f.color_hex, f.theme_tag, f.character_supported, f.cre_dt, "
                + "d.author_ko, d.author_ja, d.age_range, d.duration_min, d.page_count, "
                + "d.full_content_ko, d.full_content_ja, d.content_version "
                + "from fairytales f join fairytale_details d on d.fairytale_id = f.id "
                + "where f.id = ? and f.del_yn = 'N' and d.del_yn = 'N'",
            (resultSet, rowNum) -> curatedHeader(resultSet),
            legacyId
        ).stream().findFirst().orElseThrow(() -> new LegacyImportException(
            "LEGACY_STORY_NOT_FOUND", "CURATED:" + legacyId));
        List<String> categories = jdbc.query(
            "select c.category_key from fairytale_categories fc join categories c on c.id = fc.category_id "
                + "where fc.fairytale_id = ? order by c.category_key asc",
            (resultSet, rowNum) -> resultSet.getString(1),
            legacyId
        );
        List<CuratedPageBuilder> pages = curatedPages(header);
        Map<String, List<String>> voices = completeCuratedVoices(pages);
        Map<String, String> defaults = defaults(voices);
        boolean pageLess = pages.isEmpty();
        boolean publishable = !pageLess
            && !voices.getOrDefault("ko", List.of()).isEmpty()
            && !voices.getOrDefault("ja", List.of()).isEmpty();
        LegacyFairytaleReadAdapter.MigrationState migration = new LegacyFairytaleReadAdapter.MigrationState(
            null,
            pageLess ? "ARCHIVED" : "PUBLISHED",
            publishable ? "PUBLISHED" : "DRAFT",
            publishable,
            "slide",
            pageLess ? "INCOMPLETE_PAGES" : publishable ? "COMPLETED" : "INCOMPLETE_VOICE_MATRIX",
            "ko,ja",
            pageLess ? Map.of() : voices,
            pageLess ? Map.of() : defaults
        );
        Long canonicalStoryId = publishedStoryId(LegacyType.CURATED, legacyId);
        LegacyFairytaleReadAdapter.CuratedList list = new LegacyFairytaleReadAdapter.CuratedList(
            legacyId, header.titleKo(), header.titleJa(), header.descriptionKo(), header.descriptionJa(),
            header.rating(), header.colorHex(), header.themeTag(), categories,
            header.characterSupported(), canonicalStoryId
        );
        LegacyFairytaleReadAdapter.CuratedDetail detail = new LegacyFairytaleReadAdapter.CuratedDetail(
            header.authorKo(), header.authorJa(), header.ageRange(), header.durationMin(),
            header.pageCount(), header.fullContentKo(), header.fullContentJa(),
            header.characterSupported(), "LOCAL_OVERLAY", header.contentVersion()
        );
        LegacyFairytaleReadAdapter.CuratedSlides slides = pages.isEmpty()
            ? null
            : new LegacyFairytaleReadAdapter.CuratedSlides(
                legacyId,
                header.contentVersion(),
                header.characterSupported(),
                "LOCAL_OVERLAY",
                pages.stream().map(page -> page.snapshot(header.characterSupported())).toList()
            );
        return new LegacyFairytaleReadAdapter.Snapshot(
            LegacyType.CURATED.name(), legacyId, migration, list, detail, slides,
            null, null, List.of()
        );
    }

    private LegacyFairytaleReadAdapter.Snapshot aiLegacy(long legacyId) {
        AiHeader header = jdbc.query(
            "select id, user_id, title, settings, genre, theme, chapter_count, voice_type, language, "
                + "format, status, shared, video_url, cre_dt from ai_fairytales "
                + "where id = ? and del_yn = 'N'",
            (resultSet, rowNum) -> aiHeader(resultSet),
            legacyId
        ).stream().findFirst().orElseThrow(() -> new LegacyImportException(
            "LEGACY_STORY_NOT_FOUND", "AI:" + legacyId));
        List<LegacyFairytaleReadAdapter.AiPage> pages = jdbc.query(
            "select page_index, text, image_url, audio_url from ai_fairytale_pages "
                + "where ai_fairytale_id = ? and del_yn = 'N' order by page_index asc, id asc",
            (resultSet, rowNum) -> new LegacyFairytaleReadAdapter.AiPage(
                resultSet.getInt("page_index"),
                resultSet.getString("text"),
                mediaIdentity.fromLegacyUrl(resultSet.getString("image_url")),
                mediaIdentity.fromLegacyUrl(resultSet.getString("audio_url"))
            ),
            legacyId
        );
        String status = header.status().toUpperCase();
        String format = "video".equalsIgnoreCase(header.format()) ? "video" : "slide";
        String locale = normalizedLocale(header.language());
        String voiceType = fallback(header.voiceType(), "narrator");
        boolean hasPages = !pages.isEmpty();
        boolean completed = "COMPLETED".equals(status);
        boolean failedVideoWithPages = "video".equals(format) && "FAILED".equals(status) && hasPages;
        boolean completeVoice = hasPages && pages.stream().allMatch(page -> page.audioUrl() != null);
        Map<String, List<String>> voices = Map.of(locale, completeVoice ? List.of(voiceType) : List.of());
        Map<String, String> defaults = completeVoice ? Map.of(locale, voiceType) : Map.of();
        boolean publishable = completed && hasPages
            && (!"video".equals(format) || header.videoUrl() != null)
            && header.ownerId() != null;
        String visibility = header.ownerId() == null
            ? "ARCHIVED"
            : header.shared() ? "SHARED" : "OWNER_PRIVATE";
        LegacyFairytaleReadAdapter.MigrationState migration = new LegacyFairytaleReadAdapter.MigrationState(
            header.ownerId(), visibility, publishable ? "PUBLISHED" : "DRAFT", publishable,
            format, status, locale, voices, defaults
        );
        LegacyFairytaleReadAdapter.AiList list = new LegacyFairytaleReadAdapter.AiList(
            legacyId, header.title(), format, status, locale, header.shared(),
            pages.isEmpty() ? null : pages.get(0).imageUrl(), pages.size(),
            header.createdAt(), header.ownerId()
        );
        LegacyFairytaleReadAdapter.AiSlides slides = completed || failedVideoWithPages
            ? new LegacyFairytaleReadAdapter.AiSlides(
                legacyId, header.title(), locale, voiceType, pages,
                mediaIdentity.fromLegacyUrl(header.videoUrl())
            )
            : null;
        return new LegacyFairytaleReadAdapter.Snapshot(
            LegacyType.AI.name(), legacyId, migration, null, null, null,
            list, slides, aiJobs(status, format, hasPages)
        );
    }

    private LegacyFairytaleReadAdapter.Snapshot canonicalCurated(
        CanonicalHeader header,
        LegacyFairytaleReadAdapter.MigrationState migration,
        Map<Long, CanonicalSceneBuilder> scenes,
        List<LegacyFairytaleReadAdapter.Job> jobs
    ) {
        LegacyContractMetadata metadata = header.metadata();
        boolean characterSupported = metadata.curatedCharacterSupportedOrDefault();
        Long canonicalStoryId = migration.publishedPointer()
            && "PUBLISHED".equals(migration.visibility()) ? header.storyId() : null;
        LegacyFairytaleReadAdapter.CuratedList list = new LegacyFairytaleReadAdapter.CuratedList(
            header.legacyId(), header.titleKo(), header.titleJa(), header.descriptionKo(),
            header.descriptionJa(), metadata.curatedRating(), metadata.curatedColorHex(),
            metadata.curatedThemeTag(), header.categoryKeys(), characterSupported, canonicalStoryId
        );
        LegacyFairytaleReadAdapter.CuratedDetail detail = new LegacyFairytaleReadAdapter.CuratedDetail(
            metadata.curatedAuthorKo(), metadata.curatedAuthorJa(), metadata.curatedAgeRange(),
            number(metadata.curatedDurationMin()), number(metadata.curatedPageCount()),
            metadata.curatedFullContentKo(), metadata.curatedFullContentJa(), characterSupported,
            "LOCAL_OVERLAY", metadata.curatedContentVersion()
        );
        LegacyFairytaleReadAdapter.CuratedSlides slides = scenes.isEmpty()
            ? null
            : new LegacyFairytaleReadAdapter.CuratedSlides(
                header.legacyId(), metadata.curatedContentVersion(), characterSupported, "LOCAL_OVERLAY",
                scenes.values().stream().map(scene -> scene.curatedSnapshot(characterSupported)).toList()
            );
        return new LegacyFairytaleReadAdapter.Snapshot(
            LegacyType.CURATED.name(), header.legacyId(), migration, list, detail, slides,
            null, null, jobs
        );
    }

    private LegacyFairytaleReadAdapter.Snapshot canonicalAi(
        CanonicalHeader header,
        LegacyFairytaleReadAdapter.MigrationState migration,
        Map<Long, CanonicalSceneBuilder> scenes,
        List<LegacyFairytaleReadAdapter.Job> jobs,
        String video
    ) {
        LegacyContractMetadata metadata = header.metadata();
        boolean shared = metadata.aiShared() != null
            ? metadata.aiShared()
            : "SHARED".equals(header.visibility());
        String locale = normalizedLocale(header.legacyLanguage());
        String voiceType = fallback(metadata.aiVoiceType(),
            migration.defaultVoiceTypes().get(locale));
        List<LegacyFairytaleReadAdapter.AiPage> pages = scenes.values().stream()
            .map(scene -> scene.aiSnapshot(locale, voiceType))
            .toList();
        LegacyFairytaleReadAdapter.AiList list = new LegacyFairytaleReadAdapter.AiList(
            header.legacyId(), header.titleKo(), header.legacyFormat(), header.legacyStatusCode(),
            locale, shared, pages.isEmpty() ? null : pages.get(0).imageUrl(), pages.size(),
            exactCreatedAt(metadata.createdAt(), header.storyCreatedAt()), header.ownerUserId()
        );
        boolean servable = "COMPLETED".equals(header.legacyStatusCode())
            || ("video".equals(header.legacyFormat())
                && "FAILED".equals(header.legacyStatusCode()) && !pages.isEmpty());
        LegacyFairytaleReadAdapter.AiSlides slides = servable
            ? new LegacyFairytaleReadAdapter.AiSlides(
                header.legacyId(), header.titleKo(), locale, voiceType, pages, video)
            : null;
        return new LegacyFairytaleReadAdapter.Snapshot(
            LegacyType.AI.name(), header.legacyId(), migration, null, null, null,
            list, slides, jobs
        );
    }

    private List<CuratedPageBuilder> curatedPages(CuratedHeader header) {
        if (header.contentVersion() == null || header.contentVersion().isBlank()) {
            return List.of();
        }
        List<CuratedPageBuilder> pages = jdbc.query(
            "select id, page_index, image_url, text_ko, text_ja, placement_x, placement_y, "
                + "placement_width, placement_height, placement_z_index, placement_pose, placement_flip_x "
                + "from curated_fairytale_pages where fairytale_id = ? and content_version = ? "
                + "and del_yn = 'N' order by page_index asc, id asc",
            (resultSet, rowNum) -> new CuratedPageBuilder(
                resultSet.getLong("id"),
                resultSet.getInt("page_index"),
                mediaIdentity.fromLegacyUrl(resultSet.getString("image_url")),
                resultSet.getString("text_ko"),
                resultSet.getString("text_ja"),
                placement(resultSet),
                new ArrayList<>()
            ),
            header.id(),
            header.contentVersion()
        );
        if (pages.isEmpty()) {
            return pages;
        }
        Map<Long, CuratedPageBuilder> byId = new LinkedHashMap<>();
        pages.forEach(page -> byId.put(page.id(), page));
        String placeholders = String.join(",", Collections.nCopies(pages.size(), "?"));
        jdbc.query(
            "select page_id, voice_type, locale, audio_url from curated_fairytale_audios "
                + "where page_id in (" + placeholders + ") and del_yn = 'N' "
                + "order by page_id asc, voice_type asc, locale asc, id asc",
            (RowCallbackHandler) resultSet -> byId.get(resultSet.getLong("page_id")).audios().add(
                new AudioValue(
                    resultSet.getString("voice_type"),
                    resultSet.getString("locale"),
                    mediaIdentity.fromLegacyUrl(resultSet.getString("audio_url"))
                )
            ),
            pages.stream().map(CuratedPageBuilder::id).toArray()
        );
        return pages;
    }

    private Map<Long, CanonicalSceneBuilder> canonicalScenes(long versionId) {
        Map<Long, CanonicalSceneBuilder> scenes = new LinkedHashMap<>();
        jdbc.query(
            "select s.id, s.scene_key, s.order_index, trim(a.sha256) as image_sha "
                + "from story_scenes s join media_assets a on a.id = s.fallback_asset_id "
                + "where s.version_id = ? order by s.order_index asc, s.scene_key asc",
            (RowCallbackHandler) resultSet -> scenes.put(
                resultSet.getLong("id"),
                new CanonicalSceneBuilder(
                    resultSet.getString("scene_key"),
                    resultSet.getInt("order_index"),
                    mediaIdentity.fromCanonicalSha(resultSet.getString("image_sha")),
                    new LinkedHashMap<>(),
                    new ArrayList<>(),
                    null
                )
            ),
            versionId
        );
        if (scenes.isEmpty()) {
            return scenes;
        }
        jdbc.query(
            "select c.scene_id, c.locale, c.display_text from scene_localized_contents c "
                + "join story_scenes s on s.id = c.scene_id where s.version_id = ? "
                + "order by s.order_index asc, c.locale asc",
            (RowCallbackHandler) resultSet -> scenes.get(resultSet.getLong("scene_id"))
                .text().put(resultSet.getString("locale"), resultSet.getString("display_text")),
            versionId
        );
        jdbc.query(
            "select c.scene_id, a.locale, a.voice_type, trim(m.sha256) as media_sha "
                + "from audio_variants a join scene_audio_cues c on c.id = a.audio_cue_id "
                + "join story_scenes s on s.id = c.scene_id join media_assets m on m.id = a.asset_id "
                + "where s.version_id = ? and a.status = 'READY' "
                + "order by s.order_index asc, a.voice_type asc, a.locale asc",
            (RowCallbackHandler) resultSet -> scenes.get(resultSet.getLong("scene_id")).audios().add(
                new AudioValue(
                    resultSet.getString("voice_type"),
                    resultSet.getString("locale"),
                    mediaIdentity.fromCanonicalSha(resultSet.getString("media_sha"))
                )
            ),
            versionId
        );
        jdbc.query(
            "select l.scene_id, l.x, l.y, l.scale_x, l.scale_y, l.z_index, "
                + "l.properties_json->>'pose' as pose, "
                + "coalesce((l.properties_json->>'flipX')::boolean, false) as flip_x "
                + "from story_layers l join story_scenes s on s.id = l.scene_id "
                + "where s.version_id = ? and l.type = 'CHARACTER_SLOT' order by l.scene_id asc",
            (RowCallbackHandler) resultSet -> scenes.get(resultSet.getLong("scene_id")).placement(
                new LegacyFairytaleReadAdapter.CharacterPlacement(
                    resultSet.getBigDecimal("x").doubleValue(),
                    resultSet.getBigDecimal("y").doubleValue(),
                    resultSet.getBigDecimal("scale_x").doubleValue(),
                    resultSet.getBigDecimal("scale_y").doubleValue(),
                    resultSet.getInt("z_index"),
                    resultSet.getString("pose"),
                    resultSet.getBoolean("flip_x")
                )
            ),
            versionId
        );
        return scenes;
    }

    private LegacyFairytaleReadAdapter.MigrationState migration(
        Long ownerUserId,
        String visibility,
        String versionStatus,
        boolean publishedPointer,
        String format,
        String status,
        String language,
        Map<Long, CanonicalSceneBuilder> scenes
    ) {
        Map<String, List<String>> available = completeCanonicalVoices(scenes);
        return new LegacyFairytaleReadAdapter.MigrationState(
            ownerUserId, visibility, versionStatus, publishedPointer, format, status, language,
            available, defaults(available)
        );
    }

    private Map<String, List<String>> completeCuratedVoices(List<CuratedPageBuilder> pages) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("ko", completeVoices(pages.stream().map(CuratedPageBuilder::audios).toList(), "ko"));
        result.put("ja", completeVoices(pages.stream().map(CuratedPageBuilder::audios).toList(), "ja"));
        return result;
    }

    private Map<String, List<String>> completeCanonicalVoices(Map<Long, CanonicalSceneBuilder> scenes) {
        Set<String> locales = new LinkedHashSet<>();
        scenes.values().forEach(scene -> scene.audios().forEach(audio -> locales.add(audio.locale())));
        Map<String, List<String>> result = new LinkedHashMap<>();
        locales.stream().sorted().forEach(locale -> result.put(
            locale,
            completeVoices(scenes.values().stream().map(CanonicalSceneBuilder::audios).toList(), locale)
        ));
        return result;
    }

    private List<String> completeVoices(List<List<AudioValue>> pageAudios, String locale) {
        Set<String> intersection = null;
        for (List<AudioValue> audios : pageAudios) {
            Set<String> voices = new LinkedHashSet<>();
            audios.stream().filter(audio -> locale.equals(audio.locale()))
                .map(AudioValue::voiceType).forEach(voices::add);
            if (intersection == null) {
                intersection = voices;
            } else {
                intersection.retainAll(voices);
            }
        }
        return intersection == null ? List.of() : intersection.stream().sorted().toList();
    }

    private Map<String, String> defaults(Map<String, List<String>> voices) {
        Map<String, String> result = new LinkedHashMap<>();
        voices.forEach((locale, values) -> {
            if (!values.isEmpty()) {
                result.put(locale, values.get(0));
            }
        });
        return result;
    }

    private List<LegacyFairytaleReadAdapter.Job> canonicalJobs(long versionId) {
        return jdbc.query(
            "select kind, status, error_code from content_render_jobs where version_id = ? order by kind asc",
            (resultSet, rowNum) -> new LegacyFairytaleReadAdapter.Job(
                resultSet.getString("kind"), resultSet.getString("status"), resultSet.getString("error_code")),
            versionId
        );
    }

    private List<LegacyFairytaleReadAdapter.Job> aiJobs(String status, String format, boolean hasPages) {
        if ("COMPLETED".equals(status)) {
            List<LegacyFairytaleReadAdapter.Job> jobs = new ArrayList<>();
            jobs.add(new LegacyFairytaleReadAdapter.Job("CONTENT_GENERATION", "SUCCEEDED", null));
            if ("video".equals(format)) {
                jobs.add(new LegacyFairytaleReadAdapter.Job("VIDEO_RENDER", "SUCCEEDED", null));
            }
            return jobs;
        }
        if ("video".equals(format) && "FAILED".equals(status) && hasPages) {
            return List.of(
                new LegacyFairytaleReadAdapter.Job("CONTENT_GENERATION", "SUCCEEDED", null),
                new LegacyFairytaleReadAdapter.Job("VIDEO_RENDER", "FAILED", "LEGACY_VIDEO_RENDER_FAILED")
            );
        }
        return switch (status) {
            case "PENDING" -> List.of(new LegacyFairytaleReadAdapter.Job(
                "CONTENT_GENERATION", "QUEUED", null));
            case "GENERATING" -> List.of(new LegacyFairytaleReadAdapter.Job(
                "CONTENT_GENERATION", "RUNNING", null));
            case "FAILED" -> List.of(new LegacyFairytaleReadAdapter.Job(
                "CONTENT_GENERATION", "FAILED", "LEGACY_CONTENT_GENERATION_FAILED"));
            default -> throw new LegacyImportException("UNSUPPORTED_LEGACY_STATUS", status);
        };
    }

    private String canonicalVideoIdentity(long versionId) {
        return jdbc.query(
            "select trim(a.sha256) from content_rendition_variants v "
                + "join content_renditions r on r.id = v.rendition_id "
                + "join media_assets a on a.id = v.output_asset_id "
                + "where r.version_id = ? and r.type = 'VIDEO' and v.status = 'READY' "
                + "order by v.locale asc, v.voice_type asc limit 1",
            (resultSet, rowNum) -> mediaIdentity.fromCanonicalSha(resultSet.getString(1)),
            versionId
        ).stream().findFirst().orElse(null);
    }

    private Long publishedStoryId(LegacyType type, long legacyId) {
        return jdbc.query(
            "select s.id from legacy_story_links l join stories s on s.id = l.story_id "
                + "join story_content_versions v on v.id = l.content_version_id "
                + "where l.legacy_type = ? and l.legacy_id = ? and s.visibility = 'PUBLISHED' "
                + "and s.published_version_id = l.content_version_id and v.status = 'PUBLISHED'",
            (resultSet, rowNum) -> resultSet.getLong(1),
            type.name(), legacyId
        ).stream().findFirst().orElse(null);
    }

    private CanonicalHeader canonicalHeader(ResultSet resultSet) throws SQLException {
        try {
            return new CanonicalHeader(
                resultSet.getString("legacy_type"), resultSet.getLong("legacy_id"),
                resultSet.getString("legacy_format"), resultSet.getString("legacy_status_code"),
                resultSet.getString("legacy_language"), resultSet.getLong("content_version_id"),
                resultSet.getLong("story_id"), resultSet.getObject("owner_user_id", Long.class),
                resultSet.getString("visibility"), resultSet.getString("title_ko"),
                resultSet.getString("title_ja"), resultSet.getString("description_ko"),
                resultSet.getString("description_ja"), categories(resultSet.getString("category_keys")),
                resultSet.getObject("published_version_id", Long.class), resultSet.getString("status"),
                resultSet.getTimestamp("created_at").toLocalDateTime().toString(),
                objectMapper.readValue(
                    resultSet.getString("legacy_contract_metadata"), LegacyContractMetadata.class)
            );
        } catch (RuntimeException exception) {
            throw new LegacyImportException("CANONICAL_LEGACY_METADATA_INVALID", null, exception);
        }
    }

    private List<String> categories(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (RuntimeException exception) {
            throw new LegacyImportException("CANONICAL_CATEGORY_KEYS_INVALID", json, exception);
        }
    }

    private CuratedHeader curatedHeader(ResultSet resultSet) throws SQLException {
        return new CuratedHeader(
            resultSet.getLong("id"), resultSet.getString("title"), resultSet.getString("title_ja"),
            resultSet.getString("description"), resultSet.getString("description_ja"),
            resultSet.getObject("rating", Double.class), resultSet.getString("color_hex"),
            resultSet.getString("theme_tag"), resultSet.getBoolean("character_supported"),
            resultSet.getString("author_ko"), resultSet.getString("author_ja"),
            resultSet.getString("age_range"), resultSet.getInt("duration_min"),
            resultSet.getInt("page_count"), resultSet.getString("full_content_ko"),
            resultSet.getString("full_content_ja"), resultSet.getString("content_version")
        );
    }

    private AiHeader aiHeader(ResultSet resultSet) throws SQLException {
        return new AiHeader(
            resultSet.getLong("id"), resultSet.getObject("user_id", Long.class),
            resultSet.getString("title"), resultSet.getString("voice_type"),
            resultSet.getString("language"), resultSet.getString("format"),
            resultSet.getString("status"), "Y".equals(resultSet.getString("shared")),
            resultSet.getString("video_url"),
            resultSet.getTimestamp("cre_dt").toLocalDateTime().toString()
        );
    }

    private LegacyFairytaleReadAdapter.CharacterPlacement placement(ResultSet resultSet)
        throws SQLException {
        BigDecimal x = resultSet.getBigDecimal("placement_x");
        BigDecimal y = resultSet.getBigDecimal("placement_y");
        BigDecimal width = resultSet.getBigDecimal("placement_width");
        BigDecimal height = resultSet.getBigDecimal("placement_height");
        Integer zIndex = resultSet.getObject("placement_z_index", Integer.class);
        String pose = resultSet.getString("placement_pose");
        Boolean flipX = resultSet.getObject("placement_flip_x", Boolean.class);
        if (x == null || y == null || width == null || height == null
            || zIndex == null || pose == null || flipX == null) {
            return null;
        }
        return new LegacyFairytaleReadAdapter.CharacterPlacement(
            x.doubleValue(), y.doubleValue(), width.doubleValue(), height.doubleValue(),
            zIndex, pose, flipX
        );
    }

    private String normalizedLocale(String value) {
        return "ja".equalsIgnoreCase(value) ? "ja" : "ko";
    }

    private String exactCreatedAt(String metadataCreatedAt, String storyCreatedAt) {
        return metadataCreatedAt == null || metadataCreatedAt.chars().allMatch(Character::isDigit)
            ? storyCreatedAt
            : metadataCreatedAt;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int number(Integer value) {
        return value == null ? 0 : value;
    }

    private record CuratedHeader(
        long id, String titleKo, String titleJa, String descriptionKo, String descriptionJa,
        Double rating, String colorHex, String themeTag, boolean characterSupported,
        String authorKo, String authorJa, String ageRange, int durationMin, int pageCount,
        String fullContentKo, String fullContentJa, String contentVersion
    ) {
    }

    private record AiHeader(
        long id, Long ownerId, String title, String voiceType, String language, String format,
        String status, boolean shared, String videoUrl, String createdAt
    ) {
    }

    private record CanonicalHeader(
        String legacyType, long legacyId, String legacyFormat, String legacyStatusCode,
        String legacyLanguage, long versionId, long storyId, Long ownerUserId, String visibility,
        String titleKo, String titleJa, String descriptionKo, String descriptionJa,
        List<String> categoryKeys, Long publishedVersionId, String versionStatus,
        String storyCreatedAt,
        LegacyContractMetadata metadata
    ) {
    }

    private record AudioValue(String voiceType, String locale, String url) {
    }

    private record CuratedPageBuilder(
        long id,
        int pageIndex,
        String imageUrl,
        String textKo,
        String textJa,
        LegacyFairytaleReadAdapter.CharacterPlacement placement,
        List<AudioValue> audios
    ) {
        LegacyFairytaleReadAdapter.CuratedPage snapshot(boolean characterSupported) {
            return new LegacyFairytaleReadAdapter.CuratedPage(
                pageIndex, imageUrl, new LegacyFairytaleReadAdapter.LocalizedText(textKo, textJa),
                audioMap(audios), characterSupported ? placement : null
            );
        }
    }

    private final class CanonicalSceneBuilder {
        private final String sceneKey;
        private final int orderIndex;
        private final String imageUrl;
        private final Map<String, String> text;
        private final List<AudioValue> audios;
        private LegacyFairytaleReadAdapter.CharacterPlacement placement;

        private CanonicalSceneBuilder(
            String sceneKey,
            int orderIndex,
            String imageUrl,
            Map<String, String> text,
            List<AudioValue> audios,
            LegacyFairytaleReadAdapter.CharacterPlacement placement
        ) {
            this.sceneKey = sceneKey;
            this.orderIndex = orderIndex;
            this.imageUrl = imageUrl;
            this.text = text;
            this.audios = audios;
            this.placement = placement;
        }

        Map<String, String> text() {
            return text;
        }

        List<AudioValue> audios() {
            return audios;
        }

        void placement(LegacyFairytaleReadAdapter.CharacterPlacement value) {
            placement = value;
        }

        LegacyFairytaleReadAdapter.CuratedPage curatedSnapshot(boolean characterSupported) {
            return new LegacyFairytaleReadAdapter.CuratedPage(
                orderIndex, imageUrl,
                new LegacyFairytaleReadAdapter.LocalizedText(text.get("ko"), text.get("ja")),
                audioMap(audios), characterSupported ? placement : null
            );
        }

        LegacyFairytaleReadAdapter.AiPage aiSnapshot(String locale, String voiceType) {
            String audio = audios.stream()
                .filter(value -> locale.equals(value.locale()) && voiceType.equals(value.voiceType()))
                .map(AudioValue::url)
                .findFirst().orElse(null);
            return new LegacyFairytaleReadAdapter.AiPage(
                orderIndex, text.get(locale), imageUrl, audio
            );
        }
    }

    private static Map<String, Map<String, String>> audioMap(List<AudioValue> audios) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        audios.stream()
            .sorted(Comparator.comparing(AudioValue::voiceType).thenComparing(AudioValue::locale))
            .forEach(audio -> result.computeIfAbsent(audio.voiceType(), ignored -> new LinkedHashMap<>())
                .put(audio.locale(), audio.url()));
        return result;
    }
}

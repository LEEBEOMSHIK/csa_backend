package org.example.csa_backend.fairytale;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.fairytale.dto.FairytaleGenerateResponse;
import org.example.csa_backend.fairytale.dto.MyFairytaleDto;
import org.example.csa_backend.storycontent.migration.LegacyContractMetadata;
import org.example.csa_backend.storycontent.migration.LegacyImportException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class CanonicalAiReadRepository {

    private static final String HEADER_SQL = """
        select l.legacy_id, l.legacy_format, l.legacy_status_code, l.legacy_language,
               l.content_version_id, s.id as story_id, s.owner_user_id, s.visibility,
               s.title_ko, s.created_at, v.status as version_status,
               v.legacy_contract_metadata::text as legacy_contract_metadata,
               (select count(*) from story_scenes sc where sc.version_id = v.id) as page_count,
               (select ma.public_url
                  from story_scenes sc
                  join media_assets ma on ma.id = sc.fallback_asset_id
                 where sc.version_id = v.id
                 order by sc.order_index asc, sc.scene_key asc
                 limit 1) as thumbnail_url
          from legacy_story_links l
          join stories s on s.id = l.story_id
          join story_content_versions v on v.id = l.content_version_id
         where l.legacy_type = 'AI'
           and v.status <> 'ARCHIVED'
        """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public CanonicalAiReadRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<MyFairytaleDto> getMyFairytales(Long userId) {
        return headers().stream()
            .filter(header -> Objects.equals(header.ownerId(), userId))
            .sorted((left, right) -> Long.compare(right.legacyId(), left.legacyId()))
            .map(this::summary)
            .toList();
    }

    public List<MyFairytaleDto> getSharedFairytales() {
        return headers().stream()
            .filter(Header::shared)
            .filter(header -> "COMPLETED".equals(header.status()))
            .sorted((left, right) -> Long.compare(right.legacyId(), left.legacyId()))
            .map(this::summary)
            .toList();
    }

    public FairytaleGenerateResponse getMyFairytaleSlides(Long userId, Long fairytaleId) {
        Header header = requireHeader(fairytaleId);
        if (!Objects.equals(header.ownerId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return slides(header);
    }

    public FairytaleGenerateResponse getSharedFairytaleSlides(Long fairytaleId) {
        Header header = requireHeader(fairytaleId);
        if (!header.shared()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return slides(header);
    }

    private FairytaleGenerateResponse slides(Header header) {
        Map<Long, SceneBuilder> scenes = scenes(header.versionId());
        if (!"COMPLETED".equals(header.status())
            && !("video".equals(header.format())
                && "FAILED".equals(header.status())
                && !scenes.isEmpty())) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }
        String voiceType = header.metadata().aiVoiceType();
        return new FairytaleGenerateResponse(
            header.legacyId(),
            header.title(),
            header.language(),
            voiceType,
            scenes.values().stream()
                .map(scene -> scene.toPage(header.language(), voiceType))
                .toList(),
            videoUrl(header.versionId())
        );
    }

    private MyFairytaleDto summary(Header header) {
        return new MyFairytaleDto(
            header.legacyId(),
            header.title(),
            header.format(),
            header.status(),
            header.language(),
            header.shared(),
            header.thumbnailUrl(),
            header.pageCount(),
            exactCreatedAt(header.metadata().createdAt(), header.storyCreatedAt()),
            header.ownerId()
        );
    }

    private List<Header> headers() {
        return jdbc.query(HEADER_SQL + " order by l.legacy_id desc", this::header);
    }

    private Header requireHeader(Long fairytaleId) {
        if (fairytaleId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Optional<Header> header = jdbc.query(
            HEADER_SQL + " and l.legacy_id = ?", this::header, fairytaleId
        ).stream().findFirst();
        return header.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private Map<Long, SceneBuilder> scenes(long versionId) {
        Map<Long, SceneBuilder> scenes = new LinkedHashMap<>();
        jdbc.query(
            "select s.id, s.order_index, a.public_url as image_url "
                + "from story_scenes s join media_assets a on a.id = s.fallback_asset_id "
                + "where s.version_id = ? order by s.order_index asc, s.scene_key asc",
            (RowCallbackHandler) resultSet -> scenes.put(
                resultSet.getLong("id"),
                new SceneBuilder(
                    resultSet.getInt("order_index"),
                    resultSet.getString("image_url"),
                    new LinkedHashMap<>(),
                    new ArrayList<>()
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
                .texts().put(resultSet.getString("locale"), resultSet.getString("display_text")),
            versionId
        );
        jdbc.query(
            "select c.scene_id, a.locale, a.voice_type, m.public_url as audio_url "
                + "from audio_variants a join scene_audio_cues c on c.id = a.audio_cue_id "
                + "join story_scenes s on s.id = c.scene_id "
                + "join media_assets m on m.id = a.asset_id "
                + "where s.version_id = ? and a.status = 'READY' "
                + "order by s.order_index asc, a.voice_type asc, a.locale asc",
            (RowCallbackHandler) resultSet -> scenes.get(resultSet.getLong("scene_id"))
                .audios().add(new AudioValue(
                    resultSet.getString("locale"),
                    resultSet.getString("voice_type"),
                    resultSet.getString("audio_url")
                )),
            versionId
        );
        return scenes;
    }

    private String videoUrl(long versionId) {
        return jdbc.query(
            "select a.public_url from content_rendition_variants v "
                + "join content_renditions r on r.id = v.rendition_id "
                + "join media_assets a on a.id = v.output_asset_id "
                + "where r.version_id = ? and r.type = 'VIDEO' and v.status = 'READY' "
                + "order by v.locale asc, v.voice_type asc limit 1",
            (resultSet, rowNum) -> resultSet.getString(1),
            versionId
        ).stream().findFirst().orElse(null);
    }

    private Header header(ResultSet resultSet, int rowNum) throws SQLException {
        try {
            LegacyContractMetadata metadata = objectMapper.readValue(
                resultSet.getString("legacy_contract_metadata"), LegacyContractMetadata.class);
            boolean shared = metadata.aiShared() != null
                ? metadata.aiShared()
                : "SHARED".equals(resultSet.getString("visibility"));
            return new Header(
                resultSet.getLong("legacy_id"),
                resultSet.getString("legacy_format"),
                resultSet.getString("legacy_status_code"),
                resultSet.getString("legacy_language"),
                resultSet.getLong("content_version_id"),
                resultSet.getObject("owner_user_id", Long.class),
                resultSet.getString("title_ko"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getInt("page_count"),
                resultSet.getString("thumbnail_url"),
                shared,
                metadata
            );
        } catch (RuntimeException exception) {
            throw new LegacyImportException("CANONICAL_LEGACY_METADATA_INVALID", null, exception);
        }
    }

    private LocalDateTime exactCreatedAt(String metadataCreatedAt, LocalDateTime storyCreatedAt) {
        return metadataCreatedAt == null || metadataCreatedAt.chars().allMatch(Character::isDigit)
            ? storyCreatedAt
            : LocalDateTime.parse(metadataCreatedAt);
    }

    private record Header(
        long legacyId,
        String format,
        String status,
        String language,
        long versionId,
        Long ownerId,
        String title,
        LocalDateTime storyCreatedAt,
        int pageCount,
        String thumbnailUrl,
        boolean shared,
        LegacyContractMetadata metadata
    ) {
    }

    private record AudioValue(String locale, String voiceType, String url) {
    }

    private record SceneBuilder(
        int pageIndex,
        String imageUrl,
        Map<String, String> texts,
        List<AudioValue> audios
    ) {
        private FairytaleGenerateResponse.PageDto toPage(String locale, String voiceType) {
            String audioUrl = audios.stream()
                .filter(audio -> Objects.equals(locale, audio.locale()))
                .filter(audio -> voiceType == null || Objects.equals(voiceType, audio.voiceType()))
                .map(AudioValue::url)
                .findFirst()
                .orElse(null);
            return new FairytaleGenerateResponse.PageDto(
                pageIndex, texts.get(locale), imageUrl, audioUrl
            );
        }
    }
}

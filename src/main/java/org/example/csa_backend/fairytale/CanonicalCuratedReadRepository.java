package org.example.csa_backend.fairytale;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.fairytale.dto.CuratedSlidesResponse;
import org.example.csa_backend.fairytale.dto.FairytaleDetailDto;
import org.example.csa_backend.fairytale.dto.FairytaleDto;
import org.example.csa_backend.fairytale.dto.HomePageDto;
import org.example.csa_backend.storycontent.migration.LegacyContractMetadata;
import org.example.csa_backend.storycontent.migration.LegacyImportException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class CanonicalCuratedReadRepository {

    private static final String HEADER_SQL = """
        select l.legacy_id, l.content_version_id, s.id as story_id, s.visibility,
               s.title_ko, s.title_ja, s.description_ko, s.description_ja,
               s.category_keys::text as category_keys, s.published_version_id,
               v.status as version_status,
               v.legacy_contract_metadata::text as legacy_contract_metadata
          from legacy_story_links l
          join stories s on s.id = l.story_id
          join story_content_versions v on v.id = l.content_version_id
         where l.legacy_type = 'CURATED'
           and v.status <> 'ARCHIVED'
        """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public CanonicalCuratedReadRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public HomePageDto getHomePage(String categoryKey) {
        String key = normalizeCategory(categoryKey);
        List<Header> headers = matchingHeaders(key);
        return new HomePageDto(
            headers.stream().filter(header -> Boolean.TRUE.equals(header.metadata().curatedIsTheme()))
                .map(this::summary).toList(),
            headers.stream().filter(header -> Boolean.TRUE.equals(header.metadata().curatedIsNew()))
                .map(this::summary).toList(),
            headers.stream().filter(header -> Boolean.TRUE.equals(header.metadata().curatedIsRecommended()))
                .map(this::summary).toList()
        );
    }

    public List<FairytaleDto> getFairytales(String categoryKey, String sort) {
        List<Header> headers = new ArrayList<>(matchingHeaders(normalizeCategory(categoryKey)));
        headers.sort(summaryOrder(sort));
        return headers.stream().map(this::summary).toList();
    }

    public FairytaleDetailDto getFairytaleDetail(Long fairytaleId) {
        Header header = findHeader(fairytaleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Detail not found"));
        LegacyContractMetadata metadata = header.metadata();
        return new FairytaleDetailDto(
            metadata.curatedAuthorKo(),
            metadata.curatedAuthorJa(),
            metadata.curatedAgeRange(),
            number(metadata.curatedDurationMin()),
            number(metadata.curatedPageCount()),
            metadata.curatedFullContentKo(),
            metadata.curatedFullContentJa(),
            metadata.curatedCharacterSupportedOrDefault(),
            "LOCAL_OVERLAY",
            metadata.curatedContentVersion()
        );
    }

    public CuratedSlidesResponse getCuratedSlides(Long fairytaleId) {
        Header header = findHeader(fairytaleId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        String contentVersion = header.metadata().curatedContentVersion();
        if (contentVersion == null || contentVersion.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Map<Long, SceneBuilder> scenes = scenes(header.versionId());
        if (scenes.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        boolean characterSupported = header.metadata().curatedCharacterSupportedOrDefault();
        return new CuratedSlidesResponse(
            header.legacyId(),
            contentVersion,
            characterSupported,
            "LOCAL_OVERLAY",
            scenes.values().stream().map(scene -> scene.toPage(characterSupported)).toList()
        );
    }

    private List<Header> matchingHeaders(String categoryKey) {
        return jdbc.query(HEADER_SQL + " order by l.legacy_id asc", this::header).stream()
            .filter(header -> categoryKey == null || header.categories().contains(categoryKey))
            .toList();
    }

    private java.util.Optional<Header> findHeader(Long fairytaleId) {
        if (fairytaleId == null) {
            return java.util.Optional.empty();
        }
        return jdbc.query(HEADER_SQL + " and l.legacy_id = ?", this::header, fairytaleId)
            .stream().findFirst();
    }

    private FairytaleDto summary(Header header) {
        LegacyContractMetadata metadata = header.metadata();
        Long canonicalStoryId = "PUBLISHED".equals(header.visibility())
            && "PUBLISHED".equals(header.versionStatus())
            && Objects.equals(header.publishedVersionId(), header.versionId())
            ? header.storyId()
            : null;
        return new FairytaleDto(
            header.legacyId(),
            header.titleKo(),
            header.titleJa(),
            header.descriptionKo(),
            header.descriptionJa(),
            metadata.curatedRating(),
            metadata.curatedColorHex(),
            metadata.curatedThemeTag(),
            header.categories(),
            metadata.curatedCharacterSupportedOrDefault(),
            canonicalStoryId
        );
    }

    private Comparator<Header> summaryOrder(String sort) {
        return switch (sort == null ? "latest" : sort) {
            case "rating" -> Comparator
                .comparing(
                    (Header header) -> header.metadata().curatedRating(),
                    Comparator.nullsFirst(Comparator.reverseOrder())
                )
                .thenComparingLong(Header::legacyId);
            case "title" -> Comparator.comparing(Header::titleKo)
                .thenComparingLong(Header::legacyId);
            default -> Comparator.comparingLong(Header::legacyId).reversed();
        };
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
                    new LinkedHashMap<>(),
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
                .texts().put(resultSet.getString("locale"), resultSet.getString("display_text")),
            versionId
        );
        jdbc.query(
            "select c.scene_id, a.voice_type, a.locale, m.public_url as audio_url "
                + "from audio_variants a join scene_audio_cues c on c.id = a.audio_cue_id "
                + "join story_scenes s on s.id = c.scene_id "
                + "join media_assets m on m.id = a.asset_id "
                + "where s.version_id = ? and a.status = 'READY' "
                + "order by s.order_index asc, a.voice_type asc, a.locale asc",
            (RowCallbackHandler) resultSet -> scenes.get(resultSet.getLong("scene_id"))
                .audios()
                .computeIfAbsent(resultSet.getString("voice_type"), ignored -> new LinkedHashMap<>())
                .put(resultSet.getString("locale"), resultSet.getString("audio_url")),
            versionId
        );
        jdbc.query(
            "select l.scene_id, l.x, l.y, l.scale_x, l.scale_y, l.z_index, "
                + "l.properties_json->>'pose' as pose, "
                + "coalesce((l.properties_json->>'flipX')::boolean, false) as flip_x "
                + "from story_layers l join story_scenes s on s.id = l.scene_id "
                + "where s.version_id = ? and l.type = 'CHARACTER_SLOT' "
                + "order by s.order_index asc, l.id asc",
            (RowCallbackHandler) resultSet -> {
                String pose = resultSet.getString("pose");
                if (pose != null) {
                    scenes.get(resultSet.getLong("scene_id")).placement(
                        new CuratedSlidesResponse.CharacterPlacement(
                            resultSet.getBigDecimal("x").doubleValue(),
                            resultSet.getBigDecimal("y").doubleValue(),
                            resultSet.getBigDecimal("scale_x").doubleValue(),
                            resultSet.getBigDecimal("scale_y").doubleValue(),
                            resultSet.getInt("z_index"),
                            pose,
                            resultSet.getBoolean("flip_x")
                        )
                    );
                }
            },
            versionId
        );
        return scenes;
    }

    private Header header(ResultSet resultSet, int rowNum) throws SQLException {
        try {
            return new Header(
                resultSet.getLong("legacy_id"),
                resultSet.getLong("content_version_id"),
                resultSet.getLong("story_id"),
                resultSet.getString("visibility"),
                resultSet.getString("title_ko"),
                resultSet.getString("title_ja"),
                resultSet.getString("description_ko"),
                resultSet.getString("description_ja"),
                categories(resultSet.getString("category_keys")),
                resultSet.getObject("published_version_id", Long.class),
                resultSet.getString("version_status"),
                objectMapper.readValue(
                    resultSet.getString("legacy_contract_metadata"),
                    LegacyContractMetadata.class
                )
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

    private String normalizeCategory(String categoryKey) {
        return categoryKey == null || categoryKey.isBlank() ? null : categoryKey;
    }

    private int number(Integer value) {
        return value == null ? 0 : value;
    }

    private record Header(
        long legacyId,
        long versionId,
        long storyId,
        String visibility,
        String titleKo,
        String titleJa,
        String descriptionKo,
        String descriptionJa,
        List<String> categories,
        Long publishedVersionId,
        String versionStatus,
        LegacyContractMetadata metadata
    ) {
    }

    private static final class SceneBuilder {
        private final int pageIndex;
        private final String imageUrl;
        private final Map<String, String> texts;
        private final Map<String, Map<String, String>> audios;
        private CuratedSlidesResponse.CharacterPlacement placement;

        private SceneBuilder(
            int pageIndex,
            String imageUrl,
            Map<String, String> texts,
            Map<String, Map<String, String>> audios,
            CuratedSlidesResponse.CharacterPlacement placement
        ) {
            this.pageIndex = pageIndex;
            this.imageUrl = imageUrl;
            this.texts = texts;
            this.audios = audios;
            this.placement = placement;
        }

        private Map<String, String> texts() {
            return texts;
        }

        private Map<String, Map<String, String>> audios() {
            return audios;
        }

        private void placement(CuratedSlidesResponse.CharacterPlacement value) {
            placement = value;
        }

        private CuratedSlidesResponse.Page toPage(boolean characterSupported) {
            return new CuratedSlidesResponse.Page(
                pageIndex,
                imageUrl,
                new CuratedSlidesResponse.LocalizedText(texts.get("ko"), texts.get("ja")),
                audios,
                characterSupported ? placement : null
            );
        }
    }
}

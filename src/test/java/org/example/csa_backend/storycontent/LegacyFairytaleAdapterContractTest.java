package org.example.csa_backend.storycontent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.example.csa_backend.fairytale.FairytaleService;
import org.example.csa_backend.fairytale.dto.CuratedSlidesResponse;
import org.example.csa_backend.fairytale.dto.FairytaleDetailDto;
import org.example.csa_backend.fairytale.dto.FairytaleDto;
import org.example.csa_backend.fairytale.dto.FairytaleGenerateResponse;
import org.example.csa_backend.fairytale.dto.MyFairytaleDto;
import org.example.csa_backend.fairytale.service.AiFairytaleService;
import org.example.csa_backend.storycontent.migration.LegacyStoryImportService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Tag("postgres")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
class LegacyFairytaleAdapterContractTest {

    private static final long CURATED_ID = 780L;
    private static final long AI_ID = 781L;
    private static final long AI_OWNER_ID = 10_781L;
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
    }

    @Autowired
    private LegacyStoryImportService importer;

    @Autowired
    private FairytaleService fairytaleService;

    @Autowired
    private AiFairytaleService aiFairytaleService;

    @Autowired
    private LegacyCuratedReadAdapter curatedAdapter;

    @Autowired
    private LegacyAiReadAdapter aiAdapter;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void actualAiListCreatedAtJsonMatchesCanonicalAdapterAfterUrlShaNormalization() throws IOException {
        insertAiWithMicrosecondCreatedAt();
        writeAiMedia();
        importer.importAiBatch(AI_ID - 1, 1);

        MyFairytaleDto actualList = aiFairytaleService.getMyFairytales(AI_OWNER_ID).get(0);
        FairytaleGenerateResponse actualSlides = aiFairytaleService
            .getMyFairytaleSlides(AI_OWNER_ID, AI_ID);
        LegacyFairytaleReadAdapter.Snapshot canonical = (LegacyFairytaleReadAdapter.Snapshot)
            aiAdapter.readCanonical(AI_ID);

        JsonNode actualListJson = normalizeActualUrls(
            actualList,
            Map.of("/files/generated-fairytales/" + AI_ID + "/page_0.png", sha("ai-image-781"))
        );
        JsonNode actualSlidesJson = normalizeActualUrls(
            actualSlides,
            Map.of(
                "/files/generated-fairytales/" + AI_ID + "/page_0.png", sha("ai-image-781"),
                "/files/generated-fairytales/" + AI_ID + "/page_0_dad_ko.mp3", sha("ai-audio-781")
            )
        );

        assertThat(actualListJson.path("createdAt").asText())
            .isEqualTo("2024-02-03T04:05:06.123456");
        JsonNode canonicalListJson = objectMapper.valueToTree(canonical.aiList());
        JsonNode canonicalSlidesJson = objectMapper.valueToTree(canonical.aiSlides());
        assertThat(canonicalListJson).isEqualTo(actualListJson);
        assertThat(canonicalSlidesJson).isEqualTo(actualSlidesJson);
    }

    @Test
    void actualCuratedListRetainsSoftDeletedCategoryInCanonicalAdapter() throws IOException {
        insertCuratedWithSoftDeletedCategory();
        writeCuratedMedia();
        importer.importCuratedBatch(CURATED_ID - 1, 1);

        FairytaleDto actualList = fairytaleService.getFairytales(null, "latest").stream()
            .filter(item -> item.id().equals(CURATED_ID))
            .findFirst()
            .orElseThrow();
        FairytaleDetailDto actualDetail = fairytaleService.getFairytaleDetail(CURATED_ID);
        CuratedSlidesResponse actualSlides = fairytaleService.getCuratedSlides(CURATED_ID);
        LegacyFairytaleReadAdapter.Snapshot canonical = (LegacyFairytaleReadAdapter.Snapshot)
            curatedAdapter.readCanonical(CURATED_ID);

        JsonNode actualSlidesJson = normalizeActualUrls(
            actualSlides,
            Map.of(
                "/uploads/legacy/curated/780/page-0.png", sha("image-780"),
                "/uploads/legacy/curated/780/page-0-dad-ko.mp3", sha("audio-ko-780"),
                "/uploads/legacy/curated/780/page-0-dad-ja.mp3", sha("audio-ja-780")
            )
        );

        assertThat(actualList.categories()).containsExactly("retired-780");
        JsonNode canonicalListJson = objectMapper.valueToTree(canonical.curatedList());
        JsonNode canonicalDetailJson = objectMapper.valueToTree(canonical.curatedDetail());
        JsonNode canonicalSlidesJson = objectMapper.valueToTree(canonical.curatedSlides());
        JsonNode actualListJson = objectMapper.valueToTree(actualList);
        JsonNode actualDetailJson = objectMapper.valueToTree(actualDetail);
        assertThat(canonicalListJson).isEqualTo(actualListJson);
        assertThat(canonicalDetailJson).isEqualTo(actualDetailJson);
        assertThat(canonicalSlidesJson).isEqualTo(actualSlidesJson);
    }

    private void insertAiWithMicrosecondCreatedAt() {
        jdbc.update(
            "insert into front.users (id, email, password, created_at) values (?, ?, 'password', now())",
            AI_OWNER_ID,
            "adapter-ai-781@example.test"
        );
        jdbc.update(
            "insert into front.ai_fairytales "
                + "(id, user_id, title, settings, genre, theme, chapter_count, voice_type, language, "
                + "format, status, shared, cre_dt, cre_id, del_yn) values "
                + "(?, ?, 'AI 781', 'forest', 'adventure', 'courage', 1, 'dad', 'ko', "
                + "'slide', 'COMPLETED', 'N', ?, 'test', 'N')",
            AI_ID,
            AI_OWNER_ID,
            LocalDateTime.parse("2024-02-03T04:05:06.123456")
        );
        jdbc.update(
            "insert into front.ai_fairytale_pages "
                + "(ai_fairytale_id, page_index, text, image_url, audio_url, cre_dt, cre_id, del_yn) "
                + "values (?, 0, 'AI page 781', ?, ?, now(), 'test', 'N')",
            AI_ID,
            "/files/generated-fairytales/" + AI_ID + "/page_0.png",
            "/files/generated-fairytales/" + AI_ID + "/page_0_dad_ko.mp3"
        );
    }

    private void insertCuratedWithSoftDeletedCategory() {
        jdbc.update(
            "insert into front.fairytales "
                + "(id, title, title_ja, description, description_ja, is_theme, is_new, is_recommended, "
                + "character_supported, cre_dt, cre_id, del_yn) "
                + "values (?, '동화 780', '童話 780', '설명', '説明', 'N', 'Y', 'N', false, now(), 'test', 'N')",
            CURATED_ID
        );
        jdbc.update(
            "insert into front.fairytale_details "
                + "(fairytale_id, author_ko, author_ja, age_range, duration_min, page_count, "
                + "full_content_ko, full_content_ja, content_version, cre_dt, cre_id, del_yn) "
                + "values (?, '작가', '作家', '3-5', 1, 1, '본문', '本文', 'legacy-v1', now(), 'test', 'N')",
            CURATED_ID
        );
        long pageId = jdbc.queryForObject(
            "insert into front.curated_fairytale_pages "
                + "(fairytale_id, page_index, image_url, text_ko, text_ja, content_version, "
                + "cre_dt, cre_id, del_yn) values (?, 0, ?, '첫 장면', '最初の場面', "
                + "'legacy-v1', now(), 'test', 'N') returning id",
            Long.class,
            CURATED_ID,
            "/uploads/legacy/curated/780/page-0.png"
        );
        jdbc.update(
            "insert into front.curated_fairytale_audios "
                + "(page_id, voice_type, locale, audio_url, cre_dt, cre_id, del_yn) values "
                + "(?, 'dad', 'ko', ?, now(), 'test', 'N'), "
                + "(?, 'dad', 'ja', ?, now(), 'test', 'N')",
            pageId,
            "/uploads/legacy/curated/780/page-0-dad-ko.mp3",
            pageId,
            "/uploads/legacy/curated/780/page-0-dad-ja.mp3"
        );
        long categoryId = jdbc.queryForObject(
            "insert into front.categories "
                + "(category_key, name_ko, name_ja, cre_dt, cre_id, del_yn) "
                + "values ('retired-780', '종료', '終了', now(), 'test', 'Y') returning id",
            Long.class
        );
        jdbc.update(
            "insert into front.fairytale_categories (fairytale_id, category_id) values (?, ?)",
            CURATED_ID,
            categoryId
        );
    }

    private void writeAiMedia() throws IOException {
        Path directory = MEDIA_ROOT.resolve("legacy-ai/" + AI_ID);
        Files.createDirectories(directory);
        Files.write(directory.resolve("page_0.png"), "ai-image-781".getBytes(StandardCharsets.UTF_8));
        Files.write(
            directory.resolve("page_0_dad_ko.mp3"),
            "ai-audio-781".getBytes(StandardCharsets.UTF_8)
        );
    }

    private void writeCuratedMedia() throws IOException {
        Path directory = MEDIA_ROOT.resolve("legacy/curated/780");
        Files.createDirectories(directory);
        Files.write(directory.resolve("page-0.png"), "image-780".getBytes(StandardCharsets.UTF_8));
        Files.write(
            directory.resolve("page-0-dad-ko.mp3"),
            "audio-ko-780".getBytes(StandardCharsets.UTF_8)
        );
        Files.write(
            directory.resolve("page-0-dad-ja.mp3"),
            "audio-ja-780".getBytes(StandardCharsets.UTF_8)
        );
    }

    private JsonNode normalizeActualUrls(Object value, Map<String, String> identities) {
        JsonNode result = objectMapper.valueToTree(value);
        replaceUrls(result, identities);
        return result;
    }

    private void replaceUrls(JsonNode node, Map<String, String> identities) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List.copyOf(object.properties()).forEach(entry -> {
                JsonNode value = entry.getValue();
                String identity = value.isTextual() ? identities.get(value.asText()) : null;
                if (identity != null) {
                    object.put(entry.getKey(), identity);
                } else if (entry.getKey().endsWith("Url") && value.isTextual()) {
                    assertThat(identity)
                        .as("approved URL identity for %s", value.asText())
                        .isNotNull();
                } else {
                    replaceUrls(value, identities);
                }
            });
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> replaceUrls(item, identities));
        }
    }

    private String sha(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("sha256:");
            for (byte item : digest) {
                result.append(Character.forDigit((item >>> 4) & 0x0f, 16));
                result.append(Character.forDigit(item & 0x0f, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Path createMediaRoot() {
        try {
            return Files.createTempDirectory("legacy-adapter-contract-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}

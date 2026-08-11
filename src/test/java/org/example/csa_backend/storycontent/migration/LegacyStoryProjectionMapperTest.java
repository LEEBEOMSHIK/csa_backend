package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.example.csa_backend.fairytale.AiFairytale;
import org.example.csa_backend.fairytale.AiFairytalePage;
import org.example.csa_backend.storycontent.ContentVersionStatus;
import org.example.csa_backend.storycontent.StoryVisibility;
import org.example.csa_backend.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

class LegacyStoryProjectionMapperTest {

    private final LegacyStoryProjectionMapper mapper = new LegacyStoryProjectionMapper(new ContractChecksum());

    @ParameterizedTest
    @MethodSource("legacyStates")
    void mapsLegacyStateWithoutUsingContentLifecycleForJobStatus(
        AiFairytale legacy,
        ContentVersionStatus expectedVersionStatus,
        boolean expectedPointer,
        List<String> expectedJobs,
        boolean expectedAdapterSlide
    ) {
        LegacyProjection projection = mapper.project(legacy);

        assertThat(projection.versionStatus()).isEqualTo(expectedVersionStatus);
        assertThat(projection.publishedPointer()).isEqualTo(expectedPointer);
        assertThat(projection.jobs())
            .extracting(job -> job.kind() + ":" + job.status())
            .containsExactlyElementsOf(expectedJobs);
        assertThat(!projection.scenes().isEmpty()).isEqualTo(expectedAdapterSlide);
    }

    @Test
    void ownerAndVisibilityMapIndependentlyFromOrigin() {
        AiFairytale shared = ai(21L, "COMPLETED", "slide", true, true);
        shared.updateShared(true);
        AiFairytale privateStory = ai(22L, "COMPLETED", "slide", true, true);
        privateStory.updateShared(false);

        assertThat(mapper.project(shared).visibility()).isEqualTo(StoryVisibility.SHARED);
        assertThat(mapper.project(privateStory).visibility()).isEqualTo(StoryVisibility.OWNER_PRIVATE);
    }

    @Test
    void nullOwnerAiIsQuarantinedAsArchivedMigrationException() {
        AiFairytale orphan = ai(23L, "COMPLETED", "slide", false, true);
        orphan.updateShared(true);

        LegacyProjection projection = mapper.project(orphan);

        assertThat(projection.visibility()).isEqualTo(StoryVisibility.ARCHIVED);
        assertThat(projection.migrationExceptionCode()).isEqualTo("AI_OWNER_REQUIRED");
        assertThat(projection.publishedPointer()).isFalse();
    }

    @Test
    void aiSceneAudioUsesTheLegacyVoiceSelectedForItsLocale() {
        LegacyProjection projection = mapper.project(ai(24L, "COMPLETED", "slide", true, true));

        assertThat(projection.availableVoiceTypes()).containsEntry("ko", List.of("dad"));
        assertThat(projection.defaultVoiceTypes()).containsEntry("ko", "dad");
        assertThat(projection.scenes()).singleElement().satisfies(scene ->
            assertThat(scene.audios()).singleElement()
                .extracting(LegacyProjection.AudioProjection::voiceType)
                .isEqualTo("dad")
        );
    }

    @Test
    void curatedProjectionUsesCanonicalLocaleAndVoiceIterationOrder() {
        LegacyProjection projection = mapper.projectCurated(
            new LegacyStoryProjectionMapper.CuratedSource(
                25L,
                "제목",
                "題名",
                "설명",
                "説明",
                1,
                "legacy-v1",
                List.of("z", "a"),
                List.of(new LegacyStoryProjectionMapper.CuratedPageSource(
                    251L,
                    0,
                    "/uploads/25/page.png",
                    "본문",
                    "本文",
                    null,
                    List.of(
                        new LegacyStoryProjectionMapper.CuratedAudioSource("mom", "ja", "/uploads/25/ja.mp3"),
                        new LegacyStoryProjectionMapper.CuratedAudioSource("dad", "ko", "/uploads/25/ko-dad.mp3"),
                        new LegacyStoryProjectionMapper.CuratedAudioSource("mom", "ko", "/uploads/25/ko-mom.mp3")
                    )
                ))
            )
        );

        assertThat(projection.categoryKeys()).containsExactly("a", "z");
        assertThat(projection.availableVoiceTypes().keySet()).containsExactly("ko", "ja");
        assertThat(projection.availableVoiceTypes().get("ko")).containsExactly("dad", "mom");
        assertThat(projection.defaultVoiceTypes().keySet()).containsExactly("ko", "ja");
        assertThat(projection.scenes().get(0).text().keySet()).containsExactly("ko", "ja");

        Map<String, String> insertionOrdered = new LinkedHashMap<>();
        insertionOrdered.put("ko", "dad");
        insertionOrdered.put("ja", "mom");
        LegacyProjection.SceneProjection scene = new LegacyProjection.SceneProjection(
            "page-0", 0, 1_000, 1, 1, insertionOrdered, "/uploads/25/page.png", List.of(), null);
        assertThat(scene.text().keySet()).containsExactly("ko", "ja");
    }

    @Test
    void pageLessCuratedStoryProjectsAsArchivedRepairableDraft() {
        LegacyProjection projection = mapper.projectCurated(
            new LegacyStoryProjectionMapper.CuratedSource(
                26L,
                "제목",
                "題名",
                "설명",
                "説明",
                1,
                "legacy-v1",
                List.of("adventure"),
                List.of()
            )
        );

        assertThat(projection.visibility()).isEqualTo(StoryVisibility.ARCHIVED);
        assertThat(projection.versionStatus()).isEqualTo(ContentVersionStatus.DRAFT);
        assertThat(projection.publishedPointer()).isFalse();
        assertThat(projection.migrationExceptionCode()).isEqualTo("CURATED_PAGES_REQUIRED");
        assertThat(projection.scenes()).isEmpty();
        assertThat(projection.availableVoiceTypes()).isEmpty();
        assertThat(projection.defaultVoiceTypes()).isEmpty();
        assertThat(projection.sourceHash()).hasSize(64);
    }

    private static Stream<Arguments> legacyStates() {
        return Stream.of(
            Arguments.of(
                ai(11L, "COMPLETED", "slide", true, true),
                ContentVersionStatus.PUBLISHED,
                true,
                List.of("CONTENT_GENERATION:SUCCEEDED"),
                true
            ),
            Arguments.of(
                ai(12L, "COMPLETED", "video", true, true),
                ContentVersionStatus.PUBLISHED,
                true,
                List.of("CONTENT_GENERATION:SUCCEEDED", "VIDEO_RENDER:SUCCEEDED"),
                true
            ),
            Arguments.of(
                ai(13L, "PENDING", "slide", true, false),
                ContentVersionStatus.DRAFT,
                false,
                List.of("CONTENT_GENERATION:QUEUED"),
                false
            ),
            Arguments.of(
                ai(14L, "GENERATING", "slide", true, false),
                ContentVersionStatus.DRAFT,
                false,
                List.of("CONTENT_GENERATION:RUNNING"),
                false
            ),
            Arguments.of(
                ai(15L, "FAILED", "slide", true, false),
                ContentVersionStatus.DRAFT,
                false,
                List.of("CONTENT_GENERATION:FAILED"),
                false
            ),
            Arguments.of(
                ai(16L, "FAILED", "video", true, true),
                ContentVersionStatus.DRAFT,
                false,
                List.of("CONTENT_GENERATION:SUCCEEDED", "VIDEO_RENDER:FAILED"),
                true
            )
        );
    }

    private static AiFairytale ai(
        long id,
        String status,
        String format,
        boolean withOwner,
        boolean withPage
    ) {
        AiFairytale fairytale = new AiFairytale(
            "AI " + id,
            "forest,night",
            "adventure",
            "courage",
            withPage ? 1 : 0,
            "dad",
            "ko",
            format,
            status
        );
        ReflectionTestUtils.setField(fairytale, "id", id);
        if (withOwner) {
            User owner = new User("owner-" + id + "@example.test", "password");
            ReflectionTestUtils.setField(owner, "id", 1000L + id);
            fairytale.assignOwner(owner);
        }
        if (withPage) {
            fairytale.getPages().add(new AiFairytalePage(
                fairytale,
                0,
                "AI page " + id,
                "/files/generated-fairytales/" + id + "/page_0.png",
                "/files/generated-fairytales/" + id + "/page_0_dad_ko.mp3"
            ));
        }
        if ("video".equals(format) && "COMPLETED".equals(status)) {
            fairytale.updateVideoUrl("/files/generated-fairytales/" + id + "/video.mp4");
        }
        return fairytale;
    }
}

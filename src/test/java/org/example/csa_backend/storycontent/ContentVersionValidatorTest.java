package org.example.csa_backend.storycontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ContentVersionValidatorTest {

    private static final List<Class<?>> ENTITY_TYPES = List.of(
        Story.class,
        ContentVersion.class,
        VersionLocale.class,
        Scene.class,
        SceneLocalizedContent.class,
        Layer.class,
        Asset.class,
        AudioCue.class,
        AudioVariant.class,
        Rendition.class,
        RenditionVariant.class,
        RenderJob.class,
        LegacyStoryLink.class,
        AssetUploadSession.class,
        ReviewRecord.class,
        PublishEvent.class,
        OutboxEvent.class,
        LegacyMigrationWatermark.class,
        LegacyShadowMismatch.class,
        MigrationReconciliation.class,
        ContentMigrationControl.class
    );

    private final ContentVersionValidator validator = new ContentVersionValidator();

    @Test
    void allPersistenceFieldsArePrivate() {
        assertThat(ENTITY_TYPES)
            .as("all backend story-content JPA entities")
            .hasSize(21)
            .contains(LegacyShadowMismatch.class, MigrationReconciliation.class);
        assertThat(ENTITY_TYPES.stream()
            .flatMap(type -> Stream.of(type.getDeclaredFields()))
            .filter(field -> field.isAnnotationPresent(Column.class) || field.isAnnotationPresent(Id.class)))
            .allMatch(field -> Modifier.isPrivate(field.getModifiers()));
    }

    @Test
    void rejectsVideoReadyVersionWithoutCompatibilitySlide() {
        ContentVersionAggregate aggregate = aggregate(
            ContentVersionStatus.APPROVED,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(TestAggregateFactory.rendition(20L, RenditionType.VIDEO, RenditionStatus.READY, false, null)),
            List.of()
        );

        assertThat(validator.validateForPublish(aggregate).errors())
            .extracting(ValidationError::code)
            .contains("COMPATIBILITY_SLIDE_REQUIRED");
    }

    @Test
    void exposesOnlyVoicesReadyForEveryRequiredNarrationCue() {
        List<AudioCue> cues = List.of(
            TestAggregateFactory.narrationCue(1L, "scene-one", true),
            TestAggregateFactory.narrationCue(2L, "scene-two", true)
        );
        List<AudioVariant> variants = List.of(
            TestAggregateFactory.audioVariant(1L, "ko", "mom", 100L, AudioVariantStatus.READY),
            TestAggregateFactory.audioVariant(1L, "ko", "dad", 101L, AudioVariantStatus.READY),
            TestAggregateFactory.audioVariant(2L, "ko", "dad", 102L, AudioVariantStatus.READY)
        );

        Set<String> voices = validator.availableVoiceTypes(aggregate(
            ContentVersionStatus.APPROVED,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            cues,
            variants,
            List.of(TestAggregateFactory.locale("ko", "dad")),
            List.of(TestAggregateFactory.rendition(20L, RenditionType.SLIDE, RenditionStatus.READY, true, null)),
            List.of()
        ), "ko");

        assertThat(voices).containsExactly("dad");
    }

    @Test
    void requiredNarrationWithNoCompleteVoiceBlocksPublish() {
        List<AudioCue> cues = List.of(
            TestAggregateFactory.narrationCue(1L, "scene-one", true),
            TestAggregateFactory.narrationCue(2L, "scene-two", true)
        );
        List<AudioVariant> variants = List.of(
            TestAggregateFactory.audioVariant(1L, "ko", "mom", 100L, AudioVariantStatus.READY),
            TestAggregateFactory.audioVariant(2L, "ko", "dad", 101L, AudioVariantStatus.READY)
        );
        ContentVersionAggregate aggregate = aggregate(
            ContentVersionStatus.APPROVED,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            cues,
            variants,
            List.of(TestAggregateFactory.locale("ko", null)),
            List.of(TestAggregateFactory.rendition(20L, RenditionType.SLIDE, RenditionStatus.READY, true, null)),
            List.of()
        );

        assertThat(validator.validateForPublish(aggregate).errors())
            .extracting(ValidationError::code)
            .contains("REQUIRED_NARRATION_VOICE_UNAVAILABLE");
    }

    @Test
    void assertDraftAndReviewValidationEnforceStatus() {
        ContentVersion draft = TestAggregateFactory.version(ContentVersionStatus.DRAFT, 0);
        assertThatCode(() -> validator.assertDraft(draft)).doesNotThrowAnyException();
        assertThat(validator.validateForReview(emptyAggregate(ContentVersionStatus.DRAFT)).valid()).isTrue();

        for (ContentVersionStatus status : ContentVersionStatus.values()) {
            if (status == ContentVersionStatus.DRAFT) {
                continue;
            }
            assertThatThrownBy(() -> validator.assertDraft(TestAggregateFactory.version(status, 0)))
                .isInstanceOf(IllegalStateException.class);
        }
        assertThat(validator.validateForReview(emptyAggregate(ContentVersionStatus.APPROVED)).errors())
            .extracting(ValidationError::code)
            .containsExactly("DRAFT_REQUIRED");
    }

    @Test
    void everyAggregateContentChangeRequiresDraft() {
        ContentVersionAggregate approved = emptyAggregate(ContentVersionStatus.APPROVED);

        assertThatThrownBy(() -> approved.withScene(TestAggregateFactory.scene(1L, "scene", 100L)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> approved.withLocalizedContent(TestAggregateFactory.localized(1L, "ko", "text")))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> approved.withLayer(TestAggregateFactory.layer(1L, "hero", 100L)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> approved.withAudioCue(TestAggregateFactory.narrationCue(1L, "narration", true)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> approved.withAudioVariant(
            TestAggregateFactory.audioVariant(1L, "ko", "dad", 100L, AudioVariantStatus.READY)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> approved.withRendition(
            TestAggregateFactory.rendition(1L, RenditionType.SLIDE, RenditionStatus.READY, true, 100L)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> approved.withRenditionVariant(
            TestAggregateFactory.renditionVariant(1L, 1L, "ko", "dad", 100L, RenditionStatus.READY, 0)))
            .isInstanceOf(IllegalStateException.class);

        ContentVersionAggregate draft = emptyAggregate(ContentVersionStatus.DRAFT);
        assertThat(draft.withScene(TestAggregateFactory.scene(1L, "scene", 100L)).scenesInOrder())
            .hasSize(1);
        assertThat(draft.scenesInOrder()).isEmpty();
    }

    @Test
    void publishedAndSupersededAggregatesForkToNewDraftBeforeChanges() {
        for (ContentVersionStatus status : List.of(ContentVersionStatus.PUBLISHED, ContentVersionStatus.SUPERSEDED)) {
            ContentVersionAggregate original = emptyAggregate(status);
            ContentVersionAggregate fork = original.forkAsDraft(2, 99L, Instant.parse("2026-08-09T00:00:00Z"));

            assertThat(fork.version().getStatus()).isEqualTo(ContentVersionStatus.DRAFT);
            assertThat(fork.version().getStoryId()).isEqualTo(original.version().getStoryId());
            assertThat(fork.version().getVersionNo()).isEqualTo(2);
            assertThat(fork.scenesInOrder()).isEmpty();
            assertThat(fork.withScene(TestAggregateFactory.scene(1L, "scene", 100L)).scenesInOrder())
                .hasSize(1);
            assertThat(original.version().getStatus()).isEqualTo(status);
        }

        assertThatThrownBy(() -> emptyAggregate(ContentVersionStatus.APPROVED)
            .forkAsDraft(2, 99L, Instant.now()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void publishValidationEmitsEveryErrorInDeterministicOrder() {
        ContentVersionAggregate aggregate = aggregate(
            ContentVersionStatus.DRAFT,
            List.of(TestAggregateFactory.scene(1L, "opening", 999L)),
            List.of(),
            List.of(),
            List.of(TestAggregateFactory.layer(2L, "hero", 998L)),
            List.of(TestAggregateFactory.narrationCue(3L, "narration", true)),
            List.of(),
            List.of(TestAggregateFactory.locale("ko", null)),
            List.of(TestAggregateFactory.rendition(
                4L, RenditionType.VIDEO, RenditionStatus.BUILDING, false, null)),
            List.of(TestAggregateFactory.renditionVariant(
                5L, 4L, "ko", "dad", 997L, RenditionStatus.STALE, 6))
        );

        assertThat(validator.validateForPublish(aggregate).errors())
            .extracting(ValidationError::code)
            .containsExactly(
                "APPROVED_REQUIRED",
                "FALLBACK_ASSET_NOT_READY",
                "KO_TEXT_REQUIRED",
                "JA_TEXT_REQUIRED",
                "STALE_ASSET_REFERENCE",
                "COMPATIBILITY_SLIDE_REQUIRED",
                "RENDITION_NOT_READY",
                "RENDITION_NOT_READY",
                "STALE_ASSET_REFERENCE",
                "REQUIRED_NARRATION_VOICE_UNAVAILABLE",
                "DEFAULT_VOICE_NOT_AVAILABLE"
            );
    }

    @Test
    void completeApprovedAggregatePassesPublishValidation() {
        ContentVersionAggregate aggregate = aggregate(
            ContentVersionStatus.APPROVED,
            List.of(TestAggregateFactory.scene(1L, "opening", 100L)),
            List.of(
                TestAggregateFactory.localized(1L, "ko", "한국어"),
                TestAggregateFactory.localized(1L, "ja", "日本語")
            ),
            List.of(TestAggregateFactory.asset(100L, AssetKind.IMAGE, AssetStatus.READY)),
            List.of(TestAggregateFactory.layer(2L, "hero", 100L)),
            List.of(TestAggregateFactory.narrationCue(3L, "narration", true)),
            List.of(TestAggregateFactory.audioVariant(
                3L, "ko", "dad", 100L, AudioVariantStatus.READY)),
            List.of(TestAggregateFactory.locale("ko", "dad")),
            List.of(TestAggregateFactory.rendition(
                4L, RenditionType.SLIDE, RenditionStatus.READY, true, 100L)),
            List.of(TestAggregateFactory.renditionVariant(
                5L, 4L, "ko", "dad", 100L, RenditionStatus.READY, 7))
        );

        assertThat(validator.validateForPublish(aggregate).valid()).isTrue();
    }

    private ContentVersionAggregate emptyAggregate(ContentVersionStatus status) {
        return aggregate(
            status,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    private ContentVersionAggregate aggregate(
        ContentVersionStatus status,
        List<Scene> scenes,
        List<SceneLocalizedContent> localizedContents,
        List<Asset> assets,
        List<Layer> layers,
        List<AudioCue> audioCues,
        List<AudioVariant> audioVariants,
        List<VersionLocale> locales,
        List<Rendition> renditions,
        List<RenditionVariant> renditionVariants
    ) {
        return TestAggregateFactory.aggregate(
            status,
            7,
            scenes,
            localizedContents,
            assets,
            layers,
            audioCues,
            audioVariants,
            locales,
            renditions,
            renditionVariants
        );
    }
}

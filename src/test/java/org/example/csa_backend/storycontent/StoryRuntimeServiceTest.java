package org.example.csa_backend.storycontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.csa_backend.storycontent.dto.RuntimeCapabilities;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest;
import org.example.csa_backend.storycontent.dto.StoryRuntimeManifestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoryRuntimeServiceTest {

    private static final Long STORY_ID = 7L;
    private static final Long PUBLISHED_VERSION_ID = 17L;

    @Mock
    private StoryRepository storyRepository;

    @Mock
    private PublishedRuntimeManifestLookup runtimeManifestLookup;

    @Mock
    private PublishedManifestReader manifestReader;

    @Mock
    private RuntimeManifestSelector manifestSelector;

    private StoryRuntimeService service;
    private RuntimeCapabilities capabilities;

    @BeforeEach
    void setUp() {
        service = new StoryRuntimeService(storyRepository, runtimeManifestLookup, manifestReader, manifestSelector);
        capabilities = new RuntimeCapabilities("SLIDE", "ko", "mom", List.of(1), List.of("SLIDE"), 1);
    }

    @Test
    void publishedStoryUsesOnlyTheCapturedPublishedVersion() {
        Story story = story(StoryVisibility.PUBLISHED, PUBLISHED_VERSION_ID);
        PublishedRuntimeManifestSource source = publishedSource();
        Rendition slide = source.compatibilitySlide();
        VerifiedStoredManifest verified = verifiedManifest();
        StoryRuntimeManifestResponse selected = response();
        when(storyRepository.findById(STORY_ID)).thenReturn(Optional.of(story));
        when(runtimeManifestLookup.findPublished(PUBLISHED_VERSION_ID)).thenReturn(Optional.of(source));
        when(manifestReader.readAndVerify(slide)).thenReturn(verified);
        when(manifestSelector.select(verified, capabilities)).thenReturn(selected);

        StoryRuntimeManifestResponse response = service.getPublishedRuntime(STORY_ID, capabilities);

        assertThat(response).isSameAs(selected);
        verify(runtimeManifestLookup).findPublished(PUBLISHED_VERSION_ID);
        verify(runtimeManifestLookup, never()).findPublished(16L);
        verify(manifestReader).readAndVerify(slide);
    }

    @ParameterizedTest
    @EnumSource(value = StoryVisibility.class, names = "PUBLISHED", mode = EnumSource.Mode.EXCLUDE)
    void everyNonPublicVisibilityReturns404(StoryVisibility visibility) {
        when(storyRepository.findById(STORY_ID)).thenReturn(Optional.of(story(visibility, PUBLISHED_VERSION_ID)));

        assertNotFound(() -> service.getPublishedRuntime(STORY_ID, capabilities));

        verifyNoInteractions(runtimeManifestLookup, manifestReader, manifestSelector);
    }

    @Test
    void unknownStoryReturns404() {
        when(storyRepository.findById(STORY_ID)).thenReturn(Optional.empty());

        assertNotFound(() -> service.getPublishedRuntime(STORY_ID, capabilities));
    }

    @Test
    void publishedStoryWithoutPointerReturnsStable503() {
        when(storyRepository.findById(STORY_ID)).thenReturn(Optional.of(story(StoryVisibility.PUBLISHED, null)));

        assertUnavailable(() -> service.getPublishedRuntime(STORY_ID, capabilities));
        verifyNoInteractions(runtimeManifestLookup, manifestReader, manifestSelector);
    }

    @Test
    void missingPublishedRuntimeSourceReturnsStable503() {
        when(storyRepository.findById(STORY_ID))
            .thenReturn(Optional.of(story(StoryVisibility.PUBLISHED, PUBLISHED_VERSION_ID)));
        when(runtimeManifestLookup.findPublished(PUBLISHED_VERSION_ID)).thenReturn(Optional.empty());

        assertUnavailable(() -> service.getPublishedRuntime(STORY_ID, capabilities));
        verifyNoInteractions(manifestReader, manifestSelector);
    }

    @Test
    void missingReadyCompatibilitySlideReturnsStable503() {
        when(storyRepository.findById(STORY_ID))
            .thenReturn(Optional.of(story(StoryVisibility.PUBLISHED, PUBLISHED_VERSION_ID)));
        when(runtimeManifestLookup.findPublished(PUBLISHED_VERSION_ID)).thenReturn(Optional.empty());

        assertUnavailable(() -> service.getPublishedRuntime(STORY_ID, capabilities));
        verifyNoInteractions(manifestReader, manifestSelector);
    }

    @Test
    void brokenCurrentManifestReturns503WithoutLookingUpAnOlderVersion() {
        PublishedRuntimeManifestSource source = publishedSource();
        Rendition slide = source.compatibilitySlide();
        when(storyRepository.findById(STORY_ID))
            .thenReturn(Optional.of(story(StoryVisibility.PUBLISHED, PUBLISHED_VERSION_ID)));
        when(runtimeManifestLookup.findPublished(PUBLISHED_VERSION_ID)).thenReturn(Optional.of(source));
        when(manifestReader.readAndVerify(slide))
            .thenThrow(StoryRuntimeException.unavailable("PUBLISHED_MANIFEST_UNAVAILABLE"));

        assertUnavailable(() -> service.getPublishedRuntime(STORY_ID, capabilities));
        verify(runtimeManifestLookup).findPublished(PUBLISHED_VERSION_ID);
        verify(runtimeManifestLookup, never()).findPublished(16L);
        verifyNoInteractions(manifestSelector);
    }

    @Test
    void manifestForAnotherStoryReturns503BeforeSelection() {
        PublishedRuntimeManifestSource source = publishedSource();
        Rendition slide = source.compatibilitySlide();
        VerifiedStoredManifest wrongStory = verifiedManifest(999L);
        when(storyRepository.findById(STORY_ID))
            .thenReturn(Optional.of(story(StoryVisibility.PUBLISHED, PUBLISHED_VERSION_ID)));
        when(runtimeManifestLookup.findPublished(PUBLISHED_VERSION_ID)).thenReturn(Optional.of(source));
        when(manifestReader.readAndVerify(slide)).thenReturn(wrongStory);

        assertUnavailable(() -> service.getPublishedRuntime(STORY_ID, capabilities));
        verifyNoInteractions(manifestSelector);
    }

    @Test
    void unexpectedSelectorFailureReturnsStable503() {
        PublishedRuntimeManifestSource source = publishedSource();
        Rendition slide = source.compatibilitySlide();
        VerifiedStoredManifest verified = verifiedManifest();
        when(storyRepository.findById(STORY_ID))
            .thenReturn(Optional.of(story(StoryVisibility.PUBLISHED, PUBLISHED_VERSION_ID)));
        when(runtimeManifestLookup.findPublished(PUBLISHED_VERSION_ID)).thenReturn(Optional.of(source));
        when(manifestReader.readAndVerify(slide)).thenReturn(verified);
        when(manifestSelector.select(verified, capabilities)).thenThrow(new NullPointerException("corrupt graph"));

        assertUnavailable(() -> service.getPublishedRuntime(STORY_ID, capabilities));
    }

    private Story story(StoryVisibility visibility, Long publishedVersionId) {
        Story story = new Story();
        set(story, "id", STORY_ID);
        set(story, "visibility", visibility);
        set(story, "publishedVersionId", publishedVersionId);
        return story;
    }

    private PublishedRuntimeManifestSource publishedSource() {
        return new PublishedRuntimeManifestSource(
            PUBLISHED_VERSION_ID, STORY_ID, rendition(RenditionType.SLIDE, RenditionStatus.READY, true)
        );
    }

    private Rendition rendition(RenditionType type, RenditionStatus status, boolean compatibilityFallback) {
        Rendition rendition = new Rendition();
        set(rendition, "id", type == RenditionType.SLIDE ? 71L : 72L);
        set(rendition, "versionId", PUBLISHED_VERSION_ID);
        set(rendition, "type", type);
        set(rendition, "status", status);
        set(rendition, "manifestAssetId", 501L);
        set(rendition, "rendererVersion", 1);
        set(rendition, "checksum", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        set(rendition, "compatibilityFallback", compatibilityFallback);
        return rendition;
    }

    private VerifiedStoredManifest verifiedManifest() {
        return verifiedManifest(STORY_ID);
    }

    private VerifiedStoredManifest verifiedManifest(Long manifestStoryId) {
        StoredRuntimeManifest manifest = new StoredRuntimeManifest(
            1, manifestStoryId, PUBLISHED_VERSION_ID, "3", "CURATED", "SLIDE", "SLIDE", "1",
            List.of("ko"), "ko", "mom", Map.of("ko", List.of("mom")), Map.of("ko", "mom"),
            List.of(), List.of(), List.of(), List.of(), null
        );
        return new VerifiedStoredManifest(
            manifest, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );
    }

    private StoryRuntimeManifestResponse response() {
        return StoryRuntimeManifestResponse.flat(
            verifiedManifest().manifest(), verifiedManifest().storedBytesSha256()
        );
    }

    private void assertNotFound(ThrowingCall call) {
        assertThatThrownBy(call::run)
            .isInstanceOfSatisfying(StoryRuntimeException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(exception.getCode()).isEqualTo("STORY_NOT_FOUND");
            });
    }

    private void assertUnavailable(ThrowingCall call) {
        assertThatThrownBy(call::run)
            .isInstanceOfSatisfying(StoryRuntimeException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(exception.getCode()).isEqualTo("PUBLISHED_MANIFEST_UNAVAILABLE");
            });
    }

    private void set(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}

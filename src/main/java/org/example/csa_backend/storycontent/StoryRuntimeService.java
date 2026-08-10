package org.example.csa_backend.storycontent;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.storycontent.dto.RuntimeCapabilities;
import org.example.csa_backend.storycontent.dto.StoryRuntimeManifestResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoryRuntimeService {

    private static final String NOT_FOUND = "STORY_NOT_FOUND";
    private static final String UNAVAILABLE = "PUBLISHED_MANIFEST_UNAVAILABLE";

    private final StoryRepository storyRepository;
    private final PublishedRuntimeManifestLookup runtimeManifestLookup;
    private final PublishedManifestReader manifestReader;
    private final RuntimeManifestSelector manifestSelector;

    @Transactional(readOnly = true)
    public StoryRuntimeManifestResponse getPublishedRuntime(
        Long storyId,
        RuntimeCapabilities capabilities
    ) {
        try {
            Story story = storyRepository.findById(storyId)
                .filter(candidate -> candidate.getVisibility() == StoryVisibility.PUBLISHED)
                .orElseThrow(() -> StoryRuntimeException.notFound(NOT_FOUND));
            Long publishedVersionId = story.getPublishedVersionId();
            if (publishedVersionId == null) {
                throw StoryRuntimeException.unavailable(UNAVAILABLE);
            }

            PublishedRuntimeManifestSource source = runtimeManifestLookup.findPublished(publishedVersionId)
                .filter(candidate -> candidate.storyId() == storyId
                    && candidate.contentVersionId() == publishedVersionId)
                .orElseThrow(() -> StoryRuntimeException.unavailable(UNAVAILABLE));
            Rendition compatibilitySlide = source.compatibilitySlide();

            VerifiedStoredManifest verified = manifestReader.readAndVerify(compatibilitySlide);
            if (verified == null || verified.manifest() == null
                || verified.manifest().storyId() != storyId
                || verified.manifest().contentVersionId() != publishedVersionId) {
                throw StoryRuntimeException.unavailable(UNAVAILABLE);
            }
            return manifestSelector.select(verified, capabilities);
        } catch (StoryRuntimeException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw StoryRuntimeException.unavailable(UNAVAILABLE, exception);
        }
    }

}

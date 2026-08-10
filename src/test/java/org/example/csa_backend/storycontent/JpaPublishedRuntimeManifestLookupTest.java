package org.example.csa_backend.storycontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JpaPublishedRuntimeManifestLookupTest {

    @Mock
    private ContentVersionRepository contentVersionRepository;

    @Mock
    private RenditionRepository renditionRepository;

    private JpaPublishedRuntimeManifestLookup lookup;

    @BeforeEach
    void setUp() {
        lookup = new JpaPublishedRuntimeManifestLookup(contentVersionRepository, renditionRepository);
    }

    @Test
    void returnsOnlyThePublishedVersionIdentityAndReadyCompatibilitySlide() {
        ContentVersion version = new ContentVersion();
        set(version, "id", 17L);
        set(version, "storyId", 7L);
        set(version, "status", ContentVersionStatus.PUBLISHED);
        Rendition slide = new Rendition();
        set(slide, "id", 71L);
        set(slide, "versionId", 17L);
        set(slide, "type", RenditionType.SLIDE);
        set(slide, "status", RenditionStatus.READY);
        set(slide, "compatibilityFallback", true);
        set(slide, "manifestAssetId", 501L);
        when(contentVersionRepository.findById(17L)).thenReturn(Optional.of(version));
        when(renditionRepository.findByVersionIdAndTypeAndStatusAndCompatibilityFallbackTrue(
            17L, RenditionType.SLIDE, RenditionStatus.READY
        )).thenReturn(Optional.of(slide));

        PublishedRuntimeManifestSource source = lookup.findPublished(17L).orElseThrow();

        assertThat(source.contentVersionId()).isEqualTo(17L);
        assertThat(source.storyId()).isEqualTo(7L);
        assertThat(source.compatibilitySlide()).isSameAs(slide);
        assertThat(source.compatibilitySlide().getManifestAssetId()).isEqualTo(501L);
    }

    private void set(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }
}

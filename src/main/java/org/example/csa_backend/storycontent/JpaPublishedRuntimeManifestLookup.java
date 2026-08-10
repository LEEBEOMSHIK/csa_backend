package org.example.csa_backend.storycontent;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaPublishedRuntimeManifestLookup implements PublishedRuntimeManifestLookup {

    private final ContentVersionRepository contentVersionRepository;
    private final RenditionRepository renditionRepository;

    @Override
    public Optional<PublishedRuntimeManifestSource> findPublished(Long versionId) {
        return contentVersionRepository.findById(versionId)
            .filter(version -> version.getStatus() == ContentVersionStatus.PUBLISHED)
            .flatMap(version -> renditionRepository
                .findByVersionIdAndTypeAndStatusAndCompatibilityFallbackTrue(
                    versionId, RenditionType.SLIDE, RenditionStatus.READY
                )
                .map(slide -> new PublishedRuntimeManifestSource(
                    version.getId(), version.getStoryId(), slide
                )));
    }
}

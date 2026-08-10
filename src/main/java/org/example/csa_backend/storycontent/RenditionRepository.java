package org.example.csa_backend.storycontent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RenditionRepository extends JpaRepository<Rendition, Long> {

    Optional<Rendition> findByVersionIdAndType(Long versionId, RenditionType type);

    Optional<Rendition> findByVersionIdAndTypeAndStatusAndCompatibilityFallbackTrue(
        Long versionId,
        RenditionType type,
        RenditionStatus status
    );

    List<Rendition> findByVersionIdOrderByIdAsc(Long versionId);
}

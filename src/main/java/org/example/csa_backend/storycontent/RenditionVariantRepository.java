package org.example.csa_backend.storycontent;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RenditionVariantRepository extends JpaRepository<RenditionVariant, Long> {

    Optional<RenditionVariant> findByRenditionIdAndLocaleAndVoiceType(
        Long renditionId,
        String locale,
        String voiceType
    );

    List<RenditionVariant> findByRenditionIdInOrderByRenditionIdAscIdAsc(
        Collection<Long> renditionIds
    );
}

package org.example.csa_backend.storycontent;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegacyShadowMismatchRepository extends JpaRepository<LegacyShadowMismatch, Long> {

    Optional<LegacyShadowMismatch> findByLegacyTypeAndLegacyIdAndResolvedAtIsNull(
        LegacyType type,
        Long legacyId
    );

    long countByResolvedAtIsNull();
}

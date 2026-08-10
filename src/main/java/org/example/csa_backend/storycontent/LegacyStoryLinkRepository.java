package org.example.csa_backend.storycontent;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegacyStoryLinkRepository extends JpaRepository<LegacyStoryLink, Long> {

    Optional<LegacyStoryLink> findByLegacyTypeAndLegacyId(LegacyType legacyType, Long legacyId);
}

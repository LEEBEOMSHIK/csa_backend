package org.example.csa_backend.storycontent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VersionLocaleRepository extends JpaRepository<VersionLocale, Long> {

    List<VersionLocale> findByVersionIdOrderByIdAsc(Long versionId);
}

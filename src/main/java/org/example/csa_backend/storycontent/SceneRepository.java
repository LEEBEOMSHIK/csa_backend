package org.example.csa_backend.storycontent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SceneRepository extends JpaRepository<Scene, Long> {

    List<Scene> findByVersionIdOrderByOrderIndexAscIdAsc(Long versionId);
}

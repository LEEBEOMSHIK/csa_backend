package org.example.csa_backend.storycontent;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SceneLocalizedContentRepository extends JpaRepository<SceneLocalizedContent, Long> {

    List<SceneLocalizedContent> findBySceneIdInOrderBySceneIdAscLocaleAsc(Collection<Long> sceneIds);
}

package org.example.csa_backend.storycontent;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AudioCueRepository extends JpaRepository<AudioCue, Long> {

    List<AudioCue> findBySceneIdInOrderBySceneIdAscIdAsc(Collection<Long> sceneIds);
}

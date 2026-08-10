package org.example.csa_backend.storycontent;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AudioVariantRepository extends JpaRepository<AudioVariant, Long> {

    List<AudioVariant> findByAudioCueIdInOrderByAudioCueIdAscIdAsc(Collection<Long> audioCueIds);
}

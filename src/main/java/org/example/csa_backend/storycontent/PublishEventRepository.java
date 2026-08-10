package org.example.csa_backend.storycontent;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublishEventRepository extends JpaRepository<PublishEvent, Long> {

    Optional<PublishEvent> findByStoryIdAndIdempotencyKey(Long storyId, String idempotencyKey);
}

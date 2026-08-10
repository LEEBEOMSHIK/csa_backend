package org.example.csa_backend.storycontent;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentVersionRepository extends JpaRepository<ContentVersion, Long> {

    Optional<ContentVersion> findByStoryIdAndStatus(Long storyId, ContentVersionStatus status);

    List<ContentVersion> findByStoryIdOrderByVersionNoDesc(Long storyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ContentVersion v where v.id = :id")
    Optional<ContentVersion> findAggregateForUpdate(@Param("id") Long id);
}

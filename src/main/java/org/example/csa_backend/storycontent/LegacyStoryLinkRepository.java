package org.example.csa_backend.storycontent;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LegacyStoryLinkRepository extends JpaRepository<LegacyStoryLink, Long> {

    Optional<LegacyStoryLink> findByLegacyTypeAndLegacyId(LegacyType legacyType, Long legacyId);

    List<LegacyStoryLink> findByLegacyTypeAndLegacyIdGreaterThanOrderByLegacyIdAsc(
        LegacyType legacyType,
        Long legacyId,
        Pageable pageable
    );

    @Query(value = """
        select l.story_id
        from legacy_story_links l
        join stories s on s.id = l.story_id
        join story_content_versions v on v.id = l.content_version_id
        where l.legacy_type = :legacyType
          and l.legacy_id = :legacyId
          and s.visibility = 'PUBLISHED'
          and s.published_version_id = l.content_version_id
          and v.status = 'PUBLISHED'
        """, nativeQuery = true)
    Optional<Long> findPublishedStoryId(
        @Param("legacyType") String legacyType,
        @Param("legacyId") Long legacyId
    );

    @Query("""
        select min(l.legacyId)
        from LegacyStoryLink l, Story s
        where s.id = l.storyId
          and l.legacyType = :legacyType
          and s.visibility = :visibility
        """)
    Optional<Long> findFirstLegacyIdForVisibility(
        @Param("legacyType") LegacyType legacyType,
        @Param("visibility") StoryVisibility visibility
    );

    @Query("""
        select min(l.storyId)
        from LegacyStoryLink l, Story s, ContentVersion v
        where s.id = l.storyId
          and v.id = l.contentVersionId
          and l.legacyType = :legacyType
          and s.visibility = org.example.csa_backend.storycontent.StoryVisibility.PUBLISHED
          and s.publishedVersionId = l.contentVersionId
          and v.status = org.example.csa_backend.storycontent.ContentVersionStatus.PUBLISHED
        """)
    Optional<Long> findFirstPublishedStoryId(@Param("legacyType") LegacyType legacyType);

    @Query("""
        select s.ownerUserId
        from LegacyStoryLink l, Story s
        where s.id = l.storyId
          and l.legacyType = :legacyType
          and l.legacyId = :legacyId
        """)
    Optional<Long> findOwnerUserId(
        @Param("legacyType") LegacyType legacyType,
        @Param("legacyId") Long legacyId
    );
}

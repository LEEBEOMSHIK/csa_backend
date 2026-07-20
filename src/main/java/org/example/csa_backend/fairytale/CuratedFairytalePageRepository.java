package org.example.csa_backend.fairytale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CuratedFairytalePageRepository extends JpaRepository<CuratedFairytalePage, Long> {

    @Query("""
            SELECT DISTINCT p FROM CuratedFairytalePage p
            LEFT JOIN FETCH p.audios
            JOIN p.fairytale f
            WHERE f.id = :fairytaleId
              AND f.delYn = 'N'
              AND p.delYn = 'N'
              AND p.contentVersion = :contentVersion
            ORDER BY p.pageIndex ASC
            """)
    List<CuratedFairytalePage> findActiveByFairytaleIdAndContentVersionOrderByPageIndexAsc(
            @Param("fairytaleId") Long fairytaleId,
            @Param("contentVersion") String contentVersion);
}

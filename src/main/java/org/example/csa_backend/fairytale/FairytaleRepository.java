package org.example.csa_backend.fairytale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FairytaleRepository extends JpaRepository<Fairytale, Long> {

    @Query("""
        SELECT f FROM Fairytale f
        WHERE f.delYn = 'N'
          AND f.isTheme = 'Y'
          AND (:categoryKey IS NULL OR EXISTS (
              SELECT 1 FROM f.categories c WHERE c.categoryKey = :categoryKey
          ))
        """)
    List<Fairytale> findThemes(@Param("categoryKey") String categoryKey);

    @Query("""
        SELECT f FROM Fairytale f
        WHERE f.delYn = 'N'
          AND f.isNew = 'Y'
          AND (:categoryKey IS NULL OR EXISTS (
              SELECT 1 FROM f.categories c WHERE c.categoryKey = :categoryKey
          ))
        """)
    List<Fairytale> findNewItems(@Param("categoryKey") String categoryKey);

    @Query("""
        SELECT f FROM Fairytale f
        WHERE f.delYn = 'N'
          AND f.isRecommended = 'Y'
          AND (:categoryKey IS NULL OR EXISTS (
              SELECT 1 FROM f.categories c WHERE c.categoryKey = :categoryKey
          ))
        """)
    List<Fairytale> findRecommended(@Param("categoryKey") String categoryKey);
}

package org.example.csa_backend.fairytale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FairytaleDetailRepository extends JpaRepository<FairytaleDetail, Long> {

    @Query("""
            SELECT d FROM FairytaleDetail d
            JOIN d.fairytale f
            WHERE f.id = :fairytaleId
              AND f.delYn = 'N'
              AND d.delYn = 'N'
            """)
    Optional<FairytaleDetail> findActiveByFairytaleId(@Param("fairytaleId") Long fairytaleId);
}

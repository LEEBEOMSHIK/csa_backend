package org.example.csa_backend.fairytale;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FairytaleDetailRepository extends JpaRepository<FairytaleDetail, Long> {

    Optional<FairytaleDetail> findByFairytaleId(Long fairytaleId);
}

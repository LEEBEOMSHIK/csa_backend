package org.example.csa_backend.fairytale;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiFairytalePageRepository extends JpaRepository<AiFairytalePage, Long> {
    List<AiFairytalePage> findByAiFairytaleIdOrderByPageIndex(Long aiFairytaleId);
}

package org.example.csa_backend.fairytale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByCategoryKey(String categoryKey);

    boolean existsByCategoryKey(String categoryKey);

    @Query("""
        SELECT c FROM Category c
        WHERE c.delYn = 'N'
        ORDER BY SIZE(c.fairytales) DESC
        """)
    List<Category> findAllOrderByFairytaleCountDesc();
}

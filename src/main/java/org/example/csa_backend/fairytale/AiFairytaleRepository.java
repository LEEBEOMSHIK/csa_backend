package org.example.csa_backend.fairytale;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiFairytaleRepository extends JpaRepository<AiFairytale, Long> {

    @EntityGraph(attributePaths = "pages")
    List<AiFairytale> findByOwnerIdOrderByIdDesc(Long ownerId);

    @EntityGraph(attributePaths = "pages")
    List<AiFairytale> findBySharedAndStatusOrderByIdDesc(String shared, String status);
}

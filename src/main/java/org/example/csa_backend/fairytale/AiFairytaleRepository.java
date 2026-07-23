package org.example.csa_backend.fairytale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiFairytaleRepository extends JpaRepository<AiFairytale, Long> {

    @EntityGraph(attributePaths = "pages")
    List<AiFairytale> findByOwnerIdOrderByIdDesc(Long ownerId);

    @EntityGraph(attributePaths = "pages")
    List<AiFairytale> findBySharedAndStatusOrderByIdDesc(String shared, String status);

    // admin 목록 전용: q(제목/소유자 이메일)·status·shared를 각각 독립적으로 optional 취급
    @Query("select f from AiFairytale f left join fetch f.owner o where " +
            "(:q is null or lower(f.title) like lower(concat('%', cast(:q as string), '%')) or lower(o.email) like lower(concat('%', cast(:q as string), '%'))) and " +
            "(:status is null or f.status = :status) and " +
            "(:shared is null or f.shared = :shared) " +
            "order by f.id desc")
    Page<AiFairytale> searchForAdmin(@Param("q") String q, @Param("status") String status, @Param("shared") String shared, Pageable pageable);
}

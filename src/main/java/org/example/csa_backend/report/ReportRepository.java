package org.example.csa_backend.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // admin 목록 전용: targetType·status를 각각 독립적으로 optional 취급
    @Query("select r from Report r join fetch r.reporter u where " +
            "(:targetType is null or r.targetType = :targetType) and " +
            "(:status is null or r.status = :status) " +
            "order by r.id desc")
    Page<Report> searchForAdmin(@Param("targetType") String targetType,
                                 @Param("status") String status,
                                 Pageable pageable);
}

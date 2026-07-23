package org.example.csa_backend.subscription;

import org.example.csa_backend.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByUser(User user);

    List<Subscription> findByUserId(Long userId);

    Optional<Subscription> findByOriginalTransactionId(String originalTransactionId);

    // admin 목록 전용: q(사용자 이메일)·platform·status를 각각 독립적으로 optional 취급
    @Query("select s from Subscription s join fetch s.user u where " +
            "(:q is null or lower(u.email) like lower(concat('%', :q, '%'))) and " +
            "(:platform is null or s.platform = :platform) and " +
            "(:status is null or s.status = :status) " +
            "order by s.id desc")
    Page<Subscription> searchForAdmin(@Param("q") String q,
                                       @Param("platform") Platform platform,
                                       @Param("status") SubscriptionStatus status,
                                       Pageable pageable);
}

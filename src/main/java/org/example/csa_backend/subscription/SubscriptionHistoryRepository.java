package org.example.csa_backend.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionHistoryRepository extends JpaRepository<SubscriptionHistory, Long> {

    List<SubscriptionHistory> findBySubscriptionIdOrderByCreatedAtAsc(Long subscriptionId);
}

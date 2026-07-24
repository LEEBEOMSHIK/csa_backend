package org.example.csa_backend.subscription;

import org.example.csa_backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByUser(User user);

    List<Subscription> findByUserId(Long userId);

    Optional<Subscription> findByOriginalTransactionId(String originalTransactionId);
}

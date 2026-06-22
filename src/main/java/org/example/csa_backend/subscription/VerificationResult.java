package org.example.csa_backend.subscription;

import java.time.LocalDateTime;

public record VerificationResult(
        String originalTransactionId,
        SubscriptionStatus status,
        LocalDateTime currentPeriodEnd,
        boolean autoRenew,
        StoreEnvironment environment
) {
}

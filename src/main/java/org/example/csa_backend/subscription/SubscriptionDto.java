package org.example.csa_backend.subscription;

import java.time.LocalDateTime;

public record SubscriptionDto(
        Platform platform,
        String productId,
        SubscriptionStatus status,
        LocalDateTime currentPeriodEnd,
        boolean autoRenew,
        StoreEnvironment environment
) {
    public static SubscriptionDto from(Subscription subscription) {
        return new SubscriptionDto(
                subscription.getPlatform(),
                subscription.getProductId(),
                subscription.getStatus(),
                subscription.getCurrentPeriodEnd(),
                subscription.isAutoRenewEnabled(),
                subscription.getEnvironment()
        );
    }
}

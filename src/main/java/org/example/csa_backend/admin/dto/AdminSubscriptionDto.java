package org.example.csa_backend.admin.dto;

import org.example.csa_backend.subscription.Platform;
import org.example.csa_backend.subscription.StoreEnvironment;
import org.example.csa_backend.subscription.Subscription;
import org.example.csa_backend.subscription.SubscriptionStatus;

import java.time.LocalDateTime;

public record AdminSubscriptionDto(
        Long id,
        String userEmail,
        Platform platform,
        String productId,
        SubscriptionStatus status,
        StoreEnvironment environment,
        LocalDateTime currentPeriodEnd,
        boolean autoRenew
) {
    public static AdminSubscriptionDto from(Subscription subscription) {
        String userEmail = subscription.getUser() != null ? subscription.getUser().getEmail() : null;
        return new AdminSubscriptionDto(
                subscription.getId(),
                userEmail,
                subscription.getPlatform(),
                subscription.getProductId(),
                subscription.getStatus(),
                subscription.getEnvironment(),
                subscription.getCurrentPeriodEnd(),
                subscription.isAutoRenewEnabled()
        );
    }
}

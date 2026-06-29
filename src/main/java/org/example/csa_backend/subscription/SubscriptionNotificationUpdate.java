package org.example.csa_backend.subscription;

import java.time.LocalDateTime;

/**
 * Authoritative subscription state derived from a verified store lifecycle notification.
 * {@code notificationTime} is used as an out-of-order guard so an older notification cannot
 * revert a newer persisted state. {@code autoRenewOnly} indicates a renewal-status change that
 * must not alter {@code status}/{@code currentPeriodEnd}.
 */
public record SubscriptionNotificationUpdate(
        String originalTransactionId,
        SubscriptionStatus status,
        LocalDateTime currentPeriodEnd,
        boolean autoRenew,
        LocalDateTime notificationTime,
        boolean autoRenewOnly
) {

    public static SubscriptionNotificationUpdate state(
            String originalTransactionId,
            SubscriptionStatus status,
            LocalDateTime currentPeriodEnd,
            boolean autoRenew,
            LocalDateTime notificationTime) {
        return new SubscriptionNotificationUpdate(
                originalTransactionId, status, currentPeriodEnd, autoRenew, notificationTime, false);
    }

    public static SubscriptionNotificationUpdate autoRenewChange(
            String originalTransactionId,
            boolean autoRenew,
            LocalDateTime notificationTime) {
        return new SubscriptionNotificationUpdate(
                originalTransactionId, null, null, autoRenew, notificationTime, true);
    }
}

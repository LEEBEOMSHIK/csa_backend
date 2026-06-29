package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Decodes an Apple App Store Server Notification V2 signed payload, verifies every JWS via
 * {@link AppleJwsVerifier} (signature + x5c PKIX chain), and maps the notification type/subtype to
 * an authoritative {@link SubscriptionNotificationUpdate}. The JWS signature verification is what
 * authenticates this otherwise-unauthenticated webhook; a verification failure is rejected.
 */
class AppleNotificationProcessor {

    private static final int AUTO_RENEW_ON = 1;

    private final AppleJwsVerifier jwsVerifier;

    AppleNotificationProcessor(AppleJwsVerifier jwsVerifier) {
        this.jwsVerifier = jwsVerifier;
    }

    SubscriptionNotificationUpdate process(String signedPayload) {
        AppleNotificationPayload notification =
                jwsVerifier.verify(signedPayload, AppleNotificationPayload.class);
        if (notification.notificationType() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Apple 통지 유형이 없습니다.");
        }
        if (notification.data() == null
                || notification.data().signedTransactionInfo() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Apple 통지 거래 정보가 없습니다.");
        }

        AppleTransactionPayload transaction = jwsVerifier.verify(
                notification.data().signedTransactionInfo(), AppleTransactionPayload.class);
        AppleRenewalPayload renewal = notification.data().signedRenewalInfo() == null
                ? null
                : jwsVerifier.verify(notification.data().signedRenewalInfo(), AppleRenewalPayload.class);

        String originalTransactionId = transaction.originalTransactionId();
        if (originalTransactionId == null || originalTransactionId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Apple 원거래 식별자를 확인할 수 없습니다.");
        }

        LocalDateTime notificationTime = epochMillisToUtc(notification.signedDate());
        LocalDateTime expiry = epochMillisToUtc(transaction.expiresDate());
        boolean autoRenew = renewal != null
                && renewal.autoRenewStatus() != null
                && renewal.autoRenewStatus() == AUTO_RENEW_ON;

        String type = notification.notificationType();
        String subtype = notification.subtype();

        return switch (type) {
            case "SUBSCRIBED", "DID_RENEW" -> SubscriptionNotificationUpdate.state(
                    originalTransactionId, SubscriptionStatus.ACTIVE, expiry, autoRenew, notificationTime);
            case "DID_CHANGE_RENEWAL_STATUS" -> SubscriptionNotificationUpdate.autoRenewChange(
                    originalTransactionId, autoRenew, notificationTime);
            case "DID_FAIL_TO_RENEW" -> SubscriptionNotificationUpdate.state(
                    originalTransactionId,
                    "GRACE_PERIOD".equals(subtype) ? SubscriptionStatus.GRACE : SubscriptionStatus.EXPIRED,
                    expiry, autoRenew, notificationTime);
            case "EXPIRED", "GRACE_PERIOD_EXPIRED", "REVOKE" -> SubscriptionNotificationUpdate.state(
                    originalTransactionId, SubscriptionStatus.EXPIRED, expiry, false, notificationTime);
            case "REFUND" -> SubscriptionNotificationUpdate.state(
                    originalTransactionId, SubscriptionStatus.EXPIRED, expiry, false, notificationTime);
            default -> null;
        };
    }

    private LocalDateTime epochMillisToUtc(Long epochMillis) {
        if (epochMillis == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }
}

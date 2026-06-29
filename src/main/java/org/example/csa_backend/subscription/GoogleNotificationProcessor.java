package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

/**
 * Decodes a Google Pub/Sub push envelope carrying a Real-time Developer Notification (RTDN).
 *
 * <p>Trust model: the notification itself is treated only as a trigger and is never trusted to set
 * subscription state. Authoritative state is re-fetched from the Google Play Developer API via
 * {@link GooglePlaySubscriptionGateway} (which requires our service-account credentials), so a
 * forged notification cannot manufacture an authoritative status. Pub/Sub OIDC token verification
 * is an additional optional gate to be added at the controller layer once the push subscription is
 * configured in the cloud console.
 */
class GoogleNotificationProcessor {

    private final ObjectMapper objectMapper;
    private final GooglePlayProperties properties;
    private final GooglePlaySubscriptionGateway gateway;

    GoogleNotificationProcessor(ObjectMapper objectMapper,
                                GooglePlayProperties properties,
                                GooglePlaySubscriptionGateway gateway) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.gateway = gateway;
    }

    SubscriptionNotificationUpdate process(GooglePubSubMessage message) {
        if (message == null || message.data() == null || message.data().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Google 통지 메시지가 비어 있습니다.");
        }

        GoogleDeveloperNotification notification = decode(message.data());
        GoogleSubscriptionNotification subscriptionNotification =
                notification.subscriptionNotification();
        if (subscriptionNotification == null
                || subscriptionNotification.purchaseToken() == null
                || subscriptionNotification.purchaseToken().isBlank()) {
            // Non-subscription notification (e.g. test/voided/one-time). Ignore safely.
            return null;
        }
        if (properties.getPackageName() == null || properties.getPackageName().isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google Play package name 설정이 필요합니다.");
        }

        String purchaseToken = subscriptionNotification.purchaseToken();
        // Authoritative re-query. purchaseToken is the stored originalTransactionId for Google.
        GooglePlaySubscriptionPurchase purchase =
                gateway.getSubscription(properties.getPackageName(), purchaseToken);
        GooglePlaySubscriptionLineItem lineItem = primaryLineItem(purchase);

        return SubscriptionNotificationUpdate.state(
                purchaseToken,
                mapStatus(purchase.subscriptionState()),
                parseExpiry(lineItem.expiryTime()),
                isAutoRenewEnabled(lineItem),
                epochMillisToUtc(notification.eventTimeMillis())
        );
    }

    private GoogleDeveloperNotification decode(String base64Data) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Data);
            return objectMapper.readValue(
                    new String(decoded, StandardCharsets.UTF_8), GoogleDeveloperNotification.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Google 통지 데이터를 해석할 수 없습니다.");
        }
    }

    private GooglePlaySubscriptionLineItem primaryLineItem(GooglePlaySubscriptionPurchase purchase) {
        if (purchase.lineItems() == null || purchase.lineItems().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Google Play 구독 상품을 찾을 수 없습니다.");
        }
        return purchase.lineItems().get(0);
    }

    private SubscriptionStatus mapStatus(String subscriptionState) {
        return switch (subscriptionState) {
            case "SUBSCRIPTION_STATE_ACTIVE" -> SubscriptionStatus.ACTIVE;
            case "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" -> SubscriptionStatus.GRACE;
            case "SUBSCRIPTION_STATE_CANCELED" -> SubscriptionStatus.CANCELED;
            case "SUBSCRIPTION_STATE_EXPIRED" -> SubscriptionStatus.EXPIRED;
            case "SUBSCRIPTION_STATE_PAUSED", "SUBSCRIPTION_STATE_ON_HOLD",
                    "SUBSCRIPTION_STATE_PENDING", "SUBSCRIPTION_STATE_UNSPECIFIED" -> SubscriptionStatus.EXPIRED;
            default -> throw new BusinessException(
                    ErrorCode.EXTERNAL_API_ERROR, "알 수 없는 Google Play 구독 상태입니다.");
        };
    }

    private LocalDateTime parseExpiry(String expiryTime) {
        try {
            return LocalDateTime.ofInstant(Instant.parse(expiryTime), ZoneOffset.UTC);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google Play 구독 만료일을 해석할 수 없습니다.");
        }
    }

    private boolean isAutoRenewEnabled(GooglePlaySubscriptionLineItem lineItem) {
        return lineItem.autoRenewingPlan() != null && lineItem.autoRenewingPlan().autoRenewEnabled();
    }

    private LocalDateTime epochMillisToUtc(Long epochMillis) {
        if (epochMillis == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }
}

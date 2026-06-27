package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@Profile("prod")
public class GooglePlayReceiptVerifier implements ReceiptVerifier {

    private final GooglePlayProperties properties;
    private final GooglePlaySubscriptionGateway gateway;

    GooglePlayReceiptVerifier(GooglePlayProperties properties, GooglePlaySubscriptionGateway gateway) {
        this.properties = properties;
        this.gateway = gateway;
    }

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.GOOGLE;
    }

    @Override
    public VerificationResult verify(Platform platform, String purchaseToken, String productId) {
        validateRequest(platform, purchaseToken, productId);
        GooglePlaySubscriptionPurchase purchase =
                gateway.getSubscription(properties.getPackageName(), purchaseToken);
        GooglePlaySubscriptionLineItem lineItem = matchingLineItem(purchase, productId);
        return new VerificationResult(
                purchaseToken,
                mapStatus(purchase.subscriptionState()),
                parseExpiry(lineItem.expiryTime()),
                isAutoRenewEnabled(lineItem),
                StoreEnvironment.PRODUCTION
        );
    }

    private void validateRequest(Platform platform, String purchaseToken, String productId) {
        if (platform != Platform.GOOGLE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Google Play 구독만 검증할 수 있습니다.");
        }
        if (purchaseToken == null || purchaseToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "구매 토큰은 필수입니다.");
        }
        if (productId == null || productId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "상품 ID는 필수입니다.");
        }
        if (properties.getPackageName() == null || properties.getPackageName().isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google Play package name 설정이 필요합니다.");
        }
    }

    private GooglePlaySubscriptionLineItem matchingLineItem(GooglePlaySubscriptionPurchase purchase,
                                                           String productId) {
        if (purchase.lineItems() == null || purchase.lineItems().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Google Play 구독 상품을 찾을 수 없습니다.");
        }
        return purchase.lineItems().stream()
                .filter(lineItem -> productId.equals(lineItem.productId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_INPUT,
                        "요청한 상품 ID와 Google Play 구독 정보가 일치하지 않습니다."
                ));
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
                    ErrorCode.EXTERNAL_API_ERROR,
                    "알 수 없는 Google Play 구독 상태입니다."
            );
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
}

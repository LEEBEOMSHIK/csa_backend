package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@Profile("prod")
public class AppleReceiptVerifier implements ReceiptVerifier {

    private static final int APPLE_STATUS_ACTIVE = 1;
    private static final int APPLE_STATUS_EXPIRED = 2;
    private static final int APPLE_STATUS_BILLING_RETRY = 3;
    private static final int APPLE_STATUS_GRACE_PERIOD = 4;
    private static final int APPLE_STATUS_REVOKED = 5;

    private final AppleProperties properties;
    private final AppleAppStoreServerGateway gateway;
    private final AppleJwsVerifier jwsVerifier;

    AppleReceiptVerifier(AppleProperties properties,
                         AppleAppStoreServerGateway gateway,
                         AppleJwsVerifier jwsVerifier) {
        this.properties = properties;
        this.gateway = gateway;
        this.jwsVerifier = jwsVerifier;
    }

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.APPLE;
    }

    @Override
    public VerificationResult verify(Platform platform, String purchaseToken, String productId) {
        validateRequest(platform, purchaseToken, productId);

        String transactionId = transactionId(purchaseToken);
        AppleSubscriptionStatusResponse statuses = gateway.getSubscriptionStatuses(transactionId);
        AppleLastTransaction lastTransaction = latestTransaction(statuses);

        AppleTransactionPayload transaction =
                jwsVerifier.verify(lastTransaction.signedTransactionInfo(), AppleTransactionPayload.class);
        AppleRenewalPayload renewal =
                jwsVerifier.verify(lastTransaction.signedRenewalInfo(), AppleRenewalPayload.class);

        validateTransaction(transaction, productId);

        return new VerificationResult(
                transaction.originalTransactionId(),
                mapStatus(lastTransaction.status()),
                parseExpiry(transaction.expiresDate()),
                isAutoRenewEnabled(renewal),
                mapEnvironment(transaction.environment())
        );
    }

    private void validateRequest(Platform platform, String purchaseToken, String productId) {
        if (platform != Platform.APPLE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "App Store 구독만 검증할 수 있습니다.");
        }
        if (purchaseToken == null || purchaseToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "구매 토큰은 필수입니다.");
        }
        if (productId == null || productId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "상품 ID는 필수입니다.");
        }
        if (properties.getBundleId() == null || properties.getBundleId().isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Apple bundle ID 설정이 필요합니다.");
        }
    }

    private String transactionId(String purchaseToken) {
        if (!isJws(purchaseToken)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "지원하지 않는 Apple 영수증 형식입니다.");
        }
        AppleTransactionPayload payload =
                jwsVerifier.verify(purchaseToken, AppleTransactionPayload.class);
        if (payload.transactionId() == null || payload.transactionId().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Apple 거래 식별자를 확인할 수 없습니다.");
        }
        return payload.transactionId();
    }

    private boolean isJws(String value) {
        return value.split("\\.").length == 3;
    }

    private AppleLastTransaction latestTransaction(AppleSubscriptionStatusResponse statuses) {
        if (statuses.data() == null || statuses.data().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Apple 구독 정보를 찾을 수 없습니다.");
        }
        List<AppleLastTransaction> transactions = statuses.data().get(0).lastTransactions();
        if (transactions == null || transactions.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Apple 구독 거래 정보를 찾을 수 없습니다.");
        }
        return transactions.get(0);
    }

    private void validateTransaction(AppleTransactionPayload transaction, String productId) {
        if (transaction.bundleId() != null && !properties.getBundleId().equals(transaction.bundleId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Apple 구독 번들 ID가 일치하지 않습니다.");
        }
        if (!productId.equals(transaction.productId())) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "요청한 상품 ID와 Apple 구독 정보가 일치하지 않습니다.");
        }
    }

    private SubscriptionStatus mapStatus(Integer status) {
        if (status == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Apple 구독 상태를 확인할 수 없습니다.");
        }
        return switch (status) {
            case APPLE_STATUS_ACTIVE -> SubscriptionStatus.ACTIVE;
            case APPLE_STATUS_GRACE_PERIOD, APPLE_STATUS_BILLING_RETRY -> SubscriptionStatus.GRACE;
            case APPLE_STATUS_EXPIRED, APPLE_STATUS_REVOKED -> SubscriptionStatus.EXPIRED;
            default -> throw new BusinessException(
                    ErrorCode.EXTERNAL_API_ERROR, "알 수 없는 Apple 구독 상태입니다.");
        };
    }

    private LocalDateTime parseExpiry(Long expiresDate) {
        if (expiresDate == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(expiresDate), ZoneOffset.UTC);
    }

    private boolean isAutoRenewEnabled(AppleRenewalPayload renewal) {
        return renewal != null && renewal.autoRenewStatus() != null && renewal.autoRenewStatus() == 1;
    }

    private StoreEnvironment mapEnvironment(String environment) {
        return "Sandbox".equalsIgnoreCase(environment) ? StoreEnvironment.SANDBOX : StoreEnvironment.PRODUCTION;
    }
}

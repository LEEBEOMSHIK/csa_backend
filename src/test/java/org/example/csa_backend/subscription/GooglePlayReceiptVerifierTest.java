package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GooglePlayReceiptVerifierTest {

    private final GooglePlayProperties properties = new GooglePlayProperties();
    private final RecordingGateway gateway = new RecordingGateway();
    private final GooglePlayReceiptVerifier verifier = new GooglePlayReceiptVerifier(properties, gateway);

    @BeforeEach
    void setUp() {
        properties.setPackageName("com.example.csa_frontend");
    }

    @Test
    void verifyMapsActiveGoogleSubscription() {
        gateway.purchase = purchase("SUBSCRIPTION_STATE_ACTIVE", "premium_monthly",
                "2026-07-25T00:00:00Z", true);

        VerificationResult result = verifier.verify(Platform.GOOGLE, "purchase-token", "premium_monthly");

        assertThat(gateway.packageName).isEqualTo("com.example.csa_frontend");
        assertThat(gateway.purchaseToken).isEqualTo("purchase-token");
        assertThat(result.originalTransactionId()).isEqualTo("purchase-token");
        assertThat(result.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.currentPeriodEnd()).isEqualTo(LocalDateTime.of(2026, 7, 25, 0, 0));
        assertThat(result.autoRenew()).isTrue();
        assertThat(result.environment()).isEqualTo(StoreEnvironment.PRODUCTION);
    }

    @Test
    void verifyMapsGracePeriodGoogleSubscription() {
        gateway.purchase = purchase("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "premium_monthly",
                "2026-07-25T00:00:00Z", true);

        VerificationResult result = verifier.verify(Platform.GOOGLE, "purchase-token", "premium_monthly");

        assertThat(result.status()).isEqualTo(SubscriptionStatus.GRACE);
    }

    @Test
    void verifyMapsCanceledGoogleSubscription() {
        gateway.purchase = purchase("SUBSCRIPTION_STATE_CANCELED", "premium_monthly",
                "2026-07-25T00:00:00Z", false);

        VerificationResult result = verifier.verify(Platform.GOOGLE, "purchase-token", "premium_monthly");

        assertThat(result.status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(result.autoRenew()).isFalse();
    }

    @Test
    void verifyRejectsProductMismatch() {
        gateway.purchase = purchase("SUBSCRIPTION_STATE_ACTIVE", "other_product",
                "2026-07-25T00:00:00Z", true);

        assertThatThrownBy(() -> verifier.verify(Platform.GOOGLE, "purchase-token", "premium_monthly"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void verifyRejectsNonGooglePlatform() {
        assertThatThrownBy(() -> verifier.verify(Platform.APPLE, "purchase-token", "premium_monthly"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private GooglePlaySubscriptionPurchase purchase(String state, String productId,
                                                    String expiryTime, boolean autoRenew) {
        return new GooglePlaySubscriptionPurchase(
                state,
                "GPA.1234-5678-9012-34567",
                List.of(new GooglePlaySubscriptionLineItem(
                        productId,
                        expiryTime,
                        new GooglePlayAutoRenewingPlan(autoRenew)
                ))
        );
    }

    private static class RecordingGateway implements GooglePlaySubscriptionGateway {
        private GooglePlaySubscriptionPurchase purchase;
        private String packageName;
        private String purchaseToken;

        @Override
        public GooglePlaySubscriptionPurchase getSubscription(String packageName, String purchaseToken) {
            this.packageName = packageName;
            this.purchaseToken = purchaseToken;
            return purchase;
        }
    }
}

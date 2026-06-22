package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StubReceiptVerifierTest {

    private final StubReceiptVerifier verifier = new StubReceiptVerifier();

    @Test
    void verifyNormalizesTokenToOriginalTransactionId() {
        VerificationResult result = verifier.verify(Platform.APPLE, "token-123", "premium_monthly");

        assertThat(result.originalTransactionId()).isEqualTo("token-123");
        assertThat(result.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.autoRenew()).isTrue();
        assertThat(result.environment()).isEqualTo(StoreEnvironment.SANDBOX);
        assertThat(result.currentPeriodEnd()).isAfter(LocalDateTime.now());
    }

    @Test
    void verifyRejectsBlankToken() {
        assertThatThrownBy(() -> verifier.verify(Platform.GOOGLE, "  ", "premium_monthly"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void verifyRejectsBlankProductId() {
        assertThatThrownBy(() -> verifier.verify(Platform.GOOGLE, "token-123", ""))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }
}

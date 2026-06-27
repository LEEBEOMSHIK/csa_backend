package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptVerifierDispatcherTest {

    private final RecordingVerifier googleVerifier = new RecordingVerifier(Platform.GOOGLE);
    private final RecordingVerifier appleVerifier = new RecordingVerifier(Platform.APPLE);

    @Test
    void routesToVerifierSupportingPlatform() {
        ReceiptVerifierDispatcher dispatcher =
                new ReceiptVerifierDispatcher(List.of(googleVerifier, appleVerifier));

        dispatcher.verify(Platform.APPLE, "token", "premium_monthly");
        assertThat(appleVerifier.called).isTrue();
        assertThat(googleVerifier.called).isFalse();

        dispatcher.verify(Platform.GOOGLE, "token", "premium_monthly");
        assertThat(googleVerifier.called).isTrue();
    }

    @Test
    void rejectsWhenNoVerifierSupportsPlatform() {
        ReceiptVerifierDispatcher dispatcher =
                new ReceiptVerifierDispatcher(List.of(googleVerifier));

        assertThatThrownBy(() -> dispatcher.verify(Platform.APPLE, "token", "premium_monthly"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    void rejectsNullPlatform() {
        ReceiptVerifierDispatcher dispatcher =
                new ReceiptVerifierDispatcher(List.of(googleVerifier, appleVerifier));

        assertThatThrownBy(() -> dispatcher.verify(null, "token", "premium_monthly"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private static final class RecordingVerifier implements ReceiptVerifier {
        private final Platform supported;
        private boolean called;

        private RecordingVerifier(Platform supported) {
            this.supported = supported;
        }

        @Override
        public boolean supports(Platform platform) {
            return platform == supported;
        }

        @Override
        public VerificationResult verify(Platform platform, String purchaseToken, String productId) {
            this.called = true;
            return new VerificationResult(
                    purchaseToken,
                    SubscriptionStatus.ACTIVE,
                    LocalDateTime.now().plusDays(30),
                    true,
                    StoreEnvironment.PRODUCTION);
        }
    }
}

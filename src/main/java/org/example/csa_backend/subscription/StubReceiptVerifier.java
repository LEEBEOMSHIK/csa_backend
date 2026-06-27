package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Profile("!prod")
public class StubReceiptVerifier implements ReceiptVerifier {

    private static final long STUB_PERIOD_DAYS = 30;

    @Override
    public boolean supports(Platform platform) {
        return platform != null;
    }

    @Override
    public VerificationResult verify(Platform platform, String purchaseToken, String productId) {
        if (platform == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "플랫폼은 필수입니다.");
        }
        if (purchaseToken == null || purchaseToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "구매 토큰은 필수입니다.");
        }
        if (productId == null || productId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "상품 ID는 필수입니다.");
        }
        return new VerificationResult(
                purchaseToken,
                SubscriptionStatus.ACTIVE,
                LocalDateTime.now().plusDays(STUB_PERIOD_DAYS),
                true,
                StoreEnvironment.SANDBOX
        );
    }
}

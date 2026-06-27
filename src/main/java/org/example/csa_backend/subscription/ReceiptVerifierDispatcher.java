package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReceiptVerifierDispatcher {

    private final List<ReceiptVerifier> verifiers;

    public ReceiptVerifierDispatcher(List<ReceiptVerifier> verifiers) {
        this.verifiers = List.copyOf(verifiers);
    }

    public VerificationResult verify(Platform platform, String purchaseToken, String productId) {
        if (platform == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "플랫폼은 필수입니다.");
        }
        ReceiptVerifier verifier = verifiers.stream()
                .filter(candidate -> candidate.supports(platform))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.EXTERNAL_API_ERROR, "영수증 검증을 수행할 수 없습니다."));
        return verifier.verify(platform, purchaseToken, productId);
    }
}

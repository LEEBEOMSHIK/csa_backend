package org.example.csa_backend.subscription;

public interface ReceiptVerifier {

    boolean supports(Platform platform);

    VerificationResult verify(Platform platform, String purchaseToken, String productId);
}

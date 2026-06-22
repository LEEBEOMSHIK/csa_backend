package org.example.csa_backend.subscription;

public interface ReceiptVerifier {

    VerificationResult verify(Platform platform, String purchaseToken, String productId);
}

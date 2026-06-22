package org.example.csa_backend.subscription;

public record VerifyReceiptRequest(
        Platform platform,
        String purchaseToken,
        String productId
) {
}

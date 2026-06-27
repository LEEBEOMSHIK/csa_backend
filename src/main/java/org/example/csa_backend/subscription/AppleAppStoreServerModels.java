package org.example.csa_backend.subscription;

import java.util.List;

record AppleSubscriptionStatusResponse(
        String bundleId,
        String environment,
        List<AppleSubscriptionGroup> data
) {
}

record AppleSubscriptionGroup(
        String subscriptionGroupIdentifier,
        List<AppleLastTransaction> lastTransactions
) {
}

record AppleLastTransaction(
        String originalTransactionId,
        Integer status,
        String signedTransactionInfo,
        String signedRenewalInfo
) {
}

record AppleTransactionPayload(
        String originalTransactionId,
        String transactionId,
        String productId,
        String bundleId,
        Long expiresDate,
        String environment,
        String type
) {
}

record AppleRenewalPayload(
        Integer autoRenewStatus,
        String productId
) {
}

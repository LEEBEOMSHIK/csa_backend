package org.example.csa_backend.subscription;

import java.util.List;

record GooglePlaySubscriptionPurchase(
        String subscriptionState,
        String latestOrderId,
        List<GooglePlaySubscriptionLineItem> lineItems
) {
}

record GooglePlaySubscriptionLineItem(
        String productId,
        String expiryTime,
        GooglePlayAutoRenewingPlan autoRenewingPlan
) {
}

record GooglePlayAutoRenewingPlan(
        boolean autoRenewEnabled
) {
}

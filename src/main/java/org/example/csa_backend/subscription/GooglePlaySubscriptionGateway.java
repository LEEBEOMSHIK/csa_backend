package org.example.csa_backend.subscription;

interface GooglePlaySubscriptionGateway {

    GooglePlaySubscriptionPurchase getSubscription(String packageName, String purchaseToken);
}

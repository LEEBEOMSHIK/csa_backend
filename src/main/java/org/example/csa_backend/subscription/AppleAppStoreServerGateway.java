package org.example.csa_backend.subscription;

interface AppleAppStoreServerGateway {

    AppleSubscriptionStatusResponse getSubscriptionStatuses(String transactionId);
}

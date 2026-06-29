package org.example.csa_backend.subscription;

record GooglePubSubEnvelope(
        GooglePubSubMessage message,
        String subscription
) {
}

record GooglePubSubMessage(
        String data,
        String messageId,
        String publishTime
) {
}

record GoogleDeveloperNotification(
        String version,
        String packageName,
        Long eventTimeMillis,
        GoogleSubscriptionNotification subscriptionNotification
) {
}

record GoogleSubscriptionNotification(
        String version,
        Integer notificationType,
        String purchaseToken,
        String subscriptionId
) {
}

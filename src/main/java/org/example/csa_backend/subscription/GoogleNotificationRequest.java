package org.example.csa_backend.subscription;

public record GoogleNotificationRequest(GooglePubSubMessage message, String subscription) {
}

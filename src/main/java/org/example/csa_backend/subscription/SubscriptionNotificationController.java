package org.example.csa_backend.subscription;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store lifecycle webhooks. These endpoints are called by Apple/Google, not by an authenticated
 * app user, so they are permitted without a JWT (see SecurityConfig). The real authentication is
 * the in-body proof: Apple App Store Server Notification V2 JWS signature verification, and the
 * Google Play Developer API authoritative re-query (a forged RTDN cannot manufacture state).
 *
 * <p>For Google, the request origin is additionally verified: Cloud Pub/Sub push attaches an OIDC
 * identity token in the {@code Authorization} header. In prod the {@link GooglePubSubOidcVerifier}
 * is present and is invoked before the body is handed to the service, so a request that fails OIDC
 * verification never reaches {@link SubscriptionService#applyStoreNotification} (no stale event-time
 * is persisted). In non-prod the verifier is absent and the Google path is already inert because
 * the Play gateway is missing (the service rejects it).
 */
@RestController
@RequestMapping("/subscriptions/notifications")
@RequiredArgsConstructor
public class SubscriptionNotificationController {

    private final SubscriptionNotificationService notificationService;
    private final ObjectProvider<GooglePubSubOidcVerifier> pubSubOidcVerifier;

    @PostMapping("/apple")
    public ResponseEntity<Void> appleNotification(@RequestBody AppleNotificationRequest request) {
        notificationService.handleAppleNotification(request.signedPayload());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/google")
    public ResponseEntity<Void> googleNotification(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody GoogleNotificationRequest request) {
        GooglePubSubOidcVerifier verifier = pubSubOidcVerifier.getIfAvailable();
        if (verifier != null) {
            verifier.verify(authorization);
        }
        notificationService.handleGoogleNotification(request.message());
        return ResponseEntity.ok().build();
    }
}

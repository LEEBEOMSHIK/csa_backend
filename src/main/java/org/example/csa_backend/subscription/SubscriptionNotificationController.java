package org.example.csa_backend.subscription;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store lifecycle webhooks. These endpoints are called by Apple/Google, not by an authenticated
 * app user, so they are permitted without a JWT (see SecurityConfig). The real authentication is
 * the in-body proof: Apple App Store Server Notification V2 JWS signature verification, and the
 * Google Play Developer API authoritative re-query (a forged RTDN cannot manufacture state).
 */
@RestController
@RequestMapping("/subscriptions/notifications")
@RequiredArgsConstructor
public class SubscriptionNotificationController {

    private final SubscriptionNotificationService notificationService;

    @PostMapping("/apple")
    public ResponseEntity<Void> appleNotification(@RequestBody AppleNotificationRequest request) {
        notificationService.handleAppleNotification(request.signedPayload());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/google")
    public ResponseEntity<Void> googleNotification(@RequestBody GoogleNotificationRequest request) {
        notificationService.handleGoogleNotification(request.message());
        return ResponseEntity.ok().build();
    }
}

package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Entry point for store lifecycle webhooks. Builds the platform-specific processor lazily so the
 * application starts in non-prod profiles where the prod-only store gateways/verifiers are absent.
 * Each {@code handle*} call returns silently for notifications that map to no actionable change or
 * target an unknown subscription; only verification/decoding failures propagate as errors.
 */
@Service
public class SubscriptionNotificationService {

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<AppleJwsVerifier> appleJwsVerifier;
    private final ObjectProvider<GooglePlaySubscriptionGateway> googleGateway;
    private final GooglePlayProperties googlePlayProperties;

    public SubscriptionNotificationService(SubscriptionService subscriptionService,
                                           ObjectMapper objectMapper,
                                           ObjectProvider<AppleJwsVerifier> appleJwsVerifier,
                                           ObjectProvider<GooglePlaySubscriptionGateway> googleGateway,
                                           GooglePlayProperties googlePlayProperties) {
        this.subscriptionService = subscriptionService;
        this.objectMapper = objectMapper;
        this.appleJwsVerifier = appleJwsVerifier;
        this.googleGateway = googleGateway;
        this.googlePlayProperties = googlePlayProperties;
    }

    public void handleAppleNotification(String signedPayload) {
        if (signedPayload == null || signedPayload.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Apple 통지 데이터가 비어 있습니다.");
        }
        AppleJwsVerifier verifier = appleJwsVerifier.getIfAvailable();
        if (verifier == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Apple 통지 검증 설정이 필요합니다.");
        }
        SubscriptionNotificationUpdate update =
                new AppleNotificationProcessor(verifier).process(signedPayload);
        if (update != null) {
            subscriptionService.applyStoreNotification(update);
        }
    }

    public void handleGoogleNotification(GooglePubSubMessage message) {
        GooglePlaySubscriptionGateway gateway = googleGateway.getIfAvailable();
        if (gateway == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google 통지 검증 설정이 필요합니다.");
        }
        SubscriptionNotificationUpdate update =
                new GoogleNotificationProcessor(objectMapper, googlePlayProperties, gateway).process(message);
        if (update != null) {
            subscriptionService.applyStoreNotification(update);
        }
    }
}

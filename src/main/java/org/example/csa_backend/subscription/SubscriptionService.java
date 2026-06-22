package org.example.csa_backend.subscription;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.setting.UserSettings;
import org.example.csa_backend.setting.UserSettingsRepository;
import org.example.csa_backend.user.User;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final String TIER_PREMIUM = "PREMIUM";
    private static final String TIER_FREE = "FREE";

    private final ObjectProvider<ReceiptVerifier> receiptVerifierProvider;
    private final SubscriptionRepository subscriptionRepository;
    private final UserSettingsRepository userSettingsRepository;

    @Transactional
    public SubscriptionDto verifyAndApply(User user, Platform platform, String purchaseToken, String productId) {
        ReceiptVerifier verifier = receiptVerifierProvider.getIfAvailable();
        if (verifier == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "영수증 검증을 수행할 수 없습니다.");
        }

        VerificationResult result = verifier.verify(platform, purchaseToken, productId);

        Subscription subscription = subscriptionRepository
                .findByOriginalTransactionId(result.originalTransactionId())
                .orElseGet(() -> subscriptionRepository.save(new Subscription(
                        user,
                        platform,
                        productId,
                        result.originalTransactionId(),
                        result.environment()
                )));

        subscription.updateFromVerification(
                result.status(),
                result.currentPeriodEnd(),
                result.autoRenew()
        );

        expireOtherActiveSubscriptions(user, subscription);
        recomputeTier(user);

        return SubscriptionDto.from(subscription);
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionDto> getMySubscription(User user) {
        return findCurrentSubscription(user).map(SubscriptionDto::from);
    }

    private void expireOtherActiveSubscriptions(User user, Subscription current) {
        List<Subscription> subscriptions = subscriptionRepository.findByUser(user);
        for (Subscription subscription : subscriptions) {
            if (!subscription.getId().equals(current.getId()) && subscription.isActive()) {
                subscription.expire();
            }
        }
    }

    private void recomputeTier(User user) {
        boolean hasActive = subscriptionRepository.findByUser(user).stream()
                .anyMatch(Subscription::isActive);
        UserSettings settings = userSettingsRepository.findByUser(user)
                .orElseGet(() -> userSettingsRepository.save(new UserSettings(user)));
        settings.updateSubscriptionTier(hasActive ? TIER_PREMIUM : TIER_FREE);
    }

    private Optional<Subscription> findCurrentSubscription(User user) {
        List<Subscription> subscriptions = subscriptionRepository.findByUser(user);
        return subscriptions.stream()
                .filter(Subscription::isActive)
                .findFirst()
                .or(() -> subscriptions.stream()
                        .max((a, b) -> a.getUpdatedAt().compareTo(b.getUpdatedAt())));
    }
}

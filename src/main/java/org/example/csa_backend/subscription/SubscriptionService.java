package org.example.csa_backend.subscription;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.setting.UserSettings;
import org.example.csa_backend.setting.UserSettingsRepository;
import org.example.csa_backend.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final String TIER_PREMIUM = "PREMIUM";
    private static final String TIER_FREE = "FREE";

    private final ReceiptVerifierDispatcher receiptVerifierDispatcher;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionHistoryRepository subscriptionHistoryRepository;
    private final UserSettingsRepository userSettingsRepository;

    @Transactional
    public SubscriptionDto verifyAndApply(User user, Platform platform, String purchaseToken, String productId) {
        VerificationResult result = receiptVerifierDispatcher.verify(platform, purchaseToken, productId);

        Optional<Subscription> existing =
                subscriptionRepository.findByOriginalTransactionId(result.originalTransactionId());
        boolean isNew = existing.isEmpty();
        Subscription subscription = existing
                .map(found -> requireOwnedBy(found, user))
                .orElseGet(() -> subscriptionRepository.save(new Subscription(
                        user,
                        platform,
                        productId,
                        result.originalTransactionId(),
                        result.environment()
                )));

        SubscriptionSnapshot before = SubscriptionSnapshot.of(subscription);
        subscription.updateFromVerification(
                result.status(),
                result.currentPeriodEnd(),
                result.autoRenew()
        );
        if (isNew) {
            subscriptionHistoryRepository.save(SubscriptionHistory.created(subscription));
        } else {
            recordIfChanged(subscription, before, SubscriptionChangeSource.VERIFICATION);
        }

        expireOtherActiveSubscriptions(user, subscription);
        recomputeTier(user);

        return SubscriptionDto.from(subscription);
    }

    /**
     * Applies a store lifecycle notification (Apple ASSN V2 / Google RTDN) to the matching
     * subscription. The notification is only a trigger; the authoritative state is supplied by the
     * caller after store-side verification (Apple JWS signature / Google Play API re-query).
     * Unknown subscriptions are ignored. Out-of-order notifications older than the persisted state
     * are skipped to avoid reverting a newer state.
     */
    @Transactional
    public void applyStoreNotification(SubscriptionNotificationUpdate update) {
        Optional<Subscription> found =
                subscriptionRepository.findByOriginalTransactionId(update.originalTransactionId());
        if (found.isEmpty()) {
            return;
        }
        Subscription subscription = found.get();
        if (subscription.isOlderThan(update.notificationTime())) {
            return;
        }
        SubscriptionSnapshot before = SubscriptionSnapshot.of(subscription);
        if (update.autoRenewOnly()) {
            subscription.updateAutoRenew(update.autoRenew());
        } else {
            subscription.updateFromVerification(
                    update.status(),
                    update.currentPeriodEnd(),
                    update.autoRenew()
            );
        }
        subscription.markNotificationApplied(update.notificationTime());
        recordIfChanged(subscription, before, SubscriptionChangeSource.STORE_NOTIFICATION);
        recomputeTier(subscription.getUser());
    }

    /**
     * 실제로 달라진 경우에만 이력 행을 남긴다. 스토어 알림과 영수증 검증은 같은 상태로
     * 여러 번 도착할 수 있어(재전송·중복 이벤트), 무조건 기록하면 이력이 노이즈로 찬다.
     * lastNotificationTime/updatedAt만 갱신된 경우는 변경으로 보지 않는다.
     */
    private void recordIfChanged(Subscription subscription,
                                 SubscriptionSnapshot before,
                                 SubscriptionChangeSource source) {
        if (before.matches(subscription)) {
            return;
        }
        subscriptionHistoryRepository.save(SubscriptionHistory.changed(subscription, before, source));
    }

    private Subscription requireOwnedBy(Subscription subscription, User user) {
        if (!subscription.getUser().getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return subscription;
    }

    @Transactional
    public Optional<SubscriptionDto> getMySubscription(User user) {
        return findCurrentSubscription(user).map(SubscriptionDto::from);
    }

    private void expireOtherActiveSubscriptions(User user, Subscription current) {
        List<Subscription> subscriptions = subscriptionRepository.findByUser(user);
        for (Subscription subscription : subscriptions) {
            if (!subscription.getId().equals(current.getId()) && subscription.isActive()) {
                SubscriptionSnapshot before = SubscriptionSnapshot.of(subscription);
                subscription.expire();
                recordIfChanged(subscription, before, SubscriptionChangeSource.SUPERSEDED);
            }
        }
    }

    private void recomputeTier(User user) {
        List<Subscription> subscriptions = subscriptionRepository.findByUser(user);
        expireLapsedSubscriptions(subscriptions);
        boolean hasActive = subscriptions.stream().anyMatch(Subscription::isActive);
        UserSettings settings = userSettingsRepository.findByUser(user)
                .orElseGet(() -> userSettingsRepository.save(new UserSettings(user)));
        settings.updateSubscriptionTier(hasActive ? TIER_PREMIUM : TIER_FREE);
    }

    private Optional<Subscription> findCurrentSubscription(User user) {
        List<Subscription> subscriptions = subscriptionRepository.findByUser(user);
        expireLapsedSubscriptions(subscriptions);
        return subscriptions.stream()
                .filter(Subscription::isActive)
                .findFirst()
                .or(() -> subscriptions.stream()
                        .max((a, b) -> a.getUpdatedAt().compareTo(b.getUpdatedAt())));
    }

    private void expireLapsedSubscriptions(List<Subscription> subscriptions) {
        for (Subscription subscription : subscriptions) {
            if (subscription.isExpiredByTime()) {
                SubscriptionSnapshot before = SubscriptionSnapshot.of(subscription);
                subscription.expire();
                recordIfChanged(subscription, before, SubscriptionChangeSource.EXPIRY_SWEEP);
            }
        }
    }
}

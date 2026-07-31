package org.example.csa_backend.subscription;

import org.example.csa_backend.setting.UserSettings;
import org.example.csa_backend.setting.UserSettingsRepository;
import org.example.csa_backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionNotificationApplyTest {

    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final SubscriptionHistoryRepository subscriptionHistoryRepository =
            mock(SubscriptionHistoryRepository.class);
    private final UserSettingsRepository userSettingsRepository = mock(UserSettingsRepository.class);
    private final ReceiptVerifierDispatcher dispatcher =
            new ReceiptVerifierDispatcher(List.of());

    private final SubscriptionService service = new SubscriptionService(
            dispatcher, subscriptionRepository, subscriptionHistoryRepository, userSettingsRepository);

    @Test
    void unknownSubscriptionIsIgnored() {
        when(subscriptionRepository.findByOriginalTransactionId("missing"))
                .thenReturn(Optional.empty());

        service.applyStoreNotification(SubscriptionNotificationUpdate.state(
                "missing", SubscriptionStatus.ACTIVE, LocalDateTime.now().plusDays(30), true, LocalDateTime.now()));
        // no exception, nothing to assert beyond no settings touched
    }

    @Test
    void expiredNotificationDowngradesTier() {
        User user = user(1L);
        UserSettings settings = new UserSettings(user);
        settings.updateSubscriptionTier("PREMIUM");
        Subscription sub = withId(new Subscription(
                user, Platform.APPLE, "premium_monthly", "orig-1", StoreEnvironment.PRODUCTION), 10L);
        sub.updateFromVerification(SubscriptionStatus.ACTIVE, LocalDateTime.now().plusDays(20), true);

        when(subscriptionRepository.findByOriginalTransactionId("orig-1")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(settings));

        service.applyStoreNotification(SubscriptionNotificationUpdate.state(
                "orig-1", SubscriptionStatus.EXPIRED, LocalDateTime.now().minusDays(1), false,
                LocalDateTime.now().plusSeconds(5)));

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(settings.getSubscriptionTier()).isEqualTo("FREE");
    }

    @Test
    void renewNotificationPromotesTier() {
        User user = user(1L);
        UserSettings settings = new UserSettings(user);
        Subscription sub = withId(new Subscription(
                user, Platform.APPLE, "premium_monthly", "orig-1", StoreEnvironment.PRODUCTION), 10L);
        sub.expire();

        when(subscriptionRepository.findByOriginalTransactionId("orig-1")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(settings));

        service.applyStoreNotification(SubscriptionNotificationUpdate.state(
                "orig-1", SubscriptionStatus.ACTIVE, LocalDateTime.now().plusDays(30), true,
                LocalDateTime.now().plusSeconds(5)));

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(settings.getSubscriptionTier()).isEqualTo("PREMIUM");
    }

    @Test
    void staleNotificationIsIgnoredByOrderGuard() {
        User user = user(1L);
        UserSettings settings = new UserSettings(user);
        Subscription sub = withId(new Subscription(
                user, Platform.APPLE, "premium_monthly", "orig-1", StoreEnvironment.PRODUCTION), 10L);
        sub.updateFromVerification(SubscriptionStatus.ACTIVE, LocalDateTime.now().plusDays(30), true);
        // A newer notification (event-time = now) was already applied; the incoming
        // notification claims an older event-time, so the guard must skip it. The guard
        // is driven by last_notification_time (event-time), not updated_at (wall-clock).
        ReflectionTestUtils.setField(sub, "lastNotificationTime", LocalDateTime.now());

        when(subscriptionRepository.findByOriginalTransactionId("orig-1")).thenReturn(Optional.of(sub));

        service.applyStoreNotification(SubscriptionNotificationUpdate.state(
                "orig-1", SubscriptionStatus.EXPIRED, LocalDateTime.now().minusDays(5), false,
                LocalDateTime.now().minusDays(2)));

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void delayedProcessingDoesNotSkipNewerNotification() {
        User user = user(1L);
        UserSettings settings = new UserSettings(user);
        Subscription sub = withId(new Subscription(
                user, Platform.APPLE, "premium_monthly", "orig-1", StoreEnvironment.PRODUCTION), 10L);
        sub.updateFromVerification(SubscriptionStatus.ACTIVE, LocalDateTime.now().plusDays(30), true);
        // Last applied notification had an older event-time; updated_at is "now" only
        // because processing was delayed. A newer notification must still be applied.
        ReflectionTestUtils.setField(sub, "lastNotificationTime", LocalDateTime.now().minusDays(2));
        ReflectionTestUtils.setField(sub, "updatedAt", LocalDateTime.now());

        when(subscriptionRepository.findByOriginalTransactionId("orig-1")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(settings));

        service.applyStoreNotification(SubscriptionNotificationUpdate.state(
                "orig-1", SubscriptionStatus.EXPIRED, LocalDateTime.now().minusDays(1), false,
                LocalDateTime.now().minusHours(1)));

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(settings.getSubscriptionTier()).isEqualTo("FREE");
    }

    @Test
    void firstNotificationIsAppliedWhenNoPriorEventTime() {
        User user = user(1L);
        UserSettings settings = new UserSettings(user);
        Subscription sub = withId(new Subscription(
                user, Platform.APPLE, "premium_monthly", "orig-1", StoreEnvironment.PRODUCTION), 10L);
        sub.updateFromVerification(SubscriptionStatus.ACTIVE, LocalDateTime.now().plusDays(30), true);
        // No notification has ever been applied: last_notification_time is null.
        assertThat(sub.getLastNotificationTime()).isNull();

        when(subscriptionRepository.findByOriginalTransactionId("orig-1")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(settings));

        LocalDateTime eventTime = LocalDateTime.now().minusDays(10);
        service.applyStoreNotification(SubscriptionNotificationUpdate.state(
                "orig-1", SubscriptionStatus.EXPIRED, LocalDateTime.now().minusDays(11), false, eventTime));

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(sub.getLastNotificationTime()).isEqualTo(eventTime);
    }

    @Test
    void autoRenewOnlyNotificationDoesNotChangeStatus() {
        User user = user(1L);
        UserSettings settings = new UserSettings(user);
        Subscription sub = withId(new Subscription(
                user, Platform.APPLE, "premium_monthly", "orig-1", StoreEnvironment.PRODUCTION), 10L);
        sub.updateFromVerification(SubscriptionStatus.ACTIVE, LocalDateTime.now().plusDays(30), true);

        when(subscriptionRepository.findByOriginalTransactionId("orig-1")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(settings));

        service.applyStoreNotification(SubscriptionNotificationUpdate.autoRenewChange(
                "orig-1", false, LocalDateTime.now().plusSeconds(5)));

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.isAutoRenewEnabled()).isFalse();
        assertThat(settings.getSubscriptionTier()).isEqualTo("PREMIUM");
    }

    @Test
    void lazyExpiryDowngradesActiveSubscriptionWithPastPeriodEnd() {
        User user = user(1L);
        UserSettings settings = new UserSettings(user);
        settings.updateSubscriptionTier("PREMIUM");
        Subscription sub = withId(new Subscription(
                user, Platform.APPLE, "premium_monthly", "orig-1", StoreEnvironment.PRODUCTION), 10L);
        sub.updateFromVerification(SubscriptionStatus.ACTIVE, LocalDateTime.now().minusDays(1), true);

        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));

        Optional<SubscriptionDto> dto = service.getMySubscription(user);

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(sub.isActive()).isFalse();
        assertThat(dto).isPresent();
        assertThat(dto.get().status()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    private Subscription withId(Subscription subscription, Long id) {
        ReflectionTestUtils.setField(subscription, "id", id);
        return subscription;
    }

    private User user(Long id) {
        User user = new User("user@example.com", "password");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}

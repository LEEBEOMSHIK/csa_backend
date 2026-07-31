package org.example.csa_backend.subscription;

import org.example.csa_backend.setting.UserSettings;
import org.example.csa_backend.setting.UserSettingsRepository;
import org.example.csa_backend.user.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * subscription_history 기록 규칙 검증. 핵심은 "실제로 달라졌을 때만 남긴다"이다 —
 * 스토어 알림/영수증 검증은 같은 상태로 여러 번 도착할 수 있다.
 */
class SubscriptionHistoryRecordingTest {

    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final SubscriptionHistoryRepository subscriptionHistoryRepository =
            mock(SubscriptionHistoryRepository.class);
    private final UserSettingsRepository userSettingsRepository = mock(UserSettingsRepository.class);
    private final ReceiptVerifier verifier = mock(ReceiptVerifier.class);
    private final ReceiptVerifierDispatcher dispatcher =
            new ReceiptVerifierDispatcher(List.of(verifier));

    private final SubscriptionService service = new SubscriptionService(
            dispatcher, subscriptionRepository, subscriptionHistoryRepository, userSettingsRepository);

    @Test
    void newSubscriptionRecordsCreatedRowWithNullPreviousState() {
        User user = user(1L);
        LocalDateTime periodEnd = LocalDateTime.now().plusDays(30);
        supports(Platform.APPLE);
        when(verifier.verify(Platform.APPLE, "token-1", "premium_monthly"))
                .thenReturn(result("orig-1", SubscriptionStatus.ACTIVE, periodEnd, true));
        when(subscriptionRepository.findByOriginalTransactionId("orig-1")).thenReturn(Optional.empty());
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(new UserSettings(user)));

        Subscription[] saved = new Subscription[1];
        when(subscriptionRepository.findByUser(user))
                .thenAnswer(inv -> saved[0] == null ? List.of() : List.of(saved[0]));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
            Subscription s = withId(inv.getArgument(0), 100L);
            saved[0] = s;
            return s;
        });

        service.verifyAndApply(user, Platform.APPLE, "token-1", "premium_monthly");

        SubscriptionHistory history = capturedSingleHistory();
        assertThat(history.getSource()).isEqualTo(SubscriptionChangeSource.CREATED);
        assertThat(history.getPreviousStatus()).isNull();
        assertThat(history.getPreviousPeriodEnd()).isNull();
        assertThat(history.getPreviousAutoRenew()).isNull();
        assertThat(history.getNewStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(history.getNewPeriodEnd()).isEqualTo(periodEnd);
        assertThat(history.getNewAutoRenew()).isEqualTo("Y");
        assertThat(history.getSubscription()).isSameAs(saved[0]);
    }

    @Test
    void verificationRecordsTransitionOnExistingSubscription() {
        User user = user(1L);
        LocalDateTime oldEnd = LocalDateTime.now().plusDays(2);
        LocalDateTime newEnd = LocalDateTime.now().plusDays(32);
        Subscription existing = activeSubscription(user, "orig-9", oldEnd, true);

        supports(Platform.GOOGLE);
        when(verifier.verify(Platform.GOOGLE, "token-9", "premium_monthly"))
                .thenReturn(result("orig-9", SubscriptionStatus.CANCELED, newEnd, false));
        when(subscriptionRepository.findByOriginalTransactionId("orig-9"))
                .thenReturn(Optional.of(existing));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(existing));
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(new UserSettings(user)));

        service.verifyAndApply(user, Platform.GOOGLE, "token-9", "premium_monthly");

        SubscriptionHistory history = capturedSingleHistory();
        assertThat(history.getSource()).isEqualTo(SubscriptionChangeSource.VERIFICATION);
        assertThat(history.getPreviousStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(history.getPreviousPeriodEnd()).isEqualTo(oldEnd);
        assertThat(history.getPreviousAutoRenew()).isEqualTo("Y");
        assertThat(history.getNewStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(history.getNewPeriodEnd()).isEqualTo(newEnd);
        assertThat(history.getNewAutoRenew()).isEqualTo("N");
    }

    @Test
    void repeatedVerificationWithIdenticalStateRecordsNothing() {
        User user = user(1L);
        LocalDateTime periodEnd = LocalDateTime.now().plusDays(30);
        Subscription existing = activeSubscription(user, "orig-9", periodEnd, true);

        supports(Platform.GOOGLE);
        when(verifier.verify(Platform.GOOGLE, "token-9", "premium_monthly"))
                .thenReturn(result("orig-9", SubscriptionStatus.ACTIVE, periodEnd, true));
        when(subscriptionRepository.findByOriginalTransactionId("orig-9"))
                .thenReturn(Optional.of(existing));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(existing));
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(new UserSettings(user)));

        service.verifyAndApply(user, Platform.GOOGLE, "token-9", "premium_monthly");

        verify(subscriptionHistoryRepository, never()).save(any(SubscriptionHistory.class));
    }

    @Test
    void storeNotificationRecordsTransition() {
        User user = user(1L);
        LocalDateTime periodEnd = LocalDateTime.now().plusDays(30);
        Subscription sub = activeSubscription(user, "orig-1", periodEnd, true);
        LocalDateTime expiredEnd = LocalDateTime.now().minusDays(1);

        when(subscriptionRepository.findByOriginalTransactionId("orig-1")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(new UserSettings(user)));

        service.applyStoreNotification(SubscriptionNotificationUpdate.state(
                "orig-1", SubscriptionStatus.EXPIRED, expiredEnd, false, LocalDateTime.now()));

        SubscriptionHistory history = capturedSingleHistory();
        assertThat(history.getSource()).isEqualTo(SubscriptionChangeSource.STORE_NOTIFICATION);
        assertThat(history.getPreviousStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(history.getPreviousPeriodEnd()).isEqualTo(periodEnd);
        assertThat(history.getNewStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(history.getNewPeriodEnd()).isEqualTo(expiredEnd);
        assertThat(history.getNewAutoRenew()).isEqualTo("N");
    }

    @Test
    void duplicateStoreNotificationWithIdenticalStateRecordsNothing() {
        User user = user(1L);
        LocalDateTime periodEnd = LocalDateTime.now().plusDays(30);
        Subscription sub = activeSubscription(user, "orig-1", periodEnd, true);

        when(subscriptionRepository.findByOriginalTransactionId("orig-1")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(new UserSettings(user)));

        // 같은 상태의 알림이 두 번 도착 — 이벤트 시각만 앞으로 간다.
        service.applyStoreNotification(SubscriptionNotificationUpdate.state(
                "orig-1", SubscriptionStatus.ACTIVE, periodEnd, true, LocalDateTime.now()));
        service.applyStoreNotification(SubscriptionNotificationUpdate.state(
                "orig-1", SubscriptionStatus.ACTIVE, periodEnd, true, LocalDateTime.now().plusSeconds(30)));

        verify(subscriptionHistoryRepository, never()).save(any(SubscriptionHistory.class));
    }

    @Test
    void autoRenewOnlyNotificationRecordsOnlyWhenFlagActuallyFlips() {
        User user = user(1L);
        LocalDateTime periodEnd = LocalDateTime.now().plusDays(30);
        Subscription sub = activeSubscription(user, "orig-1", periodEnd, true);

        when(subscriptionRepository.findByOriginalTransactionId("orig-1")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(new UserSettings(user)));

        service.applyStoreNotification(SubscriptionNotificationUpdate.autoRenewChange(
                "orig-1", false, LocalDateTime.now()));
        // 이미 'N'인 상태에서 같은 알림이 재전송되면 기록하지 않는다.
        service.applyStoreNotification(SubscriptionNotificationUpdate.autoRenewChange(
                "orig-1", false, LocalDateTime.now().plusSeconds(30)));

        SubscriptionHistory history = capturedSingleHistory();
        assertThat(history.getSource()).isEqualTo(SubscriptionChangeSource.STORE_NOTIFICATION);
        assertThat(history.getPreviousAutoRenew()).isEqualTo("Y");
        assertThat(history.getNewAutoRenew()).isEqualTo("N");
        assertThat(history.getPreviousStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(history.getNewStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void staleNotificationSkippedByOrderGuardRecordsNothing() {
        User user = user(1L);
        Subscription sub = activeSubscription(user, "orig-1", LocalDateTime.now().plusDays(30), true);
        ReflectionTestUtils.setField(sub, "lastNotificationTime", LocalDateTime.now());

        when(subscriptionRepository.findByOriginalTransactionId("orig-1")).thenReturn(Optional.of(sub));

        service.applyStoreNotification(SubscriptionNotificationUpdate.state(
                "orig-1", SubscriptionStatus.EXPIRED, LocalDateTime.now().minusDays(5), false,
                LocalDateTime.now().minusDays(2)));

        verify(subscriptionHistoryRepository, never()).save(any(SubscriptionHistory.class));
    }

    @Test
    void lazyExpiryOnReadRecordsExpirySweepRow() {
        User user = user(1L);
        LocalDateTime lapsedEnd = LocalDateTime.now().minusDays(1);
        Subscription sub = activeSubscription(user, "orig-1", lapsedEnd, true);
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));

        service.getMySubscription(user);

        SubscriptionHistory history = capturedSingleHistory();
        assertThat(history.getSource()).isEqualTo(SubscriptionChangeSource.EXPIRY_SWEEP);
        assertThat(history.getPreviousStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(history.getNewStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(history.getPreviousAutoRenew()).isEqualTo("Y");
        assertThat(history.getNewAutoRenew()).isEqualTo("N");
    }

    @Test
    void secondReadOfAlreadyExpiredSubscriptionRecordsNothing() {
        User user = user(1L);
        Subscription sub = activeSubscription(user, "orig-1", LocalDateTime.now().minusDays(1), true);
        sub.expire();
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));

        service.getMySubscription(user);

        verify(subscriptionHistoryRepository, never()).save(any(SubscriptionHistory.class));
    }

    @Test
    void supersededSubscriptionIsRecordedSeparatelyFromTheNewOne() {
        User user = user(1L);
        Subscription stale = activeSubscription(user, "orig-old", LocalDateTime.now().plusDays(10), true);
        Subscription current = withId(new Subscription(
                user, Platform.APPLE, "premium_monthly", "orig-new", StoreEnvironment.SANDBOX), 11L);

        supports(Platform.APPLE);
        when(verifier.verify(Platform.APPLE, "token-new", "premium_monthly"))
                .thenReturn(result("orig-new", SubscriptionStatus.ACTIVE,
                        LocalDateTime.now().plusDays(30), true));
        when(subscriptionRepository.findByOriginalTransactionId("orig-new"))
                .thenReturn(Optional.of(current));
        when(subscriptionRepository.findByUser(user))
                .thenReturn(new ArrayList<>(List.of(stale, current)));
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(new UserSettings(user)));

        service.verifyAndApply(user, Platform.APPLE, "token-new", "premium_monthly");

        List<SubscriptionHistory> histories = capturedHistories();
        assertThat(histories).hasSize(2);
        assertThat(histories.get(0).getSource()).isEqualTo(SubscriptionChangeSource.VERIFICATION);
        assertThat(histories.get(0).getSubscription()).isSameAs(current);
        assertThat(histories.get(1).getSource()).isEqualTo(SubscriptionChangeSource.SUPERSEDED);
        assertThat(histories.get(1).getSubscription()).isSameAs(stale);
        assertThat(histories.get(1).getPreviousStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(histories.get(1).getNewStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    private SubscriptionHistory capturedSingleHistory() {
        List<SubscriptionHistory> histories = capturedHistories();
        assertThat(histories).hasSize(1);
        return histories.get(0);
    }

    private List<SubscriptionHistory> capturedHistories() {
        ArgumentCaptor<SubscriptionHistory> captor = ArgumentCaptor.forClass(SubscriptionHistory.class);
        verify(subscriptionHistoryRepository, atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private Subscription activeSubscription(User user, String originalTransactionId,
                                            LocalDateTime periodEnd, boolean autoRenew) {
        Subscription subscription = withId(new Subscription(
                user, Platform.APPLE, "premium_monthly", originalTransactionId,
                StoreEnvironment.PRODUCTION), 10L);
        subscription.updateFromVerification(SubscriptionStatus.ACTIVE, periodEnd, autoRenew);
        return subscription;
    }

    private void supports(Platform platform) {
        when(verifier.supports(platform)).thenReturn(true);
    }

    private VerificationResult result(String originalTransactionId, SubscriptionStatus status,
                                     LocalDateTime periodEnd, boolean autoRenew) {
        return new VerificationResult(
                originalTransactionId, status, periodEnd, autoRenew, StoreEnvironment.SANDBOX);
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

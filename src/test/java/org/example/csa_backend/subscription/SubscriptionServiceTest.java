package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.setting.UserSettings;
import org.example.csa_backend.setting.UserSettingsRepository;
import org.example.csa_backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionServiceTest {

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
    void verifyAndApplyCreatesNewSubscriptionAndPromotesTier() {
        User user = user(1L);
        UserSettings settings = new UserSettings(user);
        supports(Platform.APPLE);
        when(verifier.verify(Platform.APPLE, "token-1", "premium_monthly"))
                .thenReturn(activeResult("orig-1"));
        when(subscriptionRepository.findByOriginalTransactionId("orig-1")).thenReturn(Optional.empty());
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(settings));

        // current subscription becomes visible to findByUser after save
        Subscription[] saved = new Subscription[1];
        when(subscriptionRepository.findByUser(user))
                .thenAnswer(inv -> saved[0] == null ? List.of() : List.of(saved[0]));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
            Subscription s = withId(inv.getArgument(0), 100L);
            saved[0] = s;
            return s;
        });

        SubscriptionDto dto = service.verifyAndApply(user, Platform.APPLE, "token-1", "premium_monthly");

        assertThat(dto.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(dto.platform()).isEqualTo(Platform.APPLE);
        assertThat(dto.autoRenew()).isTrue();
        assertThat(settings.getSubscriptionTier()).isEqualTo("PREMIUM");
    }

    @Test
    void verifyAndApplyIsIdempotentForSameOriginalTransaction() {
        User user = user(1L);
        UserSettings settings = new UserSettings(user);
        Subscription existing = withId(new Subscription(
                user, Platform.GOOGLE, "premium_monthly", "orig-9", StoreEnvironment.SANDBOX), 50L);
        supports(Platform.GOOGLE);
        when(verifier.verify(Platform.GOOGLE, "orig-9", "premium_monthly"))
                .thenReturn(activeResult("orig-9"));
        when(subscriptionRepository.findByOriginalTransactionId("orig-9"))
                .thenReturn(Optional.of(existing));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(existing));
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(settings));

        service.verifyAndApply(user, Platform.GOOGLE, "orig-9", "premium_monthly");

        verify(subscriptionRepository, never()).save(any(Subscription.class));
        assertThat(existing.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(settings.getSubscriptionTier()).isEqualTo("PREMIUM");
    }

    @Test
    void verifyAndApplyRejectsCrossUserOriginalTransaction() {
        User owner = user(1L);
        User attacker = user(2L);
        Subscription existing = withId(new Subscription(
                owner, Platform.APPLE, "premium_monthly", "orig-7", StoreEnvironment.PRODUCTION), 70L);
        supports(Platform.APPLE);
        when(verifier.verify(Platform.APPLE, "token-7", "premium_monthly"))
                .thenReturn(activeResult("orig-7"));
        when(subscriptionRepository.findByOriginalTransactionId("orig-7"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                service.verifyAndApply(attacker, Platform.APPLE, "token-7", "premium_monthly"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    void verifyAndApplyExpiresOtherActiveSubscriptions() {
        User user = user(1L);
        UserSettings settings = new UserSettings(user);
        Subscription stale = withId(new Subscription(
                user, Platform.APPLE, "premium_monthly", "orig-old", StoreEnvironment.SANDBOX), 10L);
        Subscription current = withId(new Subscription(
                user, Platform.APPLE, "premium_monthly", "orig-new", StoreEnvironment.SANDBOX), 11L);

        supports(Platform.APPLE);
        when(verifier.verify(Platform.APPLE, "orig-new", "premium_monthly"))
                .thenReturn(activeResult("orig-new"));
        when(subscriptionRepository.findByOriginalTransactionId("orig-new"))
                .thenReturn(Optional.of(current));
        List<Subscription> all = new ArrayList<>(List.of(stale, current));
        when(subscriptionRepository.findByUser(user)).thenReturn(all);
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(settings));

        service.verifyAndApply(user, Platform.APPLE, "orig-new", "premium_monthly");

        assertThat(stale.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(current.isActive()).isTrue();
        assertThat(settings.getSubscriptionTier()).isEqualTo("PREMIUM");
    }

    @Test
    void verifyAndApplyKeepsCanceledSubscriptionPremiumUntilPeriodEnd() {
        User user = user(1L);
        UserSettings settings = new UserSettings(user);
        supports(Platform.GOOGLE);
        when(verifier.verify(Platform.GOOGLE, "token-1", "premium_monthly"))
                .thenReturn(new VerificationResult(
                        "token-1",
                        SubscriptionStatus.CANCELED,
                        LocalDateTime.now().plusDays(7),
                        false,
                        StoreEnvironment.PRODUCTION
                ));
        when(subscriptionRepository.findByOriginalTransactionId("token-1")).thenReturn(Optional.empty());
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(settings));

        Subscription[] saved = new Subscription[1];
        when(subscriptionRepository.findByUser(user))
                .thenAnswer(inv -> saved[0] == null ? List.of() : List.of(saved[0]));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
            Subscription s = withId(inv.getArgument(0), 100L);
            saved[0] = s;
            return s;
        });

        SubscriptionDto dto = service.verifyAndApply(user, Platform.GOOGLE, "token-1", "premium_monthly");

        assertThat(dto.status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(settings.getSubscriptionTier()).isEqualTo("PREMIUM");
    }

    @Test
    void verifyAndApplyRejectsWhenVerifierAbsent() {
        User user = user(1L);
        SubscriptionService emptyService = new SubscriptionService(
                new ReceiptVerifierDispatcher(List.of()), subscriptionRepository,
                subscriptionHistoryRepository, userSettingsRepository);

        assertThatThrownBy(() ->
                emptyService.verifyAndApply(user, Platform.APPLE, "token-1", "premium_monthly"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);

        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    void getMySubscriptionPrefersActive() {
        User user = user(1L);
        Subscription expired = withId(new Subscription(
                user, Platform.APPLE, "premium_monthly", "orig-old", StoreEnvironment.SANDBOX), 10L);
        expired.expire();
        Subscription active = withId(new Subscription(
                user, Platform.APPLE, "premium_monthly", "orig-new", StoreEnvironment.SANDBOX), 11L);
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(expired, active));

        Optional<SubscriptionDto> dto = service.getMySubscription(user);

        assertThat(dto).isPresent();
        assertThat(dto.get().status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void getMySubscriptionEmptyWhenNone() {
        User user = user(1L);
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of());

        assertThat(service.getMySubscription(user)).isEmpty();
    }

    private void supports(Platform platform) {
        when(verifier.supports(platform)).thenReturn(true);
    }

    private VerificationResult activeResult(String originalTransactionId) {
        return new VerificationResult(
                originalTransactionId,
                SubscriptionStatus.ACTIVE,
                LocalDateTime.now().plusDays(30),
                true,
                StoreEnvironment.SANDBOX
        );
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

package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.setting.UserSettings;
import org.example.csa_backend.setting.UserSettingsRepository;
import org.example.csa_backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
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

    @SuppressWarnings("unchecked")
    private final ObjectProvider<ReceiptVerifier> receiptVerifierProvider = mock(ObjectProvider.class);
    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final UserSettingsRepository userSettingsRepository = mock(UserSettingsRepository.class);
    private final ReceiptVerifier verifier = mock(ReceiptVerifier.class);

    private final SubscriptionService service = new SubscriptionService(
            receiptVerifierProvider, subscriptionRepository, userSettingsRepository);

    @Test
    void verifyAndApplyCreatesNewSubscriptionAndPromotesTier() {
        User user = user(1L);
        UserSettings settings = new UserSettings(user);
        stubVerifierAvailable();
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
        stubVerifierAvailable();
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
    void verifyAndApplyExpiresOtherActiveSubscriptions() {
        User user = user(1L);
        UserSettings settings = new UserSettings(user);
        Subscription stale = withId(new Subscription(
                user, Platform.APPLE, "premium_monthly", "orig-old", StoreEnvironment.SANDBOX), 10L);
        Subscription current = withId(new Subscription(
                user, Platform.APPLE, "premium_monthly", "orig-new", StoreEnvironment.SANDBOX), 11L);

        stubVerifierAvailable();
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
    void verifyAndApplyRejectsWhenVerifierAbsent() {
        User user = user(1L);
        when(receiptVerifierProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.verifyAndApply(user, Platform.APPLE, "token-1", "premium_monthly"))
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

    private void stubVerifierAvailable() {
        when(receiptVerifierProvider.getIfAvailable()).thenReturn(verifier);
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

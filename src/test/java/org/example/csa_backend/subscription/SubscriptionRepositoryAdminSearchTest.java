package org.example.csa_backend.subscription;

import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SubscriptionRepositoryAdminSearchTest {

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Transactional
    void platformOnlyFilterIgnoresStatusAndReturnsCorrectTotalElements() {
        User googleUser = userRepository.saveAndFlush(new User("google-user@example.com", "pw"));
        User appleUser = userRepository.saveAndFlush(new User("apple-user@example.com", "pw"));

        Subscription googleActive = new Subscription(
                googleUser, Platform.GOOGLE, "premium_monthly", "orig-google-active", StoreEnvironment.PRODUCTION);
        Subscription googleExpired = new Subscription(
                googleUser, Platform.GOOGLE, "premium_monthly", "orig-google-expired", StoreEnvironment.PRODUCTION);
        googleExpired.expire();
        Subscription appleActive = new Subscription(
                appleUser, Platform.APPLE, "premium_monthly", "orig-apple-active", StoreEnvironment.PRODUCTION);

        subscriptionRepository.saveAllAndFlush(java.util.List.of(googleActive, googleExpired, appleActive));

        // platform=GOOGLE only, no status filter -> must include both ACTIVE and EXPIRED google subscriptions,
        // must exclude the apple subscription, and totalElements must reflect the filtered count.
        Page<Subscription> result = subscriptionRepository.searchForAdmin(
                null, Platform.GOOGLE, null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(Subscription::getPlatform)
                .containsOnly(Platform.GOOGLE);
        assertThat(result.getContent())
                .extracting(Subscription::getOriginalTransactionId)
                .containsExactlyInAnyOrder("orig-google-active", "orig-google-expired");
    }

    @Test
    @Transactional
    void statusOnlyFilterIgnoresPlatformAndReturnsCorrectTotalElements() {
        User googleUser = userRepository.saveAndFlush(new User("google-user2@example.com", "pw"));
        User appleUser = userRepository.saveAndFlush(new User("apple-user2@example.com", "pw"));

        Subscription googleActive = new Subscription(
                googleUser, Platform.GOOGLE, "premium_monthly", "orig-2-google-active", StoreEnvironment.PRODUCTION);
        Subscription appleActive = new Subscription(
                appleUser, Platform.APPLE, "premium_monthly", "orig-2-apple-active", StoreEnvironment.PRODUCTION);
        Subscription appleExpired = new Subscription(
                appleUser, Platform.APPLE, "premium_monthly", "orig-2-apple-expired", StoreEnvironment.PRODUCTION);
        appleExpired.expire();

        subscriptionRepository.saveAllAndFlush(java.util.List.of(googleActive, appleActive, appleExpired));

        // status=ACTIVE only, no platform filter -> must include both google and apple ACTIVE subscriptions,
        // must exclude the expired one, and totalElements must reflect the filtered count.
        Page<Subscription> result = subscriptionRepository.searchForAdmin(
                null, null, SubscriptionStatus.ACTIVE, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(Subscription::getStatus)
                .containsOnly(SubscriptionStatus.ACTIVE);
        assertThat(result.getContent())
                .extracting(Subscription::getOriginalTransactionId)
                .containsExactlyInAnyOrder("orig-2-google-active", "orig-2-apple-active");
    }
}

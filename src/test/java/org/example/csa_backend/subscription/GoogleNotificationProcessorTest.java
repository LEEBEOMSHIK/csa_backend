package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleNotificationProcessorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GooglePlayProperties properties = new GooglePlayProperties();
    private final GooglePlaySubscriptionGateway gateway = mock(GooglePlaySubscriptionGateway.class);

    private GoogleNotificationProcessor processor;

    @BeforeEach
    void setUp() {
        properties.setPackageName("com.example.csa");
        processor = new GoogleNotificationProcessor(objectMapper, properties, gateway);
    }

    @Test
    void rtdnTriggersAuthoritativeRequeryAndMapsState() {
        when(gateway.getSubscription(eq("com.example.csa"), eq("token-1")))
                .thenReturn(activePurchase("2099-01-01T00:00:00Z", true));

        SubscriptionNotificationUpdate update = processor.process(message("token-1"));

        assertThat(update.originalTransactionId()).isEqualTo("token-1");
        assertThat(update.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(update.autoRenew()).isTrue();
        assertThat(update.currentPeriodEnd()).isNotNull();
    }

    @Test
    void requeryFailurePropagates() {
        when(gateway.getSubscription(eq("com.example.csa"), eq("token-2")))
                .thenThrow(new BusinessException(
                        org.example.csa_backend.common.exception.ErrorCode.EXTERNAL_API_ERROR, "boom"));

        assertThatThrownBy(() -> processor.process(message("token-2")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void forgedNotificationCannotSetStateWithoutRequery() {
        // A forged RTDN claims active, but the authoritative re-query says expired.
        when(gateway.getSubscription(eq("com.example.csa"), eq("token-3")))
                .thenReturn(expiredPurchase());

        SubscriptionNotificationUpdate update = processor.process(message("token-3"));

        assertThat(update.status()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void nonSubscriptionNotificationIsIgnored() {
        String data = base64("{\"version\":\"1.0\",\"packageName\":\"com.example.csa\","
                + "\"eventTimeMillis\":\"1700000000000\","
                + "\"testNotification\":{\"version\":\"1.0\"}}");

        assertThat(processor.process(new GooglePubSubMessage(data, "m-1", null))).isNull();
    }

    @Test
    void emptyMessageIsRejected() {
        assertThatThrownBy(() -> processor.process(new GooglePubSubMessage(null, "m-1", null)))
                .isInstanceOf(BusinessException.class);
    }

    private GooglePubSubMessage message(String purchaseToken) {
        String data = base64("{\"version\":\"1.0\",\"packageName\":\"com.example.csa\","
                + "\"eventTimeMillis\":\"1700000000000\","
                + "\"subscriptionNotification\":{\"version\":\"1.0\",\"notificationType\":4,"
                + "\"purchaseToken\":\"" + purchaseToken + "\",\"subscriptionId\":\"premium_monthly\"}}");
        return new GooglePubSubMessage(data, "m-1", null);
    }

    private GooglePlaySubscriptionPurchase activePurchase(String expiry, boolean autoRenew) {
        return new GooglePlaySubscriptionPurchase(
                "SUBSCRIPTION_STATE_ACTIVE",
                "order-1",
                List.of(new GooglePlaySubscriptionLineItem(
                        "premium_monthly", expiry, new GooglePlayAutoRenewingPlan(autoRenew))));
    }

    private GooglePlaySubscriptionPurchase expiredPurchase() {
        return new GooglePlaySubscriptionPurchase(
                "SUBSCRIPTION_STATE_EXPIRED",
                "order-1",
                List.of(new GooglePlaySubscriptionLineItem(
                        "premium_monthly", "2000-01-01T00:00:00Z", new GooglePlayAutoRenewingPlan(false))));
    }

    private String base64(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}

package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleNotificationProcessorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AppleJwsTestSupport support;
    private AppleNotificationProcessor processor;

    @BeforeEach
    void setUp() throws Exception {
        support = AppleJwsTestSupport.generate();
        AppleJwsVerifier verifier = new AppleJwsVerifier(objectMapper, support.trustAnchors());
        processor = new AppleNotificationProcessor(verifier);
    }

    @Test
    void didRenewMapsToActive() throws Exception {
        String payload = notification("DID_RENEW", null,
                transaction("orig-1", expiresInDays(30)),
                renewal(1));

        SubscriptionNotificationUpdate update = processor.process(payload);

        assertThat(update.originalTransactionId()).isEqualTo("orig-1");
        assertThat(update.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(update.autoRenew()).isTrue();
        assertThat(update.autoRenewOnly()).isFalse();
        assertThat(update.currentPeriodEnd()).isNotNull();
    }

    @Test
    void expiredMapsToExpired() throws Exception {
        String payload = notification("EXPIRED", "VOLUNTARY",
                transaction("orig-2", expiresInDays(-1)),
                renewal(0));

        SubscriptionNotificationUpdate update = processor.process(payload);

        assertThat(update.status()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(update.autoRenew()).isFalse();
    }

    @Test
    void refundMapsToExpired() throws Exception {
        String payload = notification("REFUND", null,
                transaction("orig-3", expiresInDays(10)),
                renewal(1));

        SubscriptionNotificationUpdate update = processor.process(payload);

        assertThat(update.status()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void didChangeRenewalStatusIsAutoRenewOnly() throws Exception {
        String payload = notification("DID_CHANGE_RENEWAL_STATUS", "AUTO_RENEW_DISABLED",
                transaction("orig-4", expiresInDays(15)),
                renewal(0));

        SubscriptionNotificationUpdate update = processor.process(payload);

        assertThat(update.autoRenewOnly()).isTrue();
        assertThat(update.autoRenew()).isFalse();
        assertThat(update.status()).isNull();
    }

    @Test
    void didFailToRenewWithGracePeriodMapsToGrace() throws Exception {
        String payload = notification("DID_FAIL_TO_RENEW", "GRACE_PERIOD",
                transaction("orig-5", expiresInDays(2)),
                renewal(1));

        SubscriptionNotificationUpdate update = processor.process(payload);

        assertThat(update.status()).isEqualTo(SubscriptionStatus.GRACE);
    }

    @Test
    void unknownNotificationTypeMapsToNull() throws Exception {
        String payload = notification("PRICE_INCREASE", null,
                transaction("orig-6", expiresInDays(5)),
                renewal(1));

        assertThat(processor.process(payload)).isNull();
    }

    @Test
    void tamperedSignatureIsRejected() throws Exception {
        String payload = support.jwsWithBrokenSignature(notificationJson("DID_RENEW", null,
                support.signedJws(transaction("orig-7", expiresInDays(30))),
                support.signedJws(renewal(1))));

        assertThatThrownBy(() -> processor.process(payload))
                .isInstanceOf(BusinessException.class);
    }

    private String notification(String type, String subtype, String transactionJson, String renewalJson)
            throws Exception {
        return support.signedJws(notificationJson(type, subtype,
                support.signedJws(transactionJson), support.signedJws(renewalJson)));
    }

    private String notificationJson(String type, String subtype,
                                    String signedTransaction, String signedRenewal) {
        StringBuilder sb = new StringBuilder("{\"notificationType\":\"").append(type).append("\"");
        if (subtype != null) {
            sb.append(",\"subtype\":\"").append(subtype).append("\"");
        }
        sb.append(",\"signedDate\":").append(System.currentTimeMillis());
        sb.append(",\"data\":{\"environment\":\"Production\",")
                .append("\"signedTransactionInfo\":\"").append(signedTransaction).append("\",")
                .append("\"signedRenewalInfo\":\"").append(signedRenewal).append("\"}}");
        return sb.toString();
    }

    private String transaction(String originalTransactionId, long expiresDate) {
        return "{\"originalTransactionId\":\"" + originalTransactionId + "\","
                + "\"transactionId\":\"tx\",\"productId\":\"premium_monthly\","
                + "\"bundleId\":\"com.example.csa\",\"expiresDate\":" + expiresDate + ","
                + "\"environment\":\"Production\"}";
    }

    private String renewal(int autoRenewStatus) {
        return "{\"autoRenewStatus\":" + autoRenewStatus + ",\"productId\":\"premium_monthly\"}";
    }

    private long expiresInDays(int days) {
        return System.currentTimeMillis() + days * 24L * 60 * 60 * 1000;
    }
}

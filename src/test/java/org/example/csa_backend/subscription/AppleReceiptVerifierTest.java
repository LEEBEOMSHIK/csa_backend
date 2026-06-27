package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleReceiptVerifierTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppleProperties properties = new AppleProperties();
    private final RecordingGateway gateway = new RecordingGateway();

    private AppleJwsTestSupport support;
    private AppleReceiptVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        support = AppleJwsTestSupport.generate();
        properties.setBundleId("com.example.csa");
        AppleJwsVerifier jwsVerifier = new AppleJwsVerifier(objectMapper, support.trustAnchors());
        verifier = new AppleReceiptVerifier(properties, gateway, jwsVerifier);
    }

    @Test
    void verifyMapsActiveAppleSubscription() throws Exception {
        String token = support.signedJws(transactionJson("orig-1", "tx-1",
                "premium_monthly", expiresInDays(20), "Production"));
        gateway.response = statusResponse(1,
                support.signedJws(transactionJson("orig-1", "tx-2",
                        "premium_monthly", expiresInDays(20), "Production")),
                support.signedJws("{\"autoRenewStatus\":1,\"productId\":\"premium_monthly\"}"));

        VerificationResult result = verifier.verify(Platform.APPLE, token, "premium_monthly");

        assertThat(gateway.transactionId).isEqualTo("tx-1");
        assertThat(result.originalTransactionId()).isEqualTo("orig-1");
        assertThat(result.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.autoRenew()).isTrue();
        assertThat(result.environment()).isEqualTo(StoreEnvironment.PRODUCTION);
        assertThat(result.currentPeriodEnd()).isAfter(LocalDateTime.now());
    }

    @Test
    void verifyMapsGracePeriodAppleSubscription() throws Exception {
        String token = support.signedJws(transactionJson("orig-1", "tx-1",
                "premium_monthly", expiresInDays(1), "Sandbox"));
        gateway.response = statusResponse(4,
                support.signedJws(transactionJson("orig-1", "tx-2",
                        "premium_monthly", expiresInDays(1), "Sandbox")),
                support.signedJws("{\"autoRenewStatus\":0}"));

        VerificationResult result = verifier.verify(Platform.APPLE, token, "premium_monthly");

        assertThat(result.status()).isEqualTo(SubscriptionStatus.GRACE);
        assertThat(result.autoRenew()).isFalse();
        assertThat(result.environment()).isEqualTo(StoreEnvironment.SANDBOX);
    }

    @Test
    void verifyRejectsProductMismatch() throws Exception {
        String token = support.signedJws(transactionJson("orig-1", "tx-1",
                "premium_monthly", expiresInDays(20), "Production"));
        gateway.response = statusResponse(1,
                support.signedJws(transactionJson("orig-1", "tx-2",
                        "other_product", expiresInDays(20), "Production")),
                support.signedJws("{\"autoRenewStatus\":1}"));

        assertThatThrownBy(() -> verifier.verify(Platform.APPLE, token, "premium_monthly"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void verifyRejectsTamperedClientToken() throws Exception {
        String token = support.jwsWithBrokenSignature(transactionJson("orig-1", "tx-1",
                "premium_monthly", expiresInDays(20), "Production"));

        assertThatThrownBy(() -> verifier.verify(Platform.APPLE, token, "premium_monthly"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void verifyRejectsLegacyBase64Receipt() {
        assertThatThrownBy(() ->
                verifier.verify(Platform.APPLE, "bm90LWEtandz", "premium_monthly"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void verifyRejectsNonApplePlatform() {
        assertThatThrownBy(() ->
                verifier.verify(Platform.GOOGLE, "token", "premium_monthly"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void supportsOnlyApplePlatform() {
        assertThat(verifier.supports(Platform.APPLE)).isTrue();
        assertThat(verifier.supports(Platform.GOOGLE)).isFalse();
    }

    private String transactionJson(String originalTransactionId, String transactionId,
                                   String productId, long expiresDate, String environment) {
        return "{\"originalTransactionId\":\"" + originalTransactionId + "\","
                + "\"transactionId\":\"" + transactionId + "\","
                + "\"productId\":\"" + productId + "\","
                + "\"bundleId\":\"com.example.csa\","
                + "\"expiresDate\":" + expiresDate + ","
                + "\"environment\":\"" + environment + "\"}";
    }

    private long expiresInDays(int days) {
        return System.currentTimeMillis() + days * 24L * 60 * 60 * 1000;
    }

    private AppleSubscriptionStatusResponse statusResponse(int status,
                                                           String signedTransaction,
                                                           String signedRenewal) {
        AppleLastTransaction lastTransaction = new AppleLastTransaction(
                "orig-1", status, signedTransaction, signedRenewal);
        AppleSubscriptionGroup group = new AppleSubscriptionGroup(
                "group-1", List.of(lastTransaction));
        return new AppleSubscriptionStatusResponse("com.example.csa", "Production", List.of(group));
    }

    private static final class RecordingGateway implements AppleAppStoreServerGateway {
        private AppleSubscriptionStatusResponse response;
        private String transactionId;

        @Override
        public AppleSubscriptionStatusResponse getSubscriptionStatuses(String transactionId) {
            this.transactionId = transactionId;
            return response;
        }
    }
}

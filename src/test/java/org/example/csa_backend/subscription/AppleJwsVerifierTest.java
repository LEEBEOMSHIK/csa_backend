package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleJwsVerifierTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void verifyReturnsPayloadForTrustedSignedToken() throws Exception {
        AppleJwsTestSupport support = AppleJwsTestSupport.generate();
        AppleJwsVerifier verifier = new AppleJwsVerifier(objectMapper, support.trustAnchors());
        String jws = support.signedJws(
                "{\"transactionId\":\"tx-1\",\"productId\":\"premium_monthly\",\"bundleId\":\"com.example.csa\"}");

        AppleTransactionPayload payload = verifier.verify(jws, AppleTransactionPayload.class);

        assertThat(payload.transactionId()).isEqualTo("tx-1");
        assertThat(payload.productId()).isEqualTo("premium_monthly");
        assertThat(payload.bundleId()).isEqualTo("com.example.csa");
    }

    @Test
    void verifyRejectsTamperedSignature() throws Exception {
        AppleJwsTestSupport support = AppleJwsTestSupport.generate();
        AppleJwsVerifier verifier = new AppleJwsVerifier(objectMapper, support.trustAnchors());
        String jws = support.jwsWithBrokenSignature("{\"transactionId\":\"tx-1\"}");

        assertThatThrownBy(() -> verifier.verify(jws, AppleTransactionPayload.class))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void verifyRejectsUntrustedChain() throws Exception {
        AppleJwsTestSupport signer = AppleJwsTestSupport.generate();
        AppleJwsTestSupport otherTrust = AppleJwsTestSupport.generate();
        AppleJwsVerifier verifier = new AppleJwsVerifier(objectMapper, otherTrust.trustAnchors());
        String jws = signer.signedJws("{\"transactionId\":\"tx-1\"}");

        assertThatThrownBy(() -> verifier.verify(jws, AppleTransactionPayload.class))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void verifyRejectsMalformedToken() {
        AppleJwsVerifier verifier = new AppleJwsVerifier(objectMapper, Set.of());

        assertThatThrownBy(() -> verifier.verify("not-a-jws", AppleTransactionPayload.class))
                .isInstanceOf(BusinessException.class);
    }
}

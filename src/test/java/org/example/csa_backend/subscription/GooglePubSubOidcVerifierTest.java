package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GooglePubSubOidcVerifierTest {

    private static final String AUDIENCE = "https://csa.example.com/subscriptions/notifications/google";
    private static final String SA_EMAIL = "pubsub-push@csa.iam.gserviceaccount.com";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GooglePubSubOidcTestSupport support;
    private PubSubOidcProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        support = GooglePubSubOidcTestSupport.generate();
        properties = new PubSubOidcProperties();
        properties.setAudience(AUDIENCE);
        properties.setServiceAccountEmail(SA_EMAIL);
    }

    private GoogleOidcJwksClient jwks(Map<String, RSAPublicKey> keys) {
        return forceRefresh -> keys;
    }

    private GooglePubSubOidcVerifier verifier(GoogleOidcJwksClient jwksClient) {
        return new GooglePubSubOidcVerifier(objectMapper, jwksClient, properties);
    }

    private String claims(long exp) {
        return claims(AUDIENCE, SA_EMAIL, true, "https://accounts.google.com", exp);
    }

    private String claims(String aud, String email, boolean emailVerified, String iss, long exp) {
        return "{\"iss\":\"" + iss + "\",\"aud\":\"" + aud + "\",\"email\":\"" + email
                + "\",\"email_verified\":" + emailVerified + ",\"exp\":" + exp + "}";
    }

    private long futureExp() {
        return System.currentTimeMillis() / 1000L + 3600;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void acceptsValidToken() throws Exception {
        String token = support.signed(support.header(GooglePubSubOidcTestSupport.KID), claims(futureExp()));
        GooglePubSubOidcVerifier verifier =
                verifier(jwks(Map.of(GooglePubSubOidcTestSupport.KID, support.publicKey())));

        assertThatCode(() -> verifier.verify(bearer(token))).doesNotThrowAnyException();
    }

    @Test
    void rejectsBrokenSignature() throws Exception {
        String token = support.signed(support.header(GooglePubSubOidcTestSupport.KID), claims(futureExp()));
        GooglePubSubOidcVerifier verifier =
                verifier(jwks(Map.of(GooglePubSubOidcTestSupport.KID, support.publicKey())));

        assertUnauthorized(() -> verifier.verify(bearer(support.tamperSignature(token))));
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        String token = support.signed(support.header(GooglePubSubOidcTestSupport.KID),
                claims("https://attacker.example.com", SA_EMAIL, true, "https://accounts.google.com", futureExp()));
        GooglePubSubOidcVerifier verifier =
                verifier(jwks(Map.of(GooglePubSubOidcTestSupport.KID, support.publicKey())));

        assertUnauthorized(() -> verifier.verify(bearer(token)));
    }

    @Test
    void rejectsWrongServiceAccountEmail() throws Exception {
        String token = support.signed(support.header(GooglePubSubOidcTestSupport.KID),
                claims(AUDIENCE, "intruder@evil.iam.gserviceaccount.com", true,
                        "https://accounts.google.com", futureExp()));
        GooglePubSubOidcVerifier verifier =
                verifier(jwks(Map.of(GooglePubSubOidcTestSupport.KID, support.publicKey())));

        assertUnauthorized(() -> verifier.verify(bearer(token)));
    }

    @Test
    void rejectsUnverifiedEmail() throws Exception {
        String token = support.signed(support.header(GooglePubSubOidcTestSupport.KID),
                claims(AUDIENCE, SA_EMAIL, false, "https://accounts.google.com", futureExp()));
        GooglePubSubOidcVerifier verifier =
                verifier(jwks(Map.of(GooglePubSubOidcTestSupport.KID, support.publicKey())));

        assertUnauthorized(() -> verifier.verify(bearer(token)));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        long pastExp = System.currentTimeMillis() / 1000L - 3600;
        String token = support.signed(support.header(GooglePubSubOidcTestSupport.KID), claims(pastExp));
        GooglePubSubOidcVerifier verifier =
                verifier(jwks(Map.of(GooglePubSubOidcTestSupport.KID, support.publicKey())));

        assertUnauthorized(() -> verifier.verify(bearer(token)));
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        String token = support.signed(support.header(GooglePubSubOidcTestSupport.KID),
                claims(AUDIENCE, SA_EMAIL, true, "https://accounts.evil.com", futureExp()));
        GooglePubSubOidcVerifier verifier =
                verifier(jwks(Map.of(GooglePubSubOidcTestSupport.KID, support.publicKey())));

        assertUnauthorized(() -> verifier.verify(bearer(token)));
    }

    @Test
    void rejectsMissingAuthorizationHeader() {
        GooglePubSubOidcVerifier verifier =
                verifier(jwks(Map.of(GooglePubSubOidcTestSupport.KID, support.publicKey())));

        assertUnauthorized(() -> verifier.verify(null));
    }

    @Test
    void refreshesOnUnknownKidThenSucceeds() throws Exception {
        String token = support.signed(support.header(GooglePubSubOidcTestSupport.KID), claims(futureExp()));
        AtomicInteger calls = new AtomicInteger();
        GoogleOidcJwksClient jwksClient = forceRefresh -> {
            calls.incrementAndGet();
            // First (non-refresh) lookup has no matching kid; refresh provides it.
            return forceRefresh
                    ? Map.of(GooglePubSubOidcTestSupport.KID, support.publicKey())
                    : Map.of("stale-kid", support.publicKey());
        };
        GooglePubSubOidcVerifier verifier = verifier(jwksClient);

        assertThatCode(() -> verifier.verify(bearer(token))).doesNotThrowAnyException();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void rejectsUnknownKidAfterRefresh() throws Exception {
        String token = support.signed(support.header("ghost-kid"), claims(futureExp()));
        GooglePubSubOidcVerifier verifier =
                verifier(jwks(Map.of(GooglePubSubOidcTestSupport.KID, support.publicKey())));

        assertUnauthorized(() -> verifier.verify(bearer(token)));
    }

    @Test
    void rejectsWhenNotConfigured() throws Exception {
        properties.setAudience("");
        String token = support.signed(support.header(GooglePubSubOidcTestSupport.KID), claims(futureExp()));
        GooglePubSubOidcVerifier verifier =
                verifier(jwks(Map.of(GooglePubSubOidcTestSupport.KID, support.publicKey())));

        assertThatThrownBy(() -> verifier.verify(bearer(token)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    private void assertUnauthorized(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}

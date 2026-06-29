package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * Verifies the OIDC identity token that Google Cloud Pub/Sub attaches to a push request as
 * {@code Authorization: Bearer <JWT>}. This authenticates the request <em>origin</em> (the Pub/Sub
 * push subscription configured with our service account) before the RTDN body is processed.
 *
 * <p>Hand-rolled (no JWT library) to match the existing Google integration style and avoid pulling
 * a new dependency that could clash with the Jackson 3 ({@code tools.jackson}) runtime. Checks:
 * RS256 signature against Google's JWKS (by {@code kid}, refreshing on an unknown {@code kid}),
 * {@code iss} ∈ Google issuers, {@code aud} equals the configured audience, {@code email} equals the
 * configured push service-account email, {@code email_verified == true}, and {@code exp} not past
 * (with a small clock-skew allowance). Any failure throws and the notification is rejected before
 * {@link SubscriptionService#applyStoreNotification} runs. The raw token is never logged.
 */
@Component
@Profile("prod")
class GooglePubSubOidcVerifier {

    private static final Set<String> ALLOWED_ISSUERS =
            Set.of("accounts.google.com", "https://accounts.google.com");
    private static final long CLOCK_SKEW_SECONDS = 60;

    private final ObjectMapper objectMapper;
    private final GoogleOidcJwksClient jwksClient;
    private final PubSubOidcProperties properties;

    GooglePubSubOidcVerifier(ObjectMapper objectMapper,
                             GoogleOidcJwksClient jwksClient,
                             PubSubOidcProperties properties) {
        this.objectMapper = objectMapper;
        this.jwksClient = jwksClient;
        this.properties = properties;
    }

    void verify(String authorizationHeader) {
        requireConfigured();
        String token = bearerToken(authorizationHeader);

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw unauthorized("Pub/Sub OIDC 토큰 형식이 올바르지 않습니다.");
        }

        JsonNode header = decodeJson(parts[0], "헤더");
        verifyAlg(header);
        verifySignature(resolveKey(kid(header), false), parts);

        JsonNode claims = decodeJson(parts[1], "페이로드");
        verifyClaims(claims);
    }

    private void requireConfigured() {
        if (isBlank(properties.getAudience()) || isBlank(properties.getServiceAccountEmail())) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_API_ERROR, "Pub/Sub OIDC 검증 설정이 필요합니다.");
        }
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null) {
            throw unauthorized("Pub/Sub OIDC 토큰이 없습니다.");
        }
        String trimmed = authorizationHeader.trim();
        if (trimmed.length() <= 7 || !trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw unauthorized("Pub/Sub OIDC 토큰이 없습니다.");
        }
        String token = trimmed.substring(7).trim();
        if (token.isEmpty()) {
            throw unauthorized("Pub/Sub OIDC 토큰이 없습니다.");
        }
        return token;
    }

    private void verifyAlg(JsonNode header) {
        JsonNode alg = header.get("alg");
        if (alg == null || !"RS256".equals(alg.asString())) {
            throw unauthorized("Pub/Sub OIDC 토큰 알고리즘이 올바르지 않습니다.");
        }
    }

    private String kid(JsonNode header) {
        JsonNode kid = header.get("kid");
        if (kid == null || isBlank(kid.asString())) {
            throw unauthorized("Pub/Sub OIDC 토큰 키 식별자가 없습니다.");
        }
        return kid.asString();
    }

    private RSAPublicKey resolveKey(String kid, boolean forceRefresh) {
        Map<String, RSAPublicKey> keys = jwksClient.fetchKeys(forceRefresh);
        RSAPublicKey key = keys.get(kid);
        if (key == null && !forceRefresh) {
            // Unknown kid: Google may have rotated keys. Refresh once, then give up.
            keys = jwksClient.fetchKeys(true);
            key = keys.get(kid);
        }
        if (key == null) {
            throw unauthorized("Pub/Sub OIDC 토큰 키를 찾을 수 없습니다.");
        }
        return key;
    }

    private void verifySignature(RSAPublicKey publicKey, String[] parts) {
        try {
            byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
            byte[] tokenSignature = Base64.getUrlDecoder().decode(parts[2]);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(signingInput);
            if (!signature.verify(tokenSignature)) {
                throw unauthorized("Pub/Sub OIDC 토큰 서명이 유효하지 않습니다.");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw unauthorized("Pub/Sub OIDC 토큰 서명 검증에 실패했습니다.");
        }
    }

    private void verifyClaims(JsonNode claims) {
        String iss = asText(claims.get("iss"));
        if (iss == null || !ALLOWED_ISSUERS.contains(iss)) {
            throw unauthorized("Pub/Sub OIDC 토큰 발급자가 올바르지 않습니다.");
        }
        if (!properties.getAudience().equals(asText(claims.get("aud")))) {
            throw unauthorized("Pub/Sub OIDC 토큰 대상자가 올바르지 않습니다.");
        }
        if (!properties.getServiceAccountEmail().equals(asText(claims.get("email")))) {
            throw unauthorized("Pub/Sub OIDC 토큰 서비스 계정이 올바르지 않습니다.");
        }
        if (!"true".equals(asText(claims.get("email_verified")))) {
            throw unauthorized("Pub/Sub OIDC 토큰 이메일이 검증되지 않았습니다.");
        }
        long expSeconds = parseExp(asText(claims.get("exp")));
        long nowSeconds = System.currentTimeMillis() / 1000L;
        if (expSeconds + CLOCK_SKEW_SECONDS < nowSeconds) {
            throw unauthorized("Pub/Sub OIDC 토큰이 만료되었습니다.");
        }
    }

    private long parseExp(String exp) {
        if (exp == null) {
            throw unauthorized("Pub/Sub OIDC 토큰 만료 정보가 없습니다.");
        }
        try {
            return Long.parseLong(exp.trim());
        } catch (NumberFormatException e) {
            throw unauthorized("Pub/Sub OIDC 토큰 만료 정보가 올바르지 않습니다.");
        }
    }

    private JsonNode decodeJson(String segment, String part) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(segment);
            return objectMapper.readTree(decoded);
        } catch (Exception e) {
            throw unauthorized("Pub/Sub OIDC 토큰 " + part + "를 해석할 수 없습니다.");
        }
    }

    private static String asText(JsonNode node) {
        return node == null || node.isNull() ? null : node.asString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static BusinessException unauthorized(String message) {
        return new BusinessException(ErrorCode.UNAUTHORIZED, message);
    }
}

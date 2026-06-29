package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches Google's OIDC JWKS ({@code https://www.googleapis.com/oauth2/v3/certs}) and builds
 * {@link RSAPublicKey}s from the JWK {@code n}/{@code e} parameters with the JDK {@code KeyFactory}.
 *
 * <p>Keys are cached in memory and refreshed lazily: the verifier asks for a refresh only when it
 * encounters an unknown {@code kid} (Google rotates keys), avoiding a fetch per notification while
 * still picking up rotation. No secret or token is read or logged here.
 */
@Component
@Profile("prod")
class GoogleOidcJwksRestClient implements GoogleOidcJwksClient {

    private final RestClient restClient;
    private final String certsUrl;
    private final AtomicReference<Map<String, RSAPublicKey>> cache = new AtomicReference<>();

    GoogleOidcJwksRestClient(RestClient.Builder restClientBuilder, PubSubOidcProperties properties) {
        this.restClient = restClientBuilder.build();
        this.certsUrl = properties.getCertsUrl();
    }

    @Override
    public Map<String, RSAPublicKey> fetchKeys(boolean forceRefresh) {
        Map<String, RSAPublicKey> cached = cache.get();
        if (cached != null && !forceRefresh) {
            return cached;
        }
        Map<String, RSAPublicKey> keys = load();
        cache.set(keys);
        return keys;
    }

    private Map<String, RSAPublicKey> load() {
        JsonNode root;
        try {
            root = restClient.get()
                    .uri(certsUrl)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google OIDC 공개키 조회에 실패했습니다.");
        }
        JsonNode keysNode = root == null ? null : root.get("keys");
        if (keysNode == null || !keysNode.isArray() || keysNode.isEmpty()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google OIDC 공개키를 조회하지 못했습니다.");
        }
        Map<String, RSAPublicKey> keys = new LinkedHashMap<>();
        for (JsonNode jwk : keysNode) {
            JsonNode kid = jwk.get("kid");
            JsonNode n = jwk.get("n");
            JsonNode e = jwk.get("e");
            if (kid == null || n == null || e == null) {
                continue;
            }
            keys.put(kid.asString(), toRsaPublicKey(n.asString(), e.asString()));
        }
        if (keys.isEmpty()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google OIDC 공개키를 조회하지 못했습니다.");
        }
        return keys;
    }

    private RSAPublicKey toRsaPublicKey(String modulusB64Url, String exponentB64Url) {
        try {
            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(modulusB64Url));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(exponentB64Url));
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google OIDC 공개키를 해석할 수 없습니다.");
        }
    }
}

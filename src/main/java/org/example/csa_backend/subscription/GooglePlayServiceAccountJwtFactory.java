package org.example.csa_backend.subscription;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Component
@RequiredArgsConstructor
class GooglePlayServiceAccountJwtFactory {

    private static final long TOKEN_LIFETIME_SECONDS = 3600;

    private final ObjectMapper objectMapper;

    String createAssertion(GooglePlayProperties properties) {
        validate(properties);
        try {
            Instant now = Instant.now();
            Map<String, Object> header = Map.of(
                    "alg", "RS256",
                    "typ", "JWT"
            );
            Map<String, Object> payload = Map.of(
                    "iss", properties.getServiceAccountEmail(),
                    "scope", properties.getScope(),
                    "aud", properties.getTokenUrl(),
                    "iat", now.getEpochSecond(),
                    "exp", now.plusSeconds(TOKEN_LIFETIME_SECONDS).getEpochSecond()
            );
            String encodedHeader = encodeJson(header);
            String encodedPayload = encodeJson(payload);
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = sign(signingInput, properties.getServiceAccountPrivateKey());
            return signingInput + "." + signature;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google Play 인증 토큰 생성에 실패했습니다.");
        }
    }

    private void validate(GooglePlayProperties properties) {
        if (properties.getServiceAccountEmail() == null || properties.getServiceAccountEmail().isBlank()
                || properties.getServiceAccountPrivateKey() == null
                || properties.getServiceAccountPrivateKey().isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google Play 서비스 계정 설정이 필요합니다.");
        }
    }

    private String encodeJson(Map<String, Object> value) throws Exception {
        byte[] json = objectMapper.writeValueAsBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    }

    private String sign(String signingInput, String privateKeyPem) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey(privateKeyPem));
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private PrivateKey privateKey(String privateKeyPem) throws Exception {
        String normalized = privateKeyPem
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }
}

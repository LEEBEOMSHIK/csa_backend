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
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
class AppleAppStoreServerJwtFactory {

    private static final long TOKEN_LIFETIME_SECONDS = 1800;

    private final ObjectMapper objectMapper;

    String createToken(AppleProperties properties) {
        validate(properties);
        try {
            Instant now = Instant.now();
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "ES256");
            header.put("kid", properties.getKeyId());
            header.put("typ", "JWT");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("iss", properties.getIssuerId());
            payload.put("iat", now.getEpochSecond());
            payload.put("exp", now.plusSeconds(TOKEN_LIFETIME_SECONDS).getEpochSecond());
            payload.put("aud", properties.getAudience());
            payload.put("bid", properties.getBundleId());

            String signingInput = encodeJson(header) + "." + encodeJson(payload);
            String signature = sign(signingInput, properties.getPrivateKey());
            return signingInput + "." + signature;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Apple 인증 토큰 생성에 실패했습니다.");
        }
    }

    private void validate(AppleProperties properties) {
        if (properties.getIssuerId() == null || properties.getIssuerId().isBlank()
                || properties.getKeyId() == null || properties.getKeyId().isBlank()
                || properties.getPrivateKey() == null || properties.getPrivateKey().isBlank()
                || properties.getBundleId() == null || properties.getBundleId().isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Apple App Store Server 설정이 필요합니다.");
        }
    }

    private String encodeJson(Map<String, Object> value) {
        byte[] json = objectMapper.writeValueAsBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    }

    private String sign(String signingInput, String privateKeyPem) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
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
        return KeyFactory.getInstance("EC").generatePrivate(keySpec);
    }
}

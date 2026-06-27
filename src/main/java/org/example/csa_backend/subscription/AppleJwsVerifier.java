package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

class AppleJwsVerifier {

    private final ObjectMapper objectMapper;
    private final Set<TrustAnchor> trustAnchors;

    AppleJwsVerifier(ObjectMapper objectMapper, Set<TrustAnchor> trustAnchors) {
        this.objectMapper = objectMapper;
        this.trustAnchors = trustAnchors;
    }

    <T> T verify(String jws, Class<T> payloadType) {
        if (jws == null || jws.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Apple 서명 데이터가 비어 있습니다.");
        }
        String[] parts = jws.split("\\.");
        if (parts.length != 3) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Apple 서명 형식이 올바르지 않습니다.");
        }
        try {
            List<X509Certificate> chain = certificateChain(parts[0]);
            validateChain(chain);
            verifySignature(chain.get(0).getPublicKey(), parts);
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readValue(payload, payloadType);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Apple 서명 검증에 실패했습니다.");
        }
    }

    private List<X509Certificate> certificateChain(String encodedHeader) throws Exception {
        byte[] headerBytes = Base64.getUrlDecoder().decode(encodedHeader);
        JsonNode header = objectMapper.readTree(headerBytes);
        JsonNode x5c = header.get("x5c");
        if (x5c == null || !x5c.isArray() || x5c.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Apple 인증서 체인이 없습니다.");
        }
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> chain = new ArrayList<>();
        for (JsonNode node : x5c) {
            byte[] der = Base64.getDecoder().decode(node.asString());
            chain.add((X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der)));
        }
        return chain;
    }

    private void validateChain(List<X509Certificate> chain) throws Exception {
        if (trustAnchors == null || trustAnchors.isEmpty()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Apple 신뢰 인증서 설정이 필요합니다.");
        }
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> path = new ArrayList<>(chain);
        // The trust anchor (root) must not be part of the validated path.
        path.removeIf(certificate -> trustAnchors.stream()
                .anyMatch(anchor -> anchor.getTrustedCert().equals(certificate)));
        if (path.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Apple 인증서 체인이 올바르지 않습니다.");
        }
        PKIXParameters parameters = new PKIXParameters(trustAnchors);
        parameters.setRevocationEnabled(false);
        CertPathValidator validator = CertPathValidator.getInstance("PKIX");
        validator.validate(factory.generateCertPath(path), parameters);
    }

    private void verifySignature(PublicKey publicKey, String[] parts) throws Exception {
        byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
        byte[] jwsSignature = Base64.getUrlDecoder().decode(parts[2]);
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initVerify(publicKey);
        signature.update(signingInput);
        if (!signature.verify(jwsSignature)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Apple 서명이 유효하지 않습니다.");
        }
    }
}

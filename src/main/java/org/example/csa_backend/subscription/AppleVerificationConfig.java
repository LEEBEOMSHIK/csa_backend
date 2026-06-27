package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
@Profile("prod")
class AppleVerificationConfig {

    @Bean
    AppleJwsVerifier appleJwsVerifier(ObjectMapper objectMapper, AppleProperties properties) {
        return new AppleJwsVerifier(objectMapper, trustAnchors(properties));
    }

    private Set<TrustAnchor> trustAnchors(AppleProperties properties) {
        if (properties.getRootCertificates() == null || properties.getRootCertificates().isEmpty()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Apple 신뢰 인증서 설정이 필요합니다.");
        }
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            Set<TrustAnchor> anchors = new LinkedHashSet<>();
            for (String encoded : properties.getRootCertificates()) {
                byte[] der = Base64.getDecoder().decode(normalize(encoded));
                X509Certificate certificate = (X509Certificate) factory
                        .generateCertificate(new ByteArrayInputStream(der));
                anchors.add(new TrustAnchor(certificate, null));
            }
            return anchors;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Apple 신뢰 인증서를 불러오지 못했습니다.");
        }
    }

    private String normalize(String pemOrBase64) {
        return pemOrBase64
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");
    }
}

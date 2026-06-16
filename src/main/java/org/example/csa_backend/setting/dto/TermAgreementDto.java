package org.example.csa_backend.setting.dto;

import org.example.csa_backend.setting.TermAgreement;

import java.time.LocalDateTime;

public record TermAgreementDto(
        String termType,
        String termVersion,
        LocalDateTime agreedAt
) {
    public static TermAgreementDto from(TermAgreement agreement) {
        return new TermAgreementDto(
                agreement.getTermType().name(),
                agreement.getTermVersion(),
                agreement.getAgreedAt()
        );
    }
}

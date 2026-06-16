package org.example.csa_backend.setting;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;

public enum TermType {
    SERVICE,
    FINANCE,
    PRIVACY;

    public static TermType fromPath(String type) {
        if (type == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "약관 종류는 필수입니다.");
        }
        try {
            return TermType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 약관 종류입니다: " + type);
        }
    }
}

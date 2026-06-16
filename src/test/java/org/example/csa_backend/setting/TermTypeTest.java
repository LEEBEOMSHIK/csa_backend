package org.example.csa_backend.setting;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TermTypeTest {

    @Test
    void fromPathMapsLowercase() {
        assertThat(TermType.fromPath("service")).isEqualTo(TermType.SERVICE);
        assertThat(TermType.fromPath("finance")).isEqualTo(TermType.FINANCE);
        assertThat(TermType.fromPath("privacy")).isEqualTo(TermType.PRIVACY);
    }

    @Test
    void fromPathMapsUppercase() {
        assertThat(TermType.fromPath("SERVICE")).isEqualTo(TermType.SERVICE);
        assertThat(TermType.fromPath("Finance")).isEqualTo(TermType.FINANCE);
    }

    @Test
    void fromPathRejectsUnsupportedValue() {
        assertThatThrownBy(() -> TermType.fromPath("marketing"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void fromPathRejectsNull() {
        assertThatThrownBy(() -> TermType.fromPath(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }
}

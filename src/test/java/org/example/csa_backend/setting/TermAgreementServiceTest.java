package org.example.csa_backend.setting;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.setting.dto.TermAgreementDto;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TermAgreementServiceTest {

    private final TermAgreementRepository termAgreementRepository = mock(TermAgreementRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TermAgreementService service = new TermAgreementService(termAgreementRepository, userRepository);

    @Test
    void agreeInsertsFirstAgreement() {
        User user = user(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(termAgreementRepository.existsByUserAndTermTypeAndTermVersion(user, TermType.SERVICE, "v1.0"))
                .thenReturn(false);

        service.agree(5L, TermType.SERVICE, "v1.0");

        verify(termAgreementRepository, times(1)).save(any(TermAgreement.class));
    }

    @Test
    void agreeIsIdempotentForSameUserTypeVersion() {
        User user = user(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(termAgreementRepository.existsByUserAndTermTypeAndTermVersion(user, TermType.SERVICE, "v1.0"))
                .thenReturn(true);

        service.agree(5L, TermType.SERVICE, "v1.0");

        verify(termAgreementRepository, never()).save(any(TermAgreement.class));
    }

    @Test
    void agreeInsertsNewRowForRaisedVersion() {
        User user = user(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(termAgreementRepository.existsByUserAndTermTypeAndTermVersion(user, TermType.SERVICE, "v2.0"))
                .thenReturn(false);

        service.agree(5L, TermType.SERVICE, "v2.0");

        verify(termAgreementRepository, times(1)).save(any(TermAgreement.class));
    }

    @Test
    void agreeRejectsBlankVersion() {
        assertThatThrownBy(() -> service.agree(5L, TermType.SERVICE, "  "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void agreeRejectsNullVersion() {
        assertThatThrownBy(() -> service.agree(5L, TermType.SERVICE, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void agreeRejectsTooLongVersion() {
        assertThatThrownBy(() -> service.agree(5L, TermType.SERVICE, "v1234567890"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void agreeRejectsUnknownUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.agree(404L, TermType.SERVICE, "v1.0"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void getLatestAgreementsReturnsLatestPerType() {
        User user = user(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(termAgreementRepository.findFirstByUserAndTermTypeOrderByAgreedAtDesc(eq(user), eq(TermType.SERVICE)))
                .thenReturn(Optional.of(new TermAgreement(user, TermType.SERVICE, "v2.0")));
        when(termAgreementRepository.findFirstByUserAndTermTypeOrderByAgreedAtDesc(eq(user), eq(TermType.FINANCE)))
                .thenReturn(Optional.empty());
        when(termAgreementRepository.findFirstByUserAndTermTypeOrderByAgreedAtDesc(eq(user), eq(TermType.PRIVACY)))
                .thenReturn(Optional.of(new TermAgreement(user, TermType.PRIVACY, "v1.0")));

        List<TermAgreementDto> result = service.getLatestAgreements(5L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TermAgreementDto::termType)
                .containsExactly("SERVICE", "PRIVACY");
        assertThat(result).filteredOn(dto -> dto.termType().equals("SERVICE"))
                .extracting(TermAgreementDto::termVersion)
                .containsExactly("v2.0");
    }

    private User user(Long id) {
        User user = new User("user@example.com", "password");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}

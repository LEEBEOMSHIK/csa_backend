package org.example.csa_backend.setting;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.setting.dto.TermAgreementDto;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TermAgreementService {

    private final TermAgreementRepository termAgreementRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<TermAgreementDto> getLatestAgreements(Long userId) {
        User user = getUser(userId);
        List<TermAgreementDto> result = new ArrayList<>();
        for (TermType termType : TermType.values()) {
            termAgreementRepository.findFirstByUserAndTermTypeOrderByAgreedAtDesc(user, termType)
                    .map(TermAgreementDto::from)
                    .ifPresent(result::add);
        }
        return result;
    }

    @Transactional
    public void agree(Long userId, TermType termType, String termVersion) {
        if (termVersion == null || termVersion.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "약관 버전은 필수입니다.");
        }
        if (termVersion.length() > 10) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "약관 버전은 최대 10자입니다.");
        }
        User user = getUser(userId);
        if (termAgreementRepository.existsByUserAndTermTypeAndTermVersion(user, termType, termVersion)) {
            return;
        }
        termAgreementRepository.save(new TermAgreement(user, termType, termVersion));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}

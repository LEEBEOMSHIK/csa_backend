package org.example.csa_backend.admin;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.admin.dto.AdminFairytaleDto;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.common.response.PageResponse;
import org.example.csa_backend.fairytale.AiFairytale;
import org.example.csa_backend.fairytale.AiFairytaleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminFairytaleService {

    private final AiFairytaleRepository aiFairytaleRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminFairytaleDto> getFairytales(String q, String status, String shared, Pageable pageable) {
        Page<AiFairytale> fairytales = aiFairytaleRepository.searchForAdmin(
                StringUtils.hasText(q) ? q : null,
                StringUtils.hasText(status) ? status : null,
                StringUtils.hasText(shared) ? shared : null,
                pageable);
        return PageResponse.from(fairytales.map(AdminFairytaleDto::from));
    }

    @Transactional(readOnly = true)
    public AdminFairytaleDto getFairytale(Long id) {
        return AdminFairytaleDto.from(findFairytale(id));
    }

    @Transactional
    public AdminFairytaleDto unshare(Long id) {
        AiFairytale fairytale = findFairytale(id);
        fairytale.updateShared(false);
        return AdminFairytaleDto.from(fairytale);
    }

    @Transactional
    public void deleteFairytale(Long id) {
        AiFairytale fairytale = findFairytale(id);
        aiFairytaleRepository.delete(fairytale);
    }

    private AiFairytale findFairytale(Long id) {
        return aiFairytaleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "동화를 찾을 수 없습니다."));
    }
}

package org.example.csa_backend.report;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.report.dto.CreateReportRequest;
import org.example.csa_backend.report.dto.ReportDto;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    @Transactional
    public ReportDto createReport(Long reporterId, CreateReportRequest request) {
        if (!StringUtils.hasText(request.targetType()) || request.targetId() == null
                || !StringUtils.hasText(request.reason())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "신고 대상과 사유는 필수입니다.");
        }
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        Report report = new Report(reporter, request.targetType(), request.targetId(),
                request.reason(), request.detail());
        return ReportDto.from(reportRepository.save(report));
    }
}

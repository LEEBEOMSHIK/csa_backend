package org.example.csa_backend.report;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.common.response.PageResponse;
import org.example.csa_backend.report.dto.AdminReportDto;
import org.example.csa_backend.report.dto.CreateReportRequest;
import org.example.csa_backend.report.dto.ReportDto;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Transactional(readOnly = true)
    public PageResponse<AdminReportDto> getReports(String status, String targetType, Pageable pageable) {
        Page<Report> reports = reportRepository.searchForAdmin(
                StringUtils.hasText(targetType) ? targetType : null,
                StringUtils.hasText(status) ? status : null,
                pageable);
        return PageResponse.from(reports.map(AdminReportDto::from));
    }

    @Transactional(readOnly = true)
    public AdminReportDto getReport(Long id) {
        return AdminReportDto.from(findReport(id));
    }

    @Transactional
    public AdminReportDto resolveReport(Long id, String status, String adminNote, Long adminUserId) {
        if (!("RESOLVED".equals(status) || "REJECTED".equals(status))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 신고 처리 상태입니다.");
        }
        Report report = findReport(id);
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "관리자를 찾을 수 없습니다."));
        report.resolve(status, adminNote, admin);
        return AdminReportDto.from(report);
    }

    private Report findReport(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "신고 내역을 찾을 수 없습니다."));
    }
}

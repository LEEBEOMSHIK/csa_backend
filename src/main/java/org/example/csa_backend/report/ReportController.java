package org.example.csa_backend.report;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.report.dto.CreateReportRequest;
import org.example.csa_backend.report.dto.ReportDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<ReportDto> createReport(@RequestBody CreateReportRequest request,
                                                   Authentication authentication) {
        Long reporterId = currentUserId(authentication);
        return ResponseEntity.ok(reportService.createReport(reporterId, request));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}

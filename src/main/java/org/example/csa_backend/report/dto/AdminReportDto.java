package org.example.csa_backend.report.dto;

import org.example.csa_backend.report.Report;

import java.time.LocalDateTime;

public record AdminReportDto(
        Long id,
        String reporterEmail,
        String targetType,
        Long targetId,
        String reason,
        String detail,
        String status,
        String adminNote,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {
    public static AdminReportDto from(Report report) {
        String reporterEmail = report.getReporter() != null ? report.getReporter().getEmail() : null;
        return new AdminReportDto(
                report.getId(),
                reporterEmail,
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getDetail(),
                report.getStatus(),
                report.getAdminNote(),
                report.getCreDt(),
                report.getResolvedAt()
        );
    }
}

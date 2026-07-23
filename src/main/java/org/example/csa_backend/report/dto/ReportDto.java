package org.example.csa_backend.report.dto;

import org.example.csa_backend.report.Report;

public record ReportDto(
        Long id,
        String targetType,
        Long targetId,
        String reason,
        String status
) {
    public static ReportDto from(Report report) {
        return new ReportDto(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getStatus()
        );
    }
}

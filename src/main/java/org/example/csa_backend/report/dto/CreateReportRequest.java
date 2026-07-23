package org.example.csa_backend.report.dto;

public record CreateReportRequest(
        String targetType,
        Long targetId,
        String reason,
        String detail
) {
}

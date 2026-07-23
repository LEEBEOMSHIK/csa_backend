package org.example.csa_backend.report.dto;

public record ResolveReportRequest(
        String status,
        String adminNote
) {
}

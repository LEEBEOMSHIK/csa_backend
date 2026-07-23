package org.example.csa_backend.report;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.common.response.PageResponse;
import org.example.csa_backend.report.dto.AdminReportDto;
import org.example.csa_backend.report.dto.ResolveReportRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<PageResponse<AdminReportDto>> getReports(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reportService.getReports(status, targetType, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminReportDto> getReport(@PathVariable Long id) {
        return ResponseEntity.ok(reportService.getReport(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AdminReportDto> resolveReport(
            @PathVariable Long id,
            @RequestBody ResolveReportRequest request,
            Authentication authentication) {
        Long adminUserId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                reportService.resolveReport(id, request.status(), request.adminNote(), adminUserId));
    }
}

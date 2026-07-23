package org.example.csa_backend.admin;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.admin.dto.AdminFairytaleDto;
import org.example.csa_backend.common.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/fairytales")
@RequiredArgsConstructor
public class AdminFairytaleController {

    private final AdminFairytaleService adminFairytaleService;

    @GetMapping
    public ResponseEntity<PageResponse<AdminFairytaleDto>> getFairytales(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String shared,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminFairytaleService.getFairytales(q, status, shared, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminFairytaleDto> getFairytale(@PathVariable Long id) {
        return ResponseEntity.ok(adminFairytaleService.getFairytale(id));
    }

    @PatchMapping("/{id}/unshare")
    public ResponseEntity<AdminFairytaleDto> unshare(@PathVariable Long id) {
        return ResponseEntity.ok(adminFairytaleService.unshare(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFairytale(@PathVariable Long id) {
        adminFairytaleService.deleteFairytale(id);
        return ResponseEntity.noContent().build();
    }
}

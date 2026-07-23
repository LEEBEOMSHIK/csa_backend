package org.example.csa_backend.admin;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.admin.dto.AdminSubscriptionDto;
import org.example.csa_backend.common.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/subscriptions")
@RequiredArgsConstructor
public class AdminSubscriptionController {

    private final AdminSubscriptionService adminSubscriptionService;

    @GetMapping
    public ResponseEntity<PageResponse<AdminSubscriptionDto>> getSubscriptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminSubscriptionService.getSubscriptions(q, platform, status, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminSubscriptionDto> getSubscription(@PathVariable Long id) {
        return ResponseEntity.ok(adminSubscriptionService.getSubscription(id));
    }
}

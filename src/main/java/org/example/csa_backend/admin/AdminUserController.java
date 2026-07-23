package org.example.csa_backend.admin;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.admin.dto.AdminUserDetailDto;
import org.example.csa_backend.admin.dto.AdminUserDto;
import org.example.csa_backend.admin.dto.UpdateUserStatusRequest;
import org.example.csa_backend.common.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<PageResponse<AdminUserDto>> getUsers(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminUserService.getUsers(q, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserDetailDto> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.getUser(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AdminUserDto> updateStatus(
            @PathVariable Long id,
            @RequestBody UpdateUserStatusRequest request) {
        return ResponseEntity.ok(adminUserService.updateStatus(id, request.status()));
    }
}

package org.example.csa_backend.admin.dto;

import org.example.csa_backend.user.User;

import java.time.LocalDateTime;

public record AdminUserDetailDto(
        Long id,
        String email,
        String name,
        String provider,
        String role,
        String status,
        LocalDateTime createdAt
) {
    public static AdminUserDetailDto from(User user) {
        return new AdminUserDetailDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getProvider(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}

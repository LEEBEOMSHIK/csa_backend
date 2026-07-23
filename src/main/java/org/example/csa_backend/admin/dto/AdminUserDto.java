package org.example.csa_backend.admin.dto;

import org.example.csa_backend.user.User;

import java.time.LocalDateTime;

public record AdminUserDto(
        Long id,
        String email,
        String name,
        String provider,
        String role,
        String status,
        LocalDateTime createdAt
) {
    public static AdminUserDto from(User user) {
        return new AdminUserDto(
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

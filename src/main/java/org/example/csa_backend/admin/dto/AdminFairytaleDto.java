package org.example.csa_backend.admin.dto;

import org.example.csa_backend.fairytale.AiFairytale;

import java.time.LocalDateTime;

public record AdminFairytaleDto(
        Long id,
        String title,
        String ownerEmail,
        String format,
        String status,
        String language,
        boolean shared,
        int chapterCount,
        LocalDateTime createdAt
) {
    public static AdminFairytaleDto from(AiFairytale fairytale) {
        String ownerEmail = fairytale.getOwner() != null ? fairytale.getOwner().getEmail() : null;
        return new AdminFairytaleDto(
                fairytale.getId(),
                fairytale.getTitle(),
                ownerEmail,
                fairytale.getFormat(),
                fairytale.getStatus(),
                fairytale.getLanguage(),
                fairytale.isShared(),
                fairytale.getChapterCount(),
                fairytale.getCreDt()
        );
    }
}

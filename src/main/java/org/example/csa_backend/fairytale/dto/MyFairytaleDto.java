package org.example.csa_backend.fairytale.dto;

import org.example.csa_backend.fairytale.AiFairytale;
import org.example.csa_backend.fairytale.AiFairytalePage;

import java.time.LocalDateTime;
import java.util.List;

public record MyFairytaleDto(
        Long id,
        String title,
        String format,
        String status,
        String language,
        boolean shared,
        String thumbnailUrl,
        int pageCount,
        LocalDateTime createdAt,
        Long ownerId
) {
    public static MyFairytaleDto from(AiFairytale fairytale) {
        List<AiFairytalePage> pages = fairytale.getPages();
        String thumbnailUrl = pages.isEmpty() ? null : pages.get(0).getImageUrl();
        return new MyFairytaleDto(
                fairytale.getId(),
                fairytale.getTitle(),
                fairytale.getFormat(),
                fairytale.getStatus(),
                fairytale.getLanguage(),
                fairytale.isShared(),
                thumbnailUrl,
                pages.size(),
                fairytale.getCreDt(),
                fairytale.getOwner() != null ? fairytale.getOwner().getId() : null
        );
    }
}

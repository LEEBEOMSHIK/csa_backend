package org.example.csa_backend.fairytale.dto;

import org.example.csa_backend.fairytale.FairytaleDetail;

public record FairytaleDetailDto(
        String authorKo,
        String authorJa,
        String ageRange,
        int durationMin,
        int pageCount,
        String fullContentKo,
        String fullContentJa,
        boolean characterSupported,
        String characterRenderMode,
        String contentVersion
) {
    public static FairytaleDetailDto from(FairytaleDetail detail) {
        return new FairytaleDetailDto(
                detail.getAuthorKo(),
                detail.getAuthorJa(),
                detail.getAgeRange(),
                detail.getDurationMin(),
                detail.getPageCount(),
                detail.getFullContentKo(),
                detail.getFullContentJa(),
                detail.getFairytale().isCharacterSupported(),
                "LOCAL_OVERLAY",
                detail.getContentVersion()
        );
    }
}

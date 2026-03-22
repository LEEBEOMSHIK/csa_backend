package org.example.csa_backend.fairytale.dto;

import org.example.csa_backend.fairytale.FairytaleDetail;

public record FairytaleDetailDto(
        String authorKo,
        String authorJa,
        String ageRange,
        int durationMin,
        int pageCount,
        String fullContentKo,
        String fullContentJa
) {
    public static FairytaleDetailDto from(FairytaleDetail detail) {
        return new FairytaleDetailDto(
                detail.getAuthorKo(),
                detail.getAuthorJa(),
                detail.getAgeRange(),
                detail.getDurationMin(),
                detail.getPageCount(),
                detail.getFullContentKo(),
                detail.getFullContentJa()
        );
    }
}

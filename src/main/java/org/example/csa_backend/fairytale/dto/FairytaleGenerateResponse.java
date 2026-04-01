package org.example.csa_backend.fairytale.dto;

import org.example.csa_backend.fairytale.AiFairytale;
import org.example.csa_backend.fairytale.AiFairytalePage;

import java.util.List;

public record FairytaleGenerateResponse(
        Long id,
        String title,
        List<PageDto> pages
) {
    public record PageDto(int pageIndex, String text, String imageUrl, String audioUrl) {}

    public static FairytaleGenerateResponse from(AiFairytale fairytale) {
        List<PageDto> pageDtos = fairytale.getPages().stream()
                .map(p -> new PageDto(p.getPageIndex(), p.getText(), p.getImageUrl(), p.getAudioUrl()))
                .toList();
        return new FairytaleGenerateResponse(fairytale.getId(), fairytale.getTitle(), pageDtos);
    }
}

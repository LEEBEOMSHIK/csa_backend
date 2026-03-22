package org.example.csa_backend.fairytale.dto;

import org.example.csa_backend.fairytale.Fairytale;

import java.util.List;

public record FairytaleDto(
        Long id,
        String title,
        String titleJa,
        String description,
        String descriptionJa,
        Double rating,
        String colorHex,
        String themeTag,
        List<String> categories
) {
    public static FairytaleDto from(Fairytale fairytale) {
        return new FairytaleDto(
                fairytale.getId(),
                fairytale.getTitle(),
                fairytale.getTitleJa(),
                fairytale.getDescription(),
                fairytale.getDescriptionJa(),
                fairytale.getRating(),
                fairytale.getColorHex(),
                fairytale.getThemeTag(),
                fairytale.getCategories().stream()
                        .map(c -> c.getCategoryKey())
                        .toList()
        );
    }
}

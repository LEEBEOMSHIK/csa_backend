package org.example.csa_backend.fairytale.dto;

import org.example.csa_backend.fairytale.Category;

public record CategoryDto(
        String categoryKey,
        String nameKo,
        String nameJa,
        int count
) {
    public static CategoryDto from(Category category) {
        return new CategoryDto(
                category.getCategoryKey(),
                category.getNameKo(),
                category.getNameJa(),
                category.getFairytales().size()
        );
    }
}

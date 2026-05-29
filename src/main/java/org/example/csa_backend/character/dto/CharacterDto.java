package org.example.csa_backend.character.dto;

import org.example.csa_backend.character.Character;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public record CharacterDto(
        Long id,
        String name,
        List<Integer> variants,
        LocalDateTime createdAt
) {
    public static CharacterDto from(Character character) {
        String raw = character.getVariants();
        List<Integer> variants = (raw == null || raw.isBlank())
                ? List.of()
                : Arrays.stream(raw.split(","))
                        .map(String::trim)
                        .map(Integer::parseInt)
                        .toList();
        return new CharacterDto(
                character.getId(),
                character.getName(),
                variants,
                character.getCreDt()
        );
    }
}

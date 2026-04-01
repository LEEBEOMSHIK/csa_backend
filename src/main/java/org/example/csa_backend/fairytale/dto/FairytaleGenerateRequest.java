package org.example.csa_backend.fairytale.dto;

import java.util.List;

public record FairytaleGenerateRequest(
        List<String> settings,
        String genre,
        String theme,
        int chapterCount,
        boolean useCharacter,
        String voiceType,
        String language,
        String format
) {}

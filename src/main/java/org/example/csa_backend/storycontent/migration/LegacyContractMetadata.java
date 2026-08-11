package org.example.csa_backend.storycontent.migration;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record LegacyContractMetadata(
    @JsonAlias("createdAtEpochMillis") String createdAt,
    Double curatedRating,
    String curatedColorHex,
    String curatedThemeTag,
    Boolean curatedCharacterSupported,
    String curatedAuthorKo,
    String curatedAuthorJa,
    String curatedAgeRange,
    Integer curatedDurationMin,
    Integer curatedPageCount,
    String curatedFullContentKo,
    String curatedFullContentJa,
    String curatedContentVersion,
    Boolean aiShared,
    String aiVoiceType,
    String aiSettings,
    String aiGenre,
    String aiTheme,
    Integer aiChapterCount,
    Boolean curatedIsTheme,
    Boolean curatedIsNew,
    Boolean curatedIsRecommended
) {
    public LegacyContractMetadata {
        requireCreatedAt(createdAt);
        requireNonNegative(curatedDurationMin, "curatedDurationMin");
        requireNonNegative(curatedPageCount, "curatedPageCount");
        requireNonNegative(aiChapterCount, "aiChapterCount");
    }

    public LegacyContractMetadata(
        String createdAt,
        Double curatedRating,
        String curatedColorHex,
        String curatedThemeTag,
        Boolean curatedCharacterSupported,
        String curatedAuthorKo,
        String curatedAuthorJa,
        String curatedAgeRange,
        Integer curatedDurationMin,
        Integer curatedPageCount,
        String curatedFullContentKo,
        String curatedFullContentJa,
        String curatedContentVersion,
        Boolean aiShared,
        String aiVoiceType,
        String aiSettings,
        String aiGenre,
        String aiTheme,
        Integer aiChapterCount
    ) {
        this(
            createdAt, curatedRating, curatedColorHex, curatedThemeTag,
            curatedCharacterSupported, curatedAuthorKo, curatedAuthorJa, curatedAgeRange,
            curatedDurationMin, curatedPageCount, curatedFullContentKo, curatedFullContentJa,
            curatedContentVersion, aiShared, aiVoiceType, aiSettings, aiGenre, aiTheme,
            aiChapterCount, null, null, null
        );
    }

    public static LegacyContractMetadata empty() {
        return new LegacyContractMetadata(
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null
        );
    }

    public boolean curatedCharacterSupportedOrDefault() {
        return curatedCharacterSupported == null || curatedCharacterSupported;
    }

    public List<String> hashParts() {
        List<String> values = new ArrayList<>();
        values.add(createdAt);
        values.add(string(curatedRating));
        values.add(curatedColorHex);
        values.add(curatedThemeTag);
        values.add(string(curatedCharacterSupported));
        values.add(curatedAuthorKo);
        values.add(curatedAuthorJa);
        values.add(curatedAgeRange);
        values.add(string(curatedDurationMin));
        values.add(string(curatedPageCount));
        values.add(curatedFullContentKo);
        values.add(curatedFullContentJa);
        values.add(curatedContentVersion);
        values.add(string(aiShared));
        values.add(aiVoiceType);
        values.add(aiSettings);
        values.add(aiGenre);
        values.add(aiTheme);
        values.add(string(aiChapterCount));
        values.add(string(curatedIsTheme));
        values.add(string(curatedIsNew));
        values.add(string(curatedIsRecommended));
        return Collections.unmodifiableList(values);
    }

    public LegacyContractMetadata withCuratedHomeFlags(
        boolean isTheme,
        boolean isNew,
        boolean isRecommended
    ) {
        return new LegacyContractMetadata(
            createdAt, curatedRating, curatedColorHex, curatedThemeTag,
            curatedCharacterSupported, curatedAuthorKo, curatedAuthorJa, curatedAgeRange,
            curatedDurationMin, curatedPageCount, curatedFullContentKo, curatedFullContentJa,
            curatedContentVersion, aiShared, aiVoiceType, aiSettings, aiGenre, aiTheme,
            aiChapterCount, isTheme, isNew, isRecommended
        );
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static void requireCreatedAt(String value) {
        if (value == null) {
            return;
        }
        if (value.matches("-?\\d+")) {
            requireNonNegative(Long.parseLong(value), "createdAt");
            return;
        }
        LocalDateTime.parse(value);
    }

    private static void requireNonNegative(Number value, String field) {
        if (value != null && value.longValue() < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }
}

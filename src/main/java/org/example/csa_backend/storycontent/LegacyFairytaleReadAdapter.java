package org.example.csa_backend.storycontent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface LegacyFairytaleReadAdapter {

    LegacyType legacyType();

    Object readLegacy(long legacyId);

    Object readCanonical(long legacyId);

    record Snapshot(
        String legacyType,
        long legacyId,
        MigrationState migration,
        CuratedList curatedList,
        CuratedDetail curatedDetail,
        CuratedSlides curatedSlides,
        AiList aiList,
        AiSlides aiSlides,
        List<Job> jobs
    ) {
        public Snapshot {
            jobs = List.copyOf(jobs);
        }
    }

    record MigrationState(
        Long ownerUserId,
        String visibility,
        String versionStatus,
        boolean publishedPointer,
        String legacyFormat,
        String legacyStatusCode,
        String legacyLanguage,
        Map<String, List<String>> availableVoiceTypes,
        Map<String, String> defaultVoiceTypes
    ) {
        public MigrationState {
            availableVoiceTypes = immutableListMap(availableVoiceTypes);
            defaultVoiceTypes = immutableMap(defaultVoiceTypes);
        }
    }

    record CuratedList(
        long id,
        String title,
        String titleJa,
        String description,
        String descriptionJa,
        Double rating,
        String colorHex,
        String themeTag,
        List<String> categories,
        boolean characterSupported,
        Long canonicalStoryId
    ) {
        public CuratedList {
            categories = List.copyOf(categories);
        }
    }

    record CuratedDetail(
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
    }

    record CuratedSlides(
        long fairytaleId,
        String contentVersion,
        boolean characterSupported,
        String characterRenderMode,
        List<CuratedPage> pages
    ) {
        public CuratedSlides {
            pages = List.copyOf(pages);
        }
    }

    record CuratedPage(
        int pageIndex,
        String imageUrl,
        LocalizedText text,
        Map<String, Map<String, String>> audioUrls,
        CharacterPlacement characterPlacement
    ) {
        public CuratedPage {
            Map<String, Map<String, String>> copy = new LinkedHashMap<>();
            audioUrls.forEach((voice, locales) -> copy.put(voice, immutableMap(locales)));
            audioUrls = Collections.unmodifiableMap(copy);
        }
    }

    record LocalizedText(String ko, String ja) {
    }

    record CharacterPlacement(
        double x,
        double y,
        double width,
        double height,
        int zIndex,
        String pose,
        boolean flipX
    ) {
    }

    record AiList(
        long id,
        String title,
        String format,
        String status,
        String language,
        boolean shared,
        String thumbnailUrl,
        int pageCount,
        String createdAt,
        Long ownerId
    ) {
    }

    record AiSlides(
        long id,
        String title,
        String language,
        String voiceType,
        List<AiPage> pages,
        String videoUrl
    ) {
        public AiSlides {
            pages = List.copyOf(pages);
        }
    }

    record AiPage(int pageIndex, String text, String imageUrl, String audioUrl) {
    }

    record Job(String kind, String status, String errorCode) {
    }

    record MissingCanonicalSnapshot(
        String legacyType,
        long legacyId,
        boolean missingCanonical
    ) {
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static Map<String, List<String>> immutableListMap(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }
}

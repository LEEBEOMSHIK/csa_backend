package org.example.csa_backend.storycontent.migration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.csa_backend.storycontent.ContentVersionStatus;
import org.example.csa_backend.storycontent.LegacyType;
import org.example.csa_backend.storycontent.StoryOrigin;
import org.example.csa_backend.storycontent.StoryVisibility;

public record LegacyProjection(
    LegacyType legacyType,
    long legacyId,
    StoryOrigin origin,
    String originRef,
    Long ownerUserId,
    StoryVisibility visibility,
    String titleKo,
    String titleJa,
    String descriptionKo,
    String descriptionJa,
    List<String> categoryKeys,
    ContentVersionStatus versionStatus,
    boolean publishedPointer,
    String legacyFormat,
    String legacyStatusCode,
    String legacyLanguage,
    String migrationExceptionCode,
    Map<String, List<String>> availableVoiceTypes,
    Map<String, String> defaultVoiceTypes,
    List<SceneProjection> scenes,
    List<JobProjection> jobs,
    VideoProjection video,
    String sourceHash,
    LegacyContractMetadata contractMetadata
) {
    public LegacyProjection {
        categoryKeys = List.copyOf(categoryKeys);
        availableVoiceTypes = orderedListMap(availableVoiceTypes);
        defaultVoiceTypes = orderedMap(defaultVoiceTypes);
        scenes = List.copyOf(scenes);
        jobs = List.copyOf(jobs);
        contractMetadata = contractMetadata == null ? LegacyContractMetadata.empty() : contractMetadata;
    }

    public LegacyProjection(
        LegacyType legacyType,
        long legacyId,
        StoryOrigin origin,
        String originRef,
        Long ownerUserId,
        StoryVisibility visibility,
        String titleKo,
        String titleJa,
        String descriptionKo,
        String descriptionJa,
        List<String> categoryKeys,
        ContentVersionStatus versionStatus,
        boolean publishedPointer,
        String legacyFormat,
        String legacyStatusCode,
        String legacyLanguage,
        String migrationExceptionCode,
        Map<String, List<String>> availableVoiceTypes,
        Map<String, String> defaultVoiceTypes,
        List<SceneProjection> scenes,
        List<JobProjection> jobs,
        VideoProjection video,
        String sourceHash
    ) {
        this(
            legacyType,
            legacyId,
            origin,
            originRef,
            ownerUserId,
            visibility,
            titleKo,
            titleJa,
            descriptionKo,
            descriptionJa,
            categoryKeys,
            versionStatus,
            publishedPointer,
            legacyFormat,
            legacyStatusCode,
            legacyLanguage,
            migrationExceptionCode,
            availableVoiceTypes,
            defaultVoiceTypes,
            scenes,
            jobs,
            video,
            sourceHash,
            LegacyContractMetadata.empty()
        );
    }

    public LegacyProjection withSourceHash(String replacementSourceHash) {
        return new LegacyProjection(
            legacyType,
            legacyId,
            origin,
            originRef,
            ownerUserId,
            visibility,
            titleKo,
            titleJa,
            descriptionKo,
            descriptionJa,
            categoryKeys,
            versionStatus,
            publishedPointer,
            legacyFormat,
            legacyStatusCode,
            legacyLanguage,
            migrationExceptionCode,
            availableVoiceTypes,
            defaultVoiceTypes,
            scenes,
            jobs,
            video,
            replacementSourceHash,
            contractMetadata
        );
    }

    private static Map<String, List<String>> orderedListMap(Map<String, List<String>> source) {
        Map<String, List<String>> ordered = new LinkedHashMap<>();
        source.forEach((key, value) -> ordered.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(ordered);
    }

    private static <T> Map<String, T> orderedMap(Map<String, T> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    public record SceneProjection(
        String sceneKey,
        int orderIndex,
        long durationMs,
        int width,
        int height,
        Map<String, String> text,
        String imageUrl,
        List<AudioProjection> audios,
        CharacterPlacement characterPlacement
    ) {
        public SceneProjection {
            text = orderedMap(text);
            audios = List.copyOf(audios);
        }
    }

    public record AudioProjection(String voiceType, String locale, String audioUrl) {
    }

    public record CharacterPlacement(
        double x,
        double y,
        double width,
        double height,
        int zIndex,
        String pose,
        boolean flipX
    ) {
    }

    public record JobProjection(String kind, String status, String errorCode) {
    }

    public record VideoProjection(String locale, String voiceType, String videoUrl) {
    }
}

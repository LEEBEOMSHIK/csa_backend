package org.example.csa_backend.storycontent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StoredRuntimeManifest(
    int runtimeSchemaVersion,
    long storyId,
    long contentVersionId,
    String version,
    String origin,
    String rendition,
    String fallbackRendition,
    String rendererVersion,
    List<String> locales,
    String selectedLocale,
    String selectedVoiceType,
    Map<String, List<String>> availableVoiceTypes,
    Map<String, String> defaultVoiceTypes,
    List<RuntimeAsset> assets,
    List<RuntimeAudioVariant> audioVariants,
    List<RuntimeScene> scenes,
    List<RuntimeVideo> videoVariants,
    RuntimeVideo video
) {
    public StoredRuntimeManifest {
        audioVariants = audioVariants == null ? List.of() : List.copyOf(audioVariants);
        videoVariants = videoVariants == null ? List.of() : List.copyOf(videoVariants);
    }

    public JsonNode toJsonNode() {
        return new ObjectMapper().valueToTree(this);
    }

    public record RuntimeAsset(String assetKey, String kind, String url, String sha256, long sizeBytes) {
    }

    public record RuntimeScene(
        String sceneKey,
        int orderIndex,
        long durationMs,
        Map<String, String> text,
        String fallbackAssetKey,
        List<RuntimeAudioCue> audioCues,
        List<RuntimeLayer> layers,
        List<Object> tracks,
        List<Object> triggers,
        List<Object> transitions
    ) {
    }

    public record RuntimeAudioCue(String cueKey, String role, String assetKey, long startMs) {
    }

    public record RuntimeAudioVariant(String sceneKey, String cueKey, String locale, String voiceType, String assetKey) {
    }

    public record RuntimeLayer(
        String layerKey,
        String type,
        int zIndex,
        String assetKey,
        BigDecimal x,
        BigDecimal y,
        BigDecimal scaleX,
        BigDecimal scaleY,
        BigDecimal rotationDeg,
        BigDecimal opacity,
        boolean visible,
        Map<String, Object> properties
    ) {
    }

    public record RuntimeVideo(String assetKey, String locale, String voiceType, String outputMode) {
    }
}

package org.example.csa_backend.storycontent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RuntimeScene(
        String sceneKey,
        int orderIndex,
        long durationMs,
        Map<String, String> text,
        String fallbackAssetKey,
        RuntimeBackground background,
        List<RuntimeAudioCue> audioCues,
        List<RuntimeLayer> layers,
        List<RuntimeTrack> tracks,
        List<RuntimeTrigger> triggers,
        List<RuntimeTransition> transitions
    ) {
        public RuntimeScene {
            text = text == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(text));
            audioCues = audioCues == null ? List.of() : List.copyOf(audioCues);
            layers = layers == null ? List.of() : List.copyOf(layers);
            tracks = tracks == null ? List.of() : List.copyOf(tracks);
            triggers = triggers == null ? List.of() : List.copyOf(triggers);
            transitions = transitions == null ? List.of() : List.copyOf(transitions);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RuntimeAudioCue(String cueKey, String role, String assetKey, long startMs, RuntimeAudioPlayback playback) {
        public RuntimeAudioCue(String cueKey, String role, String assetKey, long startMs) {
            this(cueKey, role, assetKey, startMs, null);
        }
    }

    public record RuntimeBackground(String layerKey, String fit, boolean loop, long startOffsetMs) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RuntimeAudioPlayback(BigDecimal gainDb, boolean loop, long fadeInMs, long fadeOutMs,
                                       BigDecimal duckingDb, int maxSimultaneousInstances) {}
    public record RuntimeTrack(String trackKey, String targetLayerKey, String property, BigDecimal from, BigDecimal to,
                               long startMs, long durationMs, String easing) {}
    public record RuntimeTrigger(String triggerKey, String targetLayerKey, String event, String repeatPolicy,
                                 long videoCueMs, Map<String, String> accessibilityLabel, List<RuntimeAction> actions) {
        public RuntimeTrigger {
            accessibilityLabel = accessibilityLabel == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(accessibilityLabel));
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }
    public record RuntimeAction(String type, String trackKey, String audioCueKey, String layerKey, Boolean visible) {}
    public record RuntimeTransition(String type, Long atMs) {}

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

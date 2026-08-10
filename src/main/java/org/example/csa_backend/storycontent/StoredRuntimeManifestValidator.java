package org.example.csa_backend.storycontent;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeAsset;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeAudioCue;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeAudioVariant;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeLayer;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeScene;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeVideo;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class StoredRuntimeManifestValidator {

    private static final String UNAVAILABLE = "PUBLISHED_MANIFEST_UNAVAILABLE";
    private static final Set<String> AUDIO_ROLES = Set.of("NARRATION", "SFX", "BGM");
    private static final Set<String> LAYER_TYPES = Set.of(
        "BACKGROUND", "IMAGE", "TEXT", "SPRITE", "CHARACTER_SLOT", "SHAPE"
    );
    private static final Set<String> OUTPUT_MODES = Set.of("UPLOADED_MASTER", "GENERATED");

    public void validate(JsonNode storedJson, StoredRuntimeManifest manifest, Rendition rendition) {
        validateRawShape(storedJson);
        validateRoot(manifest, rendition);
    }

    private void validateRawShape(JsonNode root) {
        if (root == null || !root.isObject() || root.has("manifestChecksum")) {
            invalid();
        }
        requiredIntegral(root, "runtimeSchemaVersion");
        requiredIntegral(root, "storyId");
        requiredIntegral(root, "contentVersionId");
        requiredText(root, "version");
        requiredText(root, "origin");
        requiredText(root, "rendition");
        requiredText(root, "fallbackRendition");
        requiredText(root, "rendererVersion");
        requiredArray(root, "locales");
        requiredText(root, "selectedLocale");
        optionalText(root, "selectedVoiceType");
        requiredObject(root, "availableVoiceTypes");
        requiredObject(root, "defaultVoiceTypes");

        JsonNode assets = requiredArray(root, "assets");
        assets.forEach(asset -> {
            requiredText(asset, "assetKey");
            requiredText(asset, "kind");
            requiredText(asset, "url");
            requiredText(asset, "sha256");
            requiredIntegral(asset, "sizeBytes");
        });

        requiredArray(root, "audioVariants").forEach(this::validateRawAudioVariant);

        JsonNode scenes = requiredArray(root, "scenes");
        if (scenes.isEmpty()) {
            invalid();
        }
        scenes.forEach(scene -> {
            requiredText(scene, "sceneKey");
            requiredIntegral(scene, "orderIndex");
            requiredIntegral(scene, "durationMs");
            requiredObject(scene, "text");
            requiredText(scene, "fallbackAssetKey");
            requiredArray(scene, "audioCues").forEach(cue -> {
                requiredText(cue, "cueKey");
                requiredText(cue, "role");
                requiredText(cue, "assetKey");
                requiredIntegral(cue, "startMs");
            });
            requiredArray(scene, "layers").forEach(layer -> {
                requiredText(layer, "layerKey");
                requiredText(layer, "type");
                requiredIntegral(layer, "zIndex");
                optionalText(layer, "assetKey");
                requiredNumber(layer, "x");
                requiredNumber(layer, "y");
                requiredNumber(layer, "scaleX");
                requiredNumber(layer, "scaleY");
                requiredNumber(layer, "rotationDeg");
                requiredNumber(layer, "opacity");
                requiredBoolean(layer, "visible");
                requiredObject(layer, "properties");
            });
            requiredArray(scene, "tracks");
            requiredArray(scene, "triggers");
            requiredArray(scene, "transitions");
        });

        requiredArray(root, "videoVariants").forEach(this::validateRawVideo);
        JsonNode video = root.get("video");
        if (video != null && !video.isNull()) {
            validateRawVideo(video);
        }
    }

    private void validateRawVideo(JsonNode video) {
        requiredText(video, "assetKey");
        requiredText(video, "locale");
        requiredText(video, "voiceType");
        requiredText(video, "outputMode");
    }

    private void validateRawAudioVariant(JsonNode variant) {
        requiredText(variant, "sceneKey");
        requiredText(variant, "cueKey");
        requiredText(variant, "locale");
        requiredText(variant, "voiceType");
        requiredText(variant, "assetKey");
    }

    private void validateRoot(StoredRuntimeManifest manifest, Rendition rendition) {
        if (manifest == null || rendition == null
            || manifest.runtimeSchemaVersion() != 1
            || manifest.storyId() <= 0
            || manifest.contentVersionId() <= 0
            || manifest.contentVersionId() != rendition.getVersionId()
            || !positiveInteger(manifest.version())
            || !enumValue(StoryOrigin.class, manifest.origin())
            || !("SLIDE".equals(manifest.rendition()) || "VIDEO".equals(manifest.rendition()))
            || !"SLIDE".equals(manifest.fallbackRendition())
            || !positiveInteger(manifest.rendererVersion())
            || !Integer.toString(rendition.getRendererVersion()).equals(manifest.rendererVersion())) {
            invalid();
        }

        Set<String> locales = uniqueNonBlank(manifest.locales(), true);
        if (!locales.contains(manifest.selectedLocale())) {
            invalid();
        }
        validateVoices(manifest, locales);

        Map<String, RuntimeAsset> assets = validateAssets(manifest.assets());
        validateScenes(manifest.scenes(), locales, assets);
        validateAudioVariants(manifest.audioVariants(), manifest.scenes(), locales, assets);
        List<RuntimeVideo> variants = manifest.videoVariants();
        validateVideoVariants(variants, locales, manifest.availableVoiceTypes(), assets);
        validateSelectedVideo(manifest, variants, locales, assets);
    }

    private void validateVoices(StoredRuntimeManifest manifest, Set<String> locales) {
        Map<String, List<String>> available = manifest.availableVoiceTypes();
        Map<String, String> defaults = manifest.defaultVoiceTypes();
        if (available == null || defaults == null || !available.keySet().equals(locales)
            || !locales.containsAll(defaults.keySet())) {
            invalid();
        }
        available.forEach((locale, voices) -> uniqueNonBlank(voices, false));
        defaults.forEach((locale, voice) -> {
            if (blank(voice)) {
                invalid();
            }
            List<String> voices = available.get(locale);
            if (voices != null && !voices.isEmpty() && !voices.contains(voice)) {
                invalid();
            }
        });
        if (manifest.selectedVoiceType() != null) {
            if (blank(manifest.selectedVoiceType())) {
                invalid();
            }
            List<String> selectedVoices = available.get(manifest.selectedLocale());
            if (selectedVoices != null && !selectedVoices.isEmpty()
                && !selectedVoices.contains(manifest.selectedVoiceType())) {
                invalid();
            }
        }
    }

    private Map<String, RuntimeAsset> validateAssets(List<RuntimeAsset> runtimeAssets) {
        if (runtimeAssets == null || runtimeAssets.isEmpty()) {
            invalid();
        }
        Map<String, RuntimeAsset> assets = new HashMap<>();
        for (RuntimeAsset asset : runtimeAssets) {
            if (asset == null || blank(asset.assetKey()) || blank(asset.kind()) || blank(asset.url())
                || asset.sha256() == null || !asset.sha256().matches("[0-9a-f]{64}")
                || asset.sizeBytes() <= 0 || !enumValue(AssetKind.class, asset.kind())
                || assets.putIfAbsent(asset.assetKey(), asset) != null) {
                invalid();
            }
        }
        return assets;
    }

    private void validateScenes(
        List<RuntimeScene> scenes,
        Set<String> locales,
        Map<String, RuntimeAsset> assets
    ) {
        if (scenes == null || scenes.isEmpty()) {
            invalid();
        }
        Set<String> sceneKeys = new HashSet<>();
        Set<Integer> orderIndexes = new HashSet<>();
        for (RuntimeScene scene : scenes) {
            if (scene == null || blank(scene.sceneKey()) || !sceneKeys.add(scene.sceneKey())
                || scene.orderIndex() < 0 || !orderIndexes.add(scene.orderIndex())
                || scene.durationMs() <= 0 || scene.text() == null
                || !scene.text().keySet().equals(locales)
                || scene.text().entrySet().stream().anyMatch(entry -> blank(entry.getValue()))
                || !validReference(scene.fallbackAssetKey(), assets)
                || scene.audioCues() == null || scene.layers() == null
                || containsNull(scene.tracks()) || containsNull(scene.triggers())
                || containsNull(scene.transitions())) {
                invalid();
            }
            validateAudioCues(scene, assets);
            validateLayers(scene, assets);
        }
    }

    private void validateAudioCues(RuntimeScene scene, Map<String, RuntimeAsset> assets) {
        Set<String> cueKeys = new HashSet<>();
        for (RuntimeAudioCue cue : scene.audioCues()) {
            RuntimeAsset asset = cue == null ? null : assets.get(cue.assetKey());
            if (cue == null || blank(cue.cueKey()) || !cueKeys.add(cue.cueKey())
                || !AUDIO_ROLES.contains(cue.role()) || asset == null || !"AUDIO".equals(asset.kind())
                || cue.startMs() < 0 || cue.startMs() > scene.durationMs()) {
                invalid();
            }
        }
    }

    private void validateAudioVariants(
        List<RuntimeAudioVariant> variants,
        List<RuntimeScene> scenes,
        Set<String> locales,
        Map<String, RuntimeAsset> assets
    ) {
        if (variants == null) {
            invalid();
        }
        Map<String, Map<String, RuntimeAudioCue>> cuesByScene = new HashMap<>();
        for (RuntimeScene scene : scenes) {
            Map<String, RuntimeAudioCue> cues = new HashMap<>();
            for (RuntimeAudioCue cue : scene.audioCues()) {
                cues.put(cue.cueKey(), cue);
            }
            cuesByScene.put(scene.sceneKey(), cues);
        }
        Set<String> tuples = new HashSet<>();
        for (RuntimeAudioVariant variant : variants) {
            RuntimeAsset asset = variant == null ? null : assets.get(variant.assetKey());
            RuntimeAudioCue cue = variant == null ? null : cuesByScene
                .getOrDefault(variant.sceneKey(), Map.of()).get(variant.cueKey());
            if (variant == null || blank(variant.sceneKey()) || blank(variant.cueKey())
                || blank(variant.locale()) || blank(variant.voiceType()) || blank(variant.assetKey())
                || cue == null || ("NARRATION".equals(cue.role()) && !locales.contains(variant.locale()))
                || asset == null || !"AUDIO".equals(asset.kind())
                || !tuples.add(variant.sceneKey() + "\u0000" + variant.cueKey() + "\u0000"
                    + variant.locale() + "\u0000" + variant.voiceType())) {
                invalid();
            }
        }
    }

    private void validateLayers(RuntimeScene scene, Map<String, RuntimeAsset> assets) {
        Set<String> layerKeys = new HashSet<>();
        for (RuntimeLayer layer : scene.layers()) {
            if (layer == null || blank(layer.layerKey()) || !layerKeys.add(layer.layerKey())
                || !LAYER_TYPES.contains(layer.type()) || layer.zIndex() < 0
                || (layer.assetKey() != null && !validReference(layer.assetKey(), assets))
                || layer.x() == null || layer.y() == null || layer.scaleX() == null || layer.scaleY() == null
                || layer.rotationDeg() == null || layer.opacity() == null
                || layer.opacity().compareTo(BigDecimal.ZERO) < 0
                || layer.opacity().compareTo(BigDecimal.ONE) > 0 || layer.properties() == null) {
                invalid();
            }
        }
    }

    private void validateVideoVariants(
        List<RuntimeVideo> variants,
        Set<String> locales,
        Map<String, List<String>> availableVoices,
        Map<String, RuntimeAsset> assets
    ) {
        if (variants == null) {
            invalid();
        }
        Set<String> selections = new HashSet<>();
        for (RuntimeVideo variant : variants) {
            validateVideo(variant, locales, availableVoices, assets);
            if (!selections.add(variant.locale() + "\u0000" + variant.voiceType())) {
                invalid();
            }
        }
    }

    private void validateSelectedVideo(
        StoredRuntimeManifest manifest,
        List<RuntimeVideo> variants,
        Set<String> locales,
        Map<String, RuntimeAsset> assets
    ) {
        RuntimeVideo video = manifest.video();
        if (video == null) {
            if (!"SLIDE".equals(manifest.rendition())) {
                invalid();
            }
            return;
        }
        validateVideo(video, locales, manifest.availableVoiceTypes(), assets);
        if (!"VIDEO".equals(manifest.rendition()) || !variants.contains(video)
            || !video.locale().equals(manifest.selectedLocale())
            || !video.voiceType().equals(manifest.selectedVoiceType())) {
            invalid();
        }
    }

    private void validateVideo(
        RuntimeVideo video,
        Set<String> locales,
        Map<String, List<String>> availableVoices,
        Map<String, RuntimeAsset> assets
    ) {
        RuntimeAsset asset = video == null ? null : assets.get(video.assetKey());
        if (video == null || asset == null || !"VIDEO".equals(asset.kind())
            || !locales.contains(video.locale()) || blank(video.voiceType())
            || !availableVoices.getOrDefault(video.locale(), List.of()).contains(video.voiceType())
            || !OUTPUT_MODES.contains(video.outputMode())) {
            invalid();
        }
    }

    private Set<String> uniqueNonBlank(List<String> values, boolean requireNonEmpty) {
        if (values == null || (requireNonEmpty && values.isEmpty())) {
            invalid();
        }
        Set<String> unique = new HashSet<>();
        for (String value : values) {
            if (blank(value) || !unique.add(value)) {
                invalid();
            }
        }
        return unique;
    }

    private boolean validReference(String assetKey, Map<String, RuntimeAsset> assets) {
        return !blank(assetKey) && assets.containsKey(assetKey);
    }

    private boolean containsNull(List<?> values) {
        return values == null || values.stream().anyMatch(value -> value == null);
    }

    private boolean positiveInteger(String value) {
        try {
            return Integer.parseInt(value) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private <E extends Enum<E>> boolean enumValue(Class<E> type, String value) {
        try {
            Enum.valueOf(type, value);
            return true;
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }

    private JsonNode required(JsonNode object, String field) {
        if (object == null || !object.isObject()) {
            invalid();
        }
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            invalid();
        }
        return value;
    }

    private JsonNode requiredArray(JsonNode object, String field) {
        JsonNode value = required(object, field);
        if (!value.isArray()) {
            invalid();
        }
        return value;
    }

    private JsonNode requiredObject(JsonNode object, String field) {
        JsonNode value = required(object, field);
        if (!value.isObject()) {
            invalid();
        }
        return value;
    }

    private void requiredText(JsonNode object, String field) {
        JsonNode value = required(object, field);
        if (!value.isTextual() || blank(value.asText())) {
            invalid();
        }
    }

    private void optionalText(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        if (value != null && !value.isNull() && (!value.isTextual() || blank(value.asText()))) {
            invalid();
        }
    }

    private void requiredIntegral(JsonNode object, String field) {
        if (!required(object, field).isIntegralNumber()) {
            invalid();
        }
    }

    private void requiredNumber(JsonNode object, String field) {
        if (!required(object, field).isNumber()) {
            invalid();
        }
    }

    private void requiredBoolean(JsonNode object, String field) {
        if (!required(object, field).isBoolean()) {
            invalid();
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void invalid() {
        throw StoryRuntimeException.unavailable(UNAVAILABLE);
    }
}

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
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeAction;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeAudioVariant;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeBackground;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeLayer;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeScene;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeTrack;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeTransition;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeTrigger;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeVideo;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class StoredRuntimeManifestValidator {

    private static final String UNAVAILABLE = "PUBLISHED_MANIFEST_UNAVAILABLE";
    private static final String CORRUPT = "PUBLISHED_MANIFEST_CORRUPT";
    private static final Set<String> AUDIO_ROLES = Set.of("NARRATION", "SFX", "BGM");
    private static final Set<String> LAYER_TYPES = Set.of(
        "BACKGROUND", "IMAGE", "TEXT", "SPRITE", "CHARACTER_SLOT", "SHAPE"
    );
    private static final Set<String> OUTPUT_MODES = Set.of("UPLOADED_MASTER", "GENERATED");
    private static final Set<String> TRACK_PROPERTIES = Set.of(
        "TRANSLATE_X", "TRANSLATE_Y", "SCALE_X", "SCALE_Y", "ROTATION", "OPACITY", "SHAKE"
    );
    private static final Set<String> EASINGS = Set.of("LINEAR", "EASE_IN", "EASE_OUT", "EASE_IN_OUT");
    private static final Set<String> EVENTS = Set.of("TAP");
    private static final Set<String> REPEAT_POLICIES = Set.of(
        "ONCE_PER_CONTENT_VERSION", "ONCE_PER_SESSION", "REPEATABLE"
    );
    private static final Set<String> ACTION_TYPES = Set.of("PLAY_TRACK", "PLAY_AUDIO", "SET_VISIBILITY");
    private static final Set<String> TRANSITIONS = Set.of(
        "USER_NEXT", "NARRATION_END", "TIMED_NEXT", "STORY_END"
    );

    public void validate(JsonNode storedJson, StoredRuntimeManifest manifest, Rendition rendition) {
        try {
            validateRawShape(storedJson);
            validateRoot(manifest, rendition);
        } catch (StoryRuntimeException exception) {
            if (manifest != null && manifest.runtimeSchemaVersion() == 2) {
                throw StoryRuntimeException.unavailable(CORRUPT);
            }
            throw exception;
        }
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
            if (root.path("runtimeSchemaVersion").asInt() == 2) {
                validateRawInteractiveScene(scene);
            }
        });

        requiredArray(root, "videoVariants").forEach(this::validateRawVideo);
        JsonNode video = root.get("video");
        if (video != null && !video.isNull()) {
            validateRawVideo(video);
        }
    }

    private void validateRawInteractiveScene(JsonNode scene) {
        JsonNode background = scene.get("background");
        if (background != null && !background.isNull()) {
            if (!background.isObject()) {
                invalid();
            }
            requiredText(background, "layerKey");
            requiredText(background, "fit");
            requiredBoolean(background, "loop");
            requiredIntegral(background, "startOffsetMs");
        }
        requiredArray(scene, "tracks").forEach(track -> {
            requiredText(track, "trackKey");
            requiredText(track, "targetLayerKey");
            requiredText(track, "property");
            requiredNumber(track, "from");
            requiredNumber(track, "to");
            requiredIntegral(track, "startMs");
            requiredIntegral(track, "durationMs");
            requiredText(track, "easing");
        });
        requiredArray(scene, "triggers").forEach(trigger -> {
            requiredText(trigger, "triggerKey");
            requiredText(trigger, "targetLayerKey");
            requiredText(trigger, "event");
            requiredText(trigger, "repeatPolicy");
            requiredIntegral(trigger, "videoCueMs");
            requiredObject(trigger, "accessibilityLabel");
            requiredArray(trigger, "actions");
        });
        requiredArray(scene, "transitions").forEach(transition -> requiredText(transition, "type"));
        requiredArray(scene, "audioCues").forEach(cue -> {
            JsonNode playback = cue.get("playback");
            if (playback != null && !playback.isNull()) {
                if (!playback.isObject()) {
                    invalid();
                }
                requiredNumber(playback, "gainDb");
                requiredBoolean(playback, "loop");
                requiredIntegral(playback, "fadeInMs");
                requiredIntegral(playback, "fadeOutMs");
                requiredIntegral(playback, "maxSimultaneousInstances");
            }
        });
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
            || (manifest.runtimeSchemaVersion() != 1 && manifest.runtimeSchemaVersion() != 2)
            || manifest.storyId() <= 0
            || manifest.contentVersionId() <= 0
            || manifest.contentVersionId() != rendition.getVersionId()
            || !positiveInteger(manifest.version())
            || !enumValue(StoryOrigin.class, manifest.origin())
            || !("SLIDE".equals(manifest.rendition()) || "VIDEO".equals(manifest.rendition())
                || "INTERACTIVE".equals(manifest.rendition()))
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
        if (manifest.runtimeSchemaVersion() == 1) {
            requireV1Scenes(manifest.scenes());
        } else {
            validateInteractiveScenes(manifest.scenes(), locales, assets);
        }
        validateAudioVariants(manifest.audioVariants(), manifest.scenes(), locales, assets);
        List<RuntimeVideo> variants = manifest.videoVariants();
        validateVideoVariants(variants, locales, manifest.availableVoiceTypes(), assets);
        validateSelectedVideo(manifest, variants, locales, assets);
    }

    private void requireV1Scenes(List<RuntimeScene> scenes) {
        for (RuntimeScene scene : scenes) {
            if (scene.background() != null || !scene.tracks().isEmpty() || !scene.triggers().isEmpty()
                || !scene.transitions().isEmpty()
                || scene.audioCues().stream().anyMatch(cue -> cue.playback() != null)) {
                invalid();
            }
        }
    }

    private void validateInteractiveScenes(
        List<RuntimeScene> scenes, Set<String> locales, Map<String, RuntimeAsset> assets
    ) {
        for (RuntimeScene scene : scenes) {
            Map<String, RuntimeLayer> layers = new HashMap<>();
            for (RuntimeLayer layer : scene.layers()) {
                layers.put(layer.layerKey(), layer);
            }
            Map<String, RuntimeAudioCue> cues = new HashMap<>();
            for (RuntimeAudioCue cue : scene.audioCues()) {
                cues.put(cue.cueKey(), cue);
                if (cue.playback() != null) {
                    validateInteractivePlayback(cue, scene.durationMs());
                }
            }
            RuntimeBackground background = scene.background();
            if (background != null) {
                RuntimeLayer backgroundLayer = layers.get(background.layerKey());
                RuntimeAsset backgroundAsset = backgroundLayer == null ? null : assets.get(backgroundLayer.assetKey());
                RuntimeAsset fallbackAsset = assets.get(scene.fallbackAssetKey());
                if (backgroundLayer == null || background.startOffsetMs() < 0
                    || !Set.of("COVER", "CONTAIN").contains(background.fit())
                    || !"BACKGROUND".equals(backgroundLayer.type()) || backgroundAsset == null
                    || !Set.of("IMAGE", "VIDEO").contains(backgroundAsset.kind())
                    || ("VIDEO".equals(backgroundAsset.kind())
                        && (fallbackAsset == null || !"IMAGE".equals(fallbackAsset.kind())))) {
                    invalid();
                }
            }
            Map<String, RuntimeTrack> tracks = new HashMap<>();
            for (RuntimeTrack track : scene.tracks()) {
                if (blank(track.trackKey()) || tracks.putIfAbsent(track.trackKey(), track) != null
                    || !layers.containsKey(track.targetLayerKey()) || !TRACK_PROPERTIES.contains(track.property())
                    || track.from() == null || track.to() == null || track.startMs() < 0
                    || track.durationMs() < 0 || track.startMs() > scene.durationMs()
                    || track.durationMs() > scene.durationMs() - track.startMs()
                    || !EASINGS.contains(track.easing())) {
                    invalid();
                }
            }
            Set<String> triggerKeys = new HashSet<>();
            for (RuntimeTrigger trigger : scene.triggers()) {
                if (blank(trigger.triggerKey()) || !triggerKeys.add(trigger.triggerKey())
                    || !layers.containsKey(trigger.targetLayerKey()) || !EVENTS.contains(trigger.event())
                    || !REPEAT_POLICIES.contains(trigger.repeatPolicy()) || trigger.videoCueMs() < 0
                    || trigger.videoCueMs() > scene.durationMs() || trigger.accessibilityLabel() == null
                    || !trigger.accessibilityLabel().keySet().equals(locales)
                    || trigger.accessibilityLabel().values().stream().anyMatch(this::blank)
                    || trigger.actions() == null || trigger.actions().isEmpty()) {
                    invalid();
                }
                for (RuntimeAction action : trigger.actions()) {
                    validateAction(action, tracks, cues, layers);
                }
            }
            for (RuntimeTransition transition : scene.transitions()) {
                boolean timed = "TIMED_NEXT".equals(transition.type());
                if (!TRANSITIONS.contains(transition.type()) || (timed && transition.atMs() == null)
                    || (!timed && transition.atMs() != null) || (transition.atMs() != null
                    && (transition.atMs() < 0 || transition.atMs() > scene.durationMs()))) {
                    invalid();
                }
            }
        }
    }

    private void validateInteractivePlayback(RuntimeAudioCue cue, long sceneDurationMs) {
        var playback = cue.playback();
        if (playback == null || playback.gainDb() == null
            || playback.gainDb().compareTo(new BigDecimal("-60")) < 0
            || playback.gainDb().compareTo(new BigDecimal("6")) > 0 || playback.fadeInMs() < 0
            || playback.fadeOutMs() < 0 || playback.fadeInMs() > sceneDurationMs
            || playback.fadeOutMs() > sceneDurationMs
            || playback.fadeInMs() + playback.fadeOutMs() > sceneDurationMs
            || playback.maxSimultaneousInstances() < 1
            || playback.maxSimultaneousInstances() > 4) {
            invalid();
        }
        if ("BGM".equals(cue.role())) {
            if (!playback.loop() || playback.duckingDb() == null
                || playback.duckingDb().compareTo(new BigDecimal("12")) < 0
                || playback.duckingDb().compareTo(new BigDecimal("18")) > 0) {
                invalid();
            }
        } else if (playback.loop() || playback.duckingDb() != null) {
            invalid();
        }
    }

    private void validateAction(
        RuntimeAction action,
        Map<String, RuntimeTrack> tracks,
        Map<String, RuntimeAudioCue> cues,
        Map<String, RuntimeLayer> layers
    ) {
        if (action == null || !ACTION_TYPES.contains(action.type())) {
            invalid();
        }
        if ("PLAY_TRACK".equals(action.type()) && !tracks.containsKey(action.trackKey())) {
            invalid();
        }
        if ("PLAY_AUDIO".equals(action.type()) && !cues.containsKey(action.audioCueKey())) {
            invalid();
        }
        if ("SET_VISIBILITY".equals(action.type())
            && (!layers.containsKey(action.layerKey()) || action.visible() == null)) {
            invalid();
        }
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
            if (!("SLIDE".equals(manifest.rendition()) || "INTERACTIVE".equals(manifest.rendition()))) {
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

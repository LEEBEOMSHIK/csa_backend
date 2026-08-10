package org.example.csa_backend.storycontent;

import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

final class TestAggregateFactory {

    private TestAggregateFactory() {
    }

    static ContentVersionAggregate aggregate(
        ContentVersionStatus status,
        long sourceRevision,
        List<Scene> scenes,
        List<SceneLocalizedContent> localizedContents,
        List<Asset> assets,
        List<Layer> layers,
        List<AudioCue> audioCues,
        List<AudioVariant> audioVariants,
        List<VersionLocale> locales,
        List<Rendition> renditions,
        List<RenditionVariant> renditionVariants
    ) {
        return new ContentVersionAggregate(
            version(status, sourceRevision),
            scenes,
            localizedContents,
            assets,
            layers,
            audioCues,
            audioVariants,
            locales,
            renditions,
            renditionVariants
        );
    }

    static ContentVersion version(ContentVersionStatus status, long sourceRevision) {
        ContentVersion version = new ContentVersion();
        set(version, "storyId", 41L);
        set(version, "versionNo", 1);
        set(version, "status", status);
        set(version, "schemaVersion", 1);
        set(version, "sourceRevision", sourceRevision);
        return version;
    }

    static Scene scene(Long id, String sceneKey, Long fallbackAssetId) {
        Scene scene = new Scene();
        set(scene, "id", id);
        set(scene, "sceneKey", sceneKey);
        set(scene, "fallbackAssetId", fallbackAssetId);
        return scene;
    }

    static SceneLocalizedContent localized(Long sceneId, String locale, String displayText) {
        SceneLocalizedContent content = new SceneLocalizedContent();
        set(content, "sceneId", sceneId);
        set(content, "locale", locale);
        set(content, "displayText", displayText);
        return content;
    }

    static Asset asset(Long id, AssetKind kind, AssetStatus status) {
        Asset asset = new Asset();
        set(asset, "id", id);
        set(asset, "kind", kind);
        set(asset, "status", status);
        return asset;
    }

    static Layer layer(Long id, String layerKey, Long assetId) {
        Layer layer = new Layer();
        set(layer, "id", id);
        set(layer, "layerKey", layerKey);
        set(layer, "assetId", assetId);
        return layer;
    }

    static AudioCue narrationCue(Long id, String cueKey, boolean required) {
        AudioCue cue = new AudioCue();
        set(cue, "id", id);
        set(cue, "cueKey", cueKey);
        set(cue, "role", AudioRole.NARRATION);
        set(cue, "required", required);
        return cue;
    }

    static AudioVariant audioVariant(
        Long cueId,
        String locale,
        String voiceType,
        Long assetId,
        AudioVariantStatus status
    ) {
        AudioVariant variant = new AudioVariant();
        set(variant, "audioCueId", cueId);
        set(variant, "locale", locale);
        set(variant, "voiceType", voiceType);
        set(variant, "assetId", assetId);
        set(variant, "status", status);
        return variant;
    }

    static VersionLocale locale(String locale, String defaultVoiceType) {
        VersionLocale versionLocale = new VersionLocale();
        set(versionLocale, "locale", locale);
        set(versionLocale, "defaultVoiceType", defaultVoiceType);
        return versionLocale;
    }

    static Rendition rendition(
        Long id,
        RenditionType type,
        RenditionStatus status,
        boolean compatibilityFallback,
        Long manifestAssetId
    ) {
        Rendition rendition = new Rendition();
        set(rendition, "id", id);
        set(rendition, "type", type);
        set(rendition, "status", status);
        set(rendition, "compatibilityFallback", compatibilityFallback);
        set(rendition, "manifestAssetId", manifestAssetId);
        return rendition;
    }

    static RenditionVariant renditionVariant(
        Long id,
        Long renditionId,
        String locale,
        String voiceType,
        Long outputAssetId,
        RenditionStatus status,
        long sourceRevision
    ) {
        RenditionVariant variant = new RenditionVariant();
        set(variant, "id", id);
        set(variant, "renditionId", renditionId);
        set(variant, "locale", locale);
        set(variant, "voiceType", voiceType);
        set(variant, "outputAssetId", outputAssetId);
        set(variant, "status", status);
        set(variant, "sourceRevision", sourceRevision);
        return variant;
    }

    static void set(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }
}

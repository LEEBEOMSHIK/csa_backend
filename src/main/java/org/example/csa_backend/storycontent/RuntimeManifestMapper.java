package org.example.csa_backend.storycontent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeAudioCue;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeAudioVariant;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeScene;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeVideo;
import org.example.csa_backend.storycontent.dto.StoryRuntimeManifestResponse;
import org.springframework.stereotype.Component;

@Component
public class RuntimeManifestMapper {

    public StoryRuntimeManifestResponse flat(StoredRuntimeManifest manifest, String checksum) {
        return StoryRuntimeManifestResponse.flat(manifest, checksum);
    }

    public StoryRuntimeManifestResponse selected(
        StoredRuntimeManifest manifest,
        String checksum,
        String rendition,
        RuntimeVideo video,
        String selectedLocale,
        String selectedVoiceType
    ) {
        return new StoryRuntimeManifestResponse(
            manifest.runtimeSchemaVersion(), manifest.storyId(), manifest.contentVersionId(), manifest.version(),
            manifest.origin(), rendition, manifest.fallbackRendition(), manifest.rendererVersion(), checksum,
            manifest.locales(), selectedLocale, selectedVoiceType, manifest.availableVoiceTypes(),
            manifest.defaultVoiceTypes(), manifest.assets(), manifest.audioVariants(),
            projectAudio(manifest.scenes(), manifest.audioVariants(), selectedLocale, selectedVoiceType),
            manifest.videoVariants(), video
        );
    }

    private List<RuntimeScene> projectAudio(
        List<RuntimeScene> scenes,
        List<RuntimeAudioVariant> variants,
        String locale,
        String voiceType
    ) {
        Map<String, String> variantAssets = new HashMap<>();
        for (RuntimeAudioVariant variant : variants) {
            variantAssets.put(tuple(variant.sceneKey(), variant.cueKey(), variant.locale(), variant.voiceType()),
                variant.assetKey());
        }
        return scenes.stream().map(scene -> new RuntimeScene(
            scene.sceneKey(), scene.orderIndex(), scene.durationMs(), scene.text(), scene.fallbackAssetKey(),
            scene.audioCues().stream().map(cue -> projectCue(scene.sceneKey(), cue, variantAssets, locale, voiceType))
                .toList(),
            scene.layers(), scene.tracks(), scene.triggers(), scene.transitions()
        )).toList();
    }

    private RuntimeAudioCue projectCue(
        String sceneKey,
        RuntimeAudioCue cue,
        Map<String, String> variantAssets,
        String locale,
        String voiceType
    ) {
        String assetKey = cue.assetKey();
        if ("NARRATION".equals(cue.role())) {
            assetKey = requiredVariant(variantAssets, sceneKey, cue.cueKey(), locale, voiceType);
        } else {
            assetKey = variantAssets.getOrDefault(tuple(sceneKey, cue.cueKey(), "und", "none"), assetKey);
        }
        return new RuntimeAudioCue(cue.cueKey(), cue.role(), assetKey, cue.startMs());
    }

    private String requiredVariant(
        Map<String, String> variantAssets,
        String sceneKey,
        String cueKey,
        String locale,
        String voiceType
    ) {
        String assetKey = variantAssets.get(tuple(sceneKey, cueKey, locale, voiceType));
        if (assetKey == null) {
            throw StoryRuntimeException.unavailable("PUBLISHED_MANIFEST_UNAVAILABLE");
        }
        return assetKey;
    }

    private String tuple(String sceneKey, String cueKey, String locale, String voiceType) {
        return sceneKey + "\u0000" + cueKey + "\u0000" + locale + "\u0000" + voiceType;
    }
}

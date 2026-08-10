package org.example.csa_backend.storycontent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeAsset;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeAudioVariant;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeScene;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeVideo;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StoryRuntimeManifestResponse(
    int runtimeSchemaVersion,
    long storyId,
    long contentVersionId,
    String version,
    String origin,
    String rendition,
    String fallbackRendition,
    String rendererVersion,
    String manifestChecksum,
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
    public StoryRuntimeManifestResponse {
        audioVariants = audioVariants == null ? List.of() : List.copyOf(audioVariants);
        videoVariants = videoVariants == null ? List.of() : List.copyOf(videoVariants);
    }

    public static StoryRuntimeManifestResponse flat(StoredRuntimeManifest manifest, String checksum) {
        return new StoryRuntimeManifestResponse(
            manifest.runtimeSchemaVersion(), manifest.storyId(), manifest.contentVersionId(), manifest.version(),
            manifest.origin(), manifest.rendition(), manifest.fallbackRendition(), manifest.rendererVersion(), checksum,
            manifest.locales(), manifest.selectedLocale(), manifest.selectedVoiceType(), manifest.availableVoiceTypes(),
            manifest.defaultVoiceTypes(), manifest.assets(), manifest.audioVariants(), manifest.scenes(),
            manifest.videoVariants(), manifest.video()
        );
    }
}

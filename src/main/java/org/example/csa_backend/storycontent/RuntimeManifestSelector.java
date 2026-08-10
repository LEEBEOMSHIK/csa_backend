package org.example.csa_backend.storycontent;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.example.csa_backend.storycontent.dto.RuntimeCapabilities;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest.RuntimeVideo;
import org.example.csa_backend.storycontent.dto.StoryRuntimeManifestResponse;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RuntimeManifestSelector {

    private static final String UNAVAILABLE = "PUBLISHED_MANIFEST_UNAVAILABLE";

    private final RuntimeManifestMapper mapper;

    public StoryRuntimeManifestResponse select(
        VerifiedStoredManifest verified,
        RuntimeCapabilities capabilities
    ) {
        if (verified == null || verified.manifest() == null || capabilities == null) {
            throw StoryRuntimeException.unavailable(UNAVAILABLE);
        }
        StoredRuntimeManifest manifest = verified.manifest();
        if (!capabilities.supportsSchema(manifest.runtimeSchemaVersion())
            || !rendererSupported(manifest.rendererVersion(), capabilities.rendererVersion())
            || manifest.locales() == null
            || !manifest.locales().contains(capabilities.locale())) {
            throw StoryRuntimeException.unavailable(UNAVAILABLE);
        }

        String selectedVoiceType = selectedVoiceType(manifest, capabilities);
        RuntimeVideo video = selectVideo(manifest, capabilities, selectedVoiceType);
        if (video != null) {
            return mapper.selected(manifest, verified.storedBytesSha256(), "VIDEO", video,
                capabilities.locale(), selectedVoiceType);
        }
        if (capabilities.supportsRendition("SLIDE")) {
            return mapper.selected(manifest, verified.storedBytesSha256(), "SLIDE", null,
                capabilities.locale(), selectedVoiceType);
        }
        throw StoryRuntimeException.unavailable(UNAVAILABLE);
    }

    private String selectedVoiceType(StoredRuntimeManifest manifest, RuntimeCapabilities capabilities) {
        boolean narrationRequired = manifest.scenes().stream()
            .flatMap(scene -> scene.audioCues().stream())
            .anyMatch(cue -> "NARRATION".equals(cue.role()));
        if (!narrationRequired) {
            if (capabilities.voiceType() != null) {
                throw StoryRuntimeException.unavailable(UNAVAILABLE);
            }
            return null;
        }
        String voiceType = capabilities.voiceType() == null
            ? manifest.defaultVoiceTypes().get(capabilities.locale())
            : capabilities.voiceType();
        if (voiceType == null || !manifest.availableVoiceTypes()
            .getOrDefault(capabilities.locale(), java.util.List.of()).contains(voiceType)) {
            throw StoryRuntimeException.unavailable(UNAVAILABLE);
        }
        return voiceType;
    }

    private RuntimeVideo selectVideo(
        StoredRuntimeManifest manifest,
        RuntimeCapabilities capabilities,
        String selectedVoiceType
    ) {
        if (!"VIDEO".equals(capabilities.rendition()) || !capabilities.supportsRendition("VIDEO")) {
            return null;
        }
        return manifest.videoVariants().stream()
            .filter(variant -> capabilities.locale().equals(variant.locale()))
            .filter(variant -> Objects.equals(selectedVoiceType, variant.voiceType()))
            .findFirst()
            .orElse(null);
    }

    private boolean rendererSupported(String storedRendererVersion, int supportedRendererVersion) {
        try {
            return Integer.parseInt(storedRendererVersion) <= supportedRendererVersion;
        } catch (NumberFormatException exception) {
            throw StoryRuntimeException.unavailable(UNAVAILABLE, exception);
        }
    }
}

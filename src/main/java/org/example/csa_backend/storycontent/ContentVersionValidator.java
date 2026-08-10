package org.example.csa_backend.storycontent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ContentVersionValidator {

    public void assertDraft(ContentVersion version) {
        version.assertDraft();
    }

    public ValidationResult validateForReview(ContentVersionAggregate aggregate) {
        if (aggregate.version().getStatus() == ContentVersionStatus.DRAFT) {
            return new ValidationResult(List.of());
        }
        return new ValidationResult(List.of(new ValidationError("status", "DRAFT_REQUIRED")));
    }

    public ValidationResult validateForPublish(ContentVersionAggregate aggregate) {
        List<ValidationError> errors = new ArrayList<>();
        if (aggregate.version().getStatus() != ContentVersionStatus.APPROVED) {
            errors.add(new ValidationError("status", "APPROVED_REQUIRED"));
        }
        for (Scene scene : aggregate.scenesInOrder()) {
            if (!aggregate.isReadyImage(scene.getFallbackAssetId())) {
                errors.add(new ValidationError(
                    "scenes." + scene.getSceneKey() + ".fallbackAssetId",
                    "FALLBACK_ASSET_NOT_READY"
                ));
            }
            for (String locale : List.of("ko", "ja")) {
                if (aggregate.localized(scene.getId(), locale)
                    .map(SceneLocalizedContent::getDisplayText)
                    .orElse("")
                    .isBlank()) {
                    errors.add(new ValidationError(
                        "scenes." + scene.getSceneKey() + "." + locale + ".displayText",
                        locale.equals("ko") ? "KO_TEXT_REQUIRED" : "JA_TEXT_REQUIRED"
                    ));
                }
            }
        }
        for (Layer layer : aggregate.layersInOrder()) {
            if (layer.getAssetId() != null && !aggregate.isReadyAsset(layer.getAssetId())) {
                errors.add(new ValidationError(
                    "layers." + layer.getLayerKey() + ".assetId",
                    "STALE_ASSET_REFERENCE"
                ));
            }
        }
        long compatibilitySlides = aggregate.renditions().stream()
            .filter(rendition -> rendition.getType() == RenditionType.SLIDE)
            .filter(rendition -> rendition.getStatus() == RenditionStatus.READY)
            .filter(Rendition::isCompatibilityFallback)
            .count();
        if (compatibilitySlides != 1) {
            errors.add(new ValidationError("renditions.SLIDE", "COMPATIBILITY_SLIDE_REQUIRED"));
        }
        for (Rendition rendition : aggregate.renditions()) {
            if (rendition.getStatus() != RenditionStatus.READY
                && rendition.getStatus() != RenditionStatus.DISABLED) {
                errors.add(new ValidationError(
                    "renditions." + rendition.getType() + ".status",
                    "RENDITION_NOT_READY"
                ));
            }
        }
        for (RenditionVariant variant : aggregate.renditionVariants()) {
            String path = "renditionVariants." + variant.getRenditionId()
                + "." + variant.getLocale() + "." + variant.getVoiceType();
            if (variant.getStatus() != RenditionStatus.READY
                && variant.getStatus() != RenditionStatus.DISABLED) {
                errors.add(new ValidationError(path + ".status", "RENDITION_NOT_READY"));
            }
            if (variant.getSourceRevision() != aggregate.version().getSourceRevision()) {
                errors.add(new ValidationError(path + ".sourceRevision", "STALE_ASSET_REFERENCE"));
            } else if (!aggregate.isReadyAsset(variant.getOutputAssetId())) {
                errors.add(new ValidationError(path + ".outputAssetId", "STALE_ASSET_REFERENCE"));
            }
        }
        for (VersionLocale locale : aggregate.locales()) {
            Set<String> available = availableVoiceTypes(aggregate, locale.getLocale());
            if (aggregate.hasRequiredNarration(locale.getLocale()) && available.isEmpty()) {
                errors.add(new ValidationError(
                    "locales." + locale.getLocale() + ".availableVoiceTypes",
                    "REQUIRED_NARRATION_VOICE_UNAVAILABLE"
                ));
            }
            if (aggregate.hasRequiredNarration(locale.getLocale())
                && locale.getDefaultVoiceType() == null) {
                errors.add(new ValidationError(
                    "locales." + locale.getLocale() + ".defaultVoiceType",
                    "DEFAULT_VOICE_NOT_AVAILABLE"
                ));
            }
            if (locale.getDefaultVoiceType() != null
                && !available.contains(locale.getDefaultVoiceType())) {
                errors.add(new ValidationError(
                    "locales." + locale.getLocale() + ".defaultVoiceType",
                    "DEFAULT_VOICE_NOT_AVAILABLE"
                ));
            }
        }
        return new ValidationResult(errors);
    }

    public Set<String> availableVoiceTypes(ContentVersionAggregate aggregate, String locale) {
        List<AudioCue> requiredNarration = aggregate.audioCues().stream()
            .filter(AudioCue::isRequired)
            .filter(cue -> cue.getRole() == AudioRole.NARRATION)
            .toList();
        if (requiredNarration.isEmpty()) {
            return Set.of();
        }

        Set<String> available = null;
        for (AudioCue cue : requiredNarration) {
            Set<String> readyForCue = aggregate.audioVariants().stream()
                .filter(variant -> Objects.equals(variant.getAudioCueId(), cue.getId()))
                .filter(variant -> locale.equals(variant.getLocale()))
                .filter(variant -> variant.getStatus() == AudioVariantStatus.READY)
                .map(AudioVariant::getVoiceType)
                .collect(Collectors.toSet());
            available = available == null ? readyForCue : intersection(available, readyForCue);
        }
        return available == null ? Set.of() : Set.copyOf(available);
    }

    private Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.retainAll(right);
        return result;
    }
}

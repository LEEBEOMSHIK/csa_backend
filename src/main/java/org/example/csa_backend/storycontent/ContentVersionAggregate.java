package org.example.csa_backend.storycontent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ContentVersionAggregate(
    ContentVersion version,
    List<Scene> scenesInOrder,
    List<SceneLocalizedContent> localizedContents,
    List<Asset> assets,
    List<Layer> layersInOrder,
    List<AudioCue> audioCues,
    List<AudioVariant> audioVariants,
    List<VersionLocale> locales,
    List<Rendition> renditions,
    List<RenditionVariant> renditionVariants
) {

    public ContentVersionAggregate {
        Objects.requireNonNull(version);
        scenesInOrder = List.copyOf(scenesInOrder);
        localizedContents = List.copyOf(localizedContents);
        assets = List.copyOf(assets);
        layersInOrder = List.copyOf(layersInOrder);
        audioCues = List.copyOf(audioCues);
        audioVariants = List.copyOf(audioVariants);
        locales = List.copyOf(locales);
        renditions = List.copyOf(renditions);
        renditionVariants = List.copyOf(renditionVariants);
    }

    public Optional<SceneLocalizedContent> localized(Long sceneId, String locale) {
        return localizedContents.stream()
            .filter(content -> Objects.equals(content.getSceneId(), sceneId))
            .filter(content -> locale.equals(content.getLocale()))
            .findFirst();
    }

    public boolean isReadyImage(Long assetId) {
        return assets.stream()
            .anyMatch(asset -> Objects.equals(asset.getId(), assetId)
                && asset.getKind() == AssetKind.IMAGE
                && asset.getStatus() == AssetStatus.READY);
    }

    public boolean isReadyAsset(Long assetId) {
        return assets.stream()
            .anyMatch(asset -> Objects.equals(asset.getId(), assetId)
                && asset.getStatus() == AssetStatus.READY);
    }

    public boolean hasRequiredNarration(String locale) {
        return audioCues.stream()
            .anyMatch(cue -> cue.isRequired() && cue.getRole() == AudioRole.NARRATION);
    }

    public ContentVersionAggregate withScene(Scene scene) {
        requireDraft();
        return copyWith(append(scenesInOrder, scene), localizedContents, layersInOrder, audioCues,
            audioVariants, renditions, renditionVariants);
    }

    public ContentVersionAggregate withLocalizedContent(SceneLocalizedContent content) {
        requireDraft();
        return copyWith(scenesInOrder, append(localizedContents, content), layersInOrder, audioCues,
            audioVariants, renditions, renditionVariants);
    }

    public ContentVersionAggregate withLayer(Layer layer) {
        requireDraft();
        return copyWith(scenesInOrder, localizedContents, append(layersInOrder, layer), audioCues,
            audioVariants, renditions, renditionVariants);
    }

    public ContentVersionAggregate withAudioCue(AudioCue cue) {
        requireDraft();
        return copyWith(scenesInOrder, localizedContents, layersInOrder, append(audioCues, cue),
            audioVariants, renditions, renditionVariants);
    }

    public ContentVersionAggregate withAudioVariant(AudioVariant variant) {
        requireDraft();
        return copyWith(scenesInOrder, localizedContents, layersInOrder, audioCues,
            append(audioVariants, variant), renditions, renditionVariants);
    }

    public ContentVersionAggregate withRendition(Rendition rendition) {
        requireDraft();
        return copyWith(scenesInOrder, localizedContents, layersInOrder, audioCues,
            audioVariants, append(renditions, rendition), renditionVariants);
    }

    public ContentVersionAggregate withRenditionVariant(RenditionVariant variant) {
        requireDraft();
        return copyWith(scenesInOrder, localizedContents, layersInOrder, audioCues,
            audioVariants, renditions, append(renditionVariants, variant));
    }

    public ContentVersionAggregate forkAsDraft(int versionNo, Long actorId, Instant now) {
        ContentVersion draft = version.forkAsDraft(versionNo, actorId, now);
        return new ContentVersionAggregate(
            draft,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    private void requireDraft() {
        version.assertDraft();
    }

    private ContentVersionAggregate copyWith(
        List<Scene> scenes,
        List<SceneLocalizedContent> localized,
        List<Layer> layers,
        List<AudioCue> cues,
        List<AudioVariant> variants,
        List<Rendition> renditionList,
        List<RenditionVariant> renditionVariantList
    ) {
        return new ContentVersionAggregate(
            version,
            scenes,
            localized,
            assets,
            layers,
            cues,
            variants,
            locales,
            renditionList,
            renditionVariantList
        );
    }

    private static <T> List<T> append(List<T> values, T value) {
        List<T> copy = new ArrayList<>(values);
        copy.add(Objects.requireNonNull(value));
        return List.copyOf(copy);
    }
}

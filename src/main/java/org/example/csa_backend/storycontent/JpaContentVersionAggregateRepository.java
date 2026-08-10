package org.example.csa_backend.storycontent;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaContentVersionAggregateRepository implements ContentVersionAggregateRepository {

    private final ContentVersionRepository contentVersionRepository;
    private final VersionLocaleRepository versionLocaleRepository;
    private final SceneRepository sceneRepository;
    private final SceneLocalizedContentRepository localizedContentRepository;
    private final LayerRepository layerRepository;
    private final AssetRepository assetRepository;
    private final AudioCueRepository audioCueRepository;
    private final AudioVariantRepository audioVariantRepository;
    private final RenditionRepository renditionRepository;
    private final RenditionVariantRepository renditionVariantRepository;

    public JpaContentVersionAggregateRepository(
        ContentVersionRepository contentVersionRepository,
        VersionLocaleRepository versionLocaleRepository,
        SceneRepository sceneRepository,
        SceneLocalizedContentRepository localizedContentRepository,
        LayerRepository layerRepository,
        AssetRepository assetRepository,
        AudioCueRepository audioCueRepository,
        AudioVariantRepository audioVariantRepository,
        RenditionRepository renditionRepository,
        RenditionVariantRepository renditionVariantRepository
    ) {
        this.contentVersionRepository = contentVersionRepository;
        this.versionLocaleRepository = versionLocaleRepository;
        this.sceneRepository = sceneRepository;
        this.localizedContentRepository = localizedContentRepository;
        this.layerRepository = layerRepository;
        this.assetRepository = assetRepository;
        this.audioCueRepository = audioCueRepository;
        this.audioVariantRepository = audioVariantRepository;
        this.renditionRepository = renditionRepository;
        this.renditionVariantRepository = renditionVariantRepository;
    }

    @Override
    public Optional<ContentVersionAggregate> findForPublish(Long versionId) {
        return contentVersionRepository.findById(versionId).map(this::load);
    }

    @Override
    public Optional<ContentVersionAggregate> findPublished(Long versionId) {
        return contentVersionRepository.findById(versionId)
            .filter(version -> version.getStatus() == ContentVersionStatus.PUBLISHED)
            .map(this::load);
    }

    private ContentVersionAggregate load(ContentVersion version) {
        Long versionId = version.getId();
        List<Scene> scenes = sceneRepository.findByVersionIdOrderByOrderIndexAscIdAsc(versionId);
        List<Long> sceneIds = scenes.stream().map(Scene::getId).toList();
        List<SceneLocalizedContent> localized = sceneIds.isEmpty()
            ? List.of()
            : localizedContentRepository.findBySceneIdInOrderBySceneIdAscLocaleAsc(sceneIds);
        List<Layer> layers = sceneIds.isEmpty()
            ? List.of()
            : layerRepository.findBySceneIdInOrder(sceneIds);
        List<AudioCue> cues = sceneIds.isEmpty()
            ? List.of()
            : audioCueRepository.findBySceneIdInOrderBySceneIdAscIdAsc(sceneIds);
        List<Long> cueIds = cues.stream().map(AudioCue::getId).toList();
        List<AudioVariant> audioVariants = cueIds.isEmpty()
            ? List.of()
            : audioVariantRepository.findByAudioCueIdInOrderByAudioCueIdAscIdAsc(cueIds);
        List<Rendition> renditions = renditionRepository.findByVersionIdOrderByIdAsc(versionId);
        List<Long> renditionIds = renditions.stream().map(Rendition::getId).toList();
        List<RenditionVariant> renditionVariants = renditionIds.isEmpty()
            ? List.of()
            : renditionVariantRepository.findByRenditionIdInOrderByRenditionIdAscIdAsc(renditionIds);

        return new ContentVersionAggregate(
            version,
            scenes,
            localized,
            assetRepository.findByOwnerVersionIdOrderByIdAsc(versionId),
            layers,
            cues,
            audioVariants,
            versionLocaleRepository.findByVersionIdOrderByIdAsc(versionId),
            renditions,
            renditionVariants
        );
    }
}

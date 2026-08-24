package org.example.csa_backend.storycontent;

import java.io.IOException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PublishedManifestReader {

    private static final String UNAVAILABLE = "PUBLISHED_MANIFEST_UNAVAILABLE";
    private static final String CORRUPT = "PUBLISHED_MANIFEST_CORRUPT";

    private final AssetRepository assetRepository;
    private final PublishedMediaStorage mediaStorage;
    private final Sha256Digest sha256;
    private final ObjectMapper objectMapper;
    private final StoredRuntimeManifestValidator manifestValidator;

    public VerifiedStoredManifest readAndVerify(Rendition rendition) {
        try {
            validateRendition(rendition);
            Asset manifestAsset = assetRepository.findById(rendition.getManifestAssetId())
                .filter(asset -> validManifestAsset(asset, rendition))
                .orElseThrow(() -> StoryRuntimeException.unavailable(UNAVAILABLE));
            byte[] bytes = mediaStorage.read(manifestAsset.getStorageKey());
            if (bytes == null || bytes.length == 0) {
                throw StoryRuntimeException.unavailable(UNAVAILABLE);
            }
            String actualChecksum = sha256.hex(bytes);
            if (!actualChecksum.equals(rendition.getChecksum())) {
                throw StoryRuntimeException.unavailable(UNAVAILABLE);
            }
            JsonNode storedJson;
            StoredRuntimeManifest manifest;
            try {
                storedJson = objectMapper.readTree(bytes);
                manifest = objectMapper.treeToValue(storedJson, StoredRuntimeManifest.class);
            } catch (RuntimeException exception) {
                throw StoryRuntimeException.unavailable(CORRUPT, exception);
            }
            manifestValidator.validate(storedJson, manifest, rendition);
            return new VerifiedStoredManifest(manifest, actualChecksum);
        } catch (StoryRuntimeException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw StoryRuntimeException.unavailable(UNAVAILABLE, exception);
        }
    }

    private void validateRendition(Rendition rendition) {
        if (rendition == null || rendition.getStatus() != RenditionStatus.READY
            || rendition.getManifestAssetId() == null || rendition.getChecksum() == null
            || !rendition.getChecksum().matches("[0-9a-f]{64}")) {
            throw StoryRuntimeException.unavailable(UNAVAILABLE);
        }
    }

    private boolean validManifestAsset(Asset asset, Rendition rendition) {
        return asset.getStatus() == AssetStatus.READY
            && asset.getKind() == AssetKind.MANIFEST
            && Objects.equals(asset.getOwnerVersionId(), rendition.getVersionId())
            && asset.getStorageKey() != null
            && !asset.getStorageKey().isBlank();
    }

}

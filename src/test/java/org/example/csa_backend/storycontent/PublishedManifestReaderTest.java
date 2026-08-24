package org.example.csa_backend.storycontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PublishedManifestReaderTest {

    private static final Long VERSION_ID = 17L;
    private static final Long MANIFEST_ASSET_ID = 501L;
    private static final String STORAGE_KEY = "stories/101/versions/17/manifest.json";

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private PublishedMediaStorage mediaStorage;

    private Sha256Digest sha256;
    private ObjectMapper objectMapper;
    private PublishedManifestReader reader;

    @BeforeEach
    void setUp() {
        sha256 = new Sha256Digest();
        objectMapper = new ObjectMapper();
        reader = new PublishedManifestReader(
            assetRepository, mediaStorage, sha256, objectMapper, new StoredRuntimeManifestValidator()
        );
    }

    @Test
    void readsExactImmutableBytesAndReturnsTheirSha256() throws Exception {
        byte[] storedBytes = fixture("story-runtime-v1-static-slide-stored.json");
        String checksum = sha256.hex(storedBytes);
        Rendition rendition = rendition(checksum);
        when(assetRepository.findById(MANIFEST_ASSET_ID)).thenReturn(Optional.of(manifestAsset()));
        when(mediaStorage.read(STORAGE_KEY)).thenReturn(storedBytes);

        VerifiedStoredManifest verified = reader.readAndVerify(rendition);

        assertThat(verified.storedBytesSha256()).isEqualTo(checksum);
        assertThat(verified.manifest().storyId()).isEqualTo(101L);
        assertThat(verified.manifest().contentVersionId()).isEqualTo(VERSION_ID);
        verify(mediaStorage).read(STORAGE_KEY);
    }

    @Test
    void checksumMismatchReturnsStableUnavailable() throws Exception {
        byte[] storedBytes = fixture("story-runtime-v1-static-slide-stored.json");
        Rendition rendition = rendition("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
        when(assetRepository.findById(MANIFEST_ASSET_ID)).thenReturn(Optional.of(manifestAsset()));
        when(mediaStorage.read(STORAGE_KEY)).thenReturn(storedBytes);

        assertUnavailable(() -> reader.readAndVerify(rendition));
    }

    @Test
    void missingManifestAssetReturnsStableUnavailable() {
        Rendition rendition = rendition("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        when(assetRepository.findById(MANIFEST_ASSET_ID)).thenReturn(Optional.empty());

        assertUnavailable(() -> reader.readAndVerify(rendition));
    }

    @Test
    void nonReadyOrCrossVersionManifestAssetReturnsStableUnavailable() {
        Rendition rendition = rendition("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        Asset asset = manifestAsset();
        ReflectionTestUtils.setField(asset, "ownerVersionId", 18L);
        when(assetRepository.findById(MANIFEST_ASSET_ID)).thenReturn(Optional.of(asset));

        assertUnavailable(() -> reader.readAndVerify(rendition));
    }

    @Test
    void missingStoredBytesReturnsStableUnavailable() throws Exception {
        Rendition rendition = rendition("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        when(assetRepository.findById(MANIFEST_ASSET_ID)).thenReturn(Optional.of(manifestAsset()));
        when(mediaStorage.read(STORAGE_KEY)).thenThrow(new IOException("missing"));

        assertUnavailable(() -> reader.readAndVerify(rendition));
    }

    @Test
    void malformedStoredBytesReturnStableCorrupt() throws Exception {
        byte[] malformed = "not-json".getBytes(StandardCharsets.UTF_8);
        Rendition rendition = rendition(sha256.hex(malformed));
        when(assetRepository.findById(MANIFEST_ASSET_ID)).thenReturn(Optional.of(manifestAsset()));
        when(mediaStorage.read(STORAGE_KEY)).thenReturn(malformed);

        assertCorrupt(() -> reader.readAndVerify(rendition));
    }

    @Test
    void v2PlaybackPrimitiveCorruptionReturnsStableCorruptThroughReader() throws Exception {
        for (Consumer<tools.jackson.databind.node.ObjectNode> mutation : List.<Consumer<tools.jackson.databind.node.ObjectNode>>of(
            root -> playback(root).remove("loop"),
            root -> playback(root).remove("fadeOutMs"),
            root -> {
                playback(root).put("fadeInMs", 5000);
                playback(root).put("fadeOutMs", 5000);
            }
        )) {
            tools.jackson.databind.node.ObjectNode corrupt = storedFixture("story-runtime-v2-interactive-stored.json");
            mutation.accept(corrupt);
            byte[] bytes = objectMapper.writeValueAsBytes(corrupt);
            Rendition rendition = rendition(sha256.hex(bytes));
            set(rendition, "type", RenditionType.INTERACTIVE);
            set(rendition, "rendererVersion", 2);
            when(assetRepository.findById(MANIFEST_ASSET_ID)).thenReturn(Optional.of(manifestAsset()));
            when(mediaStorage.read(STORAGE_KEY)).thenReturn(bytes);

            assertCorrupt(() -> reader.readAndVerify(rendition));
        }
    }

    @Test
    void missingAlwaysPresentVideoVariantsReturnsStableUnavailable() throws Exception {
        byte[] storedBytes = fixture("story-runtime-v1-static-slide-stored.json");
        tools.jackson.databind.node.ObjectNode corrupt = (tools.jackson.databind.node.ObjectNode)
            objectMapper.readTree(storedBytes);
        corrupt.remove("videoVariants");
        byte[] corruptBytes = objectMapper.writeValueAsBytes(corrupt);
        Rendition rendition = rendition(sha256.hex(corruptBytes));
        when(assetRepository.findById(MANIFEST_ASSET_ID)).thenReturn(Optional.of(manifestAsset()));
        when(mediaStorage.read(STORAGE_KEY)).thenReturn(corruptBytes);

        assertUnavailable(() -> reader.readAndVerify(rendition));
    }

    @Test
    void missingAlwaysPresentAudioVariantsReturnsStableUnavailable() throws Exception {
        tools.jackson.databind.node.ObjectNode corrupt = storedFixture("story-runtime-v1-static-slide-stored.json");
        corrupt.remove("audioVariants");

        assertCorruptUnavailable(corrupt);
    }

    @Test
    void nullAudioVariantElementReturnsStableCorrupt() throws Exception {
        tools.jackson.databind.node.ObjectNode corrupt = storedFixture("story-runtime-v1-static-slide-stored.json");
        corrupt.putArray("audioVariants").addNull();

        assertCorruptReader(corrupt);
    }

    @Test
    void audioVariantWithMissingAssetReferenceReturnsStableUnavailable() throws Exception {
        tools.jackson.databind.node.ObjectNode corrupt = storedFixture("story-runtime-v1-static-slide-stored.json");
        tools.jackson.databind.node.ObjectNode variant = corrupt.putArray("audioVariants").addObject();
        variant.put("sceneKey", "scene-1");
        variant.put("cueKey", "narration");
        variant.put("locale", "ko");
        variant.put("voiceType", "mom");
        variant.put("assetKey", "asset-missing");

        assertCorruptUnavailable(corrupt);
    }

    @Test
    void duplicateAudioVariantTupleReturnsStableUnavailable() throws Exception {
        tools.jackson.databind.node.ObjectNode corrupt = storedFixture("story-runtime-v1-static-slide-stored.json");
        tools.jackson.databind.node.ArrayNode variants = (tools.jackson.databind.node.ArrayNode) corrupt
            .get("audioVariants");
        variants.add(variants.get(0).deepCopy());

        assertCorruptUnavailable(corrupt);
    }

    @Test
    void emptyCompatibilityScenesReturnStableUnavailable() throws Exception {
        byte[] storedBytes = fixture("story-runtime-v1-static-slide-stored.json");
        tools.jackson.databind.node.ObjectNode corrupt = (tools.jackson.databind.node.ObjectNode)
            objectMapper.readTree(storedBytes);
        corrupt.putArray("scenes");
        byte[] corruptBytes = objectMapper.writeValueAsBytes(corrupt);
        Rendition rendition = rendition(sha256.hex(corruptBytes));
        when(assetRepository.findById(MANIFEST_ASSET_ID)).thenReturn(Optional.of(manifestAsset()));
        when(mediaStorage.read(STORAGE_KEY)).thenReturn(corruptBytes);

        assertUnavailable(() -> reader.readAndVerify(rendition));
    }

    @Test
    void nullSceneElementReturnsStableUnavailable() throws Exception {
        byte[] storedBytes = fixture("story-runtime-v1-static-slide-stored.json");
        tools.jackson.databind.node.ObjectNode corrupt = (tools.jackson.databind.node.ObjectNode)
            objectMapper.readTree(storedBytes);
        ((tools.jackson.databind.node.ArrayNode) corrupt.get("scenes")).set(0, objectMapper.nullNode());
        byte[] corruptBytes = objectMapper.writeValueAsBytes(corrupt);
        Rendition rendition = rendition(sha256.hex(corruptBytes));
        when(assetRepository.findById(MANIFEST_ASSET_ID)).thenReturn(Optional.of(manifestAsset()));
        when(mediaStorage.read(STORAGE_KEY)).thenReturn(corruptBytes);

        assertUnavailable(() -> reader.readAndVerify(rendition));
    }

    @Test
    void nullVideoVariantElementReturnsStableCorrupt() throws Exception {
        byte[] storedBytes = fixture("story-runtime-v1-uploaded-video-stored.json");
        tools.jackson.databind.node.ObjectNode corrupt = (tools.jackson.databind.node.ObjectNode)
            objectMapper.readTree(storedBytes);
        ((tools.jackson.databind.node.ArrayNode) corrupt.get("videoVariants"))
            .set(0, objectMapper.nullNode());
        byte[] corruptBytes = objectMapper.writeValueAsBytes(corrupt);
        Rendition rendition = rendition(sha256.hex(corruptBytes));
        when(assetRepository.findById(MANIFEST_ASSET_ID)).thenReturn(Optional.of(manifestAsset()));
        when(mediaStorage.read(STORAGE_KEY)).thenReturn(corruptBytes);

        assertCorrupt(() -> reader.readAndVerify(rendition));
    }

    @Test
    void duplicateAssetKeyReturnsStableUnavailable() throws Exception {
        tools.jackson.databind.node.ObjectNode corrupt = storedFixture("story-runtime-v1-static-slide-stored.json");
        tools.jackson.databind.node.ArrayNode assets = (tools.jackson.databind.node.ArrayNode) corrupt.get("assets");
        assets.add(assets.get(0).deepCopy());

        assertCorruptUnavailable(corrupt);
    }

    @Test
    void missingAudioAssetReferenceReturnsStableUnavailable() throws Exception {
        tools.jackson.databind.node.ObjectNode corrupt = storedFixture("story-runtime-v1-static-slide-stored.json");
        tools.jackson.databind.node.ObjectNode scene = (tools.jackson.databind.node.ObjectNode) corrupt
            .path("scenes").path(0);
        ((tools.jackson.databind.node.ObjectNode) scene.path("audioCues").path(0))
            .put("assetKey", "asset-missing");

        assertCorruptUnavailable(corrupt);
    }

    @Test
    void missingNestedRequiredListReturnsStableUnavailable() throws Exception {
        tools.jackson.databind.node.ObjectNode corrupt = storedFixture("story-runtime-v1-static-slide-stored.json");
        ((tools.jackson.databind.node.ObjectNode) corrupt.path("scenes").path(0)).remove("audioCues");

        assertCorruptUnavailable(corrupt);
    }

    @Test
    void incompleteLayerReturnsStableUnavailable() throws Exception {
        tools.jackson.databind.node.ObjectNode corrupt = storedFixture("story-runtime-v1-static-slide-stored.json");
        tools.jackson.databind.node.ObjectNode layer = objectMapper.createObjectNode();
        layer.put("layerKey", "foreground");
        layer.put("type", "IMAGE");
        layer.put("zIndex", 0);
        layer.put("assetKey", "asset-201");
        layer.put("visible", true);
        ((tools.jackson.databind.node.ArrayNode) corrupt.path("scenes").path(0).path("layers")).add(layer);

        assertCorruptUnavailable(corrupt);
    }

    @Test
    void missingVideoAssetReferenceReturnsStableUnavailable() throws Exception {
        tools.jackson.databind.node.ObjectNode corrupt = storedFixture("story-runtime-v1-uploaded-video-stored.json");
        ((tools.jackson.databind.node.ObjectNode) corrupt.path("videoVariants").path(0))
            .put("assetKey", "asset-missing");

        assertCorruptUnavailable(corrupt);
    }

    @Test
    void blankRequiredManifestScalarReturnsStableUnavailable() throws Exception {
        tools.jackson.databind.node.ObjectNode corrupt = storedFixture("story-runtime-v1-static-slide-stored.json");
        corrupt.put("version", " ");

        assertCorruptUnavailable(corrupt);
    }

    private void assertUnavailable(ThrowingCall call) {
        assertThatThrownBy(call::run)
            .isInstanceOfSatisfying(StoryRuntimeException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(exception.getCode()).isEqualTo("PUBLISHED_MANIFEST_UNAVAILABLE");
            });
    }

    private void assertCorrupt(ThrowingCall call) {
        assertThatThrownBy(call::run)
            .isInstanceOfSatisfying(StoryRuntimeException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(exception.getCode()).isEqualTo("PUBLISHED_MANIFEST_CORRUPT");
            });
    }

    private void assertCorruptUnavailable(tools.jackson.databind.node.ObjectNode corrupt) throws Exception {
        byte[] corruptBytes = objectMapper.writeValueAsBytes(corrupt);
        Rendition rendition = rendition(sha256.hex(corruptBytes));
        when(assetRepository.findById(MANIFEST_ASSET_ID)).thenReturn(Optional.of(manifestAsset()));
        when(mediaStorage.read(STORAGE_KEY)).thenReturn(corruptBytes);
        assertUnavailable(() -> reader.readAndVerify(rendition));
    }

    private void assertCorruptReader(tools.jackson.databind.node.ObjectNode corrupt) throws Exception {
        byte[] corruptBytes = objectMapper.writeValueAsBytes(corrupt);
        Rendition rendition = rendition(sha256.hex(corruptBytes));
        when(assetRepository.findById(MANIFEST_ASSET_ID)).thenReturn(Optional.of(manifestAsset()));
        when(mediaStorage.read(STORAGE_KEY)).thenReturn(corruptBytes);
        assertCorrupt(() -> reader.readAndVerify(rendition));
    }

    private Rendition rendition(String checksum) {
        Rendition rendition = new Rendition();
        set(rendition, "id", 71L);
        set(rendition, "versionId", VERSION_ID);
        set(rendition, "type", RenditionType.SLIDE);
        set(rendition, "status", RenditionStatus.READY);
        set(rendition, "manifestAssetId", MANIFEST_ASSET_ID);
        set(rendition, "rendererVersion", 1);
        set(rendition, "checksum", checksum);
        set(rendition, "compatibilityFallback", true);
        return rendition;
    }

    private Asset manifestAsset() {
        Asset asset = new Asset();
        set(asset, "id", MANIFEST_ASSET_ID);
        set(asset, "ownerVersionId", VERSION_ID);
        set(asset, "kind", AssetKind.MANIFEST);
        set(asset, "storageKey", STORAGE_KEY);
        set(asset, "publicUrl", "https://cdn.example/" + STORAGE_KEY);
        set(asset, "sha256", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        set(asset, "actualMimeType", "application/json");
        set(asset, "byteSize", 1L);
        set(asset, "status", AssetStatus.READY);
        return asset;
    }

    private byte[] fixture(String name) throws Exception {
        return Files.readString(Path.of("src/test/resources/contracts", name), StandardCharsets.UTF_8)
            .trim()
            .getBytes(StandardCharsets.UTF_8);
    }

    private tools.jackson.databind.node.ObjectNode storedFixture(String name) throws Exception {
        return (tools.jackson.databind.node.ObjectNode) objectMapper.readTree(fixture(name));
    }

    private static tools.jackson.databind.node.ObjectNode playback(tools.jackson.databind.node.ObjectNode root) {
        return (tools.jackson.databind.node.ObjectNode) root.path("scenes").path(0)
            .path("audioCues").path(0).path("playback");
    }

    private void set(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}

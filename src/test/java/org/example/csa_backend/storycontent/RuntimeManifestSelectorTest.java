package org.example.csa_backend.storycontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.example.csa_backend.storycontent.dto.RuntimeCapabilities;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest;
import org.example.csa_backend.storycontent.dto.StoryRuntimeManifestResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

class RuntimeManifestSelectorTest {

    private final ObjectMapper json = new ObjectMapper();
    private final RuntimeManifestSelector selector = new RuntimeManifestSelector(new RuntimeManifestMapper());

    @Test
    void matchingUploadedVideoIsSelectedInsideTheVerifiedManifest() throws Exception {
        VerifiedStoredManifest verified = uploadedVideo();
        RuntimeCapabilities capabilities = capabilities("VIDEO", "ja", "mom", List.of(1),
            List.of("SLIDE", "VIDEO"), 1);

        StoryRuntimeManifestResponse response = selector.select(verified, capabilities);

        assertThat(response.rendition()).isEqualTo("VIDEO");
        assertThat(response.video().assetKey()).isEqualTo("asset-401");
        assertThat(response.contentVersionId()).isEqualTo(17L);
        assertThat(response.manifestChecksum()).isEqualTo(verified.storedBytesSha256());
    }

    @Test
    void matchingUploadedVideoDoesNotRequireSlideCapability() throws Exception {
        VerifiedStoredManifest verified = uploadedVideo();
        RuntimeCapabilities capabilities = capabilities("VIDEO", "ja", "mom", List.of(1),
            List.of("VIDEO"), 1);

        StoryRuntimeManifestResponse response = selector.select(verified, capabilities);

        assertThat(response.rendition()).isEqualTo("VIDEO");
        assertThat(response.video().assetKey()).isEqualTo("asset-401");
        assertThat(response.contentVersionId()).isEqualTo(17L);
    }

    @Test
    void unsupportedVideoFallsBackToTheSameVersionCompatibilitySlide() throws Exception {
        VerifiedStoredManifest verified = uploadedVideo();
        RuntimeCapabilities capabilities = capabilities("VIDEO", "ja", "mom", List.of(1), List.of("SLIDE"), 1);

        StoryRuntimeManifestResponse response = selector.select(verified, capabilities);

        assertThat(response.rendition()).isEqualTo("SLIDE");
        assertThat(response.video()).isNull();
        assertThat(response.videoVariants()).hasSize(3);
        assertThat(response.contentVersionId()).isEqualTo(verified.manifest().contentVersionId());
        assertThat(response.manifestChecksum()).isEqualTo(verified.storedBytesSha256());
    }

    @Test
    void unavailableRequestedVideoVoiceReturnsStableUnavailable() throws Exception {
        VerifiedStoredManifest verified = uploadedVideo();
        RuntimeCapabilities capabilities = capabilities("VIDEO", "ja", "dad", List.of(1),
            List.of("SLIDE", "VIDEO"), 1);

        assertThatThrownBy(() -> selector.select(verified, capabilities))
            .isInstanceOfSatisfying(StoryRuntimeException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("PUBLISHED_MANIFEST_UNAVAILABLE"));
    }

    @Test
    void slideRequestUsesExactJapaneseAudioProjection() throws Exception {
        VerifiedStoredManifest verified = staticSlide();
        RuntimeCapabilities capabilities = capabilities("SLIDE", "ja", "mom", List.of(1), List.of("SLIDE"), 1);

        StoryRuntimeManifestResponse response = selector.select(verified, capabilities);

        assertThat(response.selectedLocale()).isEqualTo("ja");
        assertThat(response.selectedVoiceType()).isEqualTo("mom");
        assertThat(response.scenes().get(0).audioCues().get(0).assetKey()).isEqualTo("asset-302");
    }

    @Test
    void slideRequestUsesExactKoreanDadAudioProjection() throws Exception {
        VerifiedStoredManifest verified = staticSlide();
        RuntimeCapabilities capabilities = capabilities("SLIDE", "ko", "dad", List.of(1), List.of("SLIDE"), 1);

        StoryRuntimeManifestResponse response = selector.select(verified, capabilities);

        assertThat(response.selectedLocale()).isEqualTo("ko");
        assertThat(response.selectedVoiceType()).isEqualTo("dad");
        assertThat(response.scenes().get(0).audioCues().get(0).assetKey()).isEqualTo("asset-303");
    }

    @Test
    void nullVoiceUsesTheRequestedLocaleDefaultVoice() throws Exception {
        VerifiedStoredManifest verified = staticSlide();
        RuntimeCapabilities capabilities = capabilities("SLIDE", "ko", null, List.of(1), List.of("SLIDE"), 1);

        StoryRuntimeManifestResponse response = selector.select(verified, capabilities);

        assertThat(response.selectedLocale()).isEqualTo("ko");
        assertThat(response.selectedVoiceType()).isEqualTo("mom");
        assertThat(response.scenes().get(0).audioCues().get(0).assetKey()).isEqualTo("asset-301");
    }

    @Test
    void nullVoiceSelectsSilentSlideWhenLocaleHasNoNarrationCues() throws Exception {
        VerifiedStoredManifest verified = silentSlide();
        RuntimeCapabilities capabilities = capabilities("SLIDE", "ko", null, List.of(1), List.of("SLIDE"), 1);

        StoryRuntimeManifestResponse response = selector.select(verified, capabilities);

        assertThat(response.rendition()).isEqualTo("SLIDE");
        assertThat(response.selectedLocale()).isEqualTo("ko");
        assertThat(response.selectedVoiceType()).isNull();
        assertThat(response.scenes()).allSatisfy(scene -> assertThat(scene.audioCues()).isEmpty());
    }

    @Test
    void nullVoiceVideoRequestFallsBackToSilentSlideWithoutMatchingVoicedVideo() throws Exception {
        VerifiedStoredManifest verified = silentUploadedVideo();
        RuntimeCapabilities capabilities = capabilities("VIDEO", "ja", null, List.of(1),
            List.of("SLIDE", "VIDEO"), 1);

        StoryRuntimeManifestResponse response = selector.select(verified, capabilities);

        assertThat(response.rendition()).isEqualTo("SLIDE");
        assertThat(response.video()).isNull();
        assertThat(response.videoVariants()).isNotEmpty();
        assertThat(response.selectedVoiceType()).isNull();
        assertThat(response.scenes()).allSatisfy(scene -> assertThat(scene.audioCues()).isEmpty());
    }

    @Test
    void explicitVoiceRemainsUnavailableWhenLocaleHasNoNarrationCues() throws Exception {
        VerifiedStoredManifest verified = silentSlide();
        RuntimeCapabilities capabilities = capabilities("SLIDE", "ko", "mom", List.of(1), List.of("SLIDE"), 1);

        assertThatThrownBy(() -> selector.select(verified, capabilities))
            .isInstanceOfSatisfying(StoryRuntimeException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("PUBLISHED_MANIFEST_UNAVAILABLE"));
    }

    @Test
    void unavailableRequestedVoiceReturnsStableUnavailable() throws Exception {
        VerifiedStoredManifest verified = staticSlide();
        RuntimeCapabilities capabilities = capabilities("SLIDE", "ko", "grandma", List.of(1), List.of("SLIDE"), 1);

        assertThatThrownBy(() -> selector.select(verified, capabilities))
            .isInstanceOfSatisfying(StoryRuntimeException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("PUBLISHED_MANIFEST_UNAVAILABLE"));
    }

    @Test
    void incompleteMultiCueVoiceTupleReturnsStableUnavailable() throws Exception {
        tools.jackson.databind.node.ObjectNode stored = (tools.jackson.databind.node.ObjectNode) json.readTree(
            fixture("story-runtime-v1-static-slide-stored.json")
        );
        tools.jackson.databind.node.ObjectNode secondScene = ((tools.jackson.databind.node.ObjectNode)
            stored.path("scenes").path(0)).deepCopy();
        secondScene.put("sceneKey", "scene-2");
        secondScene.put("orderIndex", 1);
        ((tools.jackson.databind.node.ObjectNode) secondScene.path("audioCues").path(0)).put("cueKey", "narration-2");
        ((tools.jackson.databind.node.ArrayNode) stored.path("scenes")).add(secondScene);
        VerifiedStoredManifest verified = manifest(stored);
        RuntimeCapabilities capabilities = capabilities("SLIDE", "ko", "dad", List.of(1), List.of("SLIDE"), 1);

        assertThatThrownBy(() -> selector.select(verified, capabilities))
            .isInstanceOfSatisfying(StoryRuntimeException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("PUBLISHED_MANIFEST_UNAVAILABLE"));
    }

    @Test
    void exactVideoSelectionUsesCompatibilitySlideAudioProjection() throws Exception {
        VerifiedStoredManifest verified = uploadedVideo();
        RuntimeCapabilities capabilities = capabilities("VIDEO", "ko", "dad", List.of(1),
            List.of("SLIDE", "VIDEO"), 1);

        StoryRuntimeManifestResponse response = selector.select(verified, capabilities);

        assertThat(response.rendition()).isEqualTo("VIDEO");
        assertThat(response.video().assetKey()).isEqualTo("asset-402");
        assertThat(response.selectedLocale()).isEqualTo("ko");
        assertThat(response.selectedVoiceType()).isEqualTo("dad");
        assertThat(response.scenes().get(0).audioCues().get(0).assetKey()).isEqualTo("asset-303");
    }

    @ParameterizedTest
    @MethodSource("incompatibleCapabilities")
    void incompleteCompatibilitySupportReturnsStableUnavailable(RuntimeCapabilities capabilities) throws Exception {
        VerifiedStoredManifest verified = uploadedVideo();

        assertThatThrownBy(() -> selector.select(verified, capabilities))
            .isInstanceOfSatisfying(StoryRuntimeException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("PUBLISHED_MANIFEST_UNAVAILABLE"));
    }

    private static Stream<Arguments> incompatibleCapabilities() {
        return Stream.of(
            Arguments.of(capabilities("VIDEO", "ja", "mom", List.of(2), List.of("SLIDE", "VIDEO"), 1)),
            Arguments.of(capabilities("VIDEO", "ja", "mom", List.of(1), List.of("SLIDE", "VIDEO"), 0))
        );
    }

    private VerifiedStoredManifest uploadedVideo() throws Exception {
        byte[] bytes = fixture("story-runtime-v1-uploaded-video-stored.json");
        return new VerifiedStoredManifest(
            json.readValue(bytes, StoredRuntimeManifest.class), new Sha256Digest().hex(bytes)
        );
    }

    private VerifiedStoredManifest staticSlide() throws Exception {
        byte[] bytes = fixture("story-runtime-v1-static-slide-stored.json");
        return new VerifiedStoredManifest(
            json.readValue(bytes, StoredRuntimeManifest.class), new Sha256Digest().hex(bytes)
        );
    }

    private VerifiedStoredManifest silentSlide() throws Exception {
        tools.jackson.databind.node.ObjectNode stored = (tools.jackson.databind.node.ObjectNode) json.readTree(
            fixture("story-runtime-v1-static-slide-stored.json")
        );
        stored.putNull("selectedVoiceType");
        ((tools.jackson.databind.node.ObjectNode) stored.path("availableVoiceTypes")).removeAll();
        ((tools.jackson.databind.node.ObjectNode) stored.path("availableVoiceTypes"))
            .set("ko", json.createArrayNode());
        ((tools.jackson.databind.node.ObjectNode) stored.path("availableVoiceTypes"))
            .set("ja", json.createArrayNode());
        ((tools.jackson.databind.node.ObjectNode) stored.path("defaultVoiceTypes")).removeAll();
        removeNarration(stored);
        return manifest(stored);
    }

    private VerifiedStoredManifest silentUploadedVideo() throws Exception {
        tools.jackson.databind.node.ObjectNode stored = (tools.jackson.databind.node.ObjectNode) json.readTree(
            fixture("story-runtime-v1-uploaded-video-stored.json")
        );
        ((tools.jackson.databind.node.ObjectNode) stored.path("defaultVoiceTypes")).remove("ja");
        removeNarration(stored);
        return manifest(stored);
    }

    private void removeNarration(tools.jackson.databind.node.ObjectNode stored) {
        ((tools.jackson.databind.node.ArrayNode) stored.path("audioVariants")).removeAll();
        stored.path("scenes").forEach(scene ->
            ((tools.jackson.databind.node.ArrayNode) scene.path("audioCues")).removeAll()
        );
    }

    private VerifiedStoredManifest manifest(tools.jackson.databind.node.ObjectNode stored) throws Exception {
        byte[] bytes = json.writeValueAsBytes(stored);
        return new VerifiedStoredManifest(json.readValue(bytes, StoredRuntimeManifest.class), new Sha256Digest().hex(bytes));
    }

    private byte[] fixture(String name) throws Exception {
        return Files.readString(Path.of("src/test/resources/contracts", name), StandardCharsets.UTF_8)
            .trim()
            .getBytes(StandardCharsets.UTF_8);
    }

    private static RuntimeCapabilities capabilities(
        String rendition,
        String locale,
        String voiceType,
        List<Integer> schemaVersions,
        List<String> renditions,
        Integer rendererVersion
    ) {
        return new RuntimeCapabilities(
            rendition, locale, voiceType, schemaVersions, renditions, rendererVersion
        );
    }
}

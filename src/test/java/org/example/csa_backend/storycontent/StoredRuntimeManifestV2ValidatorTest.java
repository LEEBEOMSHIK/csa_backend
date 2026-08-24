package org.example.csa_backend.storycontent;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class StoredRuntimeManifestV2ValidatorTest {

    private final ObjectMapper json = new ObjectMapper();
    private final StoredRuntimeManifestValidator validator = new StoredRuntimeManifestValidator();

    @Test
    void acceptsTheGoldenInteractiveManifest() throws Exception {
        ObjectNode stored = fixture();

        assertThatCode(() -> validate(stored)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("validInteractiveMutations")
    void acceptsOptionalInteractiveFieldsAndImageBackgrounds(Consumer<ObjectNode> mutation) throws Exception {
        ObjectNode stored = fixture();
        mutation.accept(stored);

        assertThatCode(() -> validate(stored)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("invalidInteractiveMutations")
    void rejectsInvalidInteractiveContract(Consumer<ObjectNode> mutation) throws Exception {
        ObjectNode stored = fixture();
        mutation.accept(stored);

        assertThatThrownBy(() -> validate(stored))
            .isInstanceOfSatisfying(StoryRuntimeException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.getCode())
                    .isEqualTo("PUBLISHED_MANIFEST_CORRUPT"));
    }

    private static Stream<Consumer<ObjectNode>> invalidInteractiveMutations() {
        return Stream.of(
            root -> object(scene(root).path("background")).put("layerKey", "missing-layer"),
            root -> object(root.path("assets").path(0)).put("kind", "VIDEO"),
            root -> object(scene(root).path("tracks").path(0)).put("targetLayerKey", "missing-layer"),
            root -> object(scene(root).path("tracks").path(0)).put("property", "UNKNOWN"),
            root -> object(scene(root).path("tracks").path(0)).put("easing", "UNKNOWN"),
            root -> {
                ObjectNode track = object(scene(root).path("tracks").path(0));
                track.put("startMs", 1);
                track.put("durationMs", Long.MAX_VALUE);
            },
            root -> object(scene(root).path("triggers").path(0)).put("targetLayerKey", "missing-layer"),
            root -> object(scene(root).path("triggers").path(0).path("accessibilityLabel")).put("ko", " "),
            root -> object(scene(root).path("triggers").path(0)).put("videoCueMs", 8001),
            root -> object(scene(root).path("triggers").path(0).path("actions").path(0))
                .put("trackKey", "missing-track"),
            root -> object(scene(root).path("triggers").path(0).path("actions").path(1))
                .put("audioCueKey", "missing-cue"),
            root -> object(object(scene(root).path("audioCues").path(0)).path("playback"))
                .put("gainDb", 7),
            root -> object(object(scene(root).path("audioCues").path(2)).path("playback"))
                .put("duckingDb", 11),
            root -> {
                ObjectNode playback = object(object(scene(root).path("audioCues").path(2)).path("playback"));
                playback.put("fadeInMs", 5000);
                playback.put("fadeOutMs", 5000);
            },
            root -> object(scene(root).path("transitions").path(0)).put("type", "UNKNOWN"),
            root -> object(scene(root).path("transitions").path(0)).put("type", "TIMED_NEXT"),
            root -> object(scene(root).path("transitions").path(0)).put("atMs", 1)
        );
    }

    private static Stream<Consumer<ObjectNode>> validInteractiveMutations() {
        return Stream.of(
            root -> scene(root).remove("background"),
            root -> object(root.path("assets").path(1)).put("kind", "IMAGE"),
            root -> scene(root).path("audioCues").forEach(cue -> object(cue).remove("playback"))
        );
    }

    private void validate(ObjectNode stored) throws Exception {
        validator.validate(json.valueToTree(stored), json.treeToValue(stored, StoredRuntimeManifest.class), rendition());
    }

    private ObjectNode fixture() throws Exception {
        return (ObjectNode) json.readTree(Files.readString(
            Path.of("src/test/resources/contracts/story-runtime-v2-interactive-stored.json"), StandardCharsets.UTF_8
        ));
    }

    private Rendition rendition() {
        Rendition rendition = new Rendition();
        ReflectionTestUtils.setField(rendition, "versionId", 17L);
        ReflectionTestUtils.setField(rendition, "type", RenditionType.INTERACTIVE);
        ReflectionTestUtils.setField(rendition, "rendererVersion", 2);
        return rendition;
    }

    private static ObjectNode scene(ObjectNode root) {
        return object(root.path("scenes").path(0));
    }

    private static ObjectNode object(tools.jackson.databind.JsonNode value) {
        return (ObjectNode) value;
    }
}

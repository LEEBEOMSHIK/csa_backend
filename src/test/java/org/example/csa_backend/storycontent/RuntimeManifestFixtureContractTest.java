package org.example.csa_backend.storycontent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest;
import org.example.csa_backend.storycontent.dto.StoryRuntimeManifestResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class RuntimeManifestFixtureContractTest {

    private final ObjectMapper json = new ObjectMapper();
    private final Sha256Digest sha256 = new Sha256Digest();
    private final RuntimeManifestMapper mapper = new RuntimeManifestMapper();

    @ParameterizedTest
    @MethodSource("fixturePairs")
    void flatWireSerializationMatchesExactStoredFixtureBytes(String storedName, String wireName) throws Exception {
        byte[] storedBytes = fixture(storedName).getBytes(StandardCharsets.UTF_8);
        StoredRuntimeManifest stored = json.readValue(storedBytes, StoredRuntimeManifest.class);
        String checksum = sha256.hex(storedBytes);

        StoryRuntimeManifestResponse response = mapper.flat(stored, checksum);
        JsonNode wire = json.readTree(fixture(wireName));

        assertThat(json.writeValueAsString(response)).isEqualTo(fixture(wireName));
        assertThat(wire.path("manifestChecksum").asString()).isEqualTo(checksum);
        assertThat(wire.has("manifest")).isFalse();
    }

    private static Stream<Arguments> fixturePairs() {
        return Stream.of(
            Arguments.of("story-runtime-v1-static-slide-stored.json", "story-runtime-v1-static-slide.json"),
            Arguments.of("story-runtime-v1-uploaded-video-stored.json", "story-runtime-v1-uploaded-video.json")
        );
    }

    private String fixture(String name) throws Exception {
        return Files.readString(Path.of("src/test/resources/contracts", name), StandardCharsets.UTF_8).trim();
    }
}

package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.example.csa_backend.config.StorageProperties;
import org.example.csa_backend.config.S3MediaStorageClient;
import org.example.csa_backend.storycontent.ContentVersionStatus;
import org.example.csa_backend.storycontent.LegacyType;
import org.example.csa_backend.storycontent.PublishedMediaRoute;
import org.example.csa_backend.storycontent.PublishedMediaStorage;
import org.example.csa_backend.storycontent.StoryOrigin;
import org.example.csa_backend.storycontent.StoryVisibility;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

class LegacyMediaSnapshotStoreTest {

    @TempDir
    Path tempDirectory;

    @Test
    void rejectsLexicalTraversalAndWindowsBackslashKeys() throws IOException {
        Path canonicalRoot = Files.createDirectory(tempDirectory.resolve("canonical"));
        Path legacyRoot = Files.createDirectory(tempDirectory.resolve("legacy"));
        LegacyMediaSnapshotStore store = store(canonicalRoot, legacyRoot);

        assertThatThrownBy(() -> store.prepare(projection(
            LegacyType.CURATED, 81L, "/uploads/../outside.png")))
            .isInstanceOfSatisfying(LegacyImportException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("MEDIA_STORAGE_KEY_ESCAPE"));
        assertThatThrownBy(() -> store.prepare(projection(
            LegacyType.CURATED, 82L, "/uploads/legacy\\outside.png")))
            .isInstanceOfSatisfying(LegacyImportException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("LEGACY_MEDIA_PREFLIGHT_FAILED"));
    }

    @Test
    void rejectsSourceReadThroughAWindowsJunction() throws Exception {
        Path canonicalRoot = Files.createDirectory(tempDirectory.resolve("canonical"));
        Path legacyRoot = Files.createDirectory(tempDirectory.resolve("legacy"));
        Path outside = Files.createDirectory(tempDirectory.resolve("outside"));
        Files.write(outside.resolve("secret.png"), "outside".getBytes(StandardCharsets.UTF_8));
        Path junction = canonicalRoot.resolve("legacy-link");
        createDirectoryJunction(junction, outside);
        try {
            assertThatThrownBy(() -> store(canonicalRoot, legacyRoot).prepare(projection(
                LegacyType.CURATED, 83L, "/uploads/legacy-link/secret.png")))
                .isInstanceOfSatisfying(LegacyImportException.class,
                    exception -> assertThat(exception.getCode()).isEqualTo("LEGACY_MEDIA_PREFLIGHT_FAILED"));
        } finally {
            Files.deleteIfExists(junction);
        }
    }

    @Test
    void rejectsSourceReadThroughASymbolicLinkWhenSupported() throws Exception {
        Path canonicalRoot = Files.createDirectory(tempDirectory.resolve("canonical"));
        Path legacyRoot = Files.createDirectory(tempDirectory.resolve("legacy"));
        Path outside = Files.createDirectory(tempDirectory.resolve("outside"));
        Files.write(outside.resolve("secret.png"), "outside".getBytes(StandardCharsets.UTF_8));
        Path link = canonicalRoot.resolve("legacy-link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException exception) {
            Assumptions.abort("symbolic links are unavailable: " + exception.getMessage());
        }
        try {
            assertThatThrownBy(() -> store(canonicalRoot, legacyRoot).prepare(projection(
                LegacyType.CURATED, 84L, "/uploads/legacy-link/secret.png")))
                .isInstanceOfSatisfying(LegacyImportException.class,
                    exception -> assertThat(exception.getCode()).isEqualTo("LEGACY_MEDIA_PREFLIGHT_FAILED"));
        } finally {
            Files.deleteIfExists(link);
        }
    }

    @Test
    void rejectsCanonicalWriteThroughAWindowsJunction() throws Exception {
        Path canonicalRoot = Files.createDirectory(tempDirectory.resolve("canonical"));
        Path legacyRoot = Files.createDirectory(tempDirectory.resolve("legacy"));
        Path legacyStory = Files.createDirectories(legacyRoot.resolve("85"));
        Files.write(legacyStory.resolve("page.png"), "legacy".getBytes(StandardCharsets.UTF_8));
        Path outside = Files.createDirectory(tempDirectory.resolve("outside"));
        Path junction = canonicalRoot.resolve("story-assets");
        createDirectoryJunction(junction, outside);
        try {
            LegacyMediaSnapshotStore store = store(canonicalRoot, legacyRoot);
            LegacyMediaSnapshotStore.PreparedImport preparedImport = store.prepare(projection(
                LegacyType.AI, 85L, "/files/generated-fairytales/85/page.png"));
            assertThatThrownBy(() -> store.materialize(preparedImport))
                .isInstanceOfSatisfying(LegacyImportException.class,
                    exception -> assertThat(exception.getCode()).isEqualTo("CANONICAL_MEDIA_WRITE_FAILED"));
            assertThat(Files.exists(outside.resolve("ai"))).isFalse();
        } finally {
            Files.deleteIfExists(junction);
        }
    }

    @Test
    void preparedAssetsAndManifestChecksumUseCanonicalOrdering() throws Exception {
        Path canonicalRoot = Files.createDirectory(tempDirectory.resolve("canonical"));
        Path legacyRoot = Files.createDirectory(tempDirectory.resolve("legacy"));
        Path source = Files.createDirectory(canonicalRoot.resolve("source"));
        Files.write(source.resolve("page.png"), "image".getBytes(StandardCharsets.UTF_8));
        Files.write(source.resolve("a.mp3"), "audio-a".getBytes(StandardCharsets.UTF_8));
        Files.write(source.resolve("z.mp3"), "audio-z".getBytes(StandardCharsets.UTF_8));
        LegacyMediaSnapshotStore store = store(canonicalRoot, legacyRoot);
        LegacyProjection firstProjection = projectionWithAudios(86L, false);
        LegacyProjection secondProjection = projectionWithAudios(86L, true);

        LegacyMediaSnapshotStore.PreparedImport firstImport = store.prepare(firstProjection);
        LegacyMediaSnapshotStore.PreparedImport secondImport = store.prepare(secondProjection);
        LegacyMediaSnapshotStore.PreparedMedia first = firstImport.media();
        LegacyMediaSnapshotStore.PreparedMedia second = secondImport.media();

        assertThat(first.assets().keySet()).containsExactly(
            "page-0-audio-a-ko", "page-0-audio-z-ko", "page-0-image");
        assertThat(second.assets().keySet()).containsExactlyElementsOf(first.assets().keySet());
        ObjectMapper objectMapper = new ObjectMapper();
        CanonicalStoryWriter writer = new CanonicalStoryWriter(null, objectMapper, store);
        StoredRuntimeManifest firstManifest = manifest(writer, firstImport.projection(), first);
        StoredRuntimeManifest secondManifest = manifest(writer, secondImport.projection(), second);
        ContractChecksum contractChecksum = new ContractChecksum();
        assertThat(contractChecksum.ofBytes(objectMapper.writeValueAsBytes(firstManifest)))
            .isEqualTo(contractChecksum.ofBytes(objectMapper.writeValueAsBytes(secondManifest)));
    }

    private LegacyMediaSnapshotStore store(Path canonicalRoot, Path legacyRoot) {
        StorageProperties properties = new StorageProperties();
        properties.setLocalBasePath(legacyRoot.toString());
        PublishedMediaStorage publishedStorage =
            new PublishedMediaStorage("local", canonicalRoot.toString(), "");
        @SuppressWarnings("unchecked")
        ObjectProvider<S3MediaStorageClient> legacyS3 = org.mockito.Mockito.mock(ObjectProvider.class);
        LegacyMediaSourceReader sourceReader = new LegacyMediaSourceReader(
            "http://localhost:18080/uploads",
            publishedStorage,
            properties,
            legacyS3
        );
        return new LegacyMediaSnapshotStore(
            "http://localhost:18080/uploads",
            new ContractChecksum(),
            new PublishedMediaRoute(""),
            publishedStorage,
            sourceReader
        );
    }

    private LegacyProjection projection(LegacyType type, long id, String imageUrl) {
        return new LegacyProjection(
            type,
            id,
            type == LegacyType.CURATED ? StoryOrigin.CURATED : StoryOrigin.AI_GENERATED,
            Long.toString(id),
            type == LegacyType.AI ? 10_000L + id : null,
            type == LegacyType.CURATED ? StoryVisibility.PUBLISHED : StoryVisibility.OWNER_PRIVATE,
            "title",
            "title",
            "description",
            "description",
            List.of(),
            ContentVersionStatus.DRAFT,
            false,
            "slide",
            "INCOMPLETE",
            "ko",
            null,
            Map.of("ko", List.of()),
            Map.of(),
            List.of(new LegacyProjection.SceneProjection(
                "page-0", 0, 1_000, 1, 1, Map.of("ko", "text"), imageUrl, List.of(), null)),
            List.of(),
            null,
            "a".repeat(64)
        );
    }

    private LegacyProjection projectionWithAudios(long id, boolean reverse) {
        LegacyProjection.AudioProjection a = new LegacyProjection.AudioProjection(
            "a", "ko", "/uploads/source/a.mp3");
        LegacyProjection.AudioProjection z = new LegacyProjection.AudioProjection(
            "z", "ko", "/uploads/source/z.mp3");
        List<LegacyProjection.AudioProjection> audios = reverse ? List.of(a, z) : List.of(z, a);
        return new LegacyProjection(
            LegacyType.CURATED,
            id,
            StoryOrigin.CURATED,
            Long.toString(id),
            null,
            StoryVisibility.PUBLISHED,
            "title",
            "title",
            "description",
            "description",
            List.of(),
            ContentVersionStatus.PUBLISHED,
            true,
            "slide",
            "COMPLETED",
            "ko",
            null,
            Map.of("ko", List.of("a", "z")),
            Map.of("ko", "a"),
            List.of(new LegacyProjection.SceneProjection(
                "page-0",
                0,
                1_000,
                1,
                1,
                Map.of("ko", "text"),
                "/uploads/source/page.png",
                audios,
                null
            )),
            List.of(),
            null,
            "b".repeat(64)
        );
    }

    private StoredRuntimeManifest manifest(
        CanonicalStoryWriter writer,
        LegacyProjection projection,
        LegacyMediaSnapshotStore.PreparedMedia media
    ) {
        return ReflectionTestUtils.invokeMethod(
            writer,
            "manifest",
            1L,
            2L,
            1,
            projection,
            media,
            new ArrayList<>(),
            new ArrayList<>()
        );
    }

    private void createDirectoryJunction(Path junction, Path target) throws Exception {
        Process process = new ProcessBuilder(
            "cmd.exe", "/c", "mklink", "/J", junction.toString(), target.toString()
        ).redirectErrorStream(true).start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(finished).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
    }
}

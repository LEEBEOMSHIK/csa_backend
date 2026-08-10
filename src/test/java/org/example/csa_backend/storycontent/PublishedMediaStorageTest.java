package org.example.csa_backend.storycontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

class PublishedMediaStorageTest {

    @TempDir
    Path tempDirectory;

    @Test
    void localModeReadsExactBytesUnderTheConfiguredRoot() throws Exception {
        byte[] expected = "immutable-manifest".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path target = tempDirectory.resolve("story-assets/7/versions/17/manifest.json");
        Files.createDirectories(target.getParent());
        Files.write(target, expected);
        PublishedMediaStorage storage = new PublishedMediaStorage("local", tempDirectory, "", null);

        assertThat(storage.read("story-assets/7/versions/17/manifest.json")).isEqualTo(expected);
    }

    @Test
    void localModeRejectsAStorageKeyOutsideTheConfiguredRoot() {
        PublishedMediaStorage storage = new PublishedMediaStorage("local", tempDirectory, "", null);

        assertThatThrownBy(() -> storage.read("../secret.json"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void localModeRejectsAWindowsJunctionThatEscapesTheConfiguredRoot() throws Exception {
        Path root = Files.createDirectory(tempDirectory.resolve("root"));
        Path outside = Files.createDirectory(tempDirectory.resolve("outside"));
        byte[] secret = "outside-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(outside.resolve("manifest.json"), secret);
        Path junction = root.resolve("story-assets");
        createDirectoryJunction(junction, outside);
        PublishedMediaStorage storage = new PublishedMediaStorage("local", root, "", null);

        try {
            assertThatThrownBy(() -> storage.read("story-assets/manifest.json"))
                .isInstanceOf(java.io.IOException.class);
        } finally {
            Files.deleteIfExists(junction);
        }
    }

    @Test
    void s3ModeReadsExactBytesUsingTheStoredKey() throws Exception {
        byte[] expected = "immutable-s3-manifest".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObjectAsBytes(org.mockito.ArgumentMatchers.<GetObjectRequest>argThat(request ->
            request != null
                && "published-media".equals(request.bucket())
                && "story-assets/7/versions/17/manifest.json".equals(request.key())
        ))).thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), expected));
        PublishedMediaStorage storage = new PublishedMediaStorage(
            "s3", tempDirectory, "published-media", s3Client
        );

        assertThat(storage.read("story-assets/7/versions/17/manifest.json")).isEqualTo(expected);
    }

    private void createDirectoryJunction(Path junction, Path target) throws Exception {
        Process process = new ProcessBuilder(
            "cmd.exe", "/c", "mklink", "/J", junction.toString(), target.toString()
        ).redirectErrorStream(true).start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(finished).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
    }
}

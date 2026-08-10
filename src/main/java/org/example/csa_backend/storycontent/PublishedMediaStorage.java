package org.example.csa_backend.storycontent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Component
public class PublishedMediaStorage {

    private final Path root;
    private final String storageMode;
    private final String bucket;
    private final S3Client s3Client;

    @Autowired
    public PublishedMediaStorage(
        @Value("${csa.media.storage-mode:local}") String storageMode,
        @Value("${csa.media.storage-root:uploads}") String root,
        @Value("${csa.media.bucket:}") String bucket
    ) {
        this(
            storageMode,
            Path.of(root),
            bucket,
            "s3".equalsIgnoreCase(storageMode)
                ? S3Client.builder().overrideConfiguration(ClientOverrideConfiguration.builder()
                    .apiCallAttemptTimeout(Duration.ofSeconds(10))
                    .apiCallTimeout(Duration.ofSeconds(30))
                    .build()).build()
                : null
        );
    }

    PublishedMediaStorage(Path root) {
        this("local", root, "", null);
    }

    PublishedMediaStorage(String storageMode, Path root, String bucket, S3Client s3Client) {
        this.root = root.toAbsolutePath().normalize();
        this.storageMode = storageMode == null ? "local" : storageMode.trim().toLowerCase(Locale.ROOT);
        this.bucket = bucket == null ? "" : bucket.trim();
        this.s3Client = s3Client;
    }

    public byte[] read(String storageKey) throws IOException {
        validateStorageKey(storageKey);
        if ("s3".equals(storageMode)) {
            return readS3(storageKey);
        }
        if (!"local".equals(storageMode)) {
            throw new IOException("Published media storage mode is unavailable");
        }
        return readLocal(storageKey);
    }

    private byte[] readLocal(String storageKey) throws IOException {
        Path target = safeResolve(storageKey);
        return Files.readAllBytes(PublishedMediaPathGuard.requireReadableFile(root, target));
    }

    private byte[] readS3(String storageKey) throws IOException {
        if (bucket.isBlank() || s3Client == null) {
            throw new IOException("Published S3 media storage is not configured");
        }
        try {
            return s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(storageKey).build()
            ).asByteArray();
        } catch (RuntimeException exception) {
            throw new IOException("Published media object is unavailable", exception);
        }
    }

    private void validateStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.indexOf('\0') >= 0
            || storageKey.contains("\\")) {
            throw new IllegalArgumentException("Invalid storage key");
        }
    }

    private Path safeResolve(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Storage key escapes configured root");
        }
        return resolved;
    }

}

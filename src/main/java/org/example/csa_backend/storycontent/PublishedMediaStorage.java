package org.example.csa_backend.storycontent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

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

    public void writeImmutable(String storageKey, byte[] bytes, String contentType) throws IOException {
        validateStorageKey(storageKey);
        if (bytes == null || bytes.length == 0) {
            throw new IOException("Published media bytes are required");
        }
        if ("s3".equals(storageMode)) {
            writeS3Immutable(storageKey, bytes, contentType);
            return;
        }
        if (!"local".equals(storageMode)) {
            throw new IOException("Published media storage mode is unavailable");
        }
        writeLocalImmutable(storageKey, bytes);
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

    private void writeLocalImmutable(String storageKey, byte[] bytes) throws IOException {
        Path target = safeResolve(storageKey);
        Path temporary = null;
        try {
            PublishedMediaPathGuard.requireSafeWriteTarget(root, target);
            Files.createDirectories(target.getParent());
            PublishedMediaPathGuard.requireSafeWriteTarget(root, target);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                verifyExisting(storageKey, bytes, Files.readAllBytes(
                    PublishedMediaPathGuard.requireReadableFile(root, target)
                ));
                return;
            }
            temporary = Files.createTempFile(target.getParent(), ".published-media-", ".tmp");
            PublishedMediaPathGuard.requireSafeWriteTarget(root, temporary);
            try (FileChannel channel = FileChannel.open(
                temporary,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, target);
                temporary = null;
            } catch (FileAlreadyExistsException ignored) {
                verifyExisting(storageKey, bytes, Files.readAllBytes(
                    PublishedMediaPathGuard.requireReadableFile(root, target)
                ));
            }
        } finally {
            if (temporary != null) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private void writeS3Immutable(String storageKey, byte[] bytes, String contentType) throws IOException {
        if (bucket.isBlank() || s3Client == null) {
            throw new IOException("Published S3 media storage is not configured");
        }
        byte[] existing = readS3IfPresent(storageKey);
        if (existing != null) {
            verifyExisting(storageKey, bytes, existing);
            return;
        }
        PutObjectRequest.Builder request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(storageKey)
            .ifNoneMatch("*");
        if (contentType != null && !contentType.isBlank()) {
            request.contentType(contentType);
        }
        try {
            s3Client.putObject(request.build(), RequestBody.fromBytes(bytes));
        } catch (S3Exception exception) {
            if (exception.statusCode() == 409 || exception.statusCode() == 412) {
                byte[] winner = readS3IfPresent(storageKey);
                if (winner != null) {
                    verifyExisting(storageKey, bytes, winner);
                    return;
                }
            }
            throw new IOException("Published S3 media write failed", exception);
        } catch (RuntimeException exception) {
            throw new IOException("Published S3 media write failed", exception);
        }
    }

    private byte[] readS3IfPresent(String storageKey) throws IOException {
        try {
            return s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(storageKey).build()
            ).asByteArray();
        } catch (NoSuchKeyException exception) {
            return null;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return null;
            }
            throw new IOException("Published S3 media lookup failed", exception);
        } catch (RuntimeException exception) {
            throw new IOException("Published S3 media lookup failed", exception);
        }
    }

    private void verifyExisting(String storageKey, byte[] expected, byte[] existing)
        throws ImmutableObjectConflictException {
        if (!Arrays.equals(expected, existing)) {
            throw new ImmutableObjectConflictException(storageKey);
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

    public static final class ImmutableObjectConflictException extends IOException {

        private ImmutableObjectConflictException(String storageKey) {
            super("Published media object conflicts with immutable key: " + storageKey);
        }
    }

}

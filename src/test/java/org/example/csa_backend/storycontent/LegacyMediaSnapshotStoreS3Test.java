package org.example.csa_backend.storycontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.csa_backend.config.StorageProperties;
import org.example.csa_backend.config.S3MediaStorageClient;
import org.example.csa_backend.storycontent.migration.ContractChecksum;
import org.example.csa_backend.storycontent.migration.LegacyImportException;
import org.example.csa_backend.storycontent.migration.LegacyMediaSourceReader;
import org.example.csa_backend.storycontent.migration.LegacyMediaSnapshotStore;
import org.example.csa_backend.storycontent.migration.LegacyProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class LegacyMediaSnapshotStoreS3Test {

    private static final String BUCKET = "published-media";
    private static final String PREFIX = "phase1/content";

    @TempDir
    Path tempDirectory;

    private final Map<String, byte[]> objects = new LinkedHashMap<>();
    private S3Client s3Client;

    @BeforeEach
    void configureS3() {
        s3Client = mock(S3Client.class);
        org.mockito.Mockito.when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
            .thenAnswer(invocation -> {
                GetObjectRequest request = invocation.getArgument(0);
                assertThat(request.bucket()).isEqualTo(BUCKET);
                byte[] bytes = objects.get(request.key());
                if (bytes == null) {
                    throw NoSuchKeyException.builder().statusCode(404).build();
                }
                return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bytes);
            });
        org.mockito.Mockito.when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenAnswer(invocation -> {
                PutObjectRequest request = invocation.getArgument(0);
                RequestBody body = invocation.getArgument(1);
                assertThat(request.bucket()).isEqualTo(BUCKET);
                byte[] bytes = body.contentStreamProvider().newStream().readAllBytes();
                if (objects.putIfAbsent(request.key(), bytes) != null) {
                    throw S3Exception.builder().statusCode(412).build();
                }
                return PutObjectResponse.builder().build();
            });
    }

    @Test
    void materializesPreparedAssetAndManifestToTheExactReadableS3Keys() throws Exception {
        byte[] imageBytes = "s3-image".getBytes(StandardCharsets.UTF_8);
        byte[] manifestBytes = "{}".getBytes(StandardCharsets.UTF_8);
        Fixture fixture = fixture(imageBytes);

        fixture.store().materialize(fixture.preparedImport());
        LegacyMediaSnapshotStore.PreparedAsset image =
            fixture.preparedImport().media().assets().get("page-0-image");
        LegacyMediaSnapshotStore.PreparedAsset manifest = fixture.store().writeManifest(
            fixture.preparedImport().projection(),
            920L,
            1_920L,
            manifestBytes
        );

        assertThat(image.storageKey()).startsWith(
            PREFIX + "/story-assets/imports/curated/920/"
        );
        assertThat(manifest.storageKey()).startsWith(
            PREFIX + "/story-assets/imports/curated/920/"
        );
        assertThat(objects).containsEntry(image.storageKey(), imageBytes)
            .containsEntry(manifest.storageKey(), manifestBytes);
        assertThat(fixture.publishedStorage().read(image.storageKey())).isEqualTo(imageBytes);
        assertThat(fixture.publishedStorage().read(manifest.storageKey())).isEqualTo(manifestBytes);
        assertThat(Files.exists(tempDirectory.resolve(image.storageKey()))).isFalse();
        assertThat(Files.exists(tempDirectory.resolve(manifest.storageKey()))).isFalse();
    }

    @Test
    void replayOfIdenticalS3BytesIsACompleteObjectWriteNoOp() throws Exception {
        Fixture fixture = fixture("same-image".getBytes(StandardCharsets.UTF_8));

        fixture.store().materialize(fixture.preparedImport());
        fixture.store().materialize(fixture.preparedImport());

        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void conflictingExistingS3BytesFailClosedWithoutOverwriteOrLocalWrite() throws Exception {
        byte[] expected = "expected-image".getBytes(StandardCharsets.UTF_8);
        Fixture fixture = fixture(expected);
        LegacyMediaSnapshotStore.PreparedAsset image =
            fixture.preparedImport().media().assets().get("page-0-image");
        objects.put(image.storageKey(), "conflicting-image".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> fixture.store().materialize(fixture.preparedImport()))
            .isInstanceOfSatisfying(LegacyImportException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("IMMUTABLE_MEDIA_CONFLICT"));
        verify(s3Client, times(0)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        assertThat(Files.exists(tempDirectory.resolve(image.storageKey()))).isFalse();
    }

    private Fixture fixture(byte[] imageBytes) throws Exception {
        objects.put("legacy/page.png", imageBytes);
        StorageProperties properties = new StorageProperties();
        properties.setLocalBasePath(tempDirectory.resolve("ai-source").toString());
        PublishedMediaRoute route = new PublishedMediaRoute(PREFIX);
        PublishedMediaStorage publishedStorage = new PublishedMediaStorage(
            "s3",
            tempDirectory,
            BUCKET,
            s3Client
        );
        @SuppressWarnings("unchecked")
        ObjectProvider<S3MediaStorageClient> legacyS3 = mock(ObjectProvider.class);
        LegacyMediaSourceReader sourceReader = new LegacyMediaSourceReader(
            "https://cdn.example/uploads",
            publishedStorage,
            properties,
            legacyS3
        );
        LegacyMediaSnapshotStore store = new LegacyMediaSnapshotStore(
            "https://cdn.example/uploads",
            new ContractChecksum(),
            route,
            publishedStorage,
            sourceReader
        );
        LegacyMediaSnapshotStore.PreparedImport preparedImport = store.prepare(projection());
        return new Fixture(store, publishedStorage, preparedImport);
    }

    private LegacyProjection projection() {
        return new LegacyProjection(
            LegacyType.CURATED,
            920L,
            StoryOrigin.CURATED,
            "920",
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
            Map.of("ko", List.of()),
            Map.of(),
            List.of(new LegacyProjection.SceneProjection(
                "page-0",
                0,
                1_000,
                1,
                1,
                Map.of("ko", "text"),
                "/uploads/legacy/page.png",
                List.of(),
                null
            )),
            List.of(),
            null,
            "a".repeat(64)
        );
    }

    private record Fixture(
        LegacyMediaSnapshotStore store,
        PublishedMediaStorage publishedStorage,
        LegacyMediaSnapshotStore.PreparedImport preparedImport
    ) {
    }
}

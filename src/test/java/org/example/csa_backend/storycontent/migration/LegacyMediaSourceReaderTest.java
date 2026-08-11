package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.example.csa_backend.config.S3MediaStorageClient;
import org.example.csa_backend.config.StorageProperties;
import org.example.csa_backend.storycontent.PublishedMediaStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

class LegacyMediaSourceReaderTest {

    @TempDir
    Path tempDirectory;

    @Test
    void readsCanonicalBytesThroughConfiguredPublishedStorageForAbsoluteAndRelativeUrls()
        throws Exception {
        PublishedMediaStorage published = mock(PublishedMediaStorage.class);
        byte[] expected = "published-s3".getBytes(StandardCharsets.UTF_8);
        String key = "phase1/story-assets/imports/curated/7/source.png";
        when(published.read(key)).thenReturn(expected);
        LegacyMediaSourceReader reader = reader(localProperties(), published, null);

        assertThat(reader.read("https://published.example/uploads/" + key)).isEqualTo(expected);
        assertThat(reader.read("/uploads/" + key)).isEqualTo(expected);

        verify(published, org.mockito.Mockito.times(2)).read(key);
    }

    @Test
    void readsLegacyAiLocalBytesOnlyThroughTheConfiguredGeneratedRoute() throws Exception {
        StorageProperties properties = localProperties();
        Path source = tempDirectory.resolve("legacy-ai/7/page_1.png");
        Files.createDirectories(source.getParent());
        byte[] expected = "legacy-local".getBytes(StandardCharsets.UTF_8);
        Files.write(source, expected);
        PublishedMediaStorage published = mock(PublishedMediaStorage.class);
        LegacyMediaSourceReader reader = reader(properties, published, null);

        assertThat(reader.read(
            "http://legacy.example/files/generated-fairytales/7/page_1.png"
        )).isEqualTo(expected);

        verify(published, never()).read(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void readsLegacyAiCdnBytesByExactConfiguredS3Key() {
        StorageProperties properties = localProperties();
        properties.setMode("cdn");
        properties.setCdnBaseUrl("https://legacy-cdn.example/media");
        S3MediaStorageClient legacyS3 = mock(S3MediaStorageClient.class);
        byte[] expected = "legacy-s3".getBytes(StandardCharsets.UTF_8);
        when(legacyS3.read("fairytales/8/page_1.png")).thenReturn(expected);
        LegacyMediaSourceReader reader = reader(
            properties,
            mock(PublishedMediaStorage.class),
            legacyS3
        );

        assertThat(reader.read(
            "https://legacy-cdn.example/media/fairytales/8/page_1.png"
        )).isEqualTo(expected);

        verify(legacyS3).read("fairytales/8/page_1.png");
    }

    @Test
    void rejectsUnknownOriginsAndUnsupportedPathsWithoutReadingAnyStorage() throws Exception {
        PublishedMediaStorage published = mock(PublishedMediaStorage.class);
        S3MediaStorageClient legacyS3 = mock(S3MediaStorageClient.class);
        StorageProperties properties = localProperties();
        properties.setMode("cdn");
        properties.setCdnBaseUrl("https://legacy-cdn.example/media");
        LegacyMediaSourceReader reader = reader(properties, published, legacyS3);

        assertThatThrownBy(() -> reader.read(
            "https://attacker.example/uploads/phase1/story-assets/source.png"
        )).isInstanceOfSatisfying(LegacyImportException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("LEGACY_MEDIA_URL_UNSUPPORTED"));
        assertThatThrownBy(() -> reader.read(
            "https://legacy-cdn.example/unrelated/fairytales/8/page_1.png"
        )).isInstanceOfSatisfying(LegacyImportException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("LEGACY_MEDIA_URL_UNSUPPORTED"));

        verify(published, never()).read(org.mockito.ArgumentMatchers.anyString());
        verify(legacyS3, never()).read(org.mockito.ArgumentMatchers.anyString());
    }

    private StorageProperties localProperties() {
        StorageProperties properties = new StorageProperties();
        properties.setMode("local");
        properties.setLocalBasePath(tempDirectory.resolve("legacy-ai").toString());
        properties.setServerBaseUrl("http://legacy.example");
        return properties;
    }

    @SuppressWarnings("unchecked")
    private LegacyMediaSourceReader reader(
        StorageProperties properties,
        PublishedMediaStorage published,
        S3MediaStorageClient legacyS3
    ) {
        ObjectProvider<S3MediaStorageClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(legacyS3);
        return new LegacyMediaSourceReader(
            "https://published.example/uploads",
            published,
            properties,
            provider
        );
    }
}

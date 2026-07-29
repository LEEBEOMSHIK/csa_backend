package org.example.csa_backend.fairytale.service;

import org.example.csa_backend.config.S3MediaStorageClient;
import org.example.csa_backend.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStorageServiceTest {

    @SuppressWarnings("unchecked")
    private ObjectProvider<S3MediaStorageClient> providerOf(S3MediaStorageClient client) {
        ObjectProvider<S3MediaStorageClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        return provider;
    }

    @Test
    void saveImageLocalModeWritesFileAndReturnsServerUrl(@TempDir Path tempDir) {
        StorageProperties props = new StorageProperties();
        props.setMode("local");
        props.setLocalBasePath(tempDir.toString());
        props.setServerBaseUrl("http://localhost:8080");
        FileStorageService service = new FileStorageService(props, providerOf(null));

        String url = service.saveImage(7L, 1, new byte[]{1, 2, 3});

        assertThat(url).isEqualTo("http://localhost:8080/files/generated-fairytales/7/page_1.png");
        assertThat(Files.exists(tempDir.resolve("7").resolve("page_1.png"))).isTrue();
    }

    @Test
    void saveAudioLocalModeWritesFileAndReturnsServerUrl(@TempDir Path tempDir) {
        StorageProperties props = new StorageProperties();
        props.setMode("local");
        props.setLocalBasePath(tempDir.toString());
        props.setServerBaseUrl("http://localhost:8080");
        FileStorageService service = new FileStorageService(props, providerOf(null));

        String url = service.saveAudio(7L, 2, "dad", "ko", new byte[]{9});

        assertThat(url).isEqualTo("http://localhost:8080/files/generated-fairytales/7/page_2_dad_ko.mp3");
        assertThat(Files.exists(tempDir.resolve("7").resolve("page_2_dad_ko.mp3"))).isTrue();
    }

    @Test
    void saveImageCdnModeUploadsAndReturnsCdnUrl() {
        StorageProperties props = new StorageProperties();
        props.setMode("cdn");
        props.setCdnBaseUrl("https://cdn.example.com");
        S3MediaStorageClient uploader = mock(S3MediaStorageClient.class);
        FileStorageService service = new FileStorageService(props, providerOf(uploader));

        byte[] data = new byte[]{1, 2, 3};
        String url = service.saveImage(7L, 1, data);

        assertThat(url).isEqualTo("https://cdn.example.com/fairytales/7/page_1.png");
        verify(uploader).upload("fairytales/7/page_1.png", data, "image/png");
    }

    @Test
    void saveAudioCdnModeUploadsWithAudioContentType() {
        StorageProperties props = new StorageProperties();
        props.setMode("cdn");
        props.setCdnBaseUrl("https://cdn.example.com");
        S3MediaStorageClient uploader = mock(S3MediaStorageClient.class);
        FileStorageService service = new FileStorageService(props, providerOf(uploader));

        byte[] data = new byte[]{4};
        String url = service.saveAudio(7L, 3, "mom", "ja", data);

        assertThat(url).isEqualTo("https://cdn.example.com/fairytales/7/page_3_mom_ja.mp3");
        verify(uploader).upload("fairytales/7/page_3_mom_ja.mp3", data, "audio/mpeg");
    }

    @Test
    void saveImageCdnModeReturnsNullWhenUploaderMissing() {
        StorageProperties props = new StorageProperties();
        props.setMode("cdn");
        props.setCdnBaseUrl("https://cdn.example.com");
        FileStorageService service = new FileStorageService(props, providerOf(null));

        String url = service.saveImage(7L, 1, new byte[]{1});

        assertThat(url).isNull();
    }

    @Test
    void saveImageCdnModeReturnsNullWhenCdnBaseBlank() {
        StorageProperties props = new StorageProperties();
        props.setMode("cdn");
        props.setCdnBaseUrl("");
        S3MediaStorageClient uploader = mock(S3MediaStorageClient.class);
        FileStorageService service = new FileStorageService(props, providerOf(uploader));

        String url = service.saveImage(7L, 1, new byte[]{1});

        assertThat(url).isNull();
        verify(uploader, never()).upload(any(), any(), any());
    }

    @Test
    void saveVideoLocalModeWritesFileAndReturnsServerUrl(@TempDir Path tempDir) {
        StorageProperties props = new StorageProperties();
        props.setMode("local");
        props.setLocalBasePath(tempDir.toString());
        props.setServerBaseUrl("http://localhost:8080");
        FileStorageService service = new FileStorageService(props, providerOf(null));

        String url = service.saveVideo(7L, new byte[]{1, 2, 3, 4});

        assertThat(url).isEqualTo("http://localhost:8080/files/generated-fairytales/7/video.mp4");
        assertThat(Files.exists(tempDir.resolve("7").resolve("video.mp4"))).isTrue();
    }

    @Test
    void saveVideoCdnModeUploadsWithVideoContentType() {
        StorageProperties props = new StorageProperties();
        props.setMode("cdn");
        props.setCdnBaseUrl("https://cdn.example.com");
        S3MediaStorageClient uploader = mock(S3MediaStorageClient.class);
        FileStorageService service = new FileStorageService(props, providerOf(uploader));

        byte[] data = new byte[]{7, 7};
        String url = service.saveVideo(7L, data);

        assertThat(url).isEqualTo("https://cdn.example.com/fairytales/7/video.mp4");
        verify(uploader).upload("fairytales/7/video.mp4", data, "video/mp4");
    }

    @Test
    void saveVideoReturnsNullWhenDataIsNull() {
        StorageProperties props = new StorageProperties();
        props.setMode("local");
        FileStorageService service = new FileStorageService(props, providerOf(null));

        assertThat(service.saveVideo(7L, null)).isNull();
    }

    @Test
    void deleteFilesCdnModeDeletesByPrefix() {
        StorageProperties props = new StorageProperties();
        props.setMode("cdn");
        props.setCdnBaseUrl("https://cdn.example.com");
        S3MediaStorageClient uploader = mock(S3MediaStorageClient.class);
        FileStorageService service = new FileStorageService(props, providerOf(uploader));

        service.deleteFiles(7L);

        ArgumentCaptor<String> prefixCaptor = ArgumentCaptor.forClass(String.class);
        verify(uploader).deleteByPrefix(prefixCaptor.capture());
        assertThat(prefixCaptor.getValue()).isEqualTo("fairytales/7/");
    }
}

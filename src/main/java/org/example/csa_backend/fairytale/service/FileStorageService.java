package org.example.csa_backend.fairytale.service;

import lombok.extern.slf4j.Slf4j;
import org.example.csa_backend.config.S3MediaStorageClient;
import org.example.csa_backend.config.StorageProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

@Slf4j
@Service
public class FileStorageService {

    private final StorageProperties storageProperties;
    private final ObjectProvider<S3MediaStorageClient> s3ClientProvider;

    public FileStorageService(StorageProperties storageProperties,
                              ObjectProvider<S3MediaStorageClient> s3ClientProvider) {
        this.storageProperties = storageProperties;
        this.s3ClientProvider = s3ClientProvider;
    }

    public String saveImage(Long fairytaleId, int pageIndex, byte[] data) {
        if (data == null) return null;

        String filename = "page_" + pageIndex + ".png";

        if ("cdn".equalsIgnoreCase(storageProperties.getMode())) {
            return uploadToCdn(fairytaleId, filename, data, "image/png");
        }
        return saveLocally(fairytaleId, filename, data, "generated-fairytales");
    }

    public String saveAudio(Long fairytaleId, int pageIndex, String voiceType,
                            String language, byte[] data) {
        if (data == null) return null;

        String filename = "page_" + pageIndex + "_" + voiceType + "_" + language + ".mp3";

        if ("cdn".equalsIgnoreCase(storageProperties.getMode())) {
            return uploadToCdn(fairytaleId, filename, data, "audio/mpeg");
        }
        return saveLocally(fairytaleId, filename, data, "generated-fairytales");
    }

    public String saveVideo(Long fairytaleId, byte[] data) {
        if (data == null) return null;

        String filename = "video.mp4";

        if ("cdn".equalsIgnoreCase(storageProperties.getMode())) {
            return uploadToCdn(fairytaleId, filename, data, "video/mp4");
        }
        return saveLocally(fairytaleId, filename, data, "generated-fairytales");
    }

    public void deleteFiles(Long fairytaleId) {
        if ("cdn".equalsIgnoreCase(storageProperties.getMode())) {
            S3MediaStorageClient client = s3ClientProvider.getIfAvailable();
            if (client == null) {
                log.warn("CDN 업로더가 등록되지 않아 파일 삭제를 건너뜁니다: fairytaleId={}", fairytaleId);
                return;
            }
            try {
                client.deleteByPrefix("fairytales/" + fairytaleId + "/");
            } catch (RuntimeException e) {
                log.error("CDN 파일 삭제 실패: fairytaleId={}", fairytaleId, e);
            }
            return;
        }

        Path dir = Paths.get(storageProperties.getLocalBasePath(), fairytaleId.toString());
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    log.warn("파일 삭제 실패: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.error("동화 파일 삭제 실패: fairytaleId={}", fairytaleId, e);
        }
    }

    private String saveLocally(Long fairytaleId, String filename, byte[] data,
                                String subDir) {
        String basePath = storageProperties.getLocalBasePath();
        Path dir = Paths.get(basePath, fairytaleId.toString());

        try {
            Files.createDirectories(dir);
            Path filePath = dir.resolve(filename);
            Files.write(filePath, data);

            String serverBase = storageProperties.getServerBaseUrl();
            return serverBase + "/files/" + subDir + "/" + fairytaleId + "/" + filename;
        } catch (IOException e) {
            log.error("파일 저장 실패: fairytaleId={}, filename={}", fairytaleId, filename, e);
            return null;
        }
    }

    private String uploadToCdn(Long fairytaleId, String filename, byte[] data, String contentType) {
        String cdnBase = storageProperties.getCdnBaseUrl();
        if (cdnBase == null || cdnBase.isBlank()) {
            log.warn("CDN base URL이 설정되지 않았습니다. storage.cdn-base-url을 설정하세요.");
            return null;
        }

        S3MediaStorageClient client = s3ClientProvider.getIfAvailable();
        if (client == null) {
            log.warn("CDN 업로더가 등록되지 않았습니다. storage.s3 설정을 확인하세요.");
            return null;
        }

        String key = "fairytales/" + fairytaleId + "/" + filename;
        try {
            client.upload(key, data, contentType);
        } catch (RuntimeException e) {
            log.error("CDN 업로드 실패: fairytaleId={}, filename={}", fairytaleId, filename, e);
            return null;
        }
        return cdnBase + "/fairytales/" + fairytaleId + "/" + filename;
    }
}

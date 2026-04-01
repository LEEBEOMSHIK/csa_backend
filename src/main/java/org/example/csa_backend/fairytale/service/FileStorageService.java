package org.example.csa_backend.fairytale.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.csa_backend.config.StorageProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final StorageProperties storageProperties;

    public String saveImage(Long fairytaleId, int pageIndex, byte[] data) {
        if (data == null) return null;

        if ("cdn".equalsIgnoreCase(storageProperties.getMode())) {
            return uploadToCdn("image", fairytaleId, pageIndex, null, null, data, ".png");
        }
        return saveLocally(fairytaleId, "page_" + pageIndex + ".png", data,
                "generated-fairytales");
    }

    public String saveAudio(Long fairytaleId, int pageIndex, String voiceType,
                            String language, byte[] data) {
        if (data == null) return null;

        String filename = "page_" + pageIndex + "_" + voiceType + "_" + language + ".mp3";

        if ("cdn".equalsIgnoreCase(storageProperties.getMode())) {
            return uploadToCdn("audio", fairytaleId, pageIndex, voiceType, language, data, ".mp3");
        }
        return saveLocally(fairytaleId, filename, data, "generated-fairytales");
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

    private String uploadToCdn(String type, Long fairytaleId, int pageIndex,
                                String voiceType, String language, byte[] data, String ext) {
        // prod 환경 CDN 업로드 (추후 구현 — S3, GCS 등 연동)
        log.info("CDN 업로드 준비: type={}, fairytaleId={}, pageIndex={}", type, fairytaleId, pageIndex);
        String cdnBase = storageProperties.getCdnBaseUrl();
        if (cdnBase.isBlank()) {
            log.warn("CDN base URL이 설정되지 않았습니다. storage.cdn-base-url을 설정하세요.");
            return null;
        }
        // TODO: 실제 CDN 업로드 구현 (AWS S3 / GCS SDK 연동)
        String filename = voiceType != null
                ? "page_" + pageIndex + "_" + voiceType + "_" + language + ext
                : "page_" + pageIndex + ext;
        return cdnBase + "/fairytales/" + fairytaleId + "/" + filename;
    }
}

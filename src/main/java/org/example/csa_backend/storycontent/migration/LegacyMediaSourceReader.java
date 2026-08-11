package org.example.csa_backend.storycontent.migration;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.example.csa_backend.config.S3MediaStorageClient;
import org.example.csa_backend.config.StorageProperties;
import org.example.csa_backend.storycontent.PublishedMediaStorage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LegacyMediaSourceReader {

    private final URI canonicalPublicBase;
    private final PublishedMediaStorage publishedMediaStorage;
    private final String legacyMode;
    private final Path legacyLocalRoot;
    private final URI legacyLocalPublicBase;
    private final URI legacyCdnPublicBase;
    private final ObjectProvider<S3MediaStorageClient> legacyS3Provider;

    public LegacyMediaSourceReader(
        @Value("${csa.media.public-base-url:http://localhost:18080/uploads}")
        String canonicalPublicBaseUrl,
        PublishedMediaStorage publishedMediaStorage,
        StorageProperties storageProperties,
        ObjectProvider<S3MediaStorageClient> legacyS3Provider
    ) {
        this.canonicalPublicBase = publicBase(canonicalPublicBaseUrl);
        this.publishedMediaStorage = publishedMediaStorage;
        this.legacyMode = normalizedMode(storageProperties.getMode());
        this.legacyLocalRoot = Path.of(storageProperties.getLocalBasePath()).toAbsolutePath().normalize();
        this.legacyLocalPublicBase = publicBase(
            stripTrailingSlash(storageProperties.getServerBaseUrl())
                + "/files/generated-fairytales"
        );
        this.legacyCdnPublicBase = isBlank(storageProperties.getCdnBaseUrl())
            ? null
            : publicBase(storageProperties.getCdnBaseUrl());
        this.legacyS3Provider = legacyS3Provider;
    }

    public byte[] read(String sourceUrl) {
        URI source = sourceUri(sourceUrl);
        String canonicalKey = knownKey(source, canonicalPublicBase);
        if (canonicalKey != null) {
            return readCanonical(canonicalKey, sourceUrl);
        }
        if ("cdn".equals(legacyMode)) {
            String legacyKey = knownKey(source, legacyCdnPublicBase);
            if (legacyKey != null && legacyKey.startsWith("fairytales/")) {
                return readLegacyS3(legacyKey, sourceUrl);
            }
        } else if ("local".equals(legacyMode)) {
            String legacyKey = knownKey(source, legacyLocalPublicBase);
            if (legacyKey != null) {
                return readLegacyLocal(legacyKey, sourceUrl);
            }
        }
        throw new LegacyImportException("LEGACY_MEDIA_URL_UNSUPPORTED", sourceUrl);
    }

    private byte[] readCanonical(String storageKey, String sourceUrl) {
        try {
            return requireBytes(publishedMediaStorage.read(storageKey), sourceUrl);
        } catch (IOException | IllegalArgumentException exception) {
            throw new LegacyImportException("LEGACY_MEDIA_PREFLIGHT_FAILED", sourceUrl, exception);
        }
    }

    private byte[] readLegacyS3(String storageKey, String sourceUrl) {
        S3MediaStorageClient client = legacyS3Provider.getIfAvailable();
        if (client == null) {
            throw new LegacyImportException("LEGACY_MEDIA_SOURCE_UNAVAILABLE", sourceUrl);
        }
        try {
            return requireBytes(client.read(storageKey), sourceUrl);
        } catch (LegacyImportException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new LegacyImportException("LEGACY_MEDIA_PREFLIGHT_FAILED", sourceUrl, exception);
        }
    }

    private byte[] readLegacyLocal(String storageKey, String sourceUrl) {
        Path target = legacyLocalRoot.resolve(storageKey).normalize();
        if (!target.startsWith(legacyLocalRoot)) {
            throw new LegacyImportException("MEDIA_STORAGE_KEY_ESCAPE", storageKey);
        }
        try {
            return requireBytes(Files.readAllBytes(
                LegacyMediaPathGuard.requireReadableFile(legacyLocalRoot, target)
            ), sourceUrl);
        } catch (IOException | IllegalArgumentException exception) {
            throw new LegacyImportException("LEGACY_MEDIA_PREFLIGHT_FAILED", sourceUrl, exception);
        }
    }

    private byte[] requireBytes(byte[] bytes, String sourceUrl) {
        if (bytes == null) {
            throw new LegacyImportException("LEGACY_MEDIA_NOT_FOUND", sourceUrl);
        }
        if (bytes.length == 0) {
            throw new LegacyImportException("LEGACY_MEDIA_EMPTY", sourceUrl);
        }
        return bytes.clone();
    }

    private String knownKey(URI source, URI configuredBase) {
        if (configuredBase == null || !sameOriginWhenAbsolute(source, configuredBase)) {
            return null;
        }
        String sourcePath = source.getPath();
        String basePath = stripTrailingSlash(configuredBase.getPath());
        if (!sourcePath.startsWith(basePath + "/")) {
            return null;
        }
        String key = sourcePath.substring(basePath.length() + 1);
        validateStorageKey(key);
        return key;
    }

    private boolean sameOriginWhenAbsolute(URI source, URI configuredBase) {
        if (!source.isAbsolute()) {
            return source.getRawAuthority() == null;
        }
        return configuredBase.isAbsolute()
            && source.getScheme().equalsIgnoreCase(configuredBase.getScheme())
            && equalIgnoreCase(source.getHost(), configuredBase.getHost())
            && effectivePort(source) == effectivePort(configuredBase);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private URI sourceUri(String sourceUrl) {
        if (isBlank(sourceUrl)) {
            throw new LegacyImportException("LEGACY_MEDIA_URL_REQUIRED", sourceUrl);
        }
        if (sourceUrl.contains("\\")) {
            throw new LegacyImportException("LEGACY_MEDIA_PREFLIGHT_FAILED", sourceUrl);
        }
        try {
            URI source = URI.create(sourceUrl.trim());
            if (source.isOpaque() || source.getPath() == null || source.getPath().isBlank()
                || source.getRawQuery() != null || source.getRawFragment() != null
                || source.getRawUserInfo() != null || source.getRawPath().contains("%")) {
                throw new LegacyImportException("LEGACY_MEDIA_URL_INVALID", sourceUrl);
            }
            if (source.isAbsolute() && !("http".equalsIgnoreCase(source.getScheme())
                || "https".equalsIgnoreCase(source.getScheme()))) {
                throw new LegacyImportException("LEGACY_MEDIA_URL_UNSUPPORTED", sourceUrl);
            }
            return source;
        } catch (LegacyImportException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new LegacyImportException("LEGACY_MEDIA_URL_INVALID", sourceUrl, exception);
        }
    }

    private URI publicBase(String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("Media public base URL is required");
        }
        URI base = URI.create(stripTrailingSlash(value.trim()));
        if (base.isOpaque() || base.getPath() == null || base.getPath().isBlank()
            || base.getRawQuery() != null || base.getRawFragment() != null
            || base.getRawUserInfo() != null) {
            throw new IllegalArgumentException("Media public base URL is invalid");
        }
        return base;
    }

    private void validateStorageKey(String key) {
        if (isBlank(key) || key.indexOf('\0') >= 0 || key.contains("\\")
            || key.startsWith("/") || key.endsWith("/")) {
            throw new LegacyImportException("MEDIA_STORAGE_KEY_INVALID", key);
        }
        for (String segment : key.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new LegacyImportException("MEDIA_STORAGE_KEY_ESCAPE", key);
            }
        }
    }

    private static String normalizedMode(String mode) {
        return mode == null ? "local" : mode.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String stripTrailingSlash(String value) {
        String stripped = value;
        while (stripped.endsWith("/") && stripped.length() > 1) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return stripped;
    }

    private static boolean equalIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}

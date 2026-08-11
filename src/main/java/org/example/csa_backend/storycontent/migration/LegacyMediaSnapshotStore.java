package org.example.csa_backend.storycontent.migration;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.example.csa_backend.storycontent.AssetKind;
import org.example.csa_backend.storycontent.PublishedMediaRoute;
import org.example.csa_backend.storycontent.PublishedMediaStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LegacyMediaSnapshotStore {

    private final String publicBaseUrl;
    private final ContractChecksum checksum;
    private final PublishedMediaRoute publishedMediaRoute;
    private final PublishedMediaStorage publishedMediaStorage;
    private final LegacyMediaSourceReader sourceReader;

    public LegacyMediaSnapshotStore(
        @Value("${csa.media.public-base-url:http://localhost:18080/uploads}") String publicBaseUrl,
        ContractChecksum checksum,
        PublishedMediaRoute publishedMediaRoute,
        PublishedMediaStorage publishedMediaStorage,
        LegacyMediaSourceReader sourceReader
    ) {
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.checksum = checksum;
        this.publishedMediaRoute = publishedMediaRoute;
        this.publishedMediaStorage = publishedMediaStorage;
        this.sourceReader = sourceReader;
    }

    public PreparedImport prepare(LegacyProjection projection) {
        Map<String, SourceAsset> sources = new LinkedHashMap<>();
        for (LegacyProjection.SceneProjection scene : projection.scenes()) {
            String imageKey = scene.sceneKey() + "-image";
            sources.put(imageKey, inspect(imageKey, AssetKind.IMAGE, scene.imageUrl()));
            for (LegacyProjection.AudioProjection audio : scene.audios()) {
                String assetKey = scene.sceneKey() + "-audio-" + safeSegment(audio.voiceType())
                    + "-" + safeSegment(audio.locale());
                sources.put(assetKey, inspect(assetKey, AssetKind.AUDIO, audio.audioUrl()));
            }
        }
        if (projection.video() != null) {
            sources.put("video", inspect("video", AssetKind.VIDEO, projection.video().videoUrl()));
        }

        String combinedSourceHash = combinedSourceHash(projection.sourceHash(), sources);
        LegacyProjection fingerprintedProjection = projection.withSourceHash(combinedSourceHash);
        Map<String, PreparedAsset> assets = new LinkedHashMap<>();
        for (SourceAsset source : sources.values().stream()
            .sorted(java.util.Comparator.comparing(SourceAsset::assetKey))
            .toList()) {
            String storageKey = baseKey(fingerprintedProjection) + "/"
                + safeSegment(source.assetKey()) + source.extension();
            assets.put(source.assetKey(), new PreparedAsset(
                source.assetKey(),
                source.kind(),
                storageKey,
                publicUrl(storageKey),
                source.sha256(),
                source.mimeType(),
                source.bytes().length
            ));
        }
        return new PreparedImport(fingerprintedProjection, new PreparedMedia(assets), sources);
    }

    public void materialize(PreparedImport preparedImport) {
        for (Map.Entry<String, PreparedAsset> entry : preparedImport.media().assets().entrySet()) {
            SourceAsset source = preparedImport.source(entry.getKey());
            if (!entry.getValue().sha256().equals(source.sha256())) {
                throw new LegacyImportException("LEGACY_MEDIA_PREFLIGHT_CHANGED", entry.getKey());
            }
            writeImmutable(
                entry.getValue().storageKey(),
                source.bytes(),
                entry.getValue().mimeType()
            );
        }
    }

    public PreparedAsset writeManifest(LegacyProjection projection, long storyId, long versionId, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new LegacyImportException("MANIFEST_EMPTY", Long.toString(projection.legacyId()));
        }
        String storageKey = baseKey(projection) + "/story-" + storyId + "-version-" + versionId + ".json";
        writeImmutable(storageKey, bytes, "application/json");
        return new PreparedAsset(
            "manifest",
            AssetKind.MANIFEST,
            storageKey,
            publicUrl(storageKey),
            checksum.ofBytes(bytes),
            "application/json",
            bytes.length
        );
    }

    private SourceAsset inspect(
        String assetKey,
        AssetKind kind,
        String sourceUrl
    ) {
        byte[] bytes = readSource(sourceUrl);
        if (bytes.length == 0) {
            throw new LegacyImportException("LEGACY_MEDIA_EMPTY", sourceUrl);
        }
        String extension = extension(sourceUrl, kind);
        return new SourceAsset(
            assetKey,
            kind,
            checksum.ofBytes(bytes),
            extension,
            mimeType(extension, kind),
            bytes
        );
    }

    private String combinedSourceHash(String projectionSourceHash, Map<String, SourceAsset> sources) {
        if (sources.isEmpty()) {
            return projectionSourceHash;
        }
        List<String> parts = new ArrayList<>();
        parts.add("LEGACY_MEDIA_FINGERPRINT_V1");
        parts.add(projectionSourceHash);
        sources.values().stream()
            .sorted(java.util.Comparator.comparing(SourceAsset::assetKey))
            .forEach(source -> {
                parts.add(source.assetKey());
                parts.add(source.kind().name());
                parts.add(source.sha256());
            });
        return checksum.ofParts(parts);
    }

    private byte[] readSource(String sourceUrl) {
        return sourceReader.read(sourceUrl);
    }

    private void writeImmutable(String storageKey, byte[] bytes, String contentType) {
        try {
            publishedMediaStorage.writeImmutable(storageKey, bytes, contentType);
        } catch (PublishedMediaStorage.ImmutableObjectConflictException exception) {
            throw new LegacyImportException("IMMUTABLE_MEDIA_CONFLICT", storageKey, exception);
        } catch (IOException exception) {
            throw new LegacyImportException("CANONICAL_MEDIA_WRITE_FAILED", storageKey, exception);
        }
    }

    private String baseKey(LegacyProjection projection) {
        return publishedMediaRoute.promotedStorageKey(
            "imports/" + projection.legacyType().name().toLowerCase(Locale.ROOT)
                + "/" + projection.legacyId() + "/" + projection.sourceHash()
        );
    }

    private String publicUrl(String storageKey) {
        return publicBaseUrl + "/" + storageKey;
    }

    private String extension(String sourceUrl, AssetKind kind) {
        String path = URI.create(sourceUrl).getPath();
        int dot = path == null ? -1 : path.lastIndexOf('.');
        String extension = dot < 0 ? "" : path.substring(dot).toLowerCase(Locale.ROOT);
        if (!extension.matches("\\.[a-z0-9]{1,8}")) {
            return switch (kind) {
                case IMAGE -> ".png";
                case AUDIO -> ".mp3";
                case VIDEO -> ".mp4";
                case CAPTION -> ".vtt";
                case MANIFEST -> ".json";
            };
        }
        return extension;
    }

    private String mimeType(String extension, AssetKind kind) {
        return switch (extension) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".webp" -> "image/webp";
            case ".gif" -> "image/gif";
            case ".mp3" -> "audio/mpeg";
            case ".wav" -> "audio/wav";
            case ".m4a" -> "audio/mp4";
            case ".mp4" -> "video/mp4";
            case ".vtt" -> "text/vtt";
            case ".json" -> "application/json";
            default -> switch (kind) {
                case IMAGE -> "image/png";
                case AUDIO -> "audio/mpeg";
                case VIDEO -> "video/mp4";
                case CAPTION -> "text/vtt";
                case MANIFEST -> "application/json";
            };
        };
    }

    private String safeSegment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]+")) {
            throw new LegacyImportException("MEDIA_ASSET_KEY_INVALID", value);
        }
        return value;
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("csa.media.public-base-url is required");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record PreparedMedia(Map<String, PreparedAsset> assets) {
        public PreparedMedia {
            Map<String, PreparedAsset> ordered = new LinkedHashMap<>();
            assets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
            assets = Collections.unmodifiableMap(ordered);
        }
    }

    public static final class PreparedImport {

        private final LegacyProjection projection;
        private final PreparedMedia media;
        private final Map<String, SourceAsset> sources;

        private PreparedImport(
            LegacyProjection projection,
            PreparedMedia media,
            Map<String, SourceAsset> sources
        ) {
            this.projection = projection;
            this.media = media;
            this.sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
        }

        public LegacyProjection projection() {
            return projection;
        }

        public PreparedMedia media() {
            return media;
        }

        private SourceAsset source(String assetKey) {
            SourceAsset source = sources.get(assetKey);
            if (source == null) {
                throw new LegacyImportException("CANONICAL_ASSET_MISSING", assetKey);
            }
            return source;
        }
    }

    private record SourceAsset(
        String assetKey,
        AssetKind kind,
        String sha256,
        String extension,
        String mimeType,
        byte[] bytes
    ) {
        private SourceAsset {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public record PreparedAsset(
        String assetKey,
        AssetKind kind,
        String storageKey,
        String publicUrl,
        String sha256,
        String mimeType,
        long byteSize
    ) {
    }
}

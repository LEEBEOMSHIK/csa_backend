package org.example.csa_backend.storycontent;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PublishedMediaRoute {

    private final String prefix;

    public PublishedMediaRoute(@Value("${csa.media.prefix:}") String prefix) {
        this.prefix = normalizePrefix(prefix);
    }

    public String requestPattern() {
        return prefix.isBlank()
            ? "/uploads/story-assets/**"
            : "/uploads/" + prefix + "/story-assets/**";
    }

    public String resourceLocation(Path storageRoot) {
        Path promotedRoot = promotedRoot(storageRoot);
        String location = promotedRoot.toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }

    public Path promotedRoot(Path storageRoot) {
        Path root = storageRoot.toAbsolutePath().normalize();
        Path promotedRoot = prefix.isBlank()
            ? root.resolve("story-assets").normalize()
            : root.resolve(prefix).resolve("story-assets").normalize();
        if (!promotedRoot.startsWith(root)) {
            throw new IllegalArgumentException("Published media prefix escapes configured root");
        }
        return promotedRoot;
    }

    private String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace('\\', '/').replaceAll("^/+|/+$", "");
        if (normalized.contains("..") || normalized.isBlank()) {
            throw new IllegalArgumentException("Invalid media prefix");
        }
        return normalized;
    }
}

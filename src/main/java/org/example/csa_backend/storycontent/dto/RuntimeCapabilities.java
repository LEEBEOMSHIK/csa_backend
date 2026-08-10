package org.example.csa_backend.storycontent.dto;

import java.util.List;
import java.util.Locale;

public record RuntimeCapabilities(
    String rendition,
    String locale,
    String voiceType,
    List<Integer> supportedRuntimeSchemaVersions,
    List<String> supportedRenditions,
    Integer rendererVersion
) {
    public RuntimeCapabilities {
        rendition = normalizeRendition(rendition, "SLIDE");
        locale = locale == null || locale.isBlank() ? "ko" : locale.trim();
        voiceType = voiceType == null || voiceType.isBlank() ? null : voiceType.trim();
        supportedRuntimeSchemaVersions = supportedRuntimeSchemaVersions == null
            || supportedRuntimeSchemaVersions.isEmpty()
            ? List.of(1)
            : List.copyOf(supportedRuntimeSchemaVersions);
        supportedRenditions = supportedRenditions == null || supportedRenditions.isEmpty()
            ? List.of("SLIDE")
            : supportedRenditions.stream().map(value -> normalizeRendition(value, "")).distinct().toList();
        rendererVersion = rendererVersion == null ? 1 : rendererVersion;
    }

    public boolean supportsSchema(int schemaVersion) {
        return supportedRuntimeSchemaVersions.contains(schemaVersion);
    }

    public boolean supportsRendition(String value) {
        return supportedRenditions.contains(normalizeRendition(value, ""));
    }

    private static String normalizeRendition(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }
}

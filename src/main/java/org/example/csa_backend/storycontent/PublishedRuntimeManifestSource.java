package org.example.csa_backend.storycontent;

public record PublishedRuntimeManifestSource(
    long contentVersionId,
    long storyId,
    Rendition compatibilitySlide
) {
}

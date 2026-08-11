package org.example.csa_backend.storycontent.migration;

public enum ContentWriteKind {
    LEGACY_AI(true),
    LEGACY_CURATED(true),
    CANONICAL_AUTHORING(false),
    CANONICAL_ASSET(false),
    CANONICAL_REVIEW(false),
    CANONICAL_PUBLISH(false);

    private final boolean legacy;

    ContentWriteKind(boolean legacy) {
        this.legacy = legacy;
    }

    public boolean isLegacy() {
        return legacy;
    }
}

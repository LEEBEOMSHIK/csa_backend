package org.example.csa_backend.storycontent.migration;

public record ImportBatchResult(
    int imported,
    int unchanged,
    long nextLegacyId,
    boolean complete
) {
}

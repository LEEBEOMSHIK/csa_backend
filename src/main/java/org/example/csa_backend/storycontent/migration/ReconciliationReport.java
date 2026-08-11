package org.example.csa_backend.storycontent.migration;

import java.util.List;

public record ReconciliationReport(
    boolean complete,
    int legacyCount,
    int linkedCount,
    List<String> missingLinks,
    List<String> unexpectedLinks,
    List<String> hashMismatches,
    long openMismatchCount,
    String checksum
) {
    public ReconciliationReport {
        missingLinks = List.copyOf(missingLinks);
        unexpectedLinks = List.copyOf(unexpectedLinks);
        hashMismatches = List.copyOf(hashMismatches);
    }
}

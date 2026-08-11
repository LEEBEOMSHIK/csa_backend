package org.example.csa_backend.storycontent;

import org.example.csa_backend.storycontent.migration.ContractChecksum;
import org.example.csa_backend.storycontent.migration.LegacyImportException;
import org.example.csa_backend.storycontent.migration.LegacyMediaSourceReader;
import org.springframework.stereotype.Component;

@Component
final class LegacyContractMediaIdentity {

    private final LegacyMediaSourceReader sourceReader;
    private final ContractChecksum checksum;

    LegacyContractMediaIdentity(
        LegacyMediaSourceReader sourceReader,
        ContractChecksum checksum
    ) {
        this.sourceReader = sourceReader;
        this.checksum = checksum;
    }

    String fromLegacyUrl(String sourceUrl) {
        if (sourceUrl == null) {
            return null;
        }
        if (sourceUrl.isBlank()) {
            throw new LegacyImportException("SHADOW_MEDIA_URL_INVALID", sourceUrl);
        }
        return "sha256:" + checksum.ofBytes(sourceReader.read(sourceUrl));
    }

    String fromCanonicalSha(String sha256) {
        if (sha256 == null) {
            return null;
        }
        String normalized = sha256.trim();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new LegacyImportException("SHADOW_MEDIA_SHA_INVALID", sha256);
        }
        return "sha256:" + normalized;
    }
}

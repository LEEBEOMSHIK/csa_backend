package org.example.csa_backend.storycontent.migration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ShadowCompareResult(
    boolean matches,
    String legacyChecksum,
    String canonicalChecksum,
    Map<String, Object> diff
) {
    public ShadowCompareResult {
        diff = Collections.unmodifiableMap(new LinkedHashMap<>(diff));
    }

    static ShadowCompareResult match(String checksum) {
        return new ShadowCompareResult(true, checksum, checksum, Map.of());
    }

    static ShadowCompareResult mismatch(
        String legacyChecksum,
        String canonicalChecksum,
        Map<String, Object> diff
    ) {
        return new ShadowCompareResult(false, legacyChecksum, canonicalChecksum, diff);
    }
}

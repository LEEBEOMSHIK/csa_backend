package org.example.csa_backend.storycontent;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LegacyCuratedReadAdapter implements LegacyFairytaleReadAdapter {

    private final LegacyAdapterSnapshots snapshots;

    public LegacyCuratedReadAdapter(LegacyAdapterSnapshots snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public LegacyType legacyType() {
        return LegacyType.CURATED;
    }

    @Override
    @Transactional(readOnly = true)
    public Object readLegacy(long legacyId) {
        return snapshots.fromLegacy(legacyType(), legacyId);
    }

    @Override
    @Transactional(readOnly = true)
    public Object readCanonical(long legacyId) {
        return snapshots.fromCanonical(legacyType(), legacyId);
    }
}

package org.example.csa_backend.storycontent.migration;

import org.example.csa_backend.storycontent.ContentSource;
import org.example.csa_backend.storycontent.MigrationState;

public record CutoverResult(
    long epoch,
    ContentSource readSource,
    ContentSource writeSource,
    MigrationState state,
    String checksum
) {
    public static CutoverResult prepared(long epoch, String checksum) {
        return new CutoverResult(
            epoch,
            ContentSource.CANONICAL,
            ContentSource.CANONICAL,
            MigrationState.CUTOVER_PENDING,
            checksum
        );
    }

    public static CutoverResult open(long epoch, String checksum) {
        return new CutoverResult(
            epoch,
            ContentSource.CANONICAL,
            ContentSource.CANONICAL,
            MigrationState.OPEN,
            checksum
        );
    }

    public static CutoverResult rolledBack(long epoch, String checksum) {
        return new CutoverResult(
            epoch,
            ContentSource.LEGACY,
            ContentSource.LEGACY,
            MigrationState.OPEN,
            checksum
        );
    }
}

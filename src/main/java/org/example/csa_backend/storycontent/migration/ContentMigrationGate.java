package org.example.csa_backend.storycontent.migration;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.storycontent.ContentMigrationControl;
import org.example.csa_backend.storycontent.ContentMigrationControlRepository;
import org.example.csa_backend.storycontent.ContentSource;
import org.example.csa_backend.storycontent.MigrationState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentMigrationGate {

    private final ContentMigrationControlRepository controlRepository;

    @Transactional(readOnly = true)
    public void assertWritesAllowed(ContentWriteKind kind) {
        ContentMigrationControl control = controlRepository.getSingleton();
        if (control.getState() != MigrationState.OPEN) {
            throw ContentMigrationException.serviceUnavailable(
                "CONTENT_MIGRATION_FREEZE",
                control.getBarrierEpoch()
            );
        }
        if (kind.isLegacy() && control.getWriteSource() != ContentSource.LEGACY) {
            throw ContentMigrationException.conflict("LEGACY_WRITE_DISABLED", control.getBarrierEpoch());
        }
        if (!kind.isLegacy() && control.getWriteSource() != ContentSource.CANONICAL) {
            throw ContentMigrationException.conflict("CANONICAL_WRITE_DISABLED", control.getBarrierEpoch());
        }
    }
}

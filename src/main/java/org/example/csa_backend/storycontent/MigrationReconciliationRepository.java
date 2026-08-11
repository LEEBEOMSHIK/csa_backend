package org.example.csa_backend.storycontent;

import org.example.csa_backend.storycontent.migration.ContentMigrationException;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationReconciliationRepository extends JpaRepository<MigrationReconciliation, Long> {

    default MigrationReconciliation requireSuccessful(long epoch) {
        return findById(epoch)
            .filter(row -> row.getStatus() == ReconciliationStatus.SUCCEEDED)
            .orElseThrow(() -> ContentMigrationException.conflict("RECONCILIATION_REQUIRED", epoch));
    }
}

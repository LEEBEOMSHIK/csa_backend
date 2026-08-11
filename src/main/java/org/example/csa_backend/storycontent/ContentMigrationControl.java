package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import org.example.csa_backend.storycontent.migration.ContentMigrationException;

@Getter
@Entity
@Table(name = "content_migration_control")
public class ContentMigrationControl {
    @Id
    @Column(name = "singleton_id")
    private Short singletonId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 24)
    private MigrationState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "read_source", nullable = false, length = 16)
    private ContentSource readSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "write_source", nullable = false, length = 16)
    private ContentSource writeSource;

    @Column(name = "barrier_epoch", nullable = false)
    private long barrierEpoch;

    @Column(name = "backend_ack_epoch")
    private Long backendAckEpoch;

    @Column(name = "admin_backend_ack_epoch")
    private Long adminBackendAckEpoch;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "reconciliation_hash", length = 64, columnDefinition = "char(64)")
    private String reconciliationHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "smoke_hash", length = 64, columnDefinition = "char(64)")
    private String smokeHash;

    @Column(name = "smoke_passed_at")
    private Instant smokePassedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public long requestFreeze(Instant now) {
        if (state != MigrationState.OPEN
            || readSource != ContentSource.LEGACY
            || writeSource != ContentSource.LEGACY) {
            throw ContentMigrationException.conflict("MIGRATION_FREEZE_NOT_ALLOWED", barrierEpoch);
        }
        barrierEpoch++;
        state = MigrationState.FREEZE_REQUESTED;
        backendAckEpoch = null;
        adminBackendAckEpoch = null;
        reconciliationHash = null;
        smokeHash = null;
        smokePassedAt = null;
        updatedAt = now;
        return barrierEpoch;
    }

    public void acknowledge(String serviceInstance, long epoch) {
        acknowledge(serviceInstance, epoch, Instant.now());
    }

    public void acknowledge(String serviceInstance, long epoch, Instant now) {
        if (state != MigrationState.FREEZE_REQUESTED || barrierEpoch != epoch) {
            throw new IllegalStateException("MIGRATION_EPOCH_NOT_FREEZING");
        }
        switch (serviceInstance) {
            case "csa_backend" -> backendAckEpoch = epoch;
            case "csa_adm_backend" -> adminBackendAckEpoch = epoch;
            default -> throw new IllegalArgumentException("UNKNOWN_CONTENT_SERVICE_IDENTITY");
        }
        if (Long.valueOf(epoch).equals(backendAckEpoch) && Long.valueOf(epoch).equals(adminBackendAckEpoch)) {
            state = MigrationState.FROZEN;
        }
        updatedAt = now;
    }

    public void assertBothBackendsFrozen(long epoch) {
        assertEpoch(epoch);
        if (!Long.valueOf(epoch).equals(backendAckEpoch)) {
            throw ContentMigrationException.conflict("CSA_BACKEND_ACK_REQUIRED", epoch);
        }
        if (!Long.valueOf(epoch).equals(adminBackendAckEpoch)) {
            throw ContentMigrationException.conflict("CSA_ADM_BACKEND_ACK_REQUIRED", epoch);
        }
        if (state != MigrationState.FROZEN) {
            throw ContentMigrationException.conflict("CONTENT_MIGRATION_FROZEN_REQUIRED", epoch);
        }
    }

    public void prepareCanonicalCutover(long epoch, String checksum, Instant now) {
        assertBothBackendsFrozen(epoch);
        readSource = ContentSource.CANONICAL;
        writeSource = ContentSource.CANONICAL;
        state = MigrationState.CUTOVER_PENDING;
        reconciliationHash = checksum;
        updatedAt = now;
    }

    public void recordFinalReconciliation(long epoch, String checksum, Instant now) {
        assertBothBackendsFrozen(epoch);
        reconciliationHash = checksum;
        updatedAt = now;
    }

    public void assertCutoverPending(long epoch) {
        assertEpoch(epoch);
        if (state != MigrationState.CUTOVER_PENDING
            || readSource != ContentSource.CANONICAL
            || writeSource != ContentSource.CANONICAL) {
            throw ContentMigrationException.conflict("CUTOVER_PENDING_REQUIRED", epoch);
        }
    }

    public void recordSmoke(long epoch, String checksum, Instant now) {
        assertCutoverPending(epoch);
        smokeHash = checksum;
        smokePassedAt = now;
        updatedAt = now;
    }

    public void assertPassingSmoke(long epoch, String reconciliationChecksum) {
        assertCutoverPending(epoch);
        if (reconciliationHash == null || !reconciliationHash.trim().equals(reconciliationChecksum.trim())) {
            throw ContentMigrationException.conflict("RECONCILIATION_REQUIRED", epoch);
        }
        if (smokeHash == null || smokePassedAt == null) {
            throw ContentMigrationException.conflict("CUTOVER_SMOKE_REQUIRED", epoch);
        }
    }

    public void openCanonicalWrites(long epoch, Instant now) {
        assertCutoverPending(epoch);
        if (smokeHash == null || smokePassedAt == null) {
            throw ContentMigrationException.conflict("CUTOVER_SMOKE_REQUIRED", epoch);
        }
        state = MigrationState.OPEN;
        updatedAt = now;
    }

    public void rollbackToLegacy(long epoch, Instant now) {
        assertEpoch(epoch);
        if (state == MigrationState.OPEN && writeSource == ContentSource.CANONICAL) {
            throw ContentMigrationException.conflict("CANONICAL_WRITES_ALREADY_OPEN", epoch);
        }
        readSource = ContentSource.LEGACY;
        writeSource = ContentSource.LEGACY;
        state = MigrationState.OPEN;
        smokeHash = null;
        smokePassedAt = null;
        updatedAt = now;
    }

    private void assertEpoch(long epoch) {
        if (barrierEpoch != epoch) {
            throw ContentMigrationException.conflict("CONTENT_MIGRATION_EPOCH_MISMATCH", barrierEpoch);
        }
    }
}

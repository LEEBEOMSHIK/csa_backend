package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;

@Getter
@Entity
@Table(name = "content_migration_reconciliations")
public class MigrationReconciliation {
    @Id
    @Column(name = "epoch")
    private Long epoch;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReconciliationStatus status;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "checksum", nullable = false, length = 64, columnDefinition = "char(64)")
    private String checksum;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> reportJson;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    public static MigrationReconciliation completed(
        long epoch,
        ReconciliationStatus status,
        String checksum,
        Map<String, Object> reportJson,
        Instant completedAt
    ) {
        MigrationReconciliation reconciliation = new MigrationReconciliation();
        reconciliation.epoch = epoch;
        reconciliation.status = status;
        reconciliation.checksum = checksum;
        reconciliation.reportJson = Map.copyOf(reportJson);
        reconciliation.completedAt = completedAt;
        return reconciliation;
    }
}

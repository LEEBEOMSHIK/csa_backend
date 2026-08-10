package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;

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
}

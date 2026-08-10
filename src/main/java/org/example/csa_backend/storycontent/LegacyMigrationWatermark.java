package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;

@Getter
@Entity
@Table(name = "legacy_migration_watermarks")
public class LegacyMigrationWatermark {
    @Id
    @Column(name = "migration_kind", length = 32)
    private String migrationKind;

    @Column(name = "watermark_at", nullable = false)
    private Instant watermarkAt;

    @Column(name = "last_legacy_id", nullable = false)
    private long lastLegacyId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

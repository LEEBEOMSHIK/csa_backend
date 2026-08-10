package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;

@Getter
@Entity
@Table(name = "legacy_shadow_mismatches")
public class LegacyShadowMismatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "legacy_type", nullable = false, length = 16)
    private LegacyType legacyType;

    @Column(name = "legacy_id", nullable = false)
    private Long legacyId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "legacy_checksum", nullable = false, length = 64, columnDefinition = "char(64)")
    private String legacyChecksum;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "canonical_checksum", nullable = false, length = 64, columnDefinition = "char(64)")
    private String canonicalChecksum;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diff_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> diffJson;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

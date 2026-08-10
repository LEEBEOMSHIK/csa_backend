package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;

@Getter
@Entity
@Table(name = "asset_upload_sessions")
public class AssetUploadSession {
    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_kind", nullable = false, length = 16)
    private AssetKind assetKind;

    @Column(name = "declared_size", nullable = false)
    private long declaredSize;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "declared_sha256", nullable = false, length = 64, columnDefinition = "char(64)")
    private String declaredSha256;

    @Column(name = "declared_mime_type", nullable = false, length = 128)
    private String declaredMimeType;

    @Column(name = "quarantine_key", nullable = false, length = 1024)
    private String quarantineKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UploadSessionStatus status;

    @Column(name = "rejection_code", length = 64)
    private String rejectionCode;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

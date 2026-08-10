package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;

@Getter
@Entity
@Table(name = "content_renditions")
public class Rendition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private RenditionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RenditionStatus status;

    @Column(name = "manifest_asset_id")
    private Long manifestAssetId;

    @Column(name = "renderer_version", nullable = false)
    private int rendererVersion;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "checksum", length = 64, columnDefinition = "char(64)")
    private String checksum;

    @Column(name = "compatibility_fallback", nullable = false)
    private boolean compatibilityFallback;
}

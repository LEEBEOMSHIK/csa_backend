package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "content_rendition_variants")
public class RenditionVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rendition_id", nullable = false)
    private Long renditionId;

    @Column(name = "locale", nullable = false, length = 8)
    private String locale;

    @Column(name = "voice_type", nullable = false, length = 64)
    private String voiceType;

    @Column(name = "output_asset_id", nullable = false)
    private Long outputAssetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_mode", nullable = false, length = 24)
    private OutputMode outputMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RenditionStatus status;

    @Column(name = "source_revision", nullable = false)
    private long sourceRevision;
}

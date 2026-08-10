package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "audio_variants")
public class AudioVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audio_cue_id", nullable = false)
    private Long audioCueId;

    @Column(name = "locale", nullable = false, length = 8)
    private String locale;

    @Column(name = "voice_type", nullable = false, length = 64)
    private String voiceType;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AudioVariantStatus status;
}

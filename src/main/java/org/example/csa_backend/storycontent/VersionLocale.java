package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "story_version_locales")
public class VersionLocale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "locale", nullable = false, length = 8)
    private String locale;

    @Column(name = "default_voice_type", length = 64)
    private String defaultVoiceType;
}

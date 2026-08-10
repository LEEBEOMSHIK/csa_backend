package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "scene_audio_cues")
public class AudioCue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scene_id", nullable = false)
    private Long sceneId;

    @Column(name = "cue_key", nullable = false, length = 128)
    private String cueKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private AudioRole role;

    @Column(name = "start_ms", nullable = false)
    private long startMs;

    @Column(name = "required", nullable = false)
    private boolean required;
}

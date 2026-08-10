package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;

@Getter
@Entity
@Table(name = "content_render_jobs")
public class RenderJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private RenderJobKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RenderJobStatus status;

    @Column(name = "locale", nullable = false, length = 8)
    private String locale;

    @Column(name = "voice_type", nullable = false, length = 64)
    private String voiceType;

    @Column(name = "source_revision", nullable = false)
    private long sourceRevision;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}

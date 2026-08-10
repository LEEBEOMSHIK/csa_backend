package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;

@Getter
@Entity
@Table(name = "legacy_story_links")
public class LegacyStoryLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "legacy_type", nullable = false, length = 16)
    private LegacyType legacyType;

    @Column(name = "legacy_id", nullable = false)
    private Long legacyId;

    @Column(name = "story_id", nullable = false)
    private Long storyId;

    @Column(name = "content_version_id", nullable = false)
    private Long contentVersionId;

    @Column(name = "legacy_format", length = 16)
    private String legacyFormat;

    @Column(name = "legacy_status_code", length = 32)
    private String legacyStatusCode;

    @Column(name = "legacy_language", length = 8)
    private String legacyLanguage;

    @Column(name = "imported_generation_job_id")
    private Long importedGenerationJobId;

    @Column(name = "imported_video_job_id")
    private Long importedVideoJobId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "source_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String sourceHash;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;
}

package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;

@Getter
@Entity
@Table(name = "content_publish_events")
public class PublishEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "story_id", nullable = false)
    private Long storyId;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "previous_version_id")
    private Long previousVersionId;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_fingerprint", nullable = false, length = 64, columnDefinition = "char(64)")
    private String requestFingerprint;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "manifest_checksum", nullable = false, length = 64, columnDefinition = "char(64)")
    private String manifestChecksum;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

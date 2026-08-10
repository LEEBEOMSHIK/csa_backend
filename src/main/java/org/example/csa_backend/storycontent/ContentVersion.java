package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

@Getter
@Entity
@Table(name = "story_content_versions")
public class ContentVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "story_id", nullable = false)
    private Long storyId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ContentVersionStatus status;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @Column(name = "source_revision", nullable = false)
    private long sourceRevision;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public void assertDraft() {
        if (status != ContentVersionStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT content versions may be mutated");
        }
    }

    public ContentVersion forkAsDraft(int nextVersionNo, Long actorId, Instant now) {
        if (status != ContentVersionStatus.PUBLISHED && status != ContentVersionStatus.SUPERSEDED) {
            throw new IllegalStateException("Only PUBLISHED or SUPERSEDED versions may be forked");
        }
        ContentVersion draft = new ContentVersion();
        draft.storyId = storyId;
        draft.versionNo = nextVersionNo;
        draft.status = ContentVersionStatus.DRAFT;
        draft.schemaVersion = schemaVersion;
        draft.lockVersion = 0;
        draft.sourceRevision = sourceRevision;
        draft.createdBy = actorId;
        draft.lastModifiedBy = actorId;
        draft.createdAt = Objects.requireNonNull(now);
        draft.updatedAt = now;
        return draft;
    }
}

package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;

@Getter
@Entity
@Table(name = "stories")
public class Story {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 32)
    private StoryOrigin origin;

    @Column(name = "origin_ref", length = 128)
    private String originRef;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 32)
    private StoryVisibility visibility;

    @Column(name = "title_ko", nullable = false, length = 255)
    private String titleKo;

    @Column(name = "title_ja", nullable = false, length = 255)
    private String titleJa;

    @Column(name = "description_ko", columnDefinition = "text")
    private String descriptionKo;

    @Column(name = "description_ja", columnDefinition = "text")
    private String descriptionJa;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_keys", nullable = false, columnDefinition = "jsonb")
    private List<String> categoryKeys;

    @Column(name = "published_version_id")
    private Long publishedVersionId;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void archive(Instant now) {
        visibility = StoryVisibility.ARCHIVED;
        archivedAt = now;
        updatedAt = now;
    }
}

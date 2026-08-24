package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import java.util.Map;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "story_scenes")
public class Scene {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "scene_key", nullable = false, length = 128)
    private String sceneKey;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "fallback_asset_id")
    private Long fallbackAssetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "properties_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> propertiesJson = Map.of();
}

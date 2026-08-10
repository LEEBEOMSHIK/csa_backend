package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;

@Getter
@Entity
@Table(name = "story_layers")
public class Layer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scene_id", nullable = false)
    private Long sceneId;

    @Column(name = "layer_key", nullable = false, length = 128)
    private String layerKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 24)
    private LayerType type;

    @Column(name = "z_index", nullable = false)
    private int zIndex;

    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "x", nullable = false, precision = 12, scale = 6)
    private BigDecimal x;

    @Column(name = "y", nullable = false, precision = 12, scale = 6)
    private BigDecimal y;

    @Column(name = "scale_x", nullable = false, precision = 12, scale = 6)
    private BigDecimal scaleX;

    @Column(name = "scale_y", nullable = false, precision = 12, scale = 6)
    private BigDecimal scaleY;

    @Column(name = "rotation_deg", nullable = false, precision = 12, scale = 6)
    private BigDecimal rotationDeg;

    @Column(name = "opacity", nullable = false, precision = 5, scale = 4)
    private BigDecimal opacity;

    @Column(name = "visible", nullable = false)
    private boolean visible;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "properties_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> propertiesJson;
}

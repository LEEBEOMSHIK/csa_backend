package org.example.csa_backend.storycontent;

import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "scene_localized_contents")
public class SceneLocalizedContent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scene_id", nullable = false)
    private Long sceneId;

    @Column(name = "locale", nullable = false, length = 8)
    private String locale;

    @Column(name = "display_text", nullable = false, columnDefinition = "text")
    private String displayText;

    @Column(name = "script_text", nullable = false, columnDefinition = "text")
    private String scriptText;

    @Column(name = "caption_asset_id")
    private Long captionAssetId;
}

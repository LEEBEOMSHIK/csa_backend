package org.example.csa_backend.fairytale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.csa_backend.common.BaseEntity;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "CURATED_FAIRYTALE_PAGES",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_CURATED_FAIRYTALE_PAGES_VERSION_PAGE",
                columnNames = {"FAIRYTALE_ID", "CONTENT_VERSION", "PAGE_INDEX"}
        )
)
@Getter
@NoArgsConstructor
public class CuratedFairytalePage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FAIRYTALE_ID", nullable = false)
    private Fairytale fairytale;

    @Column(name = "PAGE_INDEX", nullable = false)
    private int pageIndex;

    @Column(name = "IMAGE_URL", nullable = false, length = 1000)
    private String imageUrl;

    @Column(name = "TEXT_KO", nullable = false, columnDefinition = "TEXT")
    private String textKo;

    @Column(name = "TEXT_JA", nullable = false, columnDefinition = "TEXT")
    private String textJa;

    @Column(name = "CONTENT_VERSION", nullable = false, length = 50)
    private String contentVersion;

    @Column(name = "PLACEMENT_X")
    private Double placementX;

    @Column(name = "PLACEMENT_Y")
    private Double placementY;

    @Column(name = "PLACEMENT_WIDTH")
    private Double placementWidth;

    @Column(name = "PLACEMENT_HEIGHT")
    private Double placementHeight;

    @Column(name = "PLACEMENT_Z_INDEX")
    private Integer placementZIndex;

    @Column(name = "PLACEMENT_POSE", length = 50)
    private String placementPose;

    @Column(name = "PLACEMENT_FLIP_X")
    private Boolean placementFlipX;

    @OneToMany(mappedBy = "page")
    @SQLRestriction("del_yn = 'N'")
    private List<CuratedFairytaleAudio> audios = new ArrayList<>();

    public CuratedFairytalePage(Fairytale fairytale, int pageIndex, String imageUrl, String textKo, String textJa,
                                String contentVersion,
                                Double placementX, Double placementY, Double placementWidth, Double placementHeight,
                                Integer placementZIndex, String placementPose, Boolean placementFlipX) {
        this.fairytale = fairytale;
        this.pageIndex = pageIndex;
        this.imageUrl = imageUrl;
        this.textKo = textKo;
        this.textJa = textJa;
        this.contentVersion = contentVersion;
        this.placementX = placementX;
        this.placementY = placementY;
        this.placementWidth = placementWidth;
        this.placementHeight = placementHeight;
        this.placementZIndex = placementZIndex;
        this.placementPose = placementPose;
        this.placementFlipX = placementFlipX;
    }
}

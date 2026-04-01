package org.example.csa_backend.fairytale;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.csa_backend.common.BaseEntity;

@Entity
@Table(name = "AI_FAIRYTALE_PAGES")
@Getter
@NoArgsConstructor
public class AiFairytalePage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AI_FAIRYTALE_ID", nullable = false)
    private AiFairytale aiFairytale;

    @Column(name = "PAGE_INDEX", nullable = false)
    private int pageIndex;

    @Column(name = "TEXT", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "IMAGE_URL", length = 1000)
    private String imageUrl;

    @Column(name = "AUDIO_URL", length = 1000)
    private String audioUrl;

    public AiFairytalePage(AiFairytale aiFairytale, int pageIndex, String text,
                           String imageUrl, String audioUrl) {
        this.aiFairytale = aiFairytale;
        this.pageIndex = pageIndex;
        this.text = text;
        this.imageUrl = imageUrl;
        this.audioUrl = audioUrl;
    }
}

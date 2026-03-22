package org.example.csa_backend.fairytale;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.csa_backend.common.BaseEntity;

@Entity
@Table(name = "FAIRYTALE_DETAILS")
@Getter
@NoArgsConstructor
public class FairytaleDetail extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FAIRYTALE_ID", nullable = false, unique = true)
    private Fairytale fairytale;

    @Column(name = "AUTHOR_KO", nullable = false, length = 100)
    private String authorKo;

    @Column(name = "AUTHOR_JA", length = 100)
    private String authorJa;

    @Column(name = "AGE_RANGE", nullable = false, length = 20)
    private String ageRange;

    @Column(name = "DURATION_MIN", nullable = false)
    private Integer durationMin;

    @Column(name = "PAGE_COUNT", nullable = false)
    private Integer pageCount;

    @Column(name = "FULL_CONTENT_KO", columnDefinition = "TEXT")
    private String fullContentKo;

    @Column(name = "FULL_CONTENT_JA", columnDefinition = "TEXT")
    private String fullContentJa;

    public FairytaleDetail(Fairytale fairytale, String authorKo, String authorJa,
                           String ageRange, int durationMin, int pageCount,
                           String fullContentKo, String fullContentJa) {
        this.fairytale = fairytale;
        this.authorKo = authorKo;
        this.authorJa = authorJa;
        this.ageRange = ageRange;
        this.durationMin = durationMin;
        this.pageCount = pageCount;
        this.fullContentKo = fullContentKo;
        this.fullContentJa = fullContentJa;
    }
}

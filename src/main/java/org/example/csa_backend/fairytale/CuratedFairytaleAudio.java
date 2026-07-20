package org.example.csa_backend.fairytale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.csa_backend.common.BaseEntity;

@Entity
@Table(name = "CURATED_FAIRYTALE_AUDIOS")
@Getter
@NoArgsConstructor
public class CuratedFairytaleAudio extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PAGE_ID", nullable = false)
    private CuratedFairytalePage page;

    @Column(name = "VOICE_TYPE", nullable = false, length = 20)
    private String voiceType;

    @Column(name = "LOCALE", nullable = false, length = 5)
    private String locale;

    @Column(name = "AUDIO_URL", nullable = false, length = 1000)
    private String audioUrl;

    public CuratedFairytaleAudio(CuratedFairytalePage page, String voiceType, String locale, String audioUrl) {
        this.page = page;
        this.voiceType = voiceType;
        this.locale = locale;
        this.audioUrl = audioUrl;
    }
}

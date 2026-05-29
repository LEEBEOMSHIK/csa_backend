package org.example.csa_backend.fairytale;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.csa_backend.common.BaseEntity;
import org.example.csa_backend.user.User;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "AI_FAIRYTALES")
@Getter
@NoArgsConstructor
public class AiFairytale extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "TITLE", nullable = false, length = 300)
    private String title;

    @Column(name = "SETTINGS", length = 200)
    private String settings;

    @Column(name = "GENRE", length = 50)
    private String genre;

    @Column(name = "THEME", length = 50)
    private String theme;

    @Column(name = "CHAPTER_COUNT", nullable = false)
    private int chapterCount;

    @Column(name = "VOICE_TYPE", length = 20)
    private String voiceType;

    @Column(name = "LANGUAGE", length = 5, nullable = false)
    private String language;

    @Column(name = "FORMAT", length = 10, nullable = false)
    private String format;

    @Column(name = "STATUS", length = 20, nullable = false)
    private String status;

    @Column(name = "SHARED", length = 1, nullable = false)
    private String shared = "N";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID")
    private User owner;

    @OneToMany(mappedBy = "aiFairytale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("pageIndex ASC")
    private List<AiFairytalePage> pages = new ArrayList<>();

    public AiFairytale(String title, String settings, String genre, String theme,
                       int chapterCount, String voiceType, String language,
                       String format, String status) {
        this.title = title;
        this.settings = settings;
        this.genre = genre;
        this.theme = theme;
        this.chapterCount = chapterCount;
        this.voiceType = voiceType;
        this.language = language;
        this.format = format;
        this.status = status;
    }

    public void updateStatus(String status) {
        this.status = status;
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    public void assignOwner(User owner) {
        this.owner = owner;
    }

    public void updateShared(boolean shared) {
        this.shared = shared ? "Y" : "N";
    }

    public boolean isShared() {
        return "Y".equals(this.shared);
    }

    public boolean isOwnedBy(Long userId) {
        return owner != null && owner.getId().equals(userId);
    }
}

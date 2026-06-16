package org.example.csa_backend.setting;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.csa_backend.user.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_settings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id"}))
@Getter
@NoArgsConstructor
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "locale", nullable = false, length = 5)
    private String locale = "ko";

    @Column(name = "text_noti_yn", nullable = false, length = 1)
    private String textNotiYn = "Y";

    @Column(name = "push_noti_yn", nullable = false, length = 1)
    private String pushNotiYn = "Y";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public UserSettings(User user) {
        this.user = user;
    }

    public void update(String locale, boolean textNotiEnabled, boolean pushNotiEnabled) {
        this.locale = locale;
        this.textNotiYn = textNotiEnabled ? "Y" : "N";
        this.pushNotiYn = pushNotiEnabled ? "Y" : "N";
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isTextNotiEnabled() {
        return "Y".equals(textNotiYn);
    }

    public boolean isPushNotiEnabled() {
        return "Y".equals(pushNotiYn);
    }
}

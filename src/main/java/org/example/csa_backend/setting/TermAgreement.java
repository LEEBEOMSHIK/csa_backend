package org.example.csa_backend.setting;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.csa_backend.user.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "term_agreements",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "term_type", "term_version"}))
@Getter
@NoArgsConstructor
public class TermAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "term_type", nullable = false, length = 20)
    private TermType termType;

    @Column(name = "term_version", nullable = false, length = 10)
    private String termVersion;

    @Column(name = "agreed_at", nullable = false, updatable = false)
    private LocalDateTime agreedAt = LocalDateTime.now();

    public TermAgreement(User user, TermType termType, String termVersion) {
        this.user = user;
        this.termType = termType;
        this.termVersion = termVersion;
    }
}

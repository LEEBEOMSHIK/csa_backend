package org.example.csa_backend.report;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.csa_backend.common.BaseEntity;
import org.example.csa_backend.user.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "REPORTS")
@Getter
@NoArgsConstructor
public class Report extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REPORTER_ID", nullable = false)
    private User reporter;

    @Column(name = "TARGET_TYPE", nullable = false, length = 20)
    private String targetType;

    @Column(name = "TARGET_ID", nullable = false)
    private Long targetId;

    @Column(name = "REASON", nullable = false, length = 50)
    private String reason;

    @Column(name = "DETAIL", columnDefinition = "text")
    private String detail;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "ADMIN_NOTE", columnDefinition = "text")
    private String adminNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RESOLVED_BY")
    private User resolvedBy;

    @Column(name = "RESOLVED_AT")
    private LocalDateTime resolvedAt;

    public Report(User reporter, String targetType, Long targetId, String reason, String detail) {
        this.reporter = reporter;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
    }

    public void resolve(String status, String adminNote, User resolvedBy) {
        this.status = status;
        this.adminNote = adminNote;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = LocalDateTime.now();
    }
}

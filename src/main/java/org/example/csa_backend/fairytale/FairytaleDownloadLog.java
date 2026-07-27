package org.example.csa_backend.fairytale;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.csa_backend.user.User;

import java.time.LocalDateTime;

/**
 * 다운로드(오프라인 저장) 완료 이벤트를 남기는 append-only 로그.
 * 도메인 엔티티(BaseEntity 상속, 소프트 삭제/수정 이력 필요)가 아니라
 * admin_audit_log(V8)와 같은 감사성 로그 성격이라 BaseEntity를 상속하지 않는다.
 *
 * 다운로드 대상은 큐레이션 {@link Fairytale}(카테고리 보유) 또는 사용자 생성
 * {@link AiFairytale}(카테고리 없음) 둘 중 하나이며, targetType으로 구분하고
 * 정확히 한 쪽 FK만 채운다(반대쪽은 null, DB CHECK 제약으로 강제).
 */
@Entity
@Table(name = "fairytale_download_log")
@Getter
@NoArgsConstructor
public class FairytaleDownloadLog {

    public static final String TARGET_TYPE_FAIRYTALE = "FAIRYTALE";
    public static final String TARGET_TYPE_AI_FAIRYTALE = "AI_FAIRYTALE";

    public static final String FORMAT_SLIDE = "slide";
    public static final String FORMAT_VIDEO = "video";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fairytale_id")
    private Fairytale fairytale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_fairytale_id")
    private AiFairytale aiFairytale;

    @Column(nullable = false, length = 10)
    private String format;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private FairytaleDownloadLog(User user, String targetType, Fairytale fairytale,
                                  AiFairytale aiFairytale, String format) {
        this.user = user;
        this.targetType = targetType;
        this.fairytale = fairytale;
        this.aiFairytale = aiFairytale;
        this.format = format;
    }

    public static FairytaleDownloadLog forFairytale(User user, Fairytale fairytale, String format) {
        return new FairytaleDownloadLog(user, TARGET_TYPE_FAIRYTALE, fairytale, null, format);
    }

    public static FairytaleDownloadLog forAiFairytale(User user, AiFairytale aiFairytale, String format) {
        return new FairytaleDownloadLog(user, TARGET_TYPE_AI_FAIRYTALE, null, aiFairytale, format);
    }
}

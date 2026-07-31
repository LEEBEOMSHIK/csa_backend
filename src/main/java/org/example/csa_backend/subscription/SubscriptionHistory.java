package org.example.csa_backend.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 구독 상태 전이(ACTIVE→GRACE→EXPIRED 등)를 남기는 append-only 이력.
 * subscription 테이블은 구독 1건당 1행이라 갱신 시 과거 상태가 사라지므로,
 * 관리자 화면에서 시간순 전이를 보려면 이 테이블이 원천이 된다.
 *
 * 도메인 엔티티(BaseEntity 상속, 소프트 삭제/수정 이력 필요)가 아니라
 * admin_audit_log(V8) / fairytale_download_log(V9)와 같은 감사성 로그 성격이라
 * BaseEntity를 상속하지 않고 created_at만 갖는다.
 *
 * Subscription과는 단방향 ManyToOne이다 — Subscription 쪽에 컬렉션을 두지 않아
 * 기존 구독 응답 직렬화 경로나 로딩 비용에 영향을 주지 않는다.
 */
@Entity
@Table(name = "subscription_history",
        indexes = {
                @Index(name = "idx_subscription_history_subscription",
                        columnList = "subscription_id, created_at"),
                @Index(name = "idx_subscription_history_created_at", columnList = "created_at")
        })
@Getter
@NoArgsConstructor
public class SubscriptionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 12)
    private SubscriptionStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 12)
    private SubscriptionStatus newStatus;

    @Column(name = "previous_period_end")
    private LocalDateTime previousPeriodEnd;

    @Column(name = "new_period_end")
    private LocalDateTime newPeriodEnd;

    @Column(name = "previous_auto_renew", length = 1)
    private String previousAutoRenew;

    @Column(name = "new_auto_renew", nullable = false, length = 1)
    private String newAutoRenew;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private SubscriptionChangeSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private SubscriptionHistory(Subscription subscription,
                                SubscriptionSnapshot previous,
                                SubscriptionChangeSource source) {
        this.subscription = subscription;
        this.previousStatus = previous == null ? null : previous.status();
        this.previousPeriodEnd = previous == null ? null : previous.currentPeriodEnd();
        this.previousAutoRenew = previous == null ? null : previous.autoRenew();
        this.newStatus = subscription.getStatus();
        this.newPeriodEnd = subscription.getCurrentPeriodEnd();
        this.newAutoRenew = subscription.getAutoRenew();
        this.source = source;
    }

    /** 구독 최초 생성 시점의 이력. 이전 상태가 없으므로 previous_* 는 전부 null이다. */
    static SubscriptionHistory created(Subscription subscription) {
        return new SubscriptionHistory(subscription, null, SubscriptionChangeSource.CREATED);
    }

    /** 기존 구독의 상태 전이 이력. previous는 변경 메서드 호출 전에 캡처한 값이어야 한다. */
    static SubscriptionHistory changed(Subscription subscription,
                                       SubscriptionSnapshot previous,
                                       SubscriptionChangeSource source) {
        return new SubscriptionHistory(subscription, previous, source);
    }
}

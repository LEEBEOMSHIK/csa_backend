package org.example.csa_backend.subscription;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 이력 기록 대상이 되는 구독 필드의 변경 전 값. Entity 변경 메서드를 호출하기 <b>전에</b>
 * 캡처해 두고, 호출 후 상태와 비교해 실제 변경 여부를 판정한다.
 *
 * lastNotificationTime은 의도적으로 포함하지 않는다 — 같은 상태의 알림이 반복 수신되면
 * 그 값만 갱신되는데, 그것까지 변경으로 보면 이력이 노이즈로 채워진다.
 */
record SubscriptionSnapshot(SubscriptionStatus status,
                            LocalDateTime currentPeriodEnd,
                            String autoRenew) {

    static SubscriptionSnapshot of(Subscription subscription) {
        return new SubscriptionSnapshot(
                subscription.getStatus(),
                subscription.getCurrentPeriodEnd(),
                subscription.getAutoRenew()
        );
    }

    boolean matches(Subscription subscription) {
        return status == subscription.getStatus()
                && Objects.equals(currentPeriodEnd, subscription.getCurrentPeriodEnd())
                && Objects.equals(autoRenew, subscription.getAutoRenew());
    }
}

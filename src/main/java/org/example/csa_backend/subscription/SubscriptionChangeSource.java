package org.example.csa_backend.subscription;

/**
 * subscription_history 한 행이 어느 경로에서 생성됐는지 나타낸다.
 * 값은 V11 마이그레이션의 source CHECK 제약과 1:1로 대응한다.
 */
public enum SubscriptionChangeSource {

    /** 구독 최초 생성(영수증 검증으로 새 subscription 행이 만들어진 시점). */
    CREATED,

    /** 클라이언트 영수증 검증(verifyAndApply)으로 기존 구독 상태가 갱신됨. */
    VERIFICATION,

    /** 스토어 알림(Apple ASSN V2 / Google RTDN)으로 갱신됨. */
    STORE_NOTIFICATION,

    /** 같은 사용자의 새 구독이 활성화되면서 이전 활성 구독이 만료 처리됨. */
    SUPERSEDED,

    /** current_period_end가 지난 ACTIVE 구독이 지연 만료 처리됨. */
    EXPIRY_SWEEP
}

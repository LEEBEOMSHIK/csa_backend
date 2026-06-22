package org.example.csa_backend.subscription;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.csa_backend.user.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscription",
        indexes = {
                @Index(name = "idx_subscription_user", columnList = "user_id"),
                @Index(name = "ux_subscription_original_transaction_id",
                        columnList = "original_transaction_id", unique = true)
        })
@Getter
@NoArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 10)
    private Platform platform;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "original_transaction_id", length = 255)
    private String originalTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Column(name = "auto_renew", nullable = false, length = 1)
    private String autoRenew = "Y";

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 10)
    private StoreEnvironment environment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Subscription(User user, Platform platform, String productId,
                        String originalTransactionId, StoreEnvironment environment) {
        this.user = user;
        this.platform = platform;
        this.productId = productId;
        this.originalTransactionId = originalTransactionId;
        this.environment = environment;
    }

    public void updateFromVerification(SubscriptionStatus status,
                                       LocalDateTime currentPeriodEnd,
                                       boolean autoRenew) {
        this.status = status;
        this.currentPeriodEnd = currentPeriodEnd;
        this.autoRenew = autoRenew ? "Y" : "N";
        this.updatedAt = LocalDateTime.now();
    }

    public void expire() {
        this.status = SubscriptionStatus.EXPIRED;
        this.autoRenew = "N";
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.GRACE;
    }

    public boolean isAutoRenewEnabled() {
        return "Y".equals(autoRenew);
    }
}

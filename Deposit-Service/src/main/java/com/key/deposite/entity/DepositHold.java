package com.key.deposite.entity;

import com.key.deposite.enums.HoldReason;
import com.key.deposite.enums.HoldStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "deposit_holds")
@Data
public class DepositHold {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String accountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private HoldReason reason;

    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    private HoldStatus status = HoldStatus.ACTIVE;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposit_account_id")  // Fixed: Unique FK to avoid conflict
    private DepositAccount account;

    // Pre-persist: Enforce INR precision (manual scale=2)
    @PrePersist
    @PreUpdate
    private void enforceINRPrecision() {
        if (this.amount != null) {
            this.amount = this.amount.setScale(2, RoundingMode.HALF_UP);
        }
    }
}
package com.key.deposite.entity;

import com.key.deposite.enums.DepositStatus;
import com.key.deposite.enums.DepositType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "deposit_accounts", indexes = {@Index(columnList = "accountId")})
@Data
public class DepositAccount {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(unique = true, nullable = false)
    private String accountId;  // External ref to Account Service

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private DepositType type;

    @Enumerated(EnumType.STRING)
    private DepositStatus status = DepositStatus.ACTIVE;

    @Column(precision = 5, scale = 4)
    private BigDecimal interestRate = BigDecimal.ZERO;

    private String currency = "INR";  // Manual INR default

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DepositTransaction> transactions = new ArrayList<>();

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DepositHold> holds = new ArrayList<>();

    private boolean isDeleted = false;

    // Pre-persist: Enforce INR precision (manual scale=2)
    @PrePersist
    @PreUpdate
    private void enforceINRPrecision() {
        if (this.balance != null) {
            this.balance = this.balance.setScale(2, RoundingMode.HALF_UP);
        }
        if (this.availableBalance != null) {
            this.availableBalance = this.availableBalance.setScale(2, RoundingMode.HALF_UP);
        }
    }
}
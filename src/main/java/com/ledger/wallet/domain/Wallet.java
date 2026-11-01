package com.ledger.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A balance in a single currency, owned by one user.
 *
 * <p>The balance is a materialised total: it must always equal the sum of this wallet's
 * ledger entries, credits minus debits. The database additionally enforces
 * {@code CHECK (balance >= 0)}, so no code path can overdraw it.
 *
 * <p>The mutating methods live on the entity rather than in a service, so the rule
 * "never debit more than the balance" cannot be bypassed by a caller.
 */
@Entity
@Table(name = "wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_wallet_user"))
    private User user;

    /** 19 digits with 2 decimal places; exact decimal arithmetic, never floating point. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    /** ISO 4217 code. */
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private WalletStatus status = WalletStatus.ACTIVE;

    @Column(name = "wallet_type", nullable = false, length = 50)
    @Builder.Default
    private String walletType = "PRIMARY";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic lock. The money path also takes a pessimistic row lock; this guards every
     * other write path, now or later, against a lost update.
     */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    public boolean isActive() {
        return status == WalletStatus.ACTIVE;
    }

    public boolean hasSufficientBalance(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (!hasSufficientBalance(amount)) {
            throw new IllegalStateException("Debit of " + amount + " exceeds balance " + balance);
        }
        this.balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        requirePositive(amount);
        this.balance = balance.add(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive, was " + amount);
        }
    }
}

package com.natwest.wallet.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Wallet entity
 * Represents a user's wallet with balance management
 * Supports row-level locking for concurrent access
 */
@Entity
@Table(name = "wallets", indexes = {
    @Index(name = "idx_wallets_user_id", columnList = "user_id"),
    @Index(name = "idx_wallets_status", columnList = "status"),
    @Index(name = "idx_wallets_currency", columnList = "currency")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"user"})
@EqualsAndHashCode(exclude = {"user"})
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_wallet_user"))
    private User user;

    /**
     * Current balance in the wallet
     * Precision: 19 digits total, 2 decimal places
     * Allows up to 9,999,999,999,999,999.99
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    /**
     * Currency code (ISO 4217)
     * Examples: INR, USD, EUR, GBP
     */
    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Current status of the wallet
     */
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private WalletStatus status;

    /**
     * Type of wallet (PRIMARY, SAVINGS, etc.)
     */
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String walletType = "PRIMARY";

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    /**
     * Check if wallet is active and can be used
     */
    public boolean isActive() {
        return status == WalletStatus.ACTIVE;
    }

    /**
     * Check if wallet has sufficient balance for a transaction
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    /**
     * Debit money from wallet
     * Throws exception if insufficient balance
     */
    public void debit(BigDecimal amount) {
        if (!hasSufficientBalance(amount)) {
            throw new IllegalArgumentException("Insufficient balance for debit");
        }
        this.balance = balance.subtract(amount);
    }

    /**
     * Credit money to wallet
     */
    public void credit(BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.balance = balance.add(amount);
    }

    /**
     * Get current balance
     */
    public BigDecimal getBalance() {
        return balance;
    }

    /**
     * Format balance as string with currency
     */
    public String getFormattedBalance() {
        return String.format("%s %s", currency, balance);
    }
}

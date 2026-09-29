package com.natwest.wallet.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction entity
 * Represents a single transaction (transfer, deposit, or withdrawal)
 * Immutable once created - updates only status and failure reason
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transactions_idempotency_key", columnList = "idempotency_key", unique = true),
    @Index(name = "idx_transactions_from_wallet", columnList = "from_wallet_id"),
    @Index(name = "idx_transactions_to_wallet", columnList = "to_wallet_id"),
    @Index(name = "idx_transactions_status", columnList = "status"),
    @Index(name = "idx_transactions_type", columnList = "type"),
    @Index(name = "idx_transactions_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"fromWallet", "toWallet"})
@EqualsAndHashCode(exclude = {"fromWallet", "toWallet"})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    /**
     * Idempotency key - unique identifier to prevent duplicate transactions
     * Used to safely retry failed requests without double-charging
     * Required for exactly-once semantics
     */
    @Column(nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    /**
     * Source wallet for transfer/withdrawal (optional for deposits)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_wallet_id", foreignKey = @ForeignKey(name = "fk_transaction_from_wallet"))
    private Wallet fromWallet;

    /**
     * Destination wallet for transfer/deposit (optional for withdrawals)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_wallet_id", foreignKey = @ForeignKey(name = "fk_transaction_to_wallet"))
    private Wallet toWallet;

    /**
     * Transaction amount
     * Always positive, direction determined by transaction type
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /**
     * Currency code (ISO 4217)
     */
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    /**
     * Type of transaction
     */
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private TransactionType type;

    /**
     * Current status of the transaction
     */
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    /**
     * Reason for failure if status is FAILED
     */
    @Column(length = 500)
    private String failureReason;

    /**
     * Additional metadata stored as JSON
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private String metadata;

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
     * Check if transaction is completed
     */
    public boolean isCompleted() {
        return status == TransactionStatus.COMPLETED;
    }

    /**
     * Check if transaction is pending
     */
    public boolean isPending() {
        return status == TransactionStatus.PENDING;
    }

    /**
     * Check if transaction has failed
     */
    public boolean isFailed() {
        return status == TransactionStatus.FAILED;
    }

    /**
     * Mark transaction as completed
     */
    public void markCompleted() {
        this.status = TransactionStatus.COMPLETED;
        this.failureReason = null;
    }

    /**
     * Mark transaction as failed with reason
     */
    public void markFailed(String reason) {
        this.status = TransactionStatus.FAILED;
        this.failureReason = reason;
    }

    /**
     * Get transaction description
     */
    public String getDescription() {
        return switch (type) {
            case TRANSFER -> String.format("Transfer %s %s", currency, amount);
            case DEPOSIT -> String.format("Deposit %s %s", currency, amount);
            case WITHDRAWAL -> String.format("Withdrawal %s %s", currency, amount);
        };
    }
}

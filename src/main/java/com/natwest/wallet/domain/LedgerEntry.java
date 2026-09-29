package com.natwest.wallet.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ledger Entry entity
 * 
 * Immutable audit trail for all wallet transactions.
 * Implements double-entry bookkeeping:
 * - Every debit (money out) has a corresponding credit (money in)
 * - Running balance provides audit trail for wallet history
 * - No updates allowed - only inserts
 * 
 * Example for transfer of 100 INR from Wallet A to Wallet B:
 * 1. DEBIT entry: Wallet A, -100, running_balance = (previous - 100)
 * 2. CREDIT entry: Wallet B, +100, running_balance = (previous + 100)
 */
@Entity
@Table(name = "ledger_entries", indexes = {
    @Index(name = "idx_ledger_wallet_id", columnList = "wallet_id"),
    @Index(name = "idx_ledger_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_ledger_created_at", columnList = "created_at")
})
@Immutable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"wallet", "transaction"})
@EqualsAndHashCode(exclude = {"wallet", "transaction"})
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID", updatable = false)
    private UUID id;

    /**
     * The wallet this entry belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false, 
                foreignKey = @ForeignKey(name = "fk_ledger_wallet"))
    private Wallet wallet;

    /**
     * The transaction that caused this ledger entry
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false,
                foreignKey = @ForeignKey(name = "fk_ledger_transaction"))
    private Transaction transaction;

    /**
     * Type of entry: DEBIT (out) or CREDIT (in)
     */
    @Column(nullable = false, length = 50, updatable = false)
    @Enumerated(EnumType.STRING)
    private EntryType entryType;

    /**
     * Amount of this entry (always positive)
     * Direction determined by entryType
     */
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    /**
     * Running balance after this entry
     * Allows verification of wallet balance at any point in time
     */
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal runningBalance;

    /**
     * Optional description of the entry
     */
    @Column(length = 500, updatable = false)
    private String description;

    /**
     * Timestamp when entry was created (immutable)
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Check if this is a debit entry
     */
    public boolean isDebit() {
        return entryType == EntryType.DEBIT;
    }

    /**
     * Check if this is a credit entry
     */
    public boolean isCredit() {
        return entryType == EntryType.CREDIT;
    }

    /**
     * Get entry type as descriptive string
     */
    public String getEntryTypeDescription() {
        return entryType == EntryType.DEBIT ? "Money Out" : "Money In";
    }

    /**
     * Get formatted entry display
     */
    public String getFormattedEntry() {
        String type = isDebit() ? "DEBIT" : "CREDIT";
        return String.format("%s | %s | Running Balance: %s", type, amount, runningBalance);
    }

    /**
     * Verify ledger entry integrity
     * Used for auditing and compliance
     */
    public boolean isValid() {
        // Amount must be positive
        if (amount.signum() <= 0) {
            return false;
        }
        
        // Running balance must be non-negative
        if (runningBalance.signum() < 0) {
            return false;
        }
        
        return true;
    }
}

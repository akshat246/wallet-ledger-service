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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One side of a double-entry record: append-only, never updated.
 *
 * <p>A transfer of 100 writes two rows — a DEBIT against the sender and a CREDIT to the
 * receiver — in the same database transaction as the balance updates. A deposit or
 * withdrawal writes a single row, since the counterparty is outside the system.
 *
 * <p>{@code runningBalance} is the owning wallet's balance <em>after</em> this entry, so a
 * historical balance can be read directly instead of replaying the whole history.
 *
 * <p>{@link Immutable} stops Hibernate ever issuing an UPDATE here. Corrections are made by
 * appending a compensating entry, never by editing what happened.
 */
@Entity
@Table(name = "ledger_entries")
@Immutable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_ledger_wallet"))
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_ledger_transaction"))
    private Transaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 50, updatable = false)
    private EntryType entryType;

    /** Always positive; the sign is carried by {@link #entryType}. */
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "running_balance", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal runningBalance;

    @Column(length = 500, updatable = false)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isDebit() {
        return entryType == EntryType.DEBIT;
    }

    public boolean isCredit() {
        return entryType == EntryType.CREDIT;
    }

    /** This entry's signed contribution to its wallet's balance. */
    public BigDecimal signedAmount() {
        return isCredit() ? amount : amount.negate();
    }
}

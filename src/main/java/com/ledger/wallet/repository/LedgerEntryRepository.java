package com.ledger.wallet.repository;

import com.ledger.wallet.domain.EntryType;
import com.ledger.wallet.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    /**
     * Total of one side of the ledger for a wallet. Credits minus debits must equal the
     * wallet's stored balance; see {@code ReconciliationService}.
     */
    @Query("""
            SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l
            WHERE l.wallet.id = :walletId AND l.entryType = :entryType
            """)
    BigDecimal sumAmount(@Param("walletId") UUID walletId, @Param("entryType") EntryType entryType);
}

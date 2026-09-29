package com.natwest.wallet.repository;

import com.natwest.wallet.domain.EntryType;
import com.natwest.wallet.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
    List<LedgerEntry> findByTransactionId(UUID transactionId);
    List<LedgerEntry> findByWalletIdAndEntryTypeOrderByCreatedAtDesc(UUID walletId, EntryType entryType);
    
    @Query("SELECT MAX(l.runningBalance) FROM LedgerEntry l WHERE l.wallet.id = :walletId")
    Optional<BigDecimal> getLatestRunningBalance(@Param("walletId") UUID walletId);
    
    long countByWalletId(UUID walletId);
}
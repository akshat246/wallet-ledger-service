package com.ledger.wallet.repository;

import com.ledger.wallet.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /** Everything the wallet took part in, as sender or receiver, newest first. */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.fromWallet.id = :walletId OR t.toWallet.id = :walletId
            ORDER BY t.createdAt DESC
            """)
    List<Transaction> findByWalletId(@Param("walletId") UUID walletId);
}

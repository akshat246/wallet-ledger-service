package com.natwest.wallet.repository;

import com.natwest.wallet.domain.Transaction;
import com.natwest.wallet.domain.TransactionStatus;
import com.natwest.wallet.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.idempotencyKey = :key")
    Optional<Transaction> findByIdempotencyKeyForUpdate(@Param("key") String key);
    
    @Query("SELECT t FROM Transaction t WHERE (t.fromWallet.id = :walletId OR t.toWallet.id = :walletId) AND t.status = :status ORDER BY t.createdAt DESC")
    List<Transaction> findWalletTransactionsByStatus(@Param("walletId") UUID walletId, @Param("status") TransactionStatus status);
    
    @Query("SELECT t FROM Transaction t WHERE (t.fromWallet.id = :walletId OR t.toWallet.id = :walletId) ORDER BY t.createdAt DESC")
    List<Transaction> findWalletTransactions(@Param("walletId") UUID walletId);
    
    long countByStatus(TransactionStatus status);
    List<Transaction> findByType(TransactionType type);
    List<Transaction> findByStatusAndCreatedAtAfter(TransactionStatus status, LocalDateTime createdAfter);
}
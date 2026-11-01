package com.ledger.wallet.repository;

import com.ledger.wallet.domain.Wallet;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    List<Wallet> findByUserIdOrderByCreatedAtAsc(UUID userId);

    /**
     * Reads the wallet row under {@code SELECT ... FOR UPDATE}. Any other transaction asking
     * for the same row waits here until this one commits, which is what makes the
     * read-check-write sequence of a transfer safe.
     *
     * <p>The timeout caps that wait: without it, one stuck transaction would block every
     * other movement on the wallet indefinitely. Exceeding it surfaces as a
     * {@code PessimisticLockingFailureException}, reported to the client as a retryable 409.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);
}

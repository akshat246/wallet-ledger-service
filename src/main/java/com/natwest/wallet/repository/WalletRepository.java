package com.natwest.wallet.repository;

import com.natwest.wallet.domain.Wallet;
import com.natwest.wallet.domain.WalletStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    List<Wallet> findByUserId(UUID userId);
    List<Wallet> findByUserIdAndStatus(UUID userId, WalletStatus status);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);
    
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdForRead(@Param("id") UUID id);
    
    int countByUserIdAndStatus(UUID userId, WalletStatus status);
}
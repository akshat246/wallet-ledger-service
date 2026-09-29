package com.natwest.wallet.repository;

import com.natwest.wallet.domain.WalletLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletLockRepository extends JpaRepository<WalletLock, UUID> {
    Optional<WalletLock> findByWalletId(UUID walletId);
}
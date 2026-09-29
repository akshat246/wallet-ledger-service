package com.natwest.wallet.repository;

import com.natwest.wallet.domain.IdempotencyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IdempotencyLogRepository extends JpaRepository<IdempotencyLog, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM IdempotencyLog i WHERE i.idempotencyKey = :key")
    Optional<IdempotencyLog> findByKeyForUpdate(@Param("key") String key);
    
    List<IdempotencyLog> findByExpiresAtBefore(LocalDateTime expiresAt);
    long countByStatus(IdempotencyLog.IdempotencyStatus status);
}
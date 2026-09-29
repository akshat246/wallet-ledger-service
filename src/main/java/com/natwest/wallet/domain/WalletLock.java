package com.natwest.wallet.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Wallet Lock entity
 * Tracks pessimistic locks on wallets for concurrent transaction handling
 */
@Entity
@Table(name = "wallet_locks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class WalletLock {
    
    @Id
    @Column(columnDefinition = "UUID")
    private UUID walletId;
    
    @Column(nullable = false, length = 255)
    private String lockedBy;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime lockedAt;
    
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    
    /**
     * Check if lock is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * Check if lock is still valid
     */
    public boolean isValid() {
        return !isExpired();
    }
}
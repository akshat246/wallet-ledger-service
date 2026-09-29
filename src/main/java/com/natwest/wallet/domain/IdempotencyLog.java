package com.natwest.wallet.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Idempotency Log entity
 * 
 * Tracks processed idempotency keys to ensure exactly-once semantics.
 * Prevents duplicate transaction processing when requests are retried.
 * 
 * How it works:
 * 1. Client sends request with Idempotency-Key header
 * 2. System checks if key exists in this table
 * 3. If exists and SUCCESS: return cached response (idempotent)
 * 4. If exists and FAILURE: return same failure
 * 5. If not exists: process request and store result
 * 
 * Combined with Redis cache for fast lookups and DB for persistence.
 */
@Entity
@Table(name = "idempotency_keys", indexes = {
    @Index(name = "idx_idempotency_created_at", columnList = "created_at"),
    @Index(name = "idx_idempotency_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class IdempotencyLog {

    /**
     * Idempotency key from request header
     * Primary key for uniqueness
     */
    @Id
    @Column(nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    /**
     * Hash of request body for validation
     * Ensures same request is being retried, not different request with same key
     */
    @Column(length = 64)
    private String requestHash;

    /**
     * Status of request processing
     */
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private IdempotencyStatus status;

    /**
     * Cached response payload (JSON)
     * Returned for duplicate requests
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String responsePayload;

    /**
     * Error message if status is FAILURE
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * When this entry was created
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * When this entry expires and can be deleted
     * Default: 24 hours from creation
     */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Check if entry is still valid (not expired)
     */
    public boolean isValid() {
        return LocalDateTime.now().isBefore(expiresAt);
    }

    /**
     * Check if entry is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if request was successful
     */
    public boolean isSuccessful() {
        return status == IdempotencyStatus.SUCCESS;
    }

    /**
     * Check if request failed
     */
    public boolean hasFailed() {
        return status == IdempotencyStatus.FAILURE;
    }

    /**
     * Check if request is still pending
     */
    public boolean isPending() {
        return status == IdempotencyStatus.PENDING;
    }

    /**
     * Idempotency status enumeration
     */
    public enum IdempotencyStatus {
        PENDING,    // Request received but not yet processed
        SUCCESS,    // Request processed successfully
        FAILURE     // Request processing failed
    }
}

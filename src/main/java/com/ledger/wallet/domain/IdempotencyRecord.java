package com.ledger.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * The outcome of one money-movement request, keyed by the client's Idempotency-Key.
 *
 * <p>The key is the primary key, so two concurrent requests carrying the same key serialise
 * on the insert: the first to commit wins, the second is refused by the constraint and
 * replays the stored outcome. Both successes and rejections are stored, so a retry always
 * receives the answer the original request received.
 */
@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    /** SHA-256 of the request's business fields; detects a key reused for a different request. */
    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IdempotencyStatus status;

    /** Serialised response, replayed verbatim on a duplicate request. */
    @Column(name = "response_payload", columnDefinition = "text")
    private String responsePayload;

    /** Serialised rejection, replayed on a duplicate of a refused request. */
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** After this point the row may be purged, and a retry would execute afresh. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public boolean isSuccess() {
        return status == IdempotencyStatus.SUCCESS;
    }

    public boolean isFailure() {
        return status == IdempotencyStatus.FAILURE;
    }
}

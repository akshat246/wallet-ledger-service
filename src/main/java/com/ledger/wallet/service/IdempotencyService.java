package com.ledger.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.wallet.config.IdempotencyProperties;
import com.ledger.wallet.domain.IdempotencyRecord;
import com.ledger.wallet.domain.IdempotencyStatus;
import com.ledger.wallet.dto.response.TransactionResult;
import com.ledger.wallet.exception.IdempotencyKeyException;
import com.ledger.wallet.exception.ReplayedFailureException;
import com.ledger.wallet.exception.WalletLedgerException;
import com.ledger.wallet.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Remembers the outcome of every money-movement request, keyed by the client's
 * Idempotency-Key, so a retry returns the original answer instead of moving money twice.
 *
 * <p>The lifecycle of one key:
 * <ol>
 *   <li>{@link #claim} runs inside the caller's transaction. It returns the stored outcome
 *       if the key has been seen, and otherwise inserts a PENDING row. Because the key is
 *       the table's primary key, two concurrent requests carrying the same key serialise
 *       here, before either has touched a wallet.</li>
 *   <li>{@link #complete} runs in that same transaction once the ledger writes are done, so
 *       a key reaches SUCCESS only if the money actually moved: the two commit together.</li>
 *   <li>{@link #recordFailure} runs in a <em>new</em> transaction after the caller's has
 *       rolled back, so a rejection is remembered and replayed as well.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final IdempotencyProperties properties;

    /** Validates the raw header value and returns the normalised key. */
    public String requireKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IdempotencyKeyException("Idempotency-Key header is required");
        }
        String key = rawKey.trim();
        if (key.length() > properties.maxKeyLength()) {
            throw new IdempotencyKeyException(
                    "Idempotency-Key must be at most " + properties.maxKeyLength() + " characters");
        }
        return key;
    }

    /**
     * SHA-256 over the request's business fields. Comparing it with the stored hash
     * distinguishes a genuine retry from the same key being reused for a different request,
     * which is a client bug rather than something to replay.
     */
    public String fingerprint(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] canonical = String.join("|", parts).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(canonical));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", e);
        }
    }

    /**
     * Claims the key. Must run inside an existing transaction: either it returns the
     * previously stored result, or throws the previously stored rejection, or inserts a
     * PENDING row and returns empty so the caller proceeds.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TransactionResult> claim(String key, String requestHash) {
        Optional<IdempotencyRecord> existing = repository.findByKeyForUpdate(key);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            requireSamePayload(record, requestHash);

            if (record.isSuccess()) {
                return Optional.of(deserialize(record.getResponsePayload()).asReplayed());
            }
            if (record.isFailure()) {
                throw toException(record.getErrorMessage());
            }
            // PENDING: an earlier attempt claimed the key but rolled back before its
            // rejection could be recorded. Re-run the request against the same row.
            log.warn("Re-processing stale PENDING idempotency key {}", key);
            record.setRequestHash(requestHash);
            record.setExpiresAt(expiry());
            return Optional.empty();
        }

        // saveAndFlush, not save: the INSERT has to reach the database now, so a concurrent
        // duplicate collides on the primary key immediately rather than after both have
        // locked wallets and done the work.
        repository.saveAndFlush(IdempotencyRecord.builder()
                .idempotencyKey(key)
                .requestHash(requestHash)
                .status(IdempotencyStatus.PENDING)
                .expiresAt(expiry())
                .build());
        return Optional.empty();
    }

    /** Stores the successful outcome. Must run in the same transaction as {@link #claim}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(String key, TransactionResult result) {
        IdempotencyRecord record = repository.findById(key)
                .orElseThrow(() -> new IllegalStateException("Idempotency key disappeared mid-transaction: " + key));
        record.setStatus(IdempotencyStatus.SUCCESS);
        record.setResponsePayload(serialize(result));
        record.setErrorMessage(null);
        repository.save(record);
    }

    /**
     * Stores a rejection in its own transaction, because the caller's has already rolled
     * back. Skips quietly if another attempt has since written the key.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String key, String requestHash, WalletLedgerException failure) {
        if (repository.existsById(key)) {
            return;
        }
        repository.save(IdempotencyRecord.builder()
                .idempotencyKey(key)
                .requestHash(requestHash)
                .status(IdempotencyStatus.FAILURE)
                .errorMessage(serializeFailure(failure))
                .expiresAt(expiry())
                .build());
    }

    /**
     * Reads a stored outcome without claiming anything. Used by the request that lost a race
     * on the primary key and now needs the winner's answer.
     */
    @Transactional(readOnly = true)
    public Optional<TransactionResult> lookup(String key, String requestHash) {
        return repository.findById(key).map(record -> {
            requireSamePayload(record, requestHash);
            if (record.isFailure()) {
                throw toException(record.getErrorMessage());
            }
            return record.isSuccess() ? deserialize(record.getResponsePayload()).asReplayed() : null;
        });
    }

    private Instant expiry() {
        return Instant.now().plus(properties.ttl());
    }

    private void requireSamePayload(IdempotencyRecord record, String requestHash) {
        if (record.getRequestHash() != null && !record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyException(
                    "Idempotency-Key was already used for a different request",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private String serialize(TransactionResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise transaction result", e);
        }
    }

    private TransactionResult deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, TransactionResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read stored transaction result", e);
        }
    }

    private String serializeFailure(WalletLedgerException failure) {
        try {
            return objectMapper.writeValueAsString(new StoredFailure(
                    failure.getErrorCode(), failure.getMessage(), failure.getHttpStatus().value()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise failure", e);
        }
    }

    private ReplayedFailureException toException(String storedJson) {
        try {
            StoredFailure stored = objectMapper.readValue(storedJson, StoredFailure.class);
            HttpStatus status = HttpStatus.resolve(stored.httpStatus());
            return new ReplayedFailureException(stored.message(), stored.errorCode(),
                    status != null ? status : HttpStatus.UNPROCESSABLE_ENTITY);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Unreadable stored failure payload; replaying a generic rejection", e);
            return new ReplayedFailureException("This request was previously rejected",
                    "TRANSACTION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /** JSON shape stored in {@code idempotency_keys.error_message}. */
    public record StoredFailure(String errorCode, String message, int httpStatus) { }
}

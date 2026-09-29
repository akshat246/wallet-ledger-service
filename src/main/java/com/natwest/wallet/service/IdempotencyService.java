package com.natwest.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.natwest.wallet.domain.IdempotencyLog;
import com.natwest.wallet.domain.IdempotencyLog.IdempotencyStatus;
import com.natwest.wallet.dto.TransactionResponse;
import com.natwest.wallet.exception.IdempotencyKeyException;
import com.natwest.wallet.exception.WalletLedgerException;
import com.natwest.wallet.repository.IdempotencyLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Persists the outcome of every money-movement request keyed by the client-supplied
 * Idempotency-Key, so a retried request returns the original result instead of
 * being processed twice.
 *
 * <p>Lifecycle of a key:
 * <ol>
 *   <li>{@link #claim} - inside the caller's transaction. Returns the stored result if the key
 *       was already processed, otherwise inserts a PENDING row. The primary key on
 *       {@code idempotency_keys} makes concurrent duplicates serialise on the insert.</li>
 *   <li>{@link #complete} - inside the same transaction, once the ledger writes are done.
 *       Commits together with them, so a key is only ever SUCCESS if the money moved.</li>
 *   <li>{@link #recordFailure} - in a new transaction after the caller's one has rolled back,
 *       so a business rejection (e.g. insufficient funds) is remembered and replayed too.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private static final int MAX_KEY_LENGTH = 255;

    private final IdempotencyLogRepository idempotencyLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.cache.idempotency.ttl-hours:24}")
    private long ttlHours;

    /**
     * Validates the raw header value and returns the normalised key.
     */
    public String requireKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IdempotencyKeyException("Idempotency-Key header is required");
        }
        String key = rawKey.trim();
        if (key.length() > MAX_KEY_LENGTH) {
            throw new IdempotencyKeyException("Idempotency-Key must be at most " + MAX_KEY_LENGTH + " characters");
        }
        return key;
    }

    /**
     * SHA-256 of the request's business fields. Used to detect the same key being reused
     * for a different payload, which is a client bug rather than a retry.
     */
    public String fingerprint(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = String.join("|", parts);
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Must be called inside an existing transaction. Either returns the previously stored
     * response (replay), throws the previously stored failure, or inserts a PENDING row and
     * returns empty so the caller proceeds.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TransactionResponse> claim(String key, String requestHash) {
        Optional<IdempotencyLog> existing = idempotencyLogRepository.findByKeyForUpdate(key);

        if (existing.isPresent()) {
            IdempotencyLog entry = existing.get();
            verifySamePayload(entry, requestHash);

            if (entry.getStatus() == IdempotencyStatus.SUCCESS) {
                return Optional.of(deserialize(entry.getResponsePayload()));
            }
            if (entry.getStatus() == IdempotencyStatus.FAILURE) {
                throw ReplayedFailureException.from(entry.getErrorMessage());
            }
            // PENDING: a previous attempt claimed the key but never finished (crash between
            // claim and commit is impossible in one transaction, so this is a stale row from
            // an interrupted failure-recording). Re-process under the same row.
            log.warn("Reprocessing stale PENDING idempotency key {}", key);
            entry.setRequestHash(requestHash);
            entry.setExpiresAt(LocalDateTime.now().plusHours(ttlHours));
            return Optional.empty();
        }

        IdempotencyLog entry = IdempotencyLog.builder()
                .idempotencyKey(key)
                .requestHash(requestHash)
                .status(IdempotencyStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusHours(ttlHours))
                .build();
        // Flush now so a concurrent duplicate blocks on the primary key immediately,
        // before either request has touched a wallet row.
        idempotencyLogRepository.saveAndFlush(entry);
        return Optional.empty();
    }

    /**
     * Must be called inside the same transaction as {@link #claim}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(String key, TransactionResponse response) {
        IdempotencyLog entry = idempotencyLogRepository.findById(key)
                .orElseThrow(() -> new IllegalStateException("Idempotency key vanished mid-transaction: " + key));
        entry.setStatus(IdempotencyStatus.SUCCESS);
        entry.setResponsePayload(serialize(response));
        entry.setErrorMessage(null);
        idempotencyLogRepository.save(entry);
    }

    /**
     * Runs in its own transaction because the caller's has already rolled back.
     * Skips silently if another attempt for the same key has since won the race.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String key, String requestHash, WalletLedgerException failure) {
        if (idempotencyLogRepository.existsById(key)) {
            return;
        }
        IdempotencyLog entry = IdempotencyLog.builder()
                .idempotencyKey(key)
                .requestHash(requestHash)
                .status(IdempotencyStatus.FAILURE)
                .errorMessage(serializeFailure(failure))
                .expiresAt(LocalDateTime.now().plusHours(ttlHours))
                .build();
        idempotencyLogRepository.save(entry);
    }

    /**
     * Read-only lookup used after a concurrent duplicate lost the race on insert.
     */
    @Transactional(readOnly = true)
    public Optional<TransactionResponse> replay(String key, String requestHash) {
        return idempotencyLogRepository.findById(key).map(entry -> {
            verifySamePayload(entry, requestHash);
            if (entry.getStatus() == IdempotencyStatus.FAILURE) {
                throw ReplayedFailureException.from(entry.getErrorMessage());
            }
            if (entry.getStatus() == IdempotencyStatus.SUCCESS) {
                return deserialize(entry.getResponsePayload());
            }
            return null;
        });
    }

    private void verifySamePayload(IdempotencyLog entry, String requestHash) {
        if (entry.getRequestHash() != null && !entry.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyException(
                    "Idempotency-Key was already used with a different request payload",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private String serialize(TransactionResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise transaction response", e);
        }
    }

    private TransactionResponse deserialize(String payload) {
        try {
            TransactionResponse response = objectMapper.readValue(payload, TransactionResponse.class);
            response.setReplayed(true);
            return response;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not deserialise stored transaction response", e);
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

    /** JSON shape stored in idempotency_keys.error_message. */
    public record StoredFailure(String errorCode, String message, int httpStatus) { }

    /**
     * A previously recorded business failure, thrown again for a retried request.
     * Distinguished from a fresh failure so the caller does not re-record it.
     */
    public static class ReplayedFailureException extends WalletLedgerException {

        private ReplayedFailureException(String message, String errorCode, HttpStatus status) {
            super(message, errorCode, status);
        }

        static ReplayedFailureException from(String storedJson) {
            try {
                StoredFailure stored = new ObjectMapper().readValue(storedJson, StoredFailure.class);
                HttpStatus status = HttpStatus.resolve(stored.httpStatus());
                return new ReplayedFailureException(
                        stored.message(), stored.errorCode(),
                        status != null ? status : HttpStatus.UNPROCESSABLE_ENTITY);
            } catch (JsonProcessingException | RuntimeException e) {
                return new ReplayedFailureException(
                        "Request was previously rejected", "TRANSACTION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
    }
}

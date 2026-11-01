package com.ledger.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.wallet.config.IdempotencyProperties;
import com.ledger.wallet.domain.IdempotencyRecord;
import com.ledger.wallet.domain.IdempotencyStatus;
import com.ledger.wallet.dto.response.TransactionResult;
import com.ledger.wallet.exception.IdempotencyKeyException;
import com.ledger.wallet.exception.InsufficientFundsException;
import com.ledger.wallet.exception.ReplayedFailureException;
import com.ledger.wallet.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock private IdempotencyRecordRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private IdempotencyService service;

    private static final String KEY = "key-1";
    private static final String HASH = "hash-1";

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository, objectMapper,
                new IdempotencyProperties(Duration.ofHours(24), 255));
    }

    private static TransactionResult result() {
        return new TransactionResult(UUID.randomUUID(), KEY, "TRANSFER", "COMPLETED",
                new BigDecimal("10.00"), "INR", UUID.randomUUID(), UUID.randomUUID(),
                false, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("rejects a missing or blank key")
    void requiresAKey() {
        assertThatThrownBy(() -> service.requireKey(null)).isInstanceOf(IdempotencyKeyException.class);
        assertThatThrownBy(() -> service.requireKey("   ")).isInstanceOf(IdempotencyKeyException.class);
    }

    @Test
    @DisplayName("rejects a key longer than the column allows")
    void rejectsOverlongKeys() {
        assertThatThrownBy(() -> service.requireKey("k".repeat(256)))
                .isInstanceOf(IdempotencyKeyException.class);
    }

    @Test
    @DisplayName("trims surrounding whitespace, so ' k ' and 'k' are the same key")
    void trimsKeys() {
        assertThat(service.requireKey("  k  ")).isEqualTo("k");
    }

    @Test
    @DisplayName("fingerprints the same request identically and a different one differently")
    void fingerprintsRequests() {
        String a = service.fingerprint("TRANSFER", "w1", "w2", "10");
        String b = service.fingerprint("TRANSFER", "w1", "w2", "10");
        String different = service.fingerprint("TRANSFER", "w1", "w2", "11");

        assertThat(a).isEqualTo(b).hasSize(64);
        assertThat(different).isNotEqualTo(a);
    }

    @Test
    @DisplayName("claiming an unseen key inserts PENDING and lets the caller proceed")
    void claimsAFreshKey() {
        when(repository.findByKeyForUpdate(KEY)).thenReturn(Optional.empty());

        assertThat(service.claim(KEY, HASH)).isEmpty();

        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        // saveAndFlush, not save: the insert must reach the database immediately, so a
        // concurrent duplicate collides before either request touches a wallet.
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(IdempotencyStatus.PENDING);
        assertThat(captor.getValue().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("claiming a completed key returns the stored result marked as replayed")
    void replaysStoredResults() throws Exception {
        TransactionResult stored = result();
        when(repository.findByKeyForUpdate(KEY)).thenReturn(Optional.of(IdempotencyRecord.builder()
                .idempotencyKey(KEY)
                .requestHash(HASH)
                .status(IdempotencyStatus.SUCCESS)
                .responsePayload(objectMapper.writeValueAsString(stored))
                .build()));

        Optional<TransactionResult> replay = service.claim(KEY, HASH);

        assertThat(replay).isPresent();
        assertThat(replay.get().transactionId()).isEqualTo(stored.transactionId());
        assertThat(replay.get().amount()).isEqualByComparingTo("10.00");
        assertThat(replay.get().replayed()).isTrue();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("claiming a rejected key throws the original rejection again")
    void replaysStoredFailures() {
        when(repository.findByKeyForUpdate(KEY)).thenReturn(Optional.of(IdempotencyRecord.builder()
                .idempotencyKey(KEY)
                .requestHash(HASH)
                .status(IdempotencyStatus.FAILURE)
                .errorMessage("""
                        {"errorCode":"INSUFFICIENT_FUNDS","message":"Insufficient funds","httpStatus":422}
                        """)
                .build()));

        assertThatThrownBy(() -> service.claim(KEY, HASH))
                .isInstanceOf(ReplayedFailureException.class)
                .hasMessageContaining("Insufficient funds")
                .extracting(e -> ((ReplayedFailureException) e).getHttpStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("refuses a key that was used for a different request")
    void detectsKeyReuse() {
        when(repository.findByKeyForUpdate(KEY)).thenReturn(Optional.of(IdempotencyRecord.builder()
                .idempotencyKey(KEY)
                .requestHash("a-different-hash")
                .status(IdempotencyStatus.SUCCESS)
                .responsePayload("{}")
                .build()));

        assertThatThrownBy(() -> service.claim(KEY, HASH))
                .isInstanceOf(IdempotencyKeyException.class)
                .extracting(e -> ((IdempotencyKeyException) e).getHttpStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("re-runs a key left PENDING by an attempt that rolled back")
    void retriesStalePendingKeys() {
        IdempotencyRecord stale = IdempotencyRecord.builder()
                .idempotencyKey(KEY)
                .requestHash(HASH)
                .status(IdempotencyStatus.PENDING)
                .expiresAt(Instant.now())
                .build();
        when(repository.findByKeyForUpdate(KEY)).thenReturn(Optional.of(stale));

        assertThat(service.claim(KEY, HASH)).isEmpty();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("completing a key stores the response payload against it")
    void storesTheResponseOnCompletion() {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(KEY)
                .status(IdempotencyStatus.PENDING)
                .build();
        when(repository.findById(KEY)).thenReturn(Optional.of(record));

        service.complete(KEY, result());

        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.SUCCESS);
        assertThat(record.getResponsePayload()).contains("COMPLETED");
        verify(repository).save(record);
    }

    @Test
    @DisplayName("recording a rejection is skipped if another attempt already wrote the key")
    void skipsFailureRecordingOnRace() {
        when(repository.existsById(KEY)).thenReturn(true);

        service.recordFailure(KEY, HASH, new InsufficientFundsException(
                UUID.randomUUID(), BigDecimal.ZERO, BigDecimal.ONE));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("falls back to a generic rejection if the stored failure is unreadable")
    void toleratesCorruptFailurePayloads() {
        when(repository.findByKeyForUpdate(KEY)).thenReturn(Optional.of(IdempotencyRecord.builder()
                .idempotencyKey(KEY)
                .requestHash(HASH)
                .status(IdempotencyStatus.FAILURE)
                .errorMessage("not json at all")
                .build()));

        assertThatThrownBy(() -> service.claim(KEY, HASH))
                .isInstanceOf(ReplayedFailureException.class)
                .hasMessageContaining("previously rejected");
    }

    @Test
    @DisplayName("looking up an unknown key returns nothing")
    void lookupMissesAreEmpty() {
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        assertThat(service.lookup(KEY, HASH)).isEmpty();
    }
}

package com.ledger.wallet.service;

import com.ledger.wallet.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Deletes idempotency records past their TTL. Without this the table grows without bound:
 * every money movement writes exactly one row and nothing else removes it.
 *
 * <p>Deleting a key means a very old retry would execute as a new request, which is
 * acceptable because clients give up long before the TTL expires.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiredIdempotencyKeyPurger {

    private final IdempotencyRecordRepository repository;

    @Scheduled(cron = "${app.idempotency.purge-cron:0 0 * * * *}")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired idempotency keys", deleted);
        }
    }
}

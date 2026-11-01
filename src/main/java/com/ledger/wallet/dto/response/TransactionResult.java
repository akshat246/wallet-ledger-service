package com.ledger.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Outcome of a money-movement request.
 *
 * <p>This is the payload stored against the idempotency key, so a retry can be answered with
 * exactly what the original request returned. {@code replayed} is false on that first
 * execution and true whenever the stored copy is served.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResult(
        UUID transactionId,
        String idempotencyKey,
        String type,
        String status,
        BigDecimal amount,
        String currency,
        UUID fromWalletId,
        UUID toWalletId,
        boolean replayed,
        Instant timestamp
) {

    /** The same result, marked as served from the idempotency store. */
    public TransactionResult asReplayed() {
        return new TransactionResult(transactionId, idempotencyKey, type, status, amount,
                currency, fromWalletId, toWalletId, true, timestamp);
    }
}

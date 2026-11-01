package com.ledger.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A stored transaction, as returned by the read endpoints. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(
        UUID id,
        String idempotencyKey,
        UUID fromWalletId,
        UUID toWalletId,
        BigDecimal amount,
        String currency,
        String type,
        String status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) { }

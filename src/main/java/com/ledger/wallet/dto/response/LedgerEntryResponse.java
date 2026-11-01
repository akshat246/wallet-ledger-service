package com.ledger.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LedgerEntryResponse(
        UUID id,
        UUID walletId,
        UUID transactionId,
        String entryType,
        BigDecimal amount,
        BigDecimal runningBalance,
        String description,
        Instant createdAt
) { }

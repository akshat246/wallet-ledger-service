package com.ledger.wallet.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Lightweight view for the balance endpoint, which is called far more often than the rest. */
public record BalanceResponse(
        UUID walletId,
        BigDecimal balance,
        String currency,
        Instant asOf
) { }

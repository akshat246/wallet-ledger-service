package com.ledger.wallet.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(

        @NotNull(message = "From wallet ID is required")
        UUID fromWalletId,

        @NotNull(message = "To wallet ID is required")
        UUID toWalletId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @DecimalMax(value = "999999999999999.99", message = "Amount is too large")
        @Digits(integer = 17, fraction = 2, message = "Amount may have at most 2 decimal places")
        BigDecimal amount,

        @Size(max = 500, message = "Description may be at most 500 characters")
        String description
) { }

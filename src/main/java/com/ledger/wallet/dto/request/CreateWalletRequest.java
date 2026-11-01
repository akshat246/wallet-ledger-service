package com.ledger.wallet.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CreateWalletRequest(

        @NotNull(message = "User ID is required")
        UUID userId,

        /** ISO 4217 code; the service default is used when omitted. */
        @Pattern(regexp = "^$|^[A-Za-z]{3}$", message = "Currency should be a 3-letter ISO 4217 code")
        String currency,

        @Pattern(regexp = "^$|^[A-Za-z_]{2,50}$", message = "Wallet type should be 2-50 letters")
        String walletType
) { }

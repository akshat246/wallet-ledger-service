package com.natwest.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWalletRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    /**
     * ISO 4217 code. Defaults to INR when omitted.
     */
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency should be a 3-letter ISO 4217 code (e.g. INR, USD)")
    private String currency;

    private String walletType;
}

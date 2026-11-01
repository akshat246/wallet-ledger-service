package com.ledger.wallet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Set;

/**
 * Currencies the service will open wallets in. The same list is enforced by a check
 * constraint on {@code wallets.currency}; change both together.
 */
@ConfigurationProperties(prefix = "app.wallet")
public record WalletProperties(
        @DefaultValue("INR") String defaultCurrency,
        @DefaultValue({"INR", "USD", "EUR", "GBP"}) Set<String> supportedCurrencies,
        @DefaultValue("PRIMARY") String defaultType
) { }

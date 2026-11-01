package com.ledger.wallet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param ttl          how long a stored outcome stays replayable; afterwards the row is
 *                     purged and a retry would execute as a fresh request
 * @param maxKeyLength longest accepted Idempotency-Key, matching the column width
 */
@ConfigurationProperties(prefix = "app.idempotency")
public record IdempotencyProperties(
        @DefaultValue("24h") Duration ttl,
        @DefaultValue("255") int maxKeyLength
) { }

package com.ledger.wallet.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Serves the generated API description at /swagger-ui.html and /v3/api-docs. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI walletLedgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Wallet Ledger Service")
                .version("1.0.0")
                .description("""
                        Wallet balances backed by an immutable double-entry ledger.
                        Money-movement endpoints require an Idempotency-Key header; repeating a
                        key returns the original outcome instead of moving money again.
                        """));
    }
}

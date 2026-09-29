package com.natwest.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main Spring Boot Application for Wallet Ledger Service
 * 
 * Distributed payment/wallet ledger service that demonstrates:
 * - Atomic ACID transactions with row-level locking
 * - Idempotent payment processing to prevent double-charging
 * - Event-driven async processing with Kafka
 * - Resilience patterns (circuit breakers, retries)
 * - Comprehensive testing with TestContainers
 * 
 * Architecture:
 * - PostgreSQL: ACID transactions, row-level locks
 * - Redis: Distributed locks, caching
 * - Kafka: Async event processing
 * - Resilience4j: Circuit breakers, retry policies
 */
@SpringBootApplication
@EnableCaching
@EnableKafka
@EnableAsync
@EnableTransactionManagement
@ComponentScan(basePackages = "com.natwest.wallet")
public class WalletLedgerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletLedgerServiceApplication.class, args);
    }
}

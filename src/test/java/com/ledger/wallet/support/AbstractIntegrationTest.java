package com.ledger.wallet.support;

import com.ledger.wallet.domain.WalletStatus;
import com.ledger.wallet.dto.request.CreateUserRequest;
import com.ledger.wallet.dto.request.CreateWalletRequest;
import com.ledger.wallet.dto.request.DepositRequest;
import com.ledger.wallet.service.TransactionService;
import com.ledger.wallet.service.UserService;
import com.ledger.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for tests that need the real database.
 *
 * <p>These run against PostgreSQL in a container rather than an in-memory database, because
 * almost everything worth testing here — {@code SELECT FOR UPDATE} blocking, primary-key
 * contention between concurrent inserts, check constraints, JSONB — behaves differently or
 * not at all in H2. A green test on H2 would say nothing about production.
 *
 * <p>The container starts once for the whole JVM and is shared by every test class; starting
 * one per class would add about ten seconds each. Testcontainers' reaper removes it when the
 * JVM exits, so there is no shutdown hook to maintain.
 */
// RANDOM_PORT so the HTTP-level tests get a real server and a TestRestTemplate; the
// service-level tests simply ignore it.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("wallet_ledger")
                    .withUsername("walletuser")
                    .withPassword("walletpass123");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected UserService userService;
    @Autowired protected WalletService walletService;
    @Autowired protected TransactionService transactionService;
    @Autowired protected JdbcTemplate jdbcTemplate;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    /**
     * Each test starts from an empty database. Truncating is used rather than wrapping every
     * test in a rolled-back transaction, because the concurrency tests need their writes to
     * actually commit for other threads to see them.
     */
    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE ledger_entries, transactions, idempotency_keys, wallets, users
                RESTART IDENTITY CASCADE
                """);
    }

    protected UUID newUser() {
        String unique = "user" + SEQUENCE.incrementAndGet() + "-" + UUID.randomUUID() + "@example.com";
        return userService.createUser(new CreateUserRequest(unique, "Test User", null)).id();
    }

    protected UUID newWallet() {
        return newWallet("INR");
    }

    protected UUID newWallet(String currency) {
        return walletService.createWallet(new CreateWalletRequest(newUser(), currency, null)).id();
    }

    /** A wallet with money already in it, funded through the normal deposit path. */
    protected UUID newFundedWallet(String amount) {
        UUID walletId = newWallet();
        fund(walletId, amount);
        return walletId;
    }

    protected void fund(UUID walletId, String amount) {
        transactionService.deposit(key("seed"),
                new DepositRequest(walletId, new BigDecimal(amount), "Test funding"));
    }

    protected void setWalletStatus(UUID walletId, WalletStatus status) {
        jdbcTemplate.update("UPDATE wallets SET status = ? WHERE id = ?", status.name(), walletId);
    }

    protected BigDecimal balanceOf(UUID walletId) {
        return walletService.getBalance(walletId).balance();
    }

    /** A unique idempotency key, so helper calls never collide with the key under test. */
    protected static String key(String prefix) {
        return prefix + "-" + SEQUENCE.incrementAndGet() + "-" + UUID.randomUUID();
    }
}

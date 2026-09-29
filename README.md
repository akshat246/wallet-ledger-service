# Wallet Ledger Service

A wallet and ledger backend that moves money between accounts atomically, survives client
retries without double-charging, and keeps an immutable double-entry audit trail.

Stack: Java 21, Spring Boot 3.2, PostgreSQL 15 (Flyway), Redis, Kafka. Redis and Kafka are
provisioned in `docker-compose.yml` and wired in `application.yml`; they are used from Phase 3
onwards (caching, distributed locks, async events).

## How money moves

Every transfer, deposit or withdrawal runs as a single database transaction:

1. The `Idempotency-Key` header is claimed by inserting a row into `idempotency_keys`.
   Because that column is the primary key, a concurrent duplicate blocks here until the first
   request commits, then reads the stored result instead of processing again.
2. The wallet rows involved are locked with `SELECT ... FOR UPDATE`, always in ascending UUID
   order so two opposite transfers cannot deadlock.
3. Status, currency and balance are validated against the locked rows.
4. Balances are updated, a `transactions` row is written, and one `ledger_entries` row is
   written per wallet (DEBIT / CREDIT) with the running balance after the movement.
5. The idempotency row is marked SUCCESS with the response body and everything commits together.

A business rejection (insufficient funds, frozen wallet, currency mismatch) rolls all of that
back and the failure is recorded against the key in a separate transaction, so a retry with
the same key gets the same rejection. Reusing a key with a different payload is rejected with
`422 IDEMPOTENCY_KEY_ERROR`.

Wallets also carry a JPA `@Version` column as a second line of defence against lost updates.

## Running locally

Prerequisites: JDK 21, Maven (or `mvnd`), Docker.

```bash
docker compose up -d                 # postgres, redis, kafka, zookeeper
mvn spring-boot:run                  # Flyway applies src/main/resources/db/migration on start
curl localhost:8080/actuator/health
```

Database credentials and ports live in `application.yml` and match `docker-compose.yml`.

## API

All responses are wrapped as `{ "data": ..., "message": ..., "timestamp": ... }`; errors as
`{ "errorCode", "message", "status", "error", "path", "timestamp" }`.

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/users` | `{ email, name, phoneNumber? }` |
| GET | `/api/v1/users/{id}` | |
| GET | `/api/v1/users/{id}/wallets` | |
| POST | `/api/v1/wallets` | `{ userId, currency?, walletType? }` — currency defaults to INR; INR/USD/EUR/GBP supported |
| GET | `/api/v1/wallets/{id}` | |
| GET | `/api/v1/wallets/{id}/balance` | |
| GET | `/api/v1/wallets/{id}/ledger` | Double-entry rows, newest first |
| GET | `/api/v1/wallets/{id}/transactions` | Transactions where the wallet is source or destination |
| POST | `/api/v1/transactions/transfer` | Header `Idempotency-Key`; `{ fromWalletId, toWalletId, amount, description? }` |
| POST | `/api/v1/transactions/deposit` | Header `Idempotency-Key`; `{ toWalletId, amount, description? }` |
| POST | `/api/v1/transactions/withdraw` | Header `Idempotency-Key`; `{ fromWalletId, amount, description? }` |
| GET | `/api/v1/transactions/{id}` | |

Money-movement endpoints return `201` on first execution and `200` with `"replayed": true`
when the key has been seen before. `api.http` at the repo root has a runnable walkthrough.

Error codes: `VALIDATION_ERROR`, `USER_NOT_FOUND`, `USER_ALREADY_EXISTS`, `WALLET_NOT_FOUND`,
`WALLET_NOT_ACTIVE`, `UNSUPPORTED_CURRENCY`, `CURRENCY_MISMATCH`, `INSUFFICIENT_FUNDS`,
`SAME_WALLET_TRANSFER`, `INVALID_AMOUNT`, `IDEMPOTENCY_KEY_ERROR`, `TRANSACTION_NOT_FOUND`,
`CONCURRENT_MODIFICATION`, `TRANSACTION_FAILED`.

## Layout

```
src/main/java/com/natwest/wallet
├── controller/   REST endpoints
├── service/      TransactionService (API + idempotency orchestration)
│                 TransactionExecutor (the transactional core described above)
│                 IdempotencyService, WalletService, UserService
├── repository/   Spring Data JPA, incl. FOR UPDATE queries
├── domain/       JPA entities
├── dto/          Request / response bodies
└── exception/    Business exceptions + GlobalExceptionHandler
src/main/resources/db/migration   Flyway schema
```

## Roadmap

- Phase 2: unit tests (Mockito) and integration tests (Testcontainers) including concurrent
  transfer and duplicate-key scenarios; JaCoCo report.
- Phase 3: Redis cache for balances and idempotency fast-path, Kafka transaction events with
  an outbox table, Resilience4j retry / circuit breaker on downstream calls.

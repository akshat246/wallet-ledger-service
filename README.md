# Wallet Ledger Service

A wallet backend that moves money between accounts atomically, survives client retries
without double-charging, stays correct under concurrent access, and keeps an immutable
double-entry record of everything that happened.

Java 21, Spring Boot 3.2, PostgreSQL 15 with Flyway. Redis and Kafka are provisioned in
`docker-compose.yml` and configured in `application.yml`; see [What is next](#what-is-next)
for how they are meant to be used.

## How money moves

Every transfer, deposit and withdrawal runs as a single database transaction:

1. **Claim the idempotency key** by inserting a row into `idempotency_keys`. That column is
   the primary key, so a concurrent request carrying the same key blocks here until the first
   one commits, then reads the stored result instead of processing again.
2. **Lock the wallet rows** with `SELECT ... FOR UPDATE`, always in ascending id order so a
   transfer A→B and a simultaneous B→A queue rather than deadlock.
3. **Validate** status, currency and funds against the locked rows. Checking before locking
   would reintroduce the race it is there to prevent.
4. **Update balances**, write the `transactions` row, and append one `ledger_entries` row per
   wallet (DEBIT / CREDIT) carrying the balance after the movement.
5. **Mark the key SUCCESS** with the response body, and commit all of it together.

A business rejection — insufficient funds, frozen wallet, currency mismatch — rolls the whole
thing back, and the rejection is then recorded against the key in a separate transaction. A
retry of that request receives the same rejection rather than a different answer. Reusing a
key for a different payload is refused with `422 IDEMPOTENCY_KEY_ERROR`.

Wallets also carry a JPA `@Version` column, and the database enforces `CHECK (balance >= 0)`.
Neither should ever fire given the locking above; they are there so that a bug in some future
code path cannot quietly corrupt a balance.

## Running locally

Prerequisites: JDK 21, Maven, Docker.

```bash
docker compose up -d           # postgres, redis, kafka, zookeeper
mvn spring-boot:run            # Flyway applies the migration on startup
curl localhost:8080/actuator/health
```

API docs are served at `http://localhost:8080/swagger-ui.html`, and `api.http` at the repo
root is a click-through walkthrough for IntelliJ or the VS Code REST Client.

Use the `dev` profile (`mvn spring-boot:run -Dspring-boot.run.profiles=dev`) for SQL logging.
It is a separate profile deliberately: the bind-parameter logging it enables would print
account identifiers and amounts in plain text.

## Tests

```bash
mvn test       # unit tests only: fast, no Docker required
mvn verify     # adds the integration tests and writes target/site/jacoco
```

Unit tests (`*Test`) cover the movement arithmetic, double-entry correctness, lock ordering,
idempotency bookkeeping, and the HTTP contract through MockMvc.

Integration tests (`*IT`) run against a real PostgreSQL via Testcontainers, because the parts
most worth testing do not exist in an in-memory database. Among them:

| Test | What it proves |
|---|---|
| 100 concurrent withdrawals from a balance of 50 | Exactly 50 succeed — no lost update |
| 25 transfers A→B interleaved with 25 B→A | Lock ordering prevents deadlock |
| 50 concurrent requests sharing one key | Exactly one transaction row is written |
| Retry of a rejected request after funds arrive | Still rejected, identically |
| Reconciliation after mixed load | Every balance equals credits minus debits |

## API

Successful responses are wrapped as `{ "data": ..., "message": ..., "timestamp": ... }`;
errors as `{ "errorCode", "message", "status", "error", "path", "fieldErrors", "timestamp" }`.
Clients should branch on `errorCode`, which is stable.

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/users` | `{ email, name, phoneNumber? }` |
| GET | `/api/v1/users/{id}` | |
| GET | `/api/v1/users/{id}/wallets` | |
| POST | `/api/v1/wallets` | `{ userId, currency?, walletType? }` — INR, USD, EUR, GBP |
| GET | `/api/v1/wallets/{id}` | |
| GET | `/api/v1/wallets/{id}/balance` | |
| GET | `/api/v1/wallets/{id}/ledger` | Double-entry rows, newest first |
| GET | `/api/v1/wallets/{id}/transactions` | Where the wallet was sender or receiver |
| POST | `/api/v1/transactions/transfer` | Header `Idempotency-Key`; `{ fromWalletId, toWalletId, amount, description? }` |
| POST | `/api/v1/transactions/deposit` | Header `Idempotency-Key`; `{ toWalletId, amount, description? }` |
| POST | `/api/v1/transactions/withdraw` | Header `Idempotency-Key`; `{ fromWalletId, amount, description? }` |
| GET | `/api/v1/transactions/{id}` | |

Money-movement endpoints return `201` on first execution and `200` with `"replayed": true`
when the key has been seen before.

Status codes carry meaning: `400` for a malformed request, `404` for something that does not
exist, `409` for a state conflict such as a duplicate email or lock contention, `422` when
the request is well formed but a business rule refuses it, `503` when the client should
retry.

## Layout

```
src/main/java/com/ledger/wallet
├── controller/   REST endpoints and the global error handler
├── service/      TransactionService          money-movement API and idempotency orchestration
│                 LedgerTransactionExecutor   the transactional core described above
│                 IdempotencyService          claim / complete / replay
│                 ReconciliationService       proves balance == ledger
│                 UserService, WalletService
├── repository/   Spring Data JPA, including the FOR UPDATE query
├── domain/       JPA entities and enums
├── dto/          request/ and response/ records
├── mapper/       entity to DTO
├── config/       typed configuration properties, OpenAPI, scheduling
└── exception/    business exceptions, each carrying its own error code and HTTP status
src/main/resources/db/migration   Flyway schema
src/test/java/...                 *Test (unit) and *IT (Testcontainers)
```

`TransactionService` and `LedgerTransactionExecutor` are separate beans on purpose. Spring
applies `@Transactional` through a proxy, so a rollback only becomes visible to a caller
outside the annotated class — and reacting to a rollback is exactly what the idempotency
bookkeeping has to do.

## Design notes

**Money is `NUMERIC(19,2)` and `BigDecimal`, never a floating point type.** 0.1 + 0.2 is not
0.3 in binary floating point, and the error compounds across a ledger.

**Balances are materialised, and the ledger is the authority.** Storing the balance makes
reads O(1) instead of proportional to the number of entries ever written. `ReconciliationService`
is what keeps that optimisation honest, and the integration tests assert it after every
scenario.

**The ledger is append-only.** `@Immutable` stops Hibernate issuing an UPDATE; corrections
are compensating entries. This is how an auditor expects to read it.

**Schema is versioned SQL, not `ddl-auto=update`.** Hibernate only validates that entities
still match, so a drifted mapping fails at startup rather than at the first query.

## What is next

**Redis** — cache-aside for balance reads: read through with a short TTL, and evict both
wallet keys after a transfer commits rather than writing the new value, since a write-through
cache can end up holding a value from a transaction that later rolled back. Eviction belongs
in an after-commit callback, not inside the transaction.

**Kafka** — transaction events via the outbox pattern. Publishing inside the transaction is
the dual-write problem: the database can roll back after the message is sent, or the send can
fail after the commit. Instead the event is written to an `outbox` table in the same
transaction and a relay publishes it. Consumers dedupe on `transaction_id`, because Kafka
delivers at least once, and the wallet id is the partition key so one wallet's events stay
ordered.

**Resilience4j** — retry with backoff for transient failures and a circuit breaker around any
downstream dependency. Retries are only safe because the operations are idempotent, which is
the whole point of the key.

Known gaps worth naming: there is no authentication or authorisation, no rate limiting, and
no multi-currency conversion.

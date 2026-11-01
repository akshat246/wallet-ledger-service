-- Wallet ledger: users, their wallets, the movements between them, and the append-only
-- double-entry record of those movements.
--
-- Constraints here are the last line of defence. The application validates first, but these
-- hold even if a bug, a manual query, or a future service gets it wrong.

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    name          VARCHAR(255) NOT NULL,
    phone_number  VARCHAR(20),
    status        VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    kyc_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT users_status_valid CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'CLOSED'))
);

-- No separate index on email: the UNIQUE constraint already creates one.

-- ---------------------------------------------------------------------------
-- wallets
-- ---------------------------------------------------------------------------
CREATE TABLE wallets (
    id          UUID PRIMARY KEY,
    user_id     UUID           NOT NULL REFERENCES users (id),
    -- NUMERIC, never a floating point type: 0.10 + 0.20 must be exactly 0.30.
    balance     NUMERIC(19, 2) NOT NULL DEFAULT 0,
    currency    VARCHAR(3)     NOT NULL DEFAULT 'INR',
    status      VARCHAR(50)    NOT NULL DEFAULT 'ACTIVE',
    wallet_type VARCHAR(50)    NOT NULL DEFAULT 'PRIMARY',
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version     BIGINT         NOT NULL DEFAULT 0,

    -- The invariant that matters most: no code path can overdraw a wallet.
    CONSTRAINT wallets_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT wallets_currency_supported CHECK (currency IN ('INR', 'USD', 'EUR', 'GBP')),
    CONSTRAINT wallets_status_valid CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

CREATE INDEX idx_wallets_user_id ON wallets (user_id);

-- ---------------------------------------------------------------------------
-- transactions
-- ---------------------------------------------------------------------------
CREATE TABLE transactions (
    id              UUID PRIMARY KEY,
    -- UNIQUE here is a second guarantee against double processing, independent of the
    -- idempotency_keys table: even a bug there cannot produce two movements for one key.
    idempotency_key VARCHAR(255)   NOT NULL UNIQUE,
    from_wallet_id  UUID           REFERENCES wallets (id),
    to_wallet_id    UUID           REFERENCES wallets (id),
    amount          NUMERIC(19, 2) NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    type            VARCHAR(50)    NOT NULL,
    status          VARCHAR(50)    NOT NULL DEFAULT 'PENDING',
    failure_reason  VARCHAR(500),
    metadata        JSONB,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version         BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT transactions_type_valid CHECK (type IN ('TRANSFER', 'DEPOSIT', 'WITHDRAWAL')),
    CONSTRAINT transactions_status_valid CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    -- A transfer has both sides. A deposit has only a destination and a withdrawal only a
    -- source, because the counterparty is outside the system.
    CONSTRAINT transactions_wallets_match_type CHECK (
        (type = 'TRANSFER'   AND from_wallet_id IS NOT NULL AND to_wallet_id IS NOT NULL
                             AND from_wallet_id <> to_wallet_id)
     OR (type = 'DEPOSIT'    AND from_wallet_id IS NULL     AND to_wallet_id IS NOT NULL)
     OR (type = 'WITHDRAWAL' AND from_wallet_id IS NOT NULL AND to_wallet_id IS NULL)
    )
);

-- Statement history is always read newest-first for one wallet, so the sort column is part
-- of the index and Postgres can skip the sort entirely.
CREATE INDEX idx_transactions_from_wallet ON transactions (from_wallet_id, created_at DESC);
CREATE INDEX idx_transactions_to_wallet ON transactions (to_wallet_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- ledger_entries  (append-only; never updated or deleted)
-- ---------------------------------------------------------------------------
CREATE TABLE ledger_entries (
    id              UUID PRIMARY KEY,
    wallet_id       UUID           NOT NULL REFERENCES wallets (id),
    transaction_id  UUID           NOT NULL REFERENCES transactions (id),
    entry_type      VARCHAR(50)    NOT NULL,
    amount          NUMERIC(19, 2) NOT NULL,
    -- The wallet's balance immediately after this entry, so a historical balance can be read
    -- directly instead of replaying every entry up to that point.
    running_balance NUMERIC(19, 2) NOT NULL,
    description     VARCHAR(500),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT ledger_entry_type_valid CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ledger_amount_positive CHECK (amount > 0),
    CONSTRAINT ledger_running_balance_non_negative CHECK (running_balance >= 0)
);

CREATE INDEX idx_ledger_wallet ON ledger_entries (wallet_id, created_at DESC);
CREATE INDEX idx_ledger_transaction ON ledger_entries (transaction_id);

-- ---------------------------------------------------------------------------
-- idempotency_keys
-- ---------------------------------------------------------------------------
-- The key is the primary key, which is what makes concurrent duplicates serialise: the
-- second insert waits on the first and then fails, instead of both moving money.
CREATE TABLE idempotency_keys (
    idempotency_key  VARCHAR(255) PRIMARY KEY,
    request_hash     VARCHAR(64),
    status           VARCHAR(50) NOT NULL,
    response_payload TEXT,
    error_message    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ NOT NULL,

    CONSTRAINT idempotency_status_valid CHECK (status IN ('PENDING', 'SUCCESS', 'FAILURE'))
);

-- Supports the scheduled purge of expired keys.
CREATE INDEX idx_idempotency_expires_at ON idempotency_keys (expires_at);

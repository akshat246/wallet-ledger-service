-- ============================================================================
-- Initial Database Schema for Wallet Ledger Service
-- ============================================================================

-- Create UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================================
-- USERS TABLE
-- ============================================================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    kyc_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    
    CONSTRAINT email_format CHECK (email ~ '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}$')
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);

-- ============================================================================
-- WALLETS TABLE
-- ============================================================================
CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    wallet_type VARCHAR(50) NOT NULL DEFAULT 'PRIMARY',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    
    CONSTRAINT wallet_balance_precision CHECK (balance >= 0),
    CONSTRAINT wallet_currency_check CHECK (currency IN ('INR', 'USD', 'EUR', 'GBP'))
);

CREATE INDEX idx_wallets_user_id ON wallets(user_id);
CREATE INDEX idx_wallets_status ON wallets(status);
CREATE INDEX idx_wallets_currency ON wallets(currency);

-- ============================================================================
-- TRANSACTIONS TABLE
-- ============================================================================
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    from_wallet_id UUID REFERENCES wallets(id) ON DELETE SET NULL,
    to_wallet_id UUID REFERENCES wallets(id) ON DELETE SET NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    type VARCHAR(50) NOT NULL, -- TRANSFER, DEPOSIT, WITHDRAWAL
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, COMPLETED, FAILED
    failure_reason TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    
    CONSTRAINT amount_positive CHECK (amount > 0),
    CONSTRAINT type_valid CHECK (type IN ('TRANSFER', 'DEPOSIT', 'WITHDRAWAL')),
    CONSTRAINT status_valid CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT from_wallet_required_for_transfer CHECK (
        type != 'TRANSFER' OR from_wallet_id IS NOT NULL
    ),
    CONSTRAINT to_wallet_required_for_transfer CHECK (
        type != 'TRANSFER' OR to_wallet_id IS NOT NULL
    )
);

CREATE INDEX idx_transactions_idempotency_key ON transactions(idempotency_key);
CREATE INDEX idx_transactions_from_wallet ON transactions(from_wallet_id);
CREATE INDEX idx_transactions_to_wallet ON transactions(to_wallet_id);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_type ON transactions(type);
CREATE INDEX idx_transactions_created_at ON transactions(created_at DESC);

-- ============================================================================
-- LEDGER ENTRIES TABLE (Immutable Audit Trail - Double Entry Bookkeeping)
-- ============================================================================
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    entry_type VARCHAR(50) NOT NULL, -- DEBIT, CREDIT
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    running_balance NUMERIC(19, 2) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- This table is immutable, no updates allowed
    CONSTRAINT entry_type_valid CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_ledger_wallet_id ON ledger_entries(wallet_id);
CREATE INDEX idx_ledger_transaction_id ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_created_at ON ledger_entries(created_at DESC);

-- ============================================================================
-- IDEMPOTENCY LOG TABLE
-- Purpose: Store idempotency keys to prevent duplicate processing
-- ============================================================================
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    request_hash VARCHAR(64),
    status VARCHAR(50) NOT NULL, -- PENDING, SUCCESS, FAILURE
    response_payload TEXT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '24 hours'),
    
    CONSTRAINT status_valid CHECK (status IN ('PENDING', 'SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_idempotency_created_at ON idempotency_keys(created_at DESC);
CREATE INDEX idx_idempotency_expires_at ON idempotency_keys(expires_at);

-- ============================================================================
-- NOTIFICATIONS TABLE
-- Purpose: Track notifications sent to users
-- ============================================================================
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    transaction_id UUID REFERENCES transactions(id) ON DELETE SET NULL,
    notification_type VARCHAR(50) NOT NULL, -- EMAIL, SMS, IN_APP
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, SENT, FAILED
    recipient VARCHAR(255) NOT NULL,
    message_content TEXT,
    retry_count INTEGER DEFAULT 0 CHECK (retry_count >= 0),
    last_retry_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT notification_type_valid CHECK (notification_type IN ('EMAIL', 'SMS', 'IN_APP')),
    CONSTRAINT status_valid CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_transaction_id ON notifications(transaction_id);
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);

-- ============================================================================
-- WALLET LOCKS TABLE
-- Purpose: Track pessimistic locks for concurrent transaction handling
-- ============================================================================
CREATE TABLE wallet_locks (
    wallet_id UUID PRIMARY KEY REFERENCES wallets(id) ON DELETE CASCADE,
    locked_by VARCHAR(255) NOT NULL,
    locked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    
    CONSTRAINT lock_not_expired CHECK (expires_at > locked_at)
);

-- ============================================================================
-- AUDIT LOG TABLE
-- Purpose: Track system-level changes for compliance
-- ============================================================================
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL, -- CREATE, UPDATE, DELETE
    actor VARCHAR(255),
    changes JSONB,
    ip_address VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT action_valid CHECK (action IN ('CREATE', 'UPDATE', 'DELETE'))
);

CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);

-- ============================================================================
-- TRIGGERS
-- ============================================================================

-- Update modified timestamp on users
CREATE OR REPLACE FUNCTION update_users_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_updated_at_trigger
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION update_users_updated_at();

-- Update modified timestamp on wallets
CREATE OR REPLACE FUNCTION update_wallets_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER wallets_updated_at_trigger
BEFORE UPDATE ON wallets
FOR EACH ROW
EXECUTE FUNCTION update_wallets_updated_at();

-- Update modified timestamp on transactions
CREATE OR REPLACE FUNCTION update_transactions_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER transactions_updated_at_trigger
BEFORE UPDATE ON transactions
FOR EACH ROW
EXECUTE FUNCTION update_transactions_updated_at();

-- ============================================================================
-- GRANTS (for application user)
-- ============================================================================
GRANT USAGE ON SCHEMA public TO walletuser;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO walletuser;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO walletuser;

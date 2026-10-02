CREATE TABLE wallet_accounts (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    document VARCHAR(11) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT wallet_accounts_balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    from_account_id BIGINT REFERENCES wallet_accounts(id),
    to_account_id BIGINT NOT NULL REFERENCES wallet_accounts(id),
    amount NUMERIC(19, 2) NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    description VARCHAR(140),
    idempotency_key VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT transactions_distinct_accounts CHECK (from_account_id IS NULL OR from_account_id <> to_account_id),
    CONSTRAINT transactions_sender_idempotency_unique UNIQUE (from_account_id, idempotency_key)
);

CREATE INDEX transactions_sender_created_idx ON transactions(from_account_id, created_at DESC);
CREATE INDEX transactions_recipient_created_idx ON transactions(to_account_id, created_at DESC);
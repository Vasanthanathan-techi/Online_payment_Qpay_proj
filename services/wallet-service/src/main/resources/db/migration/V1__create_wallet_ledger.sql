CREATE TABLE wallet (
    id BINARY(16) PRIMARY KEY,
    owner_type VARCHAR(24) NOT NULL,
    owner_id BINARY(16) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(24) NOT NULL,
    available_minor BIGINT NOT NULL DEFAULT 0,
    held_minor BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_wallet_owner_currency UNIQUE (owner_type, owner_id, currency),
    CONSTRAINT ck_wallet_amounts CHECK (available_minor >= 0 AND held_minor >= 0),
    INDEX ix_wallet_owner (owner_id)
);

CREATE TABLE ledger_account (
    id BINARY(16) PRIMARY KEY,
    wallet_id BINARY(16) NULL,
    account_code VARCHAR(80) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    normal_side VARCHAR(6) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_ledger_account_code_currency UNIQUE (account_code, currency),
    CONSTRAINT ck_account_normal_side CHECK (normal_side IN ('DEBIT', 'CREDIT')),
    CONSTRAINT fk_account_wallet FOREIGN KEY (wallet_id) REFERENCES wallet(id),
    INDEX ix_ledger_account_wallet (wallet_id)
);

CREATE TABLE journal (
    id BINARY(16) PRIMARY KEY,
    journal_type VARCHAR(40) NOT NULL,
    business_reference VARCHAR(100) NOT NULL,
    operation VARCHAR(40) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    correlation_id BINARY(16) NOT NULL,
    description VARCHAR(500) NULL,
    posted_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_journal_business_operation UNIQUE (business_reference, operation),
    CONSTRAINT ck_journal_status CHECK (status IN ('DRAFT', 'POSTED', 'REVERSED')),
    INDEX ix_journal_correlation (correlation_id),
    INDEX ix_journal_created (created_at)
);

CREATE TABLE ledger_entry (
    id BINARY(16) PRIMARY KEY,
    journal_id BINARY(16) NOT NULL,
    account_id BINARY(16) NOT NULL,
    entry_side VARCHAR(6) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    sequence_no SMALLINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_entry_journal_sequence UNIQUE (journal_id, sequence_no),
    CONSTRAINT ck_entry_side CHECK (entry_side IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_entry_positive CHECK (amount_minor > 0),
    CONSTRAINT fk_entry_journal FOREIGN KEY (journal_id) REFERENCES journal(id),
    CONSTRAINT fk_entry_account FOREIGN KEY (account_id) REFERENCES ledger_account(id),
    INDEX ix_entry_account_created (account_id, created_at, id)
);

CREATE TABLE fund_hold (
    id BINARY(16) PRIMARY KEY,
    wallet_id BINARY(16) NOT NULL,
    business_reference VARCHAR(100) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_hold_reference UNIQUE (business_reference),
    CONSTRAINT ck_hold_amount CHECK (amount_minor > 0),
    CONSTRAINT fk_hold_wallet FOREIGN KEY (wallet_id) REFERENCES wallet(id),
    INDEX ix_hold_wallet_status (wallet_id, status),
    INDEX ix_hold_expiry (status, expires_at)
);

CREATE TABLE outbox_event (
    id BINARY(16) PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id BINARY(16) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    event_version INT NOT NULL,
    partition_key VARCHAR(190) NOT NULL,
    payload JSON NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    publish_attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NULL,
    INDEX ix_outbox_unpublished (published_at, next_attempt_at, occurred_at),
    INDEX ix_outbox_aggregate (aggregate_id, occurred_at)
);

CREATE TABLE inbox_event (
    consumer_name VARCHAR(100) NOT NULL,
    event_id BINARY(16) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
);

INSERT INTO ledger_account
    (id, wallet_id, account_code, account_type, normal_side, currency, status, created_at)
VALUES
    (UUID_TO_BIN(UUID(), 1), NULL, 'PROVIDER_CASH', 'ASSET', 'DEBIT', 'INR', 'ACTIVE', CURRENT_TIMESTAMP(6)),
    (UUID_TO_BIN(UUID(), 1), NULL, 'FEE_REVENUE', 'REVENUE', 'CREDIT', 'INR', 'ACTIVE', CURRENT_TIMESTAMP(6)),
    (UUID_TO_BIN(UUID(), 1), NULL, 'PAYOUT_CLEARING', 'ASSET', 'DEBIT', 'INR', 'ACTIVE', CURRENT_TIMESTAMP(6)),
    (UUID_TO_BIN(UUID(), 1), NULL, 'SUSPENSE', 'LIABILITY', 'CREDIT', 'INR', 'ACTIVE', CURRENT_TIMESTAMP(6));

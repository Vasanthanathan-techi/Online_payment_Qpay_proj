CREATE TABLE beneficiary (
 id BINARY(16) PRIMARY KEY, merchant_id BINARY(16) NOT NULL, beneficiary_reference VARCHAR(100) NOT NULL,
 name VARCHAR(190) NOT NULL, bank_code VARCHAR(40) NOT NULL, account_ciphertext VARBINARY(1024) NOT NULL,
 account_nonce BINARY(12) NOT NULL, account_key_version VARCHAR(64) NOT NULL, account_mask VARCHAR(32) NOT NULL,
 fingerprint BINARY(32) NOT NULL, status VARCHAR(24) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT uk_beneficiary_reference UNIQUE (merchant_id, beneficiary_reference),
 INDEX ix_beneficiary_fingerprint (merchant_id, fingerprint)
);
CREATE TABLE payout (
 id BINARY(16) PRIMARY KEY, merchant_id BINARY(16) NOT NULL, wallet_id BINARY(16) NOT NULL,
 beneficiary_id BINARY(16) NOT NULL, merchant_reference VARCHAR(100) NOT NULL, amount_minor BIGINT NOT NULL,
 fee_minor BIGINT NOT NULL DEFAULT 0, currency CHAR(3) NOT NULL, status VARCHAR(32) NOT NULL,
 hold_id BINARY(16) NULL, provider_code VARCHAR(40) NULL, provider_reference VARCHAR(190) NULL,
 failure_code VARCHAR(80) NULL, version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL, completed_at TIMESTAMP(6) NULL,
 CONSTRAINT uk_payout_merchant_reference UNIQUE (merchant_id, merchant_reference),
 CONSTRAINT uk_payout_provider_reference UNIQUE (provider_code, provider_reference),
 CONSTRAINT ck_payout_amount CHECK (amount_minor > 0),
 CONSTRAINT ck_payout_fee CHECK (fee_minor >= 0),
 CONSTRAINT fk_payout_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES beneficiary(id),
 INDEX ix_payout_merchant_created (merchant_id, created_at, id), INDEX ix_payout_status_updated (status, updated_at)
);
CREATE TABLE payout_saga (
 payout_id BINARY(16) PRIMARY KEY, saga_state VARCHAR(40) NOT NULL, next_action_at TIMESTAMP(6) NULL,
 retry_count INT NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_saga_payout FOREIGN KEY (payout_id) REFERENCES payout(id), INDEX ix_saga_action (saga_state, next_action_at)
);
CREATE TABLE idempotency_record (
 id BINARY(16) PRIMARY KEY, owner_id BINARY(16) NOT NULL, operation VARCHAR(80) NOT NULL,
 idempotency_key VARCHAR(190) NOT NULL, request_hash BINARY(32) NOT NULL, resource_id BINARY(16) NULL,
 expires_at TIMESTAMP(6) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT uk_payout_idempotency UNIQUE (owner_id, operation, idempotency_key)
);
CREATE TABLE outbox_event (
 id BINARY(16) PRIMARY KEY, aggregate_id BINARY(16) NOT NULL, event_type VARCHAR(120) NOT NULL,
 partition_key VARCHAR(190) NOT NULL, payload JSON NOT NULL, occurred_at TIMESTAMP(6) NOT NULL,
 published_at TIMESTAMP(6) NULL, publish_attempts INT NOT NULL DEFAULT 0, next_attempt_at TIMESTAMP(6) NULL,
 INDEX ix_payout_outbox (published_at, next_attempt_at, occurred_at)
);


CREATE TABLE merchant (
 id BINARY(16) PRIMARY KEY, legal_name VARCHAR(190) NOT NULL, display_name VARCHAR(190) NOT NULL,
 registration_number VARCHAR(100) NOT NULL, country_code CHAR(2) NOT NULL, status VARCHAR(24) NOT NULL,
 kyb_status VARCHAR(24) NOT NULL, default_currency CHAR(3) NOT NULL, webhook_url VARCHAR(2048) NULL,
 webhook_secret_ciphertext VARBINARY(1024) NULL, webhook_secret_nonce BINARY(12) NULL,
 webhook_secret_key_version VARCHAR(64) NULL, version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT uk_merchant_registration UNIQUE(country_code,registration_number),
 INDEX ix_merchant_status(status,updated_at), INDEX ix_merchant_kyb(kyb_status,updated_at)
);
CREATE TABLE merchant_api_key (
 id BINARY(16) PRIMARY KEY, merchant_id BINARY(16) NOT NULL, key_prefix VARCHAR(16) NOT NULL,
 secret_hash BINARY(32) NOT NULL, scopes_json JSON NOT NULL, status VARCHAR(24) NOT NULL,
 expires_at TIMESTAMP(6) NULL, last_used_at TIMESTAMP(6) NULL, created_at TIMESTAMP(6) NOT NULL,
 revoked_at TIMESTAMP(6) NULL, CONSTRAINT uk_api_key_hash UNIQUE(secret_hash),
 INDEX ix_api_key_merchant(merchant_id,status), CONSTRAINT fk_api_key_merchant FOREIGN KEY(merchant_id) REFERENCES merchant(id)
);
CREATE TABLE outbox_event (
 id BINARY(16) PRIMARY KEY, aggregate_id BINARY(16) NOT NULL, event_type VARCHAR(120) NOT NULL,
 partition_key VARCHAR(190) NOT NULL, payload JSON NOT NULL, occurred_at TIMESTAMP(6) NOT NULL,
 published_at TIMESTAMP(6) NULL, publish_attempts INT NOT NULL DEFAULT 0, next_attempt_at TIMESTAMP(6) NULL,
 INDEX ix_merchant_outbox(published_at,next_attempt_at,occurred_at)
);


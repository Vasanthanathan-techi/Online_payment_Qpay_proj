CREATE TABLE payment (
    id BINARY(16) PRIMARY KEY,
    merchant_id BINARY(16) NOT NULL,
    wallet_id BINARY(16) NOT NULL,
    merchant_reference VARCHAR(100) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    fee_minor BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    payment_method_type VARCHAR(32) NOT NULL,
    provider_code VARCHAR(40) NULL,
    provider_reference VARCHAR(190) NULL,
    next_action_url VARCHAR(2048) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    CONSTRAINT uk_payment_merchant_reference UNIQUE (merchant_id, merchant_reference),
    CONSTRAINT uk_payment_provider_reference UNIQUE (provider_code, provider_reference),
    CONSTRAINT ck_payment_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_payment_fee CHECK (fee_minor >= 0 AND fee_minor <= amount_minor),
    INDEX ix_payment_merchant_created (merchant_id, created_at, id),
    INDEX ix_payment_status_updated (status, updated_at)
);

CREATE TABLE payment_attempt (
    id BINARY(16) PRIMARY KEY,
    payment_id BINARY(16) NOT NULL,
    attempt_no INT NOT NULL,
    provider_code VARCHAR(40) NOT NULL,
    provider_request_id VARCHAR(190) NOT NULL,
    status VARCHAR(32) NOT NULL,
    failure_code VARCHAR(80) NULL,
    response_metadata JSON NULL,
    started_at TIMESTAMP(6) NOT NULL,
    finished_at TIMESTAMP(6) NULL,
    CONSTRAINT uk_payment_attempt_number UNIQUE (payment_id, attempt_no),
    CONSTRAINT uk_provider_request UNIQUE (provider_code, provider_request_id),
    CONSTRAINT fk_attempt_payment FOREIGN KEY (payment_id) REFERENCES payment(id)
);

CREATE TABLE idempotency_record (
    id BINARY(16) PRIMARY KEY,
    owner_id BINARY(16) NOT NULL,
    operation VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(190) NOT NULL,
    request_hash BINARY(32) NOT NULL,
    state VARCHAR(16) NOT NULL,
    resource_id BINARY(16) NULL,
    http_status SMALLINT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_idempotency_scope UNIQUE (owner_id, operation, idempotency_key),
    INDEX ix_idempotency_expiry (expires_at)
);

CREATE TABLE provider_webhook (
    id BINARY(16) PRIMARY KEY,
    provider_code VARCHAR(40) NOT NULL,
    provider_event_id VARCHAR(190) NOT NULL,
    payload_hash BINARY(32) NOT NULL,
    signature_valid BOOLEAN NOT NULL,
    processing_status VARCHAR(24) NOT NULL,
    related_resource_id BINARY(16) NULL,
    received_at TIMESTAMP(6) NOT NULL,
    processed_at TIMESTAMP(6) NULL,
    error_code VARCHAR(80) NULL,
    CONSTRAINT uk_provider_event UNIQUE (provider_code, provider_event_id),
    INDEX ix_webhook_processing (processing_status, received_at)
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
    INDEX ix_outbox_unpublished (published_at, next_attempt_at, occurred_at)
);


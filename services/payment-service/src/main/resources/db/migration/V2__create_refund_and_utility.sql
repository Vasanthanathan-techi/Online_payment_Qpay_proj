CREATE TABLE refund (
 id BINARY(16) PRIMARY KEY,payment_id BINARY(16) NOT NULL,merchant_id BINARY(16) NOT NULL,wallet_id BINARY(16) NOT NULL,
 merchant_reference VARCHAR(100) NOT NULL,amount_minor BIGINT NOT NULL,currency CHAR(3) NOT NULL,status VARCHAR(32) NOT NULL,
 provider_code VARCHAR(40) NULL,provider_reference VARCHAR(190) NULL,failure_code VARCHAR(80) NULL,
 version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMP(6) NOT NULL,updated_at TIMESTAMP(6) NOT NULL,completed_at TIMESTAMP(6) NULL,
 CONSTRAINT uk_refund_payment_reference UNIQUE(payment_id,merchant_reference),
 CONSTRAINT uk_refund_provider_reference UNIQUE(provider_code,provider_reference),
 CONSTRAINT ck_refund_amount CHECK(amount_minor>0),CONSTRAINT fk_refund_payment FOREIGN KEY(payment_id) REFERENCES payment(id),
 INDEX ix_refund_payment_created(payment_id,created_at),INDEX ix_refund_status_updated(status,updated_at)
);
CREATE TABLE utility_payment (
 id BINARY(16) PRIMARY KEY,merchant_id BINARY(16) NOT NULL,wallet_id BINARY(16) NOT NULL,
 merchant_reference VARCHAR(100) NOT NULL,biller_code VARCHAR(40) NOT NULL,consumer_reference VARCHAR(190) NOT NULL,
 amount_minor BIGINT NOT NULL,fee_minor BIGINT NOT NULL DEFAULT 0,currency CHAR(3) NOT NULL,status VARCHAR(32) NOT NULL,
 hold_id BINARY(16) NULL,provider_reference VARCHAR(190) NULL,failure_code VARCHAR(80) NULL,
 version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMP(6) NOT NULL,updated_at TIMESTAMP(6) NOT NULL,completed_at TIMESTAMP(6) NULL,
 CONSTRAINT uk_utility_merchant_reference UNIQUE(merchant_id,merchant_reference),
 CONSTRAINT uk_utility_provider_reference UNIQUE(biller_code,provider_reference),
 CONSTRAINT ck_utility_amount CHECK(amount_minor>0),INDEX ix_utility_status_updated(status,updated_at)
);


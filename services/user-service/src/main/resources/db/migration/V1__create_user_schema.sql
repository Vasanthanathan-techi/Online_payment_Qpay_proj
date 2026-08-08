CREATE TABLE app_user (
 id BINARY(16) PRIMARY KEY,status VARCHAR(24) NOT NULL,display_name VARCHAR(160) NOT NULL,
 email_normalized VARCHAR(190) NULL,phone_e164 VARCHAR(20) NULL,kyc_status VARCHAR(24) NOT NULL,
 kyc_reference VARCHAR(100) NULL,locale VARCHAR(16) NOT NULL DEFAULT 'en',version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL,updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT uk_user_email UNIQUE(email_normalized),CONSTRAINT uk_user_phone UNIQUE(phone_e164),
 INDEX ix_user_kyc(kyc_status,updated_at)
);
CREATE TABLE notification_preference (
 user_id BINARY(16) NOT NULL,event_type VARCHAR(80) NOT NULL,push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 email_enabled BOOLEAN NOT NULL DEFAULT FALSE,sms_enabled BOOLEAN NOT NULL DEFAULT FALSE,updated_at TIMESTAMP(6) NOT NULL,
 PRIMARY KEY(user_id,event_type),CONSTRAINT fk_preference_user FOREIGN KEY(user_id) REFERENCES app_user(id)
);
CREATE TABLE device_token (
 id BINARY(16) PRIMARY KEY,user_id BINARY(16) NOT NULL,platform VARCHAR(16) NOT NULL,
 token_ciphertext VARBINARY(2048) NOT NULL,token_nonce BINARY(12) NOT NULL,token_key_version VARCHAR(64) NOT NULL,
 token_hash BINARY(32) NOT NULL,token_mask VARCHAR(32) NOT NULL,status VARCHAR(24) NOT NULL,
 last_seen_at TIMESTAMP(6) NOT NULL,created_at TIMESTAMP(6) NOT NULL,revoked_at TIMESTAMP(6) NULL,
 CONSTRAINT uk_device_token_hash UNIQUE(token_hash),INDEX ix_device_user_status(user_id,status),
 CONSTRAINT fk_device_user FOREIGN KEY(user_id) REFERENCES app_user(id)
);
CREATE TABLE outbox_event (
 id BINARY(16) PRIMARY KEY,aggregate_id BINARY(16) NOT NULL,event_type VARCHAR(120) NOT NULL,
 partition_key VARCHAR(190) NOT NULL,payload JSON NOT NULL,occurred_at TIMESTAMP(6) NOT NULL,
 published_at TIMESTAMP(6) NULL,publish_attempts INT NOT NULL DEFAULT 0,next_attempt_at TIMESTAMP(6) NULL,
 INDEX ix_user_outbox(published_at,next_attempt_at,occurred_at)
);


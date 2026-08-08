CREATE TABLE internal_transaction (
 reference VARCHAR(190) PRIMARY KEY, provider_code VARCHAR(40) NOT NULL, provider_reference VARCHAR(190) NULL,
 amount_minor BIGINT NOT NULL, currency CHAR(3) NOT NULL, status VARCHAR(32) NOT NULL,
 occurred_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 UNIQUE KEY uk_internal_provider_ref(provider_code,provider_reference), INDEX ix_internal_date(provider_code,occurred_at)
);
CREATE TABLE reconciliation_run (
 id BINARY(16) PRIMARY KEY, provider_code VARCHAR(40) NOT NULL, settlement_date DATE NOT NULL,
 source_name VARCHAR(500) NOT NULL, source_hash BINARY(32) NOT NULL, status VARCHAR(24) NOT NULL,
 total_records BIGINT NOT NULL DEFAULT 0, matched_records BIGINT NOT NULL DEFAULT 0,
 discrepancy_records BIGINT NOT NULL DEFAULT 0, started_at TIMESTAMP(6) NOT NULL, completed_at TIMESTAMP(6) NULL,
 CONSTRAINT uk_recon_source UNIQUE(provider_code,source_hash), INDEX ix_recon_date(provider_code,settlement_date)
);
CREATE TABLE reconciliation_item (
 id BINARY(16) PRIMARY KEY, run_id BINARY(16) NOT NULL, provider_reference VARCHAR(190) NULL,
 internal_reference VARCHAR(190) NULL, provider_amount_minor BIGINT NULL, internal_amount_minor BIGINT NULL,
 currency CHAR(3) NOT NULL, provider_status VARCHAR(32) NULL, internal_status VARCHAR(32) NULL,
 match_status VARCHAR(40) NOT NULL, resolution_status VARCHAR(24) NOT NULL,
 suspense_required BOOLEAN NOT NULL DEFAULT FALSE, resolved_by BINARY(16) NULL,
 resolution_reason VARCHAR(500) NULL, created_at TIMESTAMP(6) NOT NULL, resolved_at TIMESTAMP(6) NULL,
 CONSTRAINT fk_item_run FOREIGN KEY(run_id) REFERENCES reconciliation_run(id),
 INDEX ix_recon_item_run_status(run_id,match_status), INDEX ix_recon_provider_reference(provider_reference),
 INDEX ix_recon_internal_reference(internal_reference)
);
CREATE TABLE reconciliation_audit (
 id BINARY(16) PRIMARY KEY, item_id BINARY(16) NOT NULL, actor_id BINARY(16) NOT NULL,
 action VARCHAR(40) NOT NULL, previous_status VARCHAR(24) NOT NULL, new_status VARCHAR(24) NOT NULL,
 reason VARCHAR(500) NOT NULL, occurred_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_audit_item FOREIGN KEY(item_id) REFERENCES reconciliation_item(id), INDEX ix_recon_audit_item(item_id,occurred_at)
);


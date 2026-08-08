# QPay Database Design

**Status:** Initial logical and physical design  
**Related:** [QPAY_ARCHITECTURE.md](QPAY_ARCHITECTURE.md)

## 1. Database boundaries

Each service owns a separate schema and database user. Cross-schema joins, foreign keys, and writes are prohibited. Identifiers from another service are stored as opaque references and validated through APIs or events.

| Schema | Owner | Store |
|---|---|---|
| `qpay_auth` | Authentication Service | MySQL |
| `qpay_user` | User Service | MySQL |
| `qpay_merchant` | Merchant Service | MySQL |
| `qpay_wallet` | Wallet Service | MySQL |
| `qpay_payment` | Payment Service | MySQL |
| `qpay_payout` | Payout Service | MySQL |
| `qpay_reconciliation` | Reconciliation Service | MySQL |
| `qpay_notification` | Notification Service | MongoDB |

Redis stores disposable cache, rate-limit, revocation, and session data only. It is not a financial system of record.

## 2. Shared conventions

- IDs are UUIDv7-compatible `BINARY(16)` values; APIs expose canonical UUID strings.
- Monetary values use signed `BIGINT` minor units plus `CHAR(3)` currency.
- Timestamps use `TIMESTAMP(6)` in UTC.
- Mutable aggregates include an integer `version` for optimistic locking.
- Business tables use `created_at` and `updated_at`; financial records are never hard deleted.
- Enumerations are stored as bounded `VARCHAR` values so application migrations can evolve them safely.
- Sensitive values use ciphertext, key version, and masked-display columns. Raw bank or credential data is never indexed.
- JSON is reserved for provider metadata or evolving attributes, not core queryable state.

## 3. Authentication schema

```sql
CREATE TABLE principal (
    id BINARY(16) PRIMARY KEY,
    principal_type VARCHAR(24) NOT NULL,
    external_owner_id BINARY(16) NOT NULL,
    login_name VARCHAR(190) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL,
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP(6) NULL,
    password_changed_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_principal_login (login_name),
    KEY ix_principal_owner (external_owner_id)
);

CREATE TABLE principal_role (
    principal_id BINARY(16) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (principal_id, role_code),
    CONSTRAINT fk_role_principal FOREIGN KEY (principal_id) REFERENCES principal(id)
);

CREATE TABLE refresh_token (
    id BINARY(16) PRIMARY KEY,
    principal_id BINARY(16) NOT NULL,
    family_id BINARY(16) NOT NULL,
    token_hash BINARY(32) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    revoked_at TIMESTAMP(6) NULL,
    replaced_by_id BINARY(16) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_refresh_hash (token_hash),
    KEY ix_refresh_principal_expiry (principal_id, expires_at),
    KEY ix_refresh_family (family_id),
    CONSTRAINT fk_refresh_principal FOREIGN KEY (principal_id) REFERENCES principal(id)
);
```

Refresh-token reuse revokes the entire token family. Redis may contain short-lived JWT revocation markers keyed by token ID.

## 4. User and merchant schemas

### User Service

```sql
CREATE TABLE app_user (
    id BINARY(16) PRIMARY KEY,
    status VARCHAR(24) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    email_normalized VARCHAR(190) NULL,
    phone_e164 VARCHAR(20) NULL,
    kyc_status VARCHAR(24) NOT NULL,
    kyc_reference VARCHAR(100) NULL,
    locale VARCHAR(16) NOT NULL DEFAULT 'en',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_user_email (email_normalized),
    UNIQUE KEY uk_user_phone (phone_e164),
    KEY ix_user_kyc (kyc_status, updated_at)
);
```

### Merchant Service

```sql
CREATE TABLE merchant (
    id BINARY(16) PRIMARY KEY,
    legal_name VARCHAR(190) NOT NULL,
    display_name VARCHAR(190) NOT NULL,
    registration_number VARCHAR(100) NOT NULL,
    country_code CHAR(2) NOT NULL,
    status VARCHAR(24) NOT NULL,
    kyb_status VARCHAR(24) NOT NULL,
    default_currency CHAR(3) NOT NULL,
    webhook_url VARCHAR(2048) NULL,
    webhook_secret_ciphertext VARBINARY(1024) NULL,
    webhook_secret_key_version VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_merchant_registration (country_code, registration_number),
    KEY ix_merchant_status (status, updated_at),
    KEY ix_merchant_kyb (kyb_status, updated_at)
);

CREATE TABLE merchant_api_key (
    id BINARY(16) PRIMARY KEY,
    merchant_id BINARY(16) NOT NULL,
    key_prefix VARCHAR(16) NOT NULL,
    secret_hash BINARY(32) NOT NULL,
    scopes_json JSON NOT NULL,
    status VARCHAR(24) NOT NULL,
    expires_at TIMESTAMP(6) NULL,
    last_used_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    UNIQUE KEY uk_api_key_hash (secret_hash),
    KEY ix_api_key_merchant (merchant_id, status),
    CONSTRAINT fk_api_key_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(id)
);
```

## 5. Wallet and double-entry ledger schema

```sql
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
    UNIQUE KEY uk_wallet_owner_currency (owner_type, owner_id, currency),
    KEY ix_wallet_owner (owner_id)
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
    UNIQUE KEY uk_ledger_account_code_currency (account_code, currency),
    KEY ix_ledger_account_wallet (wallet_id),
    CONSTRAINT ck_account_normal_side CHECK (normal_side IN ('DEBIT', 'CREDIT')),
    CONSTRAINT fk_account_wallet FOREIGN KEY (wallet_id) REFERENCES wallet(id)
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
    UNIQUE KEY uk_journal_business_operation (business_reference, operation),
    KEY ix_journal_correlation (correlation_id),
    KEY ix_journal_created (created_at),
    CONSTRAINT ck_journal_status CHECK (status IN ('DRAFT', 'POSTED', 'REVERSED'))
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
    UNIQUE KEY uk_entry_journal_sequence (journal_id, sequence_no),
    KEY ix_entry_account_created (account_id, created_at, id),
    CONSTRAINT ck_entry_side CHECK (entry_side IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_entry_positive CHECK (amount_minor > 0),
    CONSTRAINT fk_entry_journal FOREIGN KEY (journal_id) REFERENCES journal(id),
    CONSTRAINT fk_entry_account FOREIGN KEY (account_id) REFERENCES ledger_account(id)
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
    UNIQUE KEY uk_hold_reference (business_reference),
    KEY ix_hold_wallet_status (wallet_id, status),
    KEY ix_hold_expiry (status, expires_at),
    CONSTRAINT ck_hold_amount CHECK (amount_minor > 0),
    CONSTRAINT fk_hold_wallet FOREIGN KEY (wallet_id) REFERENCES wallet(id)
);
```

The application locks the wallet row while changing projections, creates all ledger entries, checks debit and credit totals in memory, and posts the journal within one local transaction. A database trigger is intentionally avoided because business rules and observability belong in the domain layer; periodic invariant queries independently detect corruption.

## 6. Payment schema

```sql
CREATE TABLE payment (
    id BINARY(16) PRIMARY KEY,
    merchant_id BINARY(16) NOT NULL,
    merchant_reference VARCHAR(100) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    fee_minor BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    payment_method_type VARCHAR(32) NOT NULL,
    provider_code VARCHAR(40) NULL,
    provider_reference VARCHAR(190) NULL,
    expires_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    UNIQUE KEY uk_payment_merchant_reference (merchant_id, merchant_reference),
    UNIQUE KEY uk_payment_provider_reference (provider_code, provider_reference),
    KEY ix_payment_merchant_created (merchant_id, created_at, id),
    KEY ix_payment_status_updated (status, updated_at),
    CONSTRAINT ck_payment_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_payment_fee CHECK (fee_minor >= 0)
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
    UNIQUE KEY uk_payment_attempt_number (payment_id, attempt_no),
    UNIQUE KEY uk_provider_request (provider_code, provider_request_id),
    CONSTRAINT fk_attempt_payment FOREIGN KEY (payment_id) REFERENCES payment(id)
);

CREATE TABLE refund (
    id BINARY(16) PRIMARY KEY,
    payment_id BINARY(16) NOT NULL,
    merchant_reference VARCHAR(100) NOT NULL,
    amount_minor BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(190) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_refund_payment_reference (payment_id, merchant_reference),
    KEY ix_refund_payment_created (payment_id, created_at),
    CONSTRAINT ck_refund_amount CHECK (amount_minor > 0),
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payment(id)
);
```

## 7. Payout schema

```sql
CREATE TABLE beneficiary (
    id BINARY(16) PRIMARY KEY,
    merchant_id BINARY(16) NOT NULL,
    beneficiary_reference VARCHAR(100) NOT NULL,
    name VARCHAR(190) NOT NULL,
    bank_code VARCHAR(40) NOT NULL,
    account_ciphertext VARBINARY(1024) NOT NULL,
    account_key_version VARCHAR(64) NOT NULL,
    account_mask VARCHAR(32) NOT NULL,
    fingerprint BINARY(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_beneficiary_reference (merchant_id, beneficiary_reference),
    KEY ix_beneficiary_fingerprint (merchant_id, fingerprint)
);

CREATE TABLE payout (
    id BINARY(16) PRIMARY KEY,
    merchant_id BINARY(16) NOT NULL,
    wallet_id BINARY(16) NOT NULL,
    beneficiary_id BINARY(16) NOT NULL,
    merchant_reference VARCHAR(100) NOT NULL,
    amount_minor BIGINT NOT NULL,
    fee_minor BIGINT NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    hold_id BINARY(16) NULL,
    provider_code VARCHAR(40) NULL,
    provider_reference VARCHAR(190) NULL,
    failure_code VARCHAR(80) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    UNIQUE KEY uk_payout_merchant_reference (merchant_id, merchant_reference),
    UNIQUE KEY uk_payout_provider_reference (provider_code, provider_reference),
    KEY ix_payout_merchant_created (merchant_id, created_at, id),
    KEY ix_payout_status_updated (status, updated_at),
    CONSTRAINT ck_payout_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_payout_fee CHECK (fee_minor >= 0)
);

CREATE TABLE payout_saga (
    payout_id BINARY(16) PRIMARY KEY,
    saga_state VARCHAR(40) NOT NULL,
    last_event_id BINARY(16) NULL,
    next_action_at TIMESTAMP(6) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    KEY ix_saga_action (saga_state, next_action_at),
    CONSTRAINT fk_saga_payout FOREIGN KEY (payout_id) REFERENCES payout(id)
);
```

## 8. Idempotency, inbox, and outbox

Each service that accepts commands uses the following local tables:

```sql
CREATE TABLE idempotency_record (
    id BINARY(16) PRIMARY KEY,
    owner_id BINARY(16) NOT NULL,
    operation VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(190) NOT NULL,
    request_hash BINARY(32) NOT NULL,
    state VARCHAR(16) NOT NULL,
    resource_id BINARY(16) NULL,
    http_status SMALLINT NULL,
    response_body JSON NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_idempotency_scope (owner_id, operation, idempotency_key),
    KEY ix_idempotency_expiry (expires_at)
);

CREATE TABLE inbox_event (
    consumer_name VARCHAR(100) NOT NULL,
    event_id BINARY(16) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
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
    KEY ix_outbox_unpublished (published_at, next_attempt_at, occurred_at),
    KEY ix_outbox_aggregate (aggregate_id, occurred_at)
);
```

Idempotency expiry must exceed the maximum client/provider retry window. Financial business uniqueness remains protected permanently by domain unique keys even after an idempotency response expires.

## 9. Webhook and audit records

```sql
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
    UNIQUE KEY uk_provider_event (provider_code, provider_event_id),
    KEY ix_webhook_processing (processing_status, received_at)
);

CREATE TABLE audit_record (
    id BINARY(16) PRIMARY KEY,
    actor_type VARCHAR(24) NOT NULL,
    actor_id BINARY(16) NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id VARCHAR(190) NOT NULL,
    correlation_id BINARY(16) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NULL,
    safe_details JSON NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    KEY ix_audit_resource (resource_type, resource_id, occurred_at),
    KEY ix_audit_actor (actor_id, occurred_at),
    KEY ix_audit_correlation (correlation_id)
);
```

Raw webhook bodies may be encrypted in restricted object storage when required for dispute evidence. The operational table stores only safe metadata and hashes.

## 10. Reconciliation schema

```sql
CREATE TABLE reconciliation_run (
    id BINARY(16) PRIMARY KEY,
    provider_code VARCHAR(40) NOT NULL,
    settlement_date DATE NOT NULL,
    source_object_key VARCHAR(500) NOT NULL,
    source_hash BINARY(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    total_records BIGINT NOT NULL DEFAULT 0,
    matched_records BIGINT NOT NULL DEFAULT 0,
    discrepancy_records BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    UNIQUE KEY uk_recon_source (provider_code, source_hash),
    KEY ix_recon_date (provider_code, settlement_date)
);

CREATE TABLE reconciliation_item (
    id BINARY(16) PRIMARY KEY,
    run_id BINARY(16) NOT NULL,
    provider_reference VARCHAR(190) NULL,
    internal_reference VARCHAR(190) NULL,
    provider_amount_minor BIGINT NULL,
    internal_amount_minor BIGINT NULL,
    currency CHAR(3) NOT NULL,
    match_status VARCHAR(40) NOT NULL,
    resolution_status VARCHAR(24) NOT NULL,
    resolved_by BINARY(16) NULL,
    resolution_reason VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    resolved_at TIMESTAMP(6) NULL,
    KEY ix_recon_item_run_status (run_id, match_status),
    KEY ix_recon_provider_reference (provider_reference),
    KEY ix_recon_internal_reference (internal_reference),
    CONSTRAINT fk_item_run FOREIGN KEY (run_id) REFERENCES reconciliation_run(id)
);
```

## 11. MongoDB notification collections

`notification` documents contain channel, recipient reference, template/version, safe variables, state, attempt count, next attempt, correlation ID, and timestamps. `delivery_attempt` documents contain provider response codes and redacted diagnostics. TTL indexes remove non-audit delivery details after the configured retention period.

Recommended indexes:

```javascript
db.notification.createIndex({ recipientId: 1, createdAt: -1 })
db.notification.createIndex({ status: 1, nextAttemptAt: 1 })
db.notification.createIndex({ idempotencyKey: 1 }, { unique: true })
db.delivery_attempt.createIndex({ notificationId: 1, attemptNo: 1 }, { unique: true })
db.delivery_attempt.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 })
```

## 12. Migration and data safety

- Flyway migrations are owned and executed by each service.
- Production migrations are forward-only; corrections use a new migration.
- Use expand/migrate/contract changes so old and new service versions can run together.
- Large backfills are resumable jobs, not startup migrations.
- New indexes on large tables use online capabilities and are validated against production-like volumes.
- Backup restore, point-in-time recovery, and ledger invariant checks are tested before launch.
- Read replicas may serve reporting but never wallet command decisions.

## 13. Required invariant checks

Scheduled controls must alert on:

- posted journals whose debit and credit totals differ;
- entry currency differing from journal or account currency;
- wallet projections differing from ledger-derived balances;
- negative available balances where overdraft is not enabled;
- completed payments/payouts missing a corresponding journal;
- duplicate provider or merchant references;
- stale pending provider outcomes and expired unreleased holds;
- unpublished outbox events or growing consumer inbox/DLQ backlogs.


package com.qpay.payment.infrastructure;

import com.qpay.payment.application.CreatePaymentCommand;
import com.qpay.payment.application.PaymentException;
import com.qpay.payment.application.PaymentView;
import com.qpay.payment.domain.PaymentStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PaymentJdbcRepository {
    private final JdbcClient jdbc;

    public PaymentJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Reservation reserve(CreatePaymentCommand command, byte[] requestHash, long feeMinor) {
        int inserted = jdbc.sql("""
                INSERT IGNORE INTO idempotency_record
                    (id, owner_id, operation, idempotency_key, request_hash, state,
                     expires_at, created_at, updated_at)
                VALUES (UUID_TO_BIN(:id, 1), UUID_TO_BIN(:ownerId, 1), 'CREATE_PAYMENT', :key,
                        :hash, 'PROCESSING', :expiresAt, :now, :now)
                """)
                .param("id", UUID.randomUUID().toString())
                .param("ownerId", command.merchantId().toString())
                .param("key", command.idempotencyKey())
                .param("hash", requestHash)
                .param("expiresAt", Instant.now().plus(48, ChronoUnit.HOURS))
                .param("now", Instant.now())
                .update();

        if (inserted == 0) {
            ExistingIdempotency existing = jdbc.sql("""
                    SELECT request_hash, BIN_TO_UUID(resource_id, 1) resource_id
                      FROM idempotency_record
                     WHERE owner_id = UUID_TO_BIN(:ownerId, 1)
                       AND operation = 'CREATE_PAYMENT' AND idempotency_key = :key
                    """)
                    .param("ownerId", command.merchantId().toString())
                    .param("key", command.idempotencyKey())
                    .query((rs, rowNum) -> new ExistingIdempotency(
                            rs.getBytes("request_hash"), rs.getString("resource_id")))
                    .single();
            if (!Arrays.equals(existing.requestHash(), requestHash)) {
                throw new PaymentException("idempotency key was already used with a different request");
            }
            if (existing.resourceId() == null) {
                throw new PaymentException("an identical payment request is already processing");
            }
            return new Reservation(find(UUID.fromString(existing.resourceId())).orElseThrow(), true);
        }

        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.sql("""
                INSERT INTO payment
                    (id, merchant_id, wallet_id, merchant_reference, amount_minor, currency, fee_minor,
                     status, payment_method_type, version, created_at, updated_at)
                VALUES (UUID_TO_BIN(:id, 1), UUID_TO_BIN(:merchantId, 1), UUID_TO_BIN(:walletId, 1),
                        :reference, :amount, :currency, :fee, 'CREATED', :method, 0, :now, :now)
                """)
                .param("id", paymentId.toString())
                .param("merchantId", command.merchantId().toString())
                .param("walletId", command.walletId().toString())
                .param("reference", command.merchantReference())
                .param("amount", command.amountMinor())
                .param("currency", command.currency().getCurrencyCode())
                .param("fee", feeMinor)
                .param("method", command.paymentMethodType())
                .param("now", now)
                .update();

        jdbc.sql("""
                UPDATE idempotency_record
                   SET state = 'COMPLETE', resource_id = UUID_TO_BIN(:paymentId, 1),
                       http_status = 201, updated_at = :now
                 WHERE owner_id = UUID_TO_BIN(:ownerId, 1)
                   AND operation = 'CREATE_PAYMENT' AND idempotency_key = :key
                """)
                .param("paymentId", paymentId.toString())
                .param("ownerId", command.merchantId().toString())
                .param("key", command.idempotencyKey())
                .param("now", now)
                .update();
        insertOutbox(paymentId, "payment.created.v1", paymentId.toString(), now, "PAYMENT_CREATED");
        return new Reservation(find(paymentId).orElseThrow(), false);
    }

    public void markProcessing(UUID paymentId) {
        Instant now = Instant.now();
        int changed = jdbc.sql("""
                UPDATE payment SET status = 'PROCESSING', version = version + 1, updated_at = :now
                 WHERE id = UUID_TO_BIN(:id, 1) AND status = 'CREATED'
                """).param("now", now).param("id", paymentId.toString()).update();
        if (changed != 1) {
            throw new PaymentException("payment is not ready for provider submission");
        }
    }

    public PaymentView providerCreated(UUID paymentId, String providerCode, String providerReference, String nextActionUrl) {
        Instant now = Instant.now();
        int changed = jdbc.sql("""
                UPDATE payment SET status = 'REQUIRES_ACTION', provider_code = :providerCode,
                       provider_reference = :providerReference, next_action_url = :nextActionUrl,
                       version = version + 1, updated_at = :now
                 WHERE id = UUID_TO_BIN(:id, 1) AND status = 'PROCESSING'
                """)
                .param("providerCode", providerCode)
                .param("providerReference", providerReference)
                .param("nextActionUrl", nextActionUrl)
                .param("now", now)
                .param("id", paymentId.toString())
                .update();
        if (changed != 1) {
            throw new PaymentException("payment is not in a provider-submittable state");
        }
        return find(paymentId).orElseThrow();
    }

    public PaymentView succeedWebhook(
            String providerCode, String eventId, String providerReference, byte[] payloadHash) {
        Instant now = Instant.now();
        int received = jdbc.sql("""
                INSERT IGNORE INTO provider_webhook
                    (id, provider_code, provider_event_id, payload_hash, signature_valid,
                     processing_status, received_at)
                VALUES (UUID_TO_BIN(:id, 1), :provider, :eventId, :hash, TRUE, 'PROCESSING', :now)
                """)
                .param("id", UUID.randomUUID().toString())
                .param("provider", providerCode)
                .param("eventId", eventId)
                .param("hash", payloadHash)
                .param("now", now)
                .update();

        PaymentView payment = findByProviderReference(providerCode, providerReference, true)
                .orElseThrow(() -> new PaymentException("provider payment reference was not found"));
        if (received == 0 || payment.status() == PaymentStatus.SUCCEEDED) {
            if (received == 0) {
                byte[] originalHash = jdbc.sql("""
                        SELECT payload_hash FROM provider_webhook
                         WHERE provider_code = :provider AND provider_event_id = :eventId
                        """).param("provider", providerCode).param("eventId", eventId)
                        .query(byte[].class).single();
                if (!Arrays.equals(originalHash, payloadHash)) {
                    throw new PaymentException("provider event id was reused with a different payload");
                }
            }
            return payment;
        }
        payment.status().requireTransitionTo(PaymentStatus.SUCCEEDED);

        jdbc.sql("""
                UPDATE payment SET status = 'SUCCEEDED', next_action_url = NULL,
                       version = version + 1, updated_at = :now, completed_at = :now
                 WHERE id = UUID_TO_BIN(:id, 1) AND status IN ('PROCESSING', 'REQUIRES_ACTION')
                """).param("now", now).param("id", payment.id().toString()).update();
        jdbc.sql("""
                UPDATE provider_webhook SET processing_status = 'PROCESSED',
                       related_resource_id = UUID_TO_BIN(:paymentId, 1), processed_at = :now
                 WHERE provider_code = :provider AND provider_event_id = :eventId
                """)
                .param("paymentId", payment.id().toString()).param("now", now)
                .param("provider", providerCode).param("eventId", eventId).update();
        insertPaymentSucceededOutbox(payment, providerCode, providerReference, now);
        return find(payment.id()).orElseThrow();
    }

    public Optional<PaymentView> find(UUID paymentId) {
        return paymentQuery("WHERE id = UUID_TO_BIN(:value, 1)", paymentId.toString(), false);
    }

    private Optional<PaymentView> findByProviderReference(String provider, String reference, boolean lock) {
        String suffix = lock ? " FOR UPDATE" : "";
        return jdbc.sql(baseSelect() + " WHERE provider_code = :provider AND provider_reference = :reference" + suffix)
                .param("provider", provider).param("reference", reference).query(this::map).optional();
    }

    private Optional<PaymentView> paymentQuery(String where, String value, boolean lock) {
        return jdbc.sql(baseSelect() + " " + where + (lock ? " FOR UPDATE" : ""))
                .param("value", value).query(this::map).optional();
    }

    private String baseSelect() {
        return """
                SELECT BIN_TO_UUID(id, 1) id, BIN_TO_UUID(merchant_id, 1) merchant_id,
                       BIN_TO_UUID(wallet_id, 1) wallet_id, merchant_reference, amount_minor,
                       fee_minor, currency, status, provider_reference, next_action_url,
                       created_at, updated_at FROM payment
                """;
    }

    private PaymentView map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new PaymentView(
                UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("merchant_id")),
                UUID.fromString(rs.getString("wallet_id")), rs.getString("merchant_reference"),
                rs.getLong("amount_minor"), rs.getLong("fee_minor"), Currency.getInstance(rs.getString("currency")),
                PaymentStatus.valueOf(rs.getString("status")), rs.getString("provider_reference"),
                rs.getString("next_action_url"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private void insertPaymentSucceededOutbox(
            PaymentView payment, String providerCode, String providerReference, Instant now) {
        jdbc.sql("""
                INSERT INTO outbox_event
                    (id, aggregate_type, aggregate_id, event_type, event_version, partition_key,
                     payload, occurred_at, publish_attempts)
                VALUES (UUID_TO_BIN(:eventId, 1), 'PAYMENT', UUID_TO_BIN(:paymentId, 1),
                        'payment.succeeded.v1', 1, :partitionKey,
                        JSON_OBJECT('paymentId', :paymentText, 'merchantId', :merchantId, 'walletId', :walletId,
                          'merchantReference', :reference, 'amountMinor', :amount,
                          'feeMinor', :fee, 'currency', :currency, 'providerCode', :providerCode,
                          'providerReference', :providerReference), :now, 0)
                """)
                .param("eventId", UUID.randomUUID().toString()).param("paymentId", payment.id().toString())
                .param("partitionKey", payment.id().toString()).param("paymentText", payment.id().toString())
                .param("merchantId", payment.merchantId().toString())
                .param("walletId", payment.walletId().toString()).param("reference", payment.merchantReference())
                .param("amount", payment.amountMinor()).param("fee", payment.feeMinor())
                .param("currency", payment.currency().getCurrencyCode()).param("providerCode", providerCode)
                .param("providerReference", providerReference).param("now", now).update();
    }

    private void insertOutbox(UUID aggregateId, String eventType, String partitionKey, Instant now, String state) {
        jdbc.sql("""
                INSERT INTO outbox_event
                    (id, aggregate_type, aggregate_id, event_type, event_version, partition_key,
                     payload, occurred_at, publish_attempts)
                VALUES (UUID_TO_BIN(:id, 1), 'PAYMENT', UUID_TO_BIN(:aggregateId, 1), :eventType, 1,
                        :partitionKey, JSON_OBJECT('paymentId', :paymentId, 'state', :state), :now, 0)
                """)
                .param("id", UUID.randomUUID().toString()).param("aggregateId", aggregateId.toString())
                .param("eventType", eventType).param("partitionKey", partitionKey)
                .param("paymentId", aggregateId.toString()).param("state", state).param("now", now).update();
    }

    public record Reservation(PaymentView payment, boolean replay) {
    }

    private record ExistingIdempotency(byte[] requestHash, String resourceId) {
    }
}

package com.qpay.payment.infrastructure;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
class OutboxPublisher {
    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;

    OutboxPublisher(JdbcClient jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${qpay.outbox.fixed-delay-ms:1000}")
    @Transactional
    public void publishBatch() {
        List<OutboxRow> rows = jdbc.sql("""
                SELECT BIN_TO_UUID(id, 1) id, BIN_TO_UUID(aggregate_id, 1) aggregate_id,
                       event_type, event_version, partition_key, CAST(payload AS CHAR) payload, occurred_at
                  FROM outbox_event
                 WHERE published_at IS NULL
                   AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP(6))
                 ORDER BY occurred_at LIMIT 50 FOR UPDATE SKIP LOCKED
                """).query((rs, rowNum) -> new OutboxRow(
                        UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("aggregate_id")),
                        rs.getString("event_type"), rs.getInt("event_version"), rs.getString("partition_key"),
                        rs.getString("payload"), rs.getTimestamp("occurred_at").toInstant())).list();

        for (OutboxRow row : rows) {
            try {
                kafka.send("qpay." + row.eventType(), row.partitionKey(), envelope(row)).get(10, TimeUnit.SECONDS);
                jdbc.sql("UPDATE outbox_event SET published_at = CURRENT_TIMESTAMP(6) WHERE id = UUID_TO_BIN(:id, 1)")
                        .param("id", row.id().toString()).update();
            } catch (Exception exception) {
                jdbc.sql("""
                        UPDATE outbox_event SET publish_attempts = publish_attempts + 1,
                               next_attempt_at = TIMESTAMPADD(SECOND, LEAST(300, POW(2, publish_attempts)), CURRENT_TIMESTAMP(6))
                         WHERE id = UUID_TO_BIN(:id, 1)
                        """).param("id", row.id().toString()).update();
            }
        }
    }

    private String envelope(OutboxRow row) {
        return "{\"eventId\":\"" + row.id() + "\",\"eventType\":\"" + row.eventType()
                + "\",\"eventVersion\":" + row.eventVersion() + ",\"occurredAt\":\"" + row.occurredAt()
                + "\",\"producer\":\"payment-service\",\"aggregateId\":\"" + row.aggregateId()
                + "\",\"payload\":" + row.payload() + "}";
    }

    private record OutboxRow(
            UUID id, UUID aggregateId, String eventType, int eventVersion,
            String partitionKey, String payload, Instant occurredAt) {
    }
}


package com.qpay.notification.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document("notification")
public record NotificationDocument(
        @Id String id,
        @Indexed(unique = true) String idempotencyKey,
        @Indexed String recipientId,
        String eventType,
        Map<String, String> safeVariables,
        String status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant deliveredAt,
        String lastErrorCode) {
}


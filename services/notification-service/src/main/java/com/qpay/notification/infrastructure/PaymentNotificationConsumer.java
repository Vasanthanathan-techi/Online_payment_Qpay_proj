package com.qpay.notification.infrastructure;

import com.qpay.notification.application.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
class PaymentNotificationConsumer {
    private final NotificationService notifications;
    PaymentNotificationConsumer(NotificationService notifications) { this.notifications = notifications; }

    @KafkaListener(topics = "qpay.payment.succeeded.v1", groupId = "notification-payment-v1")
    void consume(String json) {
        notifications.paymentSucceeded(
                UUID.fromString(field(json, "eventId")), field(json, "merchantId"),
                field(json, "paymentId"), field(json, "merchantReference"));
    }

    private String field(String json, String name) {
        Matcher match = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);
        if (!match.find()) throw new IllegalArgumentException("missing notification event field: " + name);
        return match.group(1);
    }
}


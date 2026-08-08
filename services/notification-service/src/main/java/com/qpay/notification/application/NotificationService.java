package com.qpay.notification.application;

import com.qpay.notification.delivery.FirebaseDeliveryPort;
import com.qpay.notification.domain.NotificationDocument;
import com.qpay.notification.infrastructure.NotificationRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationRepository repository;
    private final FirebaseDeliveryPort firebase;
    private final SimpMessagingTemplate websocket;

    public NotificationService(NotificationRepository repository, FirebaseDeliveryPort firebase, SimpMessagingTemplate websocket) {
        this.repository = repository;
        this.firebase = firebase;
        this.websocket = websocket;
    }

    public void paymentSucceeded(UUID eventId, String merchantId, String paymentId, String reference) {
        String key = "payment-succeeded:" + eventId;
        Instant now = Instant.now();
        Map<String, String> data = Map.of(
                "paymentId", paymentId, "merchantReference", reference, "status", "SUCCEEDED");
        NotificationDocument existing = repository.findByIdempotencyKey(key).orElse(null);
        if (existing != null && "DELIVERED".equals(existing.status())) return;
        NotificationDocument pending = existing;
        if (pending == null) {
            pending = new NotificationDocument(
                    UUID.randomUUID().toString(), key, merchantId, "PAYMENT_SUCCEEDED", data,
                    "PROCESSING", 0, null, now, null, null);
            try {
                repository.insert(pending);
            } catch (DuplicateKeyException duplicate) {
                pending = repository.findByIdempotencyKey(key).orElseThrow();
                if ("DELIVERED".equals(pending.status())) return;
            }
        }

        if (pending.attemptCount() == 0) {
            websocket.convertAndSend("/topic/merchants/" + merchantId + "/transactions", data);
        }
        FirebaseDeliveryPort.DeliveryResult result = firebase.send(merchantId, "PAYMENT_SUCCEEDED", data);
        repository.save(new NotificationDocument(
                pending.id(), key, merchantId, pending.eventType(), data,
                result.delivered() ? "DELIVERED" : "FAILED",
                pending.attemptCount() + 1, result.delivered() ? null : now.plusSeconds(60), pending.createdAt(),
                result.delivered() ? Instant.now() : null, result.errorCode()));
        if (!result.delivered()) throw new NotificationDeliveryException(result.errorCode());
    }

    public static class NotificationDeliveryException extends RuntimeException {
        public NotificationDeliveryException(String code) { super("Firebase delivery failed: " + code); }
    }
}

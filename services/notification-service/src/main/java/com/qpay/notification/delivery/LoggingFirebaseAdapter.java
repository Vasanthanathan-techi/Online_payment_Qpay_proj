package com.qpay.notification.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "qpay.notification.firebase.mode", havingValue = "logging", matchIfMissing = true)
class LoggingFirebaseAdapter implements FirebaseDeliveryPort {
    private static final Logger log = LoggerFactory.getLogger(LoggingFirebaseAdapter.class);
    public DeliveryResult send(String recipientId, String eventType, Map<String, String> data) {
        log.info("Development Firebase delivery recipient={} eventType={}", recipientId, eventType);
        return new DeliveryResult(true, "logging-adapter", null);
    }
}

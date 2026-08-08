package com.qpay.notification.delivery;

import java.util.Map;

public interface FirebaseDeliveryPort {
    DeliveryResult send(String recipientId, String eventType, Map<String, String> data);
    record DeliveryResult(boolean delivered, String providerMessageId, String errorCode) {}
}


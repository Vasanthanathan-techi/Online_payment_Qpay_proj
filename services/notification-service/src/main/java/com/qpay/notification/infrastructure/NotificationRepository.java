package com.qpay.notification.infrastructure;

import com.qpay.notification.domain.NotificationDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface NotificationRepository extends MongoRepository<NotificationDocument, String> {
    Optional<NotificationDocument> findByIdempotencyKey(String idempotencyKey);
}


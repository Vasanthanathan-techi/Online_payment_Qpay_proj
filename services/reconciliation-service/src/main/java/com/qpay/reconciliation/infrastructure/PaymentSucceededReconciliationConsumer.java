package com.qpay.reconciliation.infrastructure;

import com.qpay.reconciliation.application.ReconciliationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
class PaymentSucceededReconciliationConsumer {
    private static final Pattern STRING_FIELD = Pattern.compile("\\\"%s\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern NUMBER_FIELD = Pattern.compile("\\\"%s\\\"\\s*:\\s*(\\d+)");
    private final ReconciliationService reconciliation;

    PaymentSucceededReconciliationConsumer(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @KafkaListener(topics = "qpay.payment.succeeded.v1", groupId = "reconciliation-payment-v1")
    void consume(String json) {
        reconciliation.recordInternal(
                string(json, "paymentId"),
                string(json, "providerCode"),
                string(json, "providerReference"),
                number(json, "amountMinor"),
                string(json, "currency"),
                "SUCCEEDED",
                Instant.parse(string(json, "occurredAt")));
    }

    private String string(String json, String field) {
        Matcher matcher = Pattern.compile(STRING_FIELD.pattern().formatted(Pattern.quote(field))).matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("missing event field: " + field);
        return matcher.group(1);
    }

    private long number(String json, String field) {
        Matcher matcher = Pattern.compile(NUMBER_FIELD.pattern().formatted(Pattern.quote(field))).matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("missing event field: " + field);
        return Long.parseLong(matcher.group(1));
    }
}

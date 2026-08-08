package com.qpay.wallet.infrastructure;

import com.qpay.wallet.application.WalletService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Currency;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
class PaymentSucceededConsumer {
    private static final Pattern STRING_FIELD = Pattern.compile("\\\"%s\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern NUMBER_FIELD = Pattern.compile("\\\"%s\\\"\\s*:\\s*(\\d+)");
    private final WalletService wallets;

    PaymentSucceededConsumer(WalletService wallets) {
        this.wallets = wallets;
    }

    @KafkaListener(topics = "qpay.payment.succeeded.v1", groupId = "wallet-service-ledger-v1")
    void consume(String json) {
        wallets.postSuccessfulPayment(
                UUID.fromString(string(json, "eventId")),
                UUID.fromString(string(json, "paymentId")),
                UUID.fromString(string(json, "walletId")),
                string(json, "merchantReference"),
                number(json, "amountMinor"),
                number(json, "feeMinor"),
                Currency.getInstance(string(json, "currency")));
    }

    private String string(String json, String field) {
        Matcher matcher = Pattern.compile(STRING_FIELD.pattern().formatted(Pattern.quote(field))).matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("missing event field: " + field);
        }
        return matcher.group(1);
    }

    private long number(String json, String field) {
        Matcher matcher = Pattern.compile(NUMBER_FIELD.pattern().formatted(Pattern.quote(field))).matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("missing event field: " + field);
        }
        return Long.parseLong(matcher.group(1));
    }
}

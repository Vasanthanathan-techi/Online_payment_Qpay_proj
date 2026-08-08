package com.qpay.payment.application;

import java.util.Currency;
import java.util.UUID;

public record CreatePaymentCommand(
        UUID merchantId,
        UUID walletId,
        String merchantReference,
        long amountMinor,
        Currency currency,
        String paymentMethodType,
        String returnUrl,
        String idempotencyKey) {
}


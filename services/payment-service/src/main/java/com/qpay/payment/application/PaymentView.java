package com.qpay.payment.application;

import com.qpay.payment.domain.PaymentStatus;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

public record PaymentView(
        UUID id,
        UUID merchantId,
        UUID walletId,
        String merchantReference,
        long amountMinor,
        long feeMinor,
        Currency currency,
        PaymentStatus status,
        String providerReference,
        String nextActionUrl,
        Instant createdAt,
        Instant updatedAt) {
}


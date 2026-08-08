package com.qpay.payment.provider;

import java.util.Currency;
import java.util.UUID;

public interface PaymentProvider {
    ProviderPayment createPayment(ProviderRequest request);

    record ProviderRequest(
            UUID paymentId,
            String merchantReference,
            long amountMinor,
            Currency currency,
            String paymentMethodType,
            String returnUrl) {
    }

    record ProviderPayment(String providerCode, String providerReference, String nextActionUrl) {
    }
}


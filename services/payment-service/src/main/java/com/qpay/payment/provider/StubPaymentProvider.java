package com.qpay.payment.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "qpay.payment.provider", havingValue = "stub", matchIfMissing = true)
class StubPaymentProvider implements PaymentProvider {
    @Override
    public ProviderPayment createPayment(ProviderRequest request) {
        String reference = "stub-" + request.paymentId();
        return new ProviderPayment(
                "STUB", reference, "http://localhost:8085/stub/checkout/" + request.paymentId());
    }
}


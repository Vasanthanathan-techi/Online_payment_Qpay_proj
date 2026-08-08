package com.qpay.payment.api;

import com.qpay.payment.application.CreatePaymentCommand;
import com.qpay.payment.application.PaymentApplicationService;
import com.qpay.payment.application.PaymentView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Currency;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentApplicationService payments;

    public PaymentController(PaymentApplicationService payments) {
        this.payments = payments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PaymentView create(
            @RequestHeader("X-Merchant-Id") UUID merchantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        return payments.createPayment(new CreatePaymentCommand(
                merchantId, request.walletId(), request.merchantReference(), request.amount().amountMinor(),
                Currency.getInstance(request.amount().currency()), request.paymentMethod().type(),
                request.returnUrl(), idempotencyKey));
    }

    @GetMapping("/{paymentId}")
    PaymentView get(@PathVariable UUID paymentId) {
        return payments.getPayment(paymentId);
    }

    record CreatePaymentRequest(
            @NotNull UUID walletId,
            @NotBlank @Size(max = 100) String merchantReference,
            @NotNull @Valid MoneyRequest amount,
            @NotNull @Valid PaymentMethodRequest paymentMethod,
            @NotBlank @Size(max = 2048) String returnUrl) {
    }

    record MoneyRequest(@Positive long amountMinor, @NotBlank @Size(min = 3, max = 3) String currency) {
    }

    record PaymentMethodRequest(@NotBlank @Size(max = 32) String type) {
    }
}

@RestController
@RequestMapping("/api/v1/webhooks/providers/payments")
class ProviderWebhookController {
    private final PaymentApplicationService payments;

    ProviderWebhookController(PaymentApplicationService payments) {
        this.payments = payments;
    }

    @PostMapping("/{providerCode}")
    PaymentView receive(
            @PathVariable String providerCode,
            @RequestHeader("X-Provider-Event-Id") String eventId,
            @RequestHeader("X-Provider-Reference") String providerReference,
            @RequestHeader("X-Provider-Status") String providerStatus,
            @RequestHeader("X-Provider-Timestamp") long timestamp,
            @RequestHeader("X-Provider-Signature") String signature,
            @RequestBody String rawBody) {
        if (!"SUCCEEDED".equalsIgnoreCase(providerStatus)) {
            throw new IllegalArgumentException("this webhook route currently accepts only SUCCEEDED events");
        }
        return payments.processSuccessfulWebhook(
                providerCode, eventId, providerReference, timestamp, signature, rawBody);
    }
}

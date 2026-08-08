package com.qpay.payment.application;

import com.qpay.payment.infrastructure.PaymentJdbcRepository;
import com.qpay.payment.provider.PaymentProvider;
import com.qpay.payment.webhook.WebhookSignatureVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@Service
public class PaymentApplicationService {
    private final PaymentJdbcRepository repository;
    private final PaymentProvider provider;
    private final WebhookSignatureVerifier signatureVerifier;
    private final TransactionTemplate transactions;
    private final int feeBasisPoints;

    public PaymentApplicationService(
            PaymentJdbcRepository repository,
            PaymentProvider provider,
            WebhookSignatureVerifier signatureVerifier,
            TransactionTemplate transactions,
            @Value("${qpay.payment.fee-basis-points:200}") int feeBasisPoints) {
        this.repository = repository;
        this.provider = provider;
        this.signatureVerifier = signatureVerifier;
        this.transactions = transactions;
        this.feeBasisPoints = feeBasisPoints;
    }

    public PaymentView createPayment(CreatePaymentCommand command) {
        validate(command);
        byte[] requestHash = sha256(canonicalRequest(command));
        long feeMinor = calculateFee(command.amountMinor());
        PaymentJdbcRepository.Reservation reservation = transactions.execute(
                status -> repository.reserve(command, requestHash, feeMinor));
        if (reservation == null || reservation.replay()) {
            return reservation == null ? null : reservation.payment();
        }

        transactions.executeWithoutResult(status -> repository.markProcessing(reservation.payment().id()));
        PaymentProvider.ProviderPayment providerPayment = provider.createPayment(new PaymentProvider.ProviderRequest(
                reservation.payment().id(), command.merchantReference(), command.amountMinor(),
                command.currency(), command.paymentMethodType(), command.returnUrl()));
        return transactions.execute(status -> repository.providerCreated(
                reservation.payment().id(), providerPayment.providerCode(),
                providerPayment.providerReference(), providerPayment.nextActionUrl()));
    }

    public PaymentView getPayment(UUID paymentId) {
        return repository.find(paymentId).orElseThrow(() -> new PaymentException("payment not found"));
    }

    public PaymentView processSuccessfulWebhook(
            String providerCode,
            String eventId,
            String providerReference,
            long timestamp,
            String signature,
            String rawBody) {
        if (!signatureVerifier.isValid(rawBody, timestamp, signature)) {
            throw new SecurityException("invalid or expired webhook signature");
        }
        byte[] payloadHash = sha256(rawBody);
        return transactions.execute(status -> repository.succeedWebhook(
                providerCode.toUpperCase(java.util.Locale.ROOT), eventId, providerReference, payloadHash));
    }

    private void validate(CreatePaymentCommand command) {
        if (command.merchantId() == null || command.walletId() == null || command.currency() == null) {
            throw new PaymentException("merchantId, walletId, and currency are required");
        }
        if (command.amountMinor() <= 0 || command.merchantReference() == null || command.merchantReference().isBlank()) {
            throw new PaymentException("a positive amount and merchantReference are required");
        }
        if (command.idempotencyKey() == null || command.idempotencyKey().length() < 8) {
            throw new PaymentException("Idempotency-Key must contain at least 8 characters");
        }
    }

    private long calculateFee(long amountMinor) {
        return BigInteger.valueOf(amountMinor).multiply(BigInteger.valueOf(feeBasisPoints))
                .divide(BigInteger.valueOf(10_000)).longValueExact();
    }

    private String canonicalRequest(CreatePaymentCommand command) {
        return String.join("\u001f", command.merchantId().toString(), command.walletId().toString(),
                command.merchantReference(), Long.toString(command.amountMinor()),
                command.currency().getCurrencyCode(), command.paymentMethodType(), command.returnUrl());
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

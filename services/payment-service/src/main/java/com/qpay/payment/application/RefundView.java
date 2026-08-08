package com.qpay.payment.application;
import com.qpay.payment.domain.RefundStatus;import java.time.Instant;import java.util.Currency;import java.util.UUID;
public record RefundView(UUID id,UUID paymentId,UUID merchantId,UUID walletId,String merchantReference,long amountMinor,Currency currency,RefundStatus status,String providerReference,String failureCode,Instant createdAt,Instant updatedAt){}

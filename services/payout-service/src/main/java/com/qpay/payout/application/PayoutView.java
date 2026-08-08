package com.qpay.payout.application;
import com.qpay.payout.domain.PayoutStatus;import java.time.Instant;import java.util.Currency;import java.util.UUID;
public record PayoutView(UUID id,UUID merchantId,UUID walletId,UUID beneficiaryId,String merchantReference,long amountMinor,long feeMinor,Currency currency,PayoutStatus status,UUID holdId,String providerReference,String failureCode,Instant createdAt,Instant updatedAt){}

package com.qpay.payment.application;
import com.qpay.payment.domain.UtilityPaymentStatus;import java.time.Instant;import java.util.Currency;import java.util.UUID;
public record UtilityPaymentView(UUID id,UUID merchantId,UUID walletId,String merchantReference,String billerCode,String consumerReference,long amountMinor,long feeMinor,Currency currency,UtilityPaymentStatus status,UUID holdId,String providerReference,String failureCode,Instant createdAt,Instant updatedAt){}

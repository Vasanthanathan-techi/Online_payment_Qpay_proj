package com.qpay.payment.provider;
import java.util.Currency;import java.util.UUID;
public interface RefundProvider {Result submit(Request request);record Request(UUID refundId,String originalProviderReference,long amountMinor,Currency currency){}record Result(String providerCode,String providerReference,boolean accepted,String failureCode){} }

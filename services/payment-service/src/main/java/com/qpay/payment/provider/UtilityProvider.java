package com.qpay.payment.provider;
import java.util.Currency;import java.util.UUID;
public interface UtilityProvider {Result submit(Request request);record Request(UUID utilityId,String billerCode,String consumerReference,long amountMinor,Currency currency){}record Result(String providerReference,boolean succeeded,String failureCode){} }

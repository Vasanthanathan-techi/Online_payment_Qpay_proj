package com.qpay.payout.provider;
import java.util.Currency;import java.util.UUID;
public interface BankProvider { Result submit(Request request);record Request(UUID payoutId,long amountMinor,Currency currency,String beneficiaryReference){} record Result(String providerCode,String providerReference,boolean succeeded,String failureCode){} }

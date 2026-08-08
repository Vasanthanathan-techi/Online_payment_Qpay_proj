package com.qpay.reconciliation.domain;
import java.util.Optional;
public final class TransactionMatcher {
 private TransactionMatcher(){}
 public static MatchStatus match(ProviderRecord provider,Optional<InternalRecord> internal){if(internal.isEmpty())return MatchStatus.MISSING_INTERNAL;var value=internal.get();if(!provider.currency().equals(value.currency()))return MatchStatus.CURRENCY_MISMATCH;if(provider.amountMinor()!=value.amountMinor())return MatchStatus.AMOUNT_MISMATCH;if(!normalize(provider.status()).equals(normalize(value.status())))return MatchStatus.STATUS_MISMATCH;return MatchStatus.MATCHED;}
 private static String normalize(String status){return switch(status.toUpperCase()){case "SUCCESS","COMPLETED","CAPTURED"->"SUCCEEDED";case "DECLINED","ERROR"->"FAILED";default->status.toUpperCase();};}
 public record ProviderRecord(String providerReference,String internalReference,long amountMinor,String currency,String status){}
 public record InternalRecord(String reference,long amountMinor,String currency,String status){}
}

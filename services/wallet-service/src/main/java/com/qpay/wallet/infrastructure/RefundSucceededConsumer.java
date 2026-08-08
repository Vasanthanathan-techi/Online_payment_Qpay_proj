package com.qpay.wallet.infrastructure;
import com.qpay.wallet.application.WalletService;import org.springframework.kafka.annotation.KafkaListener;import org.springframework.stereotype.Component;import java.util.*;import java.util.regex.*;
@Component class RefundSucceededConsumer {
 private final WalletService wallets;RefundSucceededConsumer(WalletService wallets){this.wallets=wallets;}
 @KafkaListener(topics="qpay.refund.succeeded.v1",groupId="wallet-refund-ledger-v1") void consume(String json){wallets.postSuccessfulRefund(UUID.fromString(string(json,"eventId")),UUID.fromString(string(json,"refundId")),UUID.fromString(string(json,"walletId")),number(json,"amountMinor"),Currency.getInstance(string(json,"currency")));}
 private String string(String json,String field){var m=Pattern.compile("\\\""+Pattern.quote(field)+"\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);if(!m.find())throw new IllegalArgumentException("missing refund event field: "+field);return m.group(1);}private long number(String json,String field){var m=Pattern.compile("\\\""+Pattern.quote(field)+"\\\"\\s*:\\s*(\\d+)").matcher(json);if(!m.find())throw new IllegalArgumentException("missing refund event field: "+field);return Long.parseLong(m.group(1));}
}

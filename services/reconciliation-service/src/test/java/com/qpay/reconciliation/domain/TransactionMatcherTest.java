package com.qpay.reconciliation.domain;
import org.junit.jupiter.api.Test;import java.util.Optional;import static org.assertj.core.api.Assertions.assertThat;
class TransactionMatcherTest {
 private final TransactionMatcher.ProviderRecord provider=new TransactionMatcher.ProviderRecord("p1","q1",1000,"INR","COMPLETED");
 @Test void matchesNormalizedSuccess(){assertThat(TransactionMatcher.match(provider,Optional.of(new TransactionMatcher.InternalRecord("q1",1000,"INR","SUCCEEDED")))).isEqualTo(MatchStatus.MATCHED);}
 @Test void detectsAmountMismatch(){assertThat(TransactionMatcher.match(provider,Optional.of(new TransactionMatcher.InternalRecord("q1",999,"INR","SUCCEEDED")))).isEqualTo(MatchStatus.AMOUNT_MISMATCH);}
 @Test void detectsMissingInternal(){assertThat(TransactionMatcher.match(provider,Optional.empty())).isEqualTo(MatchStatus.MISSING_INTERNAL);}
}

package com.qpay.reconciliation.application;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class CsvParserTest {@Test void parsesQuotedCommaAndEscapedQuote(){assertThat(ReconciliationService.parse("p1,\"order,one\",100,INR,\"COMPLETED\" ")).containsExactly("p1","order,one","100","INR","COMPLETED ");}@Test void rejectsUnterminatedQuote(){assertThatIllegalArgumentException().isThrownBy(()->ReconciliationService.parse("p1,\"broken"));}}

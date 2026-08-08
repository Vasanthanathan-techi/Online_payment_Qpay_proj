package com.qpay.wallet.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class LedgerRulesTest {
    @Test
    void acceptsBalancedJournal() {
        var lines = List.of(
                new LedgerLine("PROVIDER_CASH", EntrySide.DEBIT, 1_000),
                new LedgerLine("MERCHANT_PAYABLE", EntrySide.CREDIT, 980),
                new LedgerLine("FEE_REVENUE", EntrySide.CREDIT, 20));

        assertThatNoException().isThrownBy(() -> LedgerRules.requireBalanced(lines));
    }

    @Test
    void rejectsUnbalancedJournal() {
        var lines = List.of(
                new LedgerLine("PROVIDER_CASH", EntrySide.DEBIT, 1_000),
                new LedgerLine("MERCHANT_PAYABLE", EntrySide.CREDIT, 990));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> LedgerRules.requireBalanced(lines))
                .withMessageContaining("not balanced");
    }

    @Test
    void rejectsNonPositiveLine() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LedgerLine("PROVIDER_CASH", EntrySide.DEBIT, 0));
    }
}

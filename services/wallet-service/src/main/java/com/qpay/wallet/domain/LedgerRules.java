package com.qpay.wallet.domain;

import java.util.List;

public final class LedgerRules {
    private LedgerRules() {
    }

    public static void requireBalanced(List<LedgerLine> lines) {
        if (lines == null || lines.size() < 2) {
            throw new IllegalArgumentException("a journal requires at least two lines");
        }
        long debits = sum(lines, EntrySide.DEBIT);
        long credits = sum(lines, EntrySide.CREDIT);
        if (debits != credits) {
            throw new IllegalArgumentException("journal is not balanced: debits=" + debits + ", credits=" + credits);
        }
    }

    private static long sum(List<LedgerLine> lines, EntrySide side) {
        try {
            return lines.stream()
                    .filter(line -> line.side() == side)
                    .mapToLong(LedgerLine::amountMinor)
                    .reduce(0L, Math::addExact);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("journal total exceeds supported range", exception);
        }
    }
}


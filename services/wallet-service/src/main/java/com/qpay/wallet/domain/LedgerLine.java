package com.qpay.wallet.domain;

public record LedgerLine(String accountCode, EntrySide side, long amountMinor) {
    public LedgerLine {
        if (accountCode == null || accountCode.isBlank()) {
            throw new IllegalArgumentException("accountCode is required");
        }
        if (side == null) {
            throw new IllegalArgumentException("side is required");
        }
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be positive");
        }
    }
}


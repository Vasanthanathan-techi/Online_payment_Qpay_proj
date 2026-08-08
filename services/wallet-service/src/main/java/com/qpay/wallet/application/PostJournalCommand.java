package com.qpay.wallet.application;

import com.qpay.wallet.domain.LedgerLine;

import java.util.Currency;
import java.util.List;
import java.util.UUID;

public record PostJournalCommand(
        String journalType,
        String businessReference,
        String operation,
        Currency currency,
        UUID correlationId,
        String description,
        List<LedgerLine> lines) {
}


package com.qpay.wallet.application;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

public record WalletView(
        UUID id,
        String ownerType,
        UUID ownerId,
        Currency currency,
        String status,
        long availableMinor,
        long heldMinor,
        long version,
        Instant updatedAt) {
}


package com.qpay.wallet.application;

import java.time.Instant;
import java.util.UUID;

public record JournalView(UUID id, String businessReference, String operation, String status, Instant postedAt) {
}


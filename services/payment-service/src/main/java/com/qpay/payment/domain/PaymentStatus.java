package com.qpay.payment.domain;

import java.util.EnumSet;
import java.util.Set;

public enum PaymentStatus {
    CREATED,
    PROCESSING,
    REQUIRES_ACTION,
    SUCCEEDED,
    FAILED,
    EXPIRED;

    public boolean canTransitionTo(PaymentStatus target) {
        return allowedTargets().contains(target);
    }

    public void requireTransitionTo(PaymentStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("invalid payment transition: " + this + " -> " + target);
        }
    }

    private Set<PaymentStatus> allowedTargets() {
        return switch (this) {
            case CREATED -> EnumSet.of(PROCESSING, FAILED, EXPIRED);
            case PROCESSING -> EnumSet.of(REQUIRES_ACTION, SUCCEEDED, FAILED);
            case REQUIRES_ACTION -> EnumSet.of(PROCESSING, SUCCEEDED, FAILED, EXPIRED);
            case SUCCEEDED, FAILED, EXPIRED -> EnumSet.noneOf(PaymentStatus.class);
        };
    }
}


package com.qpay.common.model;

import java.util.Currency;

public record Money(long amountMinor, Currency currency) {
    public Money {
        if (currency == null) {
            throw new IllegalArgumentException("currency is required");
        }
    }
}


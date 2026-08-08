package com.qpay.payment.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class PaymentStatusTest {
    @Test
    void permitsExpectedProviderFlow() {
        assertThat(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.PROCESSING)).isTrue();
        assertThat(PaymentStatus.PROCESSING.canTransitionTo(PaymentStatus.REQUIRES_ACTION)).isTrue();
        assertThat(PaymentStatus.REQUIRES_ACTION.canTransitionTo(PaymentStatus.SUCCEEDED)).isTrue();
    }

    @Test
    void preventsTerminalStateRegression() {
        assertThatIllegalStateException().isThrownBy(
                () -> PaymentStatus.SUCCEEDED.requireTransitionTo(PaymentStatus.PROCESSING));
    }
}

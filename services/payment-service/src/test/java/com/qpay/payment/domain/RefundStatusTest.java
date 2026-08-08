package com.qpay.payment.domain;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class RefundStatusTest {@Test void processingCanSucceed(){assertThatNoException().isThrownBy(()->RefundStatus.PROCESSING.requireTransitionTo(RefundStatus.SUCCEEDED));}@Test void successIsTerminal(){assertThatIllegalStateException().isThrownBy(()->RefundStatus.SUCCEEDED.requireTransitionTo(RefundStatus.PROCESSING));}}

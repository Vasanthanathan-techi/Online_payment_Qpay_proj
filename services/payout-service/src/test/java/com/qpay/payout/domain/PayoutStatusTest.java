package com.qpay.payout.domain;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PayoutStatusTest {
 @Test void permitsSagaFlow(){assertThatNoException().isThrownBy(()->PayoutStatus.CREATED.requireTransitionTo(PayoutStatus.FUNDS_HELD));}
 @Test void preventsCompletedRegression(){assertThatIllegalStateException().isThrownBy(()->PayoutStatus.SUCCEEDED.requireTransitionTo(PayoutStatus.PROCESSING));}
}

package com.qpay.user.domain;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class KycStatusTest {@Test void pendingCanVerify(){assertThatNoException().isThrownBy(()->KycStatus.PENDING.requireTransitionTo(KycStatus.VERIFIED));}@Test void verifiedIsTerminal(){assertThatIllegalStateException().isThrownBy(()->KycStatus.VERIFIED.requireTransitionTo(KycStatus.PENDING));}}

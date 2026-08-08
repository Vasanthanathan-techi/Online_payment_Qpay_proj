package com.qpay.merchant.domain;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class OnboardingStatusTest {@Test void approvalIsTerminal(){assertThatIllegalStateException().isThrownBy(()->OnboardingStatus.APPROVED.requireTransitionTo(OnboardingStatus.UNDER_REVIEW));}@Test void reviewCanApprove(){assertThatNoException().isThrownBy(()->OnboardingStatus.UNDER_REVIEW.requireTransitionTo(OnboardingStatus.APPROVED));}}

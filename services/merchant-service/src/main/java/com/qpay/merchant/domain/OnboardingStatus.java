package com.qpay.merchant.domain;
import java.util.EnumSet;
public enum OnboardingStatus {PENDING,UNDER_REVIEW,APPROVED,REJECTED;
 public void requireTransitionTo(OnboardingStatus target){var allowed=switch(this){case PENDING->EnumSet.of(UNDER_REVIEW);case UNDER_REVIEW->EnumSet.of(APPROVED,REJECTED);case REJECTED->EnumSet.of(UNDER_REVIEW);case APPROVED->EnumSet.noneOf(OnboardingStatus.class);};if(!allowed.contains(target))throw new IllegalStateException("invalid KYB transition: "+this+" -> "+target);}}


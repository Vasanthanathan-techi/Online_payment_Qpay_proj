package com.qpay.user.domain;
import java.util.EnumSet;
public enum KycStatus {NOT_STARTED,PENDING,VERIFIED,REJECTED;
 public void requireTransitionTo(KycStatus target){var allowed=switch(this){case NOT_STARTED->EnumSet.of(PENDING);case PENDING->EnumSet.of(VERIFIED,REJECTED);case REJECTED->EnumSet.of(PENDING);case VERIFIED->EnumSet.noneOf(KycStatus.class);};if(!allowed.contains(target))throw new IllegalStateException("invalid KYC transition: "+this+" -> "+target);}}

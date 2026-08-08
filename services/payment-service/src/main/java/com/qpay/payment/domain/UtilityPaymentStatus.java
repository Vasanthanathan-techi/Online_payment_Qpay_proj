package com.qpay.payment.domain;
import java.util.EnumSet;
public enum UtilityPaymentStatus {CREATED,FUNDS_HELD,SUBMITTED,SUCCEEDED,FAILED,FUNDS_RELEASED;
 public void requireTransitionTo(UtilityPaymentStatus target){var allowed=switch(this){case CREATED->EnumSet.of(FUNDS_HELD,FAILED);case FUNDS_HELD->EnumSet.of(SUBMITTED,FAILED);case SUBMITTED->EnumSet.of(SUCCEEDED,FAILED);case FAILED->EnumSet.of(FUNDS_RELEASED);default->EnumSet.noneOf(UtilityPaymentStatus.class);};if(!allowed.contains(target))throw new IllegalStateException("invalid utility-payment transition: "+this+" -> "+target);}}

package com.qpay.payment.domain;
import java.util.EnumSet;
public enum RefundStatus {PENDING,PROCESSING,SUCCEEDED,FAILED;
 public void requireTransitionTo(RefundStatus target){var allowed=switch(this){case PENDING->EnumSet.of(PROCESSING,FAILED);case PROCESSING->EnumSet.of(SUCCEEDED,FAILED);default->EnumSet.noneOf(RefundStatus.class);};if(!allowed.contains(target))throw new IllegalStateException("invalid refund transition: "+this+" -> "+target);}}

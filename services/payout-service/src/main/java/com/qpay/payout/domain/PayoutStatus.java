package com.qpay.payout.domain;
import java.util.EnumSet;
public enum PayoutStatus {
 CREATED, FUNDS_HELD, SUBMITTED, PROCESSING, SUCCEEDED, FAILED, CANCELLED, FUNDS_RELEASED;
 public void requireTransitionTo(PayoutStatus target) {
  var allowed=switch(this){case CREATED->EnumSet.of(FUNDS_HELD,FAILED);case FUNDS_HELD->EnumSet.of(SUBMITTED,CANCELLED,FAILED);case SUBMITTED->EnumSet.of(PROCESSING,SUCCEEDED,FAILED);case PROCESSING->EnumSet.of(SUCCEEDED,FAILED);case FAILED,CANCELLED->EnumSet.of(FUNDS_RELEASED);default->EnumSet.noneOf(PayoutStatus.class);};
  if(!allowed.contains(target)) throw new IllegalStateException("invalid payout transition: "+this+" -> "+target);
 }
}

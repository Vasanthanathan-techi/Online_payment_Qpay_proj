package com.qpay.payment.provider;
import org.springframework.stereotype.Component;
@Component class StubRefundProvider implements RefundProvider {public Result submit(Request r){return new Result("STUB","stub-refund-"+r.refundId(),true,null);}}

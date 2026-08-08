package com.qpay.payment.provider;
import org.springframework.stereotype.Component;
@Component class StubUtilityProvider implements UtilityProvider {public Result submit(Request r){return new Result("stub-bill-"+r.utilityId(),true,null);}}

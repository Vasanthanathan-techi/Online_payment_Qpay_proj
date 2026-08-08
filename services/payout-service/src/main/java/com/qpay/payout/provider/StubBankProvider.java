package com.qpay.payout.provider;
import org.springframework.stereotype.Component;
@Component class StubBankProvider implements BankProvider {public Result submit(Request r){return new Result("STUB_BANK","stub-"+r.payoutId(),true,null);}}

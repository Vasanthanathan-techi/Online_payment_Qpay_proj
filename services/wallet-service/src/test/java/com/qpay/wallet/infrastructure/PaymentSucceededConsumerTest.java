package com.qpay.wallet.infrastructure;

import com.qpay.wallet.application.WalletService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PaymentSucceededConsumerTest {
    @Test
    void malformedEventFailsBeforeAnyLedgerOperation() {
        WalletService wallets = mock(WalletService.class);
        var consumer = new PaymentSucceededConsumer(wallets);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> consumer.consume("{\"eventType\":\"payment.succeeded.v1\"}"))
                .withMessageContaining("missing event field");
        verifyNoInteractions(wallets);
    }
}

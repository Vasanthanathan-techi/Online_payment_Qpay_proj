package com.qpay.payout.application;

import com.qpay.payout.domain.PayoutStatus;
import com.qpay.payout.infrastructure.PayoutRepository;
import com.qpay.payout.provider.BankProvider;
import com.qpay.payout.security.BankDataCrypto;
import com.qpay.payout.wallet.WalletFundsClient;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PayoutCompensationTest {
    @Test
    void definiteBankFailureReleasesHoldAndNeverSettlesIt() {
        var repository = mock(PayoutRepository.class);
        var crypto = new BankDataCrypto("test-secret");
        var wallet = mock(WalletFundsClient.class);
        var bank = mock(BankProvider.class);
        var transactions = immediateTransactions();
        UUID payoutId = UUID.randomUUID(), merchant = UUID.randomUUID(), walletId = UUID.randomUUID();
        UUID beneficiary = UUID.randomUUID(), holdId = UUID.randomUUID();
        var created = view(payoutId, merchant, walletId, beneficiary, PayoutStatus.CREATED, null);
        var released = view(payoutId, merchant, walletId, beneficiary, PayoutStatus.FUNDS_RELEASED, holdId);
        when(repository.createPayout(eq(merchant), eq(walletId), eq(beneficiary), anyString(), anyLong(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(new PayoutRepository.Reservation(created, false));
        when(wallet.hold(eq(walletId), eq(payoutId.toString()), anyLong(), eq("INR"))).thenReturn(holdId);
        when(bank.submit(any())).thenReturn(new BankProvider.Result("BANK", "bank-ref", false, "ACCOUNT_CLOSED"));
        when(repository.find(payoutId)).thenReturn(java.util.Optional.of(released));

        new PayoutService(repository, crypto, wallet, bank, transactions, 100)
                .create(merchant, walletId, beneficiary, "payout-ref", 10_000, Currency.getInstance("INR"), "idem-12345678");

        verify(wallet).release(holdId);
        verify(wallet, never()).settle(any(), any(), anyLong(), anyLong(), anyString());
        verify(repository).transition(payoutId, PayoutStatus.FAILED, PayoutStatus.FUNDS_RELEASED,
                holdId, null, null, "ACCOUNT_CLOSED");
    }

    private TransactionTemplate immediateTransactions() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            public TransactionStatus getTransaction(TransactionDefinition definition) { return new SimpleTransactionStatus(); }
            public void commit(TransactionStatus status) { }
            public void rollback(TransactionStatus status) { }
        });
    }

    private PayoutView view(UUID id, UUID merchant, UUID wallet, UUID beneficiary, PayoutStatus status, UUID hold) {
        return new PayoutView(id, merchant, wallet, beneficiary, "payout-ref", 10_000, 100,
                Currency.getInstance("INR"), status, hold, null, null, Instant.now(), Instant.now());
    }
}

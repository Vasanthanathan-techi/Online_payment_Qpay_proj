package com.qpay.wallet.application;

import com.qpay.wallet.domain.LedgerRules;
import com.qpay.wallet.infrastructure.LedgerJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.List;
import java.util.UUID;
import com.qpay.wallet.domain.EntrySide;
import com.qpay.wallet.domain.LedgerLine;

@Service
public class WalletService {
    private final LedgerJdbcRepository repository;

    public WalletService(LedgerJdbcRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public WalletView createWallet(String ownerType, UUID ownerId, Currency currency) {
        if (ownerType == null || ownerType.isBlank()) {
            throw new IllegalArgumentException("ownerType is required");
        }
        return repository.createWallet(ownerType, ownerId, currency);
    }

    @Transactional(readOnly = true)
    public WalletView getWallet(UUID walletId) {
        return repository.findWallet(walletId).orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    @Transactional
    public JournalView postJournal(PostJournalCommand command) {
        if (command.currency() == null || command.correlationId() == null) {
            throw new IllegalArgumentException("currency and correlationId are required");
        }
        LedgerRules.requireBalanced(command.lines());
        return repository.postJournal(command);
    }

    @Transactional
    public void postSuccessfulPayment(
            UUID eventId, UUID paymentId, UUID walletId, String merchantReference,
            long amountMinor, long feeMinor, Currency currency) {
        if (!repository.claimEvent("payment-succeeded-ledger-v1", eventId, java.time.Instant.now())) {
            return;
        }
        long merchantNet = Math.subtractExact(amountMinor, feeMinor);
        List<LedgerLine> lines = new java.util.ArrayList<>();
        lines.add(new LedgerLine("PROVIDER_CASH", EntrySide.DEBIT, amountMinor));
        lines.add(new LedgerLine("WALLET:" + walletId, EntrySide.CREDIT, merchantNet));
        if (feeMinor > 0) {
            lines.add(new LedgerLine("FEE_REVENUE", EntrySide.CREDIT, feeMinor));
        }
        postJournal(new PostJournalCommand(
                "PAYMENT", paymentId.toString(), "PAYMENT_CAPTURE", currency,
                eventId, "Pay-In settlement for " + merchantReference, lines));
    }

    @Transactional
    public void postSuccessfulRefund(UUID eventId,UUID refundId,UUID walletId,long amountMinor,Currency currency){
        if(!repository.claimEvent("refund-succeeded-ledger-v1",eventId,java.time.Instant.now()))return;
        postJournal(new PostJournalCommand("REFUND",refundId.toString(),"REFUND_SETTLEMENT",currency,eventId,"Payment refund",List.of(
                new LedgerLine("WALLET:"+walletId,EntrySide.DEBIT,amountMinor),
                new LedgerLine("PROVIDER_CASH",EntrySide.CREDIT,amountMinor))));
    }

    @Transactional
    public UUID holdFunds(UUID walletId,String reference,long amountMinor,Currency currency){
        if(amountMinor<=0) throw new IllegalArgumentException("hold amount must be positive");
        return repository.placeHold(walletId,reference,amountMinor,currency.getCurrencyCode(),java.time.Instant.now());
    }

    @Transactional
    public void releaseHold(UUID holdId){repository.restoreHoldToAvailable(repository.lockHold(holdId),"RELEASED",java.time.Instant.now());}

    @Transactional
    public JournalView settlePayout(UUID holdId,UUID payoutId,long payoutMinor,long feeMinor,Currency currency){
        var hold=repository.lockHold(holdId);
        if(hold.amountMinor()!=Math.addExact(payoutMinor,feeMinor)) throw new LedgerPostingException("hold amount does not match payout total");
        repository.restoreHoldToAvailable(hold,"CAPTURED",java.time.Instant.now());
        var lines=new java.util.ArrayList<LedgerLine>();
        lines.add(new LedgerLine("WALLET:"+hold.walletId(),EntrySide.DEBIT,hold.amountMinor()));
        lines.add(new LedgerLine("PAYOUT_CLEARING",EntrySide.CREDIT,payoutMinor));
        if(feeMinor>0) lines.add(new LedgerLine("FEE_REVENUE",EntrySide.CREDIT,feeMinor));
        return postJournal(new PostJournalCommand("PAYOUT",payoutId.toString(),"PAYOUT_SETTLEMENT",currency,payoutId,"Payout settlement",lines));
    }

    @Transactional
    public JournalView settleUtility(UUID holdId,UUID utilityId,long billMinor,long feeMinor,Currency currency){
        var hold=repository.lockHold(holdId);if(hold.amountMinor()!=Math.addExact(billMinor,feeMinor))throw new LedgerPostingException("hold amount does not match utility total");repository.restoreHoldToAvailable(hold,"CAPTURED",java.time.Instant.now());var lines=new java.util.ArrayList<LedgerLine>();lines.add(new LedgerLine("WALLET:"+hold.walletId(),EntrySide.DEBIT,hold.amountMinor()));lines.add(new LedgerLine("BILLER_CLEARING",EntrySide.CREDIT,billMinor));if(feeMinor>0)lines.add(new LedgerLine("FEE_REVENUE",EntrySide.CREDIT,feeMinor));return postJournal(new PostJournalCommand("UTILITY_PAYMENT",utilityId.toString(),"UTILITY_SETTLEMENT",currency,utilityId,"Utility payment settlement",lines));
    }
}

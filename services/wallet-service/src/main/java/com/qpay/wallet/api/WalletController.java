package com.qpay.wallet.api;

import com.qpay.wallet.application.JournalView;
import com.qpay.wallet.application.PostJournalCommand;
import com.qpay.wallet.application.WalletService;
import com.qpay.wallet.application.WalletView;
import com.qpay.wallet.domain.EntrySide;
import com.qpay.wallet.domain.LedgerLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Currency;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {
    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WalletView create(@Valid @RequestBody CreateWalletRequest request) {
        return walletService.createWallet(
                request.ownerType(), request.ownerId(), Currency.getInstance(request.currency()));
    }

    @GetMapping("/{walletId}")
    WalletView get(@PathVariable UUID walletId) {
        return walletService.getWallet(walletId);
    }

    public record CreateWalletRequest(
            @NotBlank @Size(max = 24) String ownerType,
            @NotNull UUID ownerId,
            @NotBlank @Size(min = 3, max = 3) String currency) {
    }
}

@RestController
@RequestMapping("/internal/v1/ledger")
class LedgerController {
    private final WalletService walletService;

    LedgerController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/journals")
    @ResponseStatus(HttpStatus.CREATED)
    JournalView post(@Valid @RequestBody PostJournalRequest request) {
        List<LedgerLine> lines = request.lines().stream()
                .map(line -> new LedgerLine(line.accountCode(), line.side(), line.amountMinor()))
                .toList();
        return walletService.postJournal(new PostJournalCommand(
                request.journalType(), request.businessReference(), request.operation(),
                Currency.getInstance(request.currency()), request.correlationId(), request.description(), lines));
    }

    record PostJournalRequest(
            @NotBlank @Size(max = 40) String journalType,
            @NotBlank @Size(max = 100) String businessReference,
            @NotBlank @Size(max = 40) String operation,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotNull UUID correlationId,
            @Size(max = 500) String description,
            @NotEmpty List<@Valid LineRequest> lines) {
    }

    record LineRequest(
            @NotBlank @Size(max = 80) String accountCode,
            @NotNull EntrySide side,
            @Positive long amountMinor) {
    }
}


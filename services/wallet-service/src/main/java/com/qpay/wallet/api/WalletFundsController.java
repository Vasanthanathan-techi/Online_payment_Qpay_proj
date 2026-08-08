package com.qpay.wallet.api;
import com.qpay.wallet.application.WalletService;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;
import java.util.Currency;import java.util.UUID;
@RestController @RequestMapping("/internal/v1/wallet-funds")
class WalletFundsController {
 private final WalletService service; WalletFundsController(WalletService service){this.service=service;}
 @PostMapping("/holds") @ResponseStatus(HttpStatus.CREATED)
 HoldResponse hold(@Valid @RequestBody HoldRequest r){return new HoldResponse(service.holdFunds(r.walletId(),r.reference(),r.amountMinor(),Currency.getInstance(r.currency())));}
 @PostMapping("/holds/{id}/release") @ResponseStatus(HttpStatus.NO_CONTENT) void release(@PathVariable UUID id){service.releaseHold(id);}
 @PostMapping("/holds/{id}/settle") Object settle(@PathVariable UUID id,@Valid @RequestBody SettleRequest r){return service.settlePayout(id,r.payoutId(),r.payoutMinor(),r.feeMinor(),Currency.getInstance(r.currency()));}
 @PostMapping("/holds/{id}/settle-utility") Object settleUtility(@PathVariable UUID id,@Valid @RequestBody UtilitySettleRequest r){return service.settleUtility(id,r.utilityId(),r.amountMinor(),r.feeMinor(),Currency.getInstance(r.currency()));}
 record HoldRequest(@NotNull UUID walletId,@NotBlank String reference,@Positive long amountMinor,@Size(min=3,max=3) String currency){}
 record HoldResponse(UUID holdId){}
 record SettleRequest(@NotNull UUID payoutId,@Positive long payoutMinor,@PositiveOrZero long feeMinor,@Size(min=3,max=3) String currency){}
 record UtilitySettleRequest(@NotNull UUID utilityId,@Positive long amountMinor,@PositiveOrZero long feeMinor,@Size(min=3,max=3) String currency){}
}

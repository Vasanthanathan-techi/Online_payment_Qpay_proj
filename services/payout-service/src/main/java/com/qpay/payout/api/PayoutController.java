package com.qpay.payout.api;
import com.qpay.payout.application.*;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController public class PayoutController {
 private final PayoutService service;public PayoutController(PayoutService service){this.service=service;}
 @PostMapping("/api/v1/beneficiaries") @ResponseStatus(HttpStatus.CREATED) BeneficiaryResponse beneficiary(@RequestHeader("X-Merchant-Id") UUID merchant,@Valid @RequestBody BeneficiaryRequest r){return new BeneficiaryResponse(service.createBeneficiary(merchant,r.merchantReference(),r.name(),r.bankCode(),r.accountNumber()));}
 @PostMapping("/api/v1/payouts") @ResponseStatus(HttpStatus.ACCEPTED) PayoutView payout(@RequestHeader("X-Merchant-Id") UUID merchant,@RequestHeader("Idempotency-Key") String idem,@Valid @RequestBody PayoutRequest r){return service.create(merchant,r.walletId(),r.beneficiaryId(),r.merchantReference(),r.amount().amountMinor(),Currency.getInstance(r.amount().currency()),idem);}
 @GetMapping("/api/v1/payouts/{id}") PayoutView get(@PathVariable UUID id){return service.get(id);}
 record BeneficiaryRequest(@NotBlank String merchantReference,@NotBlank String name,@NotBlank String bankCode,@NotBlank String accountNumber){}record BeneficiaryResponse(UUID id){}
 record PayoutRequest(@NotBlank String merchantReference,@NotNull UUID walletId,@NotNull UUID beneficiaryId,@Valid @NotNull Money amount){}record Money(@Positive long amountMinor,@Size(min=3,max=3) String currency){}
}

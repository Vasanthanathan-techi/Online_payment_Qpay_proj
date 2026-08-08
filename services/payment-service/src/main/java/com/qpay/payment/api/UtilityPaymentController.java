package com.qpay.payment.api;
import com.qpay.payment.application.*;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/v1/utility-payments") class UtilityPaymentController {
 private final UtilityPaymentService service;UtilityPaymentController(UtilityPaymentService service){this.service=service;}
 @PostMapping @ResponseStatus(HttpStatus.ACCEPTED) UtilityPaymentView create(@RequestHeader("X-Merchant-Id")UUID merchant,@RequestHeader("Idempotency-Key")String idempotencyKey,@Valid @RequestBody Request r){if(idempotencyKey.length()<8)throw new IllegalArgumentException("Idempotency-Key must contain at least 8 characters");return service.create(merchant,r.walletId(),r.merchantReference(),r.billerCode(),r.consumerReference(),r.amount().amountMinor(),Currency.getInstance(r.amount().currency()));}
 @GetMapping("/{id}") UtilityPaymentView get(@PathVariable UUID id){return service.get(id);}
 record Request(@NotNull UUID walletId,@NotBlank @Size(max=100)String merchantReference,@NotBlank @Size(max=40)String billerCode,@NotBlank @Size(max=190)String consumerReference,@Valid @NotNull Money amount){}record Money(@Positive long amountMinor,@Size(min=3,max=3)String currency){}
}

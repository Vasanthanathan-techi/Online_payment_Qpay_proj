package com.qpay.payment.api;
import com.qpay.payment.application.*;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import java.util.UUID;
@RestController @RequestMapping("/api/v1") class RefundController {
 private final RefundService service;RefundController(RefundService service){this.service=service;}
 @PostMapping("/payments/{paymentId}/refunds") @ResponseStatus(HttpStatus.ACCEPTED) RefundView create(@PathVariable UUID paymentId,@RequestHeader("X-Merchant-Id") UUID merchant,@Valid @RequestBody Request r){return service.create(merchant,paymentId,r.merchantReference(),r.amount().amountMinor());}
 @GetMapping("/refunds/{id}") RefundView get(@PathVariable UUID id){return service.get(id);}
 record Request(@NotBlank @Size(max=100) String merchantReference,@NotNull @Valid Money amount){}record Money(@Positive long amountMinor,@Size(min=3,max=3) String currency){}
}

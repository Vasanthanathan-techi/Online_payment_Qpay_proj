package com.qpay.merchant.api;
import com.qpay.merchant.application.*;import com.qpay.merchant.domain.OnboardingStatus;import com.qpay.merchant.infrastructure.MerchantRepository;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.http.*;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;import java.time.Instant;import java.util.*;
@RestController @RequestMapping("/api/v1/merchants") public class MerchantController {
 private final MerchantService service;public MerchantController(MerchantService service){this.service=service;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) MerchantView create(@Valid @RequestBody CreateMerchant r){return service.create(r.legalName(),r.displayName(),r.registrationNumber(),r.countryCode(),r.defaultCurrency());}
 @GetMapping("/{id}") @PreAuthorize("@merchantAccess.canAccess(authentication,#id)") MerchantView get(@PathVariable UUID id){return service.get(id);}
 @PostMapping("/{id}/kyb/{status}") @PreAuthorize("hasAnyRole('OPS','SUPER_ADMIN')") MerchantView transition(@PathVariable UUID id,@PathVariable OnboardingStatus status){return service.transition(id,status);}
 @PostMapping("/{id}/api-keys") @PreAuthorize("@merchantAccess.canAccess(authentication,#id)") @ResponseStatus(HttpStatus.CREATED) MerchantRepository.ApiCredential key(@PathVariable UUID id,@Valid @RequestBody KeyRequest r){return service.createKey(id,r.scopes(),r.expiresAt());}
 @DeleteMapping("/{id}/api-keys/{keyId}") @PreAuthorize("@merchantAccess.canAccess(authentication,#id)") @ResponseStatus(HttpStatus.NO_CONTENT) void revoke(@PathVariable UUID id,@PathVariable UUID keyId){service.revokeKey(id,keyId);}
 @PutMapping("/{id}/webhook") @PreAuthorize("@merchantAccess.canAccess(authentication,#id)") MerchantService.WebhookSecret webhook(@PathVariable UUID id,@Valid @RequestBody WebhookRequest r){return service.configureWebhook(id,r.url());}
 record CreateMerchant(@NotBlank String legalName,@NotBlank String displayName,@NotBlank String registrationNumber,@Size(min=2,max=2) String countryCode,@Size(min=3,max=3) String defaultCurrency){}
 record KeyRequest(@NotEmpty List<@NotBlank String> scopes,Instant expiresAt){}record WebhookRequest(@NotBlank @Size(max=2048) String url){}
}

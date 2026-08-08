package com.qpay.merchant.application;
import com.qpay.merchant.domain.OnboardingStatus;import java.time.Instant;import java.util.Currency;import java.util.UUID;
public record MerchantView(UUID id,String legalName,String displayName,String registrationNumber,String countryCode,String status,OnboardingStatus kybStatus,Currency defaultCurrency,String webhookUrl,long version,Instant createdAt,Instant updatedAt){}

package com.qpay.user.application;
import com.qpay.user.domain.KycStatus;import java.time.Instant;import java.util.UUID;
public record UserView(UUID id,String status,String displayName,String email,String phone,KycStatus kycStatus,String locale,long version,Instant createdAt,Instant updatedAt){}

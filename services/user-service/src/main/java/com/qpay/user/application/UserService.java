package com.qpay.user.application;
import com.qpay.user.domain.KycStatus;import com.qpay.user.infrastructure.UserRepository;import com.qpay.user.security.DeviceTokenProtector;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.util.*;
@Service public class UserService {
 private final UserRepository repo;private final DeviceTokenProtector tokens;public UserService(UserRepository repo,DeviceTokenProtector tokens){this.repo=repo;this.tokens=tokens;}
 @Transactional public UserView create(UUID id,String name,String email,String phone,String locale){return repo.create(id,name,normalizeEmail(email),phone,locale);}
 @Transactional(readOnly=true) public UserView get(UUID id){return repo.find(id).orElseThrow(()->new IllegalArgumentException("user not found"));}
 @Transactional public UserView update(UUID id,String name,String email,String phone,String locale,long version){return repo.update(id,name,normalizeEmail(email),phone,locale,version);}
 @Transactional public UserView kyc(UUID id,KycStatus status,String reference){if(status==KycStatus.VERIFIED&&(reference==null||reference.isBlank()))throw new IllegalArgumentException("verified KYC requires a provider reference");return repo.transitionKyc(id,status,reference);}
 @Transactional public UserRepository.DeviceRegistration register(UUID user,String platform,String raw){if(raw==null||raw.length()<16)throw new IllegalArgumentException("invalid device token");return repo.registerDevice(user,platform.toUpperCase(Locale.ROOT),tokens.protect(raw));}
 @Transactional public void revoke(UUID user,UUID device){repo.revokeDevice(user,device);}
 @Transactional public void preference(UUID user,String event,boolean push,boolean email,boolean sms){repo.preference(user,event,push,email,sms);}
 private String normalizeEmail(String email){return email==null?null:email.trim().toLowerCase(Locale.ROOT);}
}

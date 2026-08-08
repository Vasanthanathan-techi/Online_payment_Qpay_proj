package com.qpay.merchant.application;
import com.qpay.merchant.domain.OnboardingStatus;import com.qpay.merchant.infrastructure.MerchantRepository;import com.qpay.merchant.security.SecretProtector;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.time.Instant;import java.util.*;
@Service public class MerchantService {
 private static final Set<String> ALLOWED_SCOPES=Set.of("payments:read","payments:write","payouts:read","payouts:write","wallets:read");private final MerchantRepository repo;private final SecretProtector secrets;public MerchantService(MerchantRepository repo,SecretProtector secrets){this.repo=repo;this.secrets=secrets;}
 @Transactional public MerchantView create(String legal,String display,String registration,String country,String currency){return repo.create(legal,display,registration,country.toUpperCase(Locale.ROOT),currency.toUpperCase(Locale.ROOT));}
 @Transactional(readOnly=true) public MerchantView get(UUID id){return repo.find(id).orElseThrow(()->new IllegalArgumentException("merchant not found"));}
 @Transactional public MerchantView transition(UUID id,OnboardingStatus target){return repo.transition(id,target);}
 @Transactional public MerchantRepository.ApiCredential createKey(UUID merchant,List<String> scopes,Instant expiry){if(scopes==null||scopes.isEmpty()||!ALLOWED_SCOPES.containsAll(scopes))throw new IllegalArgumentException("one or more API scopes are invalid");return repo.createKey(merchant,scopes.stream().distinct().sorted().toList(),expiry,secrets);}
 @Transactional public void revokeKey(UUID merchant,UUID key){repo.revokeKey(merchant,key);}
 @Transactional public WebhookSecret configureWebhook(UUID merchant,String url){String raw=secrets.randomToken(32);repo.configureWebhook(merchant,url,secrets.encrypt(raw));return new WebhookSecret(raw);}
 public record WebhookSecret(String signingSecret){}
}

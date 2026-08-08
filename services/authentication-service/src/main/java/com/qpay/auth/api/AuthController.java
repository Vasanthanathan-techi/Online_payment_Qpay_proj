package com.qpay.auth.api;
import com.nimbusds.jose.jwk.RSAKey;import com.qpay.auth.application.AuthService;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.web.bind.annotation.*;import java.util.Map;
@RestController public class AuthController {
 private final AuthService auth;private final RSAKey key;private final String issuer;public AuthController(AuthService auth,RSAKey key,@Value("${qpay.jwt.issuer:http://localhost:8081}")String issuer){this.auth=auth;this.key=key;this.issuer=issuer;}
 @PostMapping("/api/v1/auth/login") AuthService.Tokens login(@Valid @RequestBody Login r){return auth.login(r.username(),r.password());}
 @PostMapping("/api/v1/auth/refresh") AuthService.Tokens refresh(@Valid @RequestBody Refresh r){return auth.refresh(r.refreshToken());}
 @GetMapping("/.well-known/openid-configuration") Map<String,Object> discovery(){return Map.of("issuer",issuer,"jwks_uri",issuer+"/.well-known/jwks.json","id_token_signing_alg_values_supported",new String[]{"RS256"});}
 @GetMapping("/.well-known/jwks.json") Map<String,Object> jwks(){return Map.of("keys",new Object[]{key.toPublicJWK().toJSONObject()});}
 record Login(@NotBlank String username,@NotBlank @Size(min=8) String password){}record Refresh(@NotBlank String refreshToken){}
}

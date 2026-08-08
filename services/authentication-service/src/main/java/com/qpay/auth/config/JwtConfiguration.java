package com.qpay.auth.config;
import com.nimbusds.jose.jwk.JWKSet;import com.nimbusds.jose.jwk.RSAKey;import com.nimbusds.jose.jwk.source.ImmutableJWKSet;import com.nimbusds.jose.proc.SecurityContext;import org.springframework.context.annotation.*;import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;import org.springframework.security.crypto.password.PasswordEncoder;import org.springframework.security.oauth2.jwt.*;import java.security.KeyPair;import java.security.KeyPairGenerator;import java.security.interfaces.RSAPrivateKey;import java.security.interfaces.RSAPublicKey;import java.util.UUID;
@Configuration public class JwtConfiguration {
 @Bean RSAKey rsaKey() throws Exception {KeyPairGenerator g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);KeyPair p=g.generateKeyPair();return new RSAKey.Builder((RSAPublicKey)p.getPublic()).privateKey((RSAPrivateKey)p.getPrivate()).keyID(UUID.randomUUID().toString()).build();}
 @Bean JwtEncoder jwtEncoder(RSAKey key){return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));}
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
}

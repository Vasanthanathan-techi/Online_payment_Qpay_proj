package com.qpay.merchant.security;
import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Component;import javax.crypto.*;import javax.crypto.spec.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;
@Component public class SecretProtector {
 private final byte[] key;private final SecureRandom random=new SecureRandom();
 public SecretProtector(@Value("${qpay.merchant-secret-key:local-merchant-secret-change-me}")String secret){try{key=MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
 public ProtectedSecret encrypt(String raw){try{byte[] nonce=new byte[12];random.nextBytes(nonce);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));return new ProtectedSecret(cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8)),nonce,"v1");}catch(Exception e){throw new IllegalStateException("secret encryption failed",e);}}
 public byte[] hash(String raw){try{return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
 public String randomToken(int bytes){byte[] value=new byte[bytes];random.nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
 public record ProtectedSecret(byte[] ciphertext,byte[] nonce,String keyVersion){}
}

package com.qpay.user.security;
import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Component;import javax.crypto.*;import javax.crypto.spec.*;import java.nio.charset.StandardCharsets;import java.security.*;
@Component public class DeviceTokenProtector {
 private final byte[] key;private final SecureRandom random=new SecureRandom();
 public DeviceTokenProtector(@Value("${qpay.device-token-secret:local-device-secret-change-me}")String secret){try{key=MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
 public ProtectedToken protect(String raw){try{byte[] nonce=new byte[12];random.nextBytes(nonce);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));return new ProtectedToken(c.doFinal(raw.getBytes(StandardCharsets.UTF_8)),nonce,MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)),"v1",mask(raw));}catch(Exception e){throw new IllegalStateException("device-token protection failed",e);}}
 private String mask(String raw){return raw.length()<=8?"********":raw.substring(0,4)+"..."+raw.substring(raw.length()-4);}
 public record ProtectedToken(byte[] ciphertext,byte[] nonce,byte[] hash,String keyVersion,String mask){}
}

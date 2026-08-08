package com.qpay.payout.security;
import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Component;
import javax.crypto.*;import javax.crypto.spec.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.Arrays;
@Component public class BankDataCrypto {
 private final byte[] key;private final SecureRandom random=new SecureRandom();
 public BankDataCrypto(@Value("${qpay.bank-data-secret:local-dev-key-change-before-production}") String secret){try{key=MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
 public Encrypted encrypt(String value){try{byte[] nonce=new byte[12];random.nextBytes(nonce);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));return new Encrypted(c.doFinal(value.getBytes(StandardCharsets.UTF_8)),nonce,"v1");}catch(Exception e){throw new IllegalStateException("bank data encryption failed",e);}}
 public byte[] fingerprint(String bank,String account){try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal((bank+"\u001f"+account).getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
 public record Encrypted(byte[] ciphertext,byte[] nonce,String keyVersion){}
}

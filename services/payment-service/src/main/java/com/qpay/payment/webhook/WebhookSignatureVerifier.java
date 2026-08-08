package com.qpay.payment.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class WebhookSignatureVerifier {
    private final byte[] secret;
    private final Clock clock;
    private final Duration tolerance;

    @Autowired
    public WebhookSignatureVerifier(
            @Value("${qpay.payment.webhook-secret:local-webhook-secret-change-me}") String secret) {
        this(secret, Clock.systemUTC(), Duration.ofMinutes(5));
    }

    WebhookSignatureVerifier(String secret, Clock clock, Duration tolerance) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
        this.tolerance = tolerance;
    }

    public boolean isValid(String rawBody, long timestampSeconds, String signatureHex) {
        Instant signedAt = Instant.ofEpochSecond(timestampSeconds);
        if (Duration.between(signedAt, clock.instant()).abs().compareTo(tolerance) > 0) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] expected = mac.doFinal((timestampSeconds + "." + rawBody).getBytes(StandardCharsets.UTF_8));
            byte[] supplied = HexFormat.of().parseHex(signatureHex);
            return MessageDigest.isEqual(expected, supplied);
        } catch (Exception exception) {
            return false;
        }
    }
}

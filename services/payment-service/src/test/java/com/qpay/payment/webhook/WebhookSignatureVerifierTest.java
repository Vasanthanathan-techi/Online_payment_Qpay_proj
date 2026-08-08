package com.qpay.payment.webhook;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {
    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");

    @Test
    void acceptsAuthenticRecentPayload() throws Exception {
        String body = "{\"status\":\"SUCCEEDED\"}";
        long timestamp = NOW.getEpochSecond();
        var verifier = new WebhookSignatureVerifier(
                "secret", Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));

        assertThat(verifier.isValid(body, timestamp, sign("secret", timestamp, body))).isTrue();
    }

    @Test
    void rejectsReplayOutsideTolerance() throws Exception {
        String body = "{}";
        long timestamp = NOW.minus(Duration.ofMinutes(6)).getEpochSecond();
        var verifier = new WebhookSignatureVerifier(
                "secret", Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));

        assertThat(verifier.isValid(body, timestamp, sign("secret", timestamp, body))).isFalse();
    }

    private String sign(String secret, long timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }
}

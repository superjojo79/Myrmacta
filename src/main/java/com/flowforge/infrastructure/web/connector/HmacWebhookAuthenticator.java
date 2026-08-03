package com.flowforge.infrastructure.web.connector;

import com.flowforge.domain.connector.model.WebhookAuthType;
import com.flowforge.domain.connector.model.WebhookAuthenticationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class HmacWebhookAuthenticator implements WebhookAuthenticator {

    private static final String HEADER = "X-Signature-256";
    private static final String PREFIX = "sha256=";
    private static final String ALGORITHM = "HmacSHA256";

    @Override
    public boolean supports(WebhookAuthType authType) {
        return authType == WebhookAuthType.HMAC_SHA256;
    }

    @Override
    public void authenticate(UUID instanceId, String webhookSecret, byte[] rawBody, HttpHeaders headers) {
        String provided = headers.getFirst(HEADER);
        if (webhookSecret == null || webhookSecret.isBlank() || provided == null || provided.isBlank()) {
            throw new WebhookAuthenticationException(instanceId);
        }
        String expected = PREFIX + hmacHex(webhookSecret, rawBody == null ? new byte[0] : rawBody);
        if (!constantTimeEquals(expected, provided)) {
            throw new WebhookAuthenticationException(instanceId);
        }
    }

    private static String hmacHex(String secret, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(rawBody));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 calculation failed", ex);
        }
    }

    static boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}

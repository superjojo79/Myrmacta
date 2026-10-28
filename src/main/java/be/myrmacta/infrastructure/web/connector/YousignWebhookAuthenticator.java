package be.myrmacta.infrastructure.web.connector;

import be.myrmacta.domain.connector.model.WebhookAuthType;
import be.myrmacta.domain.connector.model.WebhookAuthenticationException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §109 (D-SIGNATURE-WEBHOOK) — Yousign signe le corps brut et place l'identifiant de
 * l'événement dans {@code event_id}. Une clé {@code Idempotency-Key} fournie par l'appelant ne
 * remplace pas cet identifiant : elle n'est pas couverte par la signature.
 */
@Component
public class YousignWebhookAuthenticator implements WebhookAuthenticator {

    static final String SIGNATURE_HEADER = "X-Yousign-Signature-256";
    private static final String PREFIX = "sha256=";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    public boolean supports(WebhookAuthType authType) {
        return authType == WebhookAuthType.YOUSIGN_HMAC;
    }

    @Override
    public void authenticate(UUID instanceId, String webhookSecret, byte[] rawBody, HttpHeaders headers) {
        String provided = headers.getFirst(SIGNATURE_HEADER);
        if (webhookSecret == null || webhookSecret.isBlank() || provided == null || provided.isBlank()) {
            throw new WebhookAuthenticationException(instanceId);
        }
        String expected = PREFIX + hmacHex(webhookSecret, rawBody == null ? new byte[0] : rawBody);
        if (!HmacWebhookAuthenticator.constantTimeEquals(expected, provided)) {
            throw new WebhookAuthenticationException(instanceId);
        }
    }

    /** Identifiant d'idempotence, lu dans le corps déjà authentifié. */
    public String idempotencyKey(byte[] rawBody) {
        JsonNode node;
        try {
            node = JSON.readTree(rawBody == null ? new byte[0] : rawBody);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Yousign callback body must be JSON");
        }
        JsonNode id = node == null ? null : node.get("event_id");
        if (id == null || !id.isString()) {
            throw new IllegalArgumentException("Yousign callback event_id is required");
        }
        try {
            return UUID.fromString(id.asString()).toString();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Yousign callback event_id must be a UUID");
        }
    }

    private static String hmacHex(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 calculation failed", ex);
        }
    }
}

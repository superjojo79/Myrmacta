package be.myrmacta.domain.notification.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Command for a single outbound webhook delivery (envelope + optional HMAC).
 */
public record WebhookDeliveryCommand(
        String targetUrl,
        String secretPlain,
        UUID outboxId,
        Instant at,
        Map<String, Object> payload) {

    public WebhookDeliveryCommand {
        Objects.requireNonNull(targetUrl, "targetUrl");
        Objects.requireNonNull(outboxId, "outboxId");
        Objects.requireNonNull(at, "at");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}

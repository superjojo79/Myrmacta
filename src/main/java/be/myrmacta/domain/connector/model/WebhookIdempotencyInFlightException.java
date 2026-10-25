package be.myrmacta.domain.connector.model;

import java.util.UUID;

/** Another in-flight webhook delivery holds the same Idempotency-Key (D-IDEM race). */
public class WebhookIdempotencyInFlightException extends RuntimeException {

    private final UUID instanceId;
    private final String idempotencyKey;

    public WebhookIdempotencyInFlightException(UUID instanceId, String idempotencyKey) {
        super("Webhook delivery already in progress for idempotency key");
        this.instanceId = instanceId;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID instanceId() {
        return instanceId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }
}

package com.flowforge.domain.connector.model;

import java.util.UUID;

public class WebhookAuthenticationException extends RuntimeException {

    private final UUID instanceId;

    public WebhookAuthenticationException(UUID instanceId) {
        super("Webhook authentication failed for connector instance: " + instanceId);
        this.instanceId = instanceId;
    }

    public UUID instanceId() {
        return instanceId;
    }
}

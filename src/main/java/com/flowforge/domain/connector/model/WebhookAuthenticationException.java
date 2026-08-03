package com.flowforge.domain.connector.model;

import java.util.UUID;

public class WebhookAuthenticationException extends RuntimeException {

    public WebhookAuthenticationException(UUID instanceId) {
        super("Webhook authentication failed for connector instance: " + instanceId);
    }
}

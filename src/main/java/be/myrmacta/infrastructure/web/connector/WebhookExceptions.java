package be.myrmacta.infrastructure.web.connector;

import java.util.UUID;

class WebhookEndpointNotFoundException extends RuntimeException {

    WebhookEndpointNotFoundException(UUID instanceId) {
        super("Webhook endpoint not found: " + instanceId);
    }
}

class WebhookPayloadTooLargeException extends RuntimeException {

    WebhookPayloadTooLargeException(long maxBodyBytes) {
        super("Webhook payload exceeds maximum size of " + maxBodyBytes + " bytes");
    }
}

class WebhookRateLimitExceededException extends RuntimeException {

    WebhookRateLimitExceededException(UUID instanceId) {
        super("Webhook rate limit exceeded for connector instance: " + instanceId);
    }
}

package com.flowforge.domain.notification.model;

import java.util.UUID;

public class NotificationWebhookNotFoundException extends RuntimeException {

    public NotificationWebhookNotFoundException(UUID id) {
        super("Notification webhook not found: " + id);
    }
}

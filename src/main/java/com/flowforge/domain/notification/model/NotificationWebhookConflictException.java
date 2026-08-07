package com.flowforge.domain.notification.model;

public class NotificationWebhookConflictException extends RuntimeException {

    public NotificationWebhookConflictException(String message) {
        super(message);
    }
}

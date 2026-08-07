package com.flowforge.domain.notification.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fan-out request for one or more notification channels. Validation of required
 * per-channel fields is performed by {@code SendNotificationUseCase}.
 */
public record NotificationEvent(
        List<NotificationChannel> channels,
        String to,
        String subject,
        String body,
        String userId,
        UUID webhookConfigId,
        Map<String, Object> payload) {}

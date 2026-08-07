package com.flowforge.domain.notification.port.in;

import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import java.util.List;
import java.util.UUID;

public interface ManageNotificationWebhookUseCase {

    NotificationWebhookConfig create(CreateCommand command);

    NotificationWebhookConfig get(UUID id, String tenantId);

    List<NotificationWebhookConfig> list(String tenantId);

    NotificationWebhookConfig update(UpdateCommand command);

    void softDelete(UUID id, String tenantId);

    NotificationWebhookConfig restore(UUID id, String tenantId);

    record CreateCommand(
            String tenantId, String name, String targetUrl, String secret, boolean enabled) {}

    record UpdateCommand(
            UUID id,
            String tenantId,
            String name,
            String targetUrl,
            String secret,
            Boolean enabled,
            boolean restore) {}
}

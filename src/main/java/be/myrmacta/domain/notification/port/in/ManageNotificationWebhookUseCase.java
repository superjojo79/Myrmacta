package be.myrmacta.domain.notification.port.in;

import be.myrmacta.domain.notification.model.NotificationWebhookConfig;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.util.UUID;

public interface ManageNotificationWebhookUseCase {

    NotificationWebhookConfig create(CreateCommand command);

    NotificationWebhookConfig get(UUID id, String tenantId);

    PageResult<NotificationWebhookConfig> list(String tenantId, PageQuery query);

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

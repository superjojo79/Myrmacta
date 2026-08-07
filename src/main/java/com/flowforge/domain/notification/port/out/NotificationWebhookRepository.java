package com.flowforge.domain.notification.port.out;

import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationWebhookRepository {

    NotificationWebhookConfig save(NotificationWebhookConfig config);

    /** Active (non soft-deleted) webhook for tenant. */
    Optional<NotificationWebhookConfig> findByIdAndTenantId(UUID id, String tenantId);

    Optional<NotificationWebhookConfig> findByIdAndTenantIdIncludingDeleted(UUID id, String tenantId);

    /** Active webhooks only. */
    List<NotificationWebhookConfig> listByTenantId(String tenantId);

    void softDelete(UUID id, String tenantId, Instant now);

    /**
     * Whether an active webhook with {@code name} already exists for the tenant.
     * When updating, pass the current id as {@code excludeIdOrNull} so it is ignored.
     */
    boolean existsActiveByName(String tenantId, String name, UUID excludeIdOrNull);
}

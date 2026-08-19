package com.flowforge.domain.notification.port.out;

import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
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

    default PageResult<NotificationWebhookConfig> pageByTenantId(String tenantId, PageQuery query) {
        return PageResult.slice(listByTenantId(tenantId), query);
    }

    void softDelete(UUID id, String tenantId, Instant now);

    /**
     * Whether an active webhook with {@code name} already exists for the tenant.
     * When updating, pass the current id as {@code excludeIdOrNull} so it is ignored.
     */
    boolean existsActiveByName(String tenantId, String name, UUID excludeIdOrNull);
}

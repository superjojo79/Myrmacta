package com.flowforge.domain.notification.port.in;

import com.flowforge.domain.notification.model.NotificationEvent;
import java.util.List;
import java.util.UUID;

public interface SendNotificationUseCase {

    /**
     * Validates the event and persists one PENDING outbox row per unique channel.
     *
     * @return outbox ids in channel fan-out order
     */
    List<UUID> send(NotificationEvent event, String tenantId);
}

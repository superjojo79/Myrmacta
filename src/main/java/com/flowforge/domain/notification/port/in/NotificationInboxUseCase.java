package com.flowforge.domain.notification.port.in;

import com.flowforge.domain.notification.model.UserNotificationRecord;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.util.UUID;

public interface NotificationInboxUseCase {

    PageResult<UserNotificationRecord> list(String tenantId, String recipient, PageQuery query);

    /**
     * TODO §66 F3 (Phase 10 adversarial review): server-side source of truth for the unread badge
     * — the frontend must not derive it from however many items it happens to have loaded.
     */
    long unreadCount(String tenantId, String recipient);

    /** @throws com.flowforge.domain.notification.model.UserNotificationNotFoundException if {@code id} is unknown or belongs to another recipient. */
    void markRead(UUID id, String tenantId, String recipient);

    void markAllRead(String tenantId, String recipient);
}

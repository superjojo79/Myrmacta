package com.flowforge.domain.notification.port.out;

import com.flowforge.domain.notification.model.UserNotificationRecord;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.time.Instant;
import java.util.UUID;

public interface UserNotificationRepository {

    UUID save(UUID id, String tenantId, String recipient, String payloadJson, Instant now);

    PageResult<UserNotificationRecord> page(String tenantId, String recipient, PageQuery query);

    long countUnread(String tenantId, String recipient);

    /** Scoped update — returns {@code false} if {@code id} does not exist or belongs to another recipient. */
    boolean markRead(UUID id, String tenantId, String recipient, Instant now);

    int markAllRead(String tenantId, String recipient, Instant now);

    int purgeOlderThan(Instant cutoff);
}

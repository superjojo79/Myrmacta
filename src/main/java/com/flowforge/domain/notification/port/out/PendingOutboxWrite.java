package com.flowforge.domain.notification.port.out;

import com.flowforge.domain.notification.model.NotificationChannel;
import java.util.Objects;

/** One pending outbox row to persist inside a single transactional batch. */
public record PendingOutboxWrite(NotificationChannel channel, String payloadJson) {
    public PendingOutboxWrite {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(payloadJson, "payloadJson");
    }
}

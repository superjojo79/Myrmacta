package com.flowforge.infrastructure.notification;

import com.flowforge.domain.notification.port.out.UserNotificationRepository;
import com.flowforge.infrastructure.config.NotificationProperties;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** D-NOTIF-RETENTION (TODO §66) — miroir de {@code WorkflowRetentionJob}. */
@Component
class NotificationInboxRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationInboxRetentionJob.class);

    private final UserNotificationRepository userNotificationRepository;
    private final NotificationProperties properties;

    NotificationInboxRetentionJob(
            UserNotificationRepository userNotificationRepository, NotificationProperties properties) {
        this.userNotificationRepository = Objects.requireNonNull(userNotificationRepository, "userNotificationRepository");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Scheduled(fixedDelayString = "${app.notification.inbox.poll-delay:1d}")
    void purge() {
        Instant cutoff = Instant.now().minus(properties.inbox().retention());
        int purged = userNotificationRepository.purgeOlderThan(cutoff);
        if (purged > 0) {
            log.info("Notification inbox retention job purged {} user_notification row(s)", purged);
        }
    }
}

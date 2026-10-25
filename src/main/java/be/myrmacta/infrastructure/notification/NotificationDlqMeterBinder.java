package be.myrmacta.infrastructure.notification;

import be.myrmacta.domain.notification.model.NotificationChannel;
import be.myrmacta.domain.notification.model.NotificationOutboxStatus;
import be.myrmacta.domain.notification.port.out.NotificationOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NotificationDlqMeterBinder implements MeterBinder {

    static final String WEBHOOK_DEAD = "flowforge.notification.webhook.dead";

    private static final Logger log = LoggerFactory.getLogger(NotificationDlqMeterBinder.class);

    private final NotificationOutboxRepository notificationOutboxRepository;

    public NotificationDlqMeterBinder(NotificationOutboxRepository notificationOutboxRepository) {
        this.notificationOutboxRepository =
                Objects.requireNonNull(notificationOutboxRepository, "notificationOutboxRepository");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(WEBHOOK_DEAD, this, ignored -> (double) deadCount()).register(registry);
    }

    public long deadCount() {
        try {
            return notificationOutboxRepository.countByChannelAndStatus(
                    NotificationChannel.WEBHOOK, NotificationOutboxStatus.DEAD);
        } catch (RuntimeException ex) {
            log.warn("Failed to collect webhook dead-letter count", ex);
            return 0L;
        }
    }
}

package be.myrmacta.infrastructure.observability;

import be.myrmacta.domain.dossier.port.out.PurgeOutboxRepository;
import be.myrmacta.domain.notification.port.out.NotificationOutboxRepository;
import be.myrmacta.domain.workflow.port.out.OutboxRepository;
import be.myrmacta.infrastructure.flowable.FlowableJobMetricsBinder;
import be.myrmacta.infrastructure.notification.NotificationDlqMeterBinder;
import be.myrmacta.infrastructure.notification.SseNotificationAdapter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.flowable.engine.ManagementService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ObservabilityMeterConfig {

    static final String SSE_ACTIVE = "myrmacta.sse.emitters.active";

    @Bean
    OutboxAgeMeterBinder outboxAgeMeterBinder(
            OutboxRepository outboxRepository,
            NotificationOutboxRepository notificationOutboxRepository,
            PurgeOutboxRepository purgeOutboxRepository) {
        return new OutboxAgeMeterBinder(outboxRepository, notificationOutboxRepository, purgeOutboxRepository);
    }

    @Bean
    FlowableJobMetricsBinder flowableJobMetricsBinder(ManagementService managementService) {
        return new FlowableJobMetricsBinder(managementService);
    }

    @Bean
    NotificationDlqMeterBinder notificationDlqMeterBinder(
            NotificationOutboxRepository notificationOutboxRepository) {
        return new NotificationDlqMeterBinder(notificationOutboxRepository);
    }

    @Bean
    MeterBinder sseEmitterMeterBinder(SseNotificationAdapter adapter) {
        return (MeterRegistry registry) -> Gauge.builder(SSE_ACTIVE, adapter, SseNotificationAdapter::activeEmitterCount)
                .register(registry);
    }
}

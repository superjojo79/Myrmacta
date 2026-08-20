package com.flowforge.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationOutboxStatus;
import com.flowforge.domain.notification.port.out.NotificationOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class NotificationDlqMeterBinderTest {

    @Test
    void countsWebhookDeadOnly() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        when(repository.countByChannelAndStatus(NotificationChannel.WEBHOOK, NotificationOutboxStatus.DEAD))
                .thenReturn(4L);
        NotificationDlqMeterBinder binder = new NotificationDlqMeterBinder(repository);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        binder.bindTo(registry);
        assertThat(registry.get(NotificationDlqMeterBinder.WEBHOOK_DEAD).gauge().value()).isEqualTo(4.0);
    }

    @Test
    void collectionFailure_returnsZero() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        when(repository.countByChannelAndStatus(NotificationChannel.WEBHOOK, NotificationOutboxStatus.DEAD))
                .thenThrow(new IllegalStateException("db"));
        NotificationDlqMeterBinder binder = new NotificationDlqMeterBinder(repository);
        assertThat(binder.deadCount()).isZero();
    }
}

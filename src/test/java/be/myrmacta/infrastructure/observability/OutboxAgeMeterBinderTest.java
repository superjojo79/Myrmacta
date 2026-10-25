package be.myrmacta.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.port.out.PurgeOutboxRepository;
import be.myrmacta.domain.notification.port.out.NotificationOutboxRepository;
import be.myrmacta.domain.workflow.port.out.OutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OutboxAgeMeterBinderTest {

    @Test
    void emptyOutboxes_reportZeroSeconds() {
        OutboxRepository trigger = mock(OutboxRepository.class);
        NotificationOutboxRepository notification = mock(NotificationOutboxRepository.class);
        PurgeOutboxRepository purge = mock(PurgeOutboxRepository.class);
        when(trigger.oldestPendingCreatedAt()).thenReturn(Optional.empty());
        when(notification.oldestPendingCreatedAt()).thenReturn(Optional.empty());
        when(purge.oldestUnfinishedCreatedAt()).thenReturn(Optional.empty());

        OutboxAgeMeterBinder binder = new OutboxAgeMeterBinder(trigger, notification, purge);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        binder.bindTo(registry);

        assertThat(registry.get(OutboxAgeMeterBinder.TRIGGER_AGE).gauge().value()).isZero();
        assertThat(registry.get(OutboxAgeMeterBinder.NOTIFICATION_AGE).gauge().value()).isZero();
        assertThat(registry.get(OutboxAgeMeterBinder.PURGE_AGE).gauge().value()).isZero();
    }

    @Test
    void pendingRow_reportsFlooredAgeSeconds() {
        Instant now = Instant.parse("2026-08-20T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        OutboxRepository trigger = mock(OutboxRepository.class);
        NotificationOutboxRepository notification = mock(NotificationOutboxRepository.class);
        PurgeOutboxRepository purge = mock(PurgeOutboxRepository.class);
        when(trigger.oldestPendingCreatedAt()).thenReturn(Optional.of(now.minusSeconds(125)));
        when(notification.oldestPendingCreatedAt()).thenReturn(Optional.empty());
        when(purge.oldestUnfinishedCreatedAt()).thenReturn(Optional.of(now.minusSeconds(3600)));

        OutboxAgeMeterBinder binder = new OutboxAgeMeterBinder(trigger, notification, purge, clock);
        assertThat(binder.triggerAgeSeconds()).isEqualTo(125);
        assertThat(binder.purgeAgeSeconds()).isEqualTo(3600);
    }

    @Test
    void collectionFailure_returnsZero() {
        OutboxRepository trigger = mock(OutboxRepository.class);
        when(trigger.oldestPendingCreatedAt()).thenThrow(new IllegalStateException("db"));
        OutboxAgeMeterBinder binder = new OutboxAgeMeterBinder(
                trigger, mock(NotificationOutboxRepository.class), mock(PurgeOutboxRepository.class));
        assertThat(binder.triggerAgeSeconds()).isZero();
    }
}

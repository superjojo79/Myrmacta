package com.flowforge.infrastructure.observability;

import com.flowforge.domain.dossier.port.out.PurgeOutboxRepository;
import com.flowforge.domain.notification.port.out.NotificationOutboxRepository;
import com.flowforge.domain.workflow.port.out.OutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OutboxAgeMeterBinder implements MeterBinder {

    static final String TRIGGER_AGE = "flowforge.outbox.trigger.oldest.pending.age";
    static final String NOTIFICATION_AGE = "flowforge.outbox.notification.oldest.pending.age";
    static final String PURGE_AGE = "flowforge.outbox.purge.oldest.unfinished.age";

    private static final Logger log = LoggerFactory.getLogger(OutboxAgeMeterBinder.class);

    private final OutboxRepository outboxRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final PurgeOutboxRepository purgeOutboxRepository;
    private final Clock clock;

    public OutboxAgeMeterBinder(
            OutboxRepository outboxRepository,
            NotificationOutboxRepository notificationOutboxRepository,
            PurgeOutboxRepository purgeOutboxRepository) {
        this(outboxRepository, notificationOutboxRepository, purgeOutboxRepository, Clock.systemUTC());
    }

    OutboxAgeMeterBinder(
            OutboxRepository outboxRepository,
            NotificationOutboxRepository notificationOutboxRepository,
            PurgeOutboxRepository purgeOutboxRepository,
            Clock clock) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository");
        this.notificationOutboxRepository =
                Objects.requireNonNull(notificationOutboxRepository, "notificationOutboxRepository");
        this.purgeOutboxRepository = Objects.requireNonNull(purgeOutboxRepository, "purgeOutboxRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(TRIGGER_AGE, this, OutboxAgeMeterBinder::triggerAge)
                .baseUnit("seconds")
                .register(registry);
        Gauge.builder(NOTIFICATION_AGE, this, OutboxAgeMeterBinder::notificationAge)
                .baseUnit("seconds")
                .register(registry);
        Gauge.builder(PURGE_AGE, this, OutboxAgeMeterBinder::purgeAge)
                .baseUnit("seconds")
                .register(registry);
    }

    long triggerAgeSeconds() {
        return ageSeconds(safeOldest(() -> outboxRepository.oldestPendingCreatedAt(), "trigger"));
    }

    long notificationAgeSeconds() {
        return ageSeconds(
                safeOldest(() -> notificationOutboxRepository.oldestPendingCreatedAt(), "notification"));
    }

    long purgeAgeSeconds() {
        return ageSeconds(safeOldest(() -> purgeOutboxRepository.oldestUnfinishedCreatedAt(), "purge"));
    }

    private double triggerAge() {
        return triggerAgeSeconds();
    }

    private double notificationAge() {
        return notificationAgeSeconds();
    }

    private double purgeAge() {
        return purgeAgeSeconds();
    }

    private Optional<Instant> safeOldest(OldestSupplier supplier, String name) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            log.warn("Failed to collect {} outbox age", name, ex);
            return Optional.empty();
        }
    }

    private long ageSeconds(Optional<Instant> createdAt) {
        return createdAt
                .map(at -> Math.max(0, Duration.between(at, clock.instant()).getSeconds()))
                .orElse(0L);
    }

    @FunctionalInterface
    private interface OldestSupplier {
        Optional<Instant> get();
    }
}

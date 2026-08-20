package com.flowforge.infrastructure.observability;

import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.flowable.FlowableJobMetricsBinder;
import com.flowforge.infrastructure.notification.NotificationDlqMeterBinder;
import com.flowforge.infrastructure.notification.SseNotificationAdapter;
import com.flowforge.infrastructure.pdf.FlyingSaucerPdfAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ObservabilitySnapshotFacade {

    private final TenantProperties tenantProperties;
    private final OutboxAgeMeterBinder outboxAgeMeterBinder;
    private final FlowableJobMetricsBinder flowableJobMetricsBinder;
    private final NotificationDlqMeterBinder notificationDlqMeterBinder;
    private final SseNotificationAdapter sseNotificationAdapter;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    @Autowired
    public ObservabilitySnapshotFacade(
            TenantProperties tenantProperties,
            OutboxAgeMeterBinder outboxAgeMeterBinder,
            FlowableJobMetricsBinder flowableJobMetricsBinder,
            NotificationDlqMeterBinder notificationDlqMeterBinder,
            SseNotificationAdapter sseNotificationAdapter,
            MeterRegistry meterRegistry) {
        this(
                tenantProperties,
                outboxAgeMeterBinder,
                flowableJobMetricsBinder,
                notificationDlqMeterBinder,
                sseNotificationAdapter,
                meterRegistry,
                Clock.systemUTC());
    }

    ObservabilitySnapshotFacade(
            TenantProperties tenantProperties,
            OutboxAgeMeterBinder outboxAgeMeterBinder,
            FlowableJobMetricsBinder flowableJobMetricsBinder,
            NotificationDlqMeterBinder notificationDlqMeterBinder,
            SseNotificationAdapter sseNotificationAdapter,
            MeterRegistry meterRegistry,
            Clock clock) {
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.outboxAgeMeterBinder = Objects.requireNonNull(outboxAgeMeterBinder, "outboxAgeMeterBinder");
        this.flowableJobMetricsBinder = Objects.requireNonNull(flowableJobMetricsBinder, "flowableJobMetricsBinder");
        this.notificationDlqMeterBinder =
                Objects.requireNonNull(notificationDlqMeterBinder, "notificationDlqMeterBinder");
        this.sseNotificationAdapter = Objects.requireNonNull(sseNotificationAdapter, "sseNotificationAdapter");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ObservabilitySnapshotResponse snapshot() {
        Instant observedAt = clock.instant();
        long triggerAge = outboxAgeMeterBinder.triggerAgeSeconds();
        long notificationAge = outboxAgeMeterBinder.notificationAgeSeconds();
        long purgeAge = outboxAgeMeterBinder.purgeAgeSeconds();
        long deadLetter = flowableJobMetricsBinder.deadLetterCount();
        long exceptionJobs = flowableJobMetricsBinder.exceptionCount();
        ObservabilitySnapshotResponse.PdfRenderStats pdf = pdfStats();
        long webhookDead = notificationDlqMeterBinder.deadCount();
        long sseActive = sseNotificationAdapter.activeEmitterCount();
        return new ObservabilitySnapshotResponse(
                tenantProperties.id(),
                observedAt,
                triggerAge,
                notificationAge,
                purgeAge,
                deadLetter,
                exceptionJobs,
                pdf,
                webhookDead,
                sseActive,
                new ObservabilitySnapshotResponse.ObservabilitySeverityMap(
                        ObservabilityThresholds.ageSeverity(triggerAge),
                        ObservabilityThresholds.ageSeverity(notificationAge),
                        ObservabilityThresholds.ageSeverity(purgeAge),
                        ObservabilityThresholds.flowableSeverity(deadLetter),
                        ObservabilityThresholds.flowableSeverity(exceptionJobs),
                        ObservabilityThresholds.pdfSeverity(pdf.count(), pdf.meanMs()),
                        ObservabilityThresholds.webhookSeverity(webhookDead),
                        ObservabilityThresholds.sseSeverity(sseActive)));
    }

    private ObservabilitySnapshotResponse.PdfRenderStats pdfStats() {
        Timer timer = meterRegistry.find(FlyingSaucerPdfAdapter.PDF_RENDER_TIMER).timer();
        if (timer == null || timer.count() == 0) {
            return new ObservabilitySnapshotResponse.PdfRenderStats(0, null, null);
        }
        return new ObservabilitySnapshotResponse.PdfRenderStats(
                timer.count(), timer.mean(TimeUnit.MILLISECONDS), timer.max(TimeUnit.MILLISECONDS));
    }
}

package com.flowforge.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.flowable.FlowableJobMetricsBinder;
import com.flowforge.infrastructure.notification.NotificationDlqMeterBinder;
import com.flowforge.infrastructure.notification.SseNotificationAdapter;
import com.flowforge.infrastructure.pdf.FlyingSaucerPdfAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ObservabilitySnapshotFacadeTest {

    @Test
    void snapshot_mapsAgesCountsAndSeverity_andNullPdfWhenUnused() {
        OutboxAgeMeterBinder ages = mock(OutboxAgeMeterBinder.class);
        when(ages.triggerAgeSeconds()).thenReturn(12L);
        when(ages.notificationAgeSeconds()).thenReturn(0L);
        when(ages.purgeAgeSeconds()).thenReturn(3600L);
        FlowableJobMetricsBinder flowable = mock(FlowableJobMetricsBinder.class);
        when(flowable.deadLetterCount()).thenReturn(0L);
        when(flowable.exceptionCount()).thenReturn(1L);
        NotificationDlqMeterBinder webhooks = mock(NotificationDlqMeterBinder.class);
        when(webhooks.deadCount()).thenReturn(2L);
        SseNotificationAdapter sse = mock(SseNotificationAdapter.class);
        when(sse.activeEmitterCount()).thenReturn(3);

        Instant now = Instant.parse("2026-08-20T07:41:00Z");
        ObservabilitySnapshotFacade facade = new ObservabilitySnapshotFacade(
                new TenantProperties("tenant_dev"),
                ages,
                flowable,
                webhooks,
                sse,
                new SimpleMeterRegistry(),
                Clock.fixed(now, ZoneOffset.UTC));

        ObservabilitySnapshotResponse snapshot = facade.snapshot();
        assertThat(snapshot.tenantId()).isEqualTo("tenant_dev");
        assertThat(snapshot.observedAt()).isEqualTo(now);
        assertThat(snapshot.triggerOutboxOldestPendingAgeSeconds()).isEqualTo(12);
        assertThat(snapshot.purgeOutboxOldestUnfinishedAgeSeconds()).isEqualTo(3600);
        assertThat(snapshot.pdfRender().count()).isZero();
        assertThat(snapshot.pdfRender().meanMs()).isNull();
        assertThat(snapshot.severity().purgeOutboxOldestUnfinishedAge()).isEqualTo("crit");
        assertThat(snapshot.severity().flowableExceptionJobs()).isEqualTo("warn");
        assertThat(snapshot.severity().webhookDead()).isEqualTo("warn");
        assertThat(snapshot.severity().pdfRender()).isEqualTo("ok");
        assertThat(FlyingSaucerPdfAdapter.PDF_RENDER_TIMER).isEqualTo("flowforge.pdf.render");
    }
}

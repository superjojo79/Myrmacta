package be.myrmacta.infrastructure.observability;

import java.time.Instant;

public record ObservabilitySnapshotResponse(
        String tenantId,
        Instant observedAt,
        long triggerOutboxOldestPendingAgeSeconds,
        long notificationOutboxOldestPendingAgeSeconds,
        long purgeOutboxOldestUnfinishedAgeSeconds,
        long flowableDeadLetterJobCount,
        long flowableExceptionJobCount,
        PdfRenderStats pdfRender,
        long webhookDeadCount,
        long sseActiveCount,
        ObservabilitySeverityMap severity) {

    public record PdfRenderStats(long count, Double meanMs, Double maxMs) {}

    public record ObservabilitySeverityMap(
            String triggerOutboxOldestPendingAge,
            String notificationOutboxOldestPendingAge,
            String purgeOutboxOldestUnfinishedAge,
            String flowableDeadLetterJobs,
            String flowableExceptionJobs,
            String pdfRender,
            String webhookDead,
            String sseActive) {}
}

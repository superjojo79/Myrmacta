package be.myrmacta.domain.workflow.model;

import java.time.Instant;
import java.util.Objects;

public record ActivityHistoryEntry(
        String activityId,
        String name,
        String assignee,
        Instant startTime,
        Instant endTime,
        Long durationMs
) {
    public ActivityHistoryEntry {
        Objects.requireNonNull(activityId, "activityId");
        Objects.requireNonNull(startTime, "startTime");
    }
}

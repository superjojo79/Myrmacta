package be.myrmacta.domain.reporting.model;

import java.util.Objects;

public record HistoricStepRow(String activityName, long durationMillis) {
    public HistoricStepRow {
        Objects.requireNonNull(activityName, "activityName");
        if (activityName.isBlank()) {
            throw new IllegalArgumentException("activityName must not be blank");
        }
    }
}

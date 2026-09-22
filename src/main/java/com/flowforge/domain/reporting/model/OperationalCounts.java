package com.flowforge.domain.reporting.model;

public record OperationalCounts(long deadLetterJobCount, long unassignedOpenTaskCount) {

    public OperationalCounts {
        if (deadLetterJobCount < 0 || unassignedOpenTaskCount < 0) {
            throw new IllegalArgumentException("counts must be >= 0");
        }
    }
}

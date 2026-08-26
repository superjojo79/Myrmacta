package com.flowforge.domain.reporting.model;

public class SlaCohortTooLargeException extends IllegalArgumentException {
    public SlaCohortTooLargeException(long count) {
        super("SLA cohort too large: " + count);
    }
}

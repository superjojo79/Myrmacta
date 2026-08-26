package com.flowforge.domain.reporting.model;

public class SlaCohortTooLargeException extends IllegalArgumentException {

    public static final String CODE = "SLA_COHORT_TOO_LARGE";

    public SlaCohortTooLargeException(long count) {
        super("SLA cohort too large: " + count);
    }
}

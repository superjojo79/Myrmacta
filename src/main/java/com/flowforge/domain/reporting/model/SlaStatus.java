package com.flowforge.domain.reporting.model;

import java.time.Duration;
import java.util.Objects;

public enum SlaStatus {
    ON_TRACK,
    AT_RISK,
    BREACHED;

    /**
     * Ternary SLA status. {@code null} when no target hours (unscored).
     * Closed dossiers never return {@link #AT_RISK}.
     */
    public static SlaStatus of(Duration elapsed, Integer slaTargetHours, boolean closed) {
        Objects.requireNonNull(elapsed, "elapsed");
        if (slaTargetHours == null) {
            return null;
        }
        if (SlaClock.overdue(elapsed, slaTargetHours)) {
            return BREACHED;
        }
        if (!closed) {
            long elapsedHours = SlaClock.elapsedHoursInt(elapsed);
            if (elapsedHours * 5 >= slaTargetHours * 4L && elapsedHours <= slaTargetHours) {
                return AT_RISK;
            }
        }
        return ON_TRACK;
    }
}

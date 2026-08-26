package com.flowforge.domain.reporting.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class SlaClock {

    private SlaClock() {}

    /**
     * Processing window: {@code createdAt} → {@code closedAt} when present; otherwise
     * {@code updatedAt} for a closed/archived dossier without {@code closedAt}; otherwise {@code now}.
     */
    public static Duration elapsed(
            Instant createdAt,
            Instant closedAt,
            Instant updatedAt,
            Instant now,
            boolean closed) {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(now, "now");
        Instant end;
        if (closedAt != null) {
            end = closedAt;
        } else if (closed && updatedAt != null) {
            end = updatedAt;
        } else {
            end = now;
        }
        if (end.isBefore(createdAt)) {
            return Duration.ZERO;
        }
        return Duration.between(createdAt, end);
    }

    public static boolean overdue(Duration elapsed, Integer slaTargetHours) {
        Objects.requireNonNull(elapsed, "elapsed");
        if (slaTargetHours == null) {
            return false;
        }
        return elapsed.compareTo(Duration.ofHours(slaTargetHours)) > 0;
    }

    public static Double remainingHours(Duration elapsed, Integer slaTargetHours) {
        Objects.requireNonNull(elapsed, "elapsed");
        if (slaTargetHours == null) {
            return null;
        }
        return slaTargetHours - (elapsed.toMillis() / 3_600_000.0);
    }
}

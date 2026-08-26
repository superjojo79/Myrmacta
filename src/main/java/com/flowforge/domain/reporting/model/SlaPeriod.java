package com.flowforge.domain.reporting.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Objects;

public record SlaPeriod(Instant from, Instant to) {

    public static final int DEFAULT_PERIOD_DAYS = 30;
    public static final int MAX_PERIOD_DAYS = 366;

    public SlaPeriod {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        if (Duration.between(from, to).toDays() > MAX_PERIOD_DAYS) {
            throw new IllegalArgumentException("period must be at most " + MAX_PERIOD_DAYS + " days");
        }
    }

    public static SlaPeriod resolve(String fromRaw, String toRaw, Instant now) {
        Objects.requireNonNull(now, "now");
        Instant from = blank(fromRaw) ? now.minus(Duration.ofDays(DEFAULT_PERIOD_DAYS)) : parse(fromRaw, false);
        Instant to = blank(toRaw) ? now : parse(toRaw, true);
        return new SlaPeriod(from, to);
    }

    static Instant parse(String raw, boolean exclusiveCivilEnd) {
        String value = raw.trim();
        if (isCivilDate(value)) {
            try {
                LocalDate date = LocalDate.parse(value);
                LocalDate start = exclusiveCivilEnd ? date.plusDays(1) : date;
                return start.atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("invalid period date: " + value, ex);
            }
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("invalid period instant: " + value, ex);
        }
    }

    private static boolean isCivilDate(String value) {
        return value.length() == 10 && value.charAt(4) == '-' && value.charAt(7) == '-';
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

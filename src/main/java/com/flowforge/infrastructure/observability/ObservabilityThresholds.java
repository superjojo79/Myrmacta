package com.flowforge.infrastructure.observability;

public final class ObservabilityThresholds {

    static final long AGE_WARN_SECONDS = 60;
    static final long AGE_CRIT_SECONDS = 300;
    static final long FLOWABLE_CRIT = 10;
    static final long WEBHOOK_CRIT = 5;
    static final double PDF_WARN_MS = 2000;
    static final double PDF_CRIT_MS = 5000;
    static final long SSE_WARN = 50;
    static final long SSE_CRIT = 200;

    private ObservabilityThresholds() {}

    public static String ageSeverity(long seconds) {
        if (seconds >= AGE_CRIT_SECONDS) {
            return "crit";
        }
        if (seconds >= AGE_WARN_SECONDS) {
            return "warn";
        }
        return "ok";
    }

    public static String countSeverity(long count, long critMin, long warnMin) {
        if (count >= critMin) {
            return "crit";
        }
        if (count >= warnMin) {
            return "warn";
        }
        return "ok";
    }

    public static String flowableSeverity(long count) {
        return countSeverity(count, FLOWABLE_CRIT, 1);
    }

    public static String webhookSeverity(long count) {
        return countSeverity(count, WEBHOOK_CRIT, 1);
    }

    public static String pdfSeverity(long count, Double meanMs) {
        if (count == 0 || meanMs == null) {
            return "ok";
        }
        if (meanMs >= PDF_CRIT_MS) {
            return "crit";
        }
        if (meanMs >= PDF_WARN_MS) {
            return "warn";
        }
        return "ok";
    }

    public static String sseSeverity(long count) {
        if (count >= SSE_CRIT) {
            return "crit";
        }
        if (count >= SSE_WARN) {
            return "warn";
        }
        return "ok";
    }
}

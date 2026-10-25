package be.myrmacta.domain.notification.model;

import java.util.Locale;

public enum NotificationChannel {
    EMAIL,
    WEBHOOK,
    BROWSER;

    /**
     * Parses a channel name (case-insensitive). Blank or unknown values throw
     * {@link NotificationValidationException}.
     */
    public static NotificationChannel parse(String value) {
        if (value == null || value.isBlank()) {
            throw new NotificationValidationException("channel is required");
        }
        try {
            return NotificationChannel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new NotificationValidationException("unknown notification channel: " + value);
        }
    }
}

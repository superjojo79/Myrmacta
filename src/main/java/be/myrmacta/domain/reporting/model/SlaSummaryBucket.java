package be.myrmacta.domain.reporting.model;

import java.util.Objects;

public record SlaSummaryBucket(
        String key,
        String label,
        long volume,
        long onTrack,
        long atRisk,
        long breached,
        long unscored,
        Double meanDurationHours,
        Double medianDurationHours) {
    public SlaSummaryBucket {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        if (volume != onTrack + atRisk + breached + unscored) {
            throw new IllegalArgumentException("volume must equal onTrack + atRisk + breached + unscored");
        }
    }
}

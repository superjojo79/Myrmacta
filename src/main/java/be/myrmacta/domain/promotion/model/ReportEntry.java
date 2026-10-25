package be.myrmacta.domain.promotion.model;

import java.util.Objects;

/** TODO §83 (D4) — une ligne du rapport d'application. */
public record ReportEntry(ArtifactType type, String key, ImportOutcome outcome, String message) {

    public ReportEntry {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(outcome, "outcome");
        message = message == null ? "" : message;
    }
}

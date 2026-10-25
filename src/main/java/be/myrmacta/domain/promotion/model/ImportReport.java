package be.myrmacta.domain.promotion.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** TODO §83 (D4) — rapport d'application : le plan suivi, et l'issue de chaque artefact. */
public record ImportReport(String bundleSha256, Instant performedAt, List<ReportEntry> entries) {

    public ImportReport {
        Objects.requireNonNull(bundleSha256, "bundleSha256");
        Objects.requireNonNull(performedAt, "performedAt");
        entries = List.copyOf(entries);
    }

    public long count(ImportOutcome outcome) {
        return entries.stream().filter(e -> e.outcome() == outcome).count();
    }

    public boolean hasFailures() {
        return count(ImportOutcome.FAILED) > 0;
    }
}

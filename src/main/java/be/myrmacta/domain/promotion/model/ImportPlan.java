package be.myrmacta.domain.promotion.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** TODO §83 (D4) — plan à blanc : une entrée par artefact du bundle, dans l'ordre d'application. */
public record ImportPlan(String bundleSha256, int formatVersion, String sourceLabel, List<PlanEntry> entries) {

    public ImportPlan {
        Objects.requireNonNull(bundleSha256, "bundleSha256");
        sourceLabel = sourceLabel == null ? "" : sourceLabel;
        entries = List.copyOf(entries);
    }

    public Map<PlanAction, Long> counts() {
        return entries.stream().collect(Collectors.groupingBy(PlanEntry::action, Collectors.counting()));
    }

    public java.util.Optional<PlanEntry> find(ArtifactType type, String key) {
        return entries.stream().filter(e -> e.type() == type && e.key().equals(key)).findFirst();
    }
}

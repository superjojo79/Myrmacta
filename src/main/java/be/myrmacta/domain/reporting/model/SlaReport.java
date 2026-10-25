package be.myrmacta.domain.reporting.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SlaReport(
        Instant from,
        Instant to,
        int complianceTargetPercent,
        Double compliancePercent,
        long overdueCount,
        Double meanProcessingDays,
        Double medianProcessingDays,
        Double meanDeltaVsPreviousDays,
        List<TypeShare> typeShares,
        List<StepDuration> stepDurationsHours,
        List<ManagerLoad> managerLoads,
        List<TypeBreakdown> types) {

    public SlaReport {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        typeShares = typeShares == null ? List.of() : List.copyOf(typeShares);
        stepDurationsHours = stepDurationsHours == null ? List.of() : List.copyOf(stepDurationsHours);
        managerLoads = managerLoads == null ? List.of() : List.copyOf(managerLoads);
        types = types == null ? List.of() : List.copyOf(types);
    }

    public record TypeShare(String code, String label, long volume, double percent) {
        public TypeShare {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(label, "label");
        }
    }

    public record StepDuration(String name, double meanHours) {
        public StepDuration {
            Objects.requireNonNull(name, "name");
        }
    }

    public record ManagerLoad(
            String assigneeId, String displayName, long inProgress, long overdue, Double slaPercent) {
        public ManagerLoad {
            Objects.requireNonNull(displayName, "displayName");
        }
    }

    public record TypeBreakdown(
            String typeCode,
            String typeLabel,
            String workflowKey,
            long volume,
            Double meanDays,
            Double slaPercent,
            long producedDocCount,
            Double volumeTrendPercent) {
        public TypeBreakdown {
            Objects.requireNonNull(typeCode, "typeCode");
            Objects.requireNonNull(typeLabel, "typeLabel");
            Objects.requireNonNull(workflowKey, "workflowKey");
        }
    }
}

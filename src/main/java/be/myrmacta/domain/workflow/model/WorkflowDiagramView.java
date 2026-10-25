package be.myrmacta.domain.workflow.model;

import java.util.List;
import java.util.Objects;

public record WorkflowDiagramView(
        String bpmnXml,
        List<String> activeIds,
        List<String> completedIds,
        List<String> failedIds
) {
    public WorkflowDiagramView {
        Objects.requireNonNull(bpmnXml, "bpmnXml");
        activeIds = List.copyOf(Objects.requireNonNullElse(activeIds, List.of()));
        completedIds = List.copyOf(Objects.requireNonNullElse(completedIds, List.of()));
        failedIds = List.copyOf(Objects.requireNonNullElse(failedIds, List.of()));
    }
}

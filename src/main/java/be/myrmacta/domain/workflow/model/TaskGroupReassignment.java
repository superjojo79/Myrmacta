package be.myrmacta.domain.workflow.model;

import java.util.List;

/** TODO §113 (D-GROUP-REASSIGN) — ce qu'était la tâche avant sa réaffectation à un groupe : pour la trace. */
public record TaskGroupReassignment(String taskName, List<String> previousGroups, String previousAssignee) {

    public TaskGroupReassignment {
        taskName = taskName == null ? "" : taskName;
        previousGroups = previousGroups == null ? List.of() : List.copyOf(previousGroups);
    }
}

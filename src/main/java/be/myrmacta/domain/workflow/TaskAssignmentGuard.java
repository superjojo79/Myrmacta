package be.myrmacta.domain.workflow;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;

/**
 * Shared assignee rule for complete-task (TODO §57 H2) and runtime question schemas (M8).
 * Unassigned tasks remain actionable by anyone who already passed the dossier EDIT / runtime
 * authenticated gate. Admins may act on a task assigned to someone else.
 */
public final class TaskAssignmentGuard {

    private TaskAssignmentGuard() {}

    public static void assertActorMayAct(String assignee, AccessContext.User actor) {
        if (assignee == null || assignee.isBlank()) {
            return;
        }
        if (actor.hasRole("TENANT_ADMIN") || actor.hasRole("WORKFLOW_ADMIN")) {
            return;
        }
        if (!assignee.equals(actor.userId())) {
            throw new AccessDeniedException("Only the task assignee can complete this task");
        }
    }
}

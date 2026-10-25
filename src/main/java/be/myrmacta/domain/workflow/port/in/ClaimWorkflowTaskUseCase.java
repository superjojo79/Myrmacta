package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import java.util.UUID;

/**
 * « M'assigner » (D-ASSIGN-CLAIM) : un utilisateur qui peut modifier le dossier prend une tâche
 * sans assigné. Une tâche déjà prise par un autre se réassigne (TA/WA), elle ne se prend pas.
 */
public interface ClaimWorkflowTaskUseCase {
    void claim(UUID linkId, String taskId, AccessContext.User actor);
}

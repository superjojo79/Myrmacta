package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import java.util.UUID;

/**
 * TODO §114 (D-GROUP-REASSIGN) — réaffecter une tâche active à un autre groupe : elle redevient libre, candidate pour ce seul
 * groupe, et les tâches suivantes du process vont aussi à ce groupe. Réservé aux éditeurs du dossier ; tracé dans le dossier.
 */
public interface ReassignTaskToGroupUseCase {

    void reassign(UUID linkId, String taskId, String groupCode, AccessContext.User actor);

    /**
     * TODO §113 (D-MAILROOM-MCP) — mêmes vérifications que {@link #reassign}, sans aucune écriture ({@code dryRun} de l'outil MCP).
     *
     * @return le code du groupe cible, tel qu'enregistré
     */
    String check(UUID linkId, String taskId, String groupCode, AccessContext.User actor);
}

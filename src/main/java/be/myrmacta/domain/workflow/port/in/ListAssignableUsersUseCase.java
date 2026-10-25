package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.user.model.KnownUser;
import java.util.List;
import java.util.UUID;

/**
 * Utilisateurs connus (D-ASSIGN-DIR) qui peuvent modifier le dossier du lien — les seuls à qui
 * assigner une tâche a un sens, puisque terminer une tâche exige l'ACL EDIT. Réservé TA/WA, comme
 * la réassignation qu'elle alimente.
 */
public interface ListAssignableUsersUseCase {
    List<KnownUser> listAssignableUsers(UUID linkId, AccessContext.User actor);
}

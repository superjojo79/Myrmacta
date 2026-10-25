package be.myrmacta.domain.parcours.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.parcours.model.Parcours;
import be.myrmacta.domain.parcours.model.ParcoursDefinition;
import java.util.List;

/** TODO §88 (LC5) — gérer les parcours (conception) : réservé à ceux qui conçoivent la configuration. */
public interface ManageParcoursUseCase {

    List<Parcours> list(AccessContext.User actor);

    Parcours get(AccessContext.User actor, String key);

    /**
     * Crée ({@code expectedVersion == null}) ou met à jour. Un parcours enregistré est toujours compilable : un parcours
     * incohérent est refusé avec un message qui nomme l'étape.
     *
     * @throws be.myrmacta.domain.parcours.model.ParcoursConflictException si la version attendue n'est plus la courante, ou
     *     si la clé existe déjà à la création
     */
    Parcours save(AccessContext.User actor, SaveCommand command);

    /** @param expectedVersion si fournie, la suppression n'a lieu que si c'est encore la version courante ({@link be.myrmacta.domain.parcours.model.ParcoursConflictException} sinon) */
    void delete(AccessContext.User actor, String key, Integer expectedVersion);

    record SaveCommand(String key, String label, ParcoursDefinition definition, Integer expectedVersion) {}
}

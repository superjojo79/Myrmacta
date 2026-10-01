package com.flowforge.domain.parcours.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.parcours.model.Parcours;
import com.flowforge.domain.parcours.model.ParcoursDefinition;
import java.util.List;

/** TODO §88 (LC5) — gérer les parcours (conception) : réservé à ceux qui conçoivent la configuration. */
public interface ManageParcoursUseCase {

    List<Parcours> list(AccessContext.User actor);

    Parcours get(AccessContext.User actor, String key);

    /**
     * Crée ({@code expectedVersion == null}) ou met à jour. Un parcours enregistré est toujours compilable : un parcours
     * incohérent est refusé avec un message qui nomme l'étape.
     *
     * @throws com.flowforge.domain.parcours.model.ParcoursConflictException si la version attendue n'est plus la courante, ou
     *     si la clé existe déjà à la création
     */
    Parcours save(AccessContext.User actor, SaveCommand command);

    void delete(AccessContext.User actor, String key);

    record SaveCommand(String key, String label, ParcoursDefinition definition, Integer expectedVersion) {}
}

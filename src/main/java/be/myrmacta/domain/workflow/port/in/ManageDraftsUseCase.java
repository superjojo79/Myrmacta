package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.model.ConfigPublication;
import be.myrmacta.domain.workflow.model.DraftArtifactType;
import be.myrmacta.domain.workflow.model.PendingDraft;
import be.myrmacta.domain.workflow.model.PublicationMode;
import java.time.Instant;
import java.util.List;

/**
 * TODO §82 (D6) — relecture, publication et abandon des brouillons de configuration. <b>Un seul chemin de publication</b> pour
 * l'écran d'administration et l'outil MCP (risque « double chemin qui diverge »).
 */
public interface ManageDraftsUseCase {

    List<PendingDraft> listPending(AccessContext.User actor);

    /**
     * Déploie exactement le brouillon relu.
     *
     * @param expectedUpdatedAt horodatage du brouillon tel que relu ; différent ⇒ {@code DraftChangedException} (obligatoire
     *     en mode {@code MANUAL})
     * @param mode {@code MANUAL} (humain, écran) ou {@code DIRECT} (agent, seulement si la politique l'autorise)
     */
    ConfigPublication publish(
            AccessContext.User actor, DraftArtifactType type, String key, Instant expectedUpdatedAt, PublicationMode mode);

    /**
     * @param expectedUpdatedAt horodatage du brouillon tel que vu ; différent ⇒ {@code DraftChangedException} ({@code null} :
     *     pas de garde, réservé aux appels qui n'ont rien relu)
     */
    void discard(AccessContext.User actor, DraftArtifactType type, String key, Instant expectedUpdatedAt);

    List<ConfigPublication> recentPublications(AccessContext.User actor, int limit);
}

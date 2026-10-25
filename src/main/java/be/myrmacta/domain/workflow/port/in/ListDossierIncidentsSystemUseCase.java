package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.workflow.model.TenantIncident;
import java.util.List;
import java.util.UUID;

/**
 * TODO §94 — usage système (MCP, appelant déjà autorisé sur le dossier) : échecs de toutes les exécutions d'un dossier,
 * <b>sans</b> message. Séparé de {@link WorkflowIncidentsUseCase} pour qu'aucun contrôleur ne l'appelle par erreur sans
 * acteur.
 */
public interface ListDossierIncidentsSystemUseCase {

    List<TenantIncident> listForDossierSystem(UUID dossierId);
}

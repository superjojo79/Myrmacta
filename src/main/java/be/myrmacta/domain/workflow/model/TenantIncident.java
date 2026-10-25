package be.myrmacta.domain.workflow.model;

import java.util.Objects;
import java.util.UUID;

/** TODO §94 — échec d'un job, rattaché à l'exécution et au dossier, pour l'écran de supervision. */
public record TenantIncident(
        WorkflowIncident incident, UUID linkId, UUID dossierId, String dossierReference, String processDefinitionKey) {

    public TenantIncident {
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(linkId, "linkId");
        Objects.requireNonNull(dossierId, "dossierId");
    }
}

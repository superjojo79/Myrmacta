package be.myrmacta.domain.connector.port.in;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import java.util.UUID;

public interface ConnectorTriggerRuntime {

    void onIncomingEvent(ConnectorInstanceData instance, Object rawPayload);

    /**
     * TODO §113 (D-MAILROOM-DEPOSIT) — remet un seul événement déjà construit (dépôt manuel), comme une interrogation le ferait :
     * dossier créé, pièces contrôlées, process mis en file. Le type de l'instance doit être un connecteur TRIGGER.
     *
     * @return le dossier créé ou alimenté, et le nombre de pièces écartées par les contrôles (type, contenu, antivirus, stockage)
     */
    Delivery deliver(ConnectorInstanceData instance, WorkflowTriggerEvent event);

    /** Sort d'une remise : le dossier, et les pièces écartées (revue de clôture de #217 : l'appelant doit pouvoir le dire). */
    record Delivery(UUID dossierId, int rejectedAttachments) {}
}

package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;

/**
 * Frontière transactionnelle unique (DD-TX, TX2) pour la finalisation d'un démarrage de workflow
 * réussi côté moteur : confirmation du lien réservé, mémorisation de l'idempotency-key et
 * transition de statut {@code onStart} éventuelle, commis ou annulés ensemble. Appelé <b>après</b>
 * le démarrage effectif côté moteur (hors transaction JPA de réservation, DD-TX) — jamais avant.
 */
public interface WorkflowStartConfirmationPort {

    void confirm(
            Dossier dossier,
            DossierWorkflowLink reserved,
            String processInstanceId,
            String processDefinitionKey,
            String idempotencyKey);
}

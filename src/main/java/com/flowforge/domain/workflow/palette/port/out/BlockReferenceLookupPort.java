package com.flowforge.domain.workflow.palette.port.out;

import java.util.List;

/**
 * Lecture minimale des ressources du tenant référencées par les briques (TODO §73, D-PALETTE-REFERENCES et
 * D-PALETTE-DEPLOY-REFERENCES). Sources listables : {@code documentTemplates}, {@code connectorInstances},
 * {@code notificationWebhooks} et {@code dmnTables} (dernière version déployée de chaque clé, LC1-suite). Sources
 * vérifiables : ces quatre-là + {@code dossierTypes} et {@code workflowTypes}.
 */
public interface BlockReferenceLookupPort {

    /** Ressources du tenant : templates actifs, instances de connecteur activées de rôle service, webhooks activés, tables DMN (valeur = clé). */
    List<BlockReference> list(String tenantId, String source);

    /**
     * État de la ressource dans ce tenant (TODO §92) : template actif s'il a au moins une version active, inactif s'il n'a
     * que des brouillons ; connecteur de rôle service, webhook non supprimé et type de dossier inactifs s'ils sont
     * désactivés ; {@code workflowTypes} et {@code dmnTables} n'ont pas d'état inactif.
     */
    ReferenceStatus status(String tenantId, String source, String value);

    /** Vrai si la ressource est utilisable dans ce tenant ({@link ReferenceStatus#ACTIVE}). */
    default boolean exists(String tenantId, String source, String value) {
        return status(tenantId, source, value) == ReferenceStatus.ACTIVE;
    }
}

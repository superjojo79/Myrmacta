package com.flowforge.domain.workflow.palette.port.out;

import java.util.List;

/**
 * Lecture minimale des ressources du tenant référencées par les briques (TODO §73, D-PALETTE-REFERENCES et
 * D-PALETTE-DEPLOY-REFERENCES). Sources listables : {@code documentTemplates}, {@code connectorInstances},
 * {@code notificationWebhooks}. Sources vérifiables : ces trois-là + {@code dossierTypes} et {@code workflowTypes}.
 */
public interface BlockReferenceLookupPort {

    /** Ressources du tenant : templates actifs, instances de connecteur activées de rôle service, webhooks activés. */
    List<BlockReference> list(String tenantId, String source);

    /** Vrai si la ressource existe dans ce tenant (template : au moins une version active). */
    boolean exists(String tenantId, String source, String value);
}

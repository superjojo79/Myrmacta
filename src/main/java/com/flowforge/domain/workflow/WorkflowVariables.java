package com.flowforge.domain.workflow;

import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class WorkflowVariables {

    public static final String DOSSIER_ID = "dossierId";
    public static final String DOSSIER_WORKFLOW_LINK_ID = "dossierWorkflowLinkId";
    public static final String TENANT_ID = "tenantId";

    /**
     * Instantané des champs dynamiques du dossier (voir {@code Dossier.fields()}), injecté
     * automatiquement à chaque démarrage de workflow — comble l'écart identifié avec le
     * {@code DossierAwareTask} décrit (mais jamais implémenté) dans
     * {@code analysis doc/04-dossiers.md} : un `serviceTask` ou un template de document n'a plus
     * besoin de propager explicitement chaque champ métier pour y accéder (`${dossierFields.x}`).
     */
    public static final String DOSSIER_FIELDS = "dossierFields";

    private static final Set<String> RESERVED =
            Set.of(DOSSIER_ID, DOSSIER_WORKFLOW_LINK_ID, TENANT_ID, DOSSIER_FIELDS);

    private WorkflowVariables() {}

    public static void assertNoReservedKeys(Map<String, Object> variables) {
        if (variables == null) {
            return;
        }
        for (String key : RESERVED) {
            if (variables.containsKey(key)) {
                throw new IllegalArgumentException("Workflow variable is reserved: " + key);
            }
        }
    }

    /**
     * Fusionne les variables client avec les variables plateforme injectées au démarrage
     * (dossierId / dossierWorkflowLinkId / tenantId / dossierFields), partagé par les trois
     * chemins de start (API manuelle, relais outbox, réconciliation RESERVED).
     *
     * @param dossierFields l'instantané de {@code Dossier.fields()} au moment du démarrage —
     *     jamais rafraîchi automatiquement ensuite (une modification du dossier en cours de
     *     workflow n'est pas répercutée sur cette variable ; voir GUIDE-CONCEPTION-BPMN.md pour
     *     le mécanisme de rafraîchissement explicite si nécessaire).
     */
    public static Map<String, Object> withPlatformVariables(
            Map<String, Object> variables, DossierWorkflowLink link, Map<String, Object> dossierFields) {
        Objects.requireNonNull(link, "link");
        Map<String, Object> merged = new HashMap<>(variables == null ? Map.of() : variables);
        merged.put(DOSSIER_ID, link.dossierId().toString());
        merged.put(DOSSIER_WORKFLOW_LINK_ID, link.id().toString());
        merged.put(TENANT_ID, link.tenantId());
        merged.put(DOSSIER_FIELDS, dossierFields == null ? Map.of() : Map.copyOf(dossierFields));
        return merged;
    }
}

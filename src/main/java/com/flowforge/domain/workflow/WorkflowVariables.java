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
    private static final Set<String> RESERVED = Set.of(DOSSIER_ID, DOSSIER_WORKFLOW_LINK_ID, TENANT_ID);

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
     * (dossierId / dossierWorkflowLinkId / tenantId), partagé par les trois chemins de start
     * (API manuelle, relais outbox, réconciliation RESERVED).
     */
    public static Map<String, Object> withPlatformVariables(Map<String, Object> variables, DossierWorkflowLink link) {
        Objects.requireNonNull(link, "link");
        Map<String, Object> merged = new HashMap<>(variables == null ? Map.of() : variables);
        merged.put(DOSSIER_ID, link.dossierId().toString());
        merged.put(DOSSIER_WORKFLOW_LINK_ID, link.id().toString());
        merged.put(TENANT_ID, link.tenantId());
        return merged;
    }
}

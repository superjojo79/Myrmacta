package com.flowforge.domain.workflow;

import java.util.Map;
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
}

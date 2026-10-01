package com.flowforge.domain.workflow.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A saved-but-not-yet-deployed BPMN diagram for a given process definition key. One record per
 * {@code (tenantId, processDefinitionKey)} — overwritten in place on every save, no version
 * history (D-EDIT-DRAFT-MODEL, {@code docs/ai/design/2026-08-24-feature-edition-bpmn.md}).
 *
 * <p>TODO §82 (D6) : {@code provenance} dit qui l'a rédigé (éditeur, agent MCP, assistant) ; {@code proposedConfig} est la
 * configuration de workflow proposée avec le diagramme (brouillons MCP), appliquée à la publication ; {@code pilotUserId} est
 * l'humain pilote d'un brouillon de l'assistant.
 */
public record BpmnDraft(
        String tenantId,
        String processDefinitionKey,
        String xml,
        Instant updatedAt,
        String updatedBy,
        Provenance provenance,
        ProposedWorkflowConfig proposedConfig,
        String pilotUserId) {

    public BpmnDraft {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(processDefinitionKey, "processDefinitionKey");
        Objects.requireNonNull(xml, "xml");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(updatedBy, "updatedBy");
        provenance = provenance == null ? Provenance.UI : provenance;
    }

    /** Brouillon de l'éditeur (comportement historique) : provenance {@code UI}, sans configuration proposée. */
    public BpmnDraft(String tenantId, String processDefinitionKey, String xml, Instant updatedAt, String updatedBy) {
        this(tenantId, processDefinitionKey, xml, updatedAt, updatedBy, Provenance.UI, null, null);
    }
}

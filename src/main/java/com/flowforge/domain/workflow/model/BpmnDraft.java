package com.flowforge.domain.workflow.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A saved-but-not-yet-deployed BPMN diagram for a given process definition key. One record per
 * {@code (tenantId, processDefinitionKey)} — overwritten in place on every save, no version
 * history (D-EDIT-DRAFT-MODEL, {@code https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-08-24-feature-edition-bpmn.md}).
 *
 * <p>TODO §82 (D6) : {@code provenance} dit qui l'a rédigé (éditeur, agent MCP, assistant) ; {@code proposedConfig} est la
 * configuration de workflow proposée avec le diagramme (brouillons MCP), appliquée à la publication ; {@code pilotUserId} est
 * l'humain pilote d'un brouillon de l'assistant. {@code configProvenance}/{@code configAuthor} disent qui a rédigé cette
 * configuration — elle survit à une reprise du diagramme par un humain (revue D6 M1 : le journal ne doit pas la lui attribuer).
 */
public record BpmnDraft(
        String tenantId,
        String processDefinitionKey,
        String xml,
        Instant updatedAt,
        String updatedBy,
        Provenance provenance,
        ProposedWorkflowConfig proposedConfig,
        String pilotUserId,
        Provenance configProvenance,
        String configAuthor) {

    public BpmnDraft {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(processDefinitionKey, "processDefinitionKey");
        Objects.requireNonNull(xml, "xml");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(updatedBy, "updatedBy");
        provenance = provenance == null ? Provenance.UI : provenance;
        if (proposedConfig == null) {
            configProvenance = null;
            configAuthor = null;
        } else {
            configProvenance = configProvenance == null ? provenance : configProvenance;
            configAuthor = configAuthor == null ? updatedBy : configAuthor;
        }
    }

    /** Sans trace séparée de l'origine de la configuration (elle suit alors celle du diagramme). */
    public BpmnDraft(
            String tenantId,
            String processDefinitionKey,
            String xml,
            Instant updatedAt,
            String updatedBy,
            Provenance provenance,
            ProposedWorkflowConfig proposedConfig,
            String pilotUserId) {
        this(tenantId, processDefinitionKey, xml, updatedAt, updatedBy, provenance, proposedConfig, pilotUserId, null, null);
    }

    /** Brouillon de l'éditeur (comportement historique) : provenance {@code UI}, sans configuration proposée. */
    public BpmnDraft(String tenantId, String processDefinitionKey, String xml, Instant updatedAt, String updatedBy) {
        this(tenantId, processDefinitionKey, xml, updatedAt, updatedBy, Provenance.UI, null, null, null, null);
    }
}

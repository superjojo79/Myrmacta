package com.flowforge.domain.workflow.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A saved-but-not-yet-deployed BPMN diagram for a given process definition key. One record per
 * {@code (tenantId, processDefinitionKey)} — overwritten in place on every save, no version
 * history (D-EDIT-DRAFT-MODEL, {@code docs/ai/design/2026-08-24-feature-edition-bpmn.md}).
 */
public record BpmnDraft(
        String tenantId, String processDefinitionKey, String xml, Instant updatedAt, String updatedBy) {

    public BpmnDraft {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(processDefinitionKey, "processDefinitionKey");
        Objects.requireNonNull(xml, "xml");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(updatedBy, "updatedBy");
    }
}

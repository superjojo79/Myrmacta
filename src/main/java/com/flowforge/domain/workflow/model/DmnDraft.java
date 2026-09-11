package com.flowforge.domain.workflow.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A saved-but-not-yet-deployed DMN decision table for a given decision key. One record per
 * {@code (tenantId, decisionKey)} — overwritten in place on every save, no version history
 * (mirrors {@link BpmnDraft} / D-EDIT-DRAFT-MODEL).
 */
public record DmnDraft(String tenantId, String decisionKey, String xml, Instant updatedAt, String updatedBy) {

    public DmnDraft {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(decisionKey, "decisionKey");
        Objects.requireNonNull(xml, "xml");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(updatedBy, "updatedBy");
    }
}

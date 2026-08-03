package com.flowforge.domain.workflow.model;

import java.util.Objects;

public record WorkflowStatusDefinition(
        String code, String label, boolean initial, StatusOutcome outcome
) {
    public WorkflowStatusDefinition {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(outcome, "outcome");
    }
}

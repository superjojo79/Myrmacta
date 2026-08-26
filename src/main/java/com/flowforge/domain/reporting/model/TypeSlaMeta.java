package com.flowforge.domain.reporting.model;

import java.util.Objects;

public record TypeSlaMeta(String code, String label, String workflowKey, Integer slaTargetHours) {
    public TypeSlaMeta {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(workflowKey, "workflowKey");
        if (slaTargetHours != null && slaTargetHours <= 0) {
            throw new IllegalArgumentException("slaTargetHours must be > 0 when set");
        }
    }
}

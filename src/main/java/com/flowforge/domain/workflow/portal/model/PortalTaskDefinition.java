package com.flowforge.domain.workflow.portal.model;

import java.util.Objects;

/**
 * Tâche externe telle que déclarée dans un BPMN (D-PORTAL-BPMN-MARKER) : {@code userTask}
 * portant {@code flowable:portalRecipient}. {@code ttlDays} est {@code null} quand
 * {@code flowable:portalLinkTtlDays} est absent (défaut tenant).
 */
public record PortalTaskDefinition(
        String taskDefinitionKey, String schemaKey, PortalRecipientSource recipientSource, Integer ttlDays) {

    public PortalTaskDefinition {
        Objects.requireNonNull(taskDefinitionKey, "taskDefinitionKey");
        Objects.requireNonNull(schemaKey, "schemaKey");
        Objects.requireNonNull(recipientSource, "recipientSource");
        if (ttlDays != null && ttlDays <= 0) {
            throw new IllegalArgumentException("ttlDays must be positive");
        }
    }
}

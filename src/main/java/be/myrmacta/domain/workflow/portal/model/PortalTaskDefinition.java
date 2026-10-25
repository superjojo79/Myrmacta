package be.myrmacta.domain.workflow.portal.model;

import java.util.Objects;

/**
 * Tâche externe telle que déclarée dans un BPMN (D-PORTAL-BPMN-MARKER) : {@code userTask}
 * portant {@code flowable:portalRecipient}. {@code ttlDays} est {@code null} quand
 * {@code flowable:portalLinkTtlDays} est absent (défaut tenant). {@code identityField} est {@code null} sauf pour une
 * tâche à authentification forte ({@code flowable:portalIdentityField}, TODO §104).
 */
public record PortalTaskDefinition(
        String taskDefinitionKey, String schemaKey, PortalRecipientSource recipientSource, Integer ttlDays,
        PortalIdentityField identityField) {

    public PortalTaskDefinition {
        Objects.requireNonNull(taskDefinitionKey, "taskDefinitionKey");
        Objects.requireNonNull(schemaKey, "schemaKey");
        Objects.requireNonNull(recipientSource, "recipientSource");
        if (ttlDays != null && ttlDays <= 0) {
            throw new IllegalArgumentException("ttlDays must be positive");
        }
    }

    public PortalTaskDefinition(
            String taskDefinitionKey, String schemaKey, PortalRecipientSource recipientSource, Integer ttlDays) {
        this(taskDefinitionKey, schemaKey, recipientSource, ttlDays, null);
    }

    /** Le tiers doit s'authentifier par FAS ou itsme avant de voir ou de soumettre la tâche (T1). */
    public boolean requiresStrongAuthentication() {
        return identityField != null;
    }
}

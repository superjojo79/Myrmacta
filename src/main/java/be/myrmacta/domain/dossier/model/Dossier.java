package be.myrmacta.domain.dossier.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record Dossier(
        UUID id,
        String tenantId,
        String reference,
        String dossierTypeCode,
        String label,
        DossierStatus status,
        Map<String, Object> fields,
        UUID currentWorkflowLinkId,
        long version,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt,
        String closedBy,
        Instant closedAt,
        Instant purgeEligibleAt
) {
    public Dossier {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(dossierTypeCode, "dossierTypeCode");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(status, "status");
        fields = fields == null ? Map.of() : Map.copyOf(fields);
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public Dossier withStatus(DossierStatus newStatus, String updatedBy, Instant at) {
        String nextClosedBy = closedBy;
        Instant nextClosedAt = closedAt;
        if (newStatus == DossierStatus.CLOTURE) {
            nextClosedBy = updatedBy;
            nextClosedAt = at;
        }
        return new Dossier(
                id,
                tenantId,
                reference,
                dossierTypeCode,
                label,
                newStatus,
                fields,
                currentWorkflowLinkId,
                version,
                createdBy,
                createdAt,
                updatedBy,
                at,
                nextClosedBy,
                nextClosedAt,
                purgeEligibleAt);
    }

    public Dossier withRetentionArchive(String updatedBy, Instant at) {
        Objects.requireNonNull(updatedBy, "updatedBy");
        Objects.requireNonNull(at, "at");
        DossierStatusTransitions.assertCanTransition(status, DossierStatus.ARCHIVE);
        return new Dossier(
                id,
                tenantId,
                reference,
                dossierTypeCode,
                label,
                DossierStatus.ARCHIVE,
                fields,
                currentWorkflowLinkId,
                version,
                createdBy,
                createdAt,
                updatedBy,
                at,
                closedBy,
                closedAt,
                at);
    }

    public Dossier withFields(Map<String, Object> newFields) {
        return new Dossier(
                id,
                tenantId,
                reference,
                dossierTypeCode,
                label,
                status,
                newFields,
                currentWorkflowLinkId,
                version,
                createdBy,
                createdAt,
                updatedBy,
                updatedAt,
                closedBy,
                closedAt,
                purgeEligibleAt);
    }

    public Dossier withFields(Map<String, Object> newFields, String updatedBy, Instant at) {
        return new Dossier(
                id,
                tenantId,
                reference,
                dossierTypeCode,
                label,
                status,
                newFields,
                currentWorkflowLinkId,
                version,
                createdBy,
                createdAt,
                updatedBy,
                at,
                closedBy,
                closedAt,
                purgeEligibleAt);
    }

    public Dossier withVersion(long version) {
        return new Dossier(
                id,
                tenantId,
                reference,
                dossierTypeCode,
                label,
                status,
                fields,
                currentWorkflowLinkId,
                version,
                createdBy,
                createdAt,
                updatedBy,
                updatedAt,
                closedBy,
                closedAt,
                purgeEligibleAt);
    }

    /**
     * D-LOG-GUARD: never prints {@code fields} — a record-generated {@code toString()} would
     * dump the raw map verbatim, including values of fields marked {@link FieldDefinition#isSensitive()}.
     * {@code Dossier} does not carry the {@link FieldDefinition}s needed to filter selectively, so
     * this stays unconditionally safe (entry count only) rather than attempting to distinguish
     * sensitive from non-sensitive keys here.
     */
    @Override
    public String toString() {
        return "Dossier[id=" + id
                + ", tenantId=" + tenantId
                + ", reference=" + reference
                + ", dossierTypeCode=" + dossierTypeCode
                + ", label=" + label
                + ", status=" + status
                + ", fields=<" + fields.size() + " entries redacted>"
                + ", currentWorkflowLinkId=" + currentWorkflowLinkId
                + ", version=" + version
                + ", createdBy=" + createdBy
                + ", createdAt=" + createdAt
                + ", updatedBy=" + updatedBy
                + ", updatedAt=" + updatedAt
                + ", closedBy=" + closedBy
                + ", closedAt=" + closedAt
                + ", purgeEligibleAt=" + purgeEligibleAt
                + "]";
    }

    public Dossier withCurrentWorkflowLinkId(UUID workflowLinkId, String updatedBy, Instant at) {
        return new Dossier(
                id,
                tenantId,
                reference,
                dossierTypeCode,
                label,
                status,
                fields,
                workflowLinkId,
                version,
                createdBy,
                createdAt,
                updatedBy,
                at,
                closedBy,
                closedAt,
                purgeEligibleAt);
    }
}

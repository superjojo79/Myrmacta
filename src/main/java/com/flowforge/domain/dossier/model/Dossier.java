package com.flowforge.domain.dossier.model;

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
        Instant closedAt
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
                nextClosedAt);
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
                closedAt);
    }
}

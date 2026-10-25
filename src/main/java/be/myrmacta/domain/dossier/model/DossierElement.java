package be.myrmacta.domain.dossier.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record DossierElement(
        UUID id,
        UUID dossierId,
        String tenantId,
        ElementType type,
        String label,
        String storageRef,
        Map<String, Object> metadata,
        String addedBy,
        Instant addedAt,
        String workflowStepId) {

    public DossierElement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dossierId, "dossierId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(label, "label");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        Objects.requireNonNull(addedBy, "addedBy");
        Objects.requireNonNull(addedAt, "addedAt");
    }
}

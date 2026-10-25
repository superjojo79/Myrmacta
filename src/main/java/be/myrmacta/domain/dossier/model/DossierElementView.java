package be.myrmacta.domain.dossier.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Vue API éléments — sans {@code storageRef} (DD-ELEMENTS-DTO). */
public record DossierElementView(
        UUID id,
        ElementType type,
        String label,
        String addedBy,
        Instant addedAt,
        String workflowStepId,
        Map<String, Object> metadata,
        boolean downloadable) {

    public DossierElementView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(addedBy, "addedBy");
        Objects.requireNonNull(addedAt, "addedAt");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static DossierElementView from(DossierElement element) {
        Objects.requireNonNull(element, "element");
        boolean downloadable = (element.type() == ElementType.PRODUCED_DOC || element.type() == ElementType.ATTACHMENT)
                && element.storageRef() != null
                && !element.storageRef().isBlank();
        return new DossierElementView(
                element.id(),
                element.type(),
                element.label(),
                element.addedBy(),
                element.addedAt(),
                element.workflowStepId(),
                element.metadata(),
                downloadable);
    }
}

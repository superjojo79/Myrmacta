package be.myrmacta.domain.dossier.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DossierRelationView(
        UUID id,
        RelationType type,
        RelationDirection direction,
        DossierListItem relatedDossier,
        String createdBy,
        Instant createdAt
) {
    public DossierRelationView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(relatedDossier, "relatedDossier");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}

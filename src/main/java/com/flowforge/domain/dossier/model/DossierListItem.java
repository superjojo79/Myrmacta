package com.flowforge.domain.dossier.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DossierListItem(
        UUID id,
        String reference,
        String label,
        String dossierTypeCode,
        String dossierTypeLabel,
        DossierStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public DossierListItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(dossierTypeCode, "dossierTypeCode");
        dossierTypeLabel = dossierTypeLabel == null ? "" : dossierTypeLabel;
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public DossierListItem withDossierTypeLabel(String typeLabel) {
        return new DossierListItem(
                id, reference, label, dossierTypeCode, typeLabel, status, createdAt, updatedAt);
    }

    public static DossierListItem from(Dossier dossier, String dossierTypeLabel) {
        Instant updatedAt = dossier.updatedAt() != null ? dossier.updatedAt() : dossier.createdAt();
        return new DossierListItem(
                dossier.id(),
                dossier.reference(),
                dossier.label(),
                dossier.dossierTypeCode(),
                dossierTypeLabel,
                dossier.status(),
                dossier.createdAt(),
                updatedAt);
    }
}

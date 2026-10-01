package com.flowforge.domain.workflow.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * TODO §82 (D6) — trace d'une publication de configuration : qui a rédigé (provenance et auteur du brouillon), qui a publié,
 * quand, quelle version, par quel mode.
 */
public record ConfigPublication(
        UUID id,
        String tenantId,
        DraftArtifactType artifactType,
        String artifactKey,
        Integer version,
        Provenance draftProvenance,
        String draftAuthor,
        String publishedBy,
        Instant publishedAt,
        PublicationMode mode) {

    public ConfigPublication {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(artifactType, "artifactType");
        Objects.requireNonNull(artifactKey, "artifactKey");
        Objects.requireNonNull(draftProvenance, "draftProvenance");
        Objects.requireNonNull(draftAuthor, "draftAuthor");
        Objects.requireNonNull(publishedBy, "publishedBy");
        Objects.requireNonNull(publishedAt, "publishedAt");
        Objects.requireNonNull(mode, "mode");
    }
}

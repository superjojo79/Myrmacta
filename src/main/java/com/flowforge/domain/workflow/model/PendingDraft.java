package com.flowforge.domain.workflow.model;

import java.time.Instant;
import java.util.Objects;

/**
 * TODO §82 (D6) — un brouillon en attente de publication, tel que l'écran « Brouillons à publier » et le MCP le présentent.
 *
 * @param publishedVersion version actuellement en ligne ({@code null} : jamais publié)
 * @param addedLines lignes présentes dans le brouillon et absentes de la version en ligne (indicatif)
 * @param removedLines lignes de la version en ligne absentes du brouillon (indicatif)
 */
public record PendingDraft(
        DraftArtifactType type,
        String key,
        Provenance provenance,
        String author,
        Instant updatedAt,
        boolean hasProposedConfig,
        String note,
        Integer publishedVersion,
        int addedLines,
        int removedLines) {

    public PendingDraft {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}

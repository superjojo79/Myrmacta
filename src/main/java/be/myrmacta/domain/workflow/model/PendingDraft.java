package be.myrmacta.domain.workflow.model;

import java.time.Instant;
import java.util.Objects;

/**
 * TODO §82 (D6) — un brouillon en attente de publication, tel que l'écran « Brouillons à publier » et le MCP le présentent.
 *
 * @param publishedVersion version actuellement en ligne ({@code null} : jamais publié)
 * @param addedLines lignes présentes dans le brouillon et absentes de la version en ligne (indicatif)
 * @param removedLines lignes de la version en ligne absentes du brouillon (indicatif)
 * @param proposedConfig configuration de workflow qui sera appliquée à la publication (à relire avec le diagramme)
 * @param configProvenance origine de cette configuration, qui peut différer de celle du diagramme (revue D6 M1)
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
        int removedLines,
        ProposedWorkflowConfig proposedConfig,
        Provenance configProvenance,
        String configAuthor) {

    public PendingDraft {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}

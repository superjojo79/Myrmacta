package be.myrmacta.domain.assistant.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * TODO §86 (LC3, revue adversariale M2) — une écriture directe proposée par l'assistant, en attente de l'accord de l'humain. Les
 * outils {@code define_*} écrivent tout de suite (pas de brouillon) : l'assistant ne fait qu'en valider les arguments à blanc ;
 * c'est l'utilisateur qui voit ce qui sera écrit et l'applique, avec sa propre identité.
 */
public record AssistantAction(
        UUID id,
        UUID conversationId,
        String tool,
        String argumentsJson,
        Status status,
        String outcome,
        Instant createdAt,
        Instant resolvedAt) {

    public enum Status {
        PENDING,
        /** Prise par un exécutant : une deuxième application simultanée est refusée. */
        APPLYING,
        APPLIED,
        DISMISSED,
        FAILED
    }

    public AssistantAction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(argumentsJson, "argumentsJson");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}

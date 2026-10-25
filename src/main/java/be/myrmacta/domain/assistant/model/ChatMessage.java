package be.myrmacta.domain.assistant.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Message conservé d'une conversation. {@code TOOL} = résultat d'un outil (tronqué à la conservation, jamais de secret : les
 * outils n'en renvoient pas) ; {@code toolName} renseigné pour {@code TOOL}.
 */
public record ChatMessage(UUID id, UUID conversationId, int seq, Role role, String content, String toolName, Instant createdAt) {

    public enum Role {
        USER,
        ASSISTANT,
        TOOL
    }

    public ChatMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}

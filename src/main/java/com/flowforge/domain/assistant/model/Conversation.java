package com.flowforge.domain.assistant.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** TODO §86 (LC3) — une conversation avec l'assistant de conception ; visible de son seul propriétaire. */
public record Conversation(
        UUID id,
        String tenantId,
        String ownerId,
        String title,
        Instant createdAt,
        Instant updatedAt,
        int turns,
        long inputTokens,
        long outputTokens) {

    public Conversation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public long totalTokens() {
        return inputTokens + outputTokens;
    }

    public Conversation afterTurn(Instant now, long addedInput, long addedOutput) {
        return new Conversation(id, tenantId, ownerId, title, createdAt, now, turns + 1, inputTokens + addedInput,
                outputTokens + addedOutput);
    }

    /** Ajoute la consommation d'une réponse déjà obtenue (sans compter un tour de plus). */
    public Conversation withUsage(Instant now, long addedInput, long addedOutput) {
        return new Conversation(id, tenantId, ownerId, title, createdAt, now, turns, inputTokens + addedInput,
                outputTokens + addedOutput);
    }
}

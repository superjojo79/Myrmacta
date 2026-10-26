package be.myrmacta.domain.precheck.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * TODO §106 (lot B, P4, HC3) — un constat : déterministe (passe 1, règles, pièce illisible) ou proposé par le modèle,
 * toujours avec sa source. L'agent l'accepte ou l'écarte ; rien d'autre ne change le dossier ni le workflow.
 */
public record PrecheckFinding(
        UUID id,
        int ordinal,
        FindingKind kind,
        Origin origin,
        SourceType sourceType,
        String sourceRef,
        String message,
        Double confidence,
        Decision decision,
        String decidedBy,
        Instant decidedAt) {

    public static final int MAX_MESSAGE_CHARS = 300;

    public enum Origin { DETERMINISTIC, MODEL }

    public enum SourceType { FIELD, PIECE, RULE, QUESTION }

    public enum Decision { ACCEPTED, DISMISSED }

    public PrecheckFinding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceRef, "sourceRef");
        Objects.requireNonNull(message, "message");
        message = truncate(message.strip(), MAX_MESSAGE_CHARS);
        if (origin == Origin.DETERMINISTIC && confidence != null) {
            throw new IllegalArgumentException("a deterministic finding carries no confidence");
        }
    }

    public static PrecheckFinding deterministic(int ordinal, FindingKind kind, SourceType type, String ref, String message) {
        return new PrecheckFinding(UUID.randomUUID(), ordinal, kind, Origin.DETERMINISTIC, type, ref, message, null, null,
                null, null);
    }

    public PrecheckFinding decided(Decision newDecision, String by, Instant at) {
        return new PrecheckFinding(id, ordinal, kind, origin, sourceType, sourceRef, message, confidence, newDecision,
                newDecision == null ? null : by, newDecision == null ? null : at);
    }

    /** Coupe sans séparer une paire de substitution. */
    public static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        int end = Character.isHighSurrogate(text.charAt(max - 1)) ? max - 1 : max;
        return text.substring(0, end);
    }
}

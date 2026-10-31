package be.myrmacta.domain.mailroom.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * TODO §113 (D-MAILROOM-INDEX) — un identifiant d'e-mail (Message-ID, ou conversationId Graph) associé au dossier qui l'a reçu
 * ou envoyé, et à l'adresse de l'interlocuteur (l'expéditeur d'un e-mail reçu, le destinataire d'un e-mail envoyé).
 */
public record MailIndexEntry(
        String tenantId, Kind kind, String key, Direction direction, UUID dossierId, String counterpart, Instant recordedAt) {

    public static final int MAX_KEY_CHARS = 250;
    public static final int MAX_COUNTERPART_CHARS = 320;

    public enum Kind { MESSAGE_ID, CONVERSATION }

    public enum Direction { IN, OUT }

    public MailIndexEntry {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(dossierId, "dossierId");
        Objects.requireNonNull(recordedAt, "recordedAt");
        if (key == null || key.isBlank() || key.length() > MAX_KEY_CHARS) {
            throw new IllegalArgumentException("key must be 1 to " + MAX_KEY_CHARS + " characters");
        }
        counterpart = counterpart == null || counterpart.isBlank() ? null : counterpart.strip().toLowerCase(Locale.ROOT);
        if (counterpart != null && counterpart.length() > MAX_COUNTERPART_CHARS) {
            counterpart = null;
        }
    }

    /** Domaine de l'interlocuteur ({@code null} s'il est inconnu). */
    public String counterpartDomain() {
        if (counterpart == null) {
            return null;
        }
        int at = counterpart.lastIndexOf('@');
        return at < 0 || at == counterpart.length() - 1 ? null : counterpart.substring(at + 1);
    }
}

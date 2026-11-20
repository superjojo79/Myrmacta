package be.myrmacta.domain.ebox.model;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * TODO §155 (D-EBOX-SEND-IDEMPOTENCE) — demande d'envoi.
 *
 * @param idempotencyKey clé stable d'un envoi (ex. dossier, étape, document) : elle part en {@code Idempotency-Key} (ou en
 *     {@code customerReference} pour un recommandé) et un rejeu avec la même clé ne publie jamais deux fois
 * @param dossierId dossier d'origine, facultatif
 * @param fallbackChannel voie de secours que le process suivra si l'eBox ne suffit pas
 */
public record SendEboxCommand(
        EboxSenderAccount sender,
        EboxOutgoingMessage message,
        String idempotencyKey,
        UUID dossierId,
        FallbackChannel fallbackChannel) {

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

    public SendEboxCommand {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(fallbackChannel, "fallbackChannel");
        if (idempotencyKey == null || !KEY.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException("idempotencyKey must be 1 to 100 characters among letters, digits and ._:-");
        }
    }
}

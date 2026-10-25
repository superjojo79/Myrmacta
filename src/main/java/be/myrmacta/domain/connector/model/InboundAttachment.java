package be.myrmacta.domain.connector.model;

import java.util.Objects;

/** TODO §87 (D3c) — pièce jointe d'un message entrant (e-mail), à rattacher au dossier après contrôles. */
public record InboundAttachment(String filename, String contentType, byte[] content) {

    public InboundAttachment {
        filename = filename == null ? "" : filename;
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(content, "content");
    }
}

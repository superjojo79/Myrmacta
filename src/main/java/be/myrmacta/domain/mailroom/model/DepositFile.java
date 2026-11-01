package be.myrmacta.domain.mailroom.model;

import java.util.Objects;

/** TODO §113 (D-MAILROOM-DEPOSIT) — un fichier déposé : nom d'origine et contenu. */
public record DepositFile(String filename, byte[] content) {

    public DepositFile {
        filename = filename == null ? "" : filename.strip();
        Objects.requireNonNull(content, "content");
    }
}
